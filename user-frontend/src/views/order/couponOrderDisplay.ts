import type { CouponOrderStatus, CouponOrderSummary } from '@/api/couponOrders'
import { couponOffer } from '@/views/coupon/couponDisplay'
import { formatTime } from './orderDisplay'

export const couponOrderTabs = [
  { key: 'all', label: '全部订单', status: null },
  { key: 'unpaid', label: '待支付', status: 0 },
  { key: 'paid', label: '已支付', status: 1 },
  { key: 'refunded', label: '已退款', status: 2 },
  { key: 'closed', label: '已关闭', status: 3 },
] as const satisfies readonly { key: string; label: string; status: CouponOrderStatus | null }[]
export type CouponOrderTab = (typeof couponOrderTabs)[number]['key']
export type CouponOrderAction = 'pay' | 'contact'
export const couponOrderStatusNames: Record<CouponOrderStatus, string> = {
  0: '待支付',
  1: '已支付',
  2: '已退款',
  3: '已关闭',
}
export const couponOrderStatusDescriptions: Record<CouponOrderStatus, string> = {
  0: '订单已提交，请在支付截止时间前完成付款。',
  1: '支付成功，代金券已发放，下单时即可抵扣。',
  2: '购券款已退回，对应的代金券已失效。',
  3: '订单已关闭，期待再次为你服务。',
}
export const couponOrderPageSize = 6

// 购券订单只由付费代金券产生，分页结果不返回券类型。
export function couponOrderOffer(
  order: Pick<CouponOrderSummary, 'discountAmount' | 'thresholdAmount'>,
): string {
  return couponOffer({
    type: 1,
    discountAmount: order.discountAmount,
    thresholdAmount: order.thresholdAmount,
  })
}

const timeRows = {
  0: [['支付截止', 'payDeadline']],
  1: [['支付时间', 'payTime']],
  2: [
    ['支付时间', 'payTime'],
    ['退款时间', 'refundTime'],
  ],
  3: [['关闭时间', 'cancelTime']],
} as const satisfies Record<
  CouponOrderStatus,
  readonly (readonly [string, keyof CouponOrderSummary])[]
>
export function couponOrderTimes(order: CouponOrderSummary): { label: string; value: string }[] {
  return timeRows[order.status].map(([label, field]) => ({
    label,
    value: formatTime(order[field]),
  }))
}
