import type { Page } from '@playwright/test'
import { restoreSession } from './session'
export const menuProducts = Array.from({ length: 8 }, (_, i) => ({
  id: i + 1,
  categoryId: 1,
  name: i === 0 ? '宫保鸡丁' : `家常菜${i + 1}`,
  price: i === 0 ? 22 : 16,
  image: null,
  description: '现点现做，热乎到家',
  status: 1,
  flavors:
    i === 0
      ? [
          { id: 1, dishId: 1, name: '辣度', value: '["微辣","中辣","重辣"]' },
          { id: 2, dishId: 1, name: '忌口', value: '["不要葱","不要蒜"]' },
        ]
      : [],
}))
interface FixtureItem {
  id: number
  dishId: number | null
  setmealId: number | null
  name: string
  dishFlavor: string | null
  amount: number
  number: number
  image: null
}
export async function mockMenu(
  page: Page,
  options: { closed?: boolean; addresses?: boolean } = {},
) {
  await restoreSession(page)
  let cart: FixtureItem[] = []
  const mutations: { action: string; data: unknown }[] = []
  const submissions: Record<string, unknown>[] = []
  await page.route('**/api/category/list', (r) =>
    r.fulfill({
      json: {
        code: 1,
        data: [
          { id: 1, type: 1, name: '家常热菜' },
          { id: 2, type: 2, name: '精选套餐' },
        ],
      },
    }),
  )
  await page.route('**/api/dish/list?*', (r) =>
    r.fulfill({ json: { code: 1, data: menuProducts } }),
  )
  await page.route('**/api/setmeal/list?*', (r) =>
    r.fulfill({
      json: {
        code: 1,
        data: [
          {
            id: 1,
            categoryId: 2,
            name: '双人套餐',
            price: 58,
            image: null,
            description: '两人分享刚刚好',
            status: 1,
          },
        ],
      },
    }),
  )
  await page.route('**/api/setmeal/dish/1', (r) =>
    r.fulfill({
      json: {
        code: 1,
        data: [
          { name: '宫保鸡丁', copies: 1, image: null, description: '香气满满' },
          { name: '米饭', copies: 2, image: null, description: '香软米饭' },
        ],
      },
    }),
  )
  await page.route('**/api/shop/status', (r) =>
    r.fulfill({ json: { code: 1, data: options.closed ? 0 : 1 } }),
  )
  await page.route('**/api/addressBook/list', (r) =>
    r.fulfill({
      json: {
        code: 1,
        data:
          options.addresses === false
            ? []
            : [
                {
                  id: 1,
                  consignee: '小蓝',
                  phone: '13800000000',
                  provinceName: '浙江省',
                  cityName: '杭州市',
                  districtName: '西湖区',
                  detail: '晴空路16号',
                  isDefault: 1,
                  label: '家',
                },
              ],
      },
    }),
  )
  await page.route('**/api/shoppingCart/**', (r) => {
    const action = r.request().url().split('/').pop()!
    if (action === 'list') return r.fulfill({ json: { code: 1, data: cart } })
    const data = action === 'clean' ? null : r.request().postDataJSON()
    mutations.push({ action, data })
    if (action === 'clean') cart = []
    else {
      const found = cart.find(
        (item) =>
          item.dishId === (data.dishId ?? null) &&
          item.setmealId === (data.setmealId ?? null) &&
          item.dishFlavor === data.dishFlavor,
      )
      if (action === 'sub') {
        if (found) {
          found.number--
          cart = cart.filter((item) => item.number > 0)
        }
      } else if (found) found.number++
      else {
        const product = data.setmealId
          ? { id: 1, name: '双人套餐', price: 58 }
          : menuProducts.find((p) => p.id === data.dishId)!
        cart.push({
          id: Date.now(),
          dishId: data.dishId ?? null,
          setmealId: data.setmealId ?? null,
          name: product.name,
          dishFlavor: data.dishFlavor,
          number: 1,
          amount: product.price,
          image: null,
        })
      }
    }
    return r.fulfill({ json: { code: 1, data: null } })
  })
  await page.route('**/api/order/submit', (r) => {
    submissions.push(r.request().postDataJSON())
    const amount = cart.reduce((sum, item) => sum + (item.amount + 1) * item.number, 6)
    cart = []
    return r.fulfill({
      json: {
        code: 1,
        data: {
          id: 99,
          orderNumber: 'TEST-99',
          orderAmount: amount,
          orderTime: '2026-10-22 12:00',
        },
      },
    })
  })
  await page.route('**/api/order/payment', (r) => r.fulfill({ json: { code: 1, data: {} } }))
  return { mutations, submissions }
}
