import { createRouter, createWebHistory, type RouterHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

export function createAppRouter(
  history: RouterHistory = createWebHistory(import.meta.env.BASE_URL),
) {
  const router = createRouter({
    history,
    routes: [
      { path: '/', redirect: '/dashboard' },
      {
        path: '/login',
        name: 'login',
        component: () => import('@/views/login/LoginPage.vue'),
        meta: { title: '登录 · 苍穹外卖管理端' },
      },
      {
        path: '/dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/DashboardPage.vue'),
        meta: { title: '工作台 · 苍穹外卖管理端' },
      },
      { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
    ],
  })

  router.beforeEach((to) => {
    const token = getToken()
    if (!token && to.name !== 'login') return '/login'
    if (token && to.name === 'login') return '/dashboard'
  })

  router.afterEach((to) => {
    document.title = String(to.meta.title)
  })

  return router
}

export default createAppRouter()
