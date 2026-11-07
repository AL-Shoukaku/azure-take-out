<template>
  <section class="coupons-page" aria-label="抢优惠券">
    <header class="page-heading">
      <div>
        <h1>抢优惠券</h1>
        <p>查看本店优惠与活动时间</p>
      </div>
      <ElButton class="refresh-button" :loading="loading" :disabled="busy" @click="load"
        >刷新优惠券</ElButton
      >
    </header>

    <div v-if="loading" class="coupon-grid" aria-label="正在加载优惠券" aria-busy="true">
      <ElSkeleton v-for="index in 3" :key="index" :rows="6" class="loading-card" animated />
    </div>
    <ElAlert v-else-if="error" title="优惠券加载失败" type="error" :closable="false" show-icon>
      <p>{{ error }}</p>
      <ElButton @click="load">重新加载</ElButton>
    </ElAlert>
    <div v-else-if="cards.length" class="coupon-grid">
      <article
        v-for="{ coupon, state } in cards"
        :key="coupon.id"
        class="coupon-card"
        :class="{ 'is-inactive': state !== 'active' }"
        :aria-label="coupon.name"
      >
        <div class="coupon-top">
          <span class="coupon-type">{{ coupon.type === 1 ? '代金券' : '满减券' }}</span>
          <span class="coupon-state" :class="{ 'is-active': state === 'active' }">{{
            stateNames[state]
          }}</span>
        </div>
        <div class="coupon-body">
          <h2>{{ coupon.name }}</h2>
          <p class="coupon-discount"><span>¥</span>{{ coupon.discountAmount.toFixed(2) }}</p>
          <p class="coupon-offer">{{ couponOffer(coupon) }}</p>
          <dl class="coupon-times">
            <div>
              <dt>活动开始</dt>
              <dd>{{ formatActivityTime(coupon.startTime) }}</dd>
            </div>
            <div>
              <dt>活动结束</dt>
              <dd>{{ formatActivityTime(coupon.endTime) }}</dd>
            </div>
          </dl>
        </div>
        <div class="coupon-footer">
          <p>
            剩余 <strong>{{ coupon.stock }}</strong> 张
          </p>
          <ElButton
            type="primary"
            :loading="actionCouponId === coupon.id"
            :disabled="
              busy ||
              !session.user ||
              received.has(coupon.id) ||
              submitted.has(coupon.id) ||
              state !== 'active'
            "
            @click="takeCoupon(coupon)"
            >{{ actionLabel(coupon) }}</ElButton
          >
        </div>
      </article>
    </div>
    <div v-else class="empty-state">
      <svg viewBox="0 0 48 48" fill="none" aria-hidden="true">
        <path
          d="M8 12h32v9a4 4 0 0 0 0 8v9H8v-9a4 4 0 0 0 0-8v-9Zm22 0v5m0 6v4m0 6v5"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
      <h2>暂无在售优惠券</h2>
      <p>商家还没有可领取或购买的优惠券，稍后再来看看吧。</p>
    </div>
    <CouponPaymentDialog
      v-if="paymentOrder"
      v-model="paymentOpen"
      v-model:busy="paymentBusy"
      :order="paymentOrder"
      :now="now"
      @paid="paymentSucceeded"
    />
    <CouponSuccessDialog v-if="receipt" v-model="successOpen" :result="receipt" />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onScopeDispose, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElAlert, ElButton, ElMessage, ElSkeleton } from 'element-plus'
import {
  buyCoupon,
  claimCoupon,
  getAvailableCoupons,
  type AvailableCoupon,
  type CouponPaymentResult,
} from '@/api/coupons'
import { useSessionStore } from '@/stores/session'
import { getToken } from '@/utils/auth'
import { money } from '@/utils/pricing'
import { couponOffer, couponState, formatActivityTime, stateNames } from './couponDisplay'
import { type CouponReceipt, type CouponPaymentOrder } from './couponOrders'
import CouponPaymentDialog from './components/CouponPaymentDialog.vue'
import CouponSuccessDialog from './components/CouponSuccessDialog.vue'

const session = useSessionStore()
const coupons = ref<AvailableCoupon[]>([])
const loading = ref(true),
  error = ref(''),
  now = ref(Date.now())
const cards = computed(() =>
  coupons.value.map((coupon) => ({ coupon, state: couponState(coupon, now.value) })),
)
let revision = 0,
  active = true
let timer: ReturnType<typeof setInterval> | undefined
const actionCouponId = ref<number | null>(null),
  paymentBusy = ref(false)
const busy = computed(() => actionCouponId.value !== null || paymentBusy.value)
const paymentOrder = ref<CouponPaymentOrder | null>(null),
  paymentOpen = ref(false)
const receipt = ref<CouponReceipt | null>(null),
  successOpen = ref(false)
const received = ref(new Set<number>())
const submitted = ref(new Set<number>())

watch(
  () => session.user?.id,
  () => {
    paymentOpen.value = false
    paymentOrder.value = null
    successOpen.value = false
    receipt.value = null
    received.value = new Set()
    submitted.value = new Set()
  },
  { immediate: true },
)
function actionLabel(coupon: AvailableCoupon): string {
  if (received.value.has(coupon.id)) return coupon.type === 1 ? '已购买' : '已领取'
  if (submitted.value.has(coupon.id)) return '已下单'
  return coupon.type === 1 ? `${money(coupon.price)} 购买` : '立即领取'
}

async function load(): Promise<void> {
  const current = ++revision,
    token = getToken()
  loading.value = true
  error.value = ''
  try {
    const result = await getAvailableCoupons()
    if (!active || current !== revision || token !== getToken()) return
    coupons.value = result
    now.value = Date.now()
  } catch (cause) {
    if (active && current === revision && token === getToken())
      error.value = cause instanceof Error ? cause.message : '优惠券加载失败，请稍后重试'
  } finally {
    if (active && current === revision) loading.value = false
  }
}

async function takeCoupon(coupon: AvailableCoupon): Promise<void> {
  if (
    busy.value ||
    !session.user ||
    received.value.has(coupon.id) ||
    submitted.value.has(coupon.id)
  )
    return
  now.value = Date.now()
  if (couponState(coupon, now.value) !== 'active') return
  const token = getToken(),
    userId = session.user.id
  const current = () => active && token === getToken() && userId === session.user?.id
  actionCouponId.value = coupon.id
  try {
    if (coupon.type === 2) {
      const result = await claimCoupon(coupon.id)
      if (!current()) return
      received.value.add(coupon.id)
      receipt.value = {
        kind: 'claim',
        name: coupon.name,
        offer: couponOffer(coupon),
        number: result.number,
        expireTime: result.expireTime,
      }
      successOpen.value = true
    } else {
      const result = await buyCoupon(coupon.id)
      if (!current()) return
      const order: CouponPaymentOrder = {
        ...result,
        couponId: coupon.id,
        name: coupon.name,
        offer: couponOffer(coupon),
      }
      submitted.value.add(coupon.id)
      paymentOrder.value = order
      paymentOpen.value = true
    }
    await load()
  } catch (cause) {
    if (current()) {
      ElMessage.error(cause instanceof Error ? cause.message : '操作失败，请稍后重试')
      await load()
    }
  } finally {
    actionCouponId.value = null
  }
}

function paymentSucceeded(result: CouponPaymentResult): void {
  const order = paymentOrder.value
  if (!order) return
  received.value.add(order.couponId)
  receipt.value = {
    kind: 'payment',
    name: order.name,
    offer: order.offer,
    number: result.number,
    expireTime: result.expireTime,
    price: order.price,
  }
  successOpen.value = true
  void load()
}

onBeforeRouteLeave((to) => !busy.value || to.name === 'login')
onMounted(() => {
  void load()
  timer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
})
onScopeDispose(() => {
  active = false
  revision++
  clearInterval(timer)
})
</script>

<style scoped>
.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
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
.refresh-button {
  height: 42px;
  border-radius: 8px;
}
.coupon-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
}
.coupon-card,
.loading-card,
.empty-state {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--user-border);
  border-radius: 15px;
  background: #fff;
}
.coupon-card {
  display: flex;
  flex-direction: column;
}
.coupon-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 18px 22px;
  background: #eff6ff;
  font-size: 12px;
}
.coupon-type {
  color: var(--user-primary);
  font-weight: 500;
}
.coupon-state {
  padding: 4px 9px;
  border-radius: 5px;
  background: #e4ebf6;
  color: #697b94;
}
.coupon-state.is-active {
  background: #e4f5ee;
  color: #27865c;
}
.coupon-body {
  flex: 1;
  padding: 22px;
}
h2 {
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.5;
}
.coupon-discount {
  margin: 20px 0 8px;
  color: var(--user-primary);
  font-size: 40px;
  font-weight: 600;
  line-height: 1.2;
  overflow-wrap: anywhere;
}
.coupon-discount span {
  margin-right: 4px;
  font-size: 22px;
}
.coupon-offer {
  margin: 0 0 24px;
  color: #526580;
  font-size: 14px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.coupon-times {
  display: grid;
  gap: 10px;
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
}
.coupon-times > div {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
}
dt {
  color: #8491a6;
}
dd {
  margin: 0;
}
.coupon-footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 20px 22px;
  border-top: 1px dashed var(--user-border);
}
.coupon-footer p {
  margin: 0;
  color: #8491a6;
  font-size: 13px;
}
.coupon-footer strong {
  color: #526580;
}
.coupon-footer .el-button {
  height: 38px;
  margin: 0;
  border-radius: 8px;
}
.is-inactive .coupon-discount {
  color: #7697c8;
}
.loading-card {
  padding: 28px;
}
.empty-state {
  padding: 64px 24px;
  text-align: center;
}
.empty-state svg {
  width: 52px;
  height: 52px;
  margin-bottom: 20px;
  color: #98b4e4;
}
.empty-state p {
  margin: 12px 0 0;
  color: #8491a6;
  font-size: 14px;
  line-height: 1.8;
}
@media (max-width: 1200px) {
  .coupon-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 700px) {
  .coupon-grid {
    grid-template-columns: 1fr;
  }
  .page-heading {
    flex-wrap: wrap;
  }
}
</style>
