import { request } from '@/utils/request'

export type UserCouponStatus = 0 | 1 | 2 | 3 | 4
export interface UserCoupon {
  id: number
  couponId: number
  number: string
  type: 1 | 2
  name: string
  discountAmount: number
  thresholdAmount: number
  /** 状态，0未使用 1锁定中 2已使用 3已过期 4购券款已退款 */
  status: UserCouponStatus
  expireTime: string
  createTime: string
  useTime: string | null
  refundTime: string | null
}
export interface UserCouponPageResult {
  total: number
  records: UserCoupon[]
}

export function getUserCoupons(
  page: number,
  pageSize: number,
  status: UserCouponStatus | null,
): Promise<UserCouponPageResult> {
  return request<UserCouponPageResult>({
    url: '/userCoupon/page',
    method: 'GET',
    params: { page, pageSize, ...(status === null ? {} : { status }) },
  })
}
export function getUserCoupon(id: number): Promise<UserCoupon> {
  return request<UserCoupon>({ url: `/userCoupon/details/${id}`, method: 'GET' })
}
