<template>
  <article class="order-card" :aria-label="`订单 ${order.number}`">
    <div class="card-heading">
      <div class="order-identification">
        <time>{{ formatTime(order.orderTime) }}</time>
        <p :title="order.number">订单号：{{ order.number }}</p>
      </div>
      <span class="order-status" :class="`status-${order.status}`">{{
        statusNames[order.status]
      }}</span>
    </div>
    <div class="card-summary">
      <DishImage
        :src="order.orderDetailList[0]?.image ?? null"
        :name="order.orderDetailList[0]?.name || '订单菜品'"
      />
      <div class="summary-text">
        <h2 :title="dishNames">{{ dishNames }}</h2>
        <p class="item-count">共 {{ itemCount(order) }} 件</p>
        <p class="address" :title="address">{{ address }}</p>
      </div>
      <strong class="order-amount">{{ money(order.amount) }}</strong>
    </div>
    <div class="card-footer">
      <ElButton type="primary" link :disabled="busy" @click="emit('detail')">
        订单详情 <span class="detail-arrow" aria-hidden="true">↗</span>
      </ElButton>
      <OrderActions :order="order" :busy="busy" @action="emit('action', $event)" />
    </div>
  </article>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { ElButton } from 'element-plus'
import type { Order } from '@/api/orders'
import { formatTime, itemCount, money, statusNames, type OrderAction } from '../orderDisplay'
import DishImage from './DishImage.vue'
import OrderActions from './OrderActions.vue'
const props = defineProps<{ order: Order; address: string; busy: boolean }>()
const emit = defineEmits<{ detail: []; action: [action: OrderAction] }>()
const dishNames = computed(() => {
  const items = props.order.orderDetailList
  return (
    items
      .slice(0, 2)
      .map((item) => item.name)
      .join('、') + (items.length > 2 ? '等' : '') || '订单商品'
  )
})
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
.status-1 {
  color: #c87c16;
  background: #fff6e6;
}
.status-5 {
  color: #38885c;
  background: #edf8f0;
}
.status-6 {
  color: #8491a6;
  background: #f1f4f8;
}
.card-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-block: 24px;
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
.item-count,
.address {
  margin: 7px 0 0;
  color: #8491a6;
  font-size: 12px;
  line-height: 1.6;
}
.address {
  overflow: hidden;
  margin-top: 2px;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.order-amount {
  flex-shrink: 0;
  color: var(--user-primary);
  font-size: 20px;
  font-weight: 500;
}
.card-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px 12px;
  margin-top: auto;
  padding-top: 18px;
  border-top: 1px solid #edf1f7;
}
.card-footer > .el-button {
  margin: 0;
  font-size: 12px;
}
.card-footer .order-actions {
  margin-left: auto;
}
.detail-arrow {
  margin-left: 4px;
}
@media (max-width: 600px) {
  .order-card {
    padding: 20px;
  }
  .card-summary {
    gap: 10px;
  }
  .order-amount {
    font-size: 18px;
  }
}
</style>
