<template>
  <ElDialog
    v-model="visible"
    title="订单详情"
    width="780px"
    class="user-dialog order-detail-dialog"
    modal-class="user-dialog-overlay"
    align-center
    append-to-body
    destroy-on-close
    :show-close="!busy"
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
  >
    <ElSkeleton v-if="loading" :rows="10" animated />
    <ElAlert v-else-if="error" title="订单详情加载失败" type="error" :closable="false" show-icon>
      <p>{{ error }}</p>
      <ElButton @click="emit('retry')">重新加载</ElButton>
    </ElAlert>
    <template v-else-if="order">
      <div class="status-banner" :class="{ cancelled: order.status === 6 }">
        <svg viewBox="0 0 32 32" fill="none" aria-hidden="true">
          <circle cx="16" cy="16" r="12" stroke="currentColor" stroke-width="1.7" />
          <path
            v-if="order.status === 5"
            d="m10 16 4 4 8-9"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <path
            v-else-if="order.status === 6"
            d="m12 12 8 8m0-8-8 8"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
          />
          <path
            v-else
            d="M16 8v8l6 4"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
          />
        </svg>
        <div>
          <h2>{{ statusNames[order.status] }}</h2>
          <p>{{ statusDescriptions[order.status] }}</p>
        </div>
      </div>
      <ol v-if="order.status !== 6" class="order-progress" aria-label="订单进度">
        <li
          v-for="step in progressSteps"
          :key="step"
          :class="{ reached: step <= order.status }"
          :aria-current="step === order.status ? 'step' : undefined"
        >
          <span class="step-number">{{ step < order.status ? '✓' : step }}</span
          ><span>{{ statusNames[step] }}</span>
        </li>
      </ol>
      <section class="detail-items" aria-label="订单商品">
        <div v-for="item in order.orderDetailList" :key="item.id" class="detail-item">
          <div>
            <h3>{{ item.name }}</h3>
            <p>
              {{ item.dishFlavor ? `${item.dishFlavor} × ${item.number}` : `× ${item.number}` }}
            </p>
          </div>
          <span>{{ money(itemTotal(item)) }}</span>
        </div>
        <div class="fee-row">
          <span>打包费</span><span>{{ money(order.packAmount) }}</span>
        </div>
        <div class="fee-row">
          <span>配送费</span><span>{{ money(deliveryFee) }}</span>
        </div>
        <div class="total-row">
          <strong>订单合计</strong
          ><strong data-testid="order-total">{{ money(order.amount) }}</strong>
        </div>
      </section>
      <dl class="order-information">
        <div>
          <dt>收货信息</dt>
          <dd>
            {{ order.consignee }} · {{ order.phone }}
            <p>{{ order.address }}</p>
          </dd>
        </div>
        <div>
          <dt>配送时间</dt>
          <dd>
            {{ order.deliveryStatus === 1 ? '立即送出' : formatTime(order.estimatedDeliveryTime) }}
          </dd>
        </div>
        <div v-if="order.estimatedDeliveryTime">
          <dt>预计送达</dt>
          <dd>{{ formatTime(order.estimatedDeliveryTime) }}</dd>
        </div>
        <div v-if="order.deliveryTime">
          <dt>送达时间</dt>
          <dd>{{ formatTime(order.deliveryTime) }}</dd>
        </div>
        <div>
          <dt>餐具数量</dt>
          <dd>{{ order.tablewareStatus === 1 ? '按餐量提供' : `${order.tablewareNumber} 份` }}</dd>
        </div>
        <div>
          <dt>订单备注</dt>
          <dd>{{ order.remark || '无' }}</dd>
        </div>
        <div>
          <dt>订单编号</dt>
          <dd>{{ order.number }}</dd>
        </div>
        <div>
          <dt>下单时间</dt>
          <dd>{{ formatTime(order.orderTime) }}</dd>
        </div>
        <div>
          <dt>支付状态</dt>
          <dd>{{ ['未支付', '已支付', '已退款'][order.payStatus] }}</dd>
        </div>
        <div v-if="order.checkoutTime">
          <dt>支付时间</dt>
          <dd>{{ formatTime(order.checkoutTime) }}</dd>
        </div>
        <template v-if="order.status === 6">
          <div>
            <dt>取消原因</dt>
            <dd>{{ order.cancelReason || order.rejectionReason || '未填写' }}</dd>
          </div>
          <div>
            <dt>取消时间</dt>
            <dd>{{ formatTime(order.cancelTime) }}</dd>
          </div>
        </template>
      </dl>
    </template>
    <template #footer>
      <OrderActions
        v-if="order && !loading && !error"
        :order="order"
        :busy="busy"
        @action="emit('action', $event)"
      />
      <ElButton v-else :disabled="busy" @click="visible = false">关闭</ElButton>
    </template>
  </ElDialog>
</template>
<script setup lang="ts">
import { ElAlert, ElButton, ElDialog, ElSkeleton } from 'element-plus'
import type { Order, OrderStatus } from '@/api/orders'
import {
  deliveryFee,
  formatTime,
  itemTotal,
  money,
  statusDescriptions,
  statusNames,
  type OrderAction,
} from '../orderDisplay'
import OrderActions from './OrderActions.vue'
defineProps<{ order: Order | null; loading: boolean; error: string; busy: boolean }>()
const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ retry: []; action: [action: OrderAction] }>()
const progressSteps: OrderStatus[] = [1, 2, 3, 4, 5]
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
.status-banner.cancelled {
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
.order-progress {
  display: flex;
  margin: 30px 0;
  padding: 0;
  list-style: none;
}
.order-progress li {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  gap: 12px;
  color: #a8b4c5;
  font-size: 12px;
}
.order-progress li:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 15px;
  left: calc(50% + 21px);
  right: calc(-50% + 21px);
  height: 1px;
  background: #e1e8f3;
}
.step-number {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border: 1px solid #dce5f2;
  border-radius: 50%;
  font-size: 14px;
}
.reached {
  color: var(--user-primary) !important;
}
.reached .step-number {
  border-color: #b9cdf5;
  background: #edf4ff;
}
.detail-items {
  padding-block: 8px 24px;
  border-bottom: 1px solid #e3eaf5;
}
.detail-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-bottom: 24px;
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
.detail-item > span {
  flex-shrink: 0;
}
.fee-row,
.total-row {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  margin-top: 16px;
}
.fee-row {
  color: #8491a6;
  font-size: 14px;
}
.total-row {
  margin-top: 22px;
  font-size: 16px;
}
.order-information {
  display: grid;
  gap: 20px;
  margin: 28px 0 8px;
}
.order-information > div {
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
dd p {
  margin: 6px 0 0;
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
  .order-information > div {
    grid-template-columns: 70px minmax(0, 1fr);
    gap: 10px;
  }
  .order-progress li {
    font-size: 11px;
  }
}
</style>
