<template>
  <article class="order-card" :aria-label="`订单 ${order.number}`">
    <div class="card-heading">
      <div class="order-identification">
        <time>{{ formatTime(order.createTime) }}</time>
        <p :title="order.number">订单号：{{ order.number }}</p>
      </div>
      <span class="order-status" :class="`status-${order.status}`">{{
        couponOrderStatusNames[order.status]
      }}</span>
    </div>
    <div class="card-summary">
      <span class="coupon-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none">
          <path
            d="M4 5h16v5a2 2 0 0 0 0 4v5H4v-5a2 2 0 0 0 0-4V5Zm11 0v3m0 3v2m0 3v3"
            stroke="currentColor"
            stroke-width="1.4"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </span>
      <div class="summary-text">
        <h2 :title="order.couponName">{{ order.couponName }}</h2>
        <p class="coupon-offer">{{ couponOrderOffer(order) }}</p>
      </div>
      <strong class="order-amount">{{ money(order.price) }}</strong>
    </div>
    <dl class="order-times">
      <div v-for="row in couponOrderTimes(order)" :key="row.label">
        <dt>{{ row.label }}</dt>
        <dd>{{ row.value }}</dd>
      </div>
    </dl>
    <div class="card-footer">
      <ElButton :disabled="busy" @click="emit('detail')">查看详情</ElButton>
      <CouponOrderActions :order="order" :busy="busy" @action="emit('action', $event)" />
    </div>
  </article>
</template>
<script setup lang="ts">
import { ElButton } from 'element-plus'
import type { CouponOrderSummary } from '@/api/couponOrders'
import {
  couponOrderOffer,
  couponOrderStatusNames,
  couponOrderTimes,
  type CouponOrderAction,
} from '../couponOrderDisplay'
import { formatTime, money } from '../orderDisplay'
import CouponOrderActions from './CouponOrderActions.vue'
defineProps<{ order: CouponOrderSummary; busy: boolean }>()
const emit = defineEmits<{ detail: []; action: [action: CouponOrderAction] }>()
</script>
<style scoped>
.order-card {
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 22px;
  border: 1px solid #e2e9f4;
  border-radius: 12px;
  background: #fff;
}
.card-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding-bottom: 18px;
  border-bottom: 1px solid #edf1f7;
}
.order-identification {
  min-width: 0;
  color: #8491a6;
}
time {
  font-size: 12px;
  line-height: 20px;
}
.order-identification p {
  overflow: hidden;
  margin: 5px 0 0;
  color: #a0acbd;
  font-size: 11px;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.order-status {
  flex-shrink: 0;
  padding: 4px 8px;
  border-radius: 5px;
  color: #2563eb;
  background: #edf4ff;
  font-size: 12px;
  line-height: 20px;
}
.status-0 {
  color: #c87c16;
  background: #fff6e6;
}
.status-1 {
  color: #38885c;
  background: #edf8f0;
}
.status-3 {
  color: #8491a6;
  background: #f1f4f8;
}
.card-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-block: 24px 18px;
}
.coupon-icon {
  display: grid;
  width: 76px;
  height: 76px;
  flex-shrink: 0;
  place-items: center;
  border-radius: 9px;
  color: #6f93d8;
  background: #eff6ff;
}
.coupon-icon svg {
  width: 34px;
  height: 34px;
}
.summary-text {
  flex: 1;
  min-width: 0;
}
h2 {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  margin: 0;
  font-size: 15px;
  font-weight: 500;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.coupon-offer {
  margin: 7px 0 0;
  color: #8491a6;
  font-size: 12px;
  line-height: 1.6;
}
.order-amount {
  flex-shrink: 0;
  color: var(--user-primary);
  font-size: 20px;
  font-weight: 500;
}
.order-times {
  display: grid;
  gap: 6px;
  margin: 0 0 20px;
  font-size: 12px;
  line-height: 1.6;
}
.order-times > div {
  display: flex;
  gap: 12px;
}
dt {
  flex-shrink: 0;
  color: #8491a6;
}
dd {
  margin: 0;
  color: #526580;
}
.card-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: auto;
  padding-top: 18px;
  border-top: 1px solid #edf1f7;
}
.card-footer > .el-button {
  height: 34px;
  margin: 0;
  padding: 8px 12px;
  border-radius: 7px;
  font-size: 12px;
}
@media (max-width: 600px) {
  .order-card {
    padding: 20px;
  }
  .card-summary {
    gap: 10px;
  }
  .coupon-icon {
    width: 64px;
    height: 64px;
  }
  .order-amount {
    font-size: 18px;
  }
}
</style>
