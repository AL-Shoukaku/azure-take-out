<template>
  <section class="placeholder-page">当前为{{ pageTitle }}页面</section>
</template>

<script setup lang="ts">
defineProps<{ pageTitle: string }>()
</script>

<style scoped>
.placeholder-page {
  min-height: 160px;
  padding: 24px;
  border-radius: 4px;
  background: #fff;
  font-size: 16px;
}
</style>
