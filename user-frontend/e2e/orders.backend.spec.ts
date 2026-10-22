import { expect, test } from '@playwright/test'

test('真实后端：独立账号分页、详情、模拟支付、催单、取消及再来一单', async ({ page, request }) => {
  // eslint-disable-next-line playwright/no-skipped-test -- 显式开启后仅操作独立测试账号的订单和购物车
  test.skip(process.env.E2E_BACKEND !== '1', '设置 E2E_BACKEND=1，启用订单真实后端测试')
  const account = `E2eOrder${Date.now()}`
  expect(
    await (
      await request.post('/api/user/register', { data: { account, password: 'TestPass123' } })
    ).json(),
  ).toMatchObject({ code: 1 })
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill(account)
  await page.getByLabel('密码', { exact: true }).fill('TestPass123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL('/menu')
  const token = (await page.context().cookies()).find((cookie) => cookie.name === 'token')!.value
  const headers = { authentication: token }
  const orders: { id: number; orderNumber: string }[] = []
  try {
    expect(
      await (
        await request.post('/api/addressBook', {
          headers,
          data: {
            consignee: '订单联调用户',
            phone: '13800000000',
            provinceName: '浙江省',
            cityName: '杭州市',
            districtName: '西湖区',
            detail: '订单联调路 18 号',
            label: '家',
          },
        })
      ).json(),
    ).toMatchObject({ code: 1 })
    const addressList = await (await request.get('/api/addressBook/list', { headers })).json()
    const addressId: number = addressList.data[0].id
    const categories = await (
      await request.get('/api/category/list', { headers, params: { type: 1 } })
    ).json()
    expect(categories).toMatchObject({ code: 1 })
    let dish: { id: number; name: string } | undefined
    for (const category of categories.data as { id: number }[]) {
      const dishes = await (
        await request.get('/api/dish/list', { headers, params: { categoryId: category.id } })
      ).json()
      expect(dishes).toMatchObject({ code: 1 })
      dish = dishes.data[0]
      if (dish) break
    }
    if (!dish) throw new Error('真实后端订单测试需要至少一个在售菜品')
    for (let index = 0; index < 7; index += 1) {
      expect(
        await (
          await request.post('/api/shoppingCart/add', { headers, data: { dishId: dish.id } })
        ).json(),
      ).toMatchObject({ code: 1 })
      const submitted = await (
        await request.post('/api/order/submit', {
          headers,
          data: {
            addressBookId: addressId,
            payMethod: 1,
            deliveryStatus: 1,
            tablewareStatus: 1,
            tablewareNumber: 0,
            remark: '订单页面联调',
          },
        })
      ).json()
      expect(submitted).toMatchObject({
        code: 1,
        data: { id: expect.any(Number), orderNumber: expect.any(String) },
      })
      orders.push(submitted.data)
    }
    await page
      .getByRole('navigation', { name: '主导航' })
      .getByRole('link', { name: '我的订单', exact: true })
      .click()
    await expect(page.getByRole('article')).toHaveCount(6)
    await page.getByRole('navigation', { name: '订单分页' }).locator('.btn-next').click()
    await expect(page.getByRole('article')).toHaveCount(1)
    await page.getByRole('article').first().getByRole('button', { name: '订单详情' }).click()
    const detail = page.getByRole('dialog', { name: '订单详情', exact: true })
    await expect(detail).toContainText(dish.name)
    await expect(detail).toContainText('订单联调路 18 号')
    await detail.getByRole('button', { name: '去支付', exact: true }).click()
    await page
      .getByRole('dialog', { name: '订单支付', exact: true })
      .getByRole('button', { name: '确认支付', exact: true })
      .click()
    await expect(detail.getByRole('heading', { name: '待接单', exact: true })).toBeVisible()
    const reminder = page.waitForResponse((response) => response.url().includes('/order/reminder/'))
    await detail.getByRole('button', { name: '催单', exact: true }).click()
    expect(await (await reminder).json()).toMatchObject({ code: 1 })
    await detail.getByRole('button', { name: '取消订单', exact: true }).click()
    await page.getByRole('button', { name: '确认取消', exact: true }).click()
    await expect(detail.getByRole('heading', { name: '已取消', exact: true })).toBeVisible()
    await expect(detail).toContainText('已退款')
    // 预先保留一份商品，验证再来一单追加购物车而非清空。
    expect(
      await (
        await request.post('/api/shoppingCart/add', { headers, data: { dishId: dish.id } })
      ).json(),
    ).toMatchObject({ code: 1 })
    await detail.getByRole('button', { name: '再来一单', exact: true }).click()
    await expect(page).toHaveURL('/menu')
    const cart = await (await request.get('/api/shoppingCart/list', { headers })).json()
    expect(cart).toMatchObject({ code: 1 })
    expect(
      cart.data.reduce((count: number, item: { number: number }) => count + item.number, 0),
    ).toBe(2)
    await page.goto('/orders')
    await page.getByRole('tab', { name: '进行中', exact: true }).click()
    await expect(page.getByRole('article')).toHaveCount(0)
    await page.getByRole('tab', { name: '已取消', exact: true }).click()
    await expect(page.getByRole('article')).toHaveCount(1)
  } finally {
    for (const order of orders) {
      const detail = await (
        await request.get(`/api/order/orderDetail/${order.id}`, { headers })
      ).json()
      if (detail.code === 1 && [1, 2].includes(detail.data.status)) {
        await request.put(`/api/order/cancel/${order.id}`, { headers })
      }
    }
    await request.delete('/api/shoppingCart/clean', { headers })
  }
})
