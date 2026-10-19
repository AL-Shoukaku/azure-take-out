import type { BrowserContext, Page, WebSocketRoute } from '@playwright/test'
import { mockReportAPIs } from './reports.js'
import { mockWorkspace } from './workspace.js'

export const employee = {
  id: 7,
  userName: 'store-admin',
  name: '测试店长',
  token: 'layout-test-token',
}

export async function mockLayoutAPIs(page: Page) {
  const sockets: WebSocketRoute[] = []
  await page.route('**/api/shop/status', (route) =>
    route.fulfill({ json: { code: 1, msg: null, data: 1 } }),
  )
  await page.route('**/api/employee/logout', (route) =>
    route.fulfill({ json: { code: 1, msg: null, data: null } }),
  )
  await page.route('**/api/employee/page?**', (route) =>
    route.fulfill({ json: { code: 1, msg: null, data: { total: 0, records: [] } } }),
  )
  await page.route('**/api/category/page?**', (route) =>
    route.fulfill({ json: { code: 1, msg: null, data: { total: 0, records: [] } } }),
  )
  await page.route(/\/api\/(dish|setmeal)\/page\?/, (route) =>
    route.fulfill({ json: { code: 1, msg: null, data: { total: 0, records: [] } } }),
  )
  await page.route('**/api/category/list?**', (route) =>
    route.fulfill({ json: { code: 1, msg: null, data: [] } }),
  )
  await page.route('**/api/order/conditionSearch?**', (route) =>
    route.fulfill({ json: { code: 1, msg: null, data: { total: 0, records: [] } } }),
  )
  await page.route('**/api/order/statistics', (route) =>
    route.fulfill({
      json: { code: 1, msg: null, data: { toBeConfirmed: 0, confirmed: 0, deliveryInProgress: 0 } },
    }),
  )
  await page.route('**/api/order/details/*', (route) =>
    route.fulfill({ json: { code: 0, msg: '订单不存在', data: null } }),
  )
  await mockReportAPIs(page)
  await mockWorkspace(page)
  await page.routeWebSocket(/\/ws\//, (socket) => {
    sockets.push(socket)
  })
  return sockets
}

export async function mockAdminSession(page: Page, context: BrowserContext, baseURL: string) {
  await context.addCookies([{ name: 'token', value: employee.token, url: baseURL }])
  await page.addInitScript(
    (profile) => {
      if (!localStorage.getItem('sky-admin-employee')) {
        localStorage.setItem('sky-admin-employee', JSON.stringify(profile))
      }
    },
    { id: employee.id, userName: employee.userName, name: employee.name },
  )
  return mockLayoutAPIs(page)
}
