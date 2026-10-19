import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter } from 'axios'
import { http } from '@/utils/request'
import {
  getOrderPage,
  getOrderStatistics,
  getOrderDetail,
  confirmOrder,
  rejectOrder,
  cancelOrder,
  deliverOrder,
  completeOrder,
} from '../order'

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

describe('订单接口', () => {
  it('组合查询传递状态、分页和完整日期范围', async () => {
    const result = { total: 0, records: [] }
    const adapter = respond(result)
    const params = {
      page: 2,
      pageSize: 20,
      number: '20261018',
      phone: '199',
      status: 2 as const,
      beginTime: '2026-10-17 00:00:00',
      endTime: '2026-10-18 23:59:59',
    }
    await expect(getOrderPage(params)).resolves.toEqual(result)
    expect(adapter.mock.calls[0]![0]).toMatchObject({
      url: '/order/conditionSearch',
      method: 'get',
      params,
    })
  })
  it('统计接口获取三个待处理数量', async () => {
    const data = { toBeConfirmed: 2, confirmed: 1, deliveryInProgress: 1 }
    const adapter = respond(data)
    await expect(getOrderStatistics()).resolves.toEqual(data)
    expect(adapter.mock.calls[0]![0]).toMatchObject({ url: '/order/statistics', method: 'get' })
  })
  it('详情按 ID 读取真实状态和订单明细', async () => {
    const data = { id: 12, status: 4, orderDetailList: [{ number: 2, amount: 12 }] }
    const adapter = respond(data)
    await expect(getOrderDetail(12)).resolves.toEqual(data)
    expect(adapter.mock.calls[0]![0]).toMatchObject({ url: '/order/details/12', method: 'get' })
  })
  it('接单只提交 ID，不从客户端设置状态', async () => {
    const adapter = respond()
    await confirmOrder(12)
    expect(adapter.mock.calls[0]![0]).toMatchObject({ url: '/order/confirm', method: 'put' })
    expect(JSON.parse(adapter.mock.calls[0]![0].data as string)).toEqual({ id: 12 })
  })
  it('拒单和取消使用各自原因字段', async () => {
    const adapter = respond()
    await rejectOrder(12, '餐厅已打烊，暂时无法接单')
    await cancelOrder(13, '客户电话取消')
    expect(adapter.mock.calls[0]![0]).toMatchObject({ url: '/order/rejection', method: 'put' })
    expect(JSON.parse(adapter.mock.calls[0]![0].data as string)).toEqual({
      id: 12,
      rejectionReason: '餐厅已打烊，暂时无法接单',
    })
    expect(adapter.mock.calls[1]![0]).toMatchObject({ url: '/order/cancel', method: 'put' })
    expect(JSON.parse(adapter.mock.calls[1]![0].data as string)).toEqual({
      id: 13,
      cancelReason: '客户电话取消',
    })
  })
  it('派送和完成通过路径传递 ID', async () => {
    const adapter = respond()
    await deliverOrder(12)
    await completeOrder(13)
    expect(adapter.mock.calls[0]![0]).toMatchObject({ url: '/order/delivery/12', method: 'put' })
    expect(adapter.mock.calls[1]![0]).toMatchObject({ url: '/order/complete/13', method: 'put' })
  })
  it.each(['订单状态错误', '订单不存在'])('保留后端提示：%s', async (msg) => {
    respond(null, 0, msg)
    await expect(confirmOrder(12)).rejects.toThrow(msg)
  })
})
