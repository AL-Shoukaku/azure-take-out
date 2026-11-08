import type { UserCoupon } from '@/api/userCoupons'
import { money } from '@/utils/pricing'
import { activityTime } from '@/views/coupon/couponDisplay'

export interface CouponPricePreview {
  coupons: UserCoupon[]
  discount: number
  total: number
}

const cents = (value: number) => (Number.isFinite(value) ? Math.max(0, Math.round(value * 100)) : 0)

export function couponDisabledReason(coupon: UserCoupon, subtotal: number, now: number): string {
  if (coupon.status !== 0) return '优惠券已不可用'
  const expiry = activityTime(coupon.expireTime)
  if (!Number.isFinite(expiry) || expiry <= now) return '已过期'
  if (coupon.type !== 1 && coupon.type !== 2) return '暂不支持该券类型'
  if (
    !Number.isFinite(coupon.discountAmount) ||
    coupon.discountAmount <= 0 ||
    !Number.isFinite(coupon.thresholdAmount) ||
    coupon.thresholdAmount < 0
  )
    return '优惠信息异常'
  if (cents(subtotal) === 0) return '请先添加餐品'
  if (coupon.type === 2 && cents(subtotal) < cents(coupon.thresholdAmount)) {
    return `餐品金额还差 ${money((cents(coupon.thresholdAmount) - cents(subtotal)) / 100)}`
  }
  return ''
}

export function previewCoupons(
  selected: UserCoupon[],
  subtotal: number,
  fees: number,
  now: number,
): CouponPricePreview {
  const unique = [...new Map(selected.map((coupon) => [coupon.id, coupon])).values()]
  const coupons = unique.filter((coupon) => !couponDisabledReason(coupon, subtotal, now))
  const discount = Math.min(
    cents(subtotal),
    coupons.reduce((sum, coupon) => sum + cents(coupon.discountAmount), 0),
  )
  return {
    coupons,
    discount: discount / 100,
    total: (cents(subtotal) + cents(fees) - discount) / 100,
  }
}
