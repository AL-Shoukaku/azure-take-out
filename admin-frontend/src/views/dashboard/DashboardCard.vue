<template>
  <section v-loading="loading" class="dashboard-card" :aria-label="title" :aria-busy="loading">
    <header class="dashboard-card-heading">
      <h2>
        <span>{{ title }}</span
        ><time v-if="date" :datetime="date.replace(/\./g, '-')">{{ date }}</time>
      </h2>
      <RouterLink v-if="more" :to="more.to" class="dashboard-more"
        >{{ more.label }}<span aria-hidden="true">›</span></RouterLink
      >
    </header>
    <ElAlert v-if="error" type="error" :title="error" :closable="false" show-icon>
      <ElButton link @click="$emit('retry')">重试</ElButton>
    </ElAlert>
    <slot v-else />
  </section>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { ElAlert, ElButton, vLoading } from 'element-plus'
defineProps<{
  title: string
  date?: string
  more?: { label: string; to: string }
  loading: boolean
  error: string
}>()
defineEmits<{ retry: [] }>()
</script>

<style scoped>
.dashboard-card {
  min-width: 0;
  min-height: 160px;
  padding: 20px;
  border-radius: 4px;
  background: var(--admin-surface);
}
.dashboard-card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 4px 0 18px;
}
.dashboard-card-heading h2 {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 10px;
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.dashboard-card-heading time {
  color: #666;
  font-size: 14px;
  font-weight: 400;
}
.dashboard-more {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #666;
  text-decoration: none;
  white-space: nowrap;
  font-size: 14px;
}
.dashboard-more span {
  font-size: 27px;
  line-height: 18px;
}
.dashboard-more:hover {
  color: #333;
}
</style>
