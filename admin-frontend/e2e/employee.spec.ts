import { expect, test } from '@playwright/test'
import { mockAdminSession } from './helpers/admin.js'
import { employeeForm, fillEmployee, mockEmployees } from './helpers/employees.js'

test.beforeEach(async ({ page, context, baseURL }) => {
  await mockAdminSession(page, context, baseURL!)
})

test('员工表格、权限、时间格式、分页、姓名查询和清空查询', async ({ page }) => {
  const api = await mockEmployees(page)
  await page.goto('/employee')
  await expect(page.getByRole('columnheader')).toHaveText([
    '员工姓名',
    '账号',
    '手机号',
    '账号状态',
    '最后操作时间',
    '操作',
  ])
  const admin = page.getByRole('row').filter({ hasText: '管理员' })
  await expect(admin.getByRole('button', { name: '修改' })).toBeDisabled()
  await expect(admin.getByRole('button', { name: '禁用' })).toBeDisabled()
  const self = page.getByRole('row').filter({ hasText: '测试店长' })
  await expect(self.getByRole('button', { name: '禁用' })).toBeDisabled()
  await expect(self.getByRole('button', { name: '修改' })).toBeEnabled()
  await expect(admin.getByRole('cell', { name: '2026-10-18 12:34', exact: true })).toBeVisible()
  await expect(page.getByText('共 45 条', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '下一页' }).click()
  await expect(page.getByRole('row').filter({ hasText: 'staff11' })).toBeVisible()
  expect(api.queries.at(-1)?.get('page')).toBe('2')
  await page.getByLabel('员工姓名查询').fill('演示')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByText('共 1 条', { exact: true })).toBeVisible()
  expect(api.queries.at(-1)?.get('page')).toBe('1')
  expect(api.queries.at(-1)?.get('name')).toBe('演示')
  await page.getByLabel('员工姓名查询').fill('不存在')
  await page.getByLabel('员工姓名查询').press('Enter')
  await expect(page.getByText('暂无数据', { exact: true })).toBeVisible()
  await page.getByLabel('员工姓名查询').fill('')
  await page.getByLabel('员工姓名查询').press('Enter')
  await expect(page.getByText('共 45 条', { exact: true })).toBeVisible()
  await page
    .getByRole('region', { name: '员工管理', exact: true })
    .getByText('10条/页', { exact: true })
    .click()
  await expect(page.getByRole('option')).toHaveText(['10条/页', '20条/页', '30条/页', '40条/页'])
  await page.getByRole('option', { name: '20条/页', exact: true }).click()
  await expect.poll(() => api.queries.at(-1)?.get('pageSize')).toBe('20')
  await page.getByRole('spinbutton', { name: '页', exact: true }).fill('3')
  await page.getByRole('spinbutton', { name: '页', exact: true }).press('Enter')
  await expect(page.getByRole('row').filter({ hasText: 'staff45' })).toBeVisible()
  expect(api.queries.at(-1)?.get('page')).toBe('3')
})

test('禁用与启用均需确认，取消不提交，失败保留原状态并支持重试', async ({ page }) => {
  const api = await mockEmployees(page)
  await page.goto('/employee')
  const row = page.getByRole('row').filter({ hasText: '演示店员' })
  await row.getByRole('button', { name: '禁用' }).click()
  const dialog = page.getByRole('dialog', { name: '提示' })
  await expect(dialog).toContainText('确认调整该账号的状态?')
  await dialog.getByRole('button', { name: '取消' }).click()
  expect(api.writes).toHaveLength(0)
  api.failures.status = '状态调整失败'
  await row.getByRole('button', { name: '禁用' }).click()
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(page.getByRole('alert').filter({ hasText: '状态调整失败' })).toBeVisible()
  await expect(row.getByRole('button', { name: '禁用' })).toBeEnabled()
  api.failures.status = ''
  await row.getByRole('button', { name: '禁用' }).click()
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(row.getByRole('button', { name: '启用' })).toBeVisible()
  await row.getByRole('button', { name: '启用' }).click()
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(row.getByRole('button', { name: '禁用' })).toBeVisible()
  expect(api.writes.map(({ method, path, id }) => ({ method, path, id }))).toEqual([
    { method: 'POST', path: '/api/employee/status/0', id: '2' },
    { method: 'POST', path: '/api/employee/status/0', id: '2' },
    { method: 'POST', path: '/api/employee/status/1', id: '2' },
  ])
})

test('新增表单校验账号、手机号及身份证，支持 19 开头手机号并在继续添加时清空', async ({ page }) => {
  const api = await mockEmployees(page)
  await page.goto('/employee')
  await page.getByRole('button', { name: '+ 添加员工', exact: true }).click()
  await expect(page).toHaveURL('/employee/add')
  await expect(page.getByRole('menuitem', { name: '员工管理', exact: true })).toHaveClass(
    /is-active/,
  )
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('请输入账号', { exact: true })).toBeVisible()
  await expect(page.getByText('请输入员工姓名', { exact: true })).toBeVisible()
  await expect(page.getByText('请输入手机号', { exact: true })).toBeVisible()
  await expect(page.getByText('请输入身份证号码', { exact: true })).toBeVisible()
  await fillEmployee(page)
  await page.getByLabel('账号:', { exact: true }).fill('ABC')
  await page.getByLabel('手机号:', { exact: true }).fill('12900000000')
  await page.getByLabel('身份证号:', { exact: true }).fill('123')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('账号输入不符，请输入3-20个字符', { exact: true })).toBeVisible()
  await expect(page.getByText('请输入正确的手机号!', { exact: true })).toBeVisible()
  await expect(page.getByText('身份证号码不正确', { exact: true })).toBeVisible()
  expect(api.writes).toHaveLength(0)
  await fillEmployee(page)
  await page.getByLabel('手机号:', { exact: true }).fill('19900000000')
  await page.getByText('女', { exact: true }).click()
  await page.getByRole('button', { name: '保存并继续添加', exact: true }).click()
  await expect(page.getByLabel('账号:', { exact: true })).toBeEmpty()
  await expect(page.getByRole('radio', { name: '男', exact: true })).toBeChecked()
  await expect(page.getByText('请输入账号', { exact: true })).toBeHidden()
  expect(api.writes).toHaveLength(1)
  expect(api.writes[0]).toMatchObject({
    method: 'POST',
    path: '/api/employee',
    body: { ...employeeForm, phone: '19900000000', sex: '0' },
  })
  await fillEmployee(page)
  await page.getByLabel('身份证号:', { exact: true }).fill('110101900101123')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page).toHaveURL('/employee')
  await expect(page.getByRole('row').filter({ hasText: '新员工' })).toHaveCount(2)
})

test('保存失败保留输入，再次保存期间禁止重复提交和离开', async ({ page }) => {
  const api = await mockEmployees(page)
  await page.goto('/employee/add')
  await fillEmployee(page)
  api.failures.save = "'newstaff'已存在"
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('alert').filter({ hasText: "'newstaff'已存在" })).toBeVisible()
  await expect(page.getByLabel('账号:', { exact: true })).toHaveValue('newstaff')
  api.failures.save = ''
  api.delaySave = true
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '保存并继续添加', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '取消', exact: true })).toBeDisabled()
  await expect.poll(() => api.writes.length).toBe(2)
  await page.getByRole('menuitem', { name: '工作台', exact: true }).click()
  await expect(page).toHaveURL('/employee/add')
  api.releaseSave()
  await expect(page).toHaveURL('/employee')
})

test('员工保存遇到登录过期时清除会话并进入登录页', async ({ page, context }) => {
  await mockEmployees(page)
  await page.goto('/employee/add')
  await fillEmployee(page)
  await page.route(/\/api\/employee$/, (route) => route.fulfill({ status: 401 }))
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page).toHaveURL('/login')
  expect((await context.cookies()).some((cookie) => cookie.name === 'admin_token')).toBe(false)
  expect(await page.evaluate(() => localStorage.getItem('sky-admin-employee'))).toBeNull()
})

test('修改回显详情及性别，只保存资料；刷新仍可回显，取消不提交', async ({ page }) => {
  const api = await mockEmployees(page)
  await page.goto('/employee')
  await page
    .getByRole('row')
    .filter({ hasText: '演示店员' })
    .getByRole('button', { name: '修改' })
    .click()
  await expect(page).toHaveURL('/employee/add?id=2')
  await page.reload()
  await expect(page.getByRole('heading', { name: '修改员工信息' })).toBeVisible()
  await expect(page.getByLabel('账号:', { exact: true })).toHaveValue('staff2')
  await expect(page.getByLabel('员工姓名:', { exact: true })).toHaveValue('演示店员')
  await expect(page.getByRole('radio', { name: '女', exact: true })).toBeChecked()
  await expect(page.getByRole('button', { name: '保存并继续添加' })).toHaveCount(0)
  await page.getByLabel('员工姓名:', { exact: true }).fill('修改的店员')
  await page.getByLabel('手机号:', { exact: true }).fill('19800000000')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page).toHaveURL('/employee')
  await expect(page.getByRole('row').filter({ hasText: '修改的店员' })).toBeVisible()
  expect(api.writes[0]).toMatchObject({
    method: 'PUT',
    body: {
      ...employeeForm,
      id: 2,
      username: 'staff2',
      name: '修改的店员',
      phone: '19800000000',
      sex: '0',
    },
  })
  await page.getByRole('button', { name: '+ 添加员工', exact: true }).click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page).toHaveURL('/employee')
  expect(api.writes).toHaveLength(1)
})

test('直接访问管理员修改地址无法提交，详情失败不能提交空资料', async ({ page }) => {
  const api = await mockEmployees(page)
  await page.goto('/employee/add?id=1')
  await expect(page).toHaveURL('/employee')
  await expect(page.getByRole('alert').filter({ hasText: '管理员账号不可修改' })).toBeVisible()
  api.failures.detail = '员工详情读取失败'
  await page.goto('/employee/add?id=2')
  await expect(page.getByRole('alert').filter({ hasText: '员工详情读取失败' })).toBeVisible()
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '重试', exact: true })).toBeVisible()
  api.failures.detail = ''
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.getByLabel('账号:', { exact: true })).toHaveValue('staff2')
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeEnabled()
  expect(api.writes).toHaveLength(0)
})

test('列表加载错误可重试，修改自己的资料同步顶部姓名并在刷新后保留', async ({ page }) => {
  const api = await mockEmployees(page)
  api.failures.list = '员工列表读取失败'
  await page.goto('/employee')
  await expect(page.getByText('员工列表读取失败', { exact: true }).first()).toBeVisible()
  api.failures.list = ''
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await page
    .getByRole('row')
    .filter({ hasText: '测试店长' })
    .getByRole('button', { name: '修改' })
    .click()
  await page.getByLabel('账号:', { exact: true }).fill('manager7')
  await page.getByLabel('员工姓名:', { exact: true }).fill('新的店长姓名')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('button', { name: '新的店长姓名', exact: true })).toBeVisible()
  expect(
    await page.evaluate(() => JSON.parse(localStorage.getItem('sky-admin-employee')!)),
  ).toEqual({
    id: 7,
    userName: 'manager7',
    name: '新的店长姓名',
  })
  await page.reload()
  await expect(page.getByRole('button', { name: '新的店长姓名', exact: true })).toBeVisible()
})
