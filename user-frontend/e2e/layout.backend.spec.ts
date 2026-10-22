import { expect, test } from '@playwright/test'

const account = process.env.E2E_USER_ACCOUNT
const password = process.env.E2E_USER_PASSWORD

test('真实后端：登录后显示昵称，导航切换和刷新后恢复用户信息', async ({ page }) => {
  // eslint-disable-next-line playwright/no-skipped-test -- 通过环境变量选择真实账号，只读取资料
  test.skip(!account || !password, '设置 E2E_USER_ACCOUNT 和 E2E_USER_PASSWORD，启用只读后端联调')
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill(account!)
  await page.getByLabel('密码', { exact: true }).fill(password!)
  const response = page.waitForResponse('**/api/user/login')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  const result = await (await response).json()
  expect(result.code).toBe(1)
  await expect(page).toHaveURL('/menu')
  await expect(
    page.getByRole('link', { name: `你好，${result.data.username}`, exact: true }),
  ).toBeVisible()
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '我的订单', exact: true })
    .click()
  await expect(page.getByRole('main')).toContainText('每一餐，都是日常的记录。')
  const info = page.waitForResponse('**/api/user/info')
  await page.reload()
  expect(await (await info).json()).toMatchObject({
    code: 1,
    data: { username: result.data.username },
  })
  await expect(
    page.getByRole('link', { name: `你好，${result.data.username}`, exact: true }),
  ).toBeVisible()
  await page.getByRole('link', { name: '碧蓝外卖，返回在线点餐' }).click()
  await expect(page).toHaveURL('/menu')
})
