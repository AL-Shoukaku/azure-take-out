import { expect, test } from '@playwright/test'
import { readFile } from 'node:fs/promises'

test('真实后端：独立账号编辑资料、上传头像、刷新回显、修改密码与退出登录', async ({
  page,
  request,
}) => {
  // eslint-disable-next-line playwright/no-skipped-test -- 显式开启后创建独立测试用户并修改其资料及密码
  test.skip(process.env.E2E_BACKEND !== '1', '设置 E2E_BACKEND=1，启用个人资料真实后端测试')
  const account = `E2eProfile${Date.now()}`
  const oldPassword = 'TestPass123'
  const newPassword = 'NewPass456'
  const registration = await request.post('/api/user/register', {
    data: { account, password: oldPassword },
  })
  expect(await registration.json()).toMatchObject({ code: 1 })

  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill(account)
  await page.getByLabel('密码', { exact: true }).fill(oldPassword)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL('/menu')
  const infoResponse = page.waitForResponse('**/api/user/info')
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '我的', exact: true })
    .click()
  const info = await (await infoResponse).json()
  expect(info).toMatchObject({ code: 1, data: { id: expect.any(Number), account } })
  await expect(page.getByRole('region', { name: '账号安全' })).toContainText(account)
  await page.getByRole('button', { name: '编辑资料', exact: true }).click()
  const editor = page.getByRole('dialog', { name: '编辑资料', exact: true })
  const upload = page.waitForResponse('**/api/common/upload')
  await editor
    .getByLabel('选择头像文件')
    .setInputFiles({
      name: 'avatar.png',
      mimeType: 'image/png',
      buffer: await readFile(new URL('../public/favicon.png', import.meta.url)),
    })
  const uploaded = await (await upload).json()
  expect(uploaded).toMatchObject({ code: 1, data: expect.stringContaining('/images/user/') })
  await expect(editor.getByRole('img', { name: '头像预览' })).toBeVisible()
  await editor.getByLabel('昵称', { exact: true }).fill('资料联调用户')
  await editor.getByLabel('真实姓名', { exact: true }).fill('测试姓名')
  await editor.getByRole('combobox', { name: '性别', exact: true }).press('ArrowDown')
  await page.getByRole('option', { name: '女', exact: true }).click()
  await editor.getByLabel('手机号', { exact: true }).fill('13800000000')
  await editor.getByLabel('身份证号', { exact: true }).fill('110101199001010011')
  const saving = page.waitForResponse('**/api/user/update')
  await editor.getByRole('button', { name: '保存', exact: true }).click()
  expect(await (await saving).json()).toMatchObject({ code: 1 })
  await expect(editor).toBeHidden()
  await expect(page.getByRole('link', { name: '你好，资料联调用户', exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('region', { name: '基本信息' })).toContainText('测试姓名')
  await expect(page.getByRole('region', { name: '基本信息' })).toContainText('女')
  await expect(page.getByRole('region', { name: '基本信息' })).toContainText('13800000000')
  await expect(page.getByRole('img', { name: '个人头像', exact: true })).toHaveAttribute(
    'src',
    uploaded.data,
  )

  await page.getByRole('button', { name: '修改密码', exact: true }).click()
  const passwordDialog = page.getByRole('dialog', { name: '修改密码', exact: true })
  await passwordDialog.getByLabel('旧密码', { exact: true }).fill('WrongPass123')
  await passwordDialog.getByLabel('新密码', { exact: true }).fill(newPassword)
  await passwordDialog.getByLabel('确认新密码', { exact: true }).fill(newPassword)
  await passwordDialog.getByRole('button', { name: '确认修改', exact: true }).click()
  await expect(page.getByText('原密码错误', { exact: true })).toBeVisible()
  await passwordDialog.getByLabel('旧密码', { exact: true }).fill(oldPassword)
  await passwordDialog.getByRole('button', { name: '确认修改', exact: true }).click()
  await expect(passwordDialog).toBeHidden()
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page).toHaveURL('/login')
  await page.getByLabel('账号', { exact: true }).fill(account)
  await page.getByLabel('密码', { exact: true }).fill(oldPassword)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByText('密码错误', { exact: true })).toBeVisible()
  await page.getByLabel('密码', { exact: true }).fill(newPassword)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL('/menu')
  await expect(page.getByRole('link', { name: '你好，资料联调用户', exact: true })).toBeVisible()
})
