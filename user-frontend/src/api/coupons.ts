import { request } from '@/utils/request'

export interface AvailableCoupon {
  id: number
  name: string
  type: 1 | 2
  stock: number
  discountAmount: number
  thresholdAmount: number
  price: number
  startTime: string
  endTime: string
}

export function getAvailableCoupons(): Promise<AvailableCoupon[]> {
  return request({ url: '/coupon/available', method: 'GET' })
}
