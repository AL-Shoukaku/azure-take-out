import { createRouter, createWebHistory, type RouterHistory } from 'vue-router'
import { getToken } from '@/utils/auth'
import { adminMenu } from './menu'

export function createAppRouter(
  history: RouterHistory = createWebHistory(import.meta.env.BASE_URL),
) {
  const router = createRouter({
    history,
    routes: [
      {
        path: '/',
        component: () => import('@/layouts/AdminLayout.vue'),
        redirect: '/dashboard',
        children: [
          ...adminMenu.map((item) => ({
            path: item.path.slice(1),
            name: item.path.slice(1),
            component:
              item.path === '/employee'
                ? () => import('@/views/employee/EmployeeListPage.vue')
                : () => import('@/views/PlaceholderPage.vue'),
            props: item.path === '/employee' ? undefined : { pageTitle: item.title },
            meta: { title: `${item.title} · 苍穹外卖管理端`, pageTitle: item.title },
          })),
          {
            path: 'employee/add',
            name: 'employee-form',
            component: () => import('@/views/employee/EmployeeFormPage.vue'),
            props: (route) => ({ employeeId: route.query.id ? Number(route.query.id) : undefined }),
            meta: { title: '员工资料 · 苍穹外卖管理端', activeMenu: '/employee' },
            beforeEnter: (to) =>
              to.query.id && !/^[1-9]\d*$/.test(String(to.query.id)) ? '/employee' : true,
          },
        ],
      },
      {
        path: '/login',
        name: 'login',
        component: () => import('@/views/login/LoginPage.vue'),
        meta: { title: '登录 · 苍穹外卖管理端' },
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
