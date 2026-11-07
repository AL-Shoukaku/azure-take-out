import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter } from 'axios'
import { http } from '@/utils/request'
import { getCouponOrderDetail, getCouponOrderPage, refundCouponOrder } from '../couponOrder'

const originalAdapter = http.defaults.adapter
afterEach(() => {
  http.defaults.adapter = originalAdapter
})
function respond(data: unknown = null, code = 1, msg: string | null = null) {
  const adapter = vi.fn<AxiosAdapter>(async (config) => ({
    data: { code, msg, data },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  }))
  http.defaults.adapter = adapter
  return adapter
}

describe('优惠券订单接口', () => {
  it('条件查询传递分页、订单号、用户名、待支付状态 0 和完整日期范围', async () => {
    const result = { total: 0, records: [] }
    const adapter = respond(result)
    const params = {
      page: 2,
      pageSize: 20,
      number: 'coupon-order',
      username: '小蓝',
      status: 0 as const,
      beginTime: '2026-11-01 00:00:00',
      endTime: '2026-11-07 23:59:59',
    }
    await expect(getCouponOrderPage(params)).resolves.toEqual(result)
    expect(adapter.mock.calls[0]![0]).toMatchObject({
      url: '/couponOrder/conditionSearch',
      method: 'get',
      params,
    })
  })

  it('详情和退款使用商家端接口，退款原因放在请求体中', async () => {
    const adapter = respond()
    await getCouponOrderDetail(7)
    await refundCouponOrder(7, '用户申请退款')
    expect(adapter.mock.calls[0]![0]).toMatchObject({
      url: '/couponOrder/details/7',
      method: 'get',
    })
    expect(adapter.mock.calls[1]![0]).toMatchObject({ url: '/couponOrder/refund', method: 'put' })
    expect(JSON.parse(adapter.mock.calls[1]![0].data as string)).toEqual({
      id: 7,
      refundReason: '用户申请退款',
    })
  })

  it('退款失败保留后端错误信息', async () => {
    respond(null, 0, '要退款的订单状态不是已支付！')
    await expect(refundCouponOrder(7, '重复购买')).rejects.toThrow('要退款的订单状态不是已支付！')
  })
})
