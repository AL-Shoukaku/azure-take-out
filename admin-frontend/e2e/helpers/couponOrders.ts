import type { Page } from '@playwright/test'

export function makeCouponOrder(id: number, status: number) {
  const paid = status === 1 || status === 2
  return {
    id,
    userId: 7,
    couponId: 1,
    number: `coupon-order-${String(id).padStart(4, '0')}`,
    couponNumber: `coupon-${String(id).padStart(4, '0')}`,
    username: `用户${id}`,
    couponName: '午餐代金券',
    type: 1,
    price: 9.9,
    discountAmount: 20,
    thresholdAmount: 0,
    status,
    payDeadline: '2026-11-07 10:15',
    createTime: '2026-11-07 10:00',
    payTime: paid ? '2026-11-07 10:05' : null,
    cancelTime: status === 3 ? '2026-11-07 10:16' : null,
    refundTime: status === 2 ? '2026-11-08 09:30' : null,
    cancelReason: status === 3 ? '超时未支付' : null,
    refundReason: status === 2 ? '用户申请退款' : null,
  }
}

// 分页接口返回 CouponOrderPageQueryVO，不含这些只在详情中返回的字段。
const detailOnlyFields = ['userId', 'couponId', 'couponNumber', 'type'] as const

export async function mockCouponOrders(page: Page, total = 24) {
  const records = Array.from({ length: total }, (_, i) => makeCouponOrder(i + 1, i % 4))
  const queries: URLSearchParams[] = [],
    details: number[] = []
  const writes: { method: string; path: string; body: Record<string, unknown> | null }[] = []
  const api = {
    records,
    queries,
    details,
    writes,
    failures: { list: '', detail: '', refund: '' },
  }
  await page.route(/\/api\/couponOrder\//, async (route) => {
    const request = route.request(),
      url = new URL(request.url())
    const success = (data: unknown) => route.fulfill({ json: { code: 1, msg: null, data } })
    const fail = (msg: string) => route.fulfill({ json: { code: 0, msg, data: null } })
    if (url.pathname.endsWith('/conditionSearch')) {
      queries.push(url.searchParams)
      if (api.failures.list) return fail(api.failures.list)
      const p = url.searchParams
      const filtered = records.filter(
        (r) =>
          (!p.has('status') || r.status === Number(p.get('status'))) &&
          r.number.includes(p.get('number') || '') &&
          r.username.includes(p.get('username') || '') &&
          (!p.has('beginTime') || r.createTime >= p.get('beginTime')!) &&
          (!p.has('endTime') || r.createTime <= p.get('endTime')!),
      )
      const start = (Number(p.get('page')) - 1) * Number(p.get('pageSize'))
      return success({
        total: filtered.length,
        records: filtered.slice(start, start + Number(p.get('pageSize'))).map((r) => {
          const summary: Partial<typeof r> = { ...r }
          for (const field of detailOnlyFields) delete summary[field]
          return summary
        }),
      })
    }
    if (url.pathname.includes('/details/')) {
      const id = Number(url.pathname.split('/').pop())
      details.push(id)
      if (api.failures.detail) return fail(api.failures.detail)
      const row = records.find((r) => r.id === id)
      return row ? success(row) : fail('代金券订单不存在')
    }
    const body = request.postDataJSON() as Record<string, unknown> | null
    writes.push({ method: request.method(), path: url.pathname, body })
    if (api.failures.refund) return fail(api.failures.refund)
    const row = records.find((r) => r.id === Number(body?.id))
    if (!row || row.status !== 1) return fail('要退款的订单状态不是已支付！')
    row.status = 2
    row.refundTime = '2026-11-08 09:30'
    row.refundReason = String(body?.refundReason)
    return success(null)
  })
  return api
}
