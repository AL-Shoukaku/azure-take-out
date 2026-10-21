<template>
  <p class="section-placeholder">当前是 {{ section }} 板块</p>
</template>

<script setup lang="ts">
defineProps<{ section: string }>()
</script>

<style scoped>
.section-placeholder {
  margin: 0;
  color: #465872;
  font-size: 18px;
  line-height: 1.6;
}
</style>
