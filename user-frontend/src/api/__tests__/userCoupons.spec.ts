import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter } from 'axios'
import { getUserCoupon, getUserCoupons } from '../userCoupons'
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

describe('我的优惠券接口', () => {
  it('分页传页码和每页条数，只有选择状态时才传 status，未使用的 0 也要传', async () => {
    const mock = adapter({ total: 0, records: [] })
    await getUserCoupons(2, 6, 1)
    await getUserCoupons(1, 6, 0)
    await getUserCoupons(1, 6, null)
    expect(mock.mock.calls[0]![0]).toMatchObject({
      url: '/userCoupon/page',
      method: 'get',
      params: { page: 2, pageSize: 6, status: 1 },
    })
    expect(mock.mock.calls[1]![0].params).toEqual({ page: 1, pageSize: 6, status: 0 })
    expect(mock.mock.calls[2]![0].params).toEqual({ page: 1, pageSize: 6 })
  })

  it('详情使用本人优惠券接口并返回完整信息', async () => {
    const detail = { id: 7, number: 'COUPON-7', status: 0 }
    const mock = adapter(detail)
    await expect(getUserCoupon(7)).resolves.toEqual(detail)
    expect(mock.mock.calls[0]![0]).toMatchObject({
      url: '/userCoupon/details/7',
      method: 'get',
    })
  })
})
