<template>
  <section class="employee-form-page">
    <header class="employee-form-heading">
      <button type="button" :disabled="saving" @click="cancel">
        <span aria-hidden="true">←</span> 返回
      </button>
      <h1>{{ employeeId ? '修改员工信息' : '添加员工' }}</h1>
    </header>
    <div v-loading="loading" class="employee-form-panel">
      <div v-if="loadError" class="employee-load-error" role="alert">
        <span>{{ loadError }}</span>
        <ElButton @click="loadEmployee">重试</ElButton>
      </div>
      <ElForm
        id="employee-form"
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="180px"
        :disabled="loading || saving || !!loadError"
        @submit.prevent="save(false)"
      >
        <ElFormItem label="账号:" prop="username">
          <ElInput v-model="form.username" placeholder="请输入账号" maxlength="20" />
        </ElFormItem>
        <ElFormItem label="员工姓名:" prop="name">
          <ElInput v-model="form.name" placeholder="请输入员工姓名" maxlength="12" />
        </ElFormItem>
        <ElFormItem label="手机号:" prop="phone">
          <ElInput v-model="form.phone" placeholder="请输入手机号" maxlength="11" inputmode="tel" />
        </ElFormItem>
        <ElFormItem label="性别:" prop="sex">
          <ElRadioGroup v-model="form.sex" aria-label="性别">
            <ElRadio value="1">男</ElRadio>
            <ElRadio value="0">女</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="身份证号:" prop="idNumber" class="employee-id-number">
          <ElInput v-model="form.idNumber" placeholder="请输入身份证号" maxlength="20" />
        </ElFormItem>
      </ElForm>
      <div class="employee-form-footer">
        <ElButton :disabled="saving" @click="cancel">取消</ElButton>
        <ElButton
          type="primary"
          :class="{ 'employee-dark-button': !employeeId }"
          :loading="savingAction === 'save'"
          :disabled="loading || saving || !!loadError"
          native-type="submit"
          form="employee-form"
          >保存</ElButton
        >
        <ElButton
          v-if="!employeeId"
          type="primary"
          :loading="savingAction === 'continue'"
          :disabled="saving"
          @click="save(true)"
          >保存并继续添加</ElButton
        >
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { nextTick, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElRadio,
  ElRadioGroup,
  vLoading,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { createEmployee, getEmployee, updateEmployee, type EmployeeFormDTO } from '@/api/employee'
import { useSessionStore } from '@/stores/session'
import './employee.css'

const props = defineProps<{ employeeId?: number }>()
const router = useRouter()
const session = useSessionStore()
const formRef = ref<FormInstance>()
const emptyForm = (): EmployeeFormDTO => ({
  username: '',
  name: '',
  phone: '',
  sex: '1',
  idNumber: '',
})
const form = reactive<EmployeeFormDTO>(emptyForm())
const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const savingAction = ref<'save' | 'continue' | null>(null)
const rules: FormRules<EmployeeFormDTO> = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { pattern: /^[a-z0-9]{3,20}$/, message: '账号输入不符，请输入3-20个字符', trigger: 'blur' },
  ],
  name: [{ required: true, whitespace: true, message: '请输入员工姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号!', trigger: 'blur' },
  ],
  idNumber: [
    { required: true, message: '请输入身份证号码', trigger: 'blur' },
    { pattern: /^(?:\d{15}|\d{17}[\dXx])$/, message: '身份证号码不正确', trigger: 'blur' },
  ],
}

async function loadEmployee() {
  Object.assign(form, emptyForm())
  loadError.value = ''
  if (!props.employeeId) return
  loading.value = true
  try {
    const employee = await getEmployee(props.employeeId)
    if (employee.username === 'admin') {
      ElMessage.warning('管理员账号不可修改')
      await router.replace('/employee')
      return
    }
    Object.assign(form, {
      username: employee.username,
      name: employee.name,
      phone: employee.phone,
      sex: employee.sex,
      idNumber: employee.idNumber,
    })
  } catch (error) {
    loadError.value = (error as Error).message
  } finally {
    loading.value = false
    await nextTick()
    formRef.value?.clearValidate()
  }
}

async function save(continueAdding: boolean) {
  if (saving.value || loading.value || loadError.value) return
  saving.value = true
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    saving.value = false
    return
  }
  savingAction.value = continueAdding ? 'continue' : 'save'
  const data = { ...form }
  try {
    if (props.employeeId) {
      await updateEmployee({ id: props.employeeId, ...data })
      if (props.employeeId === session.employee?.id) {
        session.updateEmployeeProfile({
          id: props.employeeId,
          userName: data.username,
          name: data.name,
        })
      }
    } else {
      await createEmployee(data)
    }
    ElMessage.success(props.employeeId ? '员工信息修改成功！' : '员工添加成功！')
    if (continueAdding) {
      Object.assign(form, emptyForm())
      await nextTick()
      formRef.value?.clearValidate()
    } else {
      saving.value = false
      await router.push('/employee')
    }
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
    savingAction.value = null
  }
}

function cancel() {
  void router.push('/employee')
}

onBeforeRouteLeave((to) => !saving.value || to.name === 'login')
watch(() => props.employeeId, loadEmployee, { immediate: true })
</script>

<style scoped>
.employee-form-heading {
  display: flex;
  height: 44px;
  align-items: center;
  margin-bottom: 20px;
  gap: 14px;
  color: #333;
}

.employee-form-heading button {
  padding: 0 14px 0 0;
  border: 0;
  border-right: 1px solid #d8dde3;
  background: transparent;
  color: inherit;
  font-size: 16px;
  cursor: pointer;
}

.employee-form-heading button:disabled {
  cursor: default;
  opacity: 0.5;
}

.employee-form-heading h1 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
}

.employee-form-panel {
  padding: 30px;
  border-radius: 4px;
  background: var(--admin-surface);
}

.employee-form-panel :deep(.el-form-item) {
  min-height: 40px;
  margin-bottom: 29px;
}

.employee-form-panel :deep(.el-form-item__label) {
  height: 40px;
  line-height: 40px;
}

.employee-form-panel .el-input {
  width: 293px;
  height: 40px;
}

.employee-form-panel :deep(.el-radio) {
  height: 40px;
}

.employee-form-panel :deep(.el-radio__input.is-checked + .el-radio__label) {
  color: #333;
}

.employee-form-panel :deep(.el-radio__input.is-checked .el-radio__inner::after) {
  background: #333;
}

.employee-form-panel .employee-id-number {
  margin-bottom: 39px;
}

.employee-form-footer {
  display: flex;
  justify-content: center;
  padding-top: 30px;
  border-top: 1px solid #ebeef5;
}

.employee-form-footer .el-button {
  min-width: 70px;
}

@media (max-width: 900px) {
  .employee-form-panel :deep(.el-form-item__label) {
    width: 120px !important;
  }
}
</style>
