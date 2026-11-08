import { expect, type Page } from '@playwright/test'

interface CouponFixture {
  id: number
  name: string
  type: number
  stock: number
  discountAmount: number
  thresholdAmount: number
  price: number
  startTime: string
  endTime: string
}

export async function mockCouponCheckout(page: Page, fixtures: CouponFixture[]) {
  const records = fixtures.map((coupon) => ({ ...coupon }))
  const writes: {
    path: string
    method: string
    params: Record<string, string>
    body: string | null
  }[] = []
  const orders = new Map<number, { number: string; price: number; payDeadline: string }>()
  const claimed = new Set<number>()
  let release: (() => void) | undefined
  const api = {
    records,
    writes,
    failures: { get: '', buy: '', pay: '' },
    delay: '' as '' | 'get' | 'buy' | 'pay',
    release: () => release?.(),
  }
  await page.route('**/api/coupon/available', (route) =>
    route.fulfill({ json: { code: 1, data: records.filter((coupon) => coupon.stock > 0) } }),
  )
  await page.route(/\/api\/coupon\/(get|buy|pay)(?:[/?]|$)/, async (route) => {
    const req = route.request(),
      url = new URL(req.url())
    const action = url.pathname.split('/')[3] as 'get' | 'buy' | 'pay'
    const params = Object.fromEntries(url.searchParams)
    writes.push({ path: url.pathname, method: req.method(), params, body: req.postData() })
    expect(req.method()).toBe('POST')
    expect(req.headers().authentication).toBe('e2e-user-token')
    const fail = (msg: string) => route.fulfill({ json: { code: 0, msg, data: null } })
    const success = (data: unknown) => route.fulfill({ json: { code: 1, data } })
    if (api.delay === action)
      await new Promise<void>((resolve) => {
        release = resolve
      })
    if (api.failures[action]) return fail(api.failures[action])
    if (action === 'pay') {
      const order = [...orders.values()].find((order) => order.number === params.couponOrderNumber)
      if (!order || url.pathname !== '/api/coupon/pay') return fail('订单号不正确')
      return success({ number: order.number, expireTime: '2026-12-09 18:30' })
    }
    const coupon = records.find((coupon) => coupon.id === Number(params.couponId))!
    if (coupon.stock <= 0) return fail('优惠券库存不足')
    if (action === 'get') {
      if (claimed.has(coupon.id)) return fail('用户已领取过该满减券')
      claimed.add(coupon.id)
      coupon.stock--
      return success({ number: `CLAIM-${coupon.id}`, expireTime: '2026-12-07 10:00' })
    }
    if (orders.has(coupon.id)) return fail('用户已购买过该代金券')
    coupon.stock--
    const order = {
      number: `COUPON-ORDER-${coupon.id}`,
      price: 8.8,
      payDeadline: '2026-11-07 10:15',
    }
    orders.set(coupon.id, order)
    return success(order)
  })
  return api
}
