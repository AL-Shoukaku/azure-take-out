import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter } from 'axios'
import { http } from '@/utils/request'
import { getCategoryList } from '../category'
import { dishAPI, setmealAPI, getAvailableDishes, uploadImage } from '../products'

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

for (const [kind, api] of [
  ['dish', dishAPI],
  ['setmeal', setmealAPI],
] as const) {
  describe(`${kind} 接口契约`, () => {
    it('组合查询保留停售状态 0 和分类 ID', async () => {
      const result = { total: 0, records: [] }
      const adapter = respond(result)
      await expect(
        api.page({ page: 2, pageSize: 20, name: '招牌', categoryId: 3, status: 0 }),
      ).resolves.toEqual(result)
      expect(adapter.mock.calls[0]![0]).toMatchObject({
        url: `/${kind}/page`,
        params: { page: 2, pageSize: 20, name: '招牌', categoryId: 3, status: 0 },
      })
    })
    it('详情包含关联信息', async () => {
      const adapter = respond({ id: 5, name: '招牌' })
      await expect(api.detail(5)).resolves.toMatchObject({ id: 5 })
      expect(adapter.mock.calls[0]![0]).toMatchObject({ url: `/${kind}/5`, method: 'get' })
    })
    it('删除以逗号连接多个 ID', async () => {
      const adapter = respond()
      await api.remove([3, 5])
      expect(adapter.mock.calls[0]![0]).toMatchObject({
        url: `/${kind}`,
        method: 'delete',
        params: { ids: '3,5' },
      })
    })
    it.each([0, 1] as const)('起停售 %s 只传单个 ID', async (status) => {
      const adapter = respond()
      await api.status(5, status)
      expect(adapter.mock.calls[0]![0]).toMatchObject({
        url: `/${kind}/status/${status}`,
        method: 'post',
        params: { id: 5 },
      })
    })
    it('保留后端删除限制的错误', async () => {
      respond(null, 0, '起售中的商品不能删除')
      await expect(api.remove([5])).rejects.toThrow('起售中的商品不能删除')
    })
  })
}

it('菜品新增与更新保留序列化口味，价格使用元', async () => {
  const data = {
    name: '招牌菜品',
    categoryId: 1,
    price: 12.5,
    image: '/image.jpg',
    description: '',
    status: 0 as const,
    flavors: [{ name: '辣度', value: '["微辣","中辣"]' }],
  }
  const adapter = respond()
  await dishAPI.create(data)
  await dishAPI.update({ ...data, id: 5, status: 1 })
  expect(JSON.parse(adapter.mock.calls[0]![0].data as string)).toEqual(data)
  expect(adapter.mock.calls[1]![0].method).toBe('put')
})

it('套餐提交选菜 ID、名称、原价和份数', async () => {
  const data = {
    name: '单人套餐',
    categoryId: 7,
    price: 32.5,
    image: '/image.jpg',
    description: '',
    status: 0 as const,
    setmealDishes: [{ dishId: 5, name: '招牌菜品', price: 12.5, copies: 2 }],
  }
  const adapter = respond()
  await setmealAPI.create(data)
  await setmealAPI.update({ ...data, id: 7 })
  expect(JSON.parse(adapter.mock.calls[0]![0].data as string)).toEqual(data)
  expect(adapter.mock.calls[1]![0].method).toBe('put')
})

it('分类仅使用后端启用分类列表，选菜按分类加载', async () => {
  const adapter = respond([])
  await getCategoryList(2)
  await getAvailableDishes(3)
  expect(adapter.mock.calls[0]![0]).toMatchObject({ url: '/category/list', params: { type: 2 } })
  expect(adapter.mock.calls[1]![0]).toMatchObject({ url: '/dish/list', params: { categoryId: 3 } })
})

it('上传使用 multipart 的 file 字段并读取返回 URL', async () => {
  const adapter = respond('http://localhost:8080/images/new.png')
  const file = new File(['image'], 'dish.png', { type: 'image/png' })
  await expect(uploadImage(file)).resolves.toBe('http://localhost:8080/images/new.png')
  const config = adapter.mock.calls[0]![0]
  expect(config).toMatchObject({ url: '/common/upload', method: 'post' })
  expect((config.data as FormData).get('file')).toBe(file)
})
