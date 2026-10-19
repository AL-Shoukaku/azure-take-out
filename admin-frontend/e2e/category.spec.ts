import { expect, test } from '@playwright/test'
import { mockAdminSession } from './helpers/admin.js'
import { mockCategories } from './helpers/categories.js'

test.beforeEach(async ({ page, context, baseURL }) => {
  await mockAdminSession(page, context, baseURL!)
})

test('分类表格、分页、名称和类型组合查询、清空及空结果', async ({ page }) => {
  const api = await mockCategories(page)
  await page.goto('/category')
  await expect(page.getByRole('columnheader')).toHaveText([
    '分类名称',
    '分类类型',
    '排序',
    '分类状态',
    '操作时间',
    '操作',
  ])
  const first = page.getByRole('row').filter({ hasText: '川味招牌' })
  await expect(first.getByRole('cell', { name: '2026-09-28 12:00', exact: true })).toBeVisible()
  await expect(first.getByRole('button')).toHaveText(['修改', '删除', '禁用'])
  await expect(page.getByText('共 45 条', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '下一页' }).click()
  await expect(page.getByRole('row').filter({ hasText: '菜品分类11' })).toBeVisible()
  await page.getByLabel('分类名称查询').fill('套餐')
  await page.locator('.category-type-select').click()
  await expect(page.getByRole('option')).toHaveText(['菜品分类', '套餐分类'])
  await page.getByRole('option', { name: '套餐分类', exact: true }).click()
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByText('共 22 条', { exact: true })).toBeVisible()
  expect(api.queries.at(-1)?.get('page')).toBe('1')
  expect(api.queries.at(-1)?.get('name')).toBe('套餐')
  expect(api.queries.at(-1)?.get('type')).toBe('2')
  await page.getByLabel('分类名称查询').fill('不存在')
  await page.getByLabel('分类名称查询').press('Enter')
  await expect(page.getByText('暂无数据', { exact: true })).toBeVisible()
  await page.getByLabel('分类名称查询').fill('')
  await page.getByLabel('分类名称查询').press('Enter')
  await expect(page.getByText('共 22 条', { exact: true })).toBeVisible()
  await page.locator('.category-type-select').hover()
  await page.locator('.category-type-select .el-select__clear').click()
  await expect(page.getByText('共 45 条', { exact: true })).toBeVisible()
  const pagination = page.getByRole('navigation', { name: '分页' })
  await pagination.getByText('10条/页', { exact: true }).click()
  await expect(page.getByRole('option')).toHaveText(['10条/页', '20条/页', '30条/页', '40条/页'])
  await page.getByRole('option', { name: '20条/页', exact: true }).click()
  await expect.poll(() => api.queries.at(-1)?.get('pageSize')).toBe('20')
  await page.getByRole('spinbutton', { name: '页', exact: true }).fill('3')
  await page.getByRole('spinbutton', { name: '页', exact: true }).press('Enter')
  await expect(page.getByRole('row').filter({ hasText: '菜品分类45' })).toBeVisible()
  await pagination.getByText('20条/页', { exact: true }).click()
  await pagination.getByRole('combobox').press('ArrowDown')
  await pagination.getByRole('combobox').press('ArrowDown')
  await pagination.getByRole('combobox').press('Enter')
  await expect.poll(() => api.queries.at(-1)?.get('pageSize')).toBe('40')
  await expect(page.getByRole('row').filter({ hasText: '川味招牌' })).toBeVisible()
  expect(api.queries.at(-1)?.get('page')).toBe('1')
})

for (const scenario of [
  { type: 1, label: '菜品分类' },
  { type: 2, label: '套餐分类' },
] as const) {
  test(`新增${scenario.label}支持连续添加、数字排序及默认禁用`, async ({ page }) => {
    const api = await mockCategories(page)
    await page.goto('/category')
    await page.getByRole('button', { name: `+ 新增${scenario.label}`, exact: true }).click()
    const dialog = page.getByRole('dialog', { name: `新增${scenario.label}`, exact: true })
    await dialog.getByLabel('分类名称', { exact: true }).fill('新增分类')
    await dialog.getByLabel('排序', { exact: true }).fill('0')
    await dialog.getByRole('button', { name: '保存并继续添加', exact: true }).click()
    await expect(dialog.getByLabel('分类名称', { exact: true })).toBeEmpty()
    await expect(dialog.getByLabel('排序', { exact: true })).toBeEmpty()
    expect(api.writes[0]).toMatchObject({
      method: 'POST',
      path: '/api/category',
      body: { type: scenario.type, name: '新增分类', sort: 0 },
    })
    await expect(
      page
        .getByRole('row')
        .filter({ hasText: '新增分类' })
        .getByRole('button', { name: '启用', exact: true }),
    ).toBeVisible()
    await dialog.getByLabel('分类名称', { exact: true }).fill('第二个分类')
    await dialog.getByLabel('排序', { exact: true }).fill('1')
    await dialog.getByRole('button', { name: '确定', exact: true }).click()
    await expect(dialog).toBeHidden()
    await expect(page.getByRole('row').filter({ hasText: '第二个分类' })).toBeVisible()
  })
}

test('分类名称及排序沿用原校验，取消或关闭后清空表单', async ({ page }) => {
  const api = await mockCategories(page)
  await page.goto('/category')
  await page.getByRole('button', { name: '+ 新增菜品分类', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '新增菜品分类', exact: true })
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(dialog.getByText('请输入分类名称', { exact: true })).toBeVisible()
  await expect(dialog.getByText('排序不能为空', { exact: true })).toBeVisible()
  await dialog.getByLabel('分类名称', { exact: true }).fill('菜')
  await dialog.getByLabel('排序', { exact: true }).fill('-1')
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(
    dialog.getByText('分类名称输入不符，请输入2-20个字符', { exact: true }),
  ).toBeVisible()
  await expect(dialog.getByText('排序只能输入数字类型', { exact: true })).toBeVisible()
  await dialog.getByLabel('分类名称', { exact: true }).fill('分类1')
  await dialog.getByLabel('排序', { exact: true }).fill('100')
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(dialog.getByText('分类名称包含特殊字符', { exact: true })).toBeVisible()
  await expect(dialog.getByText('排序只能输入0-99数字', { exact: true })).toBeVisible()
  expect(api.writes).toHaveLength(0)
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('button', { name: '+ 新增菜品分类', exact: true }).click()
  await expect(dialog.getByLabel('分类名称', { exact: true })).toBeEmpty()
  await expect(dialog.getByText('排序只能输入0-99数字', { exact: true })).toBeHidden()
  await dialog.getByRole('button', { name: '关闭此对话框' }).click()
  await expect(dialog).toBeHidden()
})

for (const scenario of [
  { id: 1, label: '菜品分类', name: '川味招牌' },
  { id: 2, label: '套餐分类', name: '单人套餐' },
] as const) {
  test(`修改${scenario.label}正确回显，失败保留内容并可重试`, async ({ page }) => {
    const api = await mockCategories(page)
    await page.goto('/category')
    const row = page.getByRole('row').filter({ hasText: scenario.name })
    await row.getByRole('button', { name: '修改', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: `修改${scenario.label}`, exact: true })
    await expect(dialog.getByLabel('分类名称', { exact: true })).toHaveValue(scenario.name)
    await expect(dialog.getByLabel('排序', { exact: true })).toHaveValue(String(scenario.id))
    await expect(dialog.getByRole('button', { name: '保存并继续添加' })).toHaveCount(0)
    await dialog.getByLabel('分类名称', { exact: true }).fill('修改后的分类')
    await dialog.getByLabel('排序', { exact: true }).fill('0')
    api.failures.save = '分类名称已存在'
    await dialog.getByRole('button', { name: '确定', exact: true }).click()
    await expect(page.getByRole('alert').filter({ hasText: '分类名称已存在' })).toBeVisible()
    await expect(dialog.getByLabel('分类名称', { exact: true })).toHaveValue('修改后的分类')
    api.failures.save = ''
    await dialog.getByRole('button', { name: '确定', exact: true }).click()
    await expect(dialog).toBeHidden()
    expect(api.writes[1]).toMatchObject({
      method: 'PUT',
      body: { id: scenario.id, type: scenario.id, name: '修改后的分类', sort: 0 },
    })
    await expect(page.getByRole('row').filter({ hasText: '修改后的分类' })).toBeVisible()
    expect(api.queries.every((query) => query.has('page'))).toBe(true)
  })
}

test('分类保存期间禁用按钮并阻止重复提交', async ({ page }) => {
  const api = await mockCategories(page)
  api.delaySave = true
  await page.goto('/category')
  await page.getByRole('button', { name: '+ 新增套餐分类', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '新增套餐分类', exact: true })
  await dialog.getByLabel('分类名称', { exact: true }).fill('新的套餐')
  await dialog.getByLabel('排序', { exact: true }).fill('0')
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(dialog.getByRole('button', { name: '确定', exact: true })).toBeDisabled()
  await expect(dialog.getByRole('button', { name: '取消', exact: true })).toBeDisabled()
  await expect(dialog.getByRole('button', { name: '保存并继续添加' })).toBeDisabled()
  await page.keyboard.press('Escape')
  await expect(dialog).toBeVisible()
  await expect.poll(() => api.writes.length).toBe(1)
  api.releaseSave()
  await expect(dialog).toBeHidden()
})

test('启停用需要确认，取消不提交，失败不改变原状态', async ({ page }) => {
  const api = await mockCategories(page)
  await page.goto('/category')
  const row = page.getByRole('row').filter({ hasText: '川味招牌' })
  const dialog = page.getByRole('dialog', { name: '提示', exact: true })
  await row.getByRole('button', { name: '禁用', exact: true }).click()
  await expect(dialog).toContainText('确认调整该分类的状态?')
  await dialog.getByRole('button', { name: '取消' }).click()
  expect(api.writes).toHaveLength(0)
  api.failures.status = '分类状态更改失败'
  await row.getByRole('button', { name: '禁用', exact: true }).click()
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(page.getByRole('alert').filter({ hasText: '分类状态更改失败' })).toBeVisible()
  await expect(row.getByRole('button', { name: '禁用', exact: true })).toBeEnabled()
  api.failures.status = ''
  await row.getByRole('button', { name: '禁用', exact: true }).click()
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(row.getByRole('button', { name: '启用', exact: true })).toBeVisible()
  await row.getByRole('button', { name: '启用', exact: true }).click()
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(row.getByRole('button', { name: '禁用', exact: true })).toBeVisible()
  expect(api.writes.map(({ path, id }) => ({ path, id }))).toEqual([
    { path: '/api/category/status/0', id: '1' },
    { path: '/api/category/status/0', id: '1' },
    { path: '/api/category/status/1', id: '1' },
  ])
})

test('删除确认、关联菜品和套餐的错误、末页最后一条删除后回到有效页码', async ({ page }) => {
  const api = await mockCategories(page, 11)
  await page.goto('/category')
  const row = page.getByRole('row').filter({ hasText: '川味招牌' })
  const dialog = page.getByRole('dialog', { name: '确定删除', exact: true })
  await row.getByRole('button', { name: '删除', exact: true }).click()
  await expect(dialog).toContainText('此操作将永久删除该分类，是否继续？')
  await dialog.getByRole('button', { name: '取消' }).click()
  expect(api.writes).toHaveLength(0)
  api.failures.delete = '当前分类关联了菜品,不能删除'
  await row.getByRole('button', { name: '删除', exact: true }).click()
  await dialog.getByRole('button', { name: '删除' }).click()
  await expect(
    page.getByRole('alert').filter({ hasText: '当前分类关联了菜品,不能删除' }),
  ).toBeVisible()
  api.failures.delete = '当前分类关联了套餐,不能删除'
  await row.getByRole('button', { name: '删除', exact: true }).click()
  await dialog.getByRole('button', { name: '删除' }).click()
  await expect(
    page.getByRole('alert').filter({ hasText: '当前分类关联了套餐,不能删除' }),
  ).toBeVisible()
  api.failures.delete = ''
  await page.getByRole('button', { name: '下一页' }).click()
  await page
    .getByRole('row')
    .filter({ hasText: '菜品分类11' })
    .getByRole('button', { name: '删除', exact: true })
    .click()
  await dialog.getByRole('button', { name: '删除' }).click()
  await expect(page.getByText('共 10 条', { exact: true })).toBeVisible()
  await expect(row).toBeVisible()
  expect(api.queries.at(-1)?.get('page')).toBe('1')
})

test('分类加载失败支持重试，刷新后仍访问分类管理', async ({ page }) => {
  const api = await mockCategories(page)
  api.failures.list = '分类列表加载失败'
  await page.goto('/category')
  await expect(page.getByRole('alert')).toContainText('分类列表加载失败')
  api.failures.list = ''
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.getByRole('row').filter({ hasText: '川味招牌' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('row').filter({ hasText: '川味招牌' })).toBeVisible()
  await expect(page.getByRole('menuitem', { name: '分类管理', exact: true })).toHaveClass(
    /is-active/,
  )
})
