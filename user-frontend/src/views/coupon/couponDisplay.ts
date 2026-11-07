import type { AvailableCoupon } from '@/api/coupons'
import { money } from '@/utils/pricing'

export type CouponState = 'upcoming' | 'active' | 'ended' | 'sold-out' | 'unavailable'
export const stateNames: Record<CouponState, string> = {
  upcoming: '未开始',
  active: '进行中',
  ended: '已结束',
  'sold-out': '已抢完',
  unavailable: '暂不可用',
}

export function couponOffer(coupon: AvailableCoupon): string {
  if (coupon.type === 1) return `可抵扣 ${money(coupon.discountAmount)}`
  return coupon.thresholdAmount > 0
    ? `满 ${money(coupon.thresholdAmount)} 减 ${money(coupon.discountAmount)}`
    : `无门槛减 ${money(coupon.discountAmount)}`
}

function activityTime(value: string): number {
  // 后端返回北京时间的 LocalDateTime，按营业时区解释，不随浏览器时区变化。
  const normalized = value?.replace(' ', 'T')
  return Date.parse(normalized ? `${normalized}+08:00` : '')
}

export function couponState(coupon: AvailableCoupon, now: number): CouponState {
  const start = activityTime(coupon.startTime),
    end = activityTime(coupon.endTime)
  if (!Number.isFinite(start) || !Number.isFinite(end) || start >= end) return 'unavailable'
  if (now >= end) return 'ended'
  if (now < start) return 'upcoming'
  if (coupon.stock <= 0) return 'sold-out'
  return 'active'
}

export function formatActivityTime(value: string): string {
  return value?.replace('T', ' ').slice(0, 16) || '—'
}
