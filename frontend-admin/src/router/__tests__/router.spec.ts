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

  it.each(['/', '/dashboard'])('未登录访问 %s 时进入登录页', async (path) => {
    const router = createAppRouter(createMemoryHistory())
    await router.push(path)
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('未登录时允许直接打开登录页', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/login')
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
