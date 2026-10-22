import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { AxiosError, type AxiosAdapter, type AxiosResponse } from 'axios'
import { login, register } from '../auth'
import { getUserInfo } from '../user'
import router from '@/router'
import { useSessionStore } from '@/stores/session'
import { getToken, removeToken, setToken } from '@/utils/auth'
import { http, request } from '@/utils/request'

const originalAdapter = http.defaults.adapter
const user = { id: 7, username: '用户7', avatar: null, token: 'user-token' }

function respondWith(data: unknown, status = 200): AxiosAdapter {
  return async (config) => {
    const response: AxiosResponse = { data, status, statusText: 'OK', headers: {}, config }
    if (status >= 400) {
      throw new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, undefined, response)
    }
    return response
  }
}

beforeEach(() => setActivePinia(createPinia()))
afterEach(() => {
  http.defaults.adapter = originalAdapter
  removeToken()
  document.cookie = 'admin_token=; Path=/; Max-Age=0'
  vi.restoreAllMocks()
})

describe('用户登录、注册和统一请求', () => {
  it('使用 account 字段登录，提交原始密码并解包用户资料', async () => {
    const adapter = vi.fn<AxiosAdapter>(respondWith({ code: 1, msg: null, data: user }))
    http.defaults.adapter = adapter
    const credentials = { account: 'customer', password: ' password ' }

    await expect(login(credentials)).resolves.toEqual(user)
    const config = adapter.mock.calls[0]![0]
    expect(config.baseURL).toBe('/api')
    expect(config.url).toBe('/user/login')
    expect(config.method).toBe('post')
    expect(JSON.parse(config.data as string)).toEqual(credentials)
  })

  it('注册仅提交后端要求的账号和密码，成功响应没有 token', async () => {
    const adapter = vi.fn<AxiosAdapter>(respondWith({ code: 1, msg: null, data: null }))
    http.defaults.adapter = adapter
    const credentials = { account: 'customer', password: '123' }
    await expect(register(credentials)).resolves.toBeNull()
    const config = adapter.mock.calls[0]![0]
    expect(config.url).toBe('/user/register')
    expect(config.method).toBe('post')
    expect(JSON.parse(config.data as string)).toEqual(credentials)
    expect(getToken()).toBe('')
  })

  it.each(['账号不存在', '密码错误', '账号被锁定', '账号已存在'])(
    '显示后端业务错误：%s',
    async (msg) => {
      http.defaults.adapter = respondWith({ code: 0, msg, data: null })
      await expect(login({ account: 'customer', password: '123' })).rejects.toThrow(msg)
      expect(getToken()).toBe('')
    },
  )

  it('从 Cookie 读取凭证并使用用户端 authentication 请求头', async () => {
    document.cookie = 'admin_token=other-admin-token; Path=/'
    setToken('saved-user-token')
    const adapter = vi.fn<AxiosAdapter>(respondWith({ code: 1, msg: null, data: null }))
    http.defaults.adapter = adapter
    await request({ url: '/user/info' })
    expect(adapter.mock.calls[0]![0].headers.get('authentication')).toBe('saved-user-token')
    expect(adapter.mock.calls[0]![0].headers.has('token')).toBe(false)
  })

  it('读取当前用户 ID、账号、昵称和头像', async () => {
    const profile = {
      id: 7,
      account: 'customer',
      username: '演示用户',
      avatar: '/images/user/avatar.png',
      name: null,
      phone: null,
      sex: null,
      idNumber: null,
    }
    const adapter = vi.fn<AxiosAdapter>(respondWith({ code: 1, msg: null, data: profile }))
    http.defaults.adapter = adapter
    await expect(getUserInfo()).resolves.toEqual(profile)
    expect(adapter.mock.calls[0]![0].url).toBe('/user/info')
    expect(adapter.mock.calls[0]![0].method).toBe('get')
  })

  it.each([
    ['ERR_NETWORK', '网络异常，请检查网络连接'],
    ['ECONNABORTED', '请求超时，请稍后重试'],
  ])('处理请求异常 %s', async (code, message) => {
    http.defaults.adapter = async () => {
      throw new AxiosError('request failed', code)
    }
    await expect(login({ account: 'customer', password: '123' })).rejects.toThrow(message)
  })

  it('401 时清除 Cookie 和 Pinia 会话并进入登录页', async () => {
    document.cookie = 'admin_token=other-admin-token; Path=/'
    const session = useSessionStore()
    session.startSession(user)
    const replace = vi.spyOn(router, 'replace').mockResolvedValue(undefined)
    http.defaults.adapter = respondWith(null, 401)
    await expect(request({ url: '/user/info' })).rejects.toThrow('登录已过期，请重新登录')
    expect(getToken()).toBe('')
    expect(session.token).toBe('')
    expect(session.user).toBeNull()
    expect(replace).toHaveBeenCalledWith('/login')
    expect(document.cookie).toContain('admin_token=other-admin-token')
  })
})
