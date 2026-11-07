import type { CouponOrderStatus, CouponOrderSummary } from '@/api/couponOrder'
import { formatTime, money } from './orderDisplay'

export const couponOrderTabs = [
  { key: 'all', label: '全部订单', status: undefined },
  { key: 'unpaid', label: '待支付', status: 0 },
  { key: 'paid', label: '已支付', status: 1 },
  { key: 'refunded', label: '已退款', status: 2 },
  { key: 'closed', label: '已关闭', status: 3 },
] as const satisfies readonly {
  key: string
  label: string
  status: CouponOrderStatus | undefined
}[]
export type CouponOrderTab = (typeof couponOrderTabs)[number]['key']
export const couponOrderStatusNames: Record<CouponOrderStatus, string> = {
  0: '待支付',
  1: '已支付',
  2: '已退款',
  3: '已关闭',
}
export const refundReasons = ['用户申请退款', '重复购买', '活动规则调整', '其他原因']

// 购券订单只由付费代金券产生，分页结果不返回券类型。
export function couponOffer(
  order: Pick<CouponOrderSummary, 'discountAmount' | 'thresholdAmount'>,
  type: 1 | 2 = 1,
): string {
  if (type === 1) return `可抵扣 ${money(order.discountAmount)}`
  return order.thresholdAmount > 0
    ? `满 ${money(order.thresholdAmount)} 减 ${money(order.discountAmount)}`
    : `无门槛减 ${money(order.discountAmount)}`
}

interface CouponOrderColumn {
  key: string
  label: string
  width: number
  value: (order: CouponOrderSummary) => string
}
const fields = {
  number: {
    key: 'number',
    label: '订单号',
    width: 170,
    value: (o: CouponOrderSummary) => o.number,
  },
  status: {
    key: 'status',
    label: '订单状态',
    width: 95,
    value: (o: CouponOrderSummary) => couponOrderStatusNames[o.status],
  },
  username: {
    key: 'username',
    label: '用户名',
    width: 100,
    value: (o: CouponOrderSummary) => o.username,
  },
  couponName: {
    key: 'couponName',
    label: '优惠券名称',
    width: 140,
    value: (o: CouponOrderSummary) => o.couponName,
  },
  offer: {
    key: 'offer',
    label: '优惠方式',
    width: 130,
    value: (o: CouponOrderSummary) => couponOffer(o),
  },
  price: {
    key: 'price',
    label: '购买价格',
    width: 100,
    value: (o: CouponOrderSummary) => money(o.price),
  },
  createTime: {
    key: 'createTime',
    label: '下单时间',
    width: 165,
    value: (o: CouponOrderSummary) => formatTime(o.createTime),
  },
  payDeadline: {
    key: 'payDeadline',
    label: '支付截止时间',
    width: 165,
    value: (o: CouponOrderSummary) => formatTime(o.payDeadline),
  },
  payTime: {
    key: 'payTime',
    label: '支付时间',
    width: 165,
    value: (o: CouponOrderSummary) => formatTime(o.payTime),
  },
  refundTime: {
    key: 'refundTime',
    label: '退款时间',
    width: 165,
    value: (o: CouponOrderSummary) => formatTime(o.refundTime),
  },
  refundReason: {
    key: 'refundReason',
    label: '退款原因',
    width: 150,
    value: (o: CouponOrderSummary) => o.refundReason || '—',
  },
  cancelTime: {
    key: 'cancelTime',
    label: '关闭时间',
    width: 165,
    value: (o: CouponOrderSummary) => formatTime(o.cancelTime),
  },
  cancelReason: {
    key: 'cancelReason',
    label: '关闭原因',
    width: 130,
    value: (o: CouponOrderSummary) => o.cancelReason || '—',
  },
} satisfies Record<string, CouponOrderColumn>

export function couponOrderColumns(tab: CouponOrderTab): CouponOrderColumn[] {
  const f = fields
  if (tab === 'unpaid')
    return [f.number, f.username, f.couponName, f.price, f.createTime, f.payDeadline]
  if (tab === 'paid')
    return [f.number, f.username, f.couponName, f.offer, f.price, f.createTime, f.payTime]
  if (tab === 'refunded')
    return [f.number, f.username, f.couponName, f.price, f.payTime, f.refundTime, f.refundReason]
  if (tab === 'closed')
    return [f.number, f.username, f.couponName, f.price, f.createTime, f.cancelTime, f.cancelReason]
  return [f.number, f.status, f.username, f.couponName, f.offer, f.price, f.createTime]
}
