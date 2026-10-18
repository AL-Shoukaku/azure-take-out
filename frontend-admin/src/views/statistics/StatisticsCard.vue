<template>
  <section v-loading="loading" class="statistics-card" :aria-label="title" :aria-busy="loading">
    <h2>{{ title }}</h2>
    <ElAlert v-if="error" :title="error" type="error" show-icon :closable="false">
      <ElButton link type="primary" @click="$emit('retry')">重试</ElButton>
    </ElAlert>
    <slot v-else />
  </section>
</template>

<script setup lang="ts">
import { ElAlert, ElButton, vLoading } from 'element-plus'
defineProps<{ title: string; loading: boolean; error: string }>()
defineEmits<{ retry: [] }>()
</script>

<style scoped>
.statistics-card {
  min-width: 0;
  min-height: 418px;
  padding: 20px;
  border-radius: 4px;
  background: #fff;
}
.statistics-card h2 {
  margin: 4px 0 18px;
  color: #333;
  font-size: 16px;
  font-weight: 600;
}
</style>
