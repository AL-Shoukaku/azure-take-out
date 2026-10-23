<template>
  <ElDialog
    v-model="visible"
    title="支付成功"
    width="480px"
    class="user-dialog success-dialog"
    modal-class="user-dialog-overlay"
    align-center
    append-to-body
    destroy-on-close
  >
    <div class="payment-success">
      <img :src="successImage" alt="支付成功插画" width="160" height="160" decoding="async" />
      <h2>支付成功</h2>
      <p>一份热乎正在准备中，<br />请耐心等待商家接单。</p>
      <strong>{{ money(order.amount) }}</strong>
      <p class="success-order">订单号：{{ order.number }}</p>
    </div>
    <template #footer>
      <ElButton @click="navigate('/menu')">继续点餐</ElButton>
      <ElButton type="primary" @click="navigate('/orders')">查看订单</ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElButton, ElDialog } from 'element-plus'
import type { Order } from '@/api/orders'
import { money } from '@/utils/pricing'
import successImage from '@/assets/images/status/payment-success.webp'

defineProps<{ order: Pick<Order, 'number' | 'amount'> }>()
const visible = defineModel<boolean>({ required: true })
const router = useRouter()

function navigate(path: string): void {
  visible.value = false
  void router.push(path)
}
</script>

<style scoped>
.payment-success {
  padding: 4px 0 12px;
  text-align: center;
}

.payment-success img {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 0 auto 16px;
  border-radius: 50%;
  object-fit: contain;
}

.payment-success h2 {
  margin: 0 0 12px;
  color: #354661;
  font-size: 23px;
}

.payment-success p {
  color: #91a0b6;
  font-size: 14px;
  line-height: 1.9;
}

.payment-success strong {
  display: block;
  margin-top: 22px;
  color: var(--user-primary);
  font-size: 30px;
  font-weight: 500;
}

.payment-success .success-order {
  margin-bottom: 0;
  overflow-wrap: anywhere;
  font-size: 12px;
}
</style>
