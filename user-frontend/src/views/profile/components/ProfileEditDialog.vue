<template>
  <ElDialog
    v-model="visible"
    title="编辑资料"
    width="560px"
    class="user-dialog"
    modal-class="user-dialog-overlay"
    align-center
    append-to-body
    destroy-on-close
    :show-close="!busy"
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
  >
    <ElForm
      id="profile-edit-form"
      ref="profileForm"
      :model="form"
      :rules="rules"
      label-position="top"
      :hide-required-asterisk="true"
      :disabled="saving"
      :aria-busy="busy"
      @submit.prevent="save"
    >
      <ElFormItem label="头像" prop="avatar">
        <AvatarUpload v-model="form.avatar" v-model:uploading="uploading" :disabled="saving" />
      </ElFormItem>
      <ElFormItem label="昵称" prop="username">
        <ElInput
          v-model="form.username"
          name="nickname"
          placeholder="请输入昵称"
          :maxlength="32"
          autocomplete="nickname"
        />
      </ElFormItem>
      <ElFormItem label="真实姓名" prop="name">
        <ElInput
          v-model="form.name"
          placeholder="请输入真实姓名（选填）"
          :maxlength="32"
          autocomplete="name"
        />
      </ElFormItem>
      <ElFormItem label="性别" prop="sex">
        <ElSelect v-model="form.sex" aria-label="性别">
          <ElOption label="未填写" value="" />
          <ElOption label="男" value="1" />
          <ElOption label="女" value="0" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="手机号" prop="phone">
        <ElInput
          v-model="form.phone"
          placeholder="请输入手机号（选填）"
          :maxlength="11"
          inputmode="tel"
          autocomplete="tel"
        />
      </ElFormItem>
      <ElFormItem label="身份证号" prop="idNumber">
        <ElInput v-model="form.idNumber" placeholder="请输入身份证号（选填）" :maxlength="18" />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton :disabled="busy" @click="visible = false">取消</ElButton>
      <ElButton
        type="primary"
        native-type="submit"
        form="profile-edit-form"
        :loading="saving"
        :disabled="uploading"
        >保存</ElButton
      >
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, useTemplateRef, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElSelect,
  ElOption,
  ElMessage,
} from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { updateUserInfo, type UserInfoDTO, type UserInfoVO } from '@/api/user'
import AvatarUpload from './AvatarUpload.vue'

const props = defineProps<{ profile: UserInfoVO }>()
const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ saved: [value: UserInfoDTO] }>()
const profileForm = useTemplateRef<FormInstance>('profileForm')
const saving = ref(false)
const uploading = ref(false)
const busy = computed(() => saving.value || uploading.value)
const form = reactive<UserInfoDTO>({
  id: props.profile.id,
  username: '',
  name: '',
  sex: '',
  phone: '',
  idNumber: '',
  avatar: '',
})
const rules: FormRules<UserInfoDTO> = {
  username: [
    { required: true, whitespace: true, message: '请输入昵称', trigger: 'blur' },
    { max: 32, message: '昵称不能超过 32 个字符', trigger: 'blur' },
  ],
  name: [{ max: 32, message: '真实姓名不能超过 32 个字符', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }],
  idNumber: [
    { pattern: /^(?:\d{15}|\d{17}[\dXx])$/, message: '请输入正确的身份证号', trigger: 'blur' },
  ],
}

watch(visible, async (open) => {
  if (!open) return
  Object.assign(form, {
    id: props.profile.id,
    username: props.profile.username,
    name: props.profile.name || '',
    sex: props.profile.sex || '',
    phone: props.profile.phone || '',
    idNumber: props.profile.idNumber || '',
    avatar: props.profile.avatar || '',
  })
  await nextTick()
  profileForm.value?.clearValidate()
})

async function save(): Promise<void> {
  if (busy.value) return
  saving.value = true
  try {
    const valid = await profileForm.value?.validate().catch(() => false)
    if (!valid) return
    const data: UserInfoDTO = {
      ...form,
      username: form.username.trim(),
      name: form.name.trim(),
      phone: form.phone.trim(),
      idNumber: form.idNumber.trim().toUpperCase(),
    }
    await updateUserInfo(data)
    emit('saved', data)
    visible.value = false
    ElMessage.success('资料保存成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资料保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}
</script>
