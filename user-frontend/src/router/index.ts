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
            path: 'coupons',
            meta: { section: '优惠券' },
            children: [
              {
                path: '',
                name: 'coupons',
                component: () => import('@/views/coupon/CouponEntryPage.vue'),
              },
              {
                path: 'take',
                name: 'coupon-take',
                component: () => import('@/views/coupon/CouponPage.vue'),
              },
              {
                path: 'mine',
                name: 'coupon-mine',
                component: () => import('@/views/coupon/MyCouponPage.vue'),
              },
            ],
          },
          {
            path: 'orders',
            meta: { section: '我的订单' },
            children: [
              {
                path: '',
                name: 'orders',
                component: () => import('@/views/order/OrderEntryPage.vue'),
              },
              {
                path: 'takeout',
                name: 'takeout-orders',
                component: () => import('@/views/order/OrderPage.vue'),
              },
              {
                path: 'coupons',
                name: 'coupon-orders',
                component: () => import('@/views/order/CouponOrderPage.vue'),
              },
            ],
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
