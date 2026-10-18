<template>
  <div class="admin-layout" :class="{ 'is-collapsed': collapsed }">
    <AdminSidebar :collapsed="collapsed" />
    <div class="layout-main">
      <AdminHeader :collapsed="collapsed" @toggle-sidebar="collapsed = !collapsed" />
      <main class="admin-content">
        <RouterView v-slot="{ Component, route }">
          <Transition name="fade-transform" mode="out-in">
            <component :is="Component" :key="route.path" />
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterView } from 'vue-router'
import AdminSidebar from './components/AdminSidebar.vue'
import AdminHeader from './components/AdminHeader.vue'
import '@/assets/styles/admin.css'

const collapsed = ref(localStorage.getItem('sky-admin-sidebar-collapsed') === 'true')
watch(collapsed, (value) => localStorage.setItem('sky-admin-sidebar-collapsed', String(value)))
</script>

<style scoped>
.admin-layout {
  --sidebar-width: 190px;
  --admin-header-background: #ffc100;
  min-width: 768px;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background: #f3f4f7;
}

.admin-layout.is-collapsed {
  --sidebar-width: 80px;
}

.layout-main {
  display: flex;
  height: 100%;
  min-width: 0;
  flex-direction: column;
  margin-left: var(--sidebar-width);
  transition: margin-left 0.28s;
}

.admin-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 20px;
}

.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.5s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(30px);
}
</style>
