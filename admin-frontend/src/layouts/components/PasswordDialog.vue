<template>
  <ElDialog
    v-model="visible"
    title="修改密码"
    width="568px"
    top="15vh"
    class="admin-dialog password-dialog"
    append-to-body
    :show-close="!saving"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
  >
    <ElForm
      ref="passwordForm"
      :model="form"
      :rules="rules"
      label-width="85px"
      @submit.prevent="save"
    >
      <ElFormItem label="原始密码：" prop="oldPassword">
        <ElInput
          v-model="form.oldPassword"
          type="password"
          placeholder="请输入"
          aria-label="原始密码"
          autocomplete="current-password"
        />
      </ElFormItem>
      <ElFormItem label="新密码：" prop="newPassword">
        <ElInput
          v-model="form.newPassword"
          type="password"
          placeholder="6-20位密码，数字或字母，区分大小写"
          aria-label="新密码"
          autocomplete="new-password"
        />
      </ElFormItem>
      <ElFormItem label="确认密码：" prop="confirmPassword">
        <ElInput
          v-model="form.confirmPassword"
          type="password"
          placeholder="请输入"
          aria-label="确认密码"
          autocomplete="new-password"
          @keydown.enter.prevent="save"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton :disabled="saving" @click="visible = false">取消</ElButton>
      <ElButton type="primary" :loading="saving" @click="save">保存</ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { nextTick, reactive, ref, useTemplateRef, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { editPassword } from '@/api/auth'

const visible = defineModel<boolean>({ required: true })
const passwordForm = useTemplateRef<FormInstance>('passwordForm')
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const saving = ref(false)
const rules: FormRules<typeof form> = {
  oldPassword: [{ required: true, message: '请输入原始密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      pattern: /^[0-9A-Za-z]{6,20}$/,
      message: '6-20位密码，数字或字母，区分大小写',
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    {
      validator: (_rule, value: string, callback) => {
        if (!value) callback(new Error('请再次输入密码'))
        else if (value !== form.newPassword) callback(new Error('密码不一致，请重新输入密码'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

watch(visible, async () => {
  Object.assign(form, { oldPassword: '', newPassword: '', confirmPassword: '' })
  await nextTick()
  passwordForm.value?.clearValidate()
})

async function save(): Promise<void> {
  if (saving.value) return
  saving.value = true
  try {
    const valid = await passwordForm.value?.validate().catch(() => false)
    if (!valid) return
    await editPassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    visible.value = false
    ElMessage.success('密码修改成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '密码修改失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
:deep(.el-form-item) {
  margin-bottom: 26px;
}

:deep(.el-form-item__label) {
  justify-content: flex-start;
  height: 40px;
  padding-right: 8px;
  line-height: 40px;
}

:deep(.el-form-item.is-required .el-form-item__label::before) {
  display: none;
}

:deep(.el-input__wrapper) {
  min-height: 40px;
  padding: 0 12px;
}
</style>
