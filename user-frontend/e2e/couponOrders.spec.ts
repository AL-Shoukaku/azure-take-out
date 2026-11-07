import { expect, test, type Page } from '@playwright/test'
import { restoreSession } from './helpers/session'
import { makeCouponOrder, mockCouponOrders } from './helpers/couponOrders'

test.beforeEach(async ({ page }) => restoreSession(page))

const ordersNavigation = (page: Page) =>
  page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '我的订单', exact: true })

test('我的订单左右两个入口分别进入外卖订单和优惠券订单，导航保持选中', async ({ page }) => {
  await mockCouponOrders(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/menu')
  await ordersNavigation(page).click()
  await expect(page).toHaveURL('/orders')
  await expect(page.getByRole('heading', { name: '我的订单', exact: true })).toBeVisible()
  const main = page.getByRole('main')
  const takeout = main.getByRole('link', { name: /外卖订单/ })
  const coupons = main.getByRole('link', { name: /优惠券订单/ })
  const takeoutBox = (await takeout.boundingBox())!
  const couponBox = (await coupons.boundingBox())!
  expect(takeoutBox.x + takeoutBox.width).toBeLessThan(couponBox.x)
  expect(takeoutBox.y).toBe(couponBox.y)
  // 两个入口作为一组位于页面中部，卡片宽大，图标在中线以上、名称在中线以下
  const heading = (await page
    .getByRole('heading', { name: '我的订单', exact: true })
    .boundingBox())!
  expect(takeoutBox.y).toBeGreaterThan(heading.y + heading.height + 100)
  expect(Math.abs((takeoutBox.x + couponBox.x + couponBox.width) / 2 - 720)).toBeLessThan(1)
  expect(couponBox.x + couponBox.width - takeoutBox.x).toBeGreaterThanOrEqual(1100)
  expect(takeoutBox.height).toBeGreaterThanOrEqual(400)
  const middle = takeoutBox.y + takeoutBox.height / 2
  const icon = (await takeout.locator('.entry-icon').boundingBox())!
  const title = (await takeout.getByRole('heading', { name: '外卖订单' }).boundingBox())!
  expect(icon.y + icon.height / 2).toBeLessThan(middle)
  expect(title.y + title.height / 2).toBeGreaterThan(middle)
  expect(await page.evaluate(() => document.documentElement.scrollHeight)).toBe(900)
  await takeout.click()
  await expect(page).toHaveURL('/orders/takeout')
  await expect(page.getByRole('heading', { name: '外卖订单', exact: true })).toBeVisible()
  await expect(ordersNavigation(page)).toHaveClass(/is-active/)
  await page.goBack()
  await main.getByRole('link', { name: /优惠券订单/ }).click()
  await expect(page).toHaveURL('/orders/coupons')
  await expect(page.getByRole('heading', { name: '优惠券订单', exact: true })).toBeVisible()
  await expect(ordersNavigation(page)).toHaveClass(/is-active/)
})

test('桌面三列两行，每页六条，卡片展示订单号、券名、价格、优惠方式、状态和创建时间', async ({
  page,
}) => {
  await mockCouponOrders(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/orders/coupons')
  await expect(page.getByText('每一张券，都是下一餐的小惊喜。', { exact: true })).toBeVisible()
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
  for (const text of [
    '2026-11-07 10:00',
    'COUPON-ORDER-0001',
    '午餐代金券',
    '¥9.90',
    '可抵扣 ¥20.50',
    '待支付',
  ]) {
    await expect(cards.first()).toContainText(text)
  }
  const buttonBoxes = async (index: number, names: string[]) =>
    Promise.all(
      names.map(
        async (name) => (await cards.nth(index).getByRole('button', { name }).boundingBox())!,
      ),
    )
  const unpaidCard = (await cards.first().boundingBox())!
  const [detail, contact, pay] = await buttonBoxes(0, ['查看详情', '联系商家', '去支付'])
  expect(detail!.x + detail!.width).toBeLessThan(contact!.x)
  expect(contact!.x + contact!.width).toBeLessThan(pay!.x)
  expect(unpaidCard.x + unpaidCard.width - (pay!.x + pay!.width)).toBeLessThan(30)
  const paidCard = (await cards.nth(1).boundingBox())!
  const [paidDetail, paidContact] = await buttonBoxes(1, ['查看详情', '联系商家'])
  expect(paidDetail!.x).toBeGreaterThan(paidCard.x + paidCard.width / 2)
  expect(paidCard.x + paidCard.width - (paidContact!.x + paidContact!.width)).toBeLessThan(30)
  for (const index of [1, 2, 3]) {
    await expect(cards.nth(index).getByRole('button', { name: '去支付' })).toHaveCount(0)
  }
  await page.getByRole('navigation', { name: '订单分页' }).locator('.btn-next').click()
  await expect(cards).toHaveCount(1)
  await expect(cards.first()).toContainText('COUPON-ORDER-0007')
})

test('卡片按订单状态显示支付截止、支付、退款或关闭时间', async ({ page }) => {
  await mockCouponOrders(page)
  await page.goto('/orders/coupons')
  const cards = page.getByRole('article')
  for (const [index, status, terms, values] of [
    [0, '待支付', ['支付截止'], ['2026-11-07 10:15']],
    [1, '已支付', ['支付时间'], ['2026-11-07 10:01']],
    [2, '已退款', ['支付时间', '退款时间'], ['2026-11-07 10:01', '2026-11-08 09:30']],
    [3, '已关闭', ['关闭时间'], ['2026-11-07 10:16']],
  ] as const) {
    await expect(cards.nth(index)).toContainText(status)
    await expect(cards.nth(index).getByRole('term')).toHaveText([...terms])
    await expect(cards.nth(index).getByRole('definition')).toHaveText([...values])
  }
})

test('五个选项卡按状态查询，切换后回到第一页', async ({ page }) => {
  const { queries } = await mockCouponOrders(page)
  await page.goto('/orders/coupons')
  await expect(page.getByRole('tab')).toHaveText([
    '全部订单',
    '待支付',
    '已支付',
    '已退款',
    '已关闭',
  ])
  await expect(page.getByRole('article')).toHaveCount(6)
  expect(queries.at(-1)).toEqual({ page: 1, pageSize: 6, status: null })
  await page.getByRole('navigation', { name: '订单分页' }).locator('.btn-next').click()
  await expect(page.getByRole('article')).toHaveCount(1)
  for (const [tab, count, status] of [
    ['待支付', 1, '0'],
    ['已支付', 3, '1'],
    ['已退款', 1, '2'],
    ['已关闭', 2, '3'],
  ] as const) {
    await page.getByRole('tab', { name: tab, exact: true }).click()
    await expect(page.getByRole('article')).toHaveCount(count)
    expect(queries.at(-1)).toEqual({ page: 1, pageSize: 6, status })
    await expect(page.getByRole('navigation', { name: '订单分页' })).toHaveCount(0)
  }
})

test('详情读取完整信息：已支付显示券号，退款与关闭显示原因，待支付显示支付截止', async ({
  page,
}) => {
  await mockCouponOrders(page)
  await page.goto('/orders/coupons')
  const cards = page.getByRole('article')
  const dialog = page.getByRole('dialog', { name: '订单详情', exact: true })
  const close = () => dialog.getByRole('button', { name: 'Close this dialog' }).click()

  await cards.nth(1).getByRole('button', { name: '查看详情' }).click()
  await expect(dialog.getByRole('heading', { name: '已支付', exact: true })).toBeVisible()
  for (const text of [
    '午餐代金券',
    '可抵扣 ¥20.50',
    '实付金额',
    'COUPON-ORDER-0002',
    'COUPON-0002',
    '2026-11-07 10:00',
    '2026-11-07 10:01',
  ]) {
    await expect(dialog).toContainText(text)
  }
  await expect(dialog.getByTestId('coupon-order-total')).toHaveText('¥9.90')
  await close()

  await cards.nth(2).getByRole('button', { name: '查看详情' }).click()
  await expect(dialog.getByRole('heading', { name: '已退款', exact: true })).toBeVisible()
  for (const text of ['券号', '退款时间', '2026-11-08 09:30', '退款原因', '用户申请退款']) {
    await expect(dialog).toContainText(text)
  }
  await close()

  await cards.nth(3).getByRole('button', { name: '查看详情' }).click()
  await expect(dialog.getByRole('heading', { name: '已关闭', exact: true })).toBeVisible()
  for (const text of ['关闭时间', '2026-11-07 10:16', '关闭原因', '超时未支付']) {
    await expect(dialog).toContainText(text)
  }
  await expect(dialog.getByText('券号', { exact: true })).toHaveCount(0)
  await close()

  await cards.nth(0).getByRole('button', { name: '查看详情' }).click()
  await expect(dialog.getByRole('heading', { name: '待支付', exact: true })).toBeVisible()
  for (const text of ['支付截止', '2026-11-07 10:15', '应付金额']) {
    await expect(dialog).toContainText(text)
  }
  await expect(dialog.getByText('券号', { exact: true })).toHaveCount(0)
  await expect(dialog.getByText('支付时间', { exact: true })).toHaveCount(0)
  await expect(page.locator('.user-dialog-overlay:visible')).toHaveCSS(
    'backdrop-filter',
    'blur(6px)',
  )
})

test('卡片和详情都能联系商家，显示明确的演示手机号', async ({ page }) => {
  await mockCouponOrders(page)
  await page.goto('/orders/coupons')
  await page.getByRole('article').first().getByRole('button', { name: '联系商家' }).click()
  const contact = page.getByRole('dialog', { name: '联系商家', exact: true })
  await expect(contact).toContainText('演示手机号')
  await expect(contact.getByRole('link', { name: '138 0000 0000' })).toHaveAttribute(
    'href',
    'tel:13800000000',
  )
  await contact.getByRole('button', { name: '知道了', exact: true }).click()
  await expect(contact).toBeHidden()
  await page.getByRole('article').first().getByRole('button', { name: '查看详情' }).click()
  const detail = page.getByRole('dialog', { name: '订单详情', exact: true })
  await detail.getByRole('button', { name: '联系商家' }).click()
  await expect(contact).toBeVisible()
})

test('右上角并排提供查看外卖订单和抢优惠券入口', async ({ page }) => {
  await mockCouponOrders(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/orders/coupons')
  const takeout = page.getByRole('button', { name: '查看外卖订单', exact: true })
  const coupons = page.getByRole('button', { name: '抢优惠券', exact: true })
  const takeoutBox = (await takeout.boundingBox())!
  const couponsBox = (await coupons.boundingBox())!
  expect(takeoutBox.x + takeoutBox.width).toBeLessThan(couponsBox.x)
  expect(takeoutBox.y).toBe(couponsBox.y)
  expect(couponsBox.y).toBeLessThan((await page.getByRole('tablist').boundingBox())!.y)
  await expect(takeout).toHaveCSS('background-color', 'rgb(37, 99, 235)')
  await expect(takeout).toHaveCSS('color', 'rgb(255, 255, 255)')
  await expect(coupons).toHaveCSS('color', 'rgb(82, 101, 128)')
  await coupons.click()
  await expect(page).toHaveURL('/coupons/take')
  await page.goBack()
  await page.getByRole('button', { name: '查看外卖订单', exact: true }).click()
  await expect(page).toHaveURL('/orders/takeout')
})

test('列表与详情失败可重试，空列表保留抢优惠券入口', async ({ page }) => {
  await mockCouponOrders(page, [makeCouponOrder(1, 1)])
  await page.route(
    '**/api/couponOrder/historyOrders**',
    (route) => route.fulfill({ json: { code: 0, msg: '订单暂时不可用' } }),
    { times: 1 },
  )
  await page.route(
    '**/api/couponOrder/orderDetail/1',
    (route) => route.fulfill({ json: { code: 0, msg: '订单详情暂时不可用' } }),
    { times: 1 },
  )
  await page.goto('/orders/coupons')
  await expect(page.getByText('订单暂时不可用', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '重新加载' }).click()
  await expect(page.getByRole('article')).toHaveCount(1)
  await page.getByRole('button', { name: '查看详情' }).click()
  const dialog = page.getByRole('dialog', { name: '订单详情', exact: true })
  await expect(dialog.getByText('订单详情暂时不可用')).toBeVisible()
  await dialog.getByRole('button', { name: '重新加载' }).click()
  await expect(dialog.getByRole('heading', { name: '已支付', exact: true })).toBeVisible()
  await dialog.getByRole('button', { name: 'Close this dialog' }).click()
  await page.getByRole('tab', { name: '已关闭', exact: true }).click()
  await expect(page.getByText('还没有相关订单', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '抢优惠券', exact: true })).toBeVisible()
})

test('手机端入口上下排列，订单单列，长订单号与长券名不横向溢出', async ({ page }) => {
  const order = makeCouponOrder(1, 2)
  order.number = 'a'.repeat(32)
  order.couponName = '超长的优惠券名称'.repeat(6)
  order.refundReason = '很长的退款原因'.repeat(20)
  await mockCouponOrders(page, [order, makeCouponOrder(2, 0)])
  await page.setViewportSize({ width: 375, height: 667 })
  await page.goto('/orders')
  const main = page.getByRole('main')
  const takeoutBox = (await main.getByRole('link', { name: /外卖订单/ }).boundingBox())!
  const couponBox = (await main.getByRole('link', { name: /优惠券订单/ }).boundingBox())!
  expect(couponBox.y).toBeGreaterThan(takeoutBox.y + takeoutBox.height)
  expect(couponBox.x).toBe(takeoutBox.x)
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)

  await main.getByRole('link', { name: /优惠券订单/ }).click()
  const cards = page.getByRole('article')
  await expect(cards).toHaveCount(2)
  const xs = await cards.evaluateAll((elements) =>
    elements.map((element) => element.getBoundingClientRect().x),
  )
  expect(xs[0]).toBe(xs[1])
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
  await cards.first().getByRole('button', { name: '查看详情' }).click()
  const dialog = page.getByRole('dialog', { name: '订单详情', exact: true })
  await expect(dialog.getByRole('button', { name: '联系商家' })).toBeInViewport()
  await dialog.getByText(order.refundReason!, { exact: true }).scrollIntoViewIfNeeded()
  expect((await dialog.locator('.el-dialog').boundingBox())!.width).toBeLessThanOrEqual(343)
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
})

test.describe('待支付订单去支付', () => {
  test.beforeEach(async ({ page }) => {
    await page.clock.setFixedTime(new Date('2026-11-07T10:05:00+08:00'))
  })

  test('卡片上去支付，成功后显示到账信息，列表刷新为已支付', async ({ page }) => {
    const { writes } = await mockCouponOrders(page)
    await page.goto('/orders/coupons')
    const card = page.getByRole('article').first()
    await card.getByRole('button', { name: '去支付' }).click()
    const payment = page.getByRole('dialog', { name: '代金券支付', exact: true })
    for (const text of [
      '午餐代金券',
      '可抵扣 ¥20.50',
      '¥9.90',
      'COUPON-ORDER-0001',
      '支付截止：2026-11-07 10:15',
      '剩余 10分00秒',
    ]) {
      await expect(payment).toContainText(text)
    }
    await payment.getByRole('button', { name: '确认支付', exact: true }).click()
    await expect(payment).toBeHidden()
    const success = page.getByRole('dialog', { name: '支付成功', exact: true })
    for (const text of [
      '代金券已到账',
      '实付 ¥9.90',
      '购券订单号：COUPON-ORDER-0001',
      '有效期至：2026-12-07 10:05',
    ]) {
      await expect(success).toContainText(text)
    }
    expect(writes).toEqual([
      {
        path: '/api/coupon/pay/COUPON-ORDER-0001',
        method: 'POST',
        params: { couponOrderNumber: 'COUPON-ORDER-0001' },
      },
    ])
    await success.getByRole('button', { name: '查看订单', exact: true }).click()
    await expect(success).toBeHidden()
    await expect(page).toHaveURL('/orders/coupons')
    await expect(card).toContainText('已支付')
    await expect(card.getByRole('term')).toHaveText(['支付时间'])
    await expect(card.getByRole('definition')).toHaveText(['2026-11-07 10:05'])
    await expect(card.getByRole('button', { name: '去支付' })).toHaveCount(0)
  })

  test('详情中去支付，成功后详情刷新为已支付并显示券号', async ({ page }) => {
    await mockCouponOrders(page)
    await page.goto('/orders/coupons')
    await page.getByRole('article').first().getByRole('button', { name: '查看详情' }).click()
    const detail = page.getByRole('dialog', { name: '订单详情', exact: true })
    await expect(detail.getByRole('heading', { name: '待支付', exact: true })).toBeVisible()
    await detail.getByRole('button', { name: '去支付' }).click()
    await page
      .getByRole('dialog', { name: '代金券支付', exact: true })
      .getByRole('button', { name: '确认支付', exact: true })
      .click()
    await page
      .getByRole('dialog', { name: '支付成功', exact: true })
      .getByRole('button', { name: '查看订单', exact: true })
      .click()
    await expect(detail.getByRole('heading', { name: '已支付', exact: true })).toBeVisible()
    await expect(detail).toContainText('COUPON-0001')
    await expect(detail).toContainText('2026-11-07 10:05')
    await expect(detail.getByRole('button', { name: '去支付' })).toHaveCount(0)
  })

  test('暂不支付不发起请求；支付失败保留弹窗并提示后端错误，可重试', async ({ page }) => {
    const { writes } = await mockCouponOrders(page)
    await page.route(
      '**/api/coupon/pay/**',
      (route) =>
        route.fulfill({ json: { code: 0, msg: '代金券订单已超过支付截止时间', data: null } }),
      { times: 1 },
    )
    await page.goto('/orders/coupons')
    const card = page.getByRole('article').first()
    const payment = page.getByRole('dialog', { name: '代金券支付', exact: true })
    await card.getByRole('button', { name: '去支付' }).click()
    await payment.getByRole('button', { name: '暂不支付', exact: true }).click()
    await expect(payment).toBeHidden()
    expect(writes).toEqual([])
    await card.getByRole('button', { name: '去支付' }).click()
    await payment.getByRole('button', { name: '确认支付', exact: true }).click()
    await expect(page.getByText('代金券订单已超过支付截止时间', { exact: true })).toBeVisible()
    await expect(payment).toBeVisible()
    await expect(page.getByRole('dialog', { name: '支付成功', exact: true })).toHaveCount(0)
    await payment.getByRole('button', { name: '确认支付', exact: true }).click()
    await expect(page.getByRole('dialog', { name: '支付成功', exact: true })).toBeVisible()
    expect(writes).toHaveLength(1)
  })
})

test('超过支付截止时间后显示已截止，不能确认支付', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-11-07T10:20:00+08:00'))
  const { writes } = await mockCouponOrders(page)
  await page.goto('/orders/coupons')
  await page.getByRole('article').first().getByRole('button', { name: '去支付' }).click()
  const payment = page.getByRole('dialog', { name: '代金券支付', exact: true })
  await expect(payment).toContainText('支付已截止')
  await expect(payment.getByRole('button', { name: '确认支付', exact: true })).toBeDisabled()
  expect(writes).toEqual([])
})
