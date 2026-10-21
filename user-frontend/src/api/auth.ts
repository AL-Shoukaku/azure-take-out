import { request } from '@/utils/request'

export interface UserCredentials {
  account: string
  password: string
}

export interface UserLoginVO {
  id: number
  username: string
  avatar: string | null
  token: string
}

export function login(data: UserCredentials): Promise<UserLoginVO> {
  return request<UserLoginVO>({ url: '/user/login', method: 'POST', data })
}

export function register(data: UserCredentials): Promise<null> {
  return request<null>({ url: '/user/register', method: 'POST', data })
}

export interface PasswordEditDTO {
  oldPassword: string
  newPassword: string
}

export function editPassword(data: PasswordEditDTO): Promise<null> {
  return request<null>({ url: '/user/editPassword', method: 'PUT', data })
}

export function logout(): Promise<null> {
  return request<null>({ url: '/user/logout', method: 'POST' })
}
