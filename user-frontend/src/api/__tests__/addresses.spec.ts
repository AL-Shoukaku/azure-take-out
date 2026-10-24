import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter } from 'axios'
import {
  createAddress,
  deleteAddress,
  getAddress,
  getAddresses,
  setDefaultAddress,
  updateAddress,
} from '../addresses'
import { http } from '@/utils/request'

const originalAdapter = http.defaults.adapter
afterEach(() => {
  http.defaults.adapter = originalAdapter
})

function mockResponse(data: unknown) {
  const adapter = vi.fn<AxiosAdapter>(async (config) => ({
    data: { code: 1, msg: null, data },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  }))
  http.defaults.adapter = adapter
  return adapter
}

describe('当前用户的收货地址接口', () => {
  it('分别读取列表与编辑所需的最新详情', async () => {
    const addresses = [{ id: 7, detail: '学府路 8 号', isDefault: 1 }]
    const list = mockResponse(addresses)
    await expect(getAddresses()).resolves.toEqual(addresses)
    expect(list.mock.calls[0]![0].url).toBe('/addressBook/list')
    const detail = mockResponse(addresses[0])
    await expect(getAddress(7)).resolves.toEqual(addresses[0])
    expect(detail.mock.calls[0]![0].url).toBe('/addressBook/7')
  })

  it('新增和编辑按现有接口提交，不携带默认标记或用户 ID', async () => {
    const data = {
      consignee: '小蓝',
      phone: '13800000000',
      provinceName: '浙江省',
      cityName: '杭州市',
      districtName: '西湖区',
      provinceCode: '',
      cityCode: '',
      districtCode: '',
      detail: '学府路 8 号',
      label: '家',
    }
    const adapter = mockResponse(null)
    await expect(createAddress(data)).resolves.toBeNull()
    await expect(updateAddress({ ...data, id: 7 })).resolves.toBeNull()
    const configs = adapter.mock.calls.map(([config]) => config)
    expect(configs.map(({ url, method }) => [url, method])).toEqual([
      ['/addressBook', 'post'],
      ['/addressBook', 'put'],
    ])
    expect(JSON.parse(configs[0]!.data as string)).toEqual(data)
    expect(JSON.parse(configs[1]!.data as string)).toEqual({ ...data, id: 7 })
  })

  it('设默认使用请求体，删除使用 id 查询参数', async () => {
    const adapter = mockResponse(null)
    await setDefaultAddress(7)
    await deleteAddress(7)
    const defaults = adapter.mock.calls[0]![0]
    expect([defaults.url, defaults.method]).toEqual(['/addressBook/default', 'put'])
    expect(JSON.parse(defaults.data as string)).toEqual({ id: 7 })
    const deletion = adapter.mock.calls[1]![0]
    expect([deletion.url, deletion.method, deletion.params]).toEqual([
      '/addressBook',
      'delete',
      { id: 7 },
    ])
  })
})
