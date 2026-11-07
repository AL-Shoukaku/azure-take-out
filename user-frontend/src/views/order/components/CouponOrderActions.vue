<template>
  <div class="order-actions">
    <ElButton type="primary" plain :disabled="busy" @click="emit('action', 'contact')"
      >联系商家</ElButton
    >
    <ElButton
      v-if="order.status === 0"
      type="primary"
      :disabled="busy"
      @click="emit('action', 'pay')"
      >去支付</ElButton
    >
  </div>
</template>
<script setup lang="ts">
import { ElButton } from 'element-plus'
import type { CouponOrderSummary } from '@/api/couponOrders'
import type { CouponOrderAction } from '../couponOrderDisplay'
defineProps<{ order: CouponOrderSummary; busy: boolean }>()
const emit = defineEmits<{ action: [action: CouponOrderAction] }>()
</script>
<style scoped>
.order-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.order-actions .el-button {
  height: 34px;
  margin: 0;
  padding: 8px 12px;
  border-radius: 7px;
  font-size: 12px;
}
</style>
