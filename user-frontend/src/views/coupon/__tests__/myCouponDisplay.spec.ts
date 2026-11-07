import { describe, expect, it } from 'vitest'
import type { UserCoupon } from '@/api/userCoupons'
import { couponOffer } from '../couponDisplay'
import {
  userCouponPageSize,
  userCouponStatusNames,
  userCouponTabs,
  userCouponTimes,
} from '../myCouponDisplay'

const coupon: UserCoupon = {
  id: 1,
  couponId: 1,
  number: 'COUPON-001',
  type: 2,
  name: '双人满减券',
  discountAmount: 15,
  thresholdAmount: 60,
  status: 0,
  expireTime: '2026-12-07 10:00',
  createTime: '2026-11-07 10:00',
  useTime: null,
  refundTime: null,
}

describe('我的优惠券展示', () => {
  it('全部及五种状态的筛选项对应后端状态值', () => {
    expect(userCouponTabs.map((tab) => [tab.label, tab.status])).toEqual([
      ['全部优惠券', null],
      ['未使用', 0],
      ['锁定中', 1],
      ['已使用', 2],
      ['已过期', 3],
      ['已退款', 4],
    ])
    expect(userCouponStatusNames[3]).toBe('已过期')
    expect(userCouponPageSize).toBe(6)
  })

  it('优惠方式按券类型展示，与抢券页格式一致', () => {
    expect(couponOffer(coupon)).toBe('满 ¥60.00 减 ¥15.00')
    expect(couponOffer({ ...coupon, type: 1, thresholdAmount: 0 })).toBe('可抵扣 ¥15.00')
    expect(couponOffer({ ...coupon, thresholdAmount: 0 })).toBe('无门槛减 ¥15.00')
  })

  it('按状态选择展示有效期、核销或退款时间', () => {
    expect(userCouponTimes(coupon)).toEqual([{ label: '有效期至', value: '2026-12-07 10:00' }])
    expect(userCouponTimes({ ...coupon, status: 1 })).toEqual([
      { label: '有效期至', value: '2026-12-07 10:00' },
    ])
    expect(userCouponTimes({ ...coupon, status: 2, useTime: '2026-11-20 12:30' })).toEqual([
      { label: '核销时间', value: '2026-11-20 12:30' },
    ])
    expect(userCouponTimes({ ...coupon, status: 3 })).toEqual([
      { label: '过期时间', value: '2026-12-07 10:00' },
    ])
    expect(userCouponTimes({ ...coupon, status: 4, refundTime: '2026-11-21 09:00' })).toEqual([
      { label: '退款时间', value: '2026-11-21 09:00' },
    ])
  })

  it('时间缺失时显示占位符，兼容 T 分隔的时间', () => {
    expect(userCouponTimes({ ...coupon, status: 2 })).toEqual([{ label: '核销时间', value: '—' }])
    expect(userCouponTimes({ ...coupon, expireTime: '2026-12-07T10:00:00' })).toEqual([
      { label: '有效期至', value: '2026-12-07 10:00' },
    ])
  })
})
