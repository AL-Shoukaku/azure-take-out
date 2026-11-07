import { expect, test, type Page } from '@playwright/test'
import { restoreSession } from './helpers/session'
import { mockCouponCheckout } from './helpers/coupons'

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

test('领取满减券后显示真实券号和有效期，刷新库存并禁止重复领取', async ({ page }) => {
  const api = await mockCouponCheckout(page, coupons)
  await page.goto('/coupons')
  await card(page, '双人满减券').getByRole('button', { name: '立即领取', exact: true }).click()
  const success = page.getByRole('dialog', { name: '领取成功', exact: true })
  await expect(success).toContainText('双人满减券')
  await expect(success).toContainText('满 ¥60.00 减 ¥15.00')
  await expect(success).toContainText('券号：CLAIM-2')
  await expect(success).toContainText('有效期至：2026-12-07 10:00')
  await expect(success.getByRole('img', { name: '领取成功插画' })).toHaveJSProperty(
    'naturalWidth',
    640,
  )
  await page.screenshot({
    path: test.info().outputPath('coupon-claim-success.png'),
    fullPage: true,
    animations: 'disabled',
  })
  await success.getByRole('button', { name: '继续领券', exact: true }).click()
  await expect(card(page, '双人满减券')).toContainText('剩余 119 张')
  await expect(
    card(page, '双人满减券').getByRole('button', { name: '已领取', exact: true }),
  ).toBeDisabled()
  expect(api.writes).toEqual([
    { path: '/api/coupon/get', method: 'POST', params: { couponId: '2' }, body: null },
  ])
  await expect(page).toHaveURL('/coupons')
})

test('代金券支付成功显示后端返回的实付金额、订单号和过期时间', async ({ page }) => {
  const api = await mockCouponCheckout(page, coupons)
  await page.goto('/coupons')
  await card(page, '午餐代金券').getByRole('button', { name: '¥9.90 购买', exact: true }).click()
  const payment = page.getByRole('dialog', { name: '代金券支付', exact: true })
  await expect(payment).toContainText('午餐代金券')
  await expect(payment).toContainText('¥8.80')
  await expect(payment).toContainText('COUPON-ORDER-1')
  await expect(payment).toContainText('支付截止：2026-11-07 10:15')
  await expect(payment).toContainText('模拟支付')
  await expect(payment.getByRole('timer')).toContainText('15分00秒')
  await page.screenshot({
    path: test.info().outputPath('coupon-payment.png'),
    fullPage: true,
    animations: 'disabled',
  })
  await payment.getByRole('button', { name: '确认支付', exact: true }).click()
  await expect(payment).toBeHidden()
  const success = page.getByRole('dialog', { name: '支付成功', exact: true })
  await expect(success).toContainText('代金券已到账')
  await expect(success).toContainText('实付 ¥8.80')
  await expect(success).toContainText('购券订单号：COUPON-ORDER-1')
  await expect(success).toContainText('有效期至：2026-12-09 18:30')
  await expect(page.getByRole('region', { name: '待支付代金券订单', exact: true })).toHaveCount(0)
  await page.screenshot({
    path: test.info().outputPath('coupon-payment-success.png'),
    fullPage: true,
    animations: 'disabled',
  })
  expect(api.writes).toEqual([
    { path: '/api/coupon/buy', method: 'POST', params: { couponId: '1' }, body: null },
    {
      path: '/api/coupon/pay/COUPON-ORDER-1',
      method: 'POST',
      params: { couponOrderNumber: 'COUPON-ORDER-1' },
      body: null,
    },
  ])
  await success.getByRole('button', { name: '去点餐', exact: true }).click()
  await expect(page).toHaveURL('/menu')
})

test('暂不支付后返回抢券列表，刷新也不展示继续支付入口', async ({ page }) => {
  const api = await mockCouponCheckout(page, coupons)
  await page.goto('/coupons')
  await card(page, '午餐代金券').getByRole('button', { name: '¥9.90 购买', exact: true }).click()
  await page
    .getByRole('dialog', { name: '代金券支付', exact: true })
    .getByRole('button', { name: '暂不支付', exact: true })
    .click()
  await expect(
    card(page, '午餐代金券').getByRole('button', { name: '已下单', exact: true }),
  ).toBeDisabled()
  await expect(page.getByRole('button', { name: /继续支付/ })).toHaveCount(0)
  await expect(page.getByRole('region', { name: '待支付代金券订单', exact: true })).toHaveCount(0)
  await page.reload()
  await expect(card(page, '午餐代金券')).toBeVisible()
  await expect(page.getByRole('button', { name: /继续支付/ })).toHaveCount(0)
  await expect(page.getByRole('dialog', { name: '代金券支付', exact: true })).toHaveCount(0)
  expect(api.writes.filter((write) => write.path === '/api/coupon/buy')).toHaveLength(1)
  expect(
    await page.evaluate(() => sessionStorage.getItem('azure-pending-coupon-orders:7')),
  ).toBeNull()
})

test('领取与下单失败不显示成功页，保留后端错误并支持重试', async ({ page }) => {
  const api = await mockCouponCheckout(page, coupons)
  api.failures.get = '用户已领取过该满减券'
  api.failures.buy = '优惠券库存不足'
  await page.goto('/coupons')
  await card(page, '双人满减券').getByRole('button', { name: '立即领取', exact: true }).click()
  await expect(page.getByRole('alert').filter({ hasText: api.failures.get })).toBeVisible()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await card(page, '午餐代金券').getByRole('button', { name: '¥9.90 购买', exact: true }).click()
  await expect(page.getByRole('alert').filter({ hasText: api.failures.buy })).toBeVisible()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  api.failures.get = ''
  await card(page, '双人满减券').getByRole('button', { name: '立即领取', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '领取成功', exact: true })).toBeVisible()
})

test('支付失败保留订单重试，支付期间阻止重复提交和导航关闭', async ({ page }) => {
  const api = await mockCouponCheckout(page, coupons)
  await page.goto('/coupons')
  await card(page, '午餐代金券').getByRole('button', { name: '¥9.90 购买', exact: true }).click()
  const payment = page.getByRole('dialog', { name: '代金券支付', exact: true })
  api.failures.pay = '支付失败，请稍后重试'
  await payment.getByRole('button', { name: '确认支付', exact: true }).click()
  await expect(page.getByRole('alert').filter({ hasText: api.failures.pay })).toBeVisible()
  await expect(payment).toBeVisible()
  await expect(page.getByRole('dialog', { name: '支付成功', exact: true })).toHaveCount(0)
  api.failures.pay = ''
  api.delay = 'pay'
  await payment.getByRole('button', { name: '确认支付', exact: true }).click()
  await expect(payment.getByRole('button', { name: '确认支付', exact: true })).toBeDisabled()
  await expect(payment.getByRole('button', { name: '暂不支付', exact: true })).toBeDisabled()
  await expect(payment.getByRole('button', { name: 'Close this dialog' })).toHaveCount(0)
  await page.keyboard.press('Escape')
  await expect(payment).toBeVisible()
  await page.evaluate(() => {
    document.querySelector<HTMLAnchorElement>('.main-navigation a')!.click()
  })
  await expect(page).toHaveURL('/coupons')
  expect(api.writes.filter((write) => write.path.includes('/pay/'))).toHaveLength(2)
  api.release()
  await expect(page.getByRole('dialog', { name: '支付成功', exact: true })).toBeVisible()
})

test('领取请求期间锁定按钮和导航，避免重复领取', async ({ page }) => {
  const api = await mockCouponCheckout(page, coupons)
  api.delay = 'get'
  await page.goto('/coupons')
  const button = card(page, '双人满减券').getByRole('button', { name: '立即领取', exact: true })
  await button.click()
  await expect(button).toBeDisabled()
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '在线点餐', exact: true })
    .click()
  await expect(page).toHaveURL('/coupons')
  expect(api.writes).toHaveLength(1)
  api.release()
  await expect(page.getByRole('dialog', { name: '领取成功', exact: true })).toBeVisible()
})

test('支付截止时间到达后禁用确认支付，手机弹窗不横向溢出', async ({ page }) => {
  const api = await mockCouponCheckout(page, coupons)
  await page.setViewportSize({ width: 375, height: 667 })
  await page.goto('/coupons')
  await card(page, '午餐代金券').getByRole('button', { name: '¥9.90 购买', exact: true }).click()
  const payment = page.getByRole('dialog', { name: '代金券支付', exact: true })
  await expect(payment.getByRole('button', { name: '确认支付', exact: true })).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
  await page.screenshot({
    path: test.info().outputPath('coupon-payment-mobile.png'),
    fullPage: true,
    animations: 'disabled',
  })
  await page.clock.setFixedTime(new Date('2026-11-07T10:15:00+08:00'))
  await expect(payment.getByRole('timer')).toContainText('支付已截止')
  await expect(payment.getByRole('button', { name: '确认支付', exact: true })).toBeDisabled()
  expect(api.writes).toHaveLength(1)
  await payment.getByRole('button', { name: '暂不支付', exact: true }).click()
  await expect(page.getByRole('button', { name: /继续支付/ })).toHaveCount(0)
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
