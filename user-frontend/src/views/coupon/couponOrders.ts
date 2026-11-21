import { getCouponOrderStatus, type CouponOrderPaymentInfo } from '@/api/couponOrders'
import { activityTime } from './couponDisplay'

export interface CouponPaymentSummary extends CouponOrderPaymentInfo {
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

const ORDER_WAIT_MS = 12_000
const ORDER_POLL_MS = 1_000

export async function waitForCouponOrder(
  number: string,
  signal: AbortSignal,
): Promise<CouponOrderPaymentInfo | undefined> {
  const deadline = performance.now() + ORDER_WAIT_MS
  for (
    let remaining = ORDER_WAIT_MS;
    remaining > 0 && !signal.aborted;
    remaining = deadline - performance.now()
  ) {
    const order = await getCouponOrderStatus(number, {
      signal,
      timeout: Math.ceil(remaining),
    })
    if (order?.number === number) return order
    const delay = Math.min(ORDER_POLL_MS, deadline - performance.now())
    if (delay > 0 && !signal.aborted) await new Promise((resolve) => setTimeout(resolve, delay))
  }
}

export function paymentSeconds(deadline: string, now: number): number {
  const remaining = activityTime(deadline) - now
  return Number.isFinite(remaining) ? Math.max(0, Math.ceil(remaining / 1000)) : 0
}
