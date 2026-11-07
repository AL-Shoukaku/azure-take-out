import { describe, expect, it } from 'vitest'
import type { AvailableCoupon } from '@/api/coupons'
import { couponOffer, couponState } from '../couponDisplay'

const coupon: AvailableCoupon = {
  id: 1,
  name: '午餐优惠',
  type: 2,
  stock: 10,
  discountAmount: 20.5,
  thresholdAmount: 60,
  price: 0,
  startTime: '2026-11-07 10:00',
  endTime: '2026-11-07 12:00',
}

describe('优惠方式', () => {
  it('满减券按门槛与抵扣金额展示，零门槛展示无门槛', () => {
    expect(couponOffer(coupon)).toBe('满 ¥60.00 减 ¥20.50')
    expect(couponOffer({ ...coupon, thresholdAmount: 0 })).toBe('无门槛减 ¥20.50')
  })
  it('代金券展示抵扣金额，购买价不作为优惠金额', () => {
    expect(couponOffer({ ...coupon, type: 1, thresholdAmount: 0, price: 9.9 })).toBe(
      '可抵扣 ¥20.50',
    )
  })
})

describe('活动状态', () => {
  it.each([
    ['2026-11-07T09:59:59+08:00', 'upcoming'],
    ['2026-11-07T10:00:00+08:00', 'active'],
    ['2026-11-07T11:59:59+08:00', 'active'],
    ['2026-11-07T12:00:00+08:00', 'ended'],
  ])('北京时间 %s 的活动边界为 %s', (time, expected) => {
    expect(couponState(coupon, Date.parse(time))).toBe(expected)
  })
  it('兼容空格和 T 分隔的后端时间，浏览器时区不影响时间比较', () => {
    expect(
      couponState({ ...coupon, startTime: '2026-11-07T10:00' }, Date.parse('2026-11-07T02:00:00Z')),
    ).toBe('active')
  })
  it('库存耗尽、错误时间范围或缺少时间时不能领取', () => {
    const now = Date.parse('2026-11-07T11:00:00+08:00')
    expect(couponState({ ...coupon, stock: 0 }, now)).toBe('sold-out')
    expect(couponState({ ...coupon, startTime: '' }, now)).toBe('unavailable')
    expect(couponState({ ...coupon, endTime: coupon.startTime }, now)).toBe('unavailable')
  })
})
