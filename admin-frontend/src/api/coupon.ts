import { request } from '@/utils/request'
import type { PageResult } from '@/types/api'

export type CouponType = 1 | 2
export type CouponStatus = 0 | 1
export type ActivityStatus = 0 | 1 | 2

export interface CouponPublishDTO {
  name: string
  type: CouponType
  totalStock: number
  discountAmount: number
  thresholdAmount: number
  price: number
  // 后端 JacksonObjectMapper 使用 yyyy-MM-dd HH:mm，不带秒和时区。
  startTime: string
  endTime: string
  validTime: number
}

export interface Coupon extends CouponPublishDTO {
  id: number
  stock: number
  status: CouponStatus
  createTime: string | null
  updateTime: string | null
}

export interface CouponUpdateDTO {
  id: number
  name: string
  price?: number
}

export interface CouponPageQuery {
  page: number
  pageSize: number
  name?: string
  type?: CouponType
  status?: CouponStatus
  activityStatus?: ActivityStatus
}

export const couponAPI = {
  page: (data: CouponPageQuery): Promise<PageResult<Coupon>> =>
    request({ url: '/coupon/pageQuery', method: 'POST', data }),
  detail: (couponId: number): Promise<Coupon> =>
    request({ url: `/coupon/${couponId}`, method: 'GET' }),
  create: (data: CouponPublishDTO): Promise<null> =>
    request({ url: '/coupon/publish', method: 'POST', data }),
  update: (data: CouponUpdateDTO): Promise<null> =>
    request({ url: '/coupon/update', method: 'PUT', data }),
  status: (couponId: number, status: CouponStatus): Promise<null> =>
    request({ url: '/coupon/status', method: 'PUT', params: { couponId, status } }),
}
