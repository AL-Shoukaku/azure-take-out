import { describe, expect, it } from 'vitest'
import type { UserCoupon } from '@/api/userCoupons'
import { couponDisabledReason, previewCoupons } from '../couponPreview'

export const coupon: UserCoupon = {
  id: 1,
  couponId: 101,
  number: 'OWNED-1',
  name: '代金券',
  type: 1,
  status: 0,
  discountAmount: 5,
  thresholdAmount: 0,
  expireTime: '2026-12-07 10:00',
  createTime: '2026-11-07 10:00',
  useTime: null,
  refundTime: null,
}
const now = Date.parse('2026-11-08T10:00:00+08:00')

describe('优惠券前端试算', () => {
  it('满减门槛按原餐品小计判断，多券叠加且顺序不影响结果', () => {
    const reduction = { ...coupon, id: 2, type: 2 as const, thresholdAmount: 16, discountAmount: 4 }
    expect(previewCoupons([coupon, reduction], 16, 7, now)).toMatchObject({
      discount: 9,
      total: 14,
    })
    expect(previewCoupons([reduction, coupon], 16, 7, now)).toMatchObject({
      discount: 9,
      total: 14,
    })
  })
  it('抵扣上限为餐品金额，打包和配送费用保留，重复勾选不重复计算', () => {
    expect(previewCoupons([coupon, coupon], 16, 7, now)).toMatchObject({ discount: 5, total: 18 })
    expect(previewCoupons([{ ...coupon, discountAmount: 50 }], 16, 7, now)).toMatchObject({
      discount: 16,
      total: 7,
    })
    expect(previewCoupons([coupon], 0, 0, now)).toMatchObject({
      discount: 0,
      total: 0,
      coupons: [],
    })
  })
  it('按分计算避免小数叠加误差', () => {
    expect(
      previewCoupons(
        [
          { ...coupon, discountAmount: 0.1 },
          { ...coupon, id: 2, discountAmount: 0.2 },
        ],
        0.3,
        7,
        now,
      ),
    ).toMatchObject({ discount: 0.3, total: 7 })
  })
  it('未达门槛、过期、已使用或异常金额都不能抵扣', () => {
    expect(couponDisabledReason({ ...coupon, type: 2, thresholdAmount: 30 }, 16, now)).toBe(
      '餐品金额还差 ¥14.00',
    )
    expect(couponDisabledReason(coupon, 16, Date.parse('2026-12-07T10:00:00+08:00'))).toBe('已过期')
    const invalid = [
      { ...coupon, status: 2 as const },
      { ...coupon, expireTime: '' },
      { ...coupon, discountAmount: NaN },
    ]
    expect(previewCoupons(invalid, 16, 7, now)).toMatchObject({
      discount: 0,
      total: 23,
      coupons: [],
    })
  })
})
