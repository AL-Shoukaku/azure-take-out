import { request } from '@/utils/request'

export interface LoginCredentials {
  username: string
  password: string
}

export interface EmployeeLoginVO {
  id: number
  userName: string
  name: string
  token: string
}

export interface PasswordEditDTO {
  oldPassword: string
  newPassword: string
}

export function editPassword(data: PasswordEditDTO): Promise<null> {
  return request<null>({ url: '/employee/editPassword', method: 'PUT', data })
}

export function logout(): Promise<null> {
  return request<null>({ url: '/employee/logout', method: 'POST' })
}

export function login(credentials: LoginCredentials): Promise<EmployeeLoginVO> {
  return request<EmployeeLoginVO>({
    url: '/employee/login',
    method: 'POST',
    data: credentials,
  })
}
