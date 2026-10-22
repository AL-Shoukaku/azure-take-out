<template>
  <div class="order-actions">
    <ElButton
      v-if="order.status === 1 || order.status === 2"
      type="danger"
      link
      :disabled="busy"
      @click="emit('action', 'cancel')"
      >取消订单</ElButton
    >
    <ElButton :disabled="busy" @click="emit('action', 'repeat')">再来一单</ElButton>
    <ElButton
      v-if="order.status === 1"
      type="primary"
      :disabled="busy"
      @click="emit('action', 'pay')"
      >去支付</ElButton
    >
    <ElButton
      v-else-if="order.status === 2"
      type="primary"
      :disabled="busy"
      @click="emit('action', 'remind')"
      >催单</ElButton
    >
    <ElButton v-else type="primary" plain :disabled="busy" @click="emit('action', 'contact')"
      >联系商家</ElButton
    >
  </div>
</template>
<script setup lang="ts">
import { ElButton } from 'element-plus'
import type { Order } from '@/api/orders'
import type { OrderAction } from '../orderDisplay'
defineProps<{ order: Order; busy: boolean }>()
const emit = defineEmits<{ action: [action: OrderAction] }>()
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
.order-actions .el-button.is-link {
  padding-inline: 0;
}
</style>
