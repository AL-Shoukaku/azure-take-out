import { expect, type Page } from '@playwright/test'
import type { CouponOrderDetail, CouponOrderStatus } from '../../src/api/couponOrders'

export function makeCouponOrder(id: number, status: CouponOrderStatus): CouponOrderDetail {
  const paid = status === 1 || status === 2
  return {
    id,
    userId: 7,
    couponId: 1,
    number: `COUPON-ORDER-${String(id).padStart(4, '0')}`,
    couponNumber: `COUPON-${String(id).padStart(4, '0')}`,
    username: '小蓝',
    couponName: '午餐代金券',
    type: 1,
    price: 9.9,
    discountAmount: 20.5,
    thresholdAmount: 0,
    status,
    payDeadline: '2026-11-07 10:15',
    createTime: '2026-11-07 10:00',
    payTime: paid ? '2026-11-07 10:01' : null,
    cancelTime: status === 3 ? '2026-11-07 10:16' : null,
    refundTime: status === 2 ? '2026-11-08 09:30' : null,
    cancelReason: status === 3 ? '超时未支付' : null,
    refundReason: status === 2 ? '用户申请退款' : null,
  }
}

// 分页接口返回 CouponOrderPageQueryVO，不含这些只在详情中返回的字段。
const detailOnlyFields = ['userId', 'couponId', 'couponNumber', 'type'] as const

export async function mockCouponOrders(
  page: Page,
  initial = [
    makeCouponOrder(1, 0),
    makeCouponOrder(2, 1),
    makeCouponOrder(3, 2),
    makeCouponOrder(4, 3),
    makeCouponOrder(5, 1),
    makeCouponOrder(6, 1),
    makeCouponOrder(7, 3),
  ],
) {
  const orders = structuredClone(initial)
  const queries: { page: number; pageSize: number; status: string | null }[] = []
  await page.route('**/api/couponOrder/**', (route) => {
    const request = route.request()
    expect(request.method()).toBe('GET')
    expect(request.headers().authentication).toBe('e2e-user-token')
    const url = new URL(request.url())
    if (url.pathname.endsWith('/historyOrders')) {
      const pageNum = Number(url.searchParams.get('page'))
      const pageSize = Number(url.searchParams.get('pageSize'))
      const status = url.searchParams.get('status')
      queries.push({ page: pageNum, pageSize, status })
      const filtered = orders.filter((order) => status === null || String(order.status) === status)
      const records = filtered.slice((pageNum - 1) * pageSize, pageNum * pageSize).map((order) => {
        const summary: Partial<CouponOrderDetail> = { ...order }
        for (const field of detailOnlyFields) delete summary[field]
        return summary
      })
      return route.fulfill({
        json: { code: 1, data: { total: filtered.length, records }, msg: null },
      })
    }
    const id = Number(url.pathname.split('/').at(-1))
    return route.fulfill({
      json: { code: 1, data: orders.find((order) => order.id === id), msg: null },
    })
  })
  const writes: { path: string; method: string; params: Record<string, string> }[] = []
  await page.route(/\/api\/coupon\/pay(?:\?|$)/, (route) => {
    const request = route.request()
    expect(request.headers().authentication).toBe('e2e-user-token')
    expect(request.method()).toBe('POST')
    const url = new URL(request.url())
    const params = Object.fromEntries(url.searchParams)
    writes.push({ path: url.pathname, method: request.method(), params })
    const order = orders.find((order) => order.number === params.couponOrderNumber)
    if (!order || order.status !== 0)
      return route.fulfill({
        json: { code: 0, msg: '订单状态更新失败，可能是订单已支付或已关闭', data: null },
      })
    order.status = 1
    order.payTime = '2026-11-07 10:05'
    return route.fulfill({
      json: { code: 1, data: { number: order.number, expireTime: '2026-12-07 10:05' }, msg: null },
    })
  })
  return { queries, writes }
}
