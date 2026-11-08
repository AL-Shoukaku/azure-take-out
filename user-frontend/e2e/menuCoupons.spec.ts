import { expect, test, type Page } from '@playwright/test'
import { mockMenu } from './helpers/menu'
import { makeUserCoupon, mockUserCoupons } from './helpers/userCoupons'

test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-11-08T10:00:00+08:00'))
})

const fixtures = [
  { ...makeUserCoupon(1, 0), name: '五元代金券', discountAmount: 5 },
  { ...makeUserCoupon(2, 0), name: '满十五减四', thresholdAmount: 15, discountAmount: 4 },
  { ...makeUserCoupon(3, 0), name: '已过期但状态未更新', expireTime: '2020-01-01 10:00' },
  { ...makeUserCoupon(4, 0), name: '满五十减十', thresholdAmount: 50, discountAmount: 10 },
  { ...makeUserCoupon(5, 2), name: '已使用券' },
]
const bag = (page: Page) => page.getByRole('complementary', { name: '我的餐袋' })
const picker = (page: Page) => page.getByRole('dialog', { name: '选择优惠券', exact: true })
async function selectCoupon(page: Page, name: string) {
  await picker(page).getByText(name, { exact: true }).click()
  await expect(
    picker(page).getByRole('checkbox', { name: `选择${name}`, exact: true }),
  ).toBeChecked()
}
async function addDish(page: Page) {
  await page.getByRole('button', { name: '添加家常菜2', exact: true }).click()
  await expect(bag(page).locator('.cart-total')).toContainText('¥23.00')
}

test('叠加用券提交用户券ID，支付与成功页面使用后端折后金额', async ({ page }) => {
  // 刻意与前端预计14元不同，验证付款以后端金额为准。
  const api = await mockMenu(page, { orderAmount: 13 })
  const coupons = await mockUserCoupons(page, fixtures)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/menu')
  await addDish(page)
  const button = bag(page).getByRole('button', { name: '选择优惠券', exact: true })
  await expect(bag(page).locator('.coupon-picker-badge .el-badge__content')).toBeHidden()
  await expect(button).toHaveClass(/el-button--primary/)
  const titleBox = await bag(page)
    .getByRole('heading', { name: /我的餐袋/ })
    .boundingBox()
  const buttonBox = await button.boundingBox()
  expect(buttonBox!.x).toBeGreaterThanOrEqual(titleBox!.x + titleBox!.width)
  await button.click()
  await expect(picker(page).getByRole('article')).toHaveCount(4)
  expect(coupons.queries.at(-1)).toEqual({ page: 1, pageSize: 6, status: '0' })
  await expect(
    picker(page).getByRole('checkbox', { name: '选择已过期但状态未更新', exact: true }),
  ).toBeDisabled()
  await expect(
    picker(page).getByRole('checkbox', { name: '选择满五十减十', exact: true }),
  ).toBeDisabled()
  await expect(picker(page)).toContainText('餐品金额还差 ¥34.00')
  await expect(picker(page)).not.toContainText('已使用券')
  const boxes = await picker(page)
    .getByRole('article')
    .evaluateAll((elements) =>
      elements.map((el) => ({ x: el.getBoundingClientRect().x, y: el.getBoundingClientRect().y })),
    )
  expect(boxes[1]!.x).toBe(boxes[0]!.x)
  expect(boxes[1]!.y).toBeGreaterThan(boxes[0]!.y)
  await selectCoupon(page, '五元代金券')
  await selectCoupon(page, '满十五减四')
  await expect(picker(page)).toContainText('已选 2 张 · 预计优惠 ¥9.00')
  await page.screenshot({
    path: test.info().outputPath('coupon-picker-desktop.png'),
    fullPage: true,
    animations: 'disabled',
  })
  await picker(page).getByRole('button', { name: '确认选择', exact: true }).click()
  await expect(bag(page).locator('.coupon-picker-badge .el-badge__content')).toHaveText('2')
  await expect(bag(page).locator('.coupon-picker-badge .el-badge__content')).toBeVisible()
  await expect(bag(page).locator('.cart-total')).toContainText(/预计合计\s*¥14\.00/)
  await expect(bag(page)).toContainText('优惠金额以下单结果为准。')
  await page.getByRole('button', { name: '去结算' }).click()
  const checkout = page.getByRole('dialog', { name: '确认订单', exact: true })
  await expect(checkout.locator('.checkout-footer')).toContainText('¥14.00')
  await expect(checkout).toContainText('优惠券优惠（2 张）')
  await expect(checkout).toContainText('提交订单时锁定所选优惠券')
  await checkout.getByRole('button', { name: '提交订单' }).click()
  const payment = page.getByRole('dialog', { name: '订单支付', exact: true })
  await expect(payment).toContainText('¥13.00')
  expect(api.submissions).toHaveLength(1)
  expect(api.submissions[0]).toHaveProperty('userCouponIds', [1, 2])
  for (const field of ['amount', 'couponId', 'userCouponId', 'couponIds', 'discountAmount'])
    expect(api.submissions[0]).not.toHaveProperty(field)
  expect(coupons.details).toEqual([])
  await expect(bag(page).locator('.coupon-picker-badge .el-badge__content')).toBeHidden()
  await payment.getByRole('button', { name: '确认支付', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '支付成功', exact: true })).toContainText('¥13.00')
  expect(api.payments).toEqual([{ orderNumber: 'TEST-99', payMethod: 1 }])
})

test('分页保留多选，取消不覆盖原选择，确认清空恢复原价', async ({ page }) => {
  await mockMenu(page)
  const api = await mockUserCoupons(
    page,
    Array.from({ length: 8 }, (_, i) => ({
      ...makeUserCoupon(i + 1, 0),
      name: `优惠券${i + 1}`,
      type: 1 as const,
      thresholdAmount: 0,
      discountAmount: 1,
    })),
  )
  await page.goto('/menu')
  await addDish(page)
  await bag(page).getByRole('button', { name: '选择优惠券', exact: true }).click()
  await selectCoupon(page, '优惠券1')
  await picker(page).locator('.btn-next').click()
  await selectCoupon(page, '优惠券7')
  expect(api.queries.at(-1)?.page).toBe(2)
  await picker(page).locator('.btn-prev').click()
  await expect(
    picker(page).getByRole('checkbox', { name: '选择优惠券1', exact: true }),
  ).toBeChecked()
  await picker(page).getByRole('button', { name: '确认选择', exact: true }).click()
  await expect(bag(page).locator('.cart-total')).toContainText('¥21.00')
  await bag(page).getByRole('button', { name: '选择优惠券', exact: true }).click()
  await picker(page).getByRole('button', { name: '清空选择', exact: true }).click()
  await picker(page).getByRole('button', { name: '取消', exact: true }).click()
  await expect(bag(page).locator('.cart-total')).toContainText('¥21.00')
  await bag(page).getByRole('button', { name: '选择优惠券', exact: true }).click()
  await picker(page).getByRole('button', { name: '清空选择', exact: true }).click()
  await picker(page).getByRole('button', { name: '确认选择', exact: true }).click()
  await expect(bag(page).locator('.cart-total')).toContainText('¥23.00')
  await expect(bag(page).locator('.coupon-picker-badge .el-badge__content')).toBeHidden()
})

test('数量变化重新判断门槛，清空餐袋移除优惠试算', async ({ page }) => {
  await mockMenu(page)
  await mockUserCoupons(page, [
    { ...makeUserCoupon(2, 0), name: '满三十减八', thresholdAmount: 30, discountAmount: 8 },
  ])
  await page.goto('/menu')
  await addDish(page)
  await bag(page).getByRole('button', { name: '增加家常菜2', exact: true }).click()
  await expect(bag(page).locator('.cart-total')).toContainText('¥40.00')
  await bag(page).getByRole('button', { name: '选择优惠券', exact: true }).click()
  await selectCoupon(page, '满三十减八')
  await picker(page).getByRole('button', { name: '确认选择', exact: true }).click()
  await expect(bag(page).locator('.cart-total')).toContainText('¥32.00')
  await bag(page).getByRole('button', { name: '减少家常菜2', exact: true }).click()
  await expect(bag(page).locator('.cart-total')).toContainText('¥23.00')
  await expect(bag(page)).not.toContainText('优惠券优惠')
  await bag(page).getByRole('button', { name: '清空餐袋', exact: true }).click()
  await page.getByRole('button', { name: '确认清空', exact: true }).click()
  await expect(bag(page).locator('.cart-total')).toContainText('¥0.00')
})

test('读取失败可以重试，空列表可关闭；未选餐品时只展示不允许勾选', async ({ page }) => {
  await mockMenu(page)
  await mockUserCoupons(page, fixtures)
  await page.route(
    '**/api/userCoupon/page?**',
    (route) => route.fulfill({ json: { code: 0, msg: '优惠券暂不可用', data: null } }),
    { times: 1 },
  )
  await page.goto('/menu')
  await bag(page).getByRole('button', { name: '选择优惠券', exact: true }).click()
  await expect(picker(page)).toContainText('优惠券暂不可用')
  await expect(picker(page).getByRole('button', { name: '确认选择', exact: true })).toBeDisabled()
  await picker(page).getByRole('button', { name: '重新加载', exact: true }).click()
  await expect(picker(page)).toContainText('请先添加餐品')
  await expect(
    picker(page).getByRole('checkbox', { name: '选择五元代金券', exact: true }),
  ).toBeDisabled()
  await picker(page).getByRole('button', { name: '取消', exact: true }).click()
  await page.route('**/api/userCoupon/page?**', (route) =>
    route.fulfill({ json: { code: 1, data: { total: 0, records: [] } } }),
  )
  await bag(page).getByRole('button', { name: '选择优惠券', exact: true }).click()
  await expect(picker(page)).toContainText('暂无未使用的优惠券')
  await picker(page).getByRole('button', { name: '确认选择', exact: true }).click()
  await expect(picker(page)).toBeHidden()
})

test('手机弹窗可勾选确认，优惠过期后自动恢复价格且不横向溢出', async ({ page }) => {
  await mockMenu(page)
  await page.clock.setFixedTime(new Date('2026-11-08T10:00:00+08:00'))
  await mockUserCoupons(page, [
    {
      ...makeUserCoupon(1, 0),
      name: '限时五元券',
      discountAmount: 5,
      expireTime: '2026-11-08 10:01',
    },
  ])
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/menu')
  await addDish(page)
  await bag(page).getByRole('button', { name: '选择优惠券', exact: true }).click()
  await selectCoupon(page, '限时五元券')
  await expect(picker(page).getByRole('button', { name: '确认选择', exact: true })).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
  await page.screenshot({
    path: test.info().outputPath('coupon-picker-mobile.png'),
    fullPage: true,
    animations: 'disabled',
  })
  await picker(page).getByRole('button', { name: '确认选择', exact: true }).click()
  await expect(bag(page).locator('.cart-total')).toContainText('¥18.00')
  await page.clock.setFixedTime(new Date('2026-11-08T10:01:00+08:00'))
  await expect(bag(page).locator('.cart-total')).toContainText('¥23.00')
  await expect(bag(page)).not.toContainText('优惠券优惠')
})

test('后端拒绝已锁定优惠券时保留餐袋和选择，允许取消用券后重新提交', async ({ page }) => {
  const api = await mockMenu(page)
  await mockUserCoupons(page, fixtures)
  await page.route(
    '**/api/order/submit',
    (route) =>
      route.fulfill({
        json: { code: 0, msg: '存在状态不为未使用的优惠券，请重新下单', data: null },
      }),
    { times: 1 },
  )
  await page.goto('/menu')
  await addDish(page)
  await bag(page).getByRole('button', { name: '选择优惠券', exact: true }).click()
  await selectCoupon(page, '五元代金券')
  await picker(page).getByRole('button', { name: '确认选择', exact: true }).click()
  await page.getByRole('button', { name: '去结算' }).click()
  const checkout = page.getByRole('dialog', { name: '确认订单', exact: true })
  await checkout.getByRole('button', { name: '提交订单' }).click()
  await expect(
    page.getByText('存在状态不为未使用的优惠券，请重新下单', { exact: true }),
  ).toBeVisible()
  await expect(checkout).toBeVisible()
  await expect(bag(page).locator('.cart-total')).toContainText('¥18.00')
  await expect(page.getByRole('dialog', { name: '订单支付', exact: true })).toHaveCount(0)
  await checkout.getByRole('button', { name: '关闭此对话框' }).click()
  await bag(page).getByRole('button', { name: '选择优惠券', exact: true }).click()
  await picker(page).getByRole('button', { name: '清空选择', exact: true }).click()
  await picker(page).getByRole('button', { name: '确认选择', exact: true }).click()
  await page.getByRole('button', { name: '去结算' }).click()
  await checkout.getByRole('button', { name: '提交订单' }).click()
  await expect(page.getByRole('dialog', { name: '订单支付', exact: true })).toContainText('¥23.00')
  expect(api.submissions[0]).not.toHaveProperty('userCouponIds')
})
