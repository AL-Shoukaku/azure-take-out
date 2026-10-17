import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import router from '@/router'
import { useSessionStore } from '@/stores/session'
import type { ApiResult } from '@/types/api'
import { getToken } from './auth'

export const http = axios.create({ baseURL: '/api', timeout: 10_000 })

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.set('token', token)
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<{ msg?: string }>) => {
    if (error.response?.status === 401) {
      useSessionStore().clearSession()
      void router.replace('/login')
      return Promise.reject(new Error('登录已过期，请重新登录'))
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
  const { data } = await http.request<ApiResult<T>>(config)
  if (data.code !== 1) throw new Error(data.msg || '请求失败，请稍后重试')
  return data.data
}
