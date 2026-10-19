<template>
  <section class="management-list category-page" aria-label="分类管理">
    <div class="management-toolbar category-toolbar">
      <form class="management-search category-search" @submit.prevent="search">
        <label for="category-name-query">分类名称：</label>
        <ElInput
          id="category-name-query"
          v-model="nameInput"
          aria-label="分类名称查询"
          placeholder="请填写分类名称"
          clearable
          @clear="search"
        />
        <label for="category-type-query" class="category-type-label">分类类型：</label>
        <ElSelect
          id="category-type-query"
          v-model="typeInput"
          aria-label="分类类型"
          placeholder="请选择"
          class="category-type-select"
          clearable
          @clear="search"
        >
          <ElOption label="菜品分类" :value="1" />
          <ElOption label="套餐分类" :value="2" />
        </ElSelect>
        <ElButton class="management-dark-button" native-type="submit">查询</ElButton>
      </form>
      <div class="category-add-buttons">
        <ElButton class="management-dark-button" @click="openCreate(1)">+ 新增菜品分类</ElButton>
        <ElButton type="primary" @click="openCreate(2)">+ 新增套餐分类</ElButton>
      </div>
    </div>

    <div v-if="loadError" class="management-load-error" role="alert">
      <span>{{ loadError }}</span>
      <ElButton @click="loadCategories">重试</ElButton>
    </div>
    <ElTable
      v-else
      v-loading="loading"
      :data="categories"
      stripe
      row-key="id"
      class="management-table"
    >
      <ElTableColumn prop="name" label="分类名称" min-width="140" />
      <!-- @vue-generic {Category} -->
      <ElTableColumn label="分类类型" min-width="130">
        <template #default="{ row }">{{ row.type === 1 ? '菜品分类' : '套餐分类' }}</template>
      </ElTableColumn>
      <ElTableColumn prop="sort" label="排序" min-width="90" />
      <!-- @vue-generic {Category} -->
      <ElTableColumn label="分类状态" min-width="110">
        <template #default="{ row }">
          <span class="management-status" :class="{ 'is-disabled': row.status === 0 }">{{
            row.status === 1 ? '启用' : '禁用'
          }}</span>
        </template>
      </ElTableColumn>
      <!-- @vue-generic {Category} -->
      <ElTableColumn label="操作时间" min-width="180">
        <template #default="{ row }">{{
          row.updateTime ? row.updateTime.replace('T', ' ').slice(0, 16) : '—'
        }}</template>
      </ElTableColumn>
      <!-- @vue-generic {Category} -->
      <ElTableColumn label="操作" width="200" align="center">
        <template #default="{ row }">
          <div class="management-actions">
            <ElButton link class="management-action-blue" :disabled="busy" @click="openEdit(row)"
              >修改</ElButton
            >
            <span class="management-action-divider" aria-hidden="true">|</span>
            <ElButton
              link
              class="management-action-red"
              :disabled="busy"
              @click="removeCategory(row)"
              >删除</ElButton
            >
            <span class="management-action-divider" aria-hidden="true">|</span>
            <ElButton
              link
              :class="row.status === 1 ? 'management-action-red' : 'management-action-blue'"
              :disabled="busy"
              @click="changeStatus(row)"
              >{{ row.status === 1 ? '禁用' : '启用' }}</ElButton
            >
          </div>
        </template>
      </ElTableColumn>
    </ElTable>
    <ManagementPagination
      v-model:page="page"
      v-model:page-size="pageSize"
      :total="total"
      :disabled="loading || busy"
    />
    <CategoryFormDialog
      v-model="formVisible"
      :type="formType"
      :category="editingCategory"
      @saved="loadCategories"
    />
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import {
  ElButton,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  vLoading,
} from 'element-plus'
import {
  deleteCategory,
  getCategoryPage,
  setCategoryStatus,
  type Category,
  type CategoryType,
} from '@/api/category'
import ManagementPagination from '@/components/ManagementPagination.vue'
import CategoryFormDialog from './CategoryFormDialog.vue'
import '@/assets/styles/management.css'

const nameInput = ref('')
const typeInput = ref<CategoryType | ''>('')
const searchedName = ref('')
const searchedType = ref<CategoryType | ''>('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const categories = ref<Category[]>([])
const loading = ref(false)
const loadError = ref('')
const busy = ref(false)
const formVisible = ref(false)
const formType = ref<CategoryType>(1)
const editingCategory = ref<Category | null>(null)
let latestRequest = 0

async function loadCategories() {
  const requestId = ++latestRequest
  loading.value = true
  loadError.value = ''
  try {
    const result = await getCategoryPage({
      page: page.value,
      pageSize: pageSize.value,
      name: searchedName.value || undefined,
      type: searchedType.value || undefined,
    })
    if (requestId !== latestRequest) return
    total.value = result.total
    const lastPage = Math.max(1, Math.ceil(result.total / pageSize.value))
    if (page.value > lastPage) page.value = lastPage
    else categories.value = result.records
  } catch (error) {
    if (requestId === latestRequest) loadError.value = (error as Error).message
  } finally {
    if (requestId === latestRequest) loading.value = false
  }
}

function search() {
  const name = nameInput.value.trim()
  const unchanged =
    page.value === 1 && searchedName.value === name && searchedType.value === typeInput.value
  searchedName.value = name
  searchedType.value = typeInput.value
  page.value = 1
  if (unchanged) void loadCategories()
}

function openCreate(type: CategoryType) {
  editingCategory.value = null
  formType.value = type
  formVisible.value = true
}

function openEdit(category: Category) {
  editingCategory.value = category
  formType.value = category.type
  formVisible.value = true
}

async function changeStatus(category: Category) {
  if (busy.value) return
  busy.value = true
  try {
    await ElMessageBox.confirm('确认调整该分类的状态?', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      customClass: 'management-confirm',
    })
    await setCategoryStatus(category.id, category.status === 1 ? 0 : 1)
    ElMessage.success('分类状态更改成功！')
    await loadCategories()
  } catch (error) {
    if (error instanceof Error) ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

async function removeCategory(category: Category) {
  if (busy.value) return
  busy.value = true
  try {
    await ElMessageBox.confirm('此操作将永久删除该分类，是否继续？', '确定删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      customClass: 'management-confirm',
    })
    await deleteCategory(category.id)
    ElMessage.success('删除成功！')
    await loadCategories()
  } catch (error) {
    if (error instanceof Error) ElMessage.error(error.message)
  } finally {
    busy.value = false
  }
}

onMounted(loadCategories)
watch([page, pageSize, searchedName, searchedType], loadCategories)
</script>

<style scoped>
.category-add-buttons {
  display: flex;
  flex-shrink: 0;
  gap: 20px;
}

.category-add-buttons .el-button {
  margin: 0;
}

.category-type-label {
  margin-left: 20px;
}

.category-type-select {
  width: 190px;
}

.category-type-select :deep(.el-select__wrapper) {
  min-height: 40px;
}

.category-search {
  flex-wrap: wrap;
  row-gap: 12px;
}

.category-page :deep(.management-actions) {
  gap: 12px;
}

@media (max-width: 1350px) {
  .category-toolbar {
    flex-wrap: wrap;
  }
}
</style>
