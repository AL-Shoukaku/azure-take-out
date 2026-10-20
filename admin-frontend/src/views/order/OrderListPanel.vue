<template>
  <section
    class="order-page"
    :class="{ 'is-embedded': embedded }"
    :aria-label="embedded ? '订单信息' : '订单管理'"
  >
    <div class="order-heading">
      <h2 v-if="embedded">订单信息</h2>
      <div class="order-tabs" role="tablist" aria-label="订单状态">
        <button
          v-for="tab in tabs"
          :id="`order-tab-${tab.status}`"
          :key="tab.status"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab.status"
          aria-controls="order-list"
          :disabled="busy"
          :class="{ 'is-active': activeTab === tab.status }"
          @click="selectTab(tab.status)"
        >
          <ElBadge :value="badgeCount(tab.status)" :max="99" :hidden="!badgeCount(tab.status)">{{
            tab.label
          }}</ElBadge>
        </button>
      </div>
    </div>
    <div
      id="order-list"
      class="management-list"
      role="tabpanel"
      :aria-labelledby="`order-tab-${activeTab}`"
    >
      <form v-if="!embedded" class="management-search order-search" @submit.prevent="search">
        <label for="order-number-query">订单号：</label
        ><ElInput
          id="order-number-query"
          v-model="draft.number"
          placeholder="请填写订单号"
          clearable
          :disabled="busy"
          @clear="search"
        />
        <label for="order-phone-query">手机号：</label
        ><ElInput
          id="order-phone-query"
          v-model="draft.phone"
          placeholder="请填写手机号"
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
      <div v-if="statisticsError" class="order-statistics-error" role="alert">
        {{ statisticsError }} <ElButton link @click="loadStatistics">重试统计</ElButton>
      </div>
      <div v-if="listError" class="management-load-error" role="alert">
        {{ listError }} <ElButton @click="refresh">重试列表</ElButton>
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
        <!-- @vue-generic {OrderSummary} -->
        <ElTableColumn
          v-for="column in columns"
          :key="column.key"
          :label="column.label"
          :min-width="column.width"
          ><template #default="{ row }">{{ column.value(row) }}</template></ElTableColumn
        >
        <!-- @vue-generic {OrderSummary} -->
        <ElTableColumn label="操作" width="210" align="center" fixed="right"
          ><template #default="{ row }"
            ><div
              class="order-row-actions"
              :class="{ 'is-view-only': row.status === 6 }"
            >
              <div>
                <ElButton
                  v-if="primaryActions[row.status]"
                  link
                  class="management-action-blue"
                  :disabled="busy || loading"
                  @click="requestAction(primaryActions[row.status]!.action, row, false)"
                  >{{ primaryActions[row.status]!.label }}</ElButton
                >
              </div>
              <div :class="{ 'has-action': secondaryAction(row.status) }">
                <ElButton
                  v-if="secondaryAction(row.status)"
                  link
                  class="management-action-red"
                  :disabled="busy || loading"
                  @click="requestAction(secondaryAction(row.status)!.action, row, false)"
                  >{{ secondaryAction(row.status)!.label }}</ElButton
                >
              </div>
              <div class="order-view-action">
                <ElButton
                  link
                  class="management-action-blue"
                  :disabled="busy"
                  @click="openDetail(row.id)"
                  >查看</ElButton
                >
              </div>
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
    <OrderDetailDialog
      v-model="detailVisible"
      v-model:auto-next="autoNext"
      :order="detail"
      :loading="detailLoading"
      :error="detailError"
      :busy="busy"
      :show-auto-next="activeTab === 2"
      @retry="openDetail(detailId!)"
      @action="(action) => requestAction(action, detail!, true)"
    />
    <OrderReasonDialog
      v-if="actionOrder"
      v-model="reasonVisible"
      :mode="reasonMode"
      :status="actionOrder.status"
      :busy="busy"
      @confirm="(reason) => executeAction(reasonMode, actionOrder!, actionFromDetail, reason)"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import {
  ElBadge,
  ElButton,
  ElDatePicker,
  ElInput,
  ElMessage,
  ElTable,
  ElTableColumn,
  vLoading,
} from 'element-plus'
import {
  cancelOrder,
  completeOrder,
  confirmOrder,
  deliverOrder,
  getOrderDetail,
  getOrderPage,
  getOrderStatistics,
  rejectOrder,
  type OrderDetail,
  type OrderPageQuery,
  type OrderStatistics,
  type OrderSummary,
  type OrderTab,
} from '@/api/order'
import { useOrderEventsStore } from '@/stores/orderEvents'
import ManagementPagination from '@/components/ManagementPagination.vue'
import OrderDetailDialog from './OrderDetailDialog.vue'
import OrderReasonDialog from './OrderReasonDialog.vue'
import {
  orderColumns,
  orderTabs,
  primaryActions,
  secondaryAction,
  type OrderAction,
} from './orderDisplay'
import '@/assets/styles/management.css'

const props = defineProps<{ embedded?: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const tabs = computed(() =>
  props.embedded ? orderTabs.filter((tab) => tab.status === 2 || tab.status === 3) : orderTabs,
)
const route = useRoute(),
  router = useRouter(),
  events = useOrderEventsStore()
function tabFromQuery(): OrderTab {
  if (props.embedded) return 2
  return orderTabs.find((tab) => tab.status === Number(route.query.status))?.status || 0
}
const activeTab = ref<OrderTab>(tabFromQuery())
const columns = computed(() => orderColumns(activeTab.value))
const page = ref(1),
  pageSize = ref(10),
  total = ref(0)
const draft = reactive<{ number: string; phone: string; dates: [string, string] | null }>({
  number: '',
  phone: '',
  dates: null,
})
const filters = ref<Omit<OrderPageQuery, 'page' | 'pageSize' | 'status'>>({})
const orders = ref<OrderSummary[]>([]),
  statistics = ref<OrderStatistics | null>(null)
const loading = ref(false),
  listError = ref(''),
  statisticsError = ref(''),
  busy = ref(false)
const detailVisible = ref(false),
  detail = ref<OrderDetail | null>(null),
  detailId = ref<number>()
const detailLoading = ref(false),
  detailError = ref(''),
  autoNext = ref(true)
const reasonVisible = ref(false),
  reasonMode = ref<'reject' | 'cancel'>('reject')
const actionOrder = ref<OrderSummary>(),
  actionFromDetail = ref(false)
let listRequest = 0,
  detailRequest = 0,
  statisticsRequest = 0,
  openNext = false,
  disposed = false

function badgeCount(tab: OrderTab) {
  if (!statistics.value) return 0
  if (tab === 2) return statistics.value.toBeConfirmed
  if (tab === 3) return statistics.value.confirmed
  if (tab === 4) return statistics.value.deliveryInProgress
  return 0
}
async function loadStatistics() {
  const id = ++statisticsRequest
  statisticsError.value = ''
  try {
    const result = await getOrderStatistics()
    if (id === statisticsRequest && !disposed) statistics.value = result
  } catch (error) {
    if (id === statisticsRequest && !disposed) statisticsError.value = (error as Error).message
  }
}
async function loadOrders() {
  const id = ++listRequest
  loading.value = true
  listError.value = ''
  try {
    const result = await getOrderPage({
      page: page.value,
      pageSize: pageSize.value,
      status: activeTab.value || undefined,
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
    if (openNext) {
      openNext = false
      const next = result.records[0]
      if (next) await openDetail(next.id)
    }
  } catch (error) {
    if (id === listRequest && !disposed) {
      listError.value = (error as Error).message
      openNext = false
    }
  } finally {
    if (id === listRequest && !disposed) loading.value = false
  }
}
async function refresh() {
  await Promise.all([loadOrders(), loadStatistics()])
}
function search() {
  page.value = 1
  filters.value = {
    number: draft.number.trim() || undefined,
    phone: draft.phone.trim() || undefined,
    beginTime: draft.dates?.[0] ? `${draft.dates[0]} 00:00:00` : undefined,
    endTime: draft.dates?.[1] ? `${draft.dates[1]} 23:59:59` : undefined,
  }
}
function resetTab(tab: OrderTab) {
  activeTab.value = tab
  draft.number = ''
  draft.phone = ''
  draft.dates = null
  page.value = 1
  filters.value = {}
}
function selectTab(tab: OrderTab) {
  if (tab === activeTab.value) return
  resetTab(tab)
  if (!props.embedded)
    void router.replace({ path: '/order', query: tab ? { status: String(tab) } : {} })
}

async function openDetail(id: number) {
  const requestId = ++detailRequest
  detailId.value = id
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  detailVisible.value = true
  try {
    const result = await getOrderDetail(id)
    if (requestId === detailRequest && !disposed) detail.value = result
  } catch (error) {
    if (requestId === detailRequest && !disposed) detailError.value = (error as Error).message
  } finally {
    if (requestId === detailRequest && !disposed) detailLoading.value = false
  }
}
function requestAction(action: OrderAction, order: OrderSummary, fromDetail: boolean) {
  if (busy.value) return
  if (action === 'reject' || action === 'cancel') {
    actionOrder.value = order
    actionFromDetail.value = fromDetail
    reasonMode.value = action
    reasonVisible.value = true
  } else void executeAction(action, order, fromDetail)
}
async function executeAction(
  action: OrderAction,
  order: OrderSummary,
  fromDetail: boolean,
  reason = '',
) {
  if (busy.value) return
  busy.value = true
  try {
    if (action === 'confirm') await confirmOrder(order.id)
    else if (action === 'deliver') await deliverOrder(order.id)
    else if (action === 'complete') await completeOrder(order.id)
    else if (action === 'reject') await rejectOrder(order.id, reason)
    else await cancelOrder(order.id, reason)
    ElMessage.success('操作成功')
    openNext = fromDetail && order.status === 2 && activeTab.value === 2 && autoNext.value
    reasonVisible.value = false
    detailVisible.value = false
    emit('changed')
    await refresh()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    busy.value = false
  }
}

onMounted(refresh)
watch([page, pageSize, activeTab, filters], refresh)
watch(() => events.revision, refresh)
watch(
  () => route.query.status,
  () => {
    if (props.embedded) return
    const tab = tabFromQuery()
    if (activeTab.value !== tab) resetTab(tab)
  },
)
watch(
  () => route.query.orderId,
  (id) => {
    if (props.embedded) return
    reasonVisible.value = false
    if (typeof id === 'string' && /^[1-9]\d*$/.test(id)) {
      void openDetail(Number(id))
    } else {
      detailRequest++
      detailVisible.value = false
      detailLoading.value = false
    }
  },
  { immediate: true },
)
watch(detailVisible, (visible) => {
  if (!props.embedded && !visible && route.query.orderId)
    void router.replace({ query: { ...route.query, orderId: undefined } })
})
onBeforeRouteLeave((to) => !busy.value || to.name === 'login')
onBeforeRouteUpdate(
  (to) =>
    !busy.value ||
    (to.path === route.path && !to.query.orderId && to.query.status === route.query.status),
)
onBeforeUnmount(() => {
  disposed = true
})
</script>

<style scoped>
.is-embedded {
  padding: 20px;
  border-radius: 4px;
  background: var(--admin-surface);
}
.is-embedded .order-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}
.order-heading h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.is-embedded .order-tabs {
  margin-bottom: 0;
}
.is-embedded .order-tabs > button {
  min-width: 120px;
}
.is-embedded .management-list {
  padding: 0;
}
.order-tabs {
  display: flex;
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
.order-tabs :deep(.el-badge__content) {
  background: #fd3333;
}
.order-tabs :deep(.el-badge__content.is-fixed) {
  top: 4px;
  right: 0;
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
.order-row-actions {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  align-items: center;
}
.order-row-actions.is-view-only {
  display: flex;
  justify-content: center;
}
.order-row-actions .el-button {
  margin: 0;
}
.order-row-actions .has-action {
  border-right: 1px solid #ebeef5;
}
.order-row-actions > div:first-child:has(button) {
  border-right: 1px solid #ebeef5;
}
.order-statistics-error {
  margin-bottom: 16px;
  color: #f56c6c;
}
</style>
