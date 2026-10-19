import { expect, test } from '@playwright/test'
import { mockLayoutAPIs } from './helpers/admin.js'

const employee = { id: 1, userName: 'admin', name: '管理员', token: 'e2e-login-token' }

test.beforeEach(async ({ page }) => {
  await mockLayoutAPIs(page)
})

test('未登录访问首页和工作台，都会进入登录页', async ({ page }) => {
  await page.goto('/')
  await expect(page).toHaveURL('/login')
  await expect(page.getByPlaceholder('账号')).toBeEmpty()
  await expect(page.getByPlaceholder('密码')).toHaveAttribute('type', 'password')
  await expect(page.getByRole('img', { name: '晴空食堂' })).toBeVisible()
  await page.goto('/dashboard')
  await expect(page).toHaveURL('/login')
})

test('账号和密码校验不通过时不发送请求', async ({ page }) => {
  const requests: string[] = []
  page.on('request', (request) => {
    if (request.url().includes('/employee/login')) requests.push(request.url())
  })
  await page.goto('/login')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByText('请输入用户名', { exact: true })).toBeVisible()
  await expect(page.getByText('请输入密码', { exact: true })).toBeVisible()
  await page.getByPlaceholder('账号').fill('   ')
  await page.getByPlaceholder('密码').fill('12345')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByText('请输入用户名', { exact: true })).toBeVisible()
  await expect(page.getByText('密码必须在6位以上', { exact: true })).toBeVisible()
  expect(requests).toHaveLength(0)
})

test('提交期间显示登录中，禁止重复提交，成功后保持登录', async ({ page, context }) => {
  let finishLogin!: () => void
  const responseReady = new Promise<void>((resolve) => {
    finishLogin = resolve
  })
  const submissions: unknown[] = []
  await page.route('**/api/employee/login', async (route) => {
    submissions.push(route.request().postDataJSON())
    await responseReady
    await route.fulfill({ json: { code: 1, msg: null, data: employee } })
  })

  await page.goto('/login')
  await page.getByPlaceholder('账号').fill('  admin  ')
  await page.getByPlaceholder('密码').fill('123456')
  await page.getByPlaceholder('密码').press('Enter')
  const loadingButton = page.getByRole('button', { name: '登录中...' })
  await expect(loadingButton).toBeVisible()
  await expect(loadingButton).toBeDisabled()
  await page.getByPlaceholder('密码').press('Enter')
  expect(submissions).toEqual([{ username: 'admin', password: '123456' }])

  finishLogin()
  await expect(page).toHaveURL('/dashboard')
  await expect(page.getByRole('region', { name: '今日数据', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: employee.name, exact: true })).toBeVisible()
  const token = (await context.cookies()).find((cookie) => cookie.name === 'token')
  expect(token).toMatchObject({ value: employee.token, path: '/', sameSite: 'Lax' })
  await page.reload()
  await expect(page).toHaveURL('/dashboard')
  await expect(page.getByRole('region', { name: '今日数据', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: employee.name, exact: true })).toBeVisible()
  await page.goto('/')
  await expect(page).toHaveURL('/dashboard')
  await page.goto('/login')
  await expect(page).toHaveURL('/dashboard')
  await context.clearCookies()
  await page.reload()
  await expect(page).toHaveURL('/login')
})

for (const message of ['账号不存在', '密码错误', '账号被锁定']) {
  test(`显示后端报错“${message}”，并允许重试`, async ({ page, context }) => {
    await page.route('**/api/employee/login', (route) =>
      route.fulfill({ json: { code: 0, msg: message, data: null } }),
    )
    await page.goto('/login')
    await page.getByPlaceholder('账号').fill('admin')
    await page.getByPlaceholder('密码').fill('123456')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText(message)
    await expect(page).toHaveURL('/login')
    await expect(page.getByRole('button', { name: '登录', exact: true })).toBeEnabled()
    expect((await context.cookies()).some((cookie) => cookie.name === 'token')).toBe(false)

    await page.route('**/api/employee/login', (route) =>
      route.fulfill({ json: { code: 1, msg: null, data: employee } }),
    )
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page).toHaveURL('/dashboard')
  })
}

test('网络失败后恢复登录按钮', async ({ page }) => {
  await page.route('**/api/employee/login', (route) => route.abort('failed'))
  await page.goto('/login')
  await page.getByPlaceholder('账号').fill('admin')
  await page.getByPlaceholder('密码').fill('123456')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('网络异常，请检查网络连接')
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeEnabled()
})

test('窄屏下表单完整可用，没有横向溢出', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/login')
  await expect(page.getByPlaceholder('账号')).toBeVisible()
  await expect(page.getByPlaceholder('密码')).toBeVisible()
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
})
