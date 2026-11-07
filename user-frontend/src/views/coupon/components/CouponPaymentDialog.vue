<template>
  <ElDialog
    v-model="visible"
    title="代金券支付"
    width="480px"
    class="user-dialog"
    modal-class="user-dialog-overlay"
    align-center
    append-to-body
    destroy-on-close
    :show-close="!busy"
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
  >
    <div class="payment-summary">
      <h2>{{ order.name }}</h2>
      <p>{{ order.offer }}</p>
      <p class="amount-label">待支付金额</p>
      <strong>{{ money(order.price) }}</strong>
      <p class="payment-number">购券订单号：{{ order.number }}</p>
      <p>支付截止：{{ formatActivityTime(order.payDeadline) }}</p>
      <p class="payment-countdown" role="timer">
        {{ seconds > 0 ? `剩余 ${countdown}` : '支付已截止' }}
      </p>
    </div>
    <ElAlert
      title="模拟支付"
      type="info"
      :closable="false"
      show-icon
      description="当前为演示支付，确认后代金券将发放至你的账户，不会产生实际扣款。"
    />
    <template #footer>
      <ElButton :disabled="busy" @click="visible = false">暂不支付</ElButton>
      <ElButton type="primary" :loading="busy" :disabled="busy || seconds <= 0" @click="pay"
        >确认支付</ElButton
      >
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { computed, onScopeDispose } from 'vue'
import { ElAlert, ElButton, ElDialog, ElMessage } from 'element-plus'
import { payCouponOrder, type CouponPaymentResult } from '@/api/coupons'
import { getToken } from '@/utils/auth'
import { money } from '@/utils/pricing'
import { formatActivityTime } from '../couponDisplay'
import { paymentSeconds, type CouponPaymentSummary } from '../couponOrders'

const props = defineProps<{ order: CouponPaymentSummary; now: number }>()
const visible = defineModel<boolean>({ required: true })
const busy = defineModel<boolean>('busy', { default: false })
const emit = defineEmits<{ paid: [result: CouponPaymentResult] }>()
const seconds = computed(() => paymentSeconds(props.order.payDeadline, props.now))
const countdown = computed(
  () => `${Math.floor(seconds.value / 60)}分${String(seconds.value % 60).padStart(2, '0')}秒`,
)
let active = true
onScopeDispose(() => {
  active = false
})

async function pay(): Promise<void> {
  if (busy.value || paymentSeconds(props.order.payDeadline, Date.now()) <= 0) return
  const token = getToken()
  busy.value = true
  try {
    const result = await payCouponOrder(props.order.number)
    if (!active || token !== getToken()) return
    emit('paid', result)
    visible.value = false
  } catch (cause) {
    if (active && token === getToken())
      ElMessage.error(cause instanceof Error ? cause.message : '支付失败，请稍后重试')
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.payment-summary {
  padding: 8px 0 24px;
  text-align: center;
}
.payment-summary h2 {
  margin: 0 0 8px;
  color: #354661;
  font-size: 18px;
  overflow-wrap: anywhere;
}
.payment-summary p {
  margin: 8px 0;
  color: #8491a6;
  font-size: 13px;
  line-height: 1.7;
}
.payment-summary .amount-label {
  margin-top: 24px;
}
.payment-summary strong {
  display: block;
  margin: 10px 0 20px;
  color: var(--user-primary);
  font-size: 36px;
  font-weight: 500;
}
.payment-number {
  overflow-wrap: anywhere;
}
.payment-summary .payment-countdown {
  color: var(--user-primary);
}
</style>
