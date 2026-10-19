import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter } from 'axios'
import { http } from '@/utils/request'
import {
  getBusinessData,
  getOrderOverview,
  getDishOverview,
  getSetmealOverview,
} from '../workspace'

const originalAdapter = http.defaults.adapter
afterEach(() => {
  http.defaults.adapter = originalAdapter
})

function respond(data: unknown, code = 1, msg: string | null = null) {
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

describe('工作台接口', () => {
  it.each([
    [
      getBusinessData,
      'businessData',
      {
        turnover: 288.35,
        validOrderCount: 3,
        orderCompletionRate: 0.3,
        unitPrice: 96.116666,
        newUsers: 2,
      },
    ],
    [
      getOrderOverview,
      'overviewOrders',
      {
        waitingOrders: 1,
        deliveredOrders: 2,
        completedOrders: 3,
        cancelledOrders: 4,
        allOrders: 10,
      },
    ],
    [getDishOverview, 'overviewDishes', { sold: 30, discontinued: 1 }],
    [getSetmealOverview, 'overviewSetmeals', { sold: 7, discontinued: 1 }],
  ] as const)('读取工作台 %s，不附加后端不支持的日期参数', async (load, endpoint, data) => {
    const adapter = respond(data)
    await expect(load()).resolves.toEqual(data)
    expect(adapter.mock.calls[0]![0]).toMatchObject({
      url: `/workspace/${endpoint}`,
      method: 'get',
    })
    expect(adapter.mock.calls[0]![0].params).toBeUndefined()
  })
  it('无订单时保留后端的零完成率与客单价', async () => {
    const data = {
      turnover: 0,
      validOrderCount: 0,
      orderCompletionRate: 0,
      unitPrice: 0,
      newUsers: 0,
    }
    respond(data)
    await expect(getBusinessData()).resolves.toEqual(data)
  })
  it('展示后端业务报错，供各板块独立重试', async () => {
    respond(null, 0, '今日营业数据读取失败')
    await expect(getBusinessData()).rejects.toThrow('今日营业数据读取失败')
  })
})
