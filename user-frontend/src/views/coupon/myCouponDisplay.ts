import type { UserCoupon, UserCouponStatus } from '@/api/userCoupons'
import { formatTime } from '@/views/order/orderDisplay'

export const userCouponTabs = [
  { key: 'all', label: '全部优惠券', status: null },
  { key: 'unused', label: '未使用', status: 0 },
  { key: 'locked', label: '锁定中', status: 1 },
  { key: 'used', label: '已使用', status: 2 },
  { key: 'expired', label: '已过期', status: 3 },
  { key: 'refunded', label: '已退款', status: 4 },
] as const satisfies readonly {
  key: string
  label: string
  status: UserCouponStatus | null
}[]
export type UserCouponTab = (typeof userCouponTabs)[number]['key']
export const userCouponStatusNames: Record<UserCouponStatus, string> = {
  0: '未使用',
  1: '锁定中',
  2: '已使用',
  3: '已过期',
  4: '已退款',
}
export const userCouponStatusDescriptions: Record<UserCouponStatus, string> = {
  0: '优惠券可正常使用，下单时选择即可抵扣。',
  1: '优惠券已锁定，正在用于订单支付。',
  2: '优惠券已核销，期待再次为你服务。',
  3: '优惠券已超过有效期，无法继续使用。',
  4: '购券款已退回，优惠券已失效。',
}
export const userCouponPageSize = 6

const timeRows = {
  0: [['有效期至', 'expireTime']],
  1: [['有效期至', 'expireTime']],
  2: [['核销时间', 'useTime']],
  3: [['过期时间', 'expireTime']],
  4: [['退款时间', 'refundTime']],
} as const satisfies Record<UserCouponStatus, readonly (readonly [string, keyof UserCoupon])[]>
export function userCouponTimes(coupon: UserCoupon): { label: string; value: string }[] {
  return timeRows[coupon.status].map(([label, field]) => ({
    label,
    value: formatTime(coupon[field]),
  }))
}
