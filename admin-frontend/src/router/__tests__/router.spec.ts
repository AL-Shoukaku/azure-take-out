import { afterEach, describe, expect, it } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { createAppRouter } from '..'
import { removeToken, setToken } from '@/utils/auth'

afterEach(removeToken)

describe('登录路由守卫', () => {
  it('根路径重定向到工作台', () => {
    const router = createAppRouter(createMemoryHistory())
    expect(router.getRoutes().find((route) => route.path === '/')?.redirect).toBe('/dashboard')
  })

  it.each([
    '/',
    '/dashboard',
    '/statistics',
    '/order',
    '/dish',
    '/setmeal',
    '/category',
    '/employee',
    '/employee/add',
    '/employee/add?id=2',
  ])('未登录访问 %s 时进入登录页', async (path) => {
    const router = createAppRouter(createMemoryHistory())
    await router.push(path)
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it.each([
    ['/dashboard', '工作台'],
    ['/statistics', '数据统计'],
    ['/order', '订单管理'],
    ['/dish', '菜品管理'],
    ['/setmeal', '套餐管理'],
    ['/category', '分类管理'],
    ['/employee', '员工管理'],
  ])('登录后可以打开 %s，页面归属公共后台布局', async (path, pageTitle) => {
    setToken('persisted-token')
    const router = createAppRouter(createMemoryHistory())
    await router.push(path)
    expect(router.currentRoute.value.path).toBe(path)
    expect(router.currentRoute.value.meta.pageTitle).toBe(pageTitle)
    expect(router.currentRoute.value.matched).toHaveLength(2)
  })

  it('未登录时允许直接打开登录页', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('员工新增和修改共用表单路由，保留员工管理菜单选中状态', async () => {
    setToken('persisted-token')
    const router = createAppRouter(createMemoryHistory())
    await router.push('/employee/add?id=2')
    expect(router.currentRoute.value.path).toBe('/employee/add')
    expect(router.currentRoute.value.meta.activeMenu).toBe('/employee')
    expect(router.currentRoute.value.matched).toHaveLength(2)
  })

  it('读取 Cookie，重新创建应用后仍可进入工作台', async () => {
    setToken('persisted-token')
    const router = createAppRouter(createMemoryHistory())
    await router.push('/')
    expect(router.currentRoute.value.path).toBe('/dashboard')
  })

  it('已登录访问登录页时进入工作台', async () => {
    setToken('persisted-token')
    const router = createAppRouter(createMemoryHistory())
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/dashboard')
  })

  it('Cookie 被移除后，下一次访问工作台须重新登录', async () => {
    setToken('persisted-token')
    const router = createAppRouter(createMemoryHistory())
    await router.push('/dashboard')
    removeToken()
    await router.push('/dashboard?check=session')
    expect(router.currentRoute.value.path).toBe('/login')
  })
})
