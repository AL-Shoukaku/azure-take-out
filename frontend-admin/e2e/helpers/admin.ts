import type { BrowserContext, Page, WebSocketRoute } from '@playwright/test'

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
