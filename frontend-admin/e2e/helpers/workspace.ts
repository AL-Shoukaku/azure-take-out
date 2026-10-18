import type { Page } from '@playwright/test'

export async function mockWorkspace(page: Page) {
  const data = {
    businessData: {
      turnover: 288.35,
      validOrderCount: 3,
      orderCompletionRate: 0.3,
      unitPrice: 96.116666,
      newUsers: 2,
    },
    overviewOrders: {
      waitingOrders: 1,
      deliveredOrders: 2,
      completedOrders: 3,
      cancelledOrders: 4,
      allOrders: 10,
    },
    overviewDishes: { sold: 30, discontinued: 1 },
    overviewSetmeals: { sold: 7, discontinued: 1 },
  }
  const failures: Partial<Record<keyof typeof data, string>> = {}
  const reads: string[] = []
  await page.route(/\/api\/workspace\//, (route) => {
    const endpoint = new URL(route.request().url()).pathname.split('/').pop() as keyof typeof data
    reads.push(endpoint)
    return route.fulfill({
      json: failures[endpoint]
        ? { code: 0, msg: failures[endpoint], data: null }
        : { code: 1, msg: null, data: data[endpoint] },
    })
  })
  return { data, failures, reads }
}
