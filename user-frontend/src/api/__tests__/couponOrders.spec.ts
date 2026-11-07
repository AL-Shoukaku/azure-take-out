import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter } from 'axios'
import { getCouponOrder, getCouponOrders } from '../couponOrders'
import { http } from '@/utils/request'

const original = http.defaults.adapter
afterEach(() => {
  http.defaults.adapter = original
})
function adapter(data: unknown = null) {
  const mock = vi.fn<AxiosAdapter>(async (config) => ({
    data: { code: 1, data, msg: null },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  }))
  http.defaults.adapter = mock
  return mock
}

describe('用户优惠券订单接口', () => {
  it('分页传页码和每页条数，只有选择状态时才传 status，未支付的 0 也要传', async () => {
    const mock = adapter({ total: 0, records: [] })
    await getCouponOrders(2, 6, 1)
    await getCouponOrders(1, 6, 0)
    await getCouponOrders(1, 6, null)
    expect(mock.mock.calls[0]![0]).toMatchObject({
      url: '/couponOrder/historyOrders',
      method: 'get',
      params: { page: 2, pageSize: 6, status: 1 },
    })
    expect(mock.mock.calls[1]![0].params).toEqual({ page: 1, pageSize: 6, status: 0 })
    expect(mock.mock.calls[2]![0].params).toEqual({ page: 1, pageSize: 6 })
  })

  it('详情使用本人订单接口并返回完整订单', async () => {
    const detail = { id: 7, number: 'COUPON-ORDER-7', couponNumber: 'COUPON-7' }
    const mock = adapter(detail)
    await expect(getCouponOrder(7)).resolves.toEqual(detail)
    expect(mock.mock.calls[0]![0]).toMatchObject({
      url: '/couponOrder/orderDetail/7',
      method: 'get',
    })
  })
})
