<template>
  <ElDialog
    v-model="visible"
    title="修改密码"
    width="560px"
    class="user-dialog"
    modal-class="user-dialog-overlay"
    align-center
    append-to-body
    destroy-on-close
    :show-close="!saving"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
  >
    <ElForm
      id="password-edit-form"
      ref="passwordForm"
      :model="form"
      :rules="rules"
      label-position="top"
      :hide-required-asterisk="true"
      :disabled="saving"
      :aria-busy="saving"
      @submit.prevent="save"
    >
      <ElFormItem label="旧密码" prop="oldPassword">
        <ElInput
          v-model="form.oldPassword"
          type="password"
          placeholder="请输入旧密码"
          autocomplete="current-password"
          show-password
        />
      </ElFormItem>
      <ElFormItem label="新密码" prop="newPassword">
        <ElInput
          v-model="form.newPassword"
          type="password"
          placeholder="至少 6 位字母或数字"
          autocomplete="new-password"
          show-password
          @input="revalidateConfirmation"
        />
      </ElFormItem>
      <ElFormItem label="确认新密码" prop="confirmPassword">
        <ElInput
          v-model="form.confirmPassword"
          type="password"
          placeholder="请再次输入新密码"
          autocomplete="new-password"
          show-password
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton :disabled="saving" @click="visible = false">取消</ElButton>
      <ElButton type="primary" native-type="submit" form="password-edit-form" :loading="saving"
        >确认修改</ElButton
      >
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { nextTick, reactive, ref, useTemplateRef, watch } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { editPassword } from '@/api/auth'

const visible = defineModel<boolean>({ required: true })
const passwordForm = useTemplateRef<FormInstance>('passwordForm')
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const saving = ref(false)
const rules: FormRules<typeof form> = {
  oldPassword: [{ required: true, whitespace: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9]{6,}$/,
      message: '密码至少 6 位，且只能包含字母和数字',
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        callback(value === form.newPassword ? undefined : new Error('两次输入的密码不一致'))
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

function revalidateConfirmation(): void {
  if (form.confirmPassword)
    void passwordForm.value?.validateField('confirmPassword').catch(() => undefined)
}

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
    ElMessage.error(error instanceof Error ? error.message : '密码修改失败，请稍后重试')
  } finally {
    saving.value = false
  }
}
</script>
