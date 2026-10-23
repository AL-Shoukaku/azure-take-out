<template>
  <div class="orders-page">
    <div class="page-heading">
      <div>
        <h1>我的订单</h1>
        <p>每一餐，都是日常的记录。</p>
      </div>
      <ElButton class="continue-button" @click="router.push('/menu')"
        >继续点餐 <span aria-hidden="true">→</span></ElButton
      >
    </div>
    <ElTabs class="order-tabs" :model-value="tab" @tab-change="selectTab($event as OrderTab)">
      <ElTabPane
        v-for="option in orderTabs"
        :key="option.key"
        :label="option.label"
        :name="option.key"
      />
    </ElTabs>

    <div v-if="loading" class="order-grid" aria-label="正在加载订单" aria-busy="true">
      <ElSkeleton v-for="index in 6" :key="index" :rows="5" class="loading-card" animated />
    </div>
    <ElAlert v-else-if="error" title="订单加载失败" type="error" :closable="false" show-icon>
      <p>{{ error }}</p>
      <ElButton @click="load">重新加载</ElButton>
    </ElAlert>
    <div v-else-if="records.length" class="order-grid" :aria-busy="busy">
      <OrderCard
        v-for="order in records"
        :key="order.id"
        :order="order"
        :address="cardAddress(order, addresses.addresses)"
        :busy="busy"
        @detail="openDetail(order.id)"
        @action="handleAction(order, $event)"
      />
    </div>
    <div v-else class="empty-state">
      <svg viewBox="0 0 48 48" fill="none" aria-hidden="true">
        <path
          d="M14 6h22v36H8V6h6Zm2 0V3h12v6H16V6Zm0 12h12m-12 8h12m-12 8h8"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
      <h2>还没有相关订单</h2>
      <p>去挑选喜欢的美食，开启下一顿美味。</p>
    </div>

    <nav
      v-if="total > orderPageSize && !loading && !error"
      class="order-pagination"
      aria-label="订单分页"
    >
      <ElConfigProvider :locale="zhCn">
        <ElPagination
          v-model:current-page="page"
          :page-size="orderPageSize"
          :total="total"
          :pager-count="5"
          :disabled="busy"
          layout="total, prev, pager, next, jumper"
        />
      </ElConfigProvider>
    </nav>

    <OrderDetailDialog
      v-model="detailOpen"
      :order="detail"
      :loading="detailLoading"
      :error="detailError"
      :busy="busy"
      @retry="loadDetail"
      @action="detail && handleAction(detail, $event)"
    />
    <PaymentDialog
      v-if="paymentOrder"
      v-model="paymentOpen"
      v-model:busy="paymentBusy"
      :order="paymentOrder"
      @paid="paymentSucceeded"
    />
    <PaymentSuccessDialog v-if="paymentOrder" v-model="successOpen" :order="paymentOrder" />
    <ElDialog
      v-model="contactOpen"
      title="联系商家"
      width="400px"
      class="user-dialog"
      modal-class="user-dialog-overlay"
      align-center
      append-to-body
      destroy-on-close
    >
      <div class="merchant-contact">
        <p>碧蓝外卖 · 演示手机号</p>
        <a href="tel:13800000000">138 0000 0000</a>
        <p>订单配送或售后问题，可以联系商家咨询。</p>
      </div>
      <template #footer
        ><ElButton type="primary" @click="contactOpen = false">知道了</ElButton></template
      >
    </ElDialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, onScopeDispose, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElConfigProvider,
  ElDialog,
  ElMessage,
  ElMessageBox,
  ElPagination,
  ElSkeleton,
  ElTabPane,
  ElTabs,
} from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { cancelOrder, remindOrder, repeatOrder, type Order } from '@/api/orders'
import { useCartStore } from '@/stores/cart'
import { useAddressesStore } from '@/stores/addresses'
import { getToken } from '@/utils/auth'
import PaymentDialog from '@/components/PaymentDialog.vue'
import PaymentSuccessDialog from '@/components/PaymentSuccessDialog.vue'
import OrderCard from './components/OrderCard.vue'
import OrderDetailDialog from './components/OrderDetailDialog.vue'
import {
  cardAddress,
  orderPageSize,
  orderTabs,
  type OrderAction,
  type OrderTab,
} from './orderDisplay'
import { useOrders } from './useOrders'

const router = useRouter()
const addresses = useAddressesStore()
const cart = useCartStore()
const {
  tab,
  page,
  records,
  total,
  loading,
  error,
  load,
  selectTab,
  detailOpen,
  detail,
  detailLoading,
  detailError,
  openDetail,
  loadDetail,
  refresh,
} = useOrders()
const actionBusy = ref(false)
const paymentOpen = ref(false)
const paymentOrder = ref<Order | null>(null)
const paymentBusy = ref(false)
const successOpen = ref(false)
const contactOpen = ref(false)
const busy = computed(() => actionBusy.value || paymentBusy.value)
let active = true
onScopeDispose(() => {
  active = false
})
onMounted(() => {
  if (!addresses.loaded) void addresses.load()
})

function paymentSucceeded(): void {
  successOpen.value = true
  void refresh()
}

async function handleAction(order: Order, action: OrderAction): Promise<void> {
  if (busy.value) return
  if (action === 'contact') {
    contactOpen.value = true
    return
  }
  if (action === 'pay') {
    paymentOrder.value = order
    paymentOpen.value = true
    return
  }
  const token = getToken()
  const current = () => active && token === getToken()
  actionBusy.value = true
  try {
    if (action === 'cancel') {
      try {
        await ElMessageBox.confirm(
          order.payStatus === 1
            ? '确认取消这笔订单吗？已支付订单将按当前演示流程退款。'
            : '确认取消这笔订单吗？',
          '取消订单',
          {
            confirmButtonText: '确认取消',
            cancelButtonText: '暂不取消',
            type: 'warning',
            modalClass: 'user-dialog-overlay',
          },
        )
      } catch {
        return
      }
      if (!current()) return
      await cancelOrder(order.id)
      if (!current()) return
      ElMessage.success('订单已取消')
      await refresh()
    } else if (action === 'remind') {
      await remindOrder(order.id)
      if (current()) ElMessage.success('已提醒商家，请耐心等待')
    } else {
      await repeatOrder(order.id)
      if (!current()) return
      try {
        await cart.load()
      } catch {
        if (current()) ElMessage.warning('商品已加入购物车，购物车信息暂未刷新，请勿重复添加')
      }
      if (!current()) return
      ElMessage.success('订单商品已加入购物车')
      await router.push('/menu')
    }
  } catch (cause) {
    if (current()) {
      ElMessage.error(cause instanceof Error ? cause.message : '操作失败，请稍后重试')
      await refresh()
    }
  } finally {
    actionBusy.value = false
  }
}
</script>
<style scoped>
.page-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-bottom: 28px;
}
h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 600;
  line-height: 1.4;
}
.page-heading p {
  margin: 10px 0 0;
  color: #8491a6;
  font-size: 14px;
}
.continue-button {
  height: 42px;
  padding-inline: 20px;
  border-radius: 8px;
  color: #526580;
}
.continue-button span {
  margin-left: 14px;
  font-size: 20px;
}
.order-tabs {
  margin-bottom: 24px;
}
.order-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}
.order-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: #e0e8f3;
}
.order-tabs :deep(.el-tabs__item) {
  height: 54px;
  color: #8491a6;
  font-weight: 400;
}
.order-tabs :deep(.el-tabs__item.is-active) {
  color: var(--user-primary);
  font-weight: 500;
}
.order-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
}
.loading-card,
.empty-state {
  padding: 28px;
  border: 1px solid #e2e9f4;
  border-radius: 12px;
  background: #fff;
}
.order-pagination {
  display: flex;
  justify-content: center;
  overflow: auto;
  margin-top: 30px;
}
.empty-state {
  padding-block: 60px;
  text-align: center;
}
.empty-state svg {
  width: 52px;
  height: 52px;
  margin-bottom: 20px;
  color: #98b4e4;
}
.empty-state h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 500;
}
.empty-state p {
  margin: 12px 0 0;
  color: #8491a6;
  font-size: 14px;
  line-height: 1.8;
}
.merchant-contact {
  padding-block: 8px;
  text-align: center;
}
.merchant-contact p {
  color: #8491a6;
  font-size: 14px;
  line-height: 1.8;
}
.merchant-contact a {
  display: inline-block;
  margin-block: 8px;
  color: var(--user-primary);
  font-size: 28px;
  text-decoration: none;
}
@media (max-width: 1199px) {
  .order-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 700px) {
  .order-grid {
    grid-template-columns: 1fr;
    gap: 20px;
  }
  .page-heading {
    align-items: flex-start;
    flex-wrap: wrap;
  }
  .continue-button {
    height: 38px;
    margin-left: auto;
    padding-inline: 14px;
  }
  .order-tabs :deep(.el-tabs__item) {
    padding-inline: 14px;
    font-size: 13px;
  }
  .order-pagination {
    justify-content: flex-start;
  }
}
</style>
