<template>
  <ElAvatar :src="src || undefined" :size="size" :alt="alt" class="user-avatar">
    <svg class="avatar-fallback" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="8" r="4" stroke="currentColor" stroke-width="1.6" />
      <path
        d="M4 21v-2a8 8 0 0 1 16 0v2"
        stroke="currentColor"
        stroke-width="1.6"
        stroke-linecap="round"
      />
    </svg>
  </ElAvatar>
</template>

<script setup lang="ts">
import { ElAvatar } from 'element-plus'

withDefaults(defineProps<{ src?: string | null; size?: number; alt?: string }>(), {
  src: null,
  size: 40,
  alt: '用户头像',
})
</script>

<style scoped>
.user-avatar {
  flex-shrink: 0;
  background: #edf3ff;
  color: #7495d0;
}

.avatar-fallback {
  width: 55%;
  height: 55%;
}
</style>
