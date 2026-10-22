import { expect, test } from '@playwright/test'
import { restoreSession } from './helpers/session'
import { makeOrder, mockOrders } from './helpers/orders'

test.beforeEach(async ({ page }) => restoreSession(page))

test('桌面三列两行，每页六条并展示订单摘要和分页', async ({ page }) => {
  await mockOrders(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/orders')
  await expect(page.getByRole('heading', { name: '我的订单', exact: true })).toBeVisible()
  await expect(page.getByText('每一餐，都是日常的记录。', { exact: true })).toBeVisible()
  const cards = page.getByRole('article')
  await expect(cards).toHaveCount(6)
  const boxes = await cards.evaluateAll((elements) =>
    elements.map((element) => ({
      x: element.getBoundingClientRect().x,
      y: element.getBoundingClientRect().y,
    })),
  )
  expect(boxes[0]!.y).toBe(boxes[2]!.y)
  expect(boxes[3]!.y).toBeGreaterThan(boxes[0]!.y)
  expect(boxes[0]!.x).toBe(boxes[3]!.x)
  await expect(cards.first()).toContainText('2026-10-22 11:30')
  await expect(cards.first()).toContainText('ORDER-0001')
  await expect(cards.first()).toContainText('共 3 件')
  await expect(cards.first()).toContainText('¥64.00')
  await expect(cards.first().getByRole('img')).toBeVisible()
  await expect(page.getByRole('navigation', { name: '订单分页' })).toBeVisible()
  await page.getByRole('navigation', { name: '订单分页' }).locator('.btn-next').click()
  await expect(cards).toHaveCount(1)
  await expect(cards.first()).toContainText('ORDER-0007')
})

test('五个选项卡准确筛选，进行中用状态列表查询，切换后回到第一页', async ({ page }) => {
  const { queries } = await mockOrders(page)
  await page.goto('/orders')
  await expect(page.getByRole('tab')).toHaveText([
    '全部订单',
    '待付款',
    '进行中',
    '已完成',
    '已取消',
  ])
  await page.getByRole('navigation', { name: '订单分页' }).locator('.btn-next').click()
  await expect(page.getByRole('article')).toHaveCount(1)
  for (const [tab, count, statuses] of [
    ['进行中', 3, '2,3,4'],
    ['待付款', 1, '1'],
    ['已完成', 2, '5'],
    ['已取消', 1, '6'],
  ] as const) {
    await page.getByRole('tab', { name: tab, exact: true }).click()
    await expect(page.getByRole('article')).toHaveCount(count)
    expect(queries.at(-1)).toEqual({ page: 1, statuses })
    await expect(page.getByRole('navigation', { name: '订单分页' })).toHaveCount(0)
  }
  await page.getByRole('button', { name: '继续点餐', exact: true }).click()
  await expect(page).toHaveURL('/menu')
})

test('根据六种状态提供操作，联系商家显示明确的演示手机号', async ({ page }) => {
  await mockOrders(page)
  await page.goto('/orders')
  const cards = page.getByRole('article')
  await expect(cards.nth(0).getByRole('button', { name: '去支付' })).toBeVisible()
  await expect(cards.nth(0).getByRole('button', { name: '取消订单' })).toBeVisible()
  await expect(cards.nth(1).getByRole('button', { name: '催单', exact: true })).toBeVisible()
  await expect(cards.nth(1).getByRole('button', { name: '取消订单' })).toBeVisible()
  for (const i of [2, 3, 4, 5]) {
    await expect(cards.nth(i).getByRole('button', { name: '联系商家' })).toBeVisible()
    await expect(cards.nth(i).getByRole('button', { name: '取消订单' })).toHaveCount(0)
  }
  await cards.nth(2).getByRole('button', { name: '联系商家' }).click()
  const dialog = page.getByRole('dialog', { name: '联系商家', exact: true })
  await expect(dialog).toContainText('演示手机号')
  await expect(dialog.getByRole('link', { name: '138 0000 0000' })).toHaveAttribute(
    'href',
    'tel:13800000000',
  )
})

test('详情读取最新信息，显示状态流程、菜品数量与行小计、费用和配送信息', async ({ page }) => {
  await mockOrders(page)
  await page.goto('/orders')
  await page.getByRole('article').nth(1).getByRole('button', { name: '订单详情' }).click()
  const dialog = page.getByRole('dialog', { name: '订单详情', exact: true })
  await expect(dialog.getByRole('heading', { name: '待接单', exact: true })).toBeVisible()
  await expect(dialog.getByText('不辣 × 2', { exact: true })).toBeVisible()
  await expect(dialog.getByText('¥52.00', { exact: true })).toBeVisible()
  await expect(dialog.getByText('打包费', { exact: true })).toBeVisible()
  await expect(dialog.getByText('配送费', { exact: true })).toBeVisible()
  await expect(dialog.getByTestId('order-total')).toHaveText('¥64.00')
  for (const text of [
    'ORDER-0002',
    '文三路 88 号 2 幢 301 室',
    '13800000000',
    '立即送出',
    '按餐量提供',
    '少放辣，谢谢',
  ]) {
    await expect(dialog).toContainText(text)
  }
  await expect(dialog.getByRole('button', { name: '取消订单' })).toBeVisible()
  await expect(dialog.getByRole('button', { name: '催单', exact: true })).toBeVisible()
  await expect(page.locator('.user-dialog-overlay:visible')).toHaveCSS(
    'backdrop-filter',
    'blur(6px)',
  )
})

test('模拟支付确认成功后刷新列表和详情状态，待付款筛选移除该单', async ({ page }) => {
  const { writes } = await mockOrders(page)
  await page.goto('/orders')
  await page.getByRole('tab', { name: '待付款', exact: true }).click()
  await page.getByRole('button', { name: '订单详情' }).click()
  const detail = page.getByRole('dialog', { name: '订单详情', exact: true })
  await detail.getByRole('button', { name: '去支付' }).click()
  const payment = page.getByRole('dialog', { name: '订单支付', exact: true })
  await expect(payment).toContainText('模拟支付')
  await payment.getByRole('button', { name: '确认支付', exact: true }).click()
  await expect(payment).toBeHidden()
  await expect(detail.getByRole('heading', { name: '待接单', exact: true })).toBeVisible()
  await detail.getByRole('button', { name: 'Close this dialog' }).click()
  await expect(page.getByRole('article')).toHaveCount(0)
  expect(writes).toContainEqual({
    url: '/api/order/payment',
    method: 'PUT',
    data: { orderNumber: 'ORDER-0001', payMethod: 1 },
  })
})

test('取消待接单订单先确认，成功后详情显示取消原因及退款状态', async ({ page }) => {
  const { writes } = await mockOrders(page)
  await page.goto('/orders')
  await page.getByRole('article').nth(1).getByRole('button', { name: '订单详情' }).click()
  const dialog = page.getByRole('dialog', { name: '订单详情', exact: true })
  await dialog.getByRole('button', { name: '取消订单' }).click()
  await page.getByRole('button', { name: '暂不取消', exact: true }).click()
  expect(writes).toHaveLength(0)
  await dialog.getByRole('button', { name: '取消订单' }).click()
  await page.getByRole('button', { name: '确认取消', exact: true }).click()
  await expect(dialog.getByRole('heading', { name: '已取消', exact: true })).toBeVisible()
  await expect(dialog).toContainText('用户在接单前取消')
  await expect(dialog).toContainText('已退款')
  await expect(dialog.getByRole('button', { name: '取消订单' })).toHaveCount(0)
})

test('催单调用 GET，失败展示错误并允许重试', async ({ page }) => {
  const { writes } = await mockOrders(page)
  await page.route(
    '**/api/order/reminder/2',
    (route) => route.fulfill({ json: { code: 0, msg: '订单状态错误' } }),
    { times: 1 },
  )
  await page.goto('/orders')
  const card = page.getByRole('article').nth(1)
  await card.getByRole('button', { name: '催单', exact: true }).click()
  await expect(page.getByText('订单状态错误', { exact: true })).toBeVisible()
  await card.getByRole('button', { name: '催单', exact: true }).click()
  await expect(page.getByText('已提醒商家，请耐心等待', { exact: true })).toBeVisible()
  expect(writes).toContainEqual({ url: '/api/order/reminder/2', method: 'GET', data: null })
})

test('再来一单先加入后端购物车并同步共享状态，再跳转点餐，不清空原购物车', async ({ page }) => {
  const { writes } = await mockOrders(page)
  await page.goto('/orders')
  const refreshed = page.waitForResponse('**/api/shoppingCart/list')
  await page.getByRole('article').first().getByRole('button', { name: '再来一单' }).click()
  const cart = await (await refreshed).json()
  expect(cart.data).toHaveLength(2)
  await expect(page).toHaveURL('/menu')
  expect(writes).toEqual([{ url: '/api/order/repetition/1', method: 'POST', data: null }])
})

test('列表失败可重试，空状态保留继续点餐入口', async ({ page }) => {
  await mockOrders(page, [])
  await page.route(
    '**/api/order/historyOrdersByStatusList**',
    (route) => route.fulfill({ json: { code: 0, msg: '订单暂时不可用' } }),
    { times: 1 },
  )
  await page.goto('/orders')
  await expect(page.getByText('订单暂时不可用', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '重新加载' }).click()
  await expect(page.getByText('还没有相关订单', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '继续点餐' })).toBeVisible()
})

test('详情失败可重试，支付失败保持弹窗且不显示成功', async ({ page }) => {
  await mockOrders(page)
  await page.route(
    '**/api/order/orderDetail/1',
    (route) => route.fulfill({ json: { code: 0, msg: '订单详情暂时不可用' } }),
    { times: 1 },
  )
  await page.route(
    '**/api/order/payment',
    (route) => route.fulfill({ json: { code: 0, msg: '订单状态错误，请刷新' } }),
    { times: 1 },
  )
  await page.goto('/orders')
  await page.getByRole('article').first().getByRole('button', { name: '订单详情' }).click()
  const dialog = page.getByRole('dialog', { name: '订单详情', exact: true })
  await expect(dialog.getByText('订单详情暂时不可用')).toBeVisible()
  await dialog.getByRole('button', { name: '重新加载' }).click()
  await dialog.getByRole('button', { name: '去支付' }).click()
  const payment = page.getByRole('dialog', { name: '订单支付', exact: true })
  await payment.getByRole('button', { name: '确认支付', exact: true }).click()
  await expect(payment).toBeVisible()
  await expect(page.getByText('订单状态错误，请刷新', { exact: true })).toBeVisible()
  await payment.getByRole('button', { name: '确认支付', exact: true }).click()
  await expect(payment).toBeHidden()
})

test('手机单列、图片失败有占位，长订单号与详情不横向溢出', async ({ page }) => {
  const order = makeOrder(1, 3)
  order.number = 'a'.repeat(32)
  order.orderDetailList[0]!.image = '/broken-image.png'
  order.address = '很长的地址'.repeat(25)
  await mockOrders(page, [order, makeOrder(2, 4)])
  await page.route('**/broken-image.png', (route) => route.fulfill({ status: 404 }))
  await page.setViewportSize({ width: 375, height: 667 })
  await page.goto('/orders')
  await expect(page.getByRole('article')).toHaveCount(2)
  await expect(page.locator('.dish-image-fallback').first()).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
  await page.getByRole('article').first().getByRole('button', { name: '订单详情' }).click()
  const dialog = page.getByRole('dialog', { name: '订单详情', exact: true })
  await expect(dialog.getByRole('button', { name: '联系商家' })).toBeInViewport()
  await dialog.getByText(order.address, { exact: true }).scrollIntoViewIfNeeded()
  expect((await dialog.locator('.el-dialog').boundingBox())!.width).toBeLessThanOrEqual(343)
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
})
