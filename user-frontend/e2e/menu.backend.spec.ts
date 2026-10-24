import { expect, test } from '@playwright/test'

test('真实后端：口味隔离、空口味增减、确认下单和模拟支付', async ({ page, request }) => {
  // eslint-disable-next-line playwright/no-skipped-test -- 显式开启，使用独立账号的购物车及订单
  test.skip(process.env.E2E_BACKEND !== '1', '设置 E2E_BACKEND=1，启用点餐真实后端联调')
  const account = `E2eMenu${Date.now()}`
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
  const token = (await page.context().cookies()).find((cookie) => cookie.name === 'user_token')!.value
  const headers = { authentication: token }
  let orderId: number | undefined
  let addressId: number | undefined
  try {
    expect(
      await (
        await request.post('/api/addressBook', {
          headers,
          data: {
            consignee: '点餐联调用户',
            phone: '13800000000',
            provinceName: '浙江省',
            cityName: '杭州市',
            districtName: '西湖区',
            detail: '点餐联调路18号',
            label: '家',
          },
        })
      ).json(),
    ).toMatchObject({ code: 1 })
    addressId = (await (await request.get('/api/addressBook/list', { headers })).json()).data[0].id
    expect(
      await (
        await request.put('/api/addressBook/default', { headers, data: { id: addressId } })
      ).json(),
    ).toMatchObject({ code: 1 })
    const categories = (
      await (await request.get('/api/category/list', { headers, params: { type: 1 } })).json()
    ).data as { id: number; name: string }[]
    const category = categories[0]!
    const dishes = (
      await (
        await request.get('/api/dish/list', { headers, params: { categoryId: category.id } })
      ).json()
    ).data as { id: number; name: string; flavors: { name: string; value: string }[] }[]
    const dish = dishes.find((item) => item.flavors.length)!
    expect(dish).toBeTruthy()
    const flavor: string = JSON.parse(dish.flavors[0]!.value)[0]
    await page.reload()
    await page.getByRole('tab', { name: category.name, exact: true }).click()
    await page.getByRole('button', { name: `查看${dish.name}详情`, exact: true }).click()
    let detail = page.getByRole('dialog', { name: '菜品详情与口味', exact: true })
    await detail.getByText(flavor, { exact: true }).click()
    await detail.getByRole('button', { name: '确认加入', exact: true }).click()
    await expect(detail).toBeHidden()
    await page.getByRole('button', { name: `查看${dish.name}详情`, exact: true }).click()
    detail = page.getByRole('dialog', { name: '菜品详情与口味', exact: true })
    await detail.getByRole('button', { name: '确认加入', exact: true }).click()
    await expect(detail).toBeHidden()
    const cart = page.getByRole('complementary', { name: '我的餐袋' })
    await expect(cart.locator('.cart-item')).toHaveCount(2)
    const plain = cart.locator('.cart-item').filter({ hasText: '默认口味' })
    await plain.getByRole('button', { name: `增加${dish.name}` }).click()
    await expect(plain.getByLabel(`${dish.name}数量`, { exact: true })).toHaveText('2')
    let serverCart = (await (await request.get('/api/shoppingCart/list', { headers })).json())
      .data as { dishFlavor: string | null; number: number }[]
    expect(serverCart.find((item) => item.dishFlavor === null)?.number).toBe(2)
    expect(serverCart.find((item) => item.dishFlavor === flavor)?.number).toBe(1)
    await plain.getByRole('button', { name: `减少${dish.name}` }).click()
    await expect(plain.getByLabel(`${dish.name}数量`, { exact: true })).toHaveText('1')
    serverCart = (await (await request.get('/api/shoppingCart/list', { headers })).json()).data
    expect(serverCart.find((item) => item.dishFlavor === flavor)?.number).toBe(1)
    await cart.getByRole('button', { name: '去结算', exact: true }).click()
    const checkout = page.getByRole('dialog', { name: '确认订单', exact: true })
    await expect(checkout).toContainText('点餐联调路18号')
    await checkout.getByLabel('订单备注', { exact: true }).fill('点餐页面联调')
    const response = page.waitForResponse('**/api/order/submit')
    await checkout.getByRole('button', { name: '提交订单' }).click()
    const result = await (await response).json()
    expect(result).toMatchObject({ code: 1 })
    orderId = result.data.id
    const payment = page.getByRole('dialog', { name: '订单支付', exact: true })
    await expect(payment).toContainText(`¥${result.data.orderAmount.toFixed(2)}`)
    await payment.getByRole('button', { name: '暂不支付' }).click()
    await expect(page.getByText('订单已提交，等待支付', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '继续支付', exact: true }).click()
    await payment.getByRole('button', { name: '确认支付' }).click()
    const success = page.getByRole('dialog', { name: '支付成功', exact: true })
    await expect(success).toContainText(result.data.orderNumber)
    const saved = await (await request.get(`/api/order/orderDetail/${orderId}`, { headers })).json()
    expect(saved).toMatchObject({
      code: 1,
      data: {
        status: 2,
        payStatus: 1,
        tablewareStatus: 1,
        deliveryStatus: 1,
        remark: '点餐页面联调',
      },
    })
    expect(saved.data.orderDetailList).toHaveLength(2)
    expect((await (await request.get('/api/shoppingCart/list', { headers })).json()).data).toEqual(
      [],
    )
    await success.getByRole('button', { name: '查看订单', exact: true }).click()
    await expect(page).toHaveURL('/orders')
    await expect(page.getByRole('article')).toHaveCount(1)
  } finally {
    if (orderId) await request.put(`/api/order/cancel/${orderId}`, { headers })
    await request.delete('/api/shoppingCart/clean', { headers })
    if (addressId) await request.delete('/api/addressBook', { headers, params: { id: addressId } })
  }
})
