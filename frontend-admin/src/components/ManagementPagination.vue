<template>
  <nav class="management-pagination" aria-label="分页">
    <ElPagination
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :page-sizes="[10, 20, 30, 40]"
      layout="total, sizes, prev, pager, next, jumper"
      :total="total"
      :disabled="disabled"
      @size-change="page = 1"
    />
  </nav>
</template>

<script setup lang="ts">
import { ElPagination } from 'element-plus'

defineProps<{ total: number; disabled?: boolean }>()
const page = defineModel<number>('page', { required: true })
const pageSize = defineModel<number>('pageSize', { required: true })
</script>

<style scoped>
.management-pagination {
  display: flex;
  justify-content: center;
  margin-top: 30px;
  overflow: auto;
}
</style>
