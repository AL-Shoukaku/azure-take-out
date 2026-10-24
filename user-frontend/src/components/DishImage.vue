<template>
  <div class="dish-image">
    <img v-if="src && !failed" :src="src" :alt="name" loading="lazy" @error="failed = true" />
    <span v-else class="dish-image-fallback" role="img" :aria-label="`${name}，暂无图片`">
      <svg viewBox="0 0 32 32" fill="none" aria-hidden="true">
        <path
          d="M5 16h22M7 16a9 9 0 0 1 18 0M16 5v2M4 23h24M9 27h14"
          stroke="currentColor"
          stroke-width="1.6"
          stroke-linecap="round"
        />
      </svg>
    </span>
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
const props = defineProps<{ src: string | null; name: string }>()
const failed = ref(false)
watch(
  () => props.src,
  () => {
    failed.value = false
  },
)
</script>
<style scoped>
.dish-image {
  width: 76px;
  height: 76px;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 9px;
  background: #f1f5fb;
}
img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.dish-image-fallback {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  color: #9aadc8;
}
svg {
  width: 34px;
  height: 34px;
}
@media (max-width: 600px) {
  .dish-image {
    width: 64px;
    height: 64px;
  }
}
</style>
