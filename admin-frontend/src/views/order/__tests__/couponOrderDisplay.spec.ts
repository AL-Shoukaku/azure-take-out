import { describe, expect, it } from 'vitest'
import type { CouponOrderSummary } from '@/api/couponOrder'
import { couponOffer, couponOrderColumns, couponOrderTabs } from '../couponOrderDisplay'

const order = {
  id: 1,
  number: 'coupon-order-0001',
  username: '小蓝',
  couponName: '午餐代金券',
  price: 9.9,
  discountAmount: 20,
  thresholdAmount: 0,
  status: 2,
  payDeadline: '2026-11-07 10:15',
  createTime: '2026-11-07 10:00',
  payTime: '2026-11-07 10:05',
  cancelTime: null,
  refundTime: '2026-11-08T09:30:00',
  cancelReason: null,
  refundReason: null,
} satisfies CouponOrderSummary

describe('优惠券订单展示', () => {
  it('全部订单不传状态，其余选项卡对应后端状态值', () => {
    expect(couponOrderTabs.map((tab) => [tab.label, tab.status])).toEqual([
      ['全部订单', undefined],
      ['待支付', 0],
      ['已支付', 1],
      ['已退款', 2],
      ['已关闭', 3],
    ])
  })

  it('优惠方式按券类型展示，分页结果默认按代金券', () => {
    expect(couponOffer(order)).toBe('可抵扣 ￥20.00')
    expect(couponOffer({ discountAmount: 15, thresholdAmount: 60 }, 2)).toBe(
      '满 ￥60.00 减 ￥15.00',
    )
    expect(couponOffer({ discountAmount: 5, thresholdAmount: 0 }, 2)).toBe('无门槛减 ￥5.00')
  })

  it('各状态显示对应的时间和原因列，缺失值显示占位符', () => {
    expect(couponOrderColumns('all').map((column) => column.label)).toEqual([
      '订单号',
      '订单状态',
      '用户名',
      '优惠券名称',
      '优惠方式',
      '购买价格',
      '下单时间',
    ])
    expect(couponOrderColumns('unpaid').map((column) => column.label)).toContain('支付截止时间')
    expect(couponOrderColumns('closed').map((column) => column.label)).toEqual(
      expect.arrayContaining(['关闭时间', '关闭原因']),
    )
    const refunded = couponOrderColumns('refunded')
    expect(refunded.map((column) => column.value(order))).toEqual([
      'coupon-order-0001',
      '小蓝',
      '午餐代金券',
      '￥9.90',
      '2026-11-07 10:05',
      '2026-11-08 09:30',
      '—',
    ])
  })
})
