import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter } from 'axios'
import { getShopStatus, setShopStatus } from '../shop'
import { editPassword, logout } from '../auth'
import { http } from '@/utils/request'

const originalAdapter = http.defaults.adapter

afterEach(() => {
  http.defaults.adapter = originalAdapter
})

function mockResponse(data: unknown) {
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

describe('后台布局接口', () => {
  it('读取后端店铺状态，0 表示打烊，1 表示营业', async () => {
    const adapter = mockResponse({ code: 1, msg: null, data: 0 })
    await expect(getShopStatus()).resolves.toBe(0)
    expect(adapter.mock.calls[0]![0]).toMatchObject({ url: '/shop/status', method: 'get' })
  })

  it.each([0, 1] as const)(
    '使用 PUT /shop/%s 保存状态，支持成功响应 data 为 null',
    async (status) => {
      const adapter = mockResponse({ code: 1, msg: null, data: null })
      await expect(setShopStatus(status)).resolves.toBeNull()
      expect(adapter.mock.calls[0]![0]).toMatchObject({ url: `/shop/${status}`, method: 'put' })
    },
  )

  it('修改密码只提交原密码和新密码，保留密码大小写及特殊字符', async () => {
    const adapter = mockResponse({ code: 1, msg: null, data: null })
    await editPassword({ oldPassword: ' old$Pass ', newPassword: 'NewPass123' })
    const config = adapter.mock.calls[0]![0]
    expect(config).toMatchObject({ url: '/employee/editPassword', method: 'put' })
    expect(JSON.parse(config.data as string)).toEqual({
      oldPassword: ' old$Pass ',
      newPassword: 'NewPass123',
    })
  })

  it('密码修改失败时保留后端原始报错', async () => {
    mockResponse({ code: 0, msg: '密码错误', data: null })
    await expect(editPassword({ oldPassword: 'wrong', newPassword: 'NewPass123' })).rejects.toThrow(
      '密码错误',
    )
  })

  it('退出调用当前后端的员工退出接口', async () => {
    const adapter = mockResponse({ code: 1, msg: null, data: null })
    await logout()
    expect(adapter.mock.calls[0]![0]).toMatchObject({ url: '/employee/logout', method: 'post' })
  })
})
