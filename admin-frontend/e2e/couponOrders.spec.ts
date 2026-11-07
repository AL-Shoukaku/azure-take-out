import { expect, test, type Page } from '@playwright/test'
import { mockAdminSession } from './helpers/admin.js'
import { mockCouponOrders } from './helpers/couponOrders.js'
import { mockOrders } from './helpers/orders.js'

test.beforeEach(async ({ page, context, baseURL }) => {
  await mockAdminSession(page, context, baseURL!)
})

function row(page: Page, number: string) {
  return page
    .getByRole('row')
    .filter({ has: page.getByRole('cell', { name: number, exact: true }) })
}
const orderMenu = (page: Page) =>
  page
    .getByRole('navigation', { name: '管理菜单' })
    .getByRole('menuitem', { name: '订单管理', exact: true })
async function chooseRefundReason(page: Page, value: string) {
  const dialog = page.getByRole('dialog', { name: '退款原因', exact: true })
  await dialog.locator('.el-select').click()
  await page.getByRole('option', { name: value, exact: true }).click()
  return dialog
}

test('订单管理先显示外卖与优惠券两个居中的入口，进入子页面后菜单保持选中', async ({ page }) => {
  await mockOrders(page)
  await mockCouponOrders(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/dashboard')
  await orderMenu(page).click()
  await expect(page).toHaveURL('/order')
  await expect(page).toHaveTitle('订单管理 · 碧蓝外卖管理端')

  await page.goto('/order')
  const takeout = page.getByRole('link', { name: /外卖订单/ })
  const coupon = page.getByRole('link', { name: /优惠券订单/ })
  const takeoutBox = (await takeout.boundingBox())!
  const couponBox = (await coupon.boundingBox())!
  const content = (await page.locator('.admin-content').boundingBox())!
  expect(takeoutBox.x + takeoutBox.width).toBeLessThan(couponBox.x)
  expect(takeoutBox.y).toBe(couponBox.y)
  expect(takeoutBox.height).toBeGreaterThanOrEqual(400)
  expect(
    Math.abs((takeoutBox.x + couponBox.x + couponBox.width) / 2 - content.x - content.width / 2),
  ).toBeLessThan(1)
  expect(
    Math.abs(takeoutBox.y + takeoutBox.height / 2 - content.y - content.height / 2),
  ).toBeLessThan(1)

  await takeout.click()
  await expect(page).toHaveURL('/order/takeout')
  await expect(page).toHaveTitle('外卖订单 · 碧蓝外卖管理端')
  await expect(page.getByRole('tab', { name: '全部订单', exact: true })).toBeVisible()
  await expect(orderMenu(page)).toHaveClass(/is-active/)
  await page.goBack()
  await page.getByRole('link', { name: /优惠券订单/ }).click()
  await expect(page).toHaveURL('/order/coupon')
  await expect(page).toHaveTitle('优惠券订单 · 碧蓝外卖管理端')
  await expect(orderMenu(page)).toHaveClass(/is-active/)
})

test('两个订单页右上角的蓝色按钮与状态选项同一行，互相跳转', async ({ page }) => {
  await mockOrders(page)
  await mockCouponOrders(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  for (const [path, tablist, name, target] of [
    ['/order/takeout', '订单状态', '查看优惠券订单', '/order/coupon'],
    ['/order/coupon', '优惠券订单状态', '查看外卖订单', '/order/takeout'],
  ] as const) {
    await page.goto(path)
    const button = page.getByRole('button', { name, exact: true })
    await expect(button).toHaveCSS('background-color', 'rgb(37, 99, 235)')
    await expect(button).toHaveCSS('color', 'rgb(255, 255, 255)')
    const tabs = (await page.getByRole('tablist', { name: tablist, exact: true }).boundingBox())!
    const panel = (await page.getByRole('tabpanel').boundingBox())!
    const box = (await button.boundingBox())!
    expect(box.x).toBeGreaterThan(tabs.x + tabs.width)
    expect(Math.abs(box.y - tabs.y)).toBeLessThan(1)
    expect(Math.abs(box.x + box.width - panel.x - panel.width)).toBeLessThan(1)
    await button.click()
    await expect(page).toHaveURL(target)
  }
})

test('全部订单字段与操作，状态选项卡按状态查询并写入地址，刷新后保留', async ({ page }) => {
  const api = await mockCouponOrders(page)
  await page.goto('/order/coupon')
  await expect(page.getByRole('tab')).toHaveText([
    '全部订单',
    '待支付',
    '已支付',
    '已退款',
    '已关闭',
  ])
  await expect(page.getByRole('columnheader')).toHaveText([
    '订单号',
    '订单状态',
    '用户名',
    '优惠券名称',
    '优惠方式',
    '购买价格',
    '下单时间',
    '操作',
  ])
  await expect(row(page, 'coupon-order-0002')).toContainText('已支付')
  await expect(row(page, 'coupon-order-0002')).toContainText('可抵扣 ￥20.00')
  await expect(row(page, 'coupon-order-0002')).toContainText('￥9.90')
  await expect(row(page, 'coupon-order-0002').getByRole('button')).toHaveText(['退款', '查看'])
  for (const number of ['coupon-order-0001', 'coupon-order-0003', 'coupon-order-0004']) {
    await expect(row(page, number).getByRole('button')).toHaveText(['查看'])
  }
  expect(Object.fromEntries(api.queries.at(-1)!)).toEqual({ page: '1', pageSize: '10' })

  for (const [tab, status, headers] of [
    ['待支付', '0', ['订单号', '用户名', '优惠券名称', '购买价格', '下单时间', '支付截止时间']],
    [
      '已支付',
      '1',
      ['订单号', '用户名', '优惠券名称', '优惠方式', '购买价格', '下单时间', '支付时间'],
    ],
    [
      '已退款',
      '2',
      ['订单号', '用户名', '优惠券名称', '购买价格', '支付时间', '退款时间', '退款原因'],
    ],
    [
      '已关闭',
      '3',
      ['订单号', '用户名', '优惠券名称', '购买价格', '下单时间', '关闭时间', '关闭原因'],
    ],
  ] as const) {
    await page.getByRole('tab', { name: tab, exact: true }).click()
    await expect(page).toHaveURL(`/order/coupon?status=${status}`)
    await expect(page.getByRole('columnheader')).toHaveText([...headers, '操作'])
    await expect(page.getByRole('cell', { name: /^coupon-order-/ })).toHaveCount(6)
    expect(api.queries.at(-1)?.get('status')).toBe(status)
  }
  await page.reload()
  await expect(page.getByRole('tab', { name: '已关闭', exact: true })).toHaveAttribute(
    'aria-selected',
    'true',
  )
  await expect(row(page, 'coupon-order-0004')).toContainText('超时未支付')
  await page.getByRole('tab', { name: '全部订单', exact: true }).click()
  await expect(page).toHaveURL('/order/coupon')
})

test('订单号、用户名和下单时间组合查询，回到第一页，切换状态清空条件', async ({ page }) => {
  const api = await mockCouponOrders(page)
  await page.goto('/order/coupon')
  await page.getByRole('button', { name: '下一页' }).click()
  await expect.poll(() => api.queries.at(-1)?.get('page')).toBe('2')
  await page.getByPlaceholder('请填写订单号').fill('0012')
  await page.getByPlaceholder('请填写用户名').fill('用户12')
  const start = page.getByPlaceholder('开始日期'),
    end = page.getByPlaceholder('结束日期')
  await start.fill('2026-11-07')
  await start.press('Tab')
  await end.fill('2026-11-07')
  await end.press('Enter')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect
    .poll(() => Object.fromEntries(api.queries.at(-1)!))
    .toEqual({
      page: '1',
      pageSize: '10',
      number: '0012',
      username: '用户12',
      beginTime: '2026-11-07 00:00:00',
      endTime: '2026-11-07 23:59:59',
    })
  await expect(page.getByText('共 1 条', { exact: true })).toBeVisible()
  await expect(row(page, 'coupon-order-0012')).toBeVisible()
  await page.getByPlaceholder('请填写用户名').fill('不存在的用户')
  await page.getByPlaceholder('请填写用户名').press('Enter')
  await expect(page.getByText('暂无数据', { exact: true })).toBeVisible()
  await page.getByRole('tab', { name: '待支付', exact: true }).click()
  await expect(page.getByPlaceholder('请填写订单号')).toBeEmpty()
  await expect(page.getByPlaceholder('请填写用户名')).toBeEmpty()
  await expect(page.getByPlaceholder('开始日期')).toBeEmpty()
  await expect
    .poll(() => Object.fromEntries(api.queries.at(-1)!))
    .toEqual({ page: '1', pageSize: '10', status: '0' })
})

test('详情展示用户、券和费用：已支付显示券号，退款与关闭显示原因，待支付显示截止时间', async ({
  page,
}) => {
  await mockCouponOrders(page)
  await page.goto('/order/coupon')
  const dialog = page.getByRole('dialog', { name: '订单信息', exact: true })

  await row(page, 'coupon-order-0002').getByRole('button', { name: '查看', exact: true }).click()
  for (const text of [
    'coupon-order-0002',
    '已支付',
    '用户2',
    '午餐代金券',
    '代金券',
    '可抵扣 ￥20.00',
    'coupon-0002',
    '支付时间：2026-11-07 10:05',
  ]) {
    await expect(dialog).toContainText(text)
  }
  await expect(dialog.getByTestId('coupon-order-price')).toHaveText('￥9.90')
  await expect(dialog.getByRole('button', { name: '退款', exact: true })).toBeVisible()
  await dialog.getByRole('button', { name: '返回', exact: true }).click()
  await expect(dialog).toBeHidden()

  await row(page, 'coupon-order-0003').getByRole('button', { name: '查看', exact: true }).click()
  for (const text of ['已退款', '退款原因', '用户申请退款', '退款时间：2026-11-08 09:30']) {
    await expect(dialog).toContainText(text)
  }
  await expect(dialog.getByRole('button', { name: '退款', exact: true })).toHaveCount(0)
  await dialog.getByRole('button', { name: '返回', exact: true }).click()

  await row(page, 'coupon-order-0004').getByRole('button', { name: '查看', exact: true }).click()
  for (const text of ['已关闭', '关闭原因', '超时未支付', '关闭时间：2026-11-07 10:16']) {
    await expect(dialog).toContainText(text)
  }
  await expect(dialog).not.toContainText('coupon-0004')
  await dialog.getByRole('button', { name: '返回', exact: true }).click()

  await row(page, 'coupon-order-0001').getByRole('button', { name: '查看', exact: true }).click()
  await expect(dialog).toContainText('支付截止时间：2026-11-07 10:15')
  await expect(dialog).not.toContainText('coupon-0001')
  await expect(dialog.getByRole('button', { name: '退款', exact: true })).toHaveCount(0)
})

test('已支付订单选择原因后退款并刷新为已退款，取消或未选原因不发请求', async ({ page }) => {
  const api = await mockCouponOrders(page)
  await page.goto('/order/coupon')
  const refund = row(page, 'coupon-order-0002').getByRole('button', { name: '退款', exact: true })
  const dialog = page.getByRole('dialog', { name: '退款原因', exact: true })
  await refund.click()
  await expect(dialog).toContainText('对应的代金券立即失效')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(dialog).toBeHidden()
  await refund.click()
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(dialog).toContainText('请选择退款原因')
  expect(api.writes).toHaveLength(0)
  await chooseRefundReason(page, '重复购买')
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByText('退款成功', { exact: true })).toBeVisible()
  expect(api.writes).toEqual([
    {
      method: 'PUT',
      path: '/api/couponOrder/refund',
      body: { id: 2, refundReason: '重复购买' },
    },
  ])
  await expect(row(page, 'coupon-order-0002')).toContainText('已退款')
  await expect(row(page, 'coupon-order-0002').getByRole('button')).toHaveText(['查看'])
})

test('详情中填写其他原因退款，后端拒绝时提示错误并保留弹窗，重试成功后关闭', async ({ page }) => {
  const api = await mockCouponOrders(page)
  api.failures.refund = '要退款的用户优惠券状态不是未使用！'
  await page.goto('/order/coupon')
  await row(page, 'coupon-order-0006').getByRole('button', { name: '查看', exact: true }).click()
  const detail = page.getByRole('dialog', { name: '订单信息', exact: true })
  await detail.getByRole('button', { name: '退款', exact: true }).click()
  const dialog = await chooseRefundReason(page, '其他原因')
  const custom = dialog.getByPlaceholder('请填写您退款的原因（限20字内）')
  await custom.fill('   ')
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(dialog).toContainText('请输入退款原因')
  expect(api.writes).toHaveLength(0)
  await custom.fill('  商家补偿  ')
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(page.getByText('要退款的用户优惠券状态不是未使用！', { exact: true })).toBeVisible()
  await expect(dialog).toBeVisible()
  await expect(detail).toBeVisible()
  api.failures.refund = ''
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(dialog).toBeHidden()
  await expect(detail).toBeHidden()
  expect(api.writes.map((write) => write.body)).toEqual([
    { id: 6, refundReason: '商家补偿' },
    { id: 6, refundReason: '商家补偿' },
  ])
  await expect(row(page, 'coupon-order-0006')).toContainText('已退款')
})

test('列表和详情加载失败可重试', async ({ page }) => {
  const api = await mockCouponOrders(page)
  api.failures.list = '优惠券订单加载失败'
  await page.goto('/order/coupon')
  await expect(page.getByRole('alert').filter({ hasText: '优惠券订单加载失败' })).toBeVisible()
  api.failures.list = ''
  await page.getByRole('button', { name: '重试列表', exact: true }).click()
  await expect(row(page, 'coupon-order-0002')).toBeVisible()
  api.failures.detail = '订单详情加载失败'
  await row(page, 'coupon-order-0002').getByRole('button', { name: '查看', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '订单信息', exact: true })
  await expect(dialog).toContainText('订单详情加载失败')
  api.failures.detail = ''
  await dialog.getByRole('button', { name: '重试详情', exact: true }).click()
  await expect(dialog).toContainText('coupon-0002')
  expect(api.details).toEqual([2, 2])
})
