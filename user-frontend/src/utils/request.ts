import axios, { AxiosError, type AxiosRequestConfig, type AxiosRequestHeaders } from 'axios'
import router from '@/router'
import { useSessionStore } from '@/stores/session'
import type { ApiResult } from '@/types/api'
import { getToken } from './auth'

export const http = axios.create({ baseURL: '/api', timeout: 10_000 })

function expireSession(headers: AxiosRequestHeaders | undefined): Error {
  const requestToken = headers?.get('authentication') || ''
  // 迟到的旧请求不能清除用户刚重新登录得到的新会话。
  if (requestToken === getToken()) {
    useSessionStore().clearSession()
    void router.replace('/login')
  }
  return new Error('登录已过期，请重新登录')
}

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.set('authentication', token)
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<{ msg?: string }>) => {
    if (error.response?.status === 401) {
      return Promise.reject(expireSession(error.config?.headers))
    }

    const message =
      error.response?.data?.msg ||
      (error.code === AxiosError.ECONNABORTED
        ? '请求超时，请稍后重试'
        : error.response
          ? '服务暂不可用，请稍后重试'
          : '网络异常，请检查网络连接')
    return Promise.reject(new Error(message))
  },
)

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const { data, config: sentConfig } = await http.request<ApiResult<T>>(config)
  // 当前用户不存在时，即使接口返回 HTTP 200，也按登录失效处理。
  if (data.code !== 1 && config.url === '/user/info' && data.msg === '用户不存在') {
    throw expireSession(sentConfig.headers)
  }
  if (data.code !== 1) throw new Error(data.msg || '请求失败，请稍后重试')
  return data.data
}
