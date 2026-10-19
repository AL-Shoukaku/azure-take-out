<template>
  <svg viewBox="0 0 1024 1024" fill="currentColor" fill-rule="evenodd" aria-hidden="true">
    <path v-for="(path, index) in menuIconPaths[name]" :key="index" :d="path" />
  </svg>
</template>

<script setup lang="ts">
import { menuIconPaths } from './menuIconPaths'

defineProps<{ name: keyof typeof menuIconPaths }>()
</script>
