<template>
  <section class="management-list employee-list" aria-label="员工管理">
    <div class="management-toolbar">
      <form class="management-search" @submit.prevent="search">
        <label for="employee-name-query">员工姓名：</label>
        <ElInput
          id="employee-name-query"
          v-model="nameInput"
          aria-label="员工姓名查询"
          placeholder="请输入员工姓名"
          clearable
          @clear="search"
        />
        <ElButton class="management-dark-button" native-type="submit">查询</ElButton>
      </form>
      <ElButton type="primary" class="employee-add" @click="router.push('/employee/add')">
        + 添加员工
      </ElButton>
    </div>

    <div v-if="loadError" class="management-load-error" role="alert">
      <span>{{ loadError }}</span>
      <ElButton @click="loadEmployees">重试</ElButton>
    </div>
    <ElTable
      v-else
      v-loading="loading"
      :data="employees"
      stripe
      class="management-table"
      row-key="id"
    >
      <ElTableColumn prop="name" label="员工姓名" min-width="120" />
      <ElTableColumn prop="username" label="账号" min-width="120" />
      <ElTableColumn prop="phone" label="手机号" min-width="130" />
      <!-- @vue-generic {Employee} -->
      <ElTableColumn label="账号状态" min-width="110">
        <template #default="{ row }">
          <span class="management-status" :class="{ 'is-disabled': row.status === 0 }">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </span>
        </template>
      </ElTableColumn>
      <!-- @vue-generic {Employee} -->
      <ElTableColumn label="最后操作时间" min-width="180">
        <template #default="{ row }">
          {{ row.updateTime ? row.updateTime.replace('T', ' ').slice(0, 16) : '—' }}
        </template>
      </ElTableColumn>
      <!-- @vue-generic {Employee} -->
      <ElTableColumn label="操作" width="160" align="center">
        <template #default="{ row }">
          <div class="management-actions">
            <ElButton
              link
              class="management-action-blue"
              :disabled="row.username === 'admin'"
              @click="router.push({ path: '/employee/add', query: { id: row.id } })"
              >修改</ElButton
            >
            <span class="management-action-divider" aria-hidden="true">|</span>
            <ElButton
              link
              :class="row.status === 1 ? 'management-action-red' : 'management-action-blue'"
              :disabled="
                row.username === 'admin' ||
                (row.id === session.employee?.id && row.status === 1) ||
                changingId !== null
              "
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
      :disabled="loading"
    />
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElButton,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  vLoading,
} from 'element-plus'
import { getEmployeePage, setEmployeeStatus, type Employee } from '@/api/employee'
import { useSessionStore } from '@/stores/session'
import ManagementPagination from '@/components/ManagementPagination.vue'
import '@/assets/styles/management.css'

const router = useRouter()
const session = useSessionStore()
const nameInput = ref('')
const searchedName = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const employees = ref<Employee[]>([])
const loading = ref(false)
const loadError = ref('')
const changingId = ref<number | null>(null)
let latestRequest = 0

async function loadEmployees() {
  const requestId = ++latestRequest
  loading.value = true
  loadError.value = ''
  try {
    const result = await getEmployeePage({
      page: page.value,
      pageSize: pageSize.value,
      name: searchedName.value || undefined,
    })
    if (requestId !== latestRequest) return
    employees.value = result.records
    total.value = result.total
  } catch (error) {
    if (requestId === latestRequest) loadError.value = (error as Error).message
  } finally {
    if (requestId === latestRequest) loading.value = false
  }
}

function search() {
  const name = nameInput.value.trim()
  const unchanged = page.value === 1 && searchedName.value === name
  searchedName.value = name
  page.value = 1
  if (unchanged) void loadEmployees()
}

async function changeStatus(employee: Employee) {
  if (
    employee.username === 'admin' ||
    changingId.value !== null ||
    (employee.id === session.employee?.id && employee.status === 1)
  )
    return
  changingId.value = employee.id
  try {
    await ElMessageBox.confirm('确认调整该账号的状态?', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      customClass: 'management-confirm',
    })
    await setEmployeeStatus(employee.id, employee.status === 1 ? 0 : 1)
    ElMessage.success('账号状态更改成功！')
    await loadEmployees()
  } catch (error) {
    if (error instanceof Error) ElMessage.error(error.message)
  } finally {
    changingId.value = null
  }
}

onMounted(loadEmployees)
watch([page, pageSize, searchedName], loadEmployees)
</script>

<style scoped>
.employee-add {
  flex-shrink: 0;
}
</style>
