import { expect, test } from '@playwright/test'
import { mockUserInfo } from './helpers/session'

test('未登录进入登录页，桌面分栏为 6:4，背景正常加载且 logo 居中', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/')
  await expect(page).toHaveURL('/login')
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
  await expect(page.getByAltText('碧蓝外卖')).toBeVisible()
  const background = await page.locator('.login-background').boundingBox()
  const panel = await page.locator('.login-panel').boundingBox()
  expect(background?.width).toBe(864)
  expect(panel?.width).toBe(576)
  expect(background?.height).toBe(900)
  await expect(page.locator('.login-background img')).toBeVisible()
  await expect(page.locator('.login-background img')).toHaveJSProperty('naturalWidth', 1402)
  const logo = await page.getByAltText('碧蓝外卖').boundingBox()
  const content = await page.locator('.login-content').boundingBox()
  expect(Math.abs(logo!.x + logo!.width / 2 - (content!.x + content!.width / 2))).toBeLessThan(1)
})

test('校验账号和密码，不请求接口', async ({ page }) => {
  let submissions = 0
  await page.route('**/api/user/login', async (route) => {
    submissions++
    await route.fulfill({ json: { code: 0, msg: '不应提交空表单', data: null } })
  })
  await page.goto('/login')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByText('请输入账号', { exact: true })).toBeVisible()
  await expect(page.getByText('请输入密码', { exact: true })).toBeVisible()
  expect(submissions).toBe(0)
})

test('登录后写入 Cookie，进入点餐页，刷新仍保留登录', async ({ page, context }) => {
  await mockUserInfo(page)
  await page.route('**/api/user/login', async (route) => {
    expect(route.request().postDataJSON()).toEqual({ account: 'customer', password: 'abc123' })
    await route.fulfill({
      json: {
        code: 1,
        msg: null,
        data: { id: 7, username: '用户7', avatar: null, token: 'e2e-user-token' },
      },
    })
  })
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill(' customer ')
  await page.getByLabel('密码', { exact: true }).fill('abc123')
  await page.getByLabel('密码', { exact: true }).press('Enter')
  await expect(page).toHaveURL('/menu')
  await expect(page.getByRole('main')).toContainText('热腾腾的烟火气，是日常里最温柔的陪伴。')
  const token = (await context.cookies()).find((cookie) => cookie.name === 'token')
  expect(token).toMatchObject({ value: 'e2e-user-token', path: '/', sameSite: 'Lax' })
  await page.reload()
  await expect(page.getByRole('main')).toContainText('热腾腾的烟火气，是日常里最温柔的陪伴。')
  await page.goto('/login')
  await expect(page).toHaveURL('/menu')
  await context.clearCookies()
  await page.reload()
  await expect(page).toHaveURL('/login')
})

test('注册时校验确认密码，成功后回到登录并保留账号', async ({ page, context }) => {
  let submissions = 0
  await page.route('**/api/user/register', async (route) => {
    submissions++
    expect(route.request().postDataJSON()).toEqual({ account: 'newcustomer', password: 'abc123' })
    await route.fulfill({ json: { code: 1, msg: null, data: null } })
  })
  await page.goto('/login')
  await page.getByRole('tab', { name: '注册', exact: true }).click()
  await expect(page.getByRole('heading', { name: '欢迎加入碧蓝外卖' })).toBeVisible()
  await page.getByLabel('账号', { exact: true }).fill('newcustomer')
  await page.getByLabel('密码', { exact: true }).fill('abc123')
  await page.getByLabel('确认密码', { exact: true }).fill('abc456')
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.getByText('两次输入的密码不一致')).toBeVisible()
  expect(submissions).toBe(0)
  await page.getByLabel('确认密码', { exact: true }).fill('abc123')
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.getByText('注册成功，请登录')).toBeVisible()
  await expect(page.getByRole('tab', { name: '登录', exact: true })).toHaveAttribute(
    'aria-selected',
    'true',
  )
  await expect(page.getByLabel('账号', { exact: true })).toHaveValue('newcustomer')
  await expect(page.getByLabel('密码', { exact: true })).toHaveValue('')
  expect((await context.cookies()).some((cookie) => cookie.name === 'token')).toBe(false)
})

test('登录错误和注册重复账号错误显示后可重试', async ({ page }) => {
  await page.route('**/api/user/login', (route) =>
    route.fulfill({ json: { code: 0, msg: '密码错误', data: null } }),
  )
  await page.route('**/api/user/register', (route) =>
    route.fulfill({ json: { code: 0, msg: '账号已存在', data: null } }),
  )
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill('customer')
  await page.getByLabel('密码', { exact: true }).fill('wrong123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByText('密码错误', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeEnabled()
  await page.getByRole('tab', { name: '注册', exact: true }).click()
  await expect(page.getByLabel('密码', { exact: true })).toHaveValue('')
  await page.getByLabel('密码', { exact: true }).fill('abc123')
  await page.getByLabel('确认密码', { exact: true }).fill('abc123')
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.getByText('账号已存在', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '注册', exact: true })).toBeEnabled()
})

test('小屏幕可完成注册表单，无横向滚动', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 667 })
  await page.goto('/login')
  await page.getByRole('tab', { name: '注册', exact: true }).click()
  await expect(page.getByLabel('确认密码', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '注册', exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
})

test('登录和注册都校验账号格式及密码格式', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill('1a')
  await page.getByLabel('密码', { exact: true }).fill('abc!')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByText('账号长度为 3–32 位，只能包含字母和数字，且以字母开头')).toBeVisible()
  await expect(page.getByText('密码至少 6 位，且只能包含字母和数字')).toBeVisible()
  await page.getByRole('tab', { name: '注册', exact: true }).click()
  await page.getByLabel('密码', { exact: true }).fill('abc!')
  await page.getByLabel('确认密码', { exact: true }).fill('abc!')
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.getByText('账号长度为 3–32 位，只能包含字母和数字，且以字母开头')).toBeVisible()
  await expect(page.getByText('密码至少 6 位，且只能包含字母和数字')).toBeVisible()
})
