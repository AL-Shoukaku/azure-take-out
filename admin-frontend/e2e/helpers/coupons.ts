import type { Page } from '@playwright/test'

interface CouponPublishDTO {
  name: string
  type: 1 | 2
  totalStock: number
  discountAmount: number
  thresholdAmount: number
  price: number
  startTime: string
  endTime: string
  validTime: number
}
interface Coupon extends CouponPublishDTO {
  id: number
  stock: number
  status: 0 | 1
  createTime: string
  updateTime: string
}
interface CouponPageQuery {
  page: number
  pageSize: number
  name?: string
  type?: number
  status?: number
  activityStatus?: number
}
type CouponUpdateDTO = Pick<Coupon, 'id' | 'name'> & { price?: number }

export async function mockCoupons(page: Page) {
  const records: Coupon[] = Array.from({ length: 25 }, (_, i) => ({
    id: i + 1,
    name: `优惠券${i + 1}`,
    type: i % 2 ? 2 : 1,
    stock: 80 - i,
    totalStock: 100,
    discountAmount: 20.5,
    thresholdAmount: i % 2 ? 60 : 0,
    price: i % 2 ? 0 : 9.9,
    status: i % 2 ? 1 : 0,
    startTime: i % 3 === 0 ? '2099-01-01 10:00' : '2020-01-01 10:00',
    endTime: i % 3 === 2 ? '2020-02-01 10:00' : '2099-02-01 10:00',
    validTime: 7,
    createTime: '2026-11-01 09:00',
    updateTime: '2026-11-02 09:00',
  }))
  const queries: CouponPageQuery[] = []
  const writes: { method: string; path: string; params: Record<string, string>; body: unknown }[] =
    []
  let release: (() => void) | undefined
  const api = {
    records,
    queries,
    writes,
    failures: { list: '', detail: '', save: '', status: '' },
    delaySave: false,
    releaseSave: () => release?.(),
  }
  await page.route(/\/api\/coupon(?:[/?]|$)/, async (route) => {
    const req = route.request()
    const url = new URL(req.url())
    const success = (data: unknown) => route.fulfill({ json: { code: 1, msg: null, data } })
    const fail = (msg: string) => route.fulfill({ json: { code: 0, msg, data: null } })
    if (url.pathname.endsWith('/pageQuery') && req.method() === 'POST') {
      const query = req.postDataJSON() as CouponPageQuery
      queries.push(query)
      if (api.failures.list) return fail(api.failures.list)
      const now = Date.now()
      const filtered = records.filter((r) => {
        const start = new Date(r.startTime.replace(' ', 'T')).getTime()
        const end = new Date(r.endTime.replace(' ', 'T')).getTime()
        const activity = start > now ? 0 : end <= now ? 2 : 1
        return (
          r.name.includes(query.name || '') &&
          (query.type === undefined || r.type === query.type) &&
          (query.status === undefined || r.status === query.status) &&
          (query.activityStatus === undefined || activity === query.activityStatus)
        )
      })
      const offset = (query.page - 1) * query.pageSize
      return success({
        total: filtered.length,
        records: filtered.slice(offset, offset + query.pageSize),
      })
    }
    if (req.method() === 'GET') {
      if (api.failures.detail) return fail(api.failures.detail)
      const record = records.find((r) => r.id === Number(url.pathname.split('/').pop()))
      return record ? success(record) : fail('优惠券不存在')
    }
    writes.push({
      method: req.method(),
      path: url.pathname,
      params: Object.fromEntries(url.searchParams),
      body: req.postData() ? req.postDataJSON() : null,
    })
    if (url.pathname.endsWith('/status') && req.method() === 'PUT') {
      if (api.failures.status) return fail(api.failures.status)
      records.find((r) => r.id === Number(url.searchParams.get('couponId')))!.status = Number(
        url.searchParams.get('status'),
      ) as 0 | 1
    } else {
      if (api.delaySave)
        await new Promise<void>((resolve) => {
          release = resolve
        })
      if (api.failures.save) return fail(api.failures.save)
      if (url.pathname.endsWith('/publish') && req.method() === 'POST') {
        const body = req.postDataJSON() as CouponPublishDTO
        if (
          ![body.startTime, body.endTime].every((value) =>
            /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(value),
          )
        )
          return fail('日期格式必须为 yyyy-MM-dd HH:mm')
        records.unshift({
          ...body,
          id: 100 + writes.length,
          stock: body.totalStock,
          status: 0,
          createTime: '2026-11-06 10:00',
          updateTime: '2026-11-06 10:00',
        })
      } else if (url.pathname.endsWith('/update') && req.method() === 'PUT') {
        const body = req.postDataJSON() as CouponUpdateDTO
        if (Object.keys(body).some((key) => !['id', 'name', 'price'].includes(key)))
          return fail('修改字段超出后端支持范围')
        Object.assign(
          records.find((r) => r.id === body.id)!,
          body,
        )
      } else return fail('接口路径或请求方式不正确')
    }
    return success(null)
  })
  return api
}
