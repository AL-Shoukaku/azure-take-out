import { request } from '@/utils/request'

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
export interface CouponOrderPageResult {
  total: number
  records: CouponOrderSummary[]
}

export function getCouponOrders(
  page: number,
  pageSize: number,
  status: CouponOrderStatus | null,
): Promise<CouponOrderPageResult> {
  return request<CouponOrderPageResult>({
    url: '/couponOrder/historyOrders',
    method: 'GET',
    params: { page, pageSize, ...(status === null ? {} : { status }) },
  })
}
export function getCouponOrder(id: number): Promise<CouponOrderDetail> {
  return request<CouponOrderDetail>({ url: `/couponOrder/orderDetail/${id}`, method: 'GET' })
}
