<template>
  <ElDialog
    v-model="visible"
    title="订单信息"
    class="admin-dialog order-detail-dialog"
    width="900px"
    top="7vh"
    append-to-body
    :show-close="!busy"
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
  >
    <div v-loading="loading" class="order-detail-scroll">
      <div v-if="error" class="management-load-error" role="alert">
        {{ error }} <ElButton @click="emit('retry')">重试详情</ElButton>
      </div>
      <template v-else-if="order">
        <div class="order-detail-top">
          <div>
            <span class="detail-label">订单号：</span><strong>{{ order.number }}</strong
            ><span
              class="order-state"
              :class="{ 'is-delivering': [3, 4].includes(order.status) }"
              >{{ statusNames[order.status] }}</span
            >
          </div>
          <p>下单时间：{{ formatTime(order.orderTime) }}</p>
        </div>
        <div class="order-customer">
          <div class="order-customer-grid">
            <div>
              <span class="detail-label">用户名：</span
              >{{ order.consignee || order.userName || '—' }}
            </div>
            <div><span class="detail-label">手机号：</span>{{ order.phone }}</div>
            <div v-if="[2, 3, 4, 5].includes(order.status)" class="full-width">
              <span class="detail-label">{{
                order.status === 5 ? '送达时间：' : '预计送达时间：'
              }}</span
              >{{
                formatTime(order.status === 5 ? order.deliveryTime : order.estimatedDeliveryTime)
              }}
            </div>
            <div class="full-width">
              <span class="detail-label">地址：</span>{{ order.address }}
            </div>
            <div class="full-width">
              <span class="detail-label">餐具数量：</span
              >{{ order.tablewareStatus === 1 ? '按餐量提供' : `${order.tablewareNumber}份` }}
            </div>
          </div>
          <div class="order-remark" :class="{ 'is-cancelled': order.status === 6 }">
            <span>{{ order.status === 6 ? '取消原因' : '备注' }}</span>
            <p>
              {{
                order.status === 6
                  ? order.cancelReason || order.rejectionReason || '—'
                  : order.remark || '—'
              }}
            </p>
          </div>
        </div>
        <div class="order-dish-section">
          <span class="detail-label">菜品</span>
          <div class="order-detail-items">
            <div v-for="item in order.orderDetailList" :key="item.id" class="order-detail-item">
              <div>
                <span>{{ item.name }}x{{ item.number }}</span
                ><small v-if="item.dishFlavor">{{ item.dishFlavor }}</small>
              </div>
              <span>{{ money(item.amount * item.number) }}</span>
            </div>
            <p class="order-dish-subtotal">
              <strong>菜品小计</strong>
              <span data-testid="dish-subtotal">{{ money(subtotal) }}</span>
            </p>
          </div>
        </div>
        <div class="order-fees">
          <span class="detail-label">费用</span>
          <div class="order-fee-grid">
            <div>菜品小计：{{ money(subtotal) }}</div>
            <div>派送费：{{ money(deliveryFee) }}</div>
            <div>打包费：{{ money(order.packAmount) }}</div>
            <div>
              合计：<span class="order-total" data-testid="order-total">{{
                money(order.amount)
              }}</span>
            </div>
            <div>支付渠道：{{ order.payMethod === 1 ? '微信支付' : '支付宝支付' }}</div>
            <div>支付时间：{{ formatTime(order.checkoutTime) }}</div>
            <div>支付状态：{{ ['待支付', '已支付', '已退款'][order.payStatus] }}</div>
          </div>
        </div>
      </template>
    </div>
    <template #footer>
      <div class="order-detail-footer">
        <ElCheckbox v-if="showAutoNext && order?.status === 2" v-model="autoNext" :disabled="busy"
          >处理完自动跳转下一条</ElCheckbox
        >
        <div class="order-detail-footer-actions">
          <ElButton :disabled="busy" @click="visible = false">返回</ElButton
          ><template v-if="order && !error && !loading"
            ><ElButton
              v-if="secondaryAction(order.status)"
              :disabled="busy"
              @click="emit('action', secondaryAction(order.status)!.action)"
              >{{ secondaryAction(order.status)!.label }}</ElButton
            ><ElButton
              v-if="primaryActions[order.status]"
              type="primary"
              :disabled="busy"
              :loading="busy"
              @click="emit('action', primaryActions[order.status]!.action)"
              >{{ primaryActions[order.status]!.label }}</ElButton
            ></template
          >
        </div>
      </div>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElButton, ElCheckbox, ElDialog, vLoading } from 'element-plus'
import type { OrderDetail } from '@/api/order'
import {
  deliveryFee,
  formatTime,
  money,
  primaryActions,
  secondaryAction,
  statusNames,
  type OrderAction,
} from './orderDisplay'

const props = defineProps<{
  order: OrderDetail | null
  loading: boolean
  error: string
  busy: boolean
  showAutoNext: boolean
}>()
const emit = defineEmits<{ retry: []; action: [action: OrderAction] }>()
const visible = defineModel<boolean>({ required: true })
const autoNext = defineModel<boolean>('autoNext', { required: true })
const subtotal = computed(() =>
  props.order
    ? (Math.round(props.order.amount * 100) -
        deliveryFee * 100 -
        Math.round(props.order.packAmount * 100)) /
      100
    : 0,
)
</script>

<style>
.order-detail-dialog .el-dialog__body {
  padding: 25px 30px 0;
}
.order-detail-scroll {
  min-height: 180px;
  max-height: calc(86vh - 175px);
  overflow: auto;
  padding-right: 8px;
}
.order-detail-top {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 22px 26px;
  border-bottom: 1px solid #e7e6e6;
}
.order-detail-top strong {
  font-size: 16px;
  overflow-wrap: anywhere;
}
.order-detail-top p {
  margin: 0;
}
.order-state {
  display: inline-block;
  margin-left: 18px;
  padding: 4px 8px;
  border-radius: 14px;
  background: #333;
  color: white;
  white-space: nowrap;
}
.order-state.is-delivering {
  background: #f56c6c;
}
.detail-label {
  color: #818693;
}
.order-customer {
  margin-top: 23px;
  padding: 20px 40px;
  background: var(--admin-surface-soft);
}
.order-customer-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 14px;
}
.order-customer-grid .full-width {
  grid-column: 1 / -1;
  overflow-wrap: anywhere;
}
.order-remark {
  display: flex;
  align-items: center;
  gap: 25px;
  margin-top: 14px;
  padding: 6px;
  border: 1px solid var(--admin-border);
  border-radius: 4px;
  background: var(--admin-surface-soft);
}
.order-remark > span {
  flex-shrink: 0;
  padding: 6px 12px;
  border-radius: 4px;
  background: var(--admin-border);
}
.order-remark p {
  margin: 0;
  color: var(--admin-primary);
  overflow-wrap: anywhere;
}
.order-remark.is-cancelled {
  border-color: #b6b6b6;
  background: var(--admin-surface);
}
.order-remark.is-cancelled > span {
  background: #e5e4e4;
}
.order-remark.is-cancelled p {
  color: #f56c6c;
}
.order-dish-section,
.order-fees {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr);
  padding: 22px 40px;
  gap: 0;
}
.order-detail-items {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px 30px;
}
.order-detail-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}
.order-detail-item small {
  display: block;
  margin-top: 4px;
  color: #999;
}
.order-detail-item > span {
  white-space: nowrap;
}
.order-dish-subtotal {
  grid-column: 1 / -1;
  margin: 8px 0 0;
}
.order-dish-subtotal > span,
.order-total {
  color: #f56c6c;
}
.order-fees {
  border-top: 1px solid #e7e6e6;
}
.order-fee-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 30px;
}
.order-detail-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.order-detail-footer-actions {
  margin-left: auto;
}
@media (max-width: 900px) {
  .order-customer {
    padding: 20px;
  }
  .order-dish-section,
  .order-fees {
    padding: 22px 20px;
  }
}
</style>
