import { expect, test, type Page } from '@playwright/test'
import { mockAdminSession } from './helpers/admin.js'
import { mockOrders, makeOrder } from './helpers/orders.js'

test.beforeEach(async ({ page, context, baseURL }) => {
  await mockAdminSession(page, context, baseURL!)
})

function row(page: Page, number: string) {
  return page
    .getByRole('row')
    .filter({ has: page.getByRole('cell', { name: number, exact: true }) })
}
async function chooseReason(page: Page, value: string) {
  const dialog = page
    .getByRole('dialog')
    .filter({ has: page.getByRole('combobox', { name: /原因选择/ }) })
  await dialog.locator('.el-select').click()
  await page.getByRole('option', { name: value, exact: true }).click()
  return dialog
}

async function expectViewCentered(page: Page, number: string) {
  const offset = await row(page, number)
    .getByRole('cell')
    .last()
    .evaluate((cell) => {
      const bounds = cell.getBoundingClientRect()
      const button = cell.querySelector('button')!.getBoundingClientRect()
      return Math.abs(button.x + button.width / 2 - bounds.x - bounds.width / 2)
    })
  expect(offset).toBeLessThan(1)
}

test('全部订单字段、三列操作规则、统计红点及六种状态切换', async ({ page }) => {
  const api = await mockOrders(page)
  await page.goto('/order')
  await expect(page.getByRole('tab')).toHaveText([
    '全部订单',
    '待接单6',
    '待派送6',
    '派送中6',
    '已完成',
    '已取消',
  ])
  await expect(page.getByRole('columnheader')).toHaveText([
    '订单号',
    '订单状态',
    '用户名',
    '手机号',
    '地址',
    '下单时间',
    '实收金额',
    '操作',
  ])
  await expect(row(page, 'order-0001').getByRole('button')).toHaveText(['取消', '查看'])
  await expect(row(page, 'order-0002').getByRole('button')).toHaveText(['接单', '拒单', '查看'])
  await expect(row(page, 'order-0003').getByRole('button')).toHaveText(['派送', '取消', '查看'])
  await expect(row(page, 'order-0004').getByRole('button')).toHaveText(['完成', '取消', '查看'])
  await expect(row(page, 'order-0005').getByRole('button')).toHaveText(['查看'])
  await expect(row(page, 'order-0006').getByRole('button')).toHaveText(['查看'])
  await expectViewCentered(page, 'order-0005')
  await expectViewCentered(page, 'order-0006')
  for (const [status, name, headers] of [
    [
      2,
      '待接单',
      ['订单号', '订单菜品', '地址', '预计送达时间', '实收金额', '备注', '餐具数量', '操作'],
    ],
    [3, '待派送', ['订单号', '订单菜品', '地址', '预计送达时间', '备注', '餐具数量', '操作']],
    [4, '派送中', ['订单号', '订单菜品', '地址', '预计送达时间', '备注', '餐具数量', '操作']],
    [5, '已完成', ['订单号', '用户名', '手机号', '地址', '送达时间', '实收金额', '备注', '操作']],
    [
      6,
      '已取消',
      ['订单号', '用户名', '手机号', '地址', '下单时间', '取消时间', '取消原因', '操作'],
    ],
  ] as const) {
    await page.getByRole('tab', { name: new RegExp(`^${name}`) }).click()
    await expect(page.getByRole('columnheader')).toHaveText([...headers])
    await expect.poll(() => api.queries.at(-1)?.get('status')).toBe(String(status))
  }
  await expect(page.getByText('餐厅已打烊，暂时无法接单', { exact: true }).first()).toBeVisible()
  for (const [name, number] of [
    ['已完成', 'order-0005'],
    ['已取消', 'order-0006'],
  ] as const) {
    await page.getByRole('tab', { name, exact: true }).click()
    await expect(row(page, number).getByRole('button')).toHaveText(['查看'])
    await expectViewCentered(page, number)
  }
})

test('订单号手机号日期组合查询、回车、清空、空结果及分页', async ({ page }) => {
  const api = await mockOrders(page, 41)
  await page.goto('/order')
  await page.getByRole('button', { name: '下一页' }).click()
  await expect.poll(() => api.queries.at(-1)?.get('page')).toBe('2')
  await page.getByPlaceholder('请填写订单号').fill('order-0002')
  await page.getByPlaceholder('请填写手机号').fill('199')
  const start = page.getByPlaceholder('开始日期'),
    end = page.getByPlaceholder('结束日期')
  await start.fill('2026-10-18')
  await start.press('Tab')
  await end.fill('2026-10-18')
  await end.press('Enter')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect
    .poll(() => Object.fromEntries(api.queries.at(-1)!))
    .toMatchObject({
      page: '1',
      number: 'order-0002',
      phone: '199',
      beginTime: '2026-10-18 00:00:00',
      endTime: '2026-10-18 23:59:59',
    })
  await expect(page.getByText('共 1 条', { exact: true })).toBeVisible()
  await page.getByPlaceholder('请填写订单号').fill('no-match')
  await page.getByPlaceholder('请填写订单号').press('Enter')
  await expect(page.getByText('暂无数据', { exact: true })).toBeVisible()
  await page.getByRole('tab', { name: /^待接单/ }).click()
  await expect(page.getByPlaceholder('请填写订单号')).toBeEmpty()
  await expect(page.getByPlaceholder('开始日期')).toBeEmpty()
})

test('订单详情金额、两份菜品、取消原因以及关闭后回到列表', async ({ page }) => {
  await mockOrders(page)
  await page.goto('/order')
  await row(page, 'order-0002').getByRole('button', { name: '查看', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '订单信息', exact: true })
  await expect(dialog).toContainText('收货人2')
  await expect(dialog).toContainText('招牌菜x2')
  await expect(dialog).toContainText('￥20.00')
  await expect(dialog.getByTestId('dish-subtotal')).toHaveText('￥24.00')
  await expect(dialog.getByTestId('order-total')).toHaveText('￥33.00')
  await expect(dialog).toContainText('微信支付')
  await expect(dialog.getByRole('button', { name: '接单', exact: true })).toBeVisible()
  await dialog.getByRole('button', { name: '返回', exact: true }).click()
  await row(page, 'order-0006').getByRole('button', { name: '查看', exact: true }).click()
  await expect(dialog).toContainText('取消原因')
  await expect(dialog).toContainText('餐厅已打烊，暂时无法接单')
  await expect(dialog.getByRole('button', { name: '接单', exact: true })).toHaveCount(0)
  await dialog.getByRole('button', { name: '关闭此对话框' }).click()
  await expect(dialog).toBeHidden()
})

test('列表接单、派送和完成后刷新统计和状态', async ({ page }) => {
  const api = await mockOrders(page)
  await page.goto('/order')
  const current = row(page, 'order-0002')
  await current.getByRole('button', { name: '接单', exact: true }).click()
  await expect(current.getByRole('button', { name: '派送', exact: true })).toBeVisible()
  await current.getByRole('button', { name: '派送', exact: true }).click()
  await expect(current.getByRole('button', { name: '完成', exact: true })).toBeVisible()
  await current.getByRole('button', { name: '完成', exact: true }).click()
  await expect(current.getByRole('button')).toHaveText(['查看'])
  expect(api.writes.map((w) => w.path)).toEqual([
    '/api/order/confirm',
    '/api/order/delivery/2',
    '/api/order/complete/2',
  ])
  await expect(page.getByRole('tab', { name: /^待接单/ })).toHaveText('待接单5')
})

test('拒单原因必填、自定义原因、失败保留内容和重试', async ({ page }) => {
  const api = await mockOrders(page)
  await page.goto('/order')
  await row(page, 'order-0002').getByRole('button', { name: '拒单', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '拒绝原因', exact: true })
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(
    dialog.locator('.el-form-item__error').filter({ hasText: '请选择拒绝原因' }),
  ).toBeVisible()
  await chooseReason(page, '自定义原因')
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(page.getByText('请输入拒绝原因', { exact: true })).toBeVisible()
  await dialog.getByPlaceholder('请填写您拒绝的原因（限20字内）').fill('今天售罄了')
  api.failures.action = '订单状态错误'
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(page.getByRole('alert').filter({ hasText: '订单状态错误' })).toBeVisible()
  await expect(dialog.getByRole('textbox')).toHaveValue('今天售罄了')
  api.failures.action = ''
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(dialog).toBeHidden()
  expect(api.writes.at(-1)?.body).toEqual({ id: 2, rejectionReason: '今天售罄了' })
  await expect(row(page, 'order-0002')).toContainText('已取消')
})

test('待付款取消选择原版原因，取消弹窗不提交', async ({ page }) => {
  const api = await mockOrders(page)
  await page.goto('/order')
  await row(page, 'order-0001').getByRole('button', { name: '取消', exact: true }).click()
  let dialog = await chooseReason(page, '客户电话取消')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  expect(api.writes).toHaveLength(0)
  await row(page, 'order-0001').getByRole('button', { name: '取消', exact: true }).click()
  dialog = await chooseReason(page, '客户电话取消')
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(dialog).toBeHidden()
  expect(api.writes[0]).toEqual({
    path: '/api/order/cancel',
    body: { id: 1, cancelReason: '客户电话取消' },
  })
})

for (const [status, label, primary] of [
  [3, '待派送', '派送'],
  [4, '派送中', '完成'],
] as const) {
  test(`${label}列表保留取消入口，失败显示后端提示并保留原因与订单状态`, async ({ page }) => {
    const api = await mockOrders(page)
    api.failures.action = '订单状态错误'
    await page.goto(`/order?status=${status}`)
    const current = row(page, `order-000${status}`)
    await current.getByRole('button', { name: '取消', exact: true }).click()
    const reason = await chooseReason(page, '客户电话取消')
    await reason.getByRole('button', { name: '确定', exact: true }).click()
    await expect(page.getByRole('alert').filter({ hasText: '订单状态错误' })).toBeVisible()
    await expect(reason).toBeVisible()
    await expect(reason.locator('.el-select')).toContainText('客户电话取消')
    await expect(current.getByRole('button', { name: primary, exact: true })).toBeEnabled()
    expect(api.writes).toEqual([
      { path: '/api/order/cancel', body: { id: status, cancelReason: '客户电话取消' } },
    ])
    expect(api.records.find((order) => order.id === status)?.status).toBe(status)
    await reason.getByRole('button', { name: '取消', exact: true }).click()
    await current.getByRole('button', { name: '查看', exact: true }).click()
    const detail = page.getByRole('dialog', { name: '订单信息', exact: true })
    await expect(detail.getByRole('button', { name: primary, exact: true })).toBeVisible()
    await expect(detail.getByRole('button', { name: '取消', exact: true })).toHaveCount(0)
    await expect(detail).toContainText(label)
  })
}

test('待接单详情处理完自动打开下一条，取消勾选后关闭', async ({ page }) => {
  const api = await mockOrders(page)
  await page.goto('/order?status=2')
  await row(page, 'order-0002').getByRole('button', { name: '查看', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '订单信息', exact: true })
  await dialog.getByRole('button', { name: '接单', exact: true }).click()
  await expect(dialog).toContainText('order-0008')
  await dialog.locator('.el-checkbox').click()
  await dialog.getByRole('button', { name: '接单', exact: true }).click()
  await expect(dialog).toBeHidden()
  expect(api.writes.map((w) => w.body?.id)).toEqual([2, 8])
})

test('通知在订单页内打开不同订单，并实时刷新数量', async ({ page, context, baseURL }) => {
  const sockets = await mockAdminSession(page, context, baseURL!)
  const api = await mockOrders(page)
  await page.goto('/order')
  await expect.poll(() => sockets.length).toBe(1)
  api.records.unshift(makeOrder(100, 2))
  sockets[0]!.send(JSON.stringify({ type: 1, orderId: 100, content: 'order-0100' }))
  await expect(page.getByRole('tab', { name: /^待接单/ })).toHaveText('待接单7')
  await page
    .getByRole('alert')
    .filter({ hasText: '您有1个' })
    .getByRole('button', { name: '订单待处理' })
    .click()
  const dialog = page.getByRole('dialog', { name: '订单信息', exact: true })
  await expect(dialog).toContainText('order-0100')
  sockets[0]!.send(JSON.stringify({ type: 2, orderId: 4, content: 'order-0004' }))
  await page
    .getByRole('alert')
    .filter({ hasText: '催单' })
    .getByRole('button', { name: '去处理' })
    .click()
  await expect(dialog).toContainText('order-0004')
  await expect(dialog.getByRole('button', { name: '完成', exact: true })).toBeVisible()
  expect(api.details).toContain(4)

  await page.goBack()
  await expect(page).toHaveURL('/order?orderId=100')
  await expect(dialog).toContainText('order-0100')
  await page.goBack()
  await expect(page).toHaveURL('/order')
  await expect(dialog).toBeHidden()
  await page.goForward()
  await expect(page).toHaveURL('/order?orderId=100')
  await expect(dialog).toContainText('order-0100')

  await dialog.getByRole('button', { name: '拒单', exact: true }).click()
  const reason = page.getByRole('dialog', { name: '拒绝原因', exact: true })
  await expect(reason).toBeVisible()
  await page.goBack()
  await expect(page).toHaveURL('/order')
  await expect(reason).toBeHidden()
  await expect(dialog).toBeHidden()
  await row(page, 'order-0002').getByRole('button', { name: '查看', exact: true }).click()
  await expect(dialog).toContainText('order-0002')
  expect(api.writes).toHaveLength(0)
})

test('提醒详情加载期间浏览器返回，迟到的响应不会保留弹窗', async ({ page, context, baseURL }) => {
  const sockets = await mockAdminSession(page, context, baseURL!)
  await mockOrders(page)
  let release!: () => void
  const gate = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route('**/api/order/details/100', async (route) => {
    await gate
    await route.fulfill({ json: { code: 1, msg: null, data: makeOrder(100, 2) } })
  })
  await page.goto('/order')
  await expect.poll(() => sockets.length).toBe(1)
  sockets[0]!.send(JSON.stringify({ type: 1, orderId: 100, content: 'order-0100' }))
  const request = page.waitForRequest('**/api/order/details/100')
  const response = page.waitForResponse('**/api/order/details/100')
  await page
    .getByRole('alert')
    .filter({ hasText: '您有1个' })
    .getByRole('button', { name: '订单待处理' })
    .click()
  await request
  await expect(page.getByRole('dialog', { name: '订单信息', exact: true })).toBeVisible()
  await page.goBack()
  release()
  await response
  await expect(page).toHaveURL('/order')
  await expect(page.getByRole('dialog', { name: '订单信息', exact: true })).toBeHidden()
})

test('列表和详情加载失败可重试，统计失败单独显示，操作期间不重复请求', async ({ page }) => {
  const api = await mockOrders(page)
  api.failures.list = '订单列表加载失败'
  api.failures.statistics = '统计暂不可用'
  await page.goto('/order')
  await expect(page.getByRole('alert').filter({ hasText: '订单列表加载失败' })).toBeVisible()
  api.failures.list = ''
  api.failures.statistics = ''
  await page.getByRole('button', { name: '重试列表', exact: true }).click()
  await expect(row(page, 'order-0002')).toBeVisible()
  api.failures.detail = '订单详情加载失败'
  await row(page, 'order-0002').getByRole('button', { name: '查看', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '订单信息', exact: true })
  await expect(dialog).toContainText('订单详情加载失败')
  api.failures.detail = ''
  await dialog.getByRole('button', { name: '重试详情', exact: true }).click()
  api.delaySave = true
  await dialog.getByRole('button', { name: '接单', exact: true }).click()
  await expect(dialog.getByRole('button', { name: '接单', exact: true })).toBeDisabled()
  await page.keyboard.press('Escape')
  await expect(dialog).toBeVisible()
  await expect.poll(() => api.writes.length).toBe(1)
  api.releaseSave()
  await expect(dialog).toBeHidden()
})
