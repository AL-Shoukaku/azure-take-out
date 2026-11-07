import { expect, type Page } from '@playwright/test'
import type { UserCoupon } from '../../src/api/userCoupons'

export function makeUserCoupon(id: number, status: UserCoupon['status']): UserCoupon {
  const used = status === 2
  return {
    id,
    couponId: ((id - 1) % 4) + 1,
    number: `COUPON-${String(id).padStart(4, '0')}`,
    type: id % 2 === 1 ? 1 : 2,
    name: id % 2 === 1 ? '午餐代金券' : '双人满减券',
    discountAmount: 15,
    thresholdAmount: id % 2 === 1 ? 0 : 60,
    status,
    expireTime: '2026-12-07 10:00',
    createTime: '2026-11-07 10:00',
    useTime: used ? '2026-11-20 12:30' : null,
    refundTime: status === 4 ? '2026-11-21 09:00' : null,
  }
}

export async function mockUserCoupons(
  page: Page,
  initial = [
    makeUserCoupon(1, 0),
    makeUserCoupon(2, 0),
    makeUserCoupon(3, 1),
    makeUserCoupon(4, 2),
    makeUserCoupon(5, 3),
    makeUserCoupon(6, 4),
    makeUserCoupon(7, 0),
  ],
) {
  const coupons = structuredClone(initial)
  const queries: { page: number; pageSize: number; status: string | null }[] = []
  const details: number[] = []
  await page.route('**/api/userCoupon/**', (route) => {
    const request = route.request()
    expect(request.method()).toBe('GET')
    expect(request.headers().authentication).toBe('e2e-user-token')
    const url = new URL(request.url())
    if (url.pathname.endsWith('/page')) {
      const pageNum = Number(url.searchParams.get('page'))
      const pageSize = Number(url.searchParams.get('pageSize'))
      const status = url.searchParams.get('status')
      queries.push({ page: pageNum, pageSize, status })
      const filtered = coupons.filter(
        (coupon) => status === null || String(coupon.status) === status,
      )
      return route.fulfill({
        json: {
          code: 1,
          data: {
            total: filtered.length,
            records: filtered.slice((pageNum - 1) * pageSize, pageNum * pageSize),
          },
          msg: null,
        },
      })
    }
    const id = Number(url.pathname.split('/').at(-1))
    details.push(id)
    return route.fulfill({
      json: { code: 1, data: coupons.find((coupon) => coupon.id === id), msg: null },
    })
  })
  return { queries, details }
}
