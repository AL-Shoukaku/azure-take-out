<template>
  <div class="dashboard-page">
    <DashboardCard
      title="今日数据"
      :date="today"
      :loading="business.state.loading"
      :error="business.state.error"
      :more="{ label: '详细数据', to: '/statistics' }"
      @retry="business.reload"
    >
      <div v-if="business.state.data" class="today-metrics">
        <div v-for="metric in businessMetrics" :key="metric.label" class="today-metric">
          <p>{{ metric.label }}</p>
          <strong :aria-label="metric.label">{{ metric.value }}</strong>
        </div>
      </div>
    </DashboardCard>
    <DashboardCard
      title="订单管理"
      :date="today"
      :loading="orders.state.loading"
      :error="orders.state.error"
      :more="{ label: '订单明细', to: '/order' }"
      @retry="orders.reload"
    >
      <div v-if="orders.state.data" class="order-overview">
        <RouterLink
          v-for="item in orderMetrics"
          :key="item.label"
          :to="item.path"
          class="order-metric"
        >
          <img :src="item.icon" alt="" aria-hidden="true" /><span>{{ item.label }}</span
          ><strong
            :class="{ 'needs-action': item.pending }"
            :aria-label="`今日${item.label}数量`"
            >{{ item.value }}</strong
          >
        </RouterLink>
      </div>
    </DashboardCard>
    <div class="product-overviews">
      <ProductOverviewCard
        kind="dish"
        :data="dishes.state.data"
        :loading="dishes.state.loading"
        :error="dishes.state.error"
        @retry="dishes.reload"
      />
      <ProductOverviewCard
        kind="setmeal"
        :data="setmeals.state.data"
        :loading="setmeals.state.loading"
        :error="setmeals.state.error"
        @retry="setmeals.reload"
      />
    </div>
    <OrderListPanel embedded @changed="refreshOrders" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  getBusinessData,
  getOrderOverview,
  getDishOverview,
  getSetmealOverview,
} from '@/api/workspace'
import { useAsyncData } from '@/composables/useAsyncData'
import { useOrderEventsStore } from '@/stores/orderEvents'
import OrderListPanel from '@/views/order/OrderListPanel.vue'
import DashboardCard from './DashboardCard.vue'
import ProductOverviewCard from './ProductOverviewCard.vue'
import waitingIcon from '@/assets/images/dashboard/waiting.png'
import deliveryIcon from '@/assets/images/dashboard/delivery.png'
import completedIcon from '@/assets/images/dashboard/completed.png'
import cancelledIcon from '@/assets/images/dashboard/cancelled.png'
import allIcon from '@/assets/images/dashboard/all.png'

function todayDate() {
  const now = new Date()
  return `${now.getFullYear()}.${String(now.getMonth() + 1).padStart(2, '0')}.${String(now.getDate()).padStart(2, '0')}`
}
const today = ref(todayDate())
const business = useAsyncData(getBusinessData)
const orders = useAsyncData(getOrderOverview)
const dishes = useAsyncData(getDishOverview)
const setmeals = useAsyncData(getSetmealOverview)
const events = useOrderEventsStore()
const businessMetrics = computed(() => {
  const data = business.state.data
  return data
    ? [
        { label: '营业额', value: `¥ ${data.turnover.toFixed(2)}` },
        { label: '有效订单', value: data.validOrderCount },
        { label: '订单完成率', value: `${(data.orderCompletionRate * 100).toFixed(0)}%` },
        { label: '平均客单价', value: `¥ ${data.unitPrice.toFixed(2)}` },
        { label: '新增用户', value: data.newUsers },
      ]
    : []
})
const orderMetrics = computed(() => {
  const data = orders.state.data
  return data
    ? [
        {
          label: '待接单',
          value: data.waitingOrders,
          icon: waitingIcon,
          path: '/order?status=2',
          pending: true,
        },
        {
          label: '待派送',
          value: data.deliveredOrders,
          icon: deliveryIcon,
          path: '/order?status=3',
          pending: true,
        },
        {
          label: '已完成',
          value: data.completedOrders,
          icon: completedIcon,
          path: '/order?status=5',
          pending: false,
        },
        {
          label: '已取消',
          value: data.cancelledOrders,
          icon: cancelledIcon,
          path: '/order?status=6',
          pending: false,
        },
        { label: '全部订单', value: data.allOrders, icon: allIcon, path: '/order', pending: false },
      ]
    : []
})
function refreshOrders() {
  today.value = todayDate()
  void business.reload()
  void orders.reload()
}
watch(() => events.revision, refreshOrders)
</script>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.today-metrics,
.order-overview {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 20px;
}
.today-metric {
  min-width: 0;
  min-height: 112px;
  padding: 20px;
  border-radius: 4px;
  background: #fffbf0;
}
.today-metric p {
  margin: 0 0 15px;
  font-size: 14px;
}
.today-metric strong {
  font-size: 28px;
  overflow-wrap: anywhere;
}
.order-metric {
  display: flex;
  min-width: 0;
  min-height: 80px;
  align-items: center;
  gap: 5px;
  padding: 20px;
  border-radius: 4px;
  background: #fffbf0;
  color: #333;
  text-decoration: none;
  font-size: 14px;
}
.order-metric:hover {
  background: #fff5d9;
}
.order-metric img {
  width: 28px;
  height: 28px;
  object-fit: contain;
}
.order-metric span {
  white-space: nowrap;
}
.order-metric strong {
  margin-left: auto;
  font-size: 28px;
}
.order-metric .needs-action {
  color: #fd3333;
}
.product-overviews {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}
@media (max-width: 1250px) {
  .today-metrics,
  .order-overview {
    gap: 12px;
  }
  .today-metric {
    padding: 16px;
  }
  .today-metric strong {
    font-size: 24px;
  }
  .order-metric {
    flex-wrap: wrap;
    padding: 14px;
  }
  .order-metric strong {
    width: 100%;
    margin-left: 0;
    font-size: 24px;
  }
}
@media (max-width: 1000px) {
  .product-overviews {
    grid-template-columns: minmax(0, 1fr);
  }
  .today-metrics,
  .order-overview {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
