import type { Page } from '@playwright/test'

export const reportData = {
  turnoverStatistics: {
    dateList: '2026-10-11,2026-10-12,2026-10-13,2026-10-14,2026-10-15,2026-10-16,2026-10-17',
    turnoverList: '0,100.5,0,300,80,200,50',
  },
  userStatistics: {
    dateList: '2026-10-11,2026-10-12,2026-10-13,2026-10-14,2026-10-15,2026-10-16,2026-10-17',
    totalUserList: '2,3,3,5,5,6,6',
    newUserList: '0,1,0,2,0,1,0',
  },
  ordersStatistics: {
    dateList: '2026-10-11,2026-10-12,2026-10-13,2026-10-14,2026-10-15,2026-10-16,2026-10-17',
    orderCountList: '0,2,0,4,1,2,3',
    validOrderCountList: '0,1,0,3,1,2,2',
    totalOrderCount: 12,
    validOrderCount: 9,
    orderCompletionRate: 0.75,
  },
  top10: { nameList: '白米饭,宫保鸡丁单人套餐,可乐', numberList: '9,3,2' },
}

export async function mockReportAPIs(page: Page) {
  const requests: { endpoint: string; begin: string | null; end: string | null }[] = []
  await page.route(
    /\/api\/report\/(turnoverStatistics|userStatistics|ordersStatistics|top10)\?/,
    (route) => {
      const url = new URL(route.request().url())
      const endpoint = url.pathname.split('/').pop() as keyof typeof reportData
      requests.push({
        endpoint,
        begin: url.searchParams.get('begin'),
        end: url.searchParams.get('end'),
      })
      return route.fulfill({ json: { code: 1, msg: null, data: reportData[endpoint] } })
    },
  )
  return requests
}
