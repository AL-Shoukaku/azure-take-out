import type { Page } from '@playwright/test'

export function makeOrder(id: number, status: number) {
  return {
    id,
    number: `order-${String(id).padStart(4, '0')}`,
    status,
    userName: '下单用户',
    consignee: `收货人${id}`,
    phone: '19912345678',
    address: '测试路1号',
    orderTime: '2026-10-18 12:34:56',
    checkoutTime: status === 1 ? null : '2026-10-18 12:35:00',
    estimatedDeliveryTime: '2026-10-18 13:30:00',
    deliveryTime: status === 5 ? '2026-10-18 13:20:00' : null,
    cancelTime: status === 6 ? '2026-10-18 12:40:00' : null,
    cancelReason: null as string | null,
    rejectionReason: status === 6 ? '餐厅已打烊，暂时无法接单' : null,
    amount: 33,
    packAmount: 3,
    payMethod: 1,
    payStatus: status === 1 ? 0 : status === 6 ? 2 : 1,
    deliveryStatus: 1,
    tablewareNumber: 2,
    tablewareStatus: 0,
    remark: '少放辣椒',
    orderDishes: '招牌菜*2;米饭*1;',
    orderDetailList: [
      { id: 101, name: '招牌菜', dishFlavor: '微辣', number: 2, amount: 10, image: '' },
      { id: 102, name: '米饭', dishFlavor: null, number: 1, amount: 4, image: '' },
    ],
  }
}

export async function mockOrders(page: Page, total = 36) {
  const records = Array.from({ length: total }, (_, i) => makeOrder(i + 1, (i % 6) + 1))
  const queries: URLSearchParams[] = [],
    details: number[] = []
  const writes: { path: string; body: Record<string, unknown> | null }[] = []
  let finish: (() => void) | undefined
  const api = {
    records,
    queries,
    details,
    writes,
    statsReads: 0,
    delaySave: false,
    releaseSave: () => finish?.(),
    failures: { list: '', detail: '', statistics: '', action: '' },
  }
  await page.route(/\/api\/order\//, async (route) => {
    const request = route.request(),
      url = new URL(request.url())
    const success = (data: unknown) => route.fulfill({ json: { code: 1, msg: null, data } })
    const fail = (msg: string) => route.fulfill({ json: { code: 0, msg, data: null } })
    if (url.pathname.endsWith('/statistics')) {
      api.statsReads++
      if (api.failures.statistics) return fail(api.failures.statistics)
      return success({
        toBeConfirmed: records.filter((r) => r.status === 2).length,
        confirmed: records.filter((r) => r.status === 3).length,
        deliveryInProgress: records.filter((r) => r.status === 4).length,
      })
    }
    if (url.pathname.endsWith('/conditionSearch')) {
      queries.push(url.searchParams)
      if (api.failures.list) return fail(api.failures.list)
      const p = url.searchParams
      const filtered = records.filter(
        (r) =>
          (!p.has('status') || r.status === Number(p.get('status'))) &&
          r.number.includes(p.get('number') || '') &&
          r.phone.includes(p.get('phone') || '') &&
          (!p.has('beginTime') || r.orderTime >= p.get('beginTime')!) &&
          (!p.has('endTime') || r.orderTime <= p.get('endTime')!),
      )
      const start = (Number(p.get('page')) - 1) * Number(p.get('pageSize'))
      return success({
        total: filtered.length,
        records: filtered.slice(start, start + Number(p.get('pageSize'))),
      })
    }
    if (url.pathname.includes('/details/')) {
      const id = Number(url.pathname.split('/').pop())
      details.push(id)
      if (api.failures.detail) return fail(api.failures.detail)
      const row = records.find((r) => r.id === id)
      return row ? success(row) : fail('订单不存在')
    }
    const body = request.postDataJSON() as Record<string, unknown> | null
    writes.push({ path: url.pathname, body })
    if (api.delaySave)
      await new Promise<void>((resolve) => {
        finish = resolve
      })
    if (api.failures.action) return fail(api.failures.action)
    const id = Number(body?.id || url.pathname.split('/').pop())
    const row = records.find((r) => r.id === id)!
    if (url.pathname.endsWith('/confirm')) row.status = 3
    else if (url.pathname.includes('/delivery/')) row.status = 4
    else if (url.pathname.includes('/complete/')) {
      row.status = 5
      row.deliveryTime = '2026-10-18 13:20:00'
    } else {
      if (url.pathname.endsWith('/cancel')) {
        if (![1, 3, 4, 5].includes(row.status)) return fail('订单状态错误')
        if (typeof body?.cancelReason !== 'string' || !body.cancelReason.trim())
          return fail('订单取消原因不能为空')
      } else if (row.status !== 2) return fail('订单状态错误')
      row.status = 6
      if (row.payStatus === 1) row.payStatus = 2
      row.cancelTime = '2026-10-18 12:40:00'
      row.cancelReason = (body?.cancelReason as string | undefined) || null
      row.rejectionReason = (body?.rejectionReason as string | undefined) || null
    }
    return success(null)
  })
  return api
}
