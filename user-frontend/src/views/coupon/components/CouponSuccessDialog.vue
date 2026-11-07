<template>
  <ElDialog
    v-model="visible"
    :title="result.kind === 'claim' ? '领取成功' : '支付成功'"
    width="480px"
    class="user-dialog"
    modal-class="user-dialog-overlay"
    align-center
    append-to-body
    destroy-on-close
  >
    <div class="coupon-success">
      <img
        :src="successImage"
        :alt="result.kind === 'claim' ? '领取成功插画' : '支付成功插画'"
        width="140"
        height="140"
        decoding="async"
      />
      <h2>{{ result.kind === 'claim' ? '领取成功' : '支付成功' }}</h2>
      <p>
        {{
          result.kind === 'claim'
            ? '满减券已领取，请在有效期内使用。'
            : '代金券已到账，下单时可使用。'
        }}
      </p>
      <div class="received-coupon">
        <h3>{{ result.name }}</h3>
        <p class="coupon-offer">{{ result.offer }}</p>
        <p v-if="result.kind === 'payment'" class="paid-amount">实付 {{ money(result.price!) }}</p>
        <p class="coupon-number">
          {{ result.kind === 'claim' ? '券号' : '购券订单号' }}：{{ result.number }}
        </p>
        <p>有效期至：{{ formatActivityTime(result.expireTime) }}</p>
      </div>
    </div>
    <template #footer>
      <ElButton @click="visible = false">{{ closeText }}</ElButton>
      <ElButton type="primary" @click="goToMenu">去点餐</ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElButton, ElDialog } from 'element-plus'
import { money } from '@/utils/pricing'
import successImage from '@/assets/images/status/payment-success.webp'
import { formatActivityTime } from '../couponDisplay'
import type { CouponReceipt } from '../couponOrders'

withDefaults(defineProps<{ result: CouponReceipt; closeText?: string }>(), {
  closeText: '继续领券',
})
const visible = defineModel<boolean>({ required: true })
const router = useRouter()
function goToMenu(): void {
  visible.value = false
  void router.push('/menu')
}
</script>

<style scoped>
.coupon-success {
  padding: 4px 0 12px;
  text-align: center;
}
.coupon-success img {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 0 auto 16px;
  border-radius: 50%;
  object-fit: contain;
}
.coupon-success h2 {
  margin: 0 0 12px;
  color: #354661;
  font-size: 23px;
}
.coupon-success p {
  color: #8491a6;
  font-size: 13px;
  line-height: 1.8;
}
.received-coupon {
  margin-top: 20px;
  padding: 18px;
  border: 1px solid var(--user-border);
  border-radius: 12px;
  background: var(--user-surface);
  overflow-wrap: anywhere;
}
.received-coupon h3 {
  margin: 0 0 10px;
  color: #354661;
  font-size: 17px;
}
.received-coupon .coupon-offer,
.received-coupon .paid-amount {
  color: var(--user-primary);
}
.received-coupon .paid-amount {
  font-size: 24px;
}
.coupon-number {
  font-size: 12px;
}
</style>
