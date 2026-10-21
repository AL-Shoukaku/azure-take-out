import { request } from '@/utils/request'

export interface UserInfoVO {
  id: number
  account: string
  username: string
  name: string | null
  phone: string | null
  sex: string | null
  idNumber: string | null
  avatar: string | null
}

export interface UserInfoDTO {
  id: number
  username: string
  name: string
  sex: string
  phone: string
  idNumber: string
  avatar: string
}

export function getUserInfo(): Promise<UserInfoVO> {
  return request<UserInfoVO>({ url: '/user/info', method: 'GET' })
}

export function updateUserInfo(data: UserInfoDTO): Promise<null> {
  return request<null>({ url: '/user/update', method: 'PUT', data })
}

export function uploadAvatar(file: File): Promise<string> {
  const data = new FormData()
  data.append('file', file)
  return request<string>({ url: '/common/upload', method: 'POST', data })
}
