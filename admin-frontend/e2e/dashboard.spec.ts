import { expect, test } from '@playwright/test'
import { mockAdminSession } from './helpers/admin.js'
import { mockWorkspace } from './helpers/workspace.js'
import { makeOrder, mockOrders } from './helpers/orders.js'

test.use({ timezoneId: 'Asia/Shanghai' })

test.beforeEach(async ({ page, context, baseURL }) => {
  await page.clock.setFixedTime(new Date('2026-10-18T12:00:00+08:00'))
  await mockAdminSession(page, context, baseURL!)
})

test('五个板块展示今日指标、商品总览及全部日期的待处理订单', async ({ page }) => {
  const workspace = await mockWorkspace(page)
  const orders = await mockOrders(page, 72)
  await page.goto('/')
  await expect(page).toHaveURL('/dashboard')
  for (const name of ['今日数据', '订单管理', '菜品总览', '套餐总览', '订单信息']) {
    await expect(page.getByRole('region', { name, exact: true })).toBeVisible()
  }
  const today = page.getByRole('region', { name: '今日数据', exact: true })
  await expect(today).toContainText('2026.10.18')
  await expect(today.getByLabel('营业额')).toHaveText('¥ 288.35')
  await expect(today.getByLabel('有效订单')).toHaveText('3')
  await expect(today.getByLabel('订单完成率')).toHaveText('30%')
  await expect(today.getByLabel('平均客单价')).toHaveText('¥ 96.12')
  await expect(today.getByLabel('新增用户')).toHaveText('2')
  const overview = page.getByRole('region', { name: '订单管理', exact: true })
  await expect(overview).toContainText('2026.10.18')
  await expect(overview.getByLabel('今日待接单数量')).toHaveText('1')
  await expect(overview.getByLabel('今日待派送数量')).toHaveText('2')
  await expect(overview.getByLabel('今日已完成数量')).toHaveText('3')
  await expect(overview.getByLabel('今日已取消数量')).toHaveText('4')
  await expect(overview.getByLabel('今日全部订单数量')).toHaveText('10')
  await expect(
    page.getByRole('region', { name: '菜品总览', exact: true }).getByLabel('已启售数量'),
  ).toHaveText('30')
  await expect(
    page.getByRole('region', { name: '套餐总览', exact: true }).getByLabel('已启售数量'),
  ).toHaveText('7')
  const list = page.getByRole('region', { name: '订单信息', exact: true })
  await expect(list.getByRole('tab')).toHaveText(['待接单12', '待派送12'])
  await expect(list.getByRole('columnheader')).toHaveText([
    '订单号',
    '订单菜品',
    '地址',
    '预计送达时间',
    '实收金额',
    '备注',
    '餐具数量',
    '操作',
  ])
  await expect(list.getByRole('textbox', { name: '订单号：' })).toHaveCount(0)
  await expect(list.locator('tbody tr')).toHaveCount(10)
  expect(orders.queries[0]?.get('status')).toBe('2')
  expect(orders.queries[0]?.has('beginTime')).toBe(false)
  expect(workspace.reads).toHaveLength(4)
  await page.reload()
  await expect(today.getByLabel('营业额')).toHaveText('¥ 288.35')
})

test('详细数据、订单明细和商品入口可导航，今日订单数量链接带对应状态', async ({ page }) => {
  await mockWorkspace(page)
  for (const [name, path] of [
    ['详细数据', '/statistics'],
    ['订单明细', '/order'],
    ['菜品管理', '/dish'],
    ['新增菜品', '/dish/add'],
    ['套餐管理', '/setmeal'],
    ['新增套餐', '/setmeal/add'],
  ]) {
    await page.goto('/dashboard')
    const link = page.getByRole('link', { name, exact: true })
    await expect(link).toHaveAttribute('href', path!)
    await link.click()
    await expect(page).toHaveURL(path!)
  }
  await page.goto('/dashboard')
  const overview = page.getByRole('region', { name: '订单管理', exact: true })
  for (const [label, path] of [
    ['待接单', '/order?status=2'],
    ['待派送', '/order?status=3'],
    ['已完成', '/order?status=5'],
    ['已取消', '/order?status=6'],
    ['全部订单', '/order'],
  ]) {
    await expect(overview.getByRole('link', { name: new RegExp(label!) })).toHaveAttribute(
      'href',
      path!,
    )
  }
})

test('待处理列表支持分页，切换状态回到首页，接单后同步更新概览和角标', async ({ page }) => {
  const workspace = await mockWorkspace(page)
  const orders = await mockOrders(page, 72)
  await page.goto('/dashboard')
  const list = page.getByRole('region', { name: '订单信息', exact: true })
  await list.getByRole('button', { name: '下一页', exact: true }).click()
  await expect.poll(() => orders.queries.at(-1)?.get('page')).toBe('2')
  await list.getByRole('tab', { name: /^待派送/ }).click()
  await expect.poll(() => orders.queries.at(-1)?.get('status')).toBe('3')
  expect(orders.queries.at(-1)?.get('page')).toBe('1')
  await expect(list.getByRole('columnheader', { name: '实收金额', exact: true })).toHaveCount(0)
  await expect(page).toHaveURL('/dashboard')
  await list.getByRole('tab', { name: /^待接单/ }).click()
  workspace.data.overviewOrders.waitingOrders = 0
  workspace.data.overviewOrders.deliveredOrders = 3
  const row = list.getByRole('row').filter({ hasText: 'order-0002' })
  await row.getByRole('button', { name: '接单', exact: true }).click()
  await expect(row).toHaveCount(0)
  await expect(list.getByRole('tab', { name: /^待接单/ })).toHaveText('待接单11')
  await expect(list.getByRole('tab', { name: /^待派送/ })).toHaveText('待派送13')
  const overview = page.getByRole('region', { name: '订单管理', exact: true })
  await expect(overview.getByLabel('今日待接单数量')).toHaveText('0')
  await expect(overview.getByLabel('今日待派送数量')).toHaveText('3')
  expect(workspace.reads.filter((name) => name === 'businessData')).toHaveLength(2)
  await list.getByRole('tab', { name: /^待派送/ }).click()
  await list
    .getByRole('row')
    .filter({ hasText: 'order-0002' })
    .getByRole('button', { name: '派送', exact: true })
    .click()
  await expect(list.getByRole('row').filter({ hasText: 'order-0002' })).toHaveCount(0)
  expect(orders.writes.map((write) => write.path)).toEqual([
    '/api/order/confirm',
    '/api/order/delivery/2',
  ])
})

for (const surface of ['列表', '详情'] as const) {
  test(`工作台待派送${surface}使用对应取消原因，成功后刷新列表和今日概览`, async ({ page }) => {
    const workspace = await mockWorkspace(page)
    const orders = await mockOrders(page)
    await page.goto('/dashboard')
    const list = page.getByRole('region', { name: '订单信息', exact: true })
    await list.getByRole('tab', { name: /^待派送/ }).click()
    const current = list.getByRole('row').filter({ hasText: 'order-0003' })
    const detail = page.getByRole('dialog', { name: '订单信息', exact: true })
    if (surface === '详情') await current.getByRole('button', { name: '查看', exact: true }).click()
    await (surface === '列表' ? current : detail)
      .getByRole('button', { name: '取消', exact: true })
      .click()
    const reason = page.getByRole('dialog', { name: '取消原因', exact: true })
    await reason.locator('.el-select').click()
    await expect(page.getByRole('option')).toHaveText([
      '客户协商取消',
      '食材不足',
      '设备故障',
      '无法安排配送',
      '其他原因',
    ])
    await page.getByRole('option', { name: '客户协商取消', exact: true }).click()
    const readsBefore = workspace.reads.filter((endpoint) => endpoint === 'overviewOrders').length
    workspace.data.overviewOrders.deliveredOrders = 1
    workspace.data.overviewOrders.cancelledOrders = 5
    await reason.getByRole('button', { name: '确定', exact: true }).click()
    await expect(reason).toBeHidden()
    await expect(detail).toBeHidden()
    await expect(current).toHaveCount(0)
    await expect(list.getByRole('tab', { name: /^待派送/ })).toHaveText('待派送5')
    const overview = page.getByRole('region', { name: '订单管理', exact: true })
    await expect(overview.getByLabel('今日待派送数量')).toHaveText('1')
    await expect(overview.getByLabel('今日已取消数量')).toHaveText('5')
    expect(
      workspace.reads.filter((endpoint) => endpoint === 'overviewOrders').length,
    ).toBeGreaterThan(readsBefore)
    expect(orders.writes).toEqual([
      { path: '/api/order/cancel', body: { id: 3, cancelReason: '客户协商取消' } },
    ])
    await expect(page).toHaveURL('/dashboard')
  })
}

test('订单详情和拒单复用现有流程，失败保留原因，成功后自动打开下一条', async ({ page }) => {
  const orders = await mockOrders(page)
  await page.goto('/dashboard')
  const list = page.getByRole('region', { name: '订单信息', exact: true })
  await list
    .getByRole('row')
    .filter({ hasText: 'order-0002' })
    .getByRole('button', { name: '查看', exact: true })
    .click()
  const detail = page.getByRole('dialog', { name: '订单信息', exact: true })
  await expect(detail).toContainText('order-0002')
  await expect(detail.getByRole('checkbox')).toBeChecked()
  await detail.getByRole('button', { name: '拒单', exact: true }).click()
  const reason = page.getByRole('dialog', { name: '拒绝原因', exact: true })
  await reason.locator('.el-select').click()
  await page.getByRole('option', { name: '其他原因', exact: true }).click()
  await reason.getByRole('textbox').fill('菜品售罄')
  orders.failures.action = '订单状态错误'
  await reason.getByRole('button', { name: '确定', exact: true }).click()
  await expect(page.getByRole('alert').filter({ hasText: '订单状态错误' })).toBeVisible()
  await expect(reason.getByRole('textbox')).toHaveValue('菜品售罄')
  orders.failures.action = ''
  await reason.getByRole('button', { name: '确定', exact: true }).click()
  await expect(reason).toBeHidden()
  await expect(detail).toContainText('order-0008')
  await expect(page).toHaveURL('/dashboard')
  expect(orders.writes.at(-1)?.body).toEqual({ id: 2, rejectionReason: '菜品售罄' })
})

test('来单消息刷新工作台，点击提醒打开订单管理详情，返回时无残留弹窗', async ({
  page,
  context,
  baseURL,
}) => {
  const sockets = await mockAdminSession(page, context, baseURL!)
  const workspace = await mockWorkspace(page)
  const orders = await mockOrders(page)
  await page.goto('/dashboard')
  await expect.poll(() => sockets.length).toBe(1)
  await expect(page.getByRole('tab', { name: /^待接单/ })).toHaveText('待接单6')
  orders.records.unshift(makeOrder(100, 2))
  workspace.data.overviewOrders.waitingOrders = 2
  sockets[0]!.send(JSON.stringify({ type: 1, orderId: 100, content: 'order-0100' }))
  await expect(page.getByRole('tab', { name: /^待接单/ })).toHaveText('待接单7')
  await expect(page.getByLabel('今日待接单数量')).toHaveText('2')
  await page
    .getByRole('alert')
    .filter({ hasText: '您有1个' })
    .getByRole('button', { name: '订单待处理' })
    .click()
  await expect(page).toHaveURL('/order?orderId=100')
  const detail = page.getByRole('dialog', { name: '订单信息', exact: true })
  await expect(detail).toContainText('order-0100')
  await page.goBack()
  await expect(page).toHaveURL('/dashboard')
  await expect(detail).toBeHidden()
  await expect(page.getByRole('region', { name: '订单信息', exact: true })).toBeVisible()
  expect(sockets).toHaveLength(1)
})

test('各概览失败可独立重试，空数据呈现零值，窄屏布局可使用', async ({ page }) => {
  const api = await mockWorkspace(page)
  api.failures.businessData = '营业数据读取失败'
  api.failures.overviewDishes = '菜品概览读取失败'
  await page.goto('/dashboard')
  const today = page.getByRole('region', { name: '今日数据', exact: true })
  await expect(today.getByRole('alert')).toContainText('营业数据读取失败')
  const dishes = page.getByRole('region', { name: '菜品总览', exact: true })
  await expect(dishes.getByRole('alert')).toContainText('菜品概览读取失败')
  await expect(
    page.getByRole('region', { name: '套餐总览', exact: true }).getByLabel('已启售数量'),
  ).toHaveText('7')
  api.failures.businessData = ''
  api.failures.overviewDishes = ''
  api.data.businessData = {
    turnover: 0,
    validOrderCount: 0,
    orderCompletionRate: 0,
    unitPrice: 0,
    newUsers: 0,
  }
  await today.getByRole('button', { name: '重试', exact: true }).click()
  await dishes.getByRole('button', { name: '重试', exact: true }).click()
  await expect(today.getByLabel('营业额')).toHaveText('¥ 0.00')
  await expect(today.getByLabel('订单完成率')).toHaveText('0%')
  await expect(today.getByLabel('平均客单价')).toHaveText('¥ 0.00')
  await expect(dishes.getByLabel('已启售数量')).toHaveText('30')
  await page.setViewportSize({ width: 800, height: 1000 })
  await expect(page.getByRole('link', { name: '新增菜品', exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: '新增套餐', exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(800)
})
