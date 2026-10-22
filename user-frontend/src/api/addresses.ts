import { request } from '@/utils/request'

export interface Address {
  id: number
  userId: number
  consignee: string
  phone: string
  sex: string | null
  provinceCode: string | null
  provinceName: string | null
  cityCode: string | null
  cityName: string | null
  districtCode: string | null
  districtName: string | null
  detail: string
  label: string | null
  isDefault: 0 | 1
}

export interface AddressFormData {
  consignee: string
  phone: string
  provinceCode: string
  provinceName: string
  cityCode: string
  cityName: string
  districtCode: string
  districtName: string
  detail: string
  label: string
}

export function getAddresses(): Promise<Address[]> {
  return request<Address[]>({ url: '/addressBook/list', method: 'GET' })
}

export function getAddress(id: number): Promise<Address> {
  return request<Address>({ url: `/addressBook/${id}`, method: 'GET' })
}

export function createAddress(data: AddressFormData): Promise<null> {
  return request<null>({ url: '/addressBook', method: 'POST', data })
}

export function updateAddress(data: AddressFormData & { id: number }): Promise<null> {
  return request<null>({ url: '/addressBook', method: 'PUT', data })
}

export function setDefaultAddress(id: number): Promise<null> {
  return request<null>({ url: '/addressBook/default', method: 'PUT', data: { id } })
}

export function deleteAddress(id: number): Promise<null> {
  return request<null>({ url: '/addressBook', method: 'DELETE', params: { id } })
}
