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

export interface CouponClaimResult {
  number: string
  expireTime: string
}

export interface CouponSubmittedOrder {
  number: string
  price: number
  payDeadline: string
}

export interface CouponPaymentResult {
  number: string
  expireTime: string
}

export function claimCoupon(couponId: number): Promise<CouponClaimResult> {
  return request({ url: '/coupon/get', method: 'POST', params: { couponId } })
}

export function buyCoupon(couponId: number): Promise<CouponSubmittedOrder> {
  return request({ url: '/coupon/buy', method: 'POST', params: { couponId } })
}

export function payCouponOrder(number: string): Promise<CouponPaymentResult> {
  return request({
    url: '/coupon/pay',
    method: 'POST',
    params: { couponOrderNumber: number },
  })
}
