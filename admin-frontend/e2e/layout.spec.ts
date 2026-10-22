import { expect, test, type Route } from '@playwright/test'
import { employee, mockAdminSession } from './helpers/admin.js'

const pages = [
  ['/dashboard', '工作台', '今日数据'],
  ['/statistics', '数据统计', '营业额统计'],
  ['/order', '订单管理', '全部订单'],
  ['/dish', '菜品管理', '+ 新建菜品'],
  ['/setmeal', '套餐管理', '+ 新建套餐'],
  ['/category', '分类管理', '+ 新增菜品分类'],
  ['/employee', '员工管理', '+ 添加员工'],
] as const

test('按指定顺序展示七个管理入口，切换页面时保留顶部栏', async ({ page, context, baseURL }) => {
  const sockets = await mockAdminSession(page, context, baseURL!)
  await page.goto('/dashboard')
  const navigation = page.getByRole('navigation', { name: '管理菜单' })
  await expect(navigation.getByRole('menuitem')).toHaveText(pages.map(([, title]) => title))
  await expect(page.getByRole('button', { name: employee.name, exact: true })).toBeVisible()
  await expect(page.getByRole('status', { name: '店铺营业状态' })).toHaveText('营业中')
  await expect(page.getByText('通知中心', { exact: true })).toHaveCount(0)
  for (const [path, title, content] of pages) {
    await navigation.getByRole('menuitem', { name: title, exact: true }).click()
    await expect(page).toHaveURL(path)
    await expect(page.getByText(content, { exact: true })).toBeVisible()
  }
  expect(sockets).toHaveLength(1)
  const colors = await page.evaluate(() => ({
    logo: getComputedStyle(document.querySelector('.sidebar-brand')!).backgroundColor,
    header: getComputedStyle(document.querySelector('.admin-header')!).backgroundColor,
  }))
  expect(colors.logo).toBe(colors.header)
})

test('折叠菜单只显示图标，仍可导航，刷新后保持折叠和员工姓名', async ({
  page,
  context,
  baseURL,
}) => {
  await mockAdminSession(page, context, baseURL!)
  await page.goto('/dashboard')
  const sidebar = page.locator('.admin-sidebar')
  await expect(sidebar).toHaveCSS('width', '190px')
  await page.getByRole('button', { name: '折叠菜单' }).click()
  await expect(sidebar).toHaveCSS('width', '80px')
  await expect(sidebar.getByText('员工管理', { exact: true })).toBeHidden()
  await sidebar.getByRole('menuitem', { name: '员工管理', exact: true }).click()
  await expect(page.getByRole('region', { name: '员工管理', exact: true })).toBeVisible()
  await page.reload()
  await expect(sidebar).toHaveCSS('width', '80px')
  await expect(page.getByRole('button', { name: employee.name, exact: true })).toBeVisible()
  await page.getByRole('button', { name: '展开菜单' }).click()
  await expect(sidebar).toHaveCSS('width', '190px')
})

test('营业状态取消不保存，确定后更新顶部状态', async ({ page, context, baseURL }) => {
  await mockAdminSession(page, context, baseURL!)
  let status = 1
  const writes: string[] = []
  await page.route('**/api/shop/status', (route) =>
    route.fulfill({ json: { code: 1, msg: null, data: status } }),
  )
  await page.route(/\/api\/shop\/[01]$/, (route) => {
    writes.push(route.request().method())
    status = Number(route.request().url().split('/').pop())
    return route.fulfill({ json: { code: 1, msg: null, data: null } })
  })
  await page.goto('/dashboard')
  await page.getByRole('button', { name: '营业状态设置' }).click()
  const dialog = page.getByRole('dialog', { name: '营业状态设置' })
  await expect(dialog.getByRole('radio', { name: /^营业中/ })).toBeChecked()
  await dialog.getByText('打烊中', { exact: true }).click()
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  expect(writes).toHaveLength(0)
  await page.getByRole('button', { name: '营业状态设置' }).click()
  await expect(dialog.getByRole('radio', { name: /^营业中/ })).toBeChecked()
  await dialog.getByText('打烊中', { exact: true }).click()
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('status', { name: '店铺营业状态' })).toHaveText('打烊中')
  expect(writes).toEqual(['PUT'])
  await page.reload()
  await expect(page.getByRole('status', { name: '店铺营业状态' })).toHaveText('打烊中')
})

test('营业状态保存失败保留弹窗和当前状态，并可重试', async ({ page, context, baseURL }) => {
  await mockAdminSession(page, context, baseURL!)
  let attempts = 0
  await page.route('**/api/shop/0', (route) => {
    attempts += 1
    return route.fulfill({
      json:
        attempts === 1
          ? { code: 0, msg: '营业状态保存失败', data: null }
          : { code: 1, msg: null, data: null },
    })
  })
  await page.goto('/dashboard')
  await page.getByRole('button', { name: '营业状态设置' }).click()
  const dialog = page.getByRole('dialog', { name: '营业状态设置' })
  await dialog.getByText('打烊中', { exact: true }).click()
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('营业状态保存失败')
  await expect(dialog).toBeVisible()
  await expect(page.getByRole('status', { name: '店铺营业状态' })).toHaveText('营业中')
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('status', { name: '店铺营业状态' })).toHaveText('打烊中')
})

test('密码校验、原始报错、重试保存及重新打开时清空表单', async ({ page, context, baseURL }) => {
  await mockAdminSession(page, context, baseURL!)
  const submissions: unknown[] = []
  await page.route('**/api/employee/editPassword', (route) => {
    submissions.push(route.request().postDataJSON())
    return route.fulfill({
      json:
        submissions.length === 1
          ? { code: 0, msg: '密码错误', data: null }
          : { code: 1, msg: null, data: null },
    })
  })
  await page.goto('/dashboard')
  const employeeButton = page.getByRole('button', { name: employee.name, exact: true })
  await employeeButton.hover()
  await page.getByRole('menuitem', { name: '修改密码', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '修改密码' })
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog.getByText('请输入原始密码', { exact: true })).toBeVisible()
  await expect(dialog.getByText('请输入新密码', { exact: true })).toBeVisible()
  await expect(dialog.getByText('请再次输入密码', { exact: true })).toBeVisible()
  await dialog.getByLabel('原始密码', { exact: true }).fill('old$ password')
  await dialog.getByLabel('新密码', { exact: true }).fill('abc_123')
  await dialog.getByLabel('确认密码', { exact: true }).fill('abc_123')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(
    dialog.getByText('6-20位密码，数字或字母，区分大小写', { exact: true }),
  ).toBeVisible()
  await dialog.getByLabel('新密码', { exact: true }).fill('Abc1234')
  await dialog.getByLabel('确认密码', { exact: true }).fill('abc1234')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog.getByText('密码不一致，请重新输入密码', { exact: true })).toBeVisible()
  expect(submissions).toHaveLength(0)
  await dialog.getByLabel('确认密码', { exact: true }).fill('Abc1234')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('alert').filter({ hasText: '密码错误' })).toBeVisible()
  await expect(dialog).toBeVisible()
  expect(submissions).toEqual([{ oldPassword: 'old$ password', newPassword: 'Abc1234' }])
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog).toBeHidden()
  await expect(page).toHaveURL('/dashboard')
  await employeeButton.hover()
  await page.getByRole('menuitem', { name: '修改密码', exact: true }).click()
  await expect(dialog.getByLabel('原始密码', { exact: true })).toBeEmpty()
  await expect(dialog.getByLabel('新密码', { exact: true })).toBeEmpty()
  await expect(dialog.getByLabel('确认密码', { exact: true })).toBeEmpty()
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
})

const logoutResponses = [
  {
    label: '成功',
    respond: (route: Route) => route.fulfill({ json: { code: 1, msg: null, data: null } }),
  },
  { label: '失败', respond: (route: Route) => route.abort('failed') },
]

for (const scenario of logoutResponses) {
  test(`退出清除 Cookie 和员工资料并关闭消息连接（后端${scenario.label}）`, async ({
    page,
    context,
    baseURL,
  }) => {
    await context.addCookies([{ name: 'user_token', value: 'existing-user-token', url: baseURL! }])
    const sockets = await mockAdminSession(page, context, baseURL!)
    await page.route('**/api/employee/logout', scenario.respond)
    await page.goto('/dashboard')
    await expect(page.getByRole('region', { name: '今日数据', exact: true })).toBeVisible()
    let closed = false
    sockets[0]!.onClose(() => {
      closed = true
    })
    await page.getByRole('button', { name: employee.name, exact: true }).hover()
    await page.getByRole('menuitem', { name: '退出登录', exact: true }).click()
    await expect(page).toHaveURL('/login')
    expect((await context.cookies()).some((cookie) => cookie.name === 'admin_token')).toBe(false)
    expect((await context.cookies()).find((cookie) => cookie.name === 'user_token')?.value).toBe(
      'existing-user-token',
    )
    expect(await page.evaluate(() => localStorage.getItem('sky-admin-employee'))).toBeNull()
    await expect.poll(() => closed).toBe(true)
  })
}

test('来单和催单通知点击进入对应订单，消息内容按文本显示', async ({ page, context, baseURL }) => {
  const sockets = await mockAdminSession(page, context, baseURL!)
  await page.goto('/dashboard')
  await expect(page.getByRole('region', { name: '今日数据', exact: true })).toBeVisible()
  await expect.poll(() => sockets.length).toBe(1)
  sockets[0]!.send('ping')
  sockets[0]!.send(JSON.stringify({ type: 3, orderId: 999, content: '忽略的消息' }))
  await page.getByRole('menuitem', { name: '菜品管理', exact: true }).click()
  await expect(page.getByRole('region', { name: '菜品管理', exact: true })).toBeVisible()
  expect(sockets).toHaveLength(1)
  sockets[0]!.send(
    JSON.stringify({ type: 1, orderId: 101, content: '订单号 <img src=x onerror=alert(1)>' }),
  )
  const incoming = page.getByRole('alert').filter({ hasText: '待接单' })
  await expect(incoming).toContainText('订单号 <img src=x onerror=alert(1)>')
  await expect(incoming.locator('img')).toHaveCount(0)
  await incoming.getByRole('button', { name: '订单待处理' }).click()
  await expect(page).toHaveURL('/order?orderId=101')
  await expect(incoming).toBeHidden()
  sockets[0]!.send(JSON.stringify({ type: 2, orderId: 102, content: '订单号 20261018002' }))
  const reminder = page.getByRole('alert').filter({ hasText: '催单' })
  await expect(reminder).toContainText('订单号 20261018002')
  await reminder.getByRole('button', { name: '去处理' }).click()
  await expect(page).toHaveURL('/order?orderId=102')
  expect(sockets).toHaveLength(1)
})
