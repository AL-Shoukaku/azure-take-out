import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter } from 'axios'
import {
  createCategory,
  deleteCategory,
  getCategoryPage,
  setCategoryStatus,
  updateCategory,
} from '../category'
import { http } from '@/utils/request'

const originalAdapter = http.defaults.adapter
afterEach(() => {
  http.defaults.adapter = originalAdapter
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

describe('分类管理接口', () => {
  it('分页查询同时传递名称、分类类型及页码', async () => {
    const result = {
      total: 11,
      records: [{ id: 7, type: 2, name: '单人套餐', sort: 0, status: 0 }],
    }
    const adapter = respond({ code: 1, msg: null, data: result })
    await expect(
      getCategoryPage({ name: '套餐', type: 2, page: 2, pageSize: 10 }),
    ).resolves.toEqual(result)
    expect(adapter.mock.calls[0]![0]).toMatchObject({
      url: '/category/page',
      method: 'get',
      params: { name: '套餐', type: 2, page: 2, pageSize: 10 },
    })
  })

  it.each([1, 2] as const)('新增类型 %s，提交数字排序，不指定默认状态', async (type) => {
    const adapter = respond({ code: 1, msg: null, data: null })
    await expect(createCategory({ type, name: '新分类', sort: 0 })).resolves.toBeNull()
    const config = adapter.mock.calls[0]![0]
    expect(config).toMatchObject({ url: '/category', method: 'post' })
    expect(JSON.parse(config.data as string)).toEqual({ type, name: '新分类', sort: 0 })
  })

  it('编辑分类只提交 ID、类型、名称、排序', async () => {
    const adapter = respond({ code: 1, msg: null, data: null })
    await updateCategory({ id: 7, type: 2, name: '修改的套餐', sort: 99 })
    const config = adapter.mock.calls[0]![0]
    expect(config).toMatchObject({ url: '/category', method: 'put' })
    expect(JSON.parse(config.data as string)).toEqual({
      id: 7,
      type: 2,
      name: '修改的套餐',
      sort: 99,
    })
  })

  it('删除使用 DELETE 和查询参数 id', async () => {
    const adapter = respond({ code: 1, msg: null, data: null })
    await deleteCategory(7)
    expect(adapter.mock.calls[0]![0]).toMatchObject({
      url: '/category',
      method: 'delete',
      params: { id: 7 },
    })
  })

  it.each([0, 1] as const)('切换状态 %s 使用 POST 和查询参数 id', async (status) => {
    const adapter = respond({ code: 1, msg: null, data: null })
    await setCategoryStatus(7, status)
    expect(adapter.mock.calls[0]![0]).toMatchObject({
      url: `/category/status/${status}`,
      method: 'post',
      params: { id: 7 },
    })
  })

  it.each(['当前分类关联了菜品,不能删除', '当前分类关联了套餐,不能删除'])(
    '删除失败完整保留后端提示：%s',
    async (msg) => {
      respond({ code: 0, msg, data: null })
      await expect(deleteCategory(7)).rejects.toThrow(msg)
    },
  )
})
