<template>
  <ElDialog
    v-model="visible"
    title="优惠券详情"
    width="560px"
    class="user-dialog my-coupon-detail-dialog"
    modal-class="user-dialog-overlay"
    align-center
    append-to-body
    destroy-on-close
  >
    <ElSkeleton v-if="loading" :rows="8" animated />
    <ElAlert v-else-if="error" title="优惠券详情加载失败" type="error" :closable="false" show-icon>
      <p>{{ error }}</p>
      <ElButton @click="emit('retry')">重新加载</ElButton>
    </ElAlert>
    <template v-else-if="coupon">
      <div class="status-banner" :class="{ ended: coupon.status === 3 || coupon.status === 4 }">
        <svg viewBox="0 0 32 32" fill="none" aria-hidden="true">
          <circle cx="16" cy="16" r="12" stroke="currentColor" stroke-width="1.7" />
          <path
            v-if="coupon.status === 0"
            d="m10 16 4 4 8-9"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <path
            v-else-if="coupon.status === 1"
            d="M16 8v8l6 4"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
          />
          <path
            v-else-if="coupon.status === 2"
            d="m12 12 8 8m0-8-8 8"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
          />
          <path
            v-else-if="coupon.status === 3"
            d="M16 8v8l6 4"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
          />
          <path
            v-else
            d="m13 12-3 3 3 3m-3-3h8a3.5 3.5 0 0 0 0-7h-1"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
        <div>
          <h2>{{ userCouponStatusNames[coupon.status] }}</h2>
          <p>{{ userCouponStatusDescriptions[coupon.status] }}</p>
        </div>
      </div>
      <section class="detail-items" aria-label="优惠券信息">
        <div class="detail-item">
          <div>
            <h3>{{ coupon.name }}</h3>
            <p>{{ couponOffer(coupon) }}</p>
          </div>
          <span class="coupon-type">{{ coupon.type === 1 ? '代金券' : '满减券' }}</span>
        </div>
      </section>
      <dl class="coupon-information">
        <div>
          <dt>券号</dt>
          <dd>{{ coupon.number }}</dd>
        </div>
        <div>
          <dt>获得时间</dt>
          <dd>{{ formatTime(coupon.createTime) }}</dd>
        </div>
        <div>
          <dt>有效期至</dt>
          <dd>{{ formatTime(coupon.expireTime) }}</dd>
        </div>
        <div v-if="coupon.status === 2">
          <dt>核销时间</dt>
          <dd>{{ formatTime(coupon.useTime) }}</dd>
        </div>
        <div v-if="coupon.status === 4">
          <dt>退款时间</dt>
          <dd>{{ formatTime(coupon.refundTime) }}</dd>
        </div>
      </dl>
    </template>
    <template #footer>
      <ElButton :disabled="loading" @click="visible = false">关闭</ElButton>
    </template>
  </ElDialog>
</template>
<script setup lang="ts">
import { ElAlert, ElButton, ElDialog, ElSkeleton } from 'element-plus'
import type { UserCoupon } from '@/api/userCoupons'
import { couponOffer } from '../couponDisplay'
import { userCouponStatusDescriptions, userCouponStatusNames } from '../myCouponDisplay'
import { formatTime } from '@/views/order/orderDisplay'
defineProps<{ coupon: UserCoupon | null; loading: boolean; error: string }>()
const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ retry: [] }>()
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
  padding-block: 30px 8px;
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
.coupon-type {
  flex-shrink: 0;
  padding: 4px 9px;
  border-radius: 5px;
  color: var(--user-primary);
  background: #eff6ff;
  font-size: 12px;
  line-height: 20px;
}
.coupon-information {
  display: grid;
  gap: 20px;
  margin: 24px 0 8px;
  padding-bottom: 16px;
}
.coupon-information > div {
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
  .coupon-information > div {
    grid-template-columns: 70px minmax(0, 1fr);
    gap: 10px;
  }
}
</style>
