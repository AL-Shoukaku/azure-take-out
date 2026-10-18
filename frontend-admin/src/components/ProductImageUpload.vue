<template>
  <div class="product-image-upload">
    <div v-loading="uploading" class="upload-preview" :class="{ 'has-image': image }">
      <input
        ref="fileInput"
        type="file"
        accept=".png,.jpeg,.jpg"
        :disabled="disabled || uploading"
        aria-label="选择图片文件"
        @change="changeFile"
      />
      <template v-if="image">
        <img :src="image" alt="已上传图片" />
        <div class="upload-overlay">
          <button type="button" :disabled="disabled || uploading" @click="image = ''">
            删除图片
          </button>
          <button type="button" :disabled="disabled || uploading" @click="fileInput?.click()">
            重新上传
          </button>
        </div>
      </template>
      <button
        v-else
        class="upload-trigger"
        type="button"
        :disabled="disabled || uploading"
        @click="fileInput?.click()"
      >
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
          aria-hidden="true"
        >
          <path d="M12 16V3m-5 5 5-5 5 5M4 14v6h16v-6" />
        </svg>
        {{ uploading ? '上传中…' : '上传图片' }}
      </button>
    </div>
    <p>
      图片大小不超过1M<br />仅能上传 PNG JPEG JPG类型图片<br />建议上传200*200或300*300尺寸的图片
    </p>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, vLoading } from 'element-plus'
import { uploadImage } from '@/api/products'

defineProps<{ disabled?: boolean }>()
const image = defineModel<string>({ required: true })
const uploading = defineModel<boolean>('uploading', { default: false })
const fileInput = ref<HTMLInputElement>()

async function changeFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (
    !/\.(png|jpe?g)$/i.test(file.name) ||
    (file.type && !['image/png', 'image/jpeg'].includes(file.type))
  ) {
    ElMessage.error('仅能上传 PNG JPEG JPG类型图片')
    return
  }
  if (file.size > 1024 * 1024) {
    ElMessage.error('上传文件大小不能超过1M!')
    return
  }
  uploading.value = true
  try {
    image.value = await uploadImage(file)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.product-image-upload {
  display: flex;
  align-items: center;
  gap: 36px;
}
.product-image-upload p {
  margin: 0;
  color: #666;
  font-size: 12px;
  line-height: 18px;
}
.upload-preview {
  position: relative;
  width: 200px;
  height: 160px;
  flex-shrink: 0;
  overflow: hidden;
  border: 1px dashed #d9d9d9;
  border-radius: 4px;
}
.upload-preview.has-image {
  border-style: solid;
}
.upload-preview > input {
  display: none;
}
.upload-preview > img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.upload-trigger {
  display: flex;
  width: 100%;
  height: 100%;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 0;
  background: #fdfdfd;
  color: #333;
  cursor: pointer;
}
.upload-trigger svg {
  width: 25px;
  height: 25px;
}
.upload-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
  background: rgb(0 0 0 / 50%);
  opacity: 0;
  transition: opacity 0.3s;
}
.upload-preview:hover .upload-overlay,
.upload-preview:focus-within .upload-overlay {
  opacity: 1;
}
.upload-overlay button {
  width: 100px;
  height: 30px;
  border: 1px solid white;
  border-radius: 4px;
  background: transparent;
  color: white;
  cursor: pointer;
}
button:disabled {
  cursor: default;
  opacity: 0.6;
}
</style>
