import { afterEach, expect, it, vi } from 'vitest'
import type { AxiosAdapter } from 'axios'
import { http } from '@/utils/request'
import { getCategories, getProducts, getSetmealItems, getShopStatus } from '../menu'
import { addCartItem, subtractCartItem, cleanCart } from '../cart'
import { submitOrder } from '../orders'

const original = http.defaults.adapter
afterEach(() => {
  http.defaults.adapter = original
})
it('分类区分菜品和套餐，并使用现有列表、明细与营业状态接口', async () => {
  const mock = vi.fn<AxiosAdapter>(async (config) => ({
    data: { code: 1, data: [], msg: null },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  }))
  http.defaults.adapter = mock
  await getCategories()
  await getProducts({ id: 3, type: 1, name: '热菜' })
  await getProducts({ id: 7, type: 2, name: '套餐' })
  await getSetmealItems(10)
  await getShopStatus()
  expect(mock.mock.calls.map(([c]) => [c.url, c.params])).toEqual([
    ['/category/list', undefined],
    ['/dish/list', { categoryId: 3 }],
    ['/setmeal/list', { categoryId: 7 }],
    ['/setmeal/dish/10', undefined],
    ['/shop/status', undefined],
  ])
})
it('购物车按商品及口味加减，下单只提交配送信息，金额由后端计算', async () => {
  const mock = vi.fn<AxiosAdapter>(async (config) => ({
    data: { code: 1, data: null, msg: null },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  }))
  http.defaults.adapter = mock
  await addCartItem({ dishId: 1, dishFlavor: '微辣,不要葱' })
  await subtractCartItem({ setmealId: 2, dishFlavor: null })
  await cleanCart()
  const data = {
    addressBookId: 4,
    payMethod: 1 as const,
    deliveryStatus: 1 as const,
    tablewareStatus: 1 as const,
    tablewareNumber: 0,
    remark: '少油',
  }
  await submitOrder(data)
  expect(mock.mock.calls.map(([c]) => [c.method, c.url])).toEqual([
    ['post', '/shoppingCart/add'],
    ['post', '/shoppingCart/sub'],
    ['delete', '/shoppingCart/clean'],
    ['post', '/order/submit'],
  ])
  expect(JSON.parse(mock.mock.calls[0]![0].data as string)).toEqual({
    dishId: 1,
    dishFlavor: '微辣,不要葱',
  })
  expect(JSON.parse(mock.mock.calls[1]![0].data as string)).toEqual({
    setmealId: 2,
    dishFlavor: null,
  })
  expect(JSON.parse(mock.mock.calls[3]![0].data as string)).toEqual(data)
})
