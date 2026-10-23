<template>
  <ElDialog
    v-model="visible"
    title="订单支付"
    width="460px"
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
      <p>待支付金额</p>
      <strong>{{ money(order.amount) }}</strong>
      <p class="payment-number">订单号：{{ order.number }}</p>
    </div>
    <ElAlert
      title="模拟支付"
      type="info"
      :closable="false"
      show-icon
      description="当前为演示支付，确认后订单将进入待接单状态，不会产生实际扣款。"
    />
    <template #footer>
      <ElButton :disabled="busy" @click="visible = false">暂不支付</ElButton>
      <ElButton type="primary" :loading="busy" @click="pay">确认支付</ElButton>
    </template>
  </ElDialog>
</template>
<script setup lang="ts">
import { onScopeDispose } from 'vue'
import { ElAlert, ElButton, ElDialog, ElMessage } from 'element-plus'
import { payOrder, type Order } from '@/api/orders'
import { getToken } from '@/utils/auth'

const props = defineProps<{ order: Pick<Order, 'id' | 'number' | 'amount'> }>()
const visible = defineModel<boolean>({ required: true })
const busy = defineModel<boolean>('busy', { default: false })
const emit = defineEmits<{ paid: [id: number] }>()
let active = true
onScopeDispose(() => {
  active = false
})
function money(value: number): string {
  return `¥${value.toFixed(2)}`
}
async function pay(): Promise<void> {
  if (busy.value) return
  const token = getToken()
  busy.value = true
  try {
    await payOrder(props.order.number)
    if (!active || token !== getToken()) return
    emit('paid', props.order.id)
    visible.value = false
  } catch (error) {
    if (active && token === getToken())
      ElMessage.error(error instanceof Error ? error.message : '支付失败，请稍后重试')
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
.payment-summary p {
  margin: 0;
  color: #8491a6;
  font-size: 14px;
}
.payment-summary strong {
  display: block;
  margin: 14px 0 20px;
  color: var(--user-primary);
  font-size: 36px;
  font-weight: 500;
}
.payment-number {
  overflow-wrap: anywhere;
  line-height: 1.7;
}
</style>
