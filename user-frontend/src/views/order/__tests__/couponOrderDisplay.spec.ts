import { describe, expect, it } from 'vitest'
import type { CouponOrderSummary } from '@/api/couponOrders'
import {
  couponOrderOffer,
  couponOrderStatusNames,
  couponOrderTabs,
  couponOrderTimes,
} from '../couponOrderDisplay'

const order: CouponOrderSummary = {
  id: 1,
  number: 'COUPON-ORDER-1',
  username: '小蓝',
  couponName: '午餐代金券',
  price: 9.9,
  discountAmount: 20.5,
  thresholdAmount: 0,
  status: 0,
  payDeadline: '2026-11-07 10:15',
  createTime: '2026-11-07 10:00',
  payTime: null,
  cancelTime: null,
  refundTime: null,
  cancelReason: null,
  refundReason: null,
}

describe('优惠券订单展示', () => {
  it('全部及四种状态的筛选项对应后端状态值', () => {
    expect(couponOrderTabs.map((tab) => [tab.label, tab.status])).toEqual([
      ['全部订单', null],
      ['待支付', 0],
      ['已支付', 1],
      ['已退款', 2],
      ['已关闭', 3],
    ])
    expect(couponOrderStatusNames[2]).toBe('已退款')
  })

  it('优惠方式沿用抢券页的代金券格式，购买价不作为优惠金额', () => {
    expect(couponOrderOffer(order)).toBe('可抵扣 ¥20.50')
  })

  it('按订单状态选择展示支付截止、支付、退款或关闭时间', () => {
    expect(couponOrderTimes(order)).toEqual([{ label: '支付截止', value: '2026-11-07 10:15' }])
    expect(couponOrderTimes({ ...order, status: 1, payTime: '2026-11-07 10:01' })).toEqual([
      { label: '支付时间', value: '2026-11-07 10:01' },
    ])
    expect(
      couponOrderTimes({
        ...order,
        status: 2,
        payTime: '2026-11-07 10:01',
        refundTime: '2026-11-08 09:30',
      }),
    ).toEqual([
      { label: '支付时间', value: '2026-11-07 10:01' },
      { label: '退款时间', value: '2026-11-08 09:30' },
    ])
    expect(couponOrderTimes({ ...order, status: 3, cancelTime: '2026-11-07 10:16' })).toEqual([
      { label: '关闭时间', value: '2026-11-07 10:16' },
    ])
  })

  it('时间缺失时显示占位符，兼容 T 分隔的时间', () => {
    expect(couponOrderTimes({ ...order, status: 1 })).toEqual([{ label: '支付时间', value: '—' }])
    expect(couponOrderTimes({ ...order, payDeadline: '2026-11-07T10:15:00' })).toEqual([
      { label: '支付截止', value: '2026-11-07 10:15' },
    ])
  })
})
