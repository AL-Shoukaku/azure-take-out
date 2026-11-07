<template>
  <section class="order-page" aria-label="优惠券订单">
    <div class="order-heading">
      <div class="order-tabs" role="tablist" aria-label="优惠券订单状态">
        <button
          v-for="tab in couponOrderTabs"
          :id="`coupon-order-tab-${tab.key}`"
          :key="tab.key"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab.key"
          aria-controls="coupon-order-list"
          :disabled="busy"
          :class="{ 'is-active': activeTab === tab.key }"
          @click="selectTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>
      <ElButton
        type="primary"
        class="order-switch"
        :disabled="busy"
        @click="router.push('/order/takeout')"
        >查看外卖订单</ElButton
      >
    </div>
    <div
      id="coupon-order-list"
      class="management-list"
      role="tabpanel"
      :aria-labelledby="`coupon-order-tab-${activeTab}`"
    >
      <form class="management-search order-search" @submit.prevent="search">
        <label for="coupon-order-number-query">订单号：</label
        ><ElInput
          id="coupon-order-number-query"
          v-model="draft.number"
          placeholder="请填写订单号"
          clearable
          :disabled="busy"
          @clear="search"
        />
        <label for="coupon-order-username-query">用户名：</label
        ><ElInput
          id="coupon-order-username-query"
          v-model="draft.username"
          placeholder="请填写用户名"
          clearable
          :disabled="busy"
          @clear="search"
        />
        <label>下单时间：</label
        ><ElDatePicker
          v-model="draft.dates"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :disabled="busy"
          clearable
          @clear="search"
        />
        <ElButton native-type="submit" class="management-dark-button" :disabled="busy"
          >查询</ElButton
        >
      </form>
      <div v-if="listError" class="management-load-error" role="alert">
        {{ listError }} <ElButton @click="loadOrders">重试列表</ElButton>
      </div>
      <ElTable
        v-else
        v-loading="loading"
        :data="orders"
        row-key="id"
        class="management-table order-table"
        stripe
        empty-text="暂无数据"
      >
        <!-- @vue-generic {CouponOrderSummary} -->
        <ElTableColumn
          v-for="column in columns"
          :key="column.key"
          :label="column.label"
          :min-width="column.width"
          ><template #default="{ row }">{{ column.value(row) }}</template></ElTableColumn
        >
        <!-- @vue-generic {CouponOrderSummary} -->
        <ElTableColumn label="操作" width="150" align="center" fixed="right"
          ><template #default="{ row }"
            ><div class="management-actions">
              <template v-if="row.status === 1"
                ><ElButton
                  link
                  class="management-action-red"
                  :disabled="busy || loading"
                  @click="requestRefund(row)"
                  >退款</ElButton
                ><span class="management-action-divider">|</span></template
              ><ElButton
                link
                class="management-action-blue"
                :disabled="busy"
                @click="openDetail(row.id)"
                >查看</ElButton
              >
            </div></template
          ></ElTableColumn
        >
      </ElTable>
      <ManagementPagination
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="total"
        :disabled="busy"
      />
    </div>
    <CouponOrderDetailDialog
      v-model="detailVisible"
      :order="detail"
      :loading="detailLoading"
      :error="detailError"
      :busy="busy"
      @retry="openDetail(detailId!)"
      @refund="detail && requestRefund(detail)"
    />
    <CouponRefundDialog v-model="refundVisible" :busy="busy" @confirm="executeRefund" />
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import {
  ElButton,
  ElDatePicker,
  ElInput,
  ElMessage,
  ElTable,
  ElTableColumn,
  vLoading,
} from 'element-plus'
import {
  getCouponOrderDetail,
  getCouponOrderPage,
  refundCouponOrder,
  type CouponOrderDetail,
  type CouponOrderPageQuery,
  type CouponOrderSummary,
} from '@/api/couponOrder'
import ManagementPagination from '@/components/ManagementPagination.vue'
import CouponOrderDetailDialog from './CouponOrderDetailDialog.vue'
import CouponRefundDialog from './CouponRefundDialog.vue'
import { couponOrderColumns, couponOrderTabs, type CouponOrderTab } from './couponOrderDisplay'
import '@/assets/styles/management.css'

const route = useRoute(),
  router = useRouter()
function tabFromQuery(): CouponOrderTab {
  return (
    couponOrderTabs.find(
      (tab) => tab.status !== undefined && String(tab.status) === route.query.status,
    )?.key ?? 'all'
  )
}
const activeTab = ref<CouponOrderTab>(tabFromQuery())
const columns = computed(() => couponOrderColumns(activeTab.value))
const page = ref(1),
  pageSize = ref(10),
  total = ref(0)
const draft = reactive<{ number: string; username: string; dates: [string, string] | null }>({
  number: '',
  username: '',
  dates: null,
})
const filters = ref<Omit<CouponOrderPageQuery, 'page' | 'pageSize' | 'status'>>({})
const orders = ref<CouponOrderSummary[]>([])
const loading = ref(false),
  listError = ref(''),
  busy = ref(false)
const detailVisible = ref(false),
  detail = ref<CouponOrderDetail | null>(null),
  detailId = ref<number>()
const detailLoading = ref(false),
  detailError = ref('')
const refundVisible = ref(false),
  refundOrder = ref<CouponOrderSummary>()
let listRequest = 0,
  detailRequest = 0,
  disposed = false

async function loadOrders() {
  const id = ++listRequest
  loading.value = true
  listError.value = ''
  try {
    const result = await getCouponOrderPage({
      page: page.value,
      pageSize: pageSize.value,
      status: couponOrderTabs.find((tab) => tab.key === activeTab.value)!.status,
      ...filters.value,
    })
    if (id !== listRequest || disposed) return
    total.value = result.total
    const lastPage = Math.max(1, Math.ceil(result.total / pageSize.value))
    if (page.value > lastPage) {
      page.value = lastPage
      return
    }
    orders.value = result.records
  } catch (error) {
    if (id === listRequest && !disposed) listError.value = (error as Error).message
  } finally {
    if (id === listRequest && !disposed) loading.value = false
  }
}
function search() {
  page.value = 1
  filters.value = {
    number: draft.number.trim() || undefined,
    username: draft.username.trim() || undefined,
    beginTime: draft.dates?.[0] ? `${draft.dates[0]} 00:00:00` : undefined,
    endTime: draft.dates?.[1] ? `${draft.dates[1]} 23:59:59` : undefined,
  }
}
function resetTab(tab: CouponOrderTab) {
  activeTab.value = tab
  draft.number = ''
  draft.username = ''
  draft.dates = null
  page.value = 1
  filters.value = {}
}
function selectTab(tab: CouponOrderTab) {
  if (tab === activeTab.value) return
  resetTab(tab)
  const status = couponOrderTabs.find((option) => option.key === tab)!.status
  void router.replace({ query: status === undefined ? {} : { status: String(status) } })
}

async function openDetail(id: number) {
  const requestId = ++detailRequest
  detailId.value = id
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  detailVisible.value = true
  try {
    const result = await getCouponOrderDetail(id)
    if (requestId === detailRequest && !disposed) detail.value = result
  } catch (error) {
    if (requestId === detailRequest && !disposed) detailError.value = (error as Error).message
  } finally {
    if (requestId === detailRequest && !disposed) detailLoading.value = false
  }
}
function requestRefund(order: CouponOrderSummary) {
  if (busy.value) return
  refundOrder.value = order
  refundVisible.value = true
}
async function executeRefund(reason: string) {
  const order = refundOrder.value
  if (busy.value || !order) return
  busy.value = true
  try {
    await refundCouponOrder(order.id, reason)
    ElMessage.success('退款成功')
    refundVisible.value = false
    detailVisible.value = false
    await loadOrders()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    busy.value = false
  }
}

onMounted(loadOrders)
watch([page, pageSize, activeTab, filters], loadOrders)
watch(
  () => route.query.status,
  () => {
    const tab = tabFromQuery()
    if (activeTab.value !== tab) resetTab(tab)
  },
)
onBeforeRouteLeave((to) => !busy.value || to.name === 'login')
onBeforeRouteUpdate((to) => !busy.value || to.query.status === route.query.status)
onBeforeUnmount(() => {
  disposed = true
})
</script>

<style scoped>
.order-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.order-tabs {
  display: flex;
  min-width: 0;
  margin-bottom: 20px;
  overflow-x: auto;
}
.order-tabs > button {
  min-width: 120px;
  height: 40px;
  border: 1px solid #e5e4e4;
  border-right: 0;
  background: var(--admin-surface);
  color: #333;
  font-size: 14px;
  cursor: pointer;
}
.order-tabs > button:last-child {
  border-right: 1px solid #e5e4e4;
}
.order-tabs > button.is-active {
  background: var(--admin-primary);
  color: var(--admin-on-primary);
  font-weight: 700;
}
.order-switch.el-button {
  flex-shrink: 0;
  height: 40px;
  font-size: 14px;
}
.order-search {
  flex-wrap: wrap;
  gap: 12px 0;
  margin-bottom: 20px;
}
.order-search label:not(:first-child) {
  margin-left: 20px;
}
.order-search :deep(.el-date-editor) {
  flex: 0 1 320px;
  min-width: 280px;
  height: 40px;
}
.order-table :deep(.cell) {
  padding: 0 18px;
}
.order-table :deep(td.el-table__cell) {
  height: 68px;
}
</style>
