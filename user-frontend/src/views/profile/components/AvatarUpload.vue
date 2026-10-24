<template>
  <div class="avatar-upload">
    <UserAvatar :src="image" :size="64" alt="头像预览" />
    <div>
      <input
        ref="fileInput"
        type="file"
        accept=".png,.jpg,.jpeg"
        aria-label="选择头像文件"
        :disabled="disabled || uploading"
        @change="changeFile"
      />
      <ElButton :loading="uploading" :disabled="disabled" @click="fileInput?.click()">
        {{ image ? '更换头像' : '上传头像' }}
      </ElButton>
      <p>支持 PNG、JPEG 格式，大小不超过 1 MB</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useTemplateRef } from 'vue'
import { ElButton, ElMessage } from 'element-plus'
import { uploadAvatar } from '@/api/user'
import UserAvatar from '@/components/UserAvatar.vue'

defineProps<{ disabled: boolean }>()
const image = defineModel<string>({ required: true })
const uploading = defineModel<boolean>('uploading', { default: false })
const fileInput = useTemplateRef<HTMLInputElement>('fileInput')

async function changeFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (
    !/\.(png|jpe?g)$/i.test(file.name) ||
    (file.type && !['image/png', 'image/jpeg'].includes(file.type))
  ) {
    ElMessage.error('仅支持 PNG、JPEG 格式的头像')
    return
  }
  if (file.size > 1024 * 1024) {
    ElMessage.error('头像大小不能超过 1 MB')
    return
  }
  uploading.value = true
  try {
    image.value = await uploadAvatar(file)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '头像上传失败，请稍后重试')
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.avatar-upload {
  display: flex;
  align-items: center;
  gap: 16px;
}

input {
  display: none;
}

p {
  margin: 8px 0 0;
  color: #8997aa;
  font-size: 12px;
  line-height: 1.5;
}
</style>
