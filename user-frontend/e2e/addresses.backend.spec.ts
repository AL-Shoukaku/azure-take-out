import { expect, test } from '@playwright/test'

test('真实后端：独立账号新增、编辑、设置默认、刷新和删除地址', async ({ page, request }) => {
  // eslint-disable-next-line playwright/no-skipped-test -- 显式开启后仅操作独立测试账号的地址
  test.skip(process.env.E2E_BACKEND !== '1', '设置 E2E_BACKEND=1，启用地址真实后端测试')
  const account = `E2eAddress${Date.now()}`
  expect(
    await (
      await request.post('/api/user/register', {
        data: { account, password: 'TestPass123' },
      })
    ).json(),
  ).toMatchObject({ code: 1 })
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill(account)
  await page.getByLabel('密码', { exact: true }).fill('TestPass123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL('/menu')
  await page.getByRole('navigation').getByRole('link', { name: '收货地址' }).click()
  await expect(page.getByText('还没有收货地址', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '新增地址', exact: true }).click()
  const dialog = page.getByRole('dialog')
  for (const [label, value] of [
    ['收货人', '地址联调用户'],
    ['手机号', '13800000000'],
    ['省份', '浙江省'],
    ['城市', '杭州市'],
    ['区县', '西湖区'],
    ['详细地址', '联调路 18 号 201 室'],
  ])
    await dialog.getByLabel(label!, { exact: true }).fill(value!)
  await dialog.getByRole('radiogroup').getByText('学校', { exact: true }).click()
  const created = page.waitForResponse(
    (r) => r.url().endsWith('/api/addressBook') && r.request().method() === 'POST',
  )
  await dialog.getByRole('button', { name: '保存地址' }).click()
  expect(await (await created).json()).toMatchObject({ code: 1, data: null })
  await expect(dialog).toBeHidden()
  const card = page.getByRole('article').first()
  await expect(card).toContainText('联调路 18 号 201 室')
  await expect(card.getByRole('button', { name: '配送到这里' })).toBeVisible()
  await card.getByRole('button', { name: '设为默认', exact: true }).click()
  await expect(card.getByRole('button', { name: '已选中' })).toBeVisible()
  await expect(card.getByText('默认', { exact: true })).toBeVisible()
  await card.getByRole('button', { name: '编辑', exact: true }).click()
  await expect(dialog.getByLabel('收货人', { exact: true })).toHaveValue('地址联调用户')
  await expect(dialog.getByRole('radio', { name: '学校', exact: true })).toBeChecked()
  await dialog.getByLabel('详细地址', { exact: true }).fill('联调路 18 号 202 室')
  await dialog.getByRole('button', { name: '保存地址' }).click()
  await expect(dialog).toBeHidden()
  await page.reload()
  await expect(card).toContainText('联调路 18 号 202 室')
  await expect(card.getByText('默认', { exact: true })).toBeVisible()
  await expect(card.getByRole('button', { name: '已选中' })).toBeVisible()
  await card.getByRole('button', { name: '删除', exact: true }).click()
  await page.getByRole('button', { name: '确认删除', exact: true }).click()
  await expect(page.getByText('还没有收货地址', { exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('article')).toHaveCount(0)
})
