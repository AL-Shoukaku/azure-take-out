import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { AxiosError, type AxiosAdapter, type AxiosResponse } from 'axios'
import { login } from '../auth'
import router from '@/router'
import { useSessionStore } from '@/stores/session'
import { getToken, removeToken, setToken } from '@/utils/auth'
import { http, request } from '@/utils/request'

const originalAdapter = http.defaults.adapter
const employee = { id: 1, userName: 'admin', name: '管理员', token: 'login-token' }

function respondWith(data: unknown, status = 200): AxiosAdapter {
  return async (config) => {
    const response: AxiosResponse = { data, status, statusText: 'OK', headers: {}, config }
    if (status >= 400) {
      throw new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, undefined, response)
    }
    return response
  }
}

beforeEach(() => {
  setActivePinia(createPinia())
})

afterEach(() => {
  http.defaults.adapter = originalAdapter
  removeToken()
  vi.restoreAllMocks()
})

describe('登录接口和统一请求', () => {
  it('向员工登录接口提交原始密码并解包后端数据', async () => {
    const adapter = vi.fn<AxiosAdapter>(respondWith({ code: 1, msg: null, data: employee }))
    http.defaults.adapter = adapter
    const credentials = { username: 'admin', password: ' password123 ' }

    await expect(login(credentials)).resolves.toEqual(employee)
    expect(adapter).toHaveBeenCalledOnce()
    const config = adapter.mock.calls[0]![0]
    expect(config.baseURL).toBe('/api')
    expect(config.url).toBe('/employee/login')
    expect(config.method).toBe('post')
    expect(JSON.parse(config.data as string)).toEqual(credentials)
  })

  it.each(['账号不存在', '密码错误', '账号被锁定'])('保留业务报错：%s', async (msg) => {
    http.defaults.adapter = respondWith({ code: 0, msg, data: null })
    await expect(login({ username: 'admin', password: '123456' })).rejects.toThrow(msg)
    expect(getToken()).toBe('')
  })

  it('后续请求从 Cookie 读取 token 请求头', async () => {
    setToken('existing-token')
    const adapter = vi.fn<AxiosAdapter>(respondWith({ code: 1, msg: null, data: null }))
    http.defaults.adapter = adapter
    await request({ url: '/employee/page' })
    expect(adapter.mock.calls[0]![0].headers.get('token')).toBe('existing-token')
  })

  it('网络断开时给出可读提示', async () => {
    http.defaults.adapter = async () => {
      throw new AxiosError('Network Error', 'ERR_NETWORK')
    }
    await expect(login({ username: 'admin', password: '123456' })).rejects.toThrow(
      '网络异常，请检查网络连接',
    )
  })

  it('请求超时时允许用户稍后重试', async () => {
    http.defaults.adapter = async () => {
      throw new AxiosError('timeout', 'ECONNABORTED')
    }
    await expect(login({ username: 'admin', password: '123456' })).rejects.toThrow(
      '请求超时，请稍后重试',
    )
  })

  it('401 时清除登录态并返回登录页', async () => {
    const session = useSessionStore()
    session.startSession({ ...employee, token: 'expired-token' })
    const replace = vi.spyOn(router, 'replace').mockResolvedValue(undefined)
    http.defaults.adapter = respondWith(null, 401)

    await expect(request({ url: '/employee/page' })).rejects.toThrow('登录已过期，请重新登录')
    expect(getToken()).toBe('')
    expect(session.token).toBe('')
    expect(session.employee).toBeNull()
    expect(replace).toHaveBeenCalledWith('/login')
  })
})
