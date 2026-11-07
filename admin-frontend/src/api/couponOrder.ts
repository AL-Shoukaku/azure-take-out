import { request } from '@/utils/request'
import type { PageResult } from '@/types/api'

export type CouponOrderStatus = 0 | 1 | 2 | 3
export interface CouponOrderSummary {
  id: number
  number: string
  username: string
  couponName: string
  price: number
  discountAmount: number
  thresholdAmount: number
  status: CouponOrderStatus
  payDeadline: string
  createTime: string
  payTime: string | null
  cancelTime: string | null
  refundTime: string | null
  cancelReason: string | null
  refundReason: string | null
}
export interface CouponOrderDetail extends CouponOrderSummary {
  userId: number
  couponId: number
  couponNumber: string
  type: 1 | 2
}
export interface CouponOrderPageQuery {
  page: number
  pageSize: number
  number?: string
  username?: string
  status?: CouponOrderStatus
  beginTime?: string
  endTime?: string
}

export function getCouponOrderPage(
  params: CouponOrderPageQuery,
): Promise<PageResult<CouponOrderSummary>> {
  return request({ url: '/couponOrder/conditionSearch', method: 'GET', params })
}
export function getCouponOrderDetail(id: number): Promise<CouponOrderDetail> {
  return request({ url: `/couponOrder/details/${id}`, method: 'GET' })
}
export function refundCouponOrder(id: number, refundReason: string): Promise<null> {
  return request({ url: '/couponOrder/refund', method: 'PUT', data: { id, refundReason } })
}
