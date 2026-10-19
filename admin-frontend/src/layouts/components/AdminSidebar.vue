<template>
  <aside class="admin-sidebar">
    <RouterLink to="/dashboard" class="sidebar-brand" aria-label="晴空食堂工作台">
      <img v-if="collapsed" :src="miniLogo" alt="晴空食堂" width="36" height="36" />
      <img v-else :src="logo" alt="晴空食堂" width="168" height="42" />
    </RouterLink>
    <nav class="sidebar-navigation" aria-label="管理菜单">
      <ElMenu
        :default-active="String(route.meta.activeMenu || route.path)"
        :collapse="collapsed"
        :collapse-transition="false"
        router
      >
        <ElMenuItem
          v-for="item in adminMenu"
          :key="item.path"
          :index="item.path"
          :aria-label="item.title"
        >
          <MenuIcon class="menu-icon" :name="item.icon" />
          <template #title>
            <span>{{ item.title }}</span>
          </template>
        </ElMenuItem>
      </ElMenu>
    </nav>
  </aside>
</template>

<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'
import { ElMenu, ElMenuItem } from 'element-plus'
import { adminMenu } from '@/router/menu'
import MenuIcon from './MenuIcon.vue'
import logo from '@/assets/images/brand/logo-with-name.png'
import miniLogo from '@/assets/images/brand/logo.png'

defineProps<{ collapsed: boolean }>()
const route = useRoute()
</script>

<style scoped>
.admin-sidebar {
  position: fixed;
  z-index: 40;
  inset: 0 auto 0 0;
  display: flex;
  width: var(--sidebar-width);
  flex-direction: column;
  overflow: hidden;
  background: var(--admin-sidebar);
  transition: width 0.28s;
}

.sidebar-brand {
  display: flex;
  height: 60px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  background: var(--admin-header-background);
}

.sidebar-brand img {
  display: block;
  border-radius: 4px;
  object-fit: contain;
}

.sidebar-navigation {
  flex: 1;
  overflow: auto;
  scrollbar-width: thin;
}

.sidebar-navigation :deep(.el-menu) {
  width: 100%;
  padding: 47px 15px 0;
  border: 0;
  background: transparent;
}

.sidebar-navigation :deep(.el-menu-item) {
  height: 42px;
  margin-bottom: 20px;
  padding: 0 0 0 17px !important;
  border-radius: 4px;
  color: #bfcbd9;
  font-size: 14px;
  line-height: 42px;
}

.sidebar-navigation :deep(.el-menu-item:hover) {
  background: #243f67;
  color: #fff;
}

.sidebar-navigation :deep(.el-menu-item.is-active) {
  background: var(--admin-surface-active);
  color: var(--admin-primary-hover);
  font-weight: 500;
}

.sidebar-navigation :deep(.el-menu--collapse .el-menu-item) {
  padding: 0 !important;
}

.sidebar-navigation :deep(.el-menu-tooltip__trigger) {
  justify-content: center;
  padding: 0 !important;
}

.menu-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  margin-right: 9px;
}

.sidebar-navigation :deep(.el-menu--collapse) .menu-icon {
  margin-right: 0;
}
</style>
