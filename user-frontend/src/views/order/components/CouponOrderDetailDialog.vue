<template>
  <ElDialog
    v-model="visible"
    title="订单详情"
    width="560px"
    class="user-dialog coupon-order-detail-dialog"
    modal-class="user-dialog-overlay"
    align-center
    append-to-body
    destroy-on-close
    :show-close="!busy"
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
  >
    <ElSkeleton v-if="loading" :rows="8" animated />
    <ElAlert v-else-if="error" title="订单详情加载失败" type="error" :closable="false" show-icon>
      <p>{{ error }}</p>
      <ElButton @click="emit('retry')">重新加载</ElButton>
    </ElAlert>
    <template v-else-if="order">
      <div class="status-banner" :class="{ ended: order.status === 2 || order.status === 3 }">
        <svg viewBox="0 0 32 32" fill="none" aria-hidden="true">
          <circle cx="16" cy="16" r="12" stroke="currentColor" stroke-width="1.7" />
          <path
            v-if="order.status === 0"
            d="M16 8v8l6 4"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
          />
          <path
            v-else-if="order.status === 1"
            d="m10 16 4 4 8-9"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <path
            v-else-if="order.status === 2"
            d="m13 12-3 3 3 3m-3-3h8a3.5 3.5 0 0 0 0-7h-1"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <path
            v-else
            d="m12 12 8 8m0-8-8 8"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
          />
        </svg>
        <div>
          <h2>{{ couponOrderStatusNames[order.status] }}</h2>
          <p>{{ couponOrderStatusDescriptions[order.status] }}</p>
        </div>
      </div>
      <section class="detail-items" aria-label="购买的优惠券">
        <div class="detail-item">
          <div>
            <h3>{{ order.couponName }}</h3>
            <p>{{ couponOffer(order) }}</p>
          </div>
          <span>{{ money(order.price) }}</span>
        </div>
        <div class="total-row">
          <strong>{{ order.status === 0 ? '应付金额' : '实付金额' }}</strong
          ><strong data-testid="coupon-order-total">{{ money(order.price) }}</strong>
        </div>
      </section>
      <dl class="order-information">
        <div>
          <dt>订单编号</dt>
          <dd>{{ order.number }}</dd>
        </div>
        <div v-if="order.status === 1 || order.status === 2">
          <dt>券号</dt>
          <dd>{{ order.couponNumber }}</dd>
        </div>
        <div>
          <dt>下单时间</dt>
          <dd>{{ formatTime(order.createTime) }}</dd>
        </div>
        <div v-if="order.status === 0">
          <dt>支付截止</dt>
          <dd>{{ formatTime(order.payDeadline) }}</dd>
        </div>
        <div v-if="order.status === 1 || order.status === 2">
          <dt>支付时间</dt>
          <dd>{{ formatTime(order.payTime) }}</dd>
        </div>
        <template v-if="order.status === 2">
          <div>
            <dt>退款时间</dt>
            <dd>{{ formatTime(order.refundTime) }}</dd>
          </div>
          <div>
            <dt>退款原因</dt>
            <dd>{{ order.refundReason || '未填写' }}</dd>
          </div>
        </template>
        <template v-if="order.status === 3">
          <div>
            <dt>关闭时间</dt>
            <dd>{{ formatTime(order.cancelTime) }}</dd>
          </div>
          <div>
            <dt>关闭原因</dt>
            <dd>{{ order.cancelReason || '未填写' }}</dd>
          </div>
        </template>
      </dl>
    </template>
    <template #footer>
      <CouponOrderActions
        v-if="order && !loading && !error"
        :order="order"
        :busy="busy"
        @action="emit('action', $event)"
      />
      <ElButton v-else :disabled="busy" @click="visible = false">关闭</ElButton>
    </template>
  </ElDialog>
</template>
<script setup lang="ts">
import { ElAlert, ElButton, ElDialog, ElSkeleton } from 'element-plus'
import type { CouponOrderDetail } from '@/api/couponOrders'
import { couponOffer } from '@/views/coupon/couponDisplay'
import {
  couponOrderStatusDescriptions,
  couponOrderStatusNames,
  type CouponOrderAction,
} from '../couponOrderDisplay'
import { formatTime, money } from '../orderDisplay'
import CouponOrderActions from './CouponOrderActions.vue'
defineProps<{
  order: CouponOrderDetail | null
  loading: boolean
  error: string
  busy: boolean
}>()
const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ retry: []; action: [action: CouponOrderAction] }>()
</script>
<style scoped>
.status-banner {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px;
  border-radius: 12px;
  background: #edf4ff;
}
.status-banner.ended {
  background: #f1f4f8;
}
.status-banner svg {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  color: #7a9ad3;
}
.status-banner h2 {
  margin: 0;
  font-size: 23px;
  font-weight: 600;
  line-height: 1.5;
}
.status-banner p {
  margin: 8px 0 0;
  color: #8491a6;
  line-height: 1.7;
  font-size: 14px;
}
.detail-items {
  padding-block: 30px 24px;
  border-bottom: 1px solid #e3eaf5;
}
.detail-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}
.detail-item > div {
  min-width: 0;
}
.detail-item h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
  overflow-wrap: anywhere;
}
.detail-item p {
  margin: 8px 0 0;
  color: #8491a6;
  font-size: 13px;
}
.detail-item > span {
  flex-shrink: 0;
}
.total-row {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  margin-top: 22px;
  font-size: 16px;
}
.order-information {
  display: grid;
  gap: 20px;
  margin: 28px 0 8px;
}
.order-information > div {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: 16px;
  font-size: 14px;
  line-height: 1.7;
}
dt {
  color: #8491a6;
}
dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}
@media (max-width: 600px) {
  .status-banner {
    gap: 12px;
    padding: 18px 14px;
  }
  .status-banner h2 {
    font-size: 20px;
  }
  .status-banner svg {
    width: 30px;
    height: 30px;
  }
  .order-information > div {
    grid-template-columns: 70px minmax(0, 1fr);
    gap: 10px;
  }
}
</style>
