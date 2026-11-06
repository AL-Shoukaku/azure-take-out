import { expect, test, type Page } from '@playwright/test'
import { mockAdminSession } from './helpers/admin.js'
import { mockCoupons } from './helpers/coupons.js'

test.beforeEach(async ({ page, context, baseURL }) => {
  await mockAdminSession(page, context, baseURL!)
})

async function select(page: Page, label: string, option: string) {
  await page
    .locator('.el-select')
    .filter({ has: page.getByRole('combobox', { name: label, exact: true }) })
    .click()
  await page
    .getByRole('listbox', { name: label, exact: true })
    .getByRole('option', { name: option, exact: true })
    .click()
}
function row(page: Page, name: string) {
  return page.getByRole('row').filter({ has: page.getByRole('cell', { name, exact: true }) })
}
async function fillDate(page: Page, label: string, value: string) {
  const input = page.getByRole('combobox', { name: label, exact: true })
  await input.fill(value)
  await input.press('Enter')
  await input.blur()
}
async function fillCreateForm(page: Page) {
  await page.getByLabel('优惠券名称:', { exact: true }).fill('周末优惠')
  await page.getByRole('spinbutton', { name: '总库存', exact: true }).fill('100')
  await page.getByRole('spinbutton', { name: '抵扣金额', exact: true }).fill('20.50')
  await page.getByRole('spinbutton', { name: '有效期', exact: true }).fill('7')
  await fillDate(page, '活动开始时间', '2099-01-01 10:00')
  await fillDate(page, '活动结束时间', '2099-01-10 10:00')
}

test('优惠券入口、四条件查询保留零值、分页、重置和空结果', async ({ page }) => {
  const api = await mockCoupons(page)
  await page.goto('/setmeal')
  await page.getByRole('menuitem', { name: '优惠券管理', exact: true }).click()
  await expect(page).toHaveURL('/coupon')
  await expect(page).toHaveTitle('优惠券管理 · 碧蓝外卖管理端')
  await expect(row(page, '优惠券1')).toContainText('80')
  await expect(page.getByText('共 25 条', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '下一页' }).click()
  await expect(row(page, '优惠券11')).toBeVisible()
  await page.getByLabel('优惠券名称查询', { exact: true }).fill('优惠券1')
  await select(page, '优惠券类型查询', '代金券')
  await select(page, '起售状态查询', '停售')
  await select(page, '活动状态查询', '未开始')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect
    .poll(() => api.queries.at(-1))
    .toEqual({ page: 1, pageSize: 10, name: '优惠券1', type: 1, status: 0, activityStatus: 0 })
  await expect(page.getByText('共 3 条', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(page.getByText('共 25 条', { exact: true })).toBeVisible()
  await expect.poll(() => api.queries.at(-1)).toEqual({ page: 1, pageSize: 10 })
  await page.screenshot({ path: test.info().outputPath('coupon-list.png'), fullPage: true })
  await page.getByLabel('优惠券名称查询', { exact: true }).fill('不存在')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByText('暂无优惠券', { exact: true })).toBeVisible()
})

test('详情读取完整信息、加载失败重试及重新打开不残留旧数据', async ({ page }) => {
  const api = await mockCoupons(page)
  await page.goto('/coupon')
  api.failures.detail = '详情暂不可用'
  await row(page, '优惠券1').getByRole('button', { name: '详情', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '优惠券详情', exact: true })
  await expect(dialog.getByRole('alert')).toContainText('详情暂不可用')
  api.failures.detail = ''
  await dialog.getByRole('button', { name: '重试详情', exact: true }).click()
  await expect(dialog).toContainText('优惠券1')
  await expect(dialog).toContainText('￥20.50')
  await expect(dialog).toContainText('￥9.90')
  await expect(dialog).toContainText('7 天')
  await expect(dialog).toContainText('2026-11-01 09:00')
  await expect(dialog.locator('.el-descriptions__label')).toHaveCount(14)
  await page.screenshot({ path: test.info().outputPath('coupon-detail.png'), fullPage: true })
  await dialog.getByRole('button', { name: '关闭', exact: true }).click()
  await row(page, '优惠券2').getByRole('button', { name: '详情', exact: true }).click()
  await expect(dialog).toContainText('优惠券2')
  await expect(dialog).toContainText('满减券')
  await expect(dialog).not.toContainText('优惠券1')
})

test('起停售支持确认取消、后端错误并按正确参数切换', async ({ page }) => {
  const api = await mockCoupons(page)
  await page.goto('/coupon')
  const coupon = row(page, '优惠券1')
  await coupon.getByRole('button', { name: '起售', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '取消', exact: true }).click()
  expect(api.writes).toHaveLength(0)
  api.failures.status = '状态修改失败'
  await coupon.getByRole('button', { name: '起售', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确定', exact: true }).click()
  await expect(page.getByRole('alert').filter({ hasText: '状态修改失败' })).toBeVisible()
  await expect(coupon.getByRole('button', { name: '起售', exact: true })).toBeEnabled()
  api.failures.status = ''
  await coupon.getByRole('button', { name: '起售', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确定', exact: true }).click()
  await expect(coupon.getByRole('button', { name: '停售', exact: true })).toBeVisible()
  expect(api.writes.at(-1)).toMatchObject({
    method: 'PUT',
    path: '/api/coupon/status',
    params: { couponId: '1', status: '1' },
    body: null,
  })
  await coupon.getByRole('button', { name: '停售', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确定', exact: true }).click()
  await expect(coupon.getByRole('button', { name: '起售', exact: true })).toBeVisible()
  expect(api.writes.at(-1)?.params).toEqual({ couponId: '1', status: '0' })
})

test('新建代金券校验必填、时间先后，金额和时间按后端格式提交', async ({ page }) => {
  const api = await mockCoupons(page)
  await page.goto('/coupon')
  await page.getByRole('button', { name: '+ 新建优惠券', exact: true }).click()
  await expect(page.getByRole('menuitem', { name: '优惠券管理', exact: true })).toHaveClass(
    /is-active/,
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('请输入优惠券名称', { exact: true })).toBeVisible()
  await expect(page.getByText('总库存必须为大于 0 的整数', { exact: true })).toBeVisible()
  expect(api.writes).toHaveLength(0)
  await fillCreateForm(page)
  await page.getByRole('spinbutton', { name: '购买价格', exact: true }).fill('9.90')
  await fillDate(page, '活动结束时间', '2099-01-01 10:00')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('活动结束时间必须晚于开始时间', { exact: true })).toBeVisible()
  expect(api.writes).toHaveLength(0)
  await fillDate(page, '活动结束时间', '2099-01-10 10:00')
  await page.screenshot({ path: test.info().outputPath('coupon-create.png'), fullPage: true })
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page).toHaveURL('/coupon')
  expect(api.writes.at(-1)).toMatchObject({
    method: 'POST',
    path: '/api/coupon/publish',
    body: {
      name: '周末优惠',
      type: 1,
      totalStock: 100,
      discountAmount: 20.5,
      price: 9.9,
      thresholdAmount: 0,
      validTime: 7,
      startTime: '2099-01-01 10:00',
      endTime: '2099-01-10 10:00',
    },
  })
  await expect(row(page, '周末优惠')).toContainText('停售')
})

test('新建满减券保留门槛金额，切换类型后不提交代金券购买价格', async ({ page }) => {
  const api = await mockCoupons(page)
  await page.goto('/coupon/add')
  await fillCreateForm(page)
  await page.getByRole('spinbutton', { name: '购买价格', exact: true }).fill('9.90')
  await select(page, '优惠券类型', '满减券')
  await expect(page.getByRole('spinbutton', { name: '购买价格', exact: true })).toBeDisabled()
  await expect(page.getByRole('spinbutton', { name: '购买价格', exact: true })).toHaveValue('0.00')
  await expect(page.getByRole('spinbutton', { name: '门槛金额', exact: true })).toBeEnabled()
  await page.getByRole('spinbutton', { name: '门槛金额', exact: true }).fill('60')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page).toHaveURL('/coupon')
  expect(api.writes.at(-1)).toMatchObject({
    path: '/api/coupon/publish',
    body: { type: 2, thresholdAmount: 60, price: 0 },
  })
})

test('修改完整回显，只提交允许修改的名称和购买价格，失败保留输入', async ({ page }) => {
  const api = await mockCoupons(page)
  await page.goto('/coupon')
  await row(page, '优惠券1').getByRole('button', { name: '修改', exact: true }).click()
  await expect(page.getByLabel('优惠券名称:', { exact: true })).toHaveValue('优惠券1')
  await expect(page.getByRole('spinbutton', { name: '总库存', exact: true })).toHaveValue('100')
  await expect(page.getByRole('spinbutton', { name: '总库存', exact: true })).toBeDisabled()
  await expect(page.getByRole('combobox', { name: '优惠券类型', exact: true })).toBeDisabled()
  await expect(page.getByRole('combobox', { name: '活动开始时间', exact: true })).toHaveValue(
    '2099-01-01 10:00',
  )
  await expect(page.getByRole('combobox', { name: '活动开始时间', exact: true })).toBeDisabled()
  await page.getByLabel('优惠券名称:', { exact: true }).fill('改名优惠券')
  await page.getByRole('spinbutton', { name: '购买价格', exact: true }).fill('8.80')
  api.failures.save = '保存失败'
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('alert').filter({ hasText: '保存失败' })).toBeVisible()
  await expect(page.getByLabel('优惠券名称:', { exact: true })).toHaveValue('改名优惠券')
  api.failures.save = ''
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page).toHaveURL('/coupon')
  expect(api.writes.at(-1)).toMatchObject({
    method: 'PUT',
    path: '/api/coupon/update',
    body: { id: 1, name: '改名优惠券', price: 8.8 },
  })
  expect(Object.keys(api.writes.at(-1)!.body as object).sort()).toEqual(['id', 'name', 'price'])
  await expect(row(page, '改名优惠券')).toBeVisible()
})

test('修改满减券、路由切换回新建时重置表单', async ({ page }) => {
  const api = await mockCoupons(page)
  await page.goto('/coupon/add?id=2')
  await expect(page.getByLabel('优惠券名称:', { exact: true })).toHaveValue('优惠券2')
  await expect(page.getByRole('spinbutton', { name: '门槛金额', exact: true })).toHaveValue('60.00')
  await page.getByLabel('优惠券名称:', { exact: true }).fill('新满减券')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page).toHaveURL('/coupon')
  expect(api.writes.at(-1)?.body).toEqual({ id: 2, name: '新满减券' })
  await page.getByRole('button', { name: '+ 新建优惠券', exact: true }).click()
  await expect(page.getByLabel('优惠券名称:', { exact: true })).toBeEmpty()
  await expect(page.getByRole('spinbutton', { name: '总库存', exact: true })).toBeEnabled()
})

test('列表、修改详情失败可重试，不存在的优惠券不能保存', async ({ page }) => {
  const api = await mockCoupons(page)
  api.failures.list = '列表暂不可用'
  await page.goto('/coupon')
  await expect(page.getByRole('alert')).toContainText('列表暂不可用')
  api.failures.list = ''
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(row(page, '优惠券1')).toBeVisible()
  api.failures.detail = '详情暂不可用'
  await page.goto('/coupon/add?id=1')
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  api.failures.detail = ''
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.getByLabel('优惠券名称:', { exact: true })).toHaveValue('优惠券1')
  await page.goto('/coupon/add?id=999')
  await expect(page.getByRole('alert')).toContainText('优惠券不存在')
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  expect(api.writes).toHaveLength(0)
})

test('保存期间阻止重复提交和导航打断', async ({ page }) => {
  const api = await mockCoupons(page)
  api.delaySave = true
  await page.goto('/coupon/add?id=1')
  await expect(page.getByLabel('优惠券名称:', { exact: true })).toHaveValue('优惠券1')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '取消', exact: true })).toBeDisabled()
  await page.getByRole('menuitem', { name: '分类管理', exact: true }).click()
  await expect(page).toHaveURL('/coupon/add?id=1')
  expect(api.writes).toHaveLength(1)
  api.releaseSave()
  await expect(page).toHaveURL('/coupon')
})
