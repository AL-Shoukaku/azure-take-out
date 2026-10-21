<template>
  <header class="site-header">
    <div class="header-inner">
      <RouterLink class="brand-link" to="/menu" aria-label="碧蓝外卖，返回在线点餐">
        <img :src="logo" alt="碧蓝外卖" width="188" height="47" />
      </RouterLink>

      <nav class="main-navigation" aria-label="主导航">
        <RouterLink
          v-for="item in navigation"
          :key="item.path"
          class="navigation-link"
          :to="item.path"
          active-class="is-active"
        >
          <svg class="navigation-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              :d="item.icon"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <RouterLink
        class="user-link"
        to="/profile"
        :aria-label="`你好，${username}`"
        :aria-busy="loading"
      >
        <UserAvatar :src="session.user?.avatar" />
        <span class="user-greeting" :title="`你好，${username}`">你好，{{ username }}</span>
      </RouterLink>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import UserAvatar from '@/components/UserAvatar.vue'
import { useSessionStore } from '@/stores/session'
import logo from '@/assets/images/brand/logo-with-name.webp'

defineProps<{ loading: boolean }>()

const session = useSessionStore()
const username = computed(() => session.user?.username || '用户')
const navigation = [
  {
    path: '/menu',
    label: '在线点餐',
    icon: 'M4 3v5a3 3 0 0 0 6 0V3M7 3v18m9-18v18m0-18c4 0 4 9 0 9',
  },
  { path: '/orders', label: '我的订单', icon: 'M7 3h10v3H7zM7 5H4v16h16V5h-3M8 11h8m-8 4h8' },
  {
    path: '/addresses',
    label: '收货地址',
    icon: 'M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0ZM15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  },
  {
    path: '/profile',
    label: '我的',
    icon: 'M20 21v-2a8 8 0 0 0-16 0v2M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  },
]
</script>

<style scoped>
.site-header {
  position: sticky;
  top: 0;
  z-index: 20;
  flex-shrink: 0;
  border-bottom: 1px solid #e4ebf6;
  background: #fff;
}

.header-inner {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  width: 100%;
  max-width: 1536px;
  min-height: 88px;
  margin-inline: auto;
  padding-inline: clamp(24px, 4vw, 64px);
}

.brand-link {
  display: block;
  justify-self: start;
  min-width: 0;
  max-width: 100%;
}

.brand-link img {
  display: block;
  width: 188px;
  max-width: 100%;
  height: auto;
}

.main-navigation {
  display: flex;
  align-self: stretch;
  align-items: stretch;
  gap: 32px;
}

.navigation-link {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0;
  color: #7b8aa2;
  font-size: 15px;
  line-height: 24px;
  text-decoration: none;
  white-space: nowrap;
  transition: color 150ms;
}

.navigation-link:hover,
.navigation-link.is-active {
  color: var(--user-primary);
}

.navigation-link.is-active::after {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: var(--user-primary);
  content: '';
}

.navigation-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
}

.user-link {
  display: flex;
  align-items: center;
  justify-self: end;
  gap: 12px;
  min-width: 0;
  max-width: 100%;
  margin-left: 20px;
  color: #465872;
  font-size: 14px;
  text-decoration: none;
}

.user-link:hover {
  color: var(--user-primary);
}

.user-greeting {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand-link:focus-visible,
.navigation-link:focus-visible,
.user-link:focus-visible {
  border-radius: 4px;
  outline: 2px solid var(--user-primary);
  outline-offset: 5px;
}

@media (max-width: 900px) {
  .header-inner {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 12px 16px;
    padding: 16px 24px 0;
  }

  .brand-link img {
    width: 156px;
  }

  .user-link {
    grid-column: 2;
    grid-row: 1;
    max-width: 180px;
    margin-left: 0;
  }

  .main-navigation {
    grid-column: 1 / -1;
    justify-content: center;
    gap: 40px;
  }

  .navigation-link {
    min-height: 48px;
  }
}

@media (max-width: 480px) {
  .header-inner {
    padding-inline: 20px;
  }

  .main-navigation {
    justify-content: space-between;
    gap: 12px;
  }

  .navigation-link {
    font-size: 14px;
  }

  .navigation-icon {
    display: none;
  }

  .user-link {
    gap: 8px;
    max-width: 148px;
    font-size: 13px;
  }
}
</style>
