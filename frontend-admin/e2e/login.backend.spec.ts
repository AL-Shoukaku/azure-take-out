/* eslint-disable playwright/no-conditional-expect -- 真实数据为空时跳过详情断言，避免创建业务数据 */
import { expect, test } from '@playwright/test'
import { readFile } from 'node:fs/promises'

const username = process.env.E2E_ADMIN_USERNAME
const password = process.env.E2E_ADMIN_PASSWORD

test('连接真实后端登录，读取工作台、管理列表和统计并下载报表', async ({ page, context }) => {
  // eslint-disable-next-line playwright/no-skipped-test -- 真实后端测试需要用户通过环境变量提供账号
  test.skip(
    !username || !password,
    '设置 E2E_ADMIN_USERNAME 和 E2E_ADMIN_PASSWORD 后执行真实后端测试',
  )

  await page.goto('/login')
  await page.getByPlaceholder('账号').fill(username!)
  await page.getByPlaceholder('密码').fill(password!)
  const workspace = ['businessData', 'overviewOrders', 'overviewDishes', 'overviewSetmeals'].map(
    (endpoint) =>
      page.waitForResponse(
        (response) => new URL(response.url()).pathname === `/api/workspace/${endpoint}`,
      ),
  )
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL('/dashboard')
  await expect(page.getByRole('region', { name: '今日数据', exact: true })).toBeVisible()
  for (const response of workspace) expect((await (await response).json()).code).toBe(1)
  await expect(
    page.getByRole('region', { name: '菜品总览', exact: true }).getByLabel('已启售数量'),
  ).toHaveText(/^\d+$/)
  await expect(page.getByRole('region', { name: '订单信息', exact: true })).toBeVisible()
  await expect(page.getByRole('status', { name: '店铺营业状态' })).toHaveText(/营业中|打烊中/)
  expect((await context.cookies()).some((cookie) => cookie.name === 'token')).toBe(true)

  await page.reload()
  await expect(page.getByRole('region', { name: '今日数据', exact: true })).toBeVisible()

  const employeeResponse = page.waitForResponse(
    (response) => new URL(response.url()).pathname === '/api/employee/page',
  )
  await page.getByRole('menuitem', { name: '员工管理', exact: true }).click()
  expect((await (await employeeResponse).json()).code).toBe(1)
  await expect(page.getByRole('region', { name: '员工管理', exact: true })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: '员工姓名', exact: true })).toBeVisible()

  const categoryResponse = page.waitForResponse(
    (response) => new URL(response.url()).pathname === '/api/category/page',
  )
  await page.getByRole('menuitem', { name: '分类管理', exact: true }).click()
  expect((await (await categoryResponse).json()).code).toBe(1)
  await expect(page.getByRole('columnheader', { name: '分类名称', exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('region', { name: '分类管理', exact: true })).toBeVisible()

  for (const [kind, label] of [
    ['dish', '菜品'],
    ['setmeal', '套餐'],
  ] as const) {
    const result = page.waitForResponse(
      (response) => new URL(response.url()).pathname === `/api/${kind}/page`,
    )
    await page.getByRole('menuitem', { name: `${label}管理`, exact: true }).click()
    const data = await (await result).json()
    expect(data.code).toBe(1)
    await expect(
      page.getByRole('columnheader', { name: `${label}名称`, exact: true }),
    ).toBeVisible()
    const record = data.data.records[0]
    // eslint-disable-next-line playwright/no-conditional-in-test -- 使用现有只读数据，空库跳过详情检查
    if (record) {
      const detail = page.waitForResponse(
        (response) => new URL(response.url()).pathname === `/api/${kind}/${record.id}`,
      )
      await page
        .getByRole('row')
        .filter({ hasText: record.name })
        .getByRole('button', { name: '修改', exact: true })
        .click()
      expect((await (await detail).json()).code).toBe(1)
      await expect(page.getByLabel(`${label}名称:`, { exact: true })).toHaveValue(record.name)
      await page.getByRole('button', { name: '取消', exact: true }).click()
    }
  }

  const orders = page.waitForResponse(
    (response) => new URL(response.url()).pathname === '/api/order/conditionSearch',
  )
  await page.getByRole('menuitem', { name: '订单管理', exact: true }).click()
  const orderData = await (await orders).json()
  expect(orderData.code).toBe(1)
  await expect(page.getByRole('tab', { name: '全部订单', exact: true })).toBeVisible()
  // eslint-disable-next-line playwright/no-conditional-in-test -- 空库仅检查列表，不创建真实订单
  if (orderData.data.records.length) {
    const record = orderData.data.records[0]
    const detail = page.waitForResponse(
      (response) => new URL(response.url()).pathname === `/api/order/details/${record.id}`,
    )
    await page
      .getByRole('row')
      .filter({ has: page.getByRole('cell', { name: record.number, exact: true }) })
      .getByRole('button', { name: '查看', exact: true })
      .click()
    expect((await (await detail).json()).code).toBe(1)
    await expect(page.getByRole('dialog', { name: '订单信息', exact: true })).toContainText(
      record.number,
    )
    await page.getByRole('dialog').getByRole('button', { name: '返回', exact: true }).click()
  }

  const reports = ['turnoverStatistics', 'userStatistics', 'ordersStatistics', 'top10'].map(
    (endpoint) =>
      page.waitForResponse(
        (response) => new URL(response.url()).pathname === `/api/report/${endpoint}`,
      ),
  )
  await page.getByRole('menuitem', { name: '数据统计', exact: true }).click()
  for (const response of reports) {
    expect((await (await response).json()).code).toBe(1)
  }
  await expect(page.getByRole('img', { name: '营业额统计折线图' })).toBeVisible()
  await expect(page.getByRole('img', { name: '订单统计折线图' })).toBeVisible()
  await page.getByRole('button', { name: '数据导出', exact: true }).click()
  const downloading = page.waitForEvent('download')
  await page
    .getByRole('dialog', { name: '提示', exact: true })
    .getByRole('button', { name: '确定', exact: true })
    .click()
  const file = await downloading
  expect(file.suggestedFilename()).toBe('运营数据统计报表.xlsx')
  expect((await readFile((await file.path())!)).subarray(0, 4)).toEqual(Buffer.from([80, 75, 3, 4]))
})
