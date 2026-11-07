import type { CouponSubmittedOrder } from '@/api/coupons'
import { activityTime } from './couponDisplay'

export interface CouponPaymentSummary extends CouponSubmittedOrder {
  name: string
  offer: string
}

export interface CouponPaymentOrder extends CouponPaymentSummary {
  couponId: number
}

export interface CouponReceipt {
  kind: 'claim' | 'payment'
  name: string
  offer: string
  number: string
  expireTime: string
  price?: number
}

export function paymentSeconds(deadline: string, now: number): number {
  const remaining = activityTime(deadline) - now
  return Number.isFinite(remaining) ? Math.max(0, Math.ceil(remaining / 1000)) : 0
}
