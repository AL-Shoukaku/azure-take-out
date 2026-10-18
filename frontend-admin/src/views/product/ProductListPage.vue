<template>
  <section class="management-list product-list" :aria-label="`${label}管理`">
    <div class="management-toolbar product-toolbar">
      <form class="management-search product-search" @submit.prevent="search">
        <label :for="`${kind}-query`">{{ label }}名称:</label>
        <ElInput
          :id="`${kind}-query`"
          v-model="draft.name"
          :aria-label="`${label}名称查询`"
          :placeholder="`请填写${label}名称`"
          clearable
          @clear="search"
        />
        <label class="product-query-label">{{ label }}分类:</label>
        <ElSelect
          v-model="draft.categoryId"
          :aria-label="`${label}分类查询`"
          placeholder="请选择"
          clearable
          @clear="search"
        >
          <ElOption
            v-for="category in categories"
            :key="category.id"
            :label="category.name"
            :value="category.id"
          />
        </ElSelect>
        <label class="product-query-label">售卖状态:</label>
        <ElSelect
          v-model="draft.status"
          aria-label="售卖状态查询"
          placeholder="请选择"
          clearable
          @clear="search"
        >
          <ElOption label="停售" :value="0" /><ElOption label="起售" :value="1" />
        </ElSelect>
        <ElButton native-type="submit" class="management-dark-button">查询</ElButton>
      </form>
      <div class="product-add-buttons">
        <ElButton
          link
          class="management-action-red"
          :disabled="busy || loading"
          @click="remove(selectedIds)"
          >批量删除</ElButton
        >
        <ElButton type="primary" :disabled="busy" @click="router.push(`/${kind}/add`)"
          >+ 新建{{ label }}</ElButton
        >
      </div>
    </div>
    <div v-if="categoryError" class="product-category-error" role="alert">
      {{ categoryError }} <ElButton link @click="loadCategories">重试分类</ElButton>
    </div>
    <div v-if="loadError" class="management-load-error" role="alert">
      {{ loadError }} <ElButton @click="loadProducts">重试</ElButton>
    </div>
    <ElTable
      v-else
      ref="tableRef"
      v-loading="loading"
      class="management-table product-table"
      :data="products"
      row-key="id"
      stripe
      empty-text="暂无数据"
      @selection-change="selectionChanged"
    >
      <ElTableColumn type="selection" width="45" :selectable="() => !busy && !loading" />
      <ElTableColumn prop="name" :label="`${label}名称`" min-width="160" />
      <!-- @vue-generic {ProductSummary} -->
      <ElTableColumn label="图片" width="110"
        ><template #default="{ row }"
          ><ElImage
            :src="row.image"
            :preview-src-list="[row.image]"
            preview-teleported
            fit="cover"
            class="product-thumbnail"
            ><template #error><span class="product-image-empty">暂无图片</span></template></ElImage
          ></template
        ></ElTableColumn
      >
      <ElTableColumn prop="categoryName" :label="`${label}分类`" min-width="135" />
      <!-- @vue-generic {ProductSummary} -->
      <ElTableColumn :label="kind === 'dish' ? '售价' : '套餐价'" min-width="100"
        ><template #default="{ row }">￥{{ Number(row.price).toFixed(2) }}</template></ElTableColumn
      >
      <!-- @vue-generic {ProductSummary} -->
      <ElTableColumn label="售卖状态" min-width="120"
        ><template #default="{ row }"
          ><span class="management-status" :class="{ 'is-disabled': row.status === 0 }">{{
            row.status === 1 ? '起售' : '停售'
          }}</span></template
        ></ElTableColumn
      >
      <!-- @vue-generic {ProductSummary} -->
      <ElTableColumn label="最后操作时间" min-width="190"
        ><template #default="{ row }">{{
          row.updateTime?.replace('T', ' ').slice(0, 16) || '—'
        }}</template></ElTableColumn
      >
      <!-- @vue-generic {ProductSummary} -->
      <ElTableColumn label="操作" width="215" align="center"
        ><template #default="{ row }"
          ><div class="management-actions">
            <ElButton
              link
              class="management-action-blue"
              :disabled="busy || loading"
              @click="router.push({ path: `/${kind}/add`, query: { id: row.id } })"
              >修改</ElButton
            >
            <span class="management-action-divider">|</span>
            <ElButton
              link
              class="management-action-red"
              :disabled="busy || loading"
              @click="remove([row.id])"
              >删除</ElButton
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
        ></ElTableColumn
      >
    </ElTable>
    <ManagementPagination
      v-model:page="page"
      v-model:page-size="pageSize"
      :total="total"
      :disabled="busy"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElButton,
  ElImage,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  vLoading,
  type TableInstance,
} from 'element-plus'
import { getCategoryList, type Category } from '@/api/category'
import {
  dishAPI,
  setmealAPI,
  type ProductKind,
  type ProductPageQuery,
  type ProductSummary,
} from '@/api/products'
import ManagementPagination from '@/components/ManagementPagination.vue'
import '@/assets/styles/management.css'

const props = defineProps<{ kind: ProductKind }>()
const router = useRouter()
const label = computed(() => (props.kind === 'dish' ? '菜品' : '套餐'))
const api = computed(() => (props.kind === 'dish' ? dishAPI : setmealAPI))
const draft = reactive<{ name: string; categoryId?: number; status?: 0 | 1 }>({ name: '' })
const filters = ref<Omit<ProductPageQuery, 'page' | 'pageSize'>>({})
const page = ref(1),
  pageSize = ref(10),
  total = ref(0)
const products = ref<ProductSummary[]>([]),
  categories = ref<Category[]>([])
const selectedIds = ref<number[]>([])
const tableRef = ref<TableInstance>()
const loading = ref(false),
  loadError = ref(''),
  categoryError = ref(''),
  busy = ref(false)
let latestRequest = 0

async function loadCategories() {
  categoryError.value = ''
  try {
    categories.value = await getCategoryList(props.kind === 'dish' ? 1 : 2)
  } catch (error) {
    categoryError.value = (error as Error).message
  }
}

async function loadProducts() {
  const id = ++latestRequest
  loading.value = true
  loadError.value = ''
  selectedIds.value = []
  tableRef.value?.clearSelection()
  try {
    const result = await api.value.page({
      page: page.value,
      pageSize: pageSize.value,
      ...filters.value,
    })
    if (id !== latestRequest) return
    total.value = result.total
    const lastPage = Math.max(1, Math.ceil(result.total / pageSize.value))
    if (page.value > lastPage) page.value = lastPage
    else products.value = result.records
  } catch (error) {
    if (id === latestRequest) loadError.value = (error as Error).message
  } finally {
    if (id === latestRequest) loading.value = false
  }
}

function search() {
  filters.value = {
    name: draft.name.trim() || undefined,
    categoryId: draft.categoryId || undefined,
    status: draft.status === 0 || draft.status === 1 ? draft.status : undefined,
  }
  page.value = 1
}
function selectionChanged(rows: ProductSummary[]) {
  selectedIds.value = rows.map((row) => row.id)
}

async function remove(ids: number[]) {
  if (busy.value || loading.value) return
  if (!ids.length) {
    ElMessage.error('请选择删除对象')
    return
  }
  busy.value = true
  const deletingIds = [...ids]
  try {
    await ElMessageBox.confirm(
      `确认删除${deletingIds.length > 1 ? '所选' : '该'}${label.value}, 是否继续?`,
      '确定删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', customClass: 'management-confirm' },
    )
    await api.value.remove(deletingIds)
    ElMessage.success('删除成功！')
    await loadProducts()
  } catch (error) {
    if (error instanceof Error) ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

async function changeStatus(product: ProductSummary) {
  if (busy.value) return
  busy.value = true
  try {
    await ElMessageBox.confirm(`确认更改该${label.value}状态?`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      customClass: 'management-confirm',
    })
    await api.value.status(product.id, product.status === 1 ? 0 : 1)
    ElMessage.success(`${label.value}状态已经更改成功！`)
    await loadProducts()
  } catch (error) {
    if (error instanceof Error) ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  void loadCategories()
  void loadProducts()
})
watch([page, pageSize, filters], loadProducts)
</script>

<style scoped>
.product-toolbar,
.product-search {
  flex-wrap: wrap;
  row-gap: 14px;
}
.product-query-label {
  margin-left: 20px;
}
.product-search .el-select {
  width: 175px;
}
.product-search :deep(.el-select__wrapper) {
  min-height: 40px;
}
.product-add-buttons {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 25px;
  margin-left: auto;
}
.product-add-buttons .el-button {
  margin: 0;
}
.product-table :deep(td.el-table__cell) {
  height: 68px;
}
.product-table :deep(.cell) {
  padding: 0 18px;
  word-break: normal;
}
.product-table :deep(.el-table__cell:nth-child(5) .cell) {
  white-space: nowrap;
}
.product-table :deep(.el-table-column--selection .cell) {
  padding: 0 12px;
}
.product-table :deep(.management-actions) {
  gap: 12px;
}
.product-thumbnail {
  display: block;
  width: 40px;
  height: 40px;
  border-radius: 5px;
}
.product-image-empty {
  font-size: 10px;
}
.product-category-error {
  margin-bottom: 16px;
  color: #f56c6c;
}
@media (min-width: 1700px) {
  .product-toolbar {
    flex-wrap: nowrap;
  }
}
</style>
