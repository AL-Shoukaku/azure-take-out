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

export function login(credentials: LoginCredentials): Promise<EmployeeLoginVO> {
  return request<EmployeeLoginVO>({
    url: '/employee/login',
    method: 'POST',
    data: credentials,
  })
}
