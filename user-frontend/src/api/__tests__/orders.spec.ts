import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter } from 'axios'
import {
  cancelOrder,
  getOrder,
  getOrders,
  payOrder,
  remindOrder,
  repeatOrder,
  submitOrder,
} from '../orders'
import { getCart } from '../cart'
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

describe('用户订单及购物车接口', () => {
  it('下单传入用户持有的券ID列表，保留后端的折后订单金额', async () => {
    const mock = adapter({
      id: 7,
      orderNumber: 'ORDER-7',
      orderAmount: 7,
      orderTime: '2026-11-08 10:00',
    })
    await expect(
      submitOrder({
        addressBookId: 1,
        payMethod: 1,
        remark: '',
        deliveryStatus: 1,
        tablewareStatus: 1,
        tablewareNumber: 0,
        userCouponIds: [12, 13],
      }),
    ).resolves.toMatchObject({ orderAmount: 7 })
    const config = mock.mock.calls[0]![0]
    expect(config).toMatchObject({ url: '/order/submit', method: 'post' })
    const data = JSON.parse(config.data as string)
    expect(data.userCouponIds).toEqual([12, 13])
    expect(data).not.toHaveProperty('amount')
  })

  it('使用固定六条的新接口，状态列表使用后端支持的逗号格式', async () => {
    const mock = adapter({ total: 0, records: [] })
    await getOrders(2, [2, 3, 4])
    await getOrders(1, [])
    expect(mock.mock.calls[0]![0]).toMatchObject({
      url: '/order/historyOrdersByStatusList',
      params: { pageNum: 2, statusList: '2,3,4' },
    })
    expect(mock.mock.calls[1]![0].params).toEqual({ pageNum: 1 })
  })

  it('详情、取消、催单和再来一单使用本人订单接口及正确方法', async () => {
    const mock = adapter()
    await getOrder(7)
    await cancelOrder(7)
    await remindOrder(7)
    await repeatOrder(7)
    await getCart()
    expect(mock.mock.calls.map(([c]) => [c.method, c.url])).toEqual([
      ['get', '/order/orderDetail/7'],
      ['put', '/order/cancel/7'],
      ['get', '/order/reminder/7'],
      ['post', '/order/repetition/7'],
      ['get', '/shoppingCart/list'],
    ])
  })

  it('支付通过订单号提交，并保留后端模拟支付响应', async () => {
    const mock = adapter({ packageStr: 'prepay_id=fake_prepay_id' })
    await expect(payOrder('ORDER-7')).resolves.toMatchObject({
      packageStr: 'prepay_id=fake_prepay_id',
    })
    expect(mock.mock.calls[0]![0]).toMatchObject({ url: '/order/payment', method: 'put' })
    expect(JSON.parse(mock.mock.calls[0]![0].data as string)).toEqual({
      orderNumber: 'ORDER-7',
      payMethod: 1,
    })
  })
})
