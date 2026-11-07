<template>
  <article class="order-card" :aria-label="`优惠券 ${coupon.name}`">
    <div class="card-heading">
      <div class="coupon-identification">
        <time>{{ formatTime(coupon.createTime) }}</time>
        <p :title="coupon.number">券号：{{ coupon.number }}</p>
      </div>
      <span class="coupon-status" :class="`status-${coupon.status}`">{{
        userCouponStatusNames[coupon.status]
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
        <h2 :title="coupon.name">{{ coupon.name }}</h2>
        <p class="coupon-offer">{{ couponOffer(coupon) }}</p>
      </div>
    </div>
    <dl class="coupon-times">
      <div v-for="row in userCouponTimes(coupon)" :key="row.label">
        <dt>{{ row.label }}</dt>
        <dd>{{ row.value }}</dd>
      </div>
    </dl>
    <div class="card-footer">
      <ElButton :disabled="busy" @click="emit('detail')">查看详情</ElButton>
    </div>
  </article>
</template>
<script setup lang="ts">
import { ElButton } from 'element-plus'
import type { UserCoupon } from '@/api/userCoupons'
import { couponOffer } from '../couponDisplay'
import { userCouponStatusNames, userCouponTimes } from '../myCouponDisplay'
import { formatTime } from '@/views/order/orderDisplay'
defineProps<{ coupon: UserCoupon; busy: boolean }>()
const emit = defineEmits<{ detail: [] }>()
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
.coupon-identification {
  min-width: 0;
  color: #8491a6;
}
time {
  font-size: 12px;
  line-height: 20px;
}
.coupon-identification p {
  overflow: hidden;
  margin: 5px 0 0;
  color: #a0acbd;
  font-size: 11px;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.coupon-status {
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
.status-2 {
  color: #38885c;
  background: #edf8f0;
}
.status-3,
.status-4 {
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
.coupon-times {
  display: grid;
  gap: 6px;
  margin: 0 0 20px;
  font-size: 12px;
  line-height: 1.6;
}
.coupon-times > div {
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
.card-footer .el-button {
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
}
</style>
