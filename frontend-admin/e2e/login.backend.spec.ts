import { expect, test } from '@playwright/test'

const username = process.env.E2E_ADMIN_USERNAME
const password = process.env.E2E_ADMIN_PASSWORD

test('连接真实后端登录，刷新后仍显示完成登录', async ({ page, context }) => {
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
  await expect(page.getByText('完成登录', { exact: true })).toBeVisible()
  expect((await context.cookies()).some((cookie) => cookie.name === 'token')).toBe(true)

  await page.reload()
  await expect(page.getByText('完成登录', { exact: true })).toBeVisible()
})
