import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter } from 'axios'
import { cancelOrder, getOrder, getOrders, payOrder, remindOrder, repeatOrder } from '../orders'
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
