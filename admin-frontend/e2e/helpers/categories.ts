import type { Page } from '@playwright/test'

export interface CategoryFixture {
  id: number
  type: 1 | 2
  name: string
  sort: number
  status: 0 | 1
  updateTime: string
}

export async function mockCategories(page: Page, count = 45) {
  let nextId = count + 1
  let records: CategoryFixture[] = Array.from({ length: count }, (_, index) => ({
    id: index + 1,
    type: index % 2 === 0 ? 1 : 2,
    name: `${index % 2 === 0 ? '菜品' : '套餐'}分类${index + 1}`,
    sort: index + 1,
    status: 1,
    updateTime: '2026-09-28 12:00:59',
  }))
  Object.assign(records[0]!, { name: '川味招牌' })
  Object.assign(records[1]!, { name: '单人套餐', status: 0 })
  const queries: URLSearchParams[] = []
  const writes: { method: string; path: string; body: unknown; id: string | null }[] = []
  const controls = {
    queries,
    writes,
    failures: { list: '', save: '', delete: '', status: '' },
    delaySave: false,
    releaseSave: () => finishSave?.(),
  }
  let finishSave: (() => void) | undefined
  await page.route(/\/api\/category(?:\/page\?|\/status\/|\?|$)/, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const success = (data: unknown) => route.fulfill({ json: { code: 1, msg: null, data } })
    const fail = (msg: string) => route.fulfill({ json: { code: 0, msg, data: null } })
    if (request.method() === 'GET') {
      if (!url.pathname.endsWith('/page')) throw new Error('分类页面只能使用分页接口读取数据')
      queries.push(url.searchParams)
      if (controls.failures.list) return fail(controls.failures.list)
      const type = Number(url.searchParams.get('type'))
      const name = url.searchParams.get('name') || ''
      const filtered = records
        .filter((record) => (!type || record.type === type) && record.name.includes(name))
        .sort((a, b) => a.sort - b.sort || b.id - a.id)
      const current = Number(url.searchParams.get('page'))
      const size = Number(url.searchParams.get('pageSize'))
      return success({
        total: filtered.length,
        records: filtered.slice((current - 1) * size, current * size),
      })
    }
    const method = request.method()
    writes.push({
      method,
      path: url.pathname,
      body: request.postDataJSON(),
      id: url.searchParams.get('id'),
    })
    if (method === 'DELETE') {
      if (controls.failures.delete) return fail(controls.failures.delete)
      records = records.filter((record) => record.id !== Number(url.searchParams.get('id')))
    } else if (url.pathname.includes('/status/')) {
      if (controls.failures.status) return fail(controls.failures.status)
      records.find((record) => record.id === Number(url.searchParams.get('id')))!.status = Number(
        url.pathname.split('/').pop(),
      ) as 0 | 1
    } else {
      if (controls.delaySave)
        await new Promise<void>((resolve) => {
          finishSave = resolve
        })
      if (controls.failures.save) return fail(controls.failures.save)
      const body = request.postDataJSON()
      if (method === 'PUT')
        Object.assign(
          records.find((record) => record.id === body.id)!,
          body,
        )
      else records.push({ ...body, id: nextId++, status: 0, updateTime: '2026-10-18 14:00' })
    }
    return success(null)
  })
  return controls
}
