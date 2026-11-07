import { expect, test, type Page } from '@playwright/test'
import { restoreSession } from './helpers/session'

test.use({ timezoneId: 'America/New_York' })

const coupons = [
  {
    id: 1,
    name: '午餐代金券',
    type: 1,
    stock: 80,
    discountAmount: 20.5,
    thresholdAmount: 0,
    price: 9.9,
    startTime: '2026-11-07 09:00',
    endTime: '2026-11-07 12:00',
  },
  {
    id: 2,
    name: '双人满减券',
    type: 2,
    stock: 120,
    discountAmount: 15,
    thresholdAmount: 60,
    price: 0,
    startTime: '2026-11-07 09:00',
    endTime: '2026-11-07 12:00',
  },
  {
    id: 3,
    name: '限时优惠券',
    type: 1,
    stock: 20,
    discountAmount: 10,
    thresholdAmount: 0,
    price: 0,
    startTime: '2026-11-07 10:01',
    endTime: '2026-11-07 10:02',
  },
  {
    id: 4,
    name: '无门槛满减券',
    type: 2,
    stock: 30,
    discountAmount: 5,
    thresholdAmount: 0,
    price: 0,
    startTime: '2026-11-07 09:00',
    endTime: '2026-11-07 12:00',
  },
]
const card = (page: Page, name: string) => page.getByRole('article', { name, exact: true })

test.beforeEach(async ({ page }) => {
  await restoreSession(page)
  await page.clock.setFixedTime(new Date('2026-11-07T10:00:00+08:00'))
  await page.route('**/api/coupon/available', (route) => {
    expect(route.request().method()).toBe('GET')
    expect(route.request().headers().authentication).toBe('e2e-user-token')
    return route.fulfill({ json: { code: 1, data: coupons } })
  })
})

test('导航进入优惠券页，展示类型、优惠方式、时间、库存和购买价格', async ({ page }) => {
  await page.goto('/menu')
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '抢优惠券', exact: true })
    .click()
  await expect(page).toHaveURL('/coupons')
  const voucher = card(page, '午餐代金券')
  await expect(voucher).toContainText('代金券')
  await expect(voucher).toContainText('可抵扣 ¥20.50')
  await expect(voucher).toContainText('2026-11-07 09:00')
  await expect(voucher).toContainText('2026-11-07 12:00')
  await expect(voucher).toContainText('剩余 80 张')
  await expect(voucher.getByRole('button', { name: '¥9.90 购买', exact: true })).toBeEnabled()
  await expect(card(page, '双人满减券')).toContainText('满 ¥60.00 减 ¥15.00')
  await expect(card(page, '无门槛满减券')).toContainText('无门槛减 ¥5.00')
  await expect(card(page, '限时优惠券')).toContainText('未开始')
  await expect(
    card(page, '限时优惠券').getByRole('button', { name: '¥0.00 购买', exact: true }),
  ).toBeDisabled()
  await page.screenshot({ path: test.info().outputPath('coupon-desktop.png'), fullPage: true })
})

test('领取购买仅显示未开放提示，不调用接口或显示下单弹窗', async ({ page }) => {
  const writes: string[] = []
  await page.route(/\/api\/coupon\/(get|buy)/, (route) => {
    writes.push(route.request().url())
    return route.fulfill({ json: { code: 0, msg: '功能未实现', data: null } })
  })
  await page.goto('/coupons')
  await card(page, '午餐代金券').getByRole('button', { name: '¥9.90 购买', exact: true }).click()
  await expect(
    page.getByRole('alert').filter({ hasText: '优惠券购买暂未开放，敬请期待' }),
  ).toBeVisible()
  await card(page, '双人满减券').getByRole('button', { name: '立即领取', exact: true }).click()
  await expect(
    page.getByRole('alert').filter({ hasText: '优惠券领取暂未开放，敬请期待' }),
  ).toBeVisible()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page).toHaveURL('/coupons')
  expect(writes).toHaveLength(0)
})

test('活动开始结束后自动更新状态，时间按北京时间解释', async ({ page }) => {
  await page.goto('/coupons')
  const upcoming = card(page, '限时优惠券')
  await expect(upcoming).toContainText('未开始')
  await page.clock.setFixedTime(new Date('2026-11-07T10:01:00+08:00'))
  await expect(upcoming).toContainText('进行中')
  await expect(upcoming.getByRole('button')).toBeEnabled()
  await page.clock.setFixedTime(new Date('2026-11-07T10:02:00+08:00'))
  await expect(upcoming).toContainText('已结束')
  await expect(upcoming.getByRole('button')).toBeDisabled()
})

test('加载失败可重试，空列表和库存耗尽有明确状态', async ({ page }) => {
  await page.route('**/api/coupon/available', (route) =>
    route.fulfill({ json: { code: 0, msg: '列表暂不可用', data: null } }),
  )
  await page.goto('/coupons')
  await expect(page.getByRole('alert')).toContainText('列表暂不可用')
  await page.route('**/api/coupon/available', (route) =>
    route.fulfill({ json: { code: 1, data: [] } }),
  )
  await page.getByRole('button', { name: '重新加载', exact: true }).click()
  await expect(page.getByRole('heading', { name: '暂无在售优惠券', exact: true })).toBeVisible()
  await page.route('**/api/coupon/available', (route) =>
    route.fulfill({ json: { code: 1, data: [{ ...coupons[0], stock: 0 }] } }),
  )
  await page.getByRole('button', { name: '刷新优惠券', exact: true }).click()
  await expect(card(page, '午餐代金券')).toContainText('已抢完')
  await expect(card(page, '午餐代金券').getByRole('button')).toBeDisabled()
})

test('手机布局可浏览完整优惠券与五个导航入口，刷新后保留页面', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/coupons')
  await expect(card(page, '午餐代金券')).toBeVisible()
  const navigation = page.getByRole('navigation', { name: '主导航' })
  await expect(navigation.getByRole('link')).toHaveText([
    '在线点餐',
    '抢优惠券',
    '我的订单',
    '收货地址',
    '我的',
  ])
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
  await page.screenshot({ path: test.info().outputPath('coupon-mobile.png'), fullPage: true })
  await page.reload()
  await expect(navigation.getByRole('link', { name: '抢优惠券', exact: true })).toHaveAttribute(
    'aria-current',
    'page',
  )
  await expect(card(page, '午餐代金券')).toBeVisible()
})
