<template>
  <ElDialog
    v-model="visible"
    title="订单信息"
    class="admin-dialog coupon-order-detail-dialog"
    width="760px"
    top="10vh"
    append-to-body
    :show-close="!busy"
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
  >
    <div v-loading="loading" class="coupon-order-detail">
      <div v-if="error" class="management-load-error" role="alert">
        {{ error }} <ElButton @click="emit('retry')">重试详情</ElButton>
      </div>
      <template v-else-if="order">
        <div class="coupon-order-detail-top">
          <div>
            <span class="detail-label">订单号：</span><strong>{{ order.number }}</strong
            ><span class="coupon-order-state" :class="{ 'is-pending': order.status === 0 }">{{
              couponOrderStatusNames[order.status]
            }}</span>
          </div>
          <p>下单时间：{{ formatTime(order.createTime) }}</p>
        </div>
        <div class="coupon-order-customer">
          <div class="coupon-order-grid">
            <div><span class="detail-label">用户名：</span>{{ order.username }}</div>
            <div><span class="detail-label">用户ID：</span>{{ order.userId }}</div>
            <div><span class="detail-label">优惠券：</span>{{ order.couponName }}</div>
            <div>
              <span class="detail-label">券类型：</span>{{ order.type === 1 ? '代金券' : '满减券' }}
            </div>
            <div>
              <span class="detail-label">优惠方式：</span>{{ couponOffer(order, order.type) }}
            </div>
            <div v-if="order.status === 1 || order.status === 2">
              <span class="detail-label">券号：</span>{{ order.couponNumber }}
            </div>
          </div>
          <div v-if="order.status === 2 || order.status === 3" class="coupon-order-reason">
            <span>{{ order.status === 2 ? '退款原因' : '关闭原因' }}</span>
            <p>{{ (order.status === 2 ? order.refundReason : order.cancelReason) || '—' }}</p>
          </div>
        </div>
        <div class="coupon-order-fees">
          <span class="detail-label">费用</span>
          <div class="coupon-order-fee-grid">
            <div>
              购买价格：<span class="coupon-order-price" data-testid="coupon-order-price">{{
                money(order.price)
              }}</span>
            </div>
            <div v-if="order.status === 0">支付截止时间：{{ formatTime(order.payDeadline) }}</div>
            <div v-if="order.status === 1 || order.status === 2">
              支付时间：{{ formatTime(order.payTime) }}
            </div>
            <div v-if="order.status === 2">退款时间：{{ formatTime(order.refundTime) }}</div>
            <div v-if="order.status === 3">关闭时间：{{ formatTime(order.cancelTime) }}</div>
          </div>
        </div>
      </template>
    </div>
    <template #footer>
      <ElButton :disabled="busy" @click="visible = false">返回</ElButton
      ><ElButton
        v-if="order && !error && !loading && order.status === 1"
        :disabled="busy"
        @click="emit('refund')"
        >退款</ElButton
      >
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { ElButton, ElDialog, vLoading } from 'element-plus'
import type { CouponOrderDetail } from '@/api/couponOrder'
import { couponOffer, couponOrderStatusNames } from './couponOrderDisplay'
import { formatTime, money } from './orderDisplay'

defineProps<{
  order: CouponOrderDetail | null
  loading: boolean
  error: string
  busy: boolean
}>()
const emit = defineEmits<{ retry: []; refund: [] }>()
const visible = defineModel<boolean>({ required: true })
</script>

<style>
.coupon-order-detail-dialog .el-dialog__body {
  padding: 25px 30px 0;
}
.coupon-order-detail {
  min-height: 180px;
  max-height: calc(86vh - 175px);
  overflow: auto;
  padding-right: 8px;
}
.coupon-order-detail .detail-label {
  color: #818693;
}
.coupon-order-detail-top {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 22px 26px;
  border-bottom: 1px solid #e7e6e6;
}
.coupon-order-detail-top strong {
  font-size: 16px;
  overflow-wrap: anywhere;
}
.coupon-order-detail-top p {
  margin: 0;
}
.coupon-order-state {
  display: inline-block;
  margin-left: 18px;
  padding: 4px 8px;
  border-radius: 14px;
  background: #333;
  color: white;
  white-space: nowrap;
}
.coupon-order-state.is-pending {
  background: #f56c6c;
}
.coupon-order-customer {
  margin-top: 23px;
  padding: 20px 40px;
  background: var(--admin-surface-soft);
}
.coupon-order-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 30px;
}
.coupon-order-grid > div {
  overflow-wrap: anywhere;
}
.coupon-order-reason {
  display: flex;
  align-items: center;
  gap: 25px;
  margin-top: 14px;
  padding: 6px;
  border: 1px solid #b6b6b6;
  border-radius: 4px;
  background: var(--admin-surface);
}
.coupon-order-reason > span {
  flex-shrink: 0;
  padding: 6px 12px;
  border-radius: 4px;
  background: #e5e4e4;
}
.coupon-order-reason p {
  margin: 0;
  color: #f56c6c;
  overflow-wrap: anywhere;
}
.coupon-order-fees {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr);
  padding: 22px 40px;
}
.coupon-order-fee-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 30px;
}
.coupon-order-price {
  color: #f56c6c;
}
@media (max-width: 900px) {
  .coupon-order-customer,
  .coupon-order-fees {
    padding-inline: 20px;
  }
}
</style>
