<template>
  <ElConfigProvider :locale="zhCn">
    <section class="menu-page">
      <div class="welcome-line">
        <span>WELCOME TO YOUR LITTLE SKY</span><span>一人食，也要有滋有味。 ✧</span>
      </div>
      <section class="menu-hero" aria-label="今日推荐">
        <div class="hero-copy">
          <p class="hero-eyebrow">— &nbsp; A LITTLE SKY, A LITTLE JOY.</p>
          <h1 aria-label="今天的好心情，从好好吃饭开始。">
            今天的好心情，<br />从<span>好好吃饭</span>开始。
          </h1>
          <p class="hero-description">热腾腾的烟火气，是日常里最温柔的陪伴。</p>
          <ElButton type="primary" @click="scrollToMenu"
            >看看今天吃什么 <span aria-hidden="true">→</span></ElButton
          >
        </div>
        <div class="hero-background" aria-hidden="true">
          <img :src="heroBackground" alt="" fetchpriority="high" decoding="async" />
        </div>
      </section>
      <section class="shop-strip" aria-label="店铺信息">
        <div class="shop-brand">
          <div class="shop-logo"><img :src="logo" alt="碧蓝外卖店铺标志" /></div>
          <div>
            <div class="shop-name">
              <h2>碧蓝外卖</h2>
              <span class="shop-status" :class="{ closed: shopStatus !== 1 }">
                {{ shopStatus === 1 ? '营业中' : shopStatus === 0 ? '休息中' : '状态加载中' }}</span
              >
            </div>
            <p>现点现做 · 用心做好每一餐</p>
          </div>
        </div>
        <div class="shop-fees">
          <span>¥6 <small>配送费</small></span
          ><span>¥1 / 件 <small>打包费</small></span>
        </div>
        <RouterLink class="delivery-address" to="/addresses"
          ><svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0Z"
              stroke="currentColor"
              stroke-width="1.6"
            />
            <circle cx="12" cy="10" r="3" stroke="currentColor" stroke-width="1.6" /></svg
          ><span
            ><small>配送至</small><b>{{ selectedAddress?.detail || '请选择收货地址' }}</b></span
          ><span aria-hidden="true">›</span></RouterLink
        >
      </section>
      <ElAlert
        v-if="shopError"
        :title="shopError"
        type="error"
        :closable="false"
        class="menu-notice"
        ><ElButton link type="primary" @click="loadShop">重新获取营业状态</ElButton></ElAlert
      >
      <ElAlert
        v-else-if="shopStatus === 0"
        title="店铺休息中，暂时无法加购和下单"
        type="info"
        :closable="false"
        class="menu-notice"
      />
      <ElAlert
        v-if="pendingOrder && !paid"
        title="订单已提交，等待支付"
        type="info"
        :closable="false"
        class="menu-notice"
        ><ElButton type="primary" link @click="paymentOpen = true">继续支付</ElButton
        ><RouterLink to="/orders">查看我的订单</RouterLink></ElAlert
      >
      <section ref="menuArea" class="ordering-area" aria-label="在线点餐">
        <ElTabs
          :model-value="selectedCategory"
          class="category-tabs"
          @tab-change="selectCategory(Number($event))"
          ><ElTabPane :name="0" label="全部餐点" /><ElTabPane
            v-for="category in categories"
            :key="category.id"
            :name="category.id"
            :label="category.name"
        /></ElTabs>
        <div class="menu-workspace">
          <section class="catalog" :aria-busy="loading" aria-label="餐点列表">
            <div class="catalog-heading">
              <p>MADE WITH CARE</p>
              <h2>
                {{ categoryName }} <small>{{ filtered.length }} 道好味</small>
              </h2>
            </div>
            <div v-if="loading" class="product-grid">
              <ElSkeleton v-for="index in 6" :key="index" animated :rows="7" />
            </div>
            <ElAlert v-else-if="error" title="餐点加载失败" type="error" :closable="false" show-icon
              ><p>{{ error }}</p>
              <ElButton @click="load">重新加载餐点</ElButton></ElAlert
            >
            <ElEmpty
              v-else-if="!filtered.length"
              description="这个分类暂时没有在售餐点，看看其他分类吧"
            />
            <div v-else class="product-grid">
              <ProductCard
                v-for="product in visibleProducts"
                :key="`${product.type}-${product.id}`"
                :product="product"
                :disabled="cartLocked || shopStatus !== 1"
                @detail="openProduct"
                @add="addProduct"
              />
            </div>
            <nav
              v-if="!loading && !error && filtered.length > menuPageSize"
              class="menu-pagination"
              aria-label="餐点分页"
            >
              <ElPagination
                :current-page="page"
                :page-size="menuPageSize"
                :total="filtered.length"
                :pager-count="5"
                layout="total, prev, pager, next, jumper"
                @current-change="changePage"
              />
            </nav>
          </section>
          <CartPanel
            :closed="shopStatus !== 1"
            :checking="checking || submitting"
            @add="changeQuantity('add', $event)"
            @subtract="changeQuantity('subtract', $event)"
            @clean="clearCart"
            @retry="loadCart"
            @checkout="checkout"
          />
        </div>
      </section>
      <ProductDialog
        v-if="selectedProduct"
        v-model="productOpen"
        :product="selectedProduct"
        :busy="cart.busy"
        :disabled="cartLocked || shopStatus !== 1"
        @add="addProduct"
      />
      <CheckoutDialog v-model="checkoutOpen" :busy="submitting" @submit="placeOrder" />
      <PaymentDialog
        v-if="pendingOrder"
        v-model="paymentOpen"
        :order="pendingOrder"
        @paid="paymentSucceeded"
      />
      <PaymentSuccessDialog v-if="pendingOrder" v-model="successOpen" :order="pendingOrder" />
    </section>
  </ElConfigProvider>
</template>
<script setup lang="ts">
import { computed, onMounted, onScopeDispose, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElConfigProvider,
  ElEmpty,
  ElMessage,
  ElMessageBox,
  ElPagination,
  ElSkeleton,
  ElTabPane,
  ElTabs,
} from 'element-plus'
import { getShopStatus, type Product } from '@/api/menu'
import type { CartItem, CartProduct } from '@/api/cart'
import { submitOrder, type OrderSubmission, type Order } from '@/api/orders'
import { useCartStore } from '@/stores/cart'
import { useAddressesStore } from '@/stores/addresses'
import { getToken } from '@/utils/auth'
import logo from '@/assets/images/brand/logo.webp'
import heroBackground from '@/assets/images/menu/background.webp'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import PaymentDialog from '@/components/PaymentDialog.vue'
import PaymentSuccessDialog from '@/components/PaymentSuccessDialog.vue'
import CartPanel from './components/CartPanel.vue'
import ProductCard from './components/ProductCard.vue'
import ProductDialog from './components/ProductDialog.vue'
import CheckoutDialog from './components/CheckoutDialog.vue'
import { useMenu } from './useMenu'
import { cartProduct, menuPageSize } from './menuDisplay'

const cart = useCartStore()
const addresses = useAddressesStore()
const {
  categories,
  selectedCategory,
  page,
  loading,
  error,
  filtered,
  visibleProducts,
  categoryName,
  selectCategory,
  load,
} = useMenu()
const shopStatus = ref<0 | 1 | null>(null)
const shopError = ref('')
const selectedAddress = computed(() =>
  addresses.addresses.find((address) => address.id === addresses.selectedId),
)
const cartLocked = computed(
  () => cart.busy || cart.loading || !!cart.error || checking.value || submitting.value,
)
const menuArea = ref<HTMLElement>()
const selectedProduct = ref<Product | null>(null)
const productOpen = ref(false)
const checkoutOpen = ref(false)
const checking = ref(false)
const submitting = ref(false)
const pendingOrder = ref<Pick<Order, 'id' | 'number' | 'amount'> | null>(null)
const paymentOpen = ref(false)
const paid = ref(false)
const successOpen = ref(false)
let active = true
let shopRevision = 0
onScopeDispose(() => {
  active = false
  shopRevision += 1
})
function valid(token: string | null): boolean {
  return active && token === getToken()
}
function report(cause: unknown): void {
  ElMessage.error(cause instanceof Error ? cause.message : '操作失败，请稍后重试')
}
async function loadShop(): Promise<void> {
  const current = ++shopRevision
  const token = getToken()
  try {
    const status = await getShopStatus()
    if (valid(token) && current === shopRevision) {
      shopStatus.value = status
      shopError.value = ''
    }
  } catch (cause) {
    if (valid(token) && current === shopRevision) {
      shopStatus.value = null
      shopError.value = cause instanceof Error ? cause.message : '营业状态获取失败'
    }
  }
}
async function loadCart(): Promise<void> {
  try {
    await cart.load()
  } catch {
    /* 购物车内提供错误提示和重试入口。 */
  }
}
onMounted(() => {
  void load()
  void loadShop()
  void loadCart()
  void addresses.load()
})
function scrollToMenu(): void {
  menuArea.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
function changePage(value: number): void {
  page.value = value
  scrollToMenu()
}
function openProduct(product: Product): void {
  selectedProduct.value = product
  productOpen.value = true
}
async function mutate(
  action: 'add' | 'subtract' | 'clean',
  product?: CartProduct,
): Promise<boolean> {
  const token = getToken()
  try {
    return await cart.mutate(action, product)
  } catch (cause) {
    if (valid(token)) report(cause)
    return false
  }
}
async function addProduct(product: Product, flavor: string | null = null): Promise<void> {
  if (cartLocked.value || shopStatus.value !== 1) return
  const result = await mutate('add', {
    ...(product.type === 1 ? { dishId: product.id } : { setmealId: product.id }),
    dishFlavor: flavor,
  })
  if (result && active) {
    productOpen.value = false
    ElMessage.success('已加入餐袋')
  }
}
async function changeQuantity(action: 'add' | 'subtract', item: CartItem): Promise<void> {
  if (cartLocked.value || (action === 'add' && shopStatus.value !== 1)) return
  await mutate(action, cartProduct(item))
}
async function clearCart(): Promise<void> {
  if (cartLocked.value || !cart.count) return
  const token = getToken()
  try {
    await ElMessageBox.confirm('清空后需要重新选择餐品，确定清空餐袋吗？', '清空餐袋', {
      confirmButtonText: '确认清空',
      cancelButtonText: '保留餐品',
      type: 'warning',
      customClass: 'user-confirm',
      modalClass: 'user-dialog-overlay',
    })
  } catch {
    return
  }
  if (valid(token)) await mutate('clean')
}
async function checkout(): Promise<void> {
  if (cartLocked.value || !cart.count || shopStatus.value !== 1) return
  const token = getToken()
  checking.value = true
  try {
    await Promise.all([cart.load(), addresses.load(), loadShop()])
    if (!valid(token)) return
    if (shopStatus.value !== 1) {
      ElMessage.warning('店铺暂时无法下单，请稍后再试')
      return
    }
    if (!cart.count) {
      ElMessage.warning('餐袋为空，请先选择餐品')
      return
    }
    checkoutOpen.value = true
  } catch (cause) {
    if (valid(token)) report(cause)
  } finally {
    if (valid(token)) checking.value = false
  }
}
async function placeOrder(data: OrderSubmission): Promise<void> {
  if (submitting.value || !cart.count) return
  const token = getToken()
  submitting.value = true
  try {
    await loadShop()
    if (!valid(token)) return
    if (shopStatus.value !== 1) {
      ElMessage.warning('店铺暂时无法下单，请稍后再试')
      return
    }
    const result = await submitOrder(data)
    if (!valid(token)) return
    pendingOrder.value = { id: result.id, number: result.orderNumber, amount: result.orderAmount }
    paid.value = false
    cart.reset()
    checkoutOpen.value = false
    paymentOpen.value = true
  } catch (cause) {
    if (valid(token)) report(cause)
  } finally {
    if (valid(token)) submitting.value = false
  }
}
function paymentSucceeded(): void {
  paid.value = true
  successOpen.value = true
}
</script>
<style scoped>
.welcome-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 22px;
  color: #95a5bb;
  font-size: 11px;
}
.welcome-line > span:first-child {
  letter-spacing: 1.4px;
}
.menu-hero {
  position: relative;
  display: flex;
  min-height: 340px;
  overflow: hidden;
  border-radius: 20px;
  background: #e7efff;
}
.hero-copy {
  position: relative;
  z-index: 1;
  padding: 40px 48px;
}
.hero-eyebrow {
  margin: 0 0 22px;
  color: #7b99d0;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 2.5px;
}
h1 {
  margin: 0;
  color: #354b70;
  font-family: 'Songti SC', 'SimSun', 'Microsoft YaHei', 'PingFang SC', serif;
  font-size: clamp(30px, 3.1vw, 46px);
  font-weight: 600;
  line-height: 1.7;
  letter-spacing: 2px;
}
h1 span {
  color: #6287cf;
}
.hero-description {
  margin: 16px 0 24px;
  color: #839abf;
  font-size: 13px;
  line-height: 1.8;
}
.hero-copy .el-button {
  height: 42px;
  padding-inline: 20px;
  border-radius: 7px;
}
.hero-copy .el-button span span {
  margin-left: 24px;
  font-size: 22px;
}
.hero-background {
  position: absolute;
  inset: 0 0 0 38%;
}
.hero-background img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 70% center;
}
.hero-background::after {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    #e7efff 0%,
    rgb(231 239 255 / 85%) 12%,
    rgb(231 239 255 / 35%) 34%,
    rgb(231 239 255 / 0%) 58%
  );
  content: '';
}
.shop-strip {
  display: flex;
  align-items: center;
  gap: 36px;
  padding: 28px 0;
  border-bottom: 1px solid var(--user-border);
}
.shop-brand {
  display: flex;
  align-items: center;
  gap: 16px;
}
.shop-logo {
  display: grid;
  width: 58px;
  height: 58px;
  flex-shrink: 0;
  place-items: center;
  overflow: hidden;
  border: 1px solid var(--user-border);
  border-radius: 14px;
  background: #fff;
}
.shop-logo img {
  width: 44px;
  height: 44px;
  object-fit: contain;
}
.shop-name {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.shop-name h2 {
  margin: 0;
  font-size: 20px;
  letter-spacing: 1px;
}
.shop-status {
  padding: 5px 8px;
  border-radius: 4px;
  background: #e8f4ee;
  color: #6b9d8d;
  font-size: 10px;
  white-space: nowrap;
}
.shop-status.closed {
  background: #e8edf5;
  color: #8997ad;
}
.shop-status::before {
  display: inline-block;
  width: 4px;
  height: 4px;
  margin-right: 5px;
  border-radius: 50%;
  background: currentColor;
  vertical-align: middle;
  content: '';
}
.shop-brand p {
  margin: 10px 0 0;
  color: #93a3b9;
  font-size: 11px;
}
.shop-fees {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  font-size: 13px;
}
.shop-fees small {
  margin-left: 5px;
  color: #91a2bc;
  font-size: 12px;
}
.delivery-address {
  display: flex;
  align-items: center;
  gap: 14px;
  max-width: 310px;
  min-width: 0;
  margin-left: auto;
  padding-left: 25px;
  border-left: 1px solid #e4ebf6;
  color: #465872;
  text-decoration: none;
}
.delivery-address svg {
  width: 20px;
  height: 23px;
  flex-shrink: 0;
  color: var(--user-primary);
}
.delivery-address > span:nth-child(2) {
  min-width: 0;
}
.delivery-address small {
  display: block;
  margin-bottom: 7px;
  color: #99a8bd;
  font-size: 10px;
}
.delivery-address b {
  display: block;
  overflow: hidden;
  font-size: 12px;
  font-weight: 400;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.delivery-address:hover {
  color: var(--user-primary);
}
.category-tabs {
  margin: 22px 0 24px;
}
.category-tabs :deep(.el-tabs__item) {
  height: 56px;
  padding-inline: 22px;
  color: #8899b1;
  font-size: 13px;
}
.category-tabs :deep(.el-tabs__item.is-active) {
  color: var(--user-primary);
  font-weight: 600;
}
.category-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: var(--user-border);
}
.menu-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 304px;
  align-items: start;
  gap: 26px;
}
.ordering-area {
  scroll-margin-top: 108px;
}
.catalog {
  min-width: 0;
}
.catalog-heading {
  padding: 6px 0 24px;
}
.catalog-heading p {
  margin: 0 0 14px;
  color: #9aaac0;
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 2px;
}
.catalog-heading h2 {
  margin: 0;
  font-size: 22px;
}
.catalog-heading small {
  margin-left: 13px;
  color: #97a7bd;
  font-size: 11px;
  font-weight: 400;
}
.product-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: stretch;
  gap: 22px;
}
.menu-pagination {
  display: flex;
  justify-content: center;
  margin-top: 30px;
  overflow-x: auto;
}
.menu-notice {
  margin-top: 15px;
}
.menu-notice a {
  margin-left: 12px;
  color: var(--user-primary);
  text-decoration: none;
}
@media (max-width: 1200px) {
  .menu-workspace {
    grid-template-columns: minmax(0, 1fr) 285px;
    gap: 20px;
  }
  .product-grid {
    gap: 16px;
  }
  .shop-strip {
    gap: 20px;
  }
  .shop-fees {
    gap: 15px;
  }
}
@media (max-width: 1100px) {
  .product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .shop-fees {
    flex-direction: column;
    gap: 10px;
  }
  .delivery-address {
    max-width: 240px;
  }
}
@media (max-width: 900px) {
  .ordering-area {
    scroll-margin-top: 136px;
  }
  .menu-workspace {
    grid-template-columns: 1fr;
  }
  .shop-strip {
    flex-wrap: wrap;
  }
  .delivery-address {
    max-width: 100%;
  }
  .hero-copy {
    padding: 32px;
  }
  .welcome-line > span:last-child {
    display: none;
  }
  .menu-hero {
    min-height: 320px;
  }
  .menu-pagination {
    justify-content: flex-start;
  }
}
@media (max-width: 600px) {
  .hero-background {
    opacity: 0.4;
  }
  .hero-copy {
    padding: 30px 24px;
  }
  h1 {
    font-size: 27px;
    letter-spacing: 0;
  }
  .hero-eyebrow {
    font-size: 8px;
    letter-spacing: 1.6px;
  }
  .hero-description {
    max-width: 215px;
    font-size: 12px;
  }
  .shop-strip {
    gap: 20px 12px;
  }
  .shop-brand {
    gap: 12px;
  }
  .shop-name h2 {
    font-size: 18px;
  }
  .shop-fees {
    flex-direction: row;
    gap: 16px;
  }
  .delivery-address {
    width: 100%;
    margin: 0;
    padding: 0;
    border: 0;
  }
  .product-grid {
    grid-template-columns: 1fr;
  }
  .category-tabs {
    margin-top: 14px;
  }
  .catalog-heading h2 {
    font-size: 20px;
  }
  .menu-pagination :deep(.el-pagination) {
    flex-wrap: wrap;
    gap: 8px 0;
  }
}
@media (prefers-reduced-motion: reduce) {
  .menu-page {
    scroll-behavior: auto;
  }
}
</style>
