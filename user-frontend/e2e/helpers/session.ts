import { expect, type Page } from '@playwright/test'

export const currentUser = {
  id: 7,
  account: 'customer',
  username: '小蓝',
  avatar: null,
  name: null,
  phone: null,
  sex: null,
  idNumber: null,
}

export async function mockUserInfo(page: Page): Promise<void> {
  await page.route('**/api/user/info', (route) => {
    expect(route.request().headers().authentication).toBe('e2e-user-token')
    return route.fulfill({ json: { code: 1, msg: null, data: currentUser } })
  })
}

export async function restoreSession(page: Page): Promise<void> {
  await page
    .context()
    .addCookies([{ name: 'token', value: 'e2e-user-token', url: 'http://localhost:5174' }])
  await mockUserInfo(page)
}
