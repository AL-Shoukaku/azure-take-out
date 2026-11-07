import { afterEach, expect, it, vi } from 'vitest'
import type { AxiosAdapter } from 'axios'
import { http } from '@/utils/request'
import { buyCoupon, claimCoupon, payCouponOrder } from '../coupons'

const original = http.defaults.adapter
afterEach(() => {
  http.defaults.adapter = original
})
function respond(data: unknown, code = 1, msg: string | null = null) {
  const adapter = vi.fn<AxiosAdapter>(async (config) => ({
    data: { code, data, msg },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  }))
  http.defaults.adapter = adapter
  return adapter
}

it('领取通过 query 传券 ID，保留后端的券号和有效期', async () => {
  const result = { number: 'CLAIM-1', expireTime: '2026-12-07 10:00' }
  const adapter = respond(result)
  await expect(claimCoupon(2)).resolves.toEqual(result)
  expect(adapter.mock.calls[0]![0]).toMatchObject({
    url: '/coupon/get',
    method: 'post',
    params: { couponId: 2 },
  })
  expect(adapter.mock.calls[0]![0].data).toBeUndefined()
})

it('购券下单使用服务端金额、订单号及支付截止时间', async () => {
  const result = { number: 'ORDER-1', price: 8.8, payDeadline: '2026-11-07 10:15' }
  const adapter = respond(result)
  await expect(buyCoupon(1)).resolves.toEqual(result)
  expect(adapter.mock.calls[0]![0]).toMatchObject({
    url: '/coupon/buy',
    method: 'post',
    params: { couponId: 1 },
  })
  expect(adapter.mock.calls[0]![0].data).toBeUndefined()
})

it('支付匹配当前后端的路径和 query 订单号，不提交前端金额', async () => {
  const result = { number: 'ORDER-1', expireTime: '2026-12-09 18:30' }
  const adapter = respond(result)
  await expect(payCouponOrder('ORDER-1')).resolves.toEqual(result)
  expect(adapter.mock.calls[0]![0]).toMatchObject({
    url: '/coupon/pay/ORDER-1',
    method: 'post',
    params: { couponOrderNumber: 'ORDER-1' },
  })
  expect(adapter.mock.calls[0]![0].data).toBeUndefined()
})

it('支付失败保留后端错误信息', async () => {
  respond(null, 0, '代金券订单已超过支付截止时间')
  await expect(payCouponOrder('ORDER-1')).rejects.toThrow('代金券订单已超过支付截止时间')
})
