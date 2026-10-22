import { expect, test } from '@playwright/test'

test('真实后端：注册、重复账号提示、密码错误提示、登录及凭证验证', async ({ page, request }) => {
  // eslint-disable-next-line playwright/no-skipped-test -- 显式开启后才向本地数据库创建测试用户
  test.skip(process.env.E2E_BACKEND !== '1', '设置 E2E_BACKEND=1，启用真实后端测试（创建测试用户）')
  const account = `E2eUser${Date.now()}`
  const password = 'TestPass123'

  await page.goto('/login')
  await page.getByRole('tab', { name: '注册', exact: true }).click()
  await page.getByLabel('账号', { exact: true }).fill(account)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByLabel('确认密码', { exact: true }).fill(password)
  const registration = page.waitForResponse('**/api/user/register')
  await page.getByRole('button', { name: '注册', exact: true }).click()
  expect(await (await registration).json()).toMatchObject({ code: 1, data: null })
  await expect(page.getByText('注册成功，请登录')).toBeVisible()

  await page.getByRole('tab', { name: '注册', exact: true }).click()
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByLabel('确认密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.getByText('账号已存在', { exact: true })).toBeVisible()

  await page.getByRole('tab', { name: '登录', exact: true }).click()
  await page.getByLabel('密码', { exact: true }).fill('WrongPass123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByText('密码错误', { exact: true })).toBeVisible()

  await page.getByLabel('密码', { exact: true }).fill(password)
  const login = page.waitForResponse('**/api/user/login')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  const result = await (await login).json()
  expect(result).toMatchObject({
    code: 1,
    data: { id: expect.any(Number), username: expect.any(String), token: expect.any(String) },
  })
  await expect(page).toHaveURL('/menu')
  await expect(page.getByRole('main')).toContainText('热腾腾的烟火气，是日常里最温柔的陪伴。')

  const info = await request.get('/api/user/info', {
    headers: { authentication: result.data.token },
  })
  expect(info.status()).toBe(200)
  expect(await info.json()).toMatchObject({ code: 1, data: { username: result.data.username } })
  await page.reload()
  await expect(page.getByRole('main')).toContainText('热腾腾的烟火气，是日常里最温柔的陪伴。')
})
