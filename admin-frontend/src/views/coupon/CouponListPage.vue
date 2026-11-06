<template>
  <section class="management-list coupon-list" aria-label="优惠券管理">
    <div class="management-toolbar coupon-toolbar">
      <form class="management-search coupon-search" @submit.prevent="search">
        <div class="coupon-filter">
          <label for="coupon-name-query">优惠券名称:</label>
          <ElInput
            id="coupon-name-query"
            v-model="draft.name"
            aria-label="优惠券名称查询"
            placeholder="请填写优惠券名称"
            clearable
            @clear="search"
          />
        </div>
        <div class="coupon-filter">
          <label for="coupon-type-query">类型:</label>
          <ElSelect
            id="coupon-type-query"
            v-model="draft.type"
            aria-label="优惠券类型查询"
            placeholder="全部类型"
            clearable
            @clear="search"
          >
            <ElOption label="代金券" :value="1" /><ElOption label="满减券" :value="2" />
          </ElSelect>
        </div>
        <div class="coupon-filter">
          <label for="coupon-status-query">状态:</label>
          <ElSelect
            id="coupon-status-query"
            v-model="draft.status"
            aria-label="起售状态查询"
            placeholder="全部状态"
            clearable
            @clear="search"
          >
            <ElOption label="停售" :value="0" /><ElOption label="起售" :value="1" />
          </ElSelect>
        </div>
        <div class="coupon-filter">
          <label for="coupon-activity-query">活动状态:</label>
          <ElSelect
            id="coupon-activity-query"
            v-model="draft.activityStatus"
            aria-label="活动状态查询"
            placeholder="全部活动"
            clearable
            @clear="search"
          >
            <ElOption label="未开始" :value="0" /><ElOption label="进行中" :value="1" /><ElOption
              label="已结束"
              :value="2"
            />
          </ElSelect>
        </div>
        <ElButton native-type="submit" class="management-dark-button">查询</ElButton>
        <ElButton @click="reset">重置</ElButton>
      </form>
      <ElButton
        class="coupon-add"
        type="primary"
        :disabled="busy"
        @click="router.push('/coupon/add')"
        >+ 新建优惠券</ElButton
      >
    </div>
    <div v-if="loadError" class="management-load-error" role="alert">
      {{ loadError }} <ElButton @click="loadCoupons">重试</ElButton>
    </div>
    <ElTable
      v-else
      v-loading="loading"
      :data="coupons"
      class="management-table coupon-table"
      row-key="id"
      stripe
      empty-text="暂无优惠券"
    >
      <ElTableColumn prop="name" label="优惠券名称" min-width="140" show-overflow-tooltip />
      <!-- @vue-generic {Coupon} -->
      <ElTableColumn label="类型" width="80"
        ><template #default="{ row }">{{
          row.type === 1 ? '代金券' : '满减券'
        }}</template></ElTableColumn
      >
      <ElTableColumn prop="stock" label="库存" width="70" />
      <ElTableColumn prop="totalStock" label="总库存" width="80" />
      <!-- @vue-generic {Coupon} -->
      <ElTableColumn label="活动开始时间" min-width="160"
        ><template #default="{ row }">{{
          row.startTime?.replace('T', ' ') || '—'
        }}</template></ElTableColumn
      >
      <!-- @vue-generic {Coupon} -->
      <ElTableColumn label="活动结束时间" min-width="160"
        ><template #default="{ row }">{{
          row.endTime?.replace('T', ' ') || '—'
        }}</template></ElTableColumn
      >
      <!-- @vue-generic {Coupon} -->
      <ElTableColumn label="起售状态" width="100"
        ><template #default="{ row }"
          ><span class="management-status" :class="{ 'is-disabled': row.status === 0 }">{{
            row.status === 1 ? '起售' : '停售'
          }}</span></template
        ></ElTableColumn
      >
      <!-- @vue-generic {Coupon} -->
      <ElTableColumn label="操作" width="200" align="center" fixed="right">
        <template #default="{ row }"
          ><div class="management-actions">
            <ElButton
              link
              class="management-action-blue"
              :disabled="busy || loading"
              @click="showDetail(row.id)"
              >详情</ElButton
            >
            <span class="management-action-divider">|</span>
            <ElButton
              link
              class="management-action-blue"
              :disabled="busy || loading"
              @click="router.push({ path: '/coupon/add', query: { id: row.id } })"
              >修改</ElButton
            >
            <span class="management-action-divider">|</span>
            <ElButton
              link
              :class="row.status === 1 ? 'management-action-red' : 'management-action-blue'"
              :disabled="busy || loading"
              @click="changeStatus(row)"
              >{{ row.status === 1 ? '停售' : '起售' }}</ElButton
            >
          </div></template
        >
      </ElTableColumn>
    </ElTable>
    <ManagementPagination
      v-model:page="page"
      v-model:page-size="pageSize"
      :total="total"
      :disabled="busy || loading"
    />
    <ElDialog
      v-model="detailVisible"
      title="优惠券详情"
      class="admin-dialog"
      width="820px"
      append-to-body
    >
      <div v-loading="detailLoading" class="coupon-detail">
        <div v-if="detailError" class="management-load-error" role="alert">
          {{ detailError }} <ElButton @click="loadDetail">重试详情</ElButton>
        </div>
        <CouponInfo v-else-if="detail" :coupon="detail" />
      </div>
      <template #footer><ElButton @click="detailVisible = false">关闭</ElButton></template>
    </ElDialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElButton,
  ElDialog,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  vLoading,
} from 'element-plus'
import { couponAPI, type Coupon, type CouponPageQuery } from '@/api/coupon'
import ManagementPagination from '@/components/ManagementPagination.vue'
import CouponInfo from './CouponInfo.vue'
import '@/assets/styles/management.css'

const router = useRouter()
type Filters = Omit<CouponPageQuery, 'page' | 'pageSize'>
const draft = reactive<Filters>({ name: '' })
const filters = ref<Filters>({})
const page = ref(1),
  pageSize = ref(10),
  total = ref(0)
const coupons = ref<Coupon[]>([])
const loading = ref(false),
  loadError = ref(''),
  busy = ref(false)
let latestRequest = 0

async function loadCoupons() {
  const requestId = ++latestRequest
  loading.value = true
  loadError.value = ''
  try {
    const result = await couponAPI.page({
      ...filters.value,
      page: page.value,
      pageSize: pageSize.value,
    })
    if (requestId !== latestRequest) return
    total.value = result.total
    const lastPage = Math.max(1, Math.ceil(result.total / pageSize.value))
    if (page.value > lastPage) page.value = lastPage
    else coupons.value = result.records
  } catch (error) {
    if (requestId === latestRequest) loadError.value = (error as Error).message
  } finally {
    if (requestId === latestRequest) loading.value = false
  }
}

function search() {
  filters.value = {
    name: draft.name?.trim() || undefined,
    type: draft.type === 1 || draft.type === 2 ? draft.type : undefined,
    status: draft.status === 0 || draft.status === 1 ? draft.status : undefined,
    activityStatus: [0, 1, 2].includes(draft.activityStatus as number)
      ? draft.activityStatus
      : undefined,
  }
  page.value = 1
}
function reset() {
  Object.assign(draft, { name: '', type: undefined, status: undefined, activityStatus: undefined })
  search()
}

async function changeStatus(coupon: Coupon) {
  if (busy.value || loading.value) return
  busy.value = true
  const action = coupon.status === 1 ? '停售' : '起售'
  try {
    await ElMessageBox.confirm(`确认${action}优惠券“${coupon.name}”？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      customClass: 'management-confirm',
    })
    await couponAPI.status(coupon.id, coupon.status === 1 ? 0 : 1)
    ElMessage.success(`优惠券${action}成功！`)
    await loadCoupons()
  } catch (error) {
    if (error instanceof Error) ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

const detailVisible = ref(false),
  detailLoading = ref(false),
  detailError = ref('')
const detailId = ref<number>(),
  detail = ref<Coupon | null>(null)
let latestDetailRequest = 0
function showDetail(id: number) {
  detailId.value = id
  detailVisible.value = true
  void loadDetail()
}
async function loadDetail() {
  if (!detailId.value) return
  const requestId = ++latestDetailRequest
  detail.value = null
  detailLoading.value = true
  detailError.value = ''
  try {
    const result = await couponAPI.detail(detailId.value)
    if (requestId === latestDetailRequest) detail.value = result
  } catch (error) {
    if (requestId === latestDetailRequest) detailError.value = (error as Error).message
  } finally {
    if (requestId === latestDetailRequest) detailLoading.value = false
  }
}

onMounted(loadCoupons)
watch([page, pageSize, filters], loadCoupons)
</script>

<style scoped>
.coupon-toolbar,
.coupon-search {
  flex-wrap: wrap;
  gap: 14px 18px;
}
.coupon-search {
  flex: 1;
}
.coupon-filter {
  display: flex;
  align-items: center;
}
.coupon-search .el-select {
  width: 120px;
}
.coupon-search :deep(.el-select__wrapper) {
  min-height: 40px;
}
.coupon-search .management-dark-button {
  margin-left: 0;
}
.coupon-search .el-button + .el-button {
  margin-left: 0;
}
.coupon-add {
  align-self: flex-start;
  margin-left: auto;
}
.coupon-table :deep(.cell) {
  padding: 0 16px;
  word-break: normal;
}
.coupon-detail {
  min-height: 180px;
  max-height: 65vh;
  overflow: auto;
}
</style>
