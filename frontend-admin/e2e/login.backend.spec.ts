import { expect, test } from '@playwright/test'

const username = process.env.E2E_ADMIN_USERNAME
const password = process.env.E2E_ADMIN_PASSWORD

test('连接真实后端登录，读取员工与分类列表，刷新后保留会话', async ({ page, context }) => {
  // eslint-disable-next-line playwright/no-skipped-test -- 真实后端测试需要用户通过环境变量提供账号
  test.skip(
    !username || !password,
    '设置 E2E_ADMIN_USERNAME 和 E2E_ADMIN_PASSWORD 后执行真实后端测试',
  )

  await page.goto('/login')
  await page.getByPlaceholder('账号').fill(username!)
  await page.getByPlaceholder('密码').fill(password!)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL('/dashboard')
  await expect(page.getByText('当前为工作台页面', { exact: true })).toBeVisible()
  await expect(page.getByRole('status', { name: '店铺营业状态' })).toHaveText(/营业中|打烊中/)
  expect((await context.cookies()).some((cookie) => cookie.name === 'token')).toBe(true)

  await page.reload()
  await expect(page.getByText('当前为工作台页面', { exact: true })).toBeVisible()

  const employeeResponse = page.waitForResponse(
    (response) => new URL(response.url()).pathname === '/api/employee/page',
  )
  await page.getByRole('menuitem', { name: '员工管理', exact: true }).click()
  expect((await (await employeeResponse).json()).code).toBe(1)
  await expect(page.getByRole('region', { name: '员工管理', exact: true })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: '员工姓名', exact: true })).toBeVisible()

  const categoryResponse = page.waitForResponse(
    (response) => new URL(response.url()).pathname === '/api/category/page',
  )
  await page.getByRole('menuitem', { name: '分类管理', exact: true }).click()
  expect((await (await categoryResponse).json()).code).toBe(1)
  await expect(page.getByRole('columnheader', { name: '分类名称', exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('region', { name: '分类管理', exact: true })).toBeVisible()
})
