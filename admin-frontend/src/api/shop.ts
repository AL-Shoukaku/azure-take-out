import { request } from '@/utils/request'

export type ShopStatus = 0 | 1

export function getShopStatus(): Promise<ShopStatus> {
  return request<ShopStatus>({ url: '/shop/status', method: 'GET' })
}

export function setShopStatus(status: ShopStatus): Promise<null> {
  return request<null>({ url: `/shop/${status}`, method: 'PUT' })
}
