import { createRouter, createWebHistory, type RouterHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

export function createAppRouter(
  history: RouterHistory = createWebHistory(import.meta.env.BASE_URL),
) {
  const router = createRouter({
    history,
    routes: [
      { path: '/login', name: 'login', component: () => import('@/views/login/LoginPage.vue') },
      {
        path: '/',
        redirect: '/menu',
        component: () => import('@/layouts/UserLayout.vue'),
        children: [
          {
            path: 'menu',
            name: 'menu',
            component: () => import('@/views/menu/MenuPage.vue'),
            meta: { section: '在线点餐' },
          },
          {
            path: 'orders',
            name: 'orders',
            component: () => import('@/views/order/OrderPage.vue'),
            meta: { section: '我的订单' },
          },
          {
            path: 'addresses',
            name: 'addresses',
            component: () => import('@/views/address/AddressPage.vue'),
            meta: { section: '收货地址' },
          },
          {
            path: 'profile',
            name: 'profile',
            component: () => import('@/views/profile/ProfilePage.vue'),
            meta: { section: '我的' },
          },
          {
            path: ':pathMatch(.*)*',
            name: 'not-found',
            component: () => import('@/views/error/NotFoundPage.vue'),
          },
        ],
      },
    ],
  })

  router.beforeEach((to) => {
    const token = getToken()
    if (to.name !== 'login' && !token) return '/login'
    if (to.name === 'login' && token) return '/menu'
  })

  return router
}

export default createAppRouter()
