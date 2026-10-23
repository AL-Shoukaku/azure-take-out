import { expect, test } from '@playwright/test'
import { mockMenu } from './helpers/menu'

test('宣传区、店铺状态、分类分页和三列布局，购物车吸顶', async ({ page }) => {
  await mockMenu(page)
  await page.setViewportSize({ width: 1536, height: 1000 })
  await page.goto('/menu')
  await expect(page.getByRole('heading', { name: '今天的好心情，从好好吃饭开始。' })).toBeVisible()
  await expect(page.getByText('营业中', { exact: true })).toBeVisible()
  await expect(page.getByRole('article')).toHaveCount(6)
  const cards = await page
    .getByRole('article')
    .evaluateAll((nodes) =>
      nodes.map((n) => ({ x: n.getBoundingClientRect().x, y: n.getBoundingClientRect().y })),
    )
  expect(cards[0]!.y).toBe(cards[2]!.y)
  expect(cards[3]!.y).toBeGreaterThan(cards[0]!.y)
  await page.getByRole('button', { name: '看看今天吃什么' }).click()
  await expect(page.getByRole('tab', { name: '全部餐点', exact: true })).toBeInViewport()
  await expect
    .poll(() =>
      page.getByRole('tablist').evaluate((n) => Math.round(n.getBoundingClientRect().top)),
    )
    .toBe(108)
  await page.evaluate(() => window.scrollBy(0, 200))
  await expect
    .poll(() =>
      page
        .getByRole('complementary', { name: '我的餐袋' })
        .evaluate((n) => Math.round(n.getBoundingClientRect().top)),
    )
    .toBe(108)
  await page.getByRole('navigation', { name: '餐点分页' }).locator('.btn-next').click()
  await expect(page.getByRole('article')).toHaveCount(3)
  await page.getByRole('tab', { name: '精选套餐', exact: true }).click()
  await expect(page.getByRole('article')).toHaveCount(1)
  await expect(page.getByRole('navigation', { name: '餐点分页' })).toHaveCount(0)
  await expect(page.getByPlaceholder(/找我想吃/)).toHaveCount(0)
})

test('空餐袋、口味组合加减、费用计算和清空', async ({ page }) => {
  const fixture = await mockMenu(page)
  await page.goto('/menu')
  const cart = page.getByRole('complementary', { name: '我的餐袋' })
  await expect(cart).toContainText('餐袋还在等一份心动')
  await expect(cart.getByRole('img', { name: '空购物车插画' })).toHaveJSProperty(
    'naturalWidth',
    640,
  )
  await expect(cart.getByRole('button', { name: '去结算' })).toBeDisabled()
  await page
    .getByRole('article', { name: '宫保鸡丁', exact: true })
    .getByRole('button', { name: '选规格' })
    .click()
  const detail = page.getByRole('dialog', { name: '菜品详情与口味' })
  await detail.getByText('中辣', { exact: true }).click()
  await detail.getByText('不要蒜', { exact: true }).click()
  await detail.getByRole('button', { name: '确认加入' }).click()
  await expect(detail).toBeHidden()
  await expect(cart).toContainText('中辣,不要蒜')
  await expect(cart.getByRole('img', { name: '空购物车插画' })).toHaveCount(0)
  expect(fixture.mutations[0]!.data).toEqual({ dishId: 1, dishFlavor: '中辣,不要蒜' })
  await cart.getByRole('button', { name: '增加宫保鸡丁' }).click()
  await expect(cart.locator('.cart-total')).toContainText('¥52.00')
  await cart.getByRole('button', { name: '减少宫保鸡丁' }).click()
  await expect(cart.locator('.cart-total')).toContainText('¥29.00')
  await cart.getByRole('button', { name: '清空餐袋' }).click()
  await page.getByRole('button', { name: '确认清空', exact: true }).click()
  await expect(cart).toContainText('餐袋还在等一份心动')
  await expect(cart.locator('.cart-total')).toContainText('¥0.00')
})

test('套餐明细和下单模拟支付成功，使用后端金额并清空购物车', async ({ page }) => {
  const fixture = await mockMenu(page)
  await page.goto('/menu')
  await page.getByRole('tab', { name: '精选套餐', exact: true }).click()
  await page.getByRole('button', { name: '查看双人套餐详情' }).click()
  const detail = page.getByRole('dialog', { name: '套餐详情' })
  await expect(detail).toContainText('米饭 × 2')
  await detail.getByRole('button', { name: '确认加入' }).click()
  await page.getByRole('button', { name: '去结算' }).click()
  const checkout = page.getByRole('dialog', { name: '确认订单', exact: true })
  await expect(checkout).toContainText('晴空路16号')
  await checkout.getByLabel('订单备注').fill('少油，谢谢')
  await checkout.getByRole('combobox', { name: '餐具数量', exact: true }).press('Enter')
  await page.getByRole('option', { name: '2 套', exact: true }).click()
  await checkout.getByRole('button', { name: '提交订单' }).click()
  const payment = page.getByRole('dialog', { name: '订单支付', exact: true })
  await expect(payment).toContainText('¥65.00')
  expect(fixture.submissions[0]).toMatchObject({
    addressBookId: 1,
    payMethod: 1,
    deliveryStatus: 1,
    tablewareStatus: 0,
    tablewareNumber: 2,
    remark: '少油，谢谢',
  })
  expect(fixture.submissions[0]).not.toHaveProperty('amount')
  await payment.getByRole('button', { name: '确认支付' }).click()
  const success = page.getByRole('dialog', { name: '支付成功', exact: true })
  await expect(success).toContainText('TEST-99')
  await expect(success.getByRole('img', { name: '支付成功插画' })).toHaveJSProperty(
    'naturalWidth',
    640,
  )
  await success.getByRole('button', { name: '继续点餐' }).click()
  await expect(page.getByRole('complementary', { name: '我的餐袋' })).toContainText(
    '餐袋还在等一份心动',
  )
})

test('缺少地址不能提交，可以前往地址管理', async ({ page }) => {
  await mockMenu(page, { addresses: false })
  await page.goto('/menu')
  await page.getByRole('button', { name: '添加家常菜2', exact: true }).click()
  await page.getByRole('button', { name: '去结算' }).click()
  const checkout = page.getByRole('dialog', { name: '确认订单', exact: true })
  await expect(checkout).toContainText('请先添加收货地址')
  await expect(checkout.getByRole('button', { name: '提交订单' })).toBeDisabled()
  await checkout.getByRole('link', { name: '管理 / 新增地址' }).click()
  await expect(page).toHaveURL('/addresses')
})

test('预约配送必选未来时间，失败可修改重试且不会重复提交', async ({ page }) => {
  const fixture = await mockMenu(page)
  let fail = true
  await page.route('**/api/order/submit', async (route) => {
    if (!fail) return route.fallback()
    fail = false
    await route.fulfill({ json: { code: 0, msg: '餐品已停售，请重新选择', data: null } })
  })
  await page.goto('/menu')
  await page.getByRole('button', { name: '添加家常菜2', exact: true }).click()
  await page.getByRole('button', { name: '去结算' }).click()
  const checkout = page.getByRole('dialog', { name: '确认订单', exact: true })
  await checkout.getByRole('combobox', { name: '配送时间', exact: true }).press('Enter')
  await page.getByRole('option', { name: '选择送达时间', exact: true }).click()
  await checkout.getByRole('button', { name: '提交订单' }).click()
  await expect(checkout.getByText('请选择未来的送达时间', { exact: true })).toBeVisible()
  expect(fixture.submissions).toEqual([])
  const tomorrow = new Date()
  tomorrow.setDate(tomorrow.getDate() + 1)
  const date = `${tomorrow.getFullYear()}-${String(tomorrow.getMonth() + 1).padStart(2, '0')}-${String(tomorrow.getDate()).padStart(2, '0')} 18:30`
  await checkout.getByPlaceholder('选择期望送达时间').fill(date)
  await checkout.getByPlaceholder('选择期望送达时间').press('Tab')
  await checkout.getByRole('button', { name: '提交订单' }).click()
  await expect(page.getByText('餐品已停售，请重新选择', { exact: true })).toBeVisible()
  await expect(checkout).toBeVisible()
  await checkout.getByRole('button', { name: '提交订单' }).click()
  await expect(page.getByRole('dialog', { name: '订单支付' })).toBeVisible()
  expect(fixture.submissions).toHaveLength(1)
  expect(fixture.submissions[0]).toMatchObject({
    deliveryStatus: 0,
    estimatedDeliveryTime: `${date}:00`,
    tablewareStatus: 1,
    tablewareNumber: 0,
  })
})

test('餐点和购物车的读取失败提供重试，数据恢复后才能加购', async ({ page }) => {
  await mockMenu(page)
  let cartFailure = true
  let menuFailure = true
  await page.route('**/api/shoppingCart/list', (route) =>
    cartFailure ? route.fulfill({ status: 503 }) : route.fallback(),
  )
  await page.route('**/api/dish/list?*', (route) =>
    menuFailure ? route.fulfill({ status: 503 }) : route.fallback(),
  )
  await page.goto('/menu')
  await expect(page.getByText('餐点加载失败', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '重新加载购物车' })).toBeVisible()
  menuFailure = false
  await page.getByRole('button', { name: '重新加载餐点' }).click()
  await expect(page.getByRole('article')).toHaveCount(6)
  await expect(page.getByRole('button', { name: '添加家常菜2', exact: true })).toBeDisabled()
  cartFailure = false
  await page.getByRole('button', { name: '重新加载购物车' }).click()
  await expect(page.getByRole('button', { name: '添加家常菜2', exact: true })).toBeEnabled()
})

test('打烊时不能加购和结算，移动端无横向溢出', async ({ page }) => {
  await mockMenu(page, { closed: true })
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/menu')
  await expect(page.getByText('休息中', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '添加家常菜2', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '去结算' })).toBeDisabled()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
})
