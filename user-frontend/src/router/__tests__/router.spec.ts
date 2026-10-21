import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { createAppRouter } from '..'
import { removeToken, setToken } from '@/utils/auth'

vi.mock('@/views/login/LoginPage.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/layouts/UserLayout.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/views/profile/ProfilePage.vue', () => ({ default: { template: '<div />' } }))

afterEach(removeToken)

describe('用户端登录守卫', () => {
  it('根路径重定向到点餐页', () => {
    const router = createAppRouter(createMemoryHistory())
    expect(router.getRoutes().find((route) => route.path === '/')?.redirect).toBe('/menu')
  })

  it.each(['/', '/menu', '/orders', '/addresses', '/profile'])(
    '没有 Cookie 凭证时，访问 %s 进入登录页',
    async (path) => {
      const router = createAppRouter(createMemoryHistory())
      await router.push(path)
      expect(router.currentRoute.value.path).toBe('/login')
    },
  )

  it('未登录时可直接打开登录页', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it.each(['/', '/menu', '/login'])('恢复 Cookie 后，访问 %s 进入点餐页', async (path) => {
    setToken('saved-user-token')
    const router = createAppRouter(createMemoryHistory())
    await router.push(path)
    expect(router.currentRoute.value.path).toBe('/menu')
  })

  it.each([
    ['/menu', '在线点餐'],
    ['/orders', '我的订单'],
    ['/addresses', '收货地址'],
    ['/profile', '我的'],
  ])('已登录可直接访问 %s，页面属于公共布局', async (path, section) => {
    setToken('saved-user-token')
    const router = createAppRouter(createMemoryHistory())
    await router.push(path)
    expect(router.currentRoute.value.path).toBe(path)
    expect(router.currentRoute.value.matched).toHaveLength(2)
    expect(router.currentRoute.value.meta.section).toBe(section)
  })

  it('每次导航都读取 Cookie，凭证移除后返回登录页', async () => {
    setToken('saved-user-token')
    const router = createAppRouter(createMemoryHistory())
    await router.push('/menu')
    removeToken()
    await router.push('/menu?check=session')
    expect(router.currentRoute.value.path).toBe('/login')
  })
})
