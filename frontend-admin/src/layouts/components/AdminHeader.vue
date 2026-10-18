<template>
  <header class="admin-header">
    <div class="header-left">
      <button
        class="sidebar-toggle"
        type="button"
        :aria-label="collapsed ? '展开菜单' : '折叠菜单'"
        :aria-expanded="!collapsed"
        @click="emit('toggle-sidebar')"
      >
        <img
          :src="hamburgerIcon"
          alt=""
          width="20"
          height="20"
          :class="{ 'is-expanded': !collapsed }"
        />
      </button>
      <span
        class="business-badge"
        :class="{ 'is-closed': status === 0, 'is-unknown': status === null }"
        role="status"
        aria-label="店铺营业状态"
      >
        {{ status === null ? '状态未获取' : status === 1 ? '营业中' : '打烊中' }}
      </span>
    </div>
    <div class="header-right">
      <ElButton text class="status-settings" :loading="loadingStatus" @click="openStatusDialog">
        <img :src="clockIcon" alt="" width="18" height="18" />营业状态设置
      </ElButton>
      <ElDropdown
        trigger="hover"
        placement="bottom-end"
        :show-timeout="0"
        :hide-timeout="100"
        :show-arrow="false"
        :offset="0"
        popper-class="employee-dropdown"
        @command="handleEmployeeCommand"
      >
        <button class="employee-trigger" type="button" :disabled="loggingOut">
          <span class="employee-name">{{ session.employee?.name || '员工' }}</span>
          <span class="employee-caret" aria-hidden="true" />
        </button>
        <template #dropdown>
          <ElDropdownMenu>
            <ElDropdownItem command="password"
              >修改密码<img :src="passwordIcon" alt="" width="18" height="18"
            /></ElDropdownItem>
            <ElDropdownItem command="logout" :disabled="loggingOut"
              >退出登录<img :src="logoutIcon" alt="" width="18" height="18"
            /></ElDropdownItem>
          </ElDropdownMenu>
        </template>
      </ElDropdown>
    </div>
    <ShopStatusDialog
      v-if="status !== null"
      v-model="statusDialogOpen"
      :status="status"
      @saved="status = $event"
    />
    <PasswordDialog v-model="passwordDialogOpen" />
    <OrderNotifications />
  </header>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElDropdown, ElDropdownItem, ElDropdownMenu, ElMessage } from 'element-plus'
import { getShopStatus, type ShopStatus } from '@/api/shop'
import { logout } from '@/api/auth'
import { useSessionStore } from '@/stores/session'
import ShopStatusDialog from './ShopStatusDialog.vue'
import PasswordDialog from './PasswordDialog.vue'
import OrderNotifications from './OrderNotifications.vue'
import hamburgerIcon from '@/assets/images/layout/hamburger.svg'
import clockIcon from '@/assets/images/layout/time.png'
import passwordIcon from '@/assets/images/layout/password.png'
import logoutIcon from '@/assets/images/layout/logout.png'

defineProps<{ collapsed: boolean }>()
const emit = defineEmits<{ 'toggle-sidebar': [] }>()
const router = useRouter()
const session = useSessionStore()
const status = ref<ShopStatus | null>(null)
const loadingStatus = ref(false)
const statusDialogOpen = ref(false)
const passwordDialogOpen = ref(false)
const loggingOut = ref(false)

async function loadStatus(): Promise<boolean> {
  loadingStatus.value = true
  try {
    status.value = await getShopStatus()
    return true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '营业状态获取失败')
    return false
  } finally {
    loadingStatus.value = false
  }
}

async function openStatusDialog(): Promise<void> {
  if (await loadStatus()) statusDialogOpen.value = true
}

async function handleEmployeeCommand(command: string): Promise<void> {
  if (command === 'password') {
    passwordDialogOpen.value = true
    return
  }
  if (command !== 'logout' || loggingOut.value) return
  loggingOut.value = true
  try {
    await logout()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '退出登录失败')
  } finally {
    session.clearSession()
    await router.replace('/login')
    loggingOut.value = false
  }
}

onMounted(loadStatus)
</script>

<style scoped>
.admin-header {
  position: relative;
  z-index: 30;
  display: flex;
  height: 60px;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  background: var(--admin-header-background);
  color: #333;
  font-size: 14px;
}

.header-left,
.header-right {
  display: flex;
  height: 100%;
  align-items: center;
}

.header-right {
  gap: 18px;
  padding-right: 20px;
}

.sidebar-toggle {
  display: flex;
  height: 100%;
  align-items: center;
  margin-right: 10px;
  padding: 0 12px 0 20px;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.sidebar-toggle:hover {
  background: rgb(0 0 0 / 2.5%);
}

.sidebar-toggle .is-expanded {
  transform: rotate(180deg);
}

.business-badge {
  padding: 0 6px;
  border: 1px solid #fff;
  border-radius: 4px;
  background: #fd3333;
  color: #fff;
  font-size: 14px;
  line-height: 20px;
  white-space: nowrap;
}

.business-badge.is-closed,
.business-badge.is-unknown {
  background: #6a6a6a;
}

.status-settings {
  height: 40px;
  padding: 0 10px;
  color: #333;
  font-size: 14px;
}

.status-settings img {
  margin-right: 4px;
}

.status-settings:hover {
  background: rgb(255 255 255 / 52%);
}

.employee-trigger {
  display: flex;
  width: 120px;
  height: 32px;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  border: 0;
  border-radius: 4px;
  background: rgb(255 255 255 / 52%);
  color: #333;
  font-size: 14px;
  text-align: left;
  cursor: pointer;
}

.employee-trigger[aria-expanded='true'] {
  border-radius: 4px 4px 0 0;
  background: #fff;
}

.employee-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.employee-caret {
  margin-left: 8px;
  border: 3px solid transparent;
  border-top-color: #333;
  transform: translateY(2px);
}

.employee-trigger[aria-expanded='true'] .employee-caret {
  transform: translateY(-2px) rotate(180deg);
}
</style>
