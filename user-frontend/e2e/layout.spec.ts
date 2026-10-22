import { expect, test } from '@playwright/test'
import { currentUser, restoreSession } from './helpers/session'

test.beforeEach(async ({ page }) => restoreSession(page))

const sections = [
  ['/menu', '在线点餐', '热腾腾的烟火气，是日常里最温柔的陪伴。'],
  ['/orders', '我的订单', '每一餐，都是日常的记录。'],
  ['/addresses', '收货地址', '把一份热乎，送到你身边。'],
  ['/profile', '我的', '个人资料与账号设置'],
] as const

test('四个导航入口切换板块并保持选中状态，logo 和用户入口正确跳转', async ({ page }) => {
  await page.goto('/menu')
  const navigation = page.getByRole('navigation', { name: '主导航' })
  await expect(navigation.getByRole('link')).toHaveCount(4)
  for (const [path, label, content] of sections) {
    await navigation.getByRole('link', { name: label, exact: true }).click()
    await expect(page).toHaveURL(path)
    await expect(page.getByRole('main')).toContainText(content)
    await expect(navigation.getByRole('link', { name: label, exact: true })).toHaveAttribute(
      'aria-current',
      'page',
    )
  }
  await page.getByRole('link', { name: '碧蓝外卖，返回在线点餐' }).click()
  await expect(page).toHaveURL('/menu')
  await page.getByRole('link', { name: `你好，${currentUser.username}`, exact: true }).click()
  await expect(page).toHaveURL('/profile')
})

for (const [path, , content] of sections) {
  test(`直接打开并刷新 ${path} 后显示正确板块及当前用户`, async ({ page }) => {
    await page.goto(path)
    await expect(page.getByRole('main')).toContainText(content)
    await expect(
      page.getByRole('link', { name: `你好，${currentUser.username}`, exact: true }),
    ).toBeVisible()
    await page.reload()
    await expect(page.getByRole('main')).toContainText(content)
    await expect(
      page.getByRole('link', { name: `你好，${currentUser.username}`, exact: true }),
    ).toBeVisible()
  })
}

test('桌面导航居中，内容滚动后白色导航栏保持可见', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/menu')
  const header = page.getByRole('banner')
  const nav = await page.getByRole('navigation', { name: '主导航' }).boundingBox()
  expect(Math.abs(nav!.x + nav!.width / 2 - 720)).toBeLessThan(1)
  await expect(header).toHaveCSS('background-color', 'rgb(255, 255, 255)')
  await page.getByRole('main').evaluate((element) => {
    element.style.minHeight = '2000px'
  })
  await page.evaluate(() => window.scrollTo(0, 1000))
  await expect(header).toBeInViewport()
  expect((await header.boundingBox())?.y).toBe(0)
})

test('显示头像；加载失败时提供默认头像，长昵称不会撑开小屏布局', async ({ page }) => {
  const username = '这是一个比较长的用户昵称用来验证导航栏不会被撑开'
  await page.route('**/api/user/info', (route) =>
    route.fulfill({
      json: {
        code: 1,
        msg: null,
        data: { ...currentUser, username, avatar: '/images/user/unavailable.png' },
      },
    }),
  )
  await page.route('**/images/user/unavailable.png', (route) => route.fulfill({ status: 404 }))
  await page.setViewportSize({ width: 375, height: 667 })
  await page.goto('/menu')
  await expect(page.getByRole('link', { name: `你好，${username}`, exact: true })).toBeVisible()
  await expect(page.locator('.avatar-fallback')).toBeVisible()
  for (const [, label] of sections) {
    await expect(
      page
        .getByRole('navigation', { name: '主导航' })
        .getByRole('link', { name: label, exact: true }),
    ).toBeVisible()
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
})

test('请求头像并显示服务端提供的头像图片', async ({ page }) => {
  await page.route('**/api/user/info', (route) =>
    route.fulfill({
      json: { code: 1, msg: null, data: { ...currentUser, avatar: '/images/user/avatar.svg' } },
    }),
  )
  await page.route('**/images/user/avatar.svg', (route) =>
    route.fulfill({
      contentType: 'image/svg+xml',
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><circle cx="20" cy="20" r="20" fill="#2563eb"/></svg>',
    }),
  )
  await page.goto('/menu')
  await expect(page.getByRole('img', { name: '用户头像' })).toBeVisible()
  await expect(page.getByRole('img', { name: '用户头像' })).toHaveJSProperty('naturalWidth', 40)
})

test('Cookie 失效时清除会话并返回登录页', async ({ page, context, baseURL }) => {
  await context.addCookies([{ name: 'admin_token', value: 'existing-admin-token', url: baseURL! }])
  await page.route('**/api/user/info', (route) => route.fulfill({ status: 401 }))
  await page.goto('/orders')
  await expect(page).toHaveURL('/login')
  expect((await context.cookies()).some((cookie) => cookie.name === 'user_token')).toBe(false)
  expect((await context.cookies()).find((cookie) => cookie.name === 'admin_token')?.value).toBe(
    'existing-admin-token',
  )
  await expect(page.getByRole('banner')).toHaveCount(0)
})

test('重建库后旧 Cookie 对应用户不存在时返回登录，并可重新登录', async ({
  page,
  context,
  baseURL,
}) => {
  await context.addCookies([{ name: 'admin_token', value: 'existing-admin-token', url: baseURL! }])
  let loggedIn = false
  await page.route('**/api/user/info', (route) =>
    route.fulfill({
      json: loggedIn ? { code: 1, data: currentUser } : { code: 0, msg: '用户不存在', data: null },
    }),
  )
  await page.route('**/api/user/login', (route) => {
    loggedIn = true
    return route.fulfill({ json: { code: 1, data: { ...currentUser, token: 'new-user-token' } } })
  })
  await page.goto('/menu')
  await expect(page).toHaveURL('/login')
  expect((await context.cookies()).some((cookie) => cookie.name === 'user_token')).toBe(false)
  await page.getByLabel('账号', { exact: true }).fill('customer')
  await page.getByLabel('密码', { exact: true }).fill('password123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL('/menu')
  await expect(
    page.getByRole('link', { name: `你好，${currentUser.username}`, exact: true }),
  ).toBeVisible()
  await page.reload()
  await expect(
    page.getByRole('link', { name: `你好，${currentUser.username}`, exact: true }),
  ).toBeVisible()
  expect((await context.cookies()).find((cookie) => cookie.name === 'user_token')?.value).toBe(
    'new-user-token',
  )
  expect((await context.cookies()).find((cookie) => cookie.name === 'admin_token')?.value).toBe(
    'existing-admin-token',
  )
})
