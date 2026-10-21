<template>
  <div class="user-layout">
    <UserHeader :loading="loadingUser" />
    <main class="user-content">
      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterView } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useSessionStore } from '@/stores/session'
import UserHeader from './components/UserHeader.vue'

const session = useSessionStore()
const loadingUser = ref(false)

onMounted(async () => {
  if (session.user) return
  loadingUser.value = true
  try {
    await session.restoreUser()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '用户信息获取失败，请稍后重试')
  } finally {
    loadingUser.value = false
  }
})
</script>

<style scoped>
.user-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--user-canvas);
}

.user-content {
  flex: 1;
  width: 100%;
  max-width: 1536px;
  margin-inline: auto;
  padding: 40px clamp(24px, 4vw, 64px);
}

@media (max-width: 600px) {
  .user-content {
    padding: 28px 20px;
  }
}
</style>
