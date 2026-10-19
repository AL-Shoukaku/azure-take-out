import { Blob as NodeBlob } from 'node:buffer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError, type AxiosAdapter } from 'axios'
import { http } from '@/utils/request'
import { setToken, removeToken } from '@/utils/auth'
import {
  getTurnoverReport,
  getUserReport,
  getOrderReport,
  getSalesTop10,
  exportBusinessReport,
} from '../report'

const originalAdapter = http.defaults.adapter
beforeEach(() => vi.stubGlobal('Blob', NodeBlob))
afterEach(() => {
  http.defaults.adapter = originalAdapter
  vi.unstubAllGlobals()
  removeToken()
})

function respond(data: unknown) {
  const adapter = vi.fn<AxiosAdapter>(async (config) => ({
    data,
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  }))
  http.defaults.adapter = adapter
  return adapter
}

describe('数据统计接口', () => {
  it.each([
    [getTurnoverReport, 'turnoverStatistics', { dateList: '2026-10-17', turnoverList: '18.5' }],
    [
      getUserReport,
      'userStatistics',
      { dateList: '2026-10-17', totalUserList: '6', newUserList: '1' },
    ],
    [
      getOrderReport,
      'ordersStatistics',
      { orderCompletionRate: 0.75, totalOrderCount: 4, validOrderCount: 3 },
    ],
    [getSalesTop10, 'top10', { nameList: '白米饭,单人套餐', numberList: '9,2' }],
  ] as const)('读取 %s，使用后端规定的 begin/end 日期参数', async (load, endpoint, data) => {
    const adapter = respond({ code: 1, msg: null, data })
    const params = { begin: '2026-10-11', end: '2026-10-17' }
    await expect(load(params)).resolves.toEqual(data)
    expect(adapter.mock.calls[0]![0]).toMatchObject({
      url: `/report/${endpoint}`,
      method: 'get',
      params,
    })
  })

  it('保留统计接口原始业务报错', async () => {
    respond({ code: 0, msg: '日期不能为空，且开始日期不能晚于结束日期', data: null })
    await expect(getSalesTop10({ begin: '2026-10-18', end: '2026-10-17' })).rejects.toThrow(
      '日期不能为空，且开始日期不能晚于结束日期',
    )
  })

  it('导出携带 token，按二进制读取，不发送后端不支持的日期筛选', async () => {
    setToken('report-test-token')
    const blob = new Blob([new Uint8Array([80, 75, 3, 4])], { type: 'application/octet-stream' })
    const adapter = respond(blob)
    await expect(exportBusinessReport()).resolves.toBe(blob)
    const config = adapter.mock.calls[0]![0]
    expect(config).toMatchObject({ url: '/report/export', method: 'get', responseType: 'blob' })
    expect(config.params).toBeUndefined()
    expect(config.headers.get('token')).toBe('report-test-token')
  })

  it('导出返回 JSON 业务错误时显示原始原因，不能下载错误文件', async () => {
    respond(
      new Blob([JSON.stringify({ code: 0, msg: '报表生成失败', data: null })], {
        type: 'application/json',
      }),
    )
    await expect(exportBusinessReport()).rejects.toThrow('报表生成失败')
  })

  it('导出 HTTP 失败时也能解析 Blob 中的后端报错', async () => {
    http.defaults.adapter = async (config) => {
      throw new AxiosError('Request failed', 'ERR_BAD_RESPONSE', config, undefined, {
        data: new Blob([JSON.stringify({ code: 0, msg: '报表模板读取失败', data: null })], {
          type: 'application/json',
        }),
        status: 500,
        statusText: 'Internal Server Error',
        headers: {},
        config,
      })
    }
    await expect(exportBusinessReport()).rejects.toThrow('报表模板读取失败')
  })
})
