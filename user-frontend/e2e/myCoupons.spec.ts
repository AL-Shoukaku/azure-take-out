import { expect, test, type Page } from '@playwright/test'
import { restoreSession } from './helpers/session'
import { makeUserCoupon, mockUserCoupons } from './helpers/userCoupons'

test.beforeEach(async ({ page }) => restoreSession(page))

const couponsNavigation = (page: Page) =>
  page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '优惠券', exact: true })

test('优惠券板块先显示两个入口，右侧进入我的优惠券，导航保持选中', async ({ page }) => {
  await mockUserCoupons(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/menu')
  await couponsNavigation(page).click()
  await expect(page).toHaveURL('/coupons')
  await expect(page.getByRole('heading', { name: '优惠券', exact: true })).toBeVisible()
  const main = page.getByRole('main')
  const take = main.getByRole('link', { name: /抢优惠券/ })
  const mine = main.getByRole('link', { name: /我的优惠券/ })
  const takeBox = (await take.boundingBox())!
  const mineBox = (await mine.boundingBox())!
  expect(takeBox.x + takeBox.width).toBeLessThan(mineBox.x)
  expect(takeBox.y).toBe(mineBox.y)
  expect(takeBox.height).toBeGreaterThanOrEqual(400)
  await mine.click()
  await expect(page).toHaveURL('/coupons/mine')
  await expect(page.getByRole('heading', { name: '我的优惠券', exact: true })).toBeVisible()
  await expect(couponsNavigation(page)).toHaveClass(/is-active/)
  await page.goBack()
  await take.click()
  await expect(page).toHaveURL('/coupons/take')
  await expect(page.getByRole('heading', { name: '抢优惠券', exact: true })).toBeVisible()
  await expect(couponsNavigation(page)).toHaveClass(/is-active/)
})

test('桌面三列两行，每页六条，卡片展示券号、获得时间、券名、优惠方式、状态和时间', async ({
  page,
}) => {
  await mockUserCoupons(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/coupons/mine')
  await expect(page.getByText('每一张券，都是实打实的优惠。', { exact: true })).toBeVisible()
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
    'COUPON-0001',
    '午餐代金券',
    '可抵扣 ¥15.00',
    '未使用',
    '有效期至',
    '2026-12-07 10:00',
  ]) {
    await expect(cards.first()).toContainText(text)
  }
  await expect(cards.first().getByRole('button', { name: '查看详情' })).toBeVisible()
  await page.getByRole('navigation', { name: '优惠券分页' }).locator('.btn-next').click()
  await expect(cards).toHaveCount(1)
  await expect(cards.first()).toContainText('COUPON-0007')
})

test('六个选项卡按状态查询，切换后回到第一页', async ({ page }) => {
  const { queries } = await mockUserCoupons(page)
  await page.goto('/coupons/mine')
  await expect(page.getByRole('tab')).toHaveText([
    '全部优惠券',
    '未使用',
    '锁定中',
    '已使用',
    '已过期',
    '已退款',
  ])
  await expect(page.getByRole('article')).toHaveCount(6)
  expect(queries.at(-1)).toEqual({ page: 1, pageSize: 6, status: null })
  await page.getByRole('navigation', { name: '优惠券分页' }).locator('.btn-next').click()
  await expect(page.getByRole('article')).toHaveCount(1)
  for (const [tab, count, status] of [
    ['未使用', 3, '0'],
    ['锁定中', 1, '1'],
    ['已使用', 1, '2'],
    ['已过期', 1, '3'],
    ['已退款', 1, '4'],
  ] as const) {
    await page.getByRole('tab', { name: tab, exact: true }).click()
    await expect(page.getByRole('article')).toHaveCount(count)
    expect(queries.at(-1)).toEqual({ page: 1, pageSize: 6, status })
    await expect(page.getByRole('navigation', { name: '优惠券分页' })).toHaveCount(0)
  }
})

test('卡片按状态显示有效期、核销或退款时间，过期券显示已过期', async ({ page }) => {
  await mockUserCoupons(page)
  await page.goto('/coupons/mine')
  const cards = page.getByRole('article')
  for (const [index, status, terms, values] of [
    [0, '未使用', ['有效期至'], ['2026-12-07 10:00']],
    [2, '锁定中', ['有效期至'], ['2026-12-07 10:00']],
    [3, '已使用', ['核销时间'], ['2026-11-20 12:30']],
    [4, '已过期', ['过期时间'], ['2026-12-07 10:00']],
    [5, '已退款', ['退款时间'], ['2026-11-21 09:00']],
  ] as const) {
    await expect(cards.nth(index)).toContainText(status)
    await expect(cards.nth(index).getByRole('term')).toHaveText([...terms])
    await expect(cards.nth(index).getByRole('definition')).toHaveText([...values])
  }
})

test('详情读取完整信息，各状态显示对应的说明与时间，失败可重试', async ({ page }) => {
  await mockUserCoupons(page)
  await page.route(
    '**/api/userCoupon/details/4',
    (route) => route.fulfill({ json: { code: 0, msg: '优惠券详情暂时不可用' } }),
    { times: 1 },
  )
  await page.goto('/coupons/mine')
  const cards = page.getByRole('article')
  const dialog = page.getByRole('dialog', { name: '优惠券详情', exact: true })
  const close = () => dialog.getByRole('button', { name: 'Close this dialog' }).click()

  await cards.nth(3).getByRole('button', { name: '查看详情' }).click()
  await expect(dialog.getByText('优惠券详情暂时不可用', { exact: true })).toBeVisible()
  await dialog.getByRole('button', { name: '重新加载' }).click()
  await expect(dialog.getByRole('heading', { name: '已使用', exact: true })).toBeVisible()
  for (const text of [
    '双人满减券',
    '满 ¥60.00 减 ¥15.00',
    '满减券',
    'COUPON-0004',
    '2026-11-07 10:00',
    '2026-12-07 10:00',
    '核销时间',
    '2026-11-20 12:30',
  ]) {
    await expect(dialog).toContainText(text)
  }
  await expect(page.locator('.user-dialog-overlay:visible')).toHaveCSS(
    'backdrop-filter',
    'blur(6px)',
  )
  await close()

  await cards.nth(0).getByRole('button', { name: '查看详情' }).click()
  await expect(dialog.getByRole('heading', { name: '未使用', exact: true })).toBeVisible()
  await expect(dialog).toContainText('优惠券可正常使用')
  await expect(dialog.getByText('核销时间', { exact: true })).toHaveCount(0)
  await close()

  await cards.nth(4).getByRole('button', { name: '查看详情' }).click()
  await expect(dialog.getByRole('heading', { name: '已过期', exact: true })).toBeVisible()
  await expect(dialog).toContainText('已超过有效期')
  await close()

  await cards.nth(5).getByRole('button', { name: '查看详情' }).click()
  await expect(dialog.getByRole('heading', { name: '已退款', exact: true })).toBeVisible()
  await expect(dialog).toContainText('2026-11-21 09:00')
})

test('两个页面右上角互为蓝色跳转按钮并紧邻刷新按钮', async ({ page }) => {
  await mockUserCoupons(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/coupons/take')
  const toMine = page.getByRole('button', { name: '查看我的优惠券', exact: true })
  const refresh = page.getByRole('button', { name: '刷新优惠券', exact: true })
  await expect(toMine).toHaveCSS('background-color', 'rgb(37, 99, 235)')
  await expect(toMine).toHaveCSS('color', 'rgb(255, 255, 255)')
  const toMineBox = (await toMine.boundingBox())!
  const refreshBox = (await refresh.boundingBox())!
  expect(toMineBox.x + toMineBox.width).toBeLessThan(refreshBox.x)
  expect(toMineBox.y).toBe(refreshBox.y)
  await toMine.click()
  await expect(page).toHaveURL('/coupons/mine')

  const toTake = page.getByRole('button', { name: '去抢优惠券', exact: true })
  const mineRefresh = page.getByRole('button', { name: '刷新优惠券', exact: true })
  await expect(toTake).toHaveCSS('background-color', 'rgb(37, 99, 235)')
  await expect(toTake).toHaveCSS('color', 'rgb(255, 255, 255)')
  const toTakeBox = (await toTake.boundingBox())!
  const mineRefreshBox = (await mineRefresh.boundingBox())!
  expect(toTakeBox.x + toTakeBox.width).toBeLessThan(mineRefreshBox.x)
  expect(toTakeBox.y).toBe(mineRefreshBox.y)
  await mineRefresh.click()
  await expect
    .poll(() => page.evaluate(() => document.querySelectorAll('.order-grid article').length))
    .toBeGreaterThan(0)
  await toTake.click()
  await expect(page).toHaveURL('/coupons/take')
})

test('列表失败可重试，空状态保留去抢优惠券入口', async ({ page }) => {
  await mockUserCoupons(page, [makeUserCoupon(1, 0)])
  await page.route(
    '**/api/userCoupon/page**',
    (route) => route.fulfill({ json: { code: 0, msg: '优惠券暂时不可用' } }),
    { times: 1 },
  )
  await page.goto('/coupons/mine')
  await expect(page.getByText('优惠券暂时不可用', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '重新加载' }).click()
  await expect(page.getByRole('article')).toHaveCount(1)
  await page.getByRole('tab', { name: '已退款', exact: true }).click()
  await expect(page.getByText('还没有相关优惠券', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '去抢优惠券', exact: true })).toBeVisible()
})

test('手机端入口上下排列，优惠券单列，长券号与长券名不横向溢出', async ({ page }) => {
  const coupon = makeUserCoupon(1, 0)
  coupon.number = 'a'.repeat(32)
  coupon.name = '超长的优惠券名称'.repeat(6)
  await mockUserCoupons(page, [coupon, makeUserCoupon(2, 2)])
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/coupons')
  const main = page.getByRole('main')
  const takeBox = (await main.getByRole('link', { name: /抢优惠券/ }).boundingBox())!
  const mineBox = (await main.getByRole('link', { name: /我的优惠券/ }).boundingBox())!
  expect(mineBox.y).toBeGreaterThan(takeBox.y + takeBox.height)
  expect(mineBox.x).toBe(takeBox.x)
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)

  await main.getByRole('link', { name: /我的优惠券/ }).click()
  const cards = page.getByRole('article')
  await expect(cards).toHaveCount(2)
  const xs = await cards.evaluateAll((elements) =>
    elements.map((element) => element.getBoundingClientRect().x),
  )
  expect(xs[0]).toBe(xs[1])
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
  await cards.first().getByRole('button', { name: '查看详情' }).click()
  const dialog = page.getByRole('dialog', { name: '优惠券详情', exact: true })
  await dialog.getByText(coupon.number, { exact: true }).scrollIntoViewIfNeeded()
  expect((await dialog.locator('.el-dialog').boundingBox())!.width).toBeLessThanOrEqual(343)
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
})
