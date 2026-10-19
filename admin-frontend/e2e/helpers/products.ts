import type { Page } from '@playwright/test'

export type Kind = 'dish' | 'setmeal'
export const categories = [
  { id: 1, type: 1, name: '川味招牌' },
  { id: 2, type: 1, name: '家常热菜' },
  { id: 7, type: 2, name: '单人套餐' },
  { id: 8, type: 2, name: '双人套餐' },
]
export const imageURL = 'http://localhost:8080/images/product-test.png'
export const imageBuffer = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j1ZkAAAAASUVORK5CYII=',
  'base64',
)

export async function mockProducts(page: Page, count = 25) {
  const makeRecords = (kind: Kind) =>
    Array.from({ length: count }, (_, i) => ({
      id: i + 1,
      name: `${kind === 'dish' ? '菜品' : '套餐'}${i + 1}`,
      categoryId: kind === 'dish' ? (i % 4 < 2 ? 1 : 2) : i % 2 ? 8 : 7,
      categoryName:
        kind === 'dish' ? (i % 4 < 2 ? '川味招牌' : '家常热菜') : i % 2 ? '双人套餐' : '单人套餐',
      price: 12.5 + i,
      status: i % 2,
      image: imageURL,
      description: '招牌特色',
      updateTime: '2026-10-18 12:34:56',
      flavors: [{ name: '辣度', value: '["微辣","中辣"]' }],
      setmealDishes: [{ dishId: 2, name: '菜品2', price: 13.5, copies: 3 }],
    }))
  const records = { dish: makeRecords('dish'), setmeal: makeRecords('setmeal') }
  const writes: {
    kind: Kind
    method: string
    path: string
    ids: string | null
    body: Record<string, unknown> | null
  }[] = []
  const queries: { kind: Kind; params: URLSearchParams }[] = []
  let finishSave: (() => void) | undefined
  const api = {
    records,
    writes,
    queries,
    uploads: 0,
    delaySave: false,
    releaseSave: () => finishSave?.(),
    failures: {
      list: '',
      detail: '',
      save: '',
      delete: '',
      status: '',
      upload: '',
      categories: '',
      dishes: '',
    },
  }
  await page.route(imageURL, (route) =>
    route.fulfill({ contentType: 'image/png', body: imageBuffer }),
  )
  await page.route('**/api/category/list?**', (route) => {
    const type = Number(new URL(route.request().url()).searchParams.get('type'))
    return route.fulfill({
      json: {
        code: api.failures.categories ? 0 : 1,
        msg: api.failures.categories || null,
        data: categories.filter((c) => c.type === type),
      },
    })
  })
  await page.route('**/api/common/upload', (route) => {
    api.uploads++
    return route.fulfill({
      json: {
        code: api.failures.upload ? 0 : 1,
        msg: api.failures.upload || null,
        data: api.failures.upload ? null : imageURL,
      },
    })
  })
  await page.route(/\/api\/(dish|setmeal)(?:[/?]|$)/, async (route) => {
    const req = route.request(),
      url = new URL(req.url())
    const kind = url.pathname.split('/')[2] as Kind
    const success = (data: unknown) => route.fulfill({ json: { code: 1, msg: null, data } })
    const fail = (msg: string) => route.fulfill({ json: { code: 0, msg, data: null } })
    if (req.method() === 'GET') {
      if (url.pathname.endsWith('/page')) {
        queries.push({ kind, params: url.searchParams })
        if (api.failures.list) return fail(api.failures.list)
        const p = url.searchParams
        const filtered = records[kind].filter(
          (r) =>
            r.name.includes(p.get('name') || '') &&
            (!p.has('categoryId') || r.categoryId === Number(p.get('categoryId'))) &&
            (!p.has('status') || r.status === Number(p.get('status'))),
        )
        const start = (Number(p.get('page')) - 1) * Number(p.get('pageSize'))
        return success({
          total: filtered.length,
          records: filtered.slice(start, start + Number(p.get('pageSize'))),
        })
      }
      if (url.pathname.endsWith('/list')) {
        if (api.failures.dishes) return fail(api.failures.dishes)
        return success(
          records.dish.filter(
            (r) => r.categoryId === Number(url.searchParams.get('categoryId')) && r.status === 1,
          ),
        )
      }
      if (api.failures.detail) return fail(api.failures.detail)
      return success(records[kind].find((r) => r.id === Number(url.pathname.split('/').pop())))
    }
    const body = req.postDataJSON() as Record<string, unknown> | null
    writes.push({
      kind,
      method: req.method(),
      path: url.pathname,
      ids: url.searchParams.get('ids') || url.searchParams.get('id'),
      body,
    })
    if (req.method() === 'DELETE') {
      if (api.failures.delete) return fail(api.failures.delete)
      const ids = (url.searchParams.get('ids') || '').split(',').map(Number)
      records[kind] = records[kind].filter((r) => !ids.includes(r.id))
    } else if (url.pathname.includes('/status/')) {
      if (api.failures.status) return fail(api.failures.status)
      records[kind].find((r) => r.id === Number(url.searchParams.get('id')))!.status = Number(
        url.pathname.split('/').pop(),
      )
    } else {
      if (api.delaySave)
        await new Promise<void>((resolve) => {
          finishSave = resolve
        })
      if (api.failures.save) return fail(api.failures.save)
      if (req.method() === 'PUT')
        Object.assign(
          records[kind].find((r) => r.id === body!.id)!,
          body,
        )
      else records[kind].unshift({ ...records[kind][0]!, ...body, id: 100 + writes.length })
    }
    return success(null)
  })
  return api
}
