<template>
  <div class="orders-page">
    <div class="page-heading">
      <div>
        <h1>优惠券订单</h1>
        <p>每一张券，都是下一餐的小惊喜。</p>
      </div>
      <div class="heading-actions">
        <ElButton type="primary" @click="router.push('/orders/takeout')">查看外卖订单</ElButton>
        <ElButton class="coupon-button" @click="router.push('/coupons/take')"
          >抢优惠券 <span aria-hidden="true">→</span></ElButton
        >
      </div>
    </div>
    <ElTabs class="order-tabs" :model-value="tab" @tab-change="selectTab($event as CouponOrderTab)">
      <ElTabPane
        v-for="option in couponOrderTabs"
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
    <div v-else-if="records.length" class="order-grid" :aria-busy="paymentBusy">
      <CouponOrderCard
        v-for="order in records"
        :key="order.id"
        :order="order"
        :busy="paymentBusy"
        @detail="openDetail(order.id)"
        @action="handleAction(order, $event)"
      />
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
      <h2>还没有相关订单</h2>
      <p>去抢一张优惠券，让下一餐更划算。</p>
    </div>

    <nav
      v-if="total > couponOrderPageSize && !loading && !error"
      class="order-pagination"
      aria-label="订单分页"
    >
      <ElConfigProvider :locale="zhCn">
        <ElPagination
          v-model:current-page="page"
          :page-size="couponOrderPageSize"
          :total="total"
          :pager-count="5"
          layout="total, prev, pager, next, jumper"
        />
      </ElConfigProvider>
    </nav>

    <CouponOrderDetailDialog
      v-model="detailOpen"
      :order="detail"
      :loading="detailLoading"
      :error="detailError"
      :busy="paymentBusy"
      @retry="loadDetail"
      @action="detail && handleAction(detail, $event)"
    />
    <CouponPaymentDialog
      v-if="paymentOrder"
      v-model="paymentOpen"
      v-model:busy="paymentBusy"
      :order="paymentOrder"
      :now="now"
      @paid="paymentSucceeded"
    />
    <CouponSuccessDialog
      v-if="receipt"
      v-model="successOpen"
      :result="receipt"
      close-text="查看订单"
    />
    <MerchantContactDialog v-model="contactOpen" />
  </div>
</template>
<script setup lang="ts">
import { onMounted, onScopeDispose, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElConfigProvider,
  ElPagination,
  ElSkeleton,
  ElTabPane,
  ElTabs,
} from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import type { CouponPaymentResult } from '@/api/coupons'
import type { CouponOrderSummary } from '@/api/couponOrders'
import { useSessionStore } from '@/stores/session'
import MerchantContactDialog from '@/components/MerchantContactDialog.vue'
import CouponPaymentDialog from '@/views/coupon/components/CouponPaymentDialog.vue'
import CouponSuccessDialog from '@/views/coupon/components/CouponSuccessDialog.vue'
import type { CouponPaymentSummary, CouponReceipt } from '@/views/coupon/couponOrders'
import CouponOrderCard from './components/CouponOrderCard.vue'
import CouponOrderDetailDialog from './components/CouponOrderDetailDialog.vue'
import {
  couponOrderOffer,
  couponOrderPageSize,
  couponOrderTabs,
  type CouponOrderAction,
  type CouponOrderTab,
} from './couponOrderDisplay'
import { useCouponOrders } from './useCouponOrders'

const router = useRouter()
const session = useSessionStore()
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
} = useCouponOrders()
const contactOpen = ref(false)
const now = ref(Date.now())
const paymentOrder = ref<CouponPaymentSummary | null>(null),
  paymentOpen = ref(false),
  paymentBusy = ref(false)
const receipt = ref<CouponReceipt | null>(null),
  successOpen = ref(false)
let timer: ReturnType<typeof setInterval> | undefined

watch(
  () => session.token,
  () => {
    paymentOpen.value = false
    paymentOrder.value = null
    successOpen.value = false
    receipt.value = null
  },
)

function handleAction(order: CouponOrderSummary, action: CouponOrderAction): void {
  if (paymentBusy.value) return
  if (action === 'contact') {
    contactOpen.value = true
    return
  }
  now.value = Date.now()
  paymentOrder.value = {
    number: order.number,
    price: order.price,
    payDeadline: order.payDeadline,
    name: order.couponName,
    offer: couponOrderOffer(order),
  }
  paymentOpen.value = true
}

function paymentSucceeded(result: CouponPaymentResult): void {
  const order = paymentOrder.value
  if (!order) return
  receipt.value = {
    kind: 'payment',
    name: order.name,
    offer: order.offer,
    number: result.number,
    expireTime: result.expireTime,
    price: order.price,
  }
  successOpen.value = true
  void refresh()
}

onBeforeRouteLeave((to) => !paymentBusy.value || to.name === 'login')
onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
})
onScopeDispose(() => {
  clearInterval(timer)
})
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
.heading-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
}
.heading-actions .el-button {
  height: 42px;
  margin: 0;
  padding-inline: 20px;
  border-radius: 8px;
}
.coupon-button {
  color: #526580;
}
.coupon-button span {
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
  .heading-actions {
    margin-left: auto;
  }
  .heading-actions .el-button {
    height: 38px;
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
