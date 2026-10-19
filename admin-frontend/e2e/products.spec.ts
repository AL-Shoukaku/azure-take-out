/* eslint-disable playwright/no-conditional-in-test, playwright/no-conditional-expect -- 两类商品共用流程，分别验证口味与套餐菜品字段 */
import { expect, test, type Page } from '@playwright/test'
import { mockAdminSession } from './helpers/admin.js'
import { imageBuffer, imageURL, mockProducts, type Kind } from './helpers/products.js'

test.beforeEach(async ({ page, context, baseURL }) => {
  await mockAdminSession(page, context, baseURL!)
})

async function select(page: Page, label: string, option: string) {
  await page
    .locator('.el-select')
    .filter({ has: page.getByRole('combobox', { name: label, exact: true }) })
    .click()
  await page
    .getByRole('listbox', { name: label, exact: true })
    .getByRole('option', { name: option, exact: true })
    .click()
}
function productRow(page: Page, name: string) {
  return page.getByRole('row').filter({ has: page.getByRole('cell', { name, exact: true }) })
}
async function upload(page: Page) {
  await page
    .locator('input[type=file]')
    .setInputFiles({ name: 'food.png', mimeType: 'image/png', buffer: imageBuffer })
  await expect(page.getByAltText('已上传图片')).toHaveAttribute('src', imageURL)
}

for (const kind of ['dish', 'setmeal'] as Kind[]) {
  const label = kind === 'dish' ? '菜品' : '套餐'

  test(`${label}组合查询、分页、刷新及清空选择`, async ({ page }) => {
    const api = await mockProducts(page)
    await page.goto(`/${kind}`)
    await expect(page.getByRole('region', { name: `${label}管理`, exact: true })).toBeVisible()
    await expect(page.getByText('2026-10-18 12:34', { exact: true }).first()).toBeVisible()
    await page.getByRole('button', { name: '下一页' }).click()
    await expect.poll(() => api.queries.at(-1)?.params.get('page')).toBe('2')
    await page.getByLabel(`${label}名称查询`, { exact: true }).fill(`${label}1`)
    await select(page, `${label}分类查询`, kind === 'dish' ? '川味招牌' : '单人套餐')
    await select(page, '售卖状态查询', '停售')
    await page.getByRole('button', { name: '查询', exact: true }).click()
    await expect
      .poll(() => Object.fromEntries(api.queries.at(-1)!.params))
      .toMatchObject({
        name: `${label}1`,
        categoryId: kind === 'dish' ? '1' : '7',
        status: '0',
        page: '1',
      })
    const filteredCount = api.records[kind].filter(
      (r) =>
        r.name.includes(`${label}1`) &&
        r.categoryId === (kind === 'dish' ? 1 : 7) &&
        r.status === 0,
    ).length
    await expect(page.getByText(`共 ${filteredCount} 条`, { exact: true })).toBeVisible()
    await page.reload()
    await expect(page.getByText('共 25 条', { exact: true })).toBeVisible()
    await page
      .getByRole('row')
      .filter({ hasText: `${label}1` })
      .first()
      .locator('.el-checkbox')
      .click()
    await page.getByRole('button', { name: '下一页' }).click()
    await page.getByRole('button', { name: '批量删除', exact: true }).click()
    await expect(page.getByRole('alert').filter({ hasText: '请选择删除对象' })).toBeVisible()
    expect(api.writes).toHaveLength(0)
  })

  test(`${label}删除确认、批量 ID、后端错误和起停售`, async ({ page }) => {
    const api = await mockProducts(page)
    await page.goto(`/${kind}`)
    const row = productRow(page, `${label}1`)
    await row.getByRole('button', { name: '删除', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: '取消', exact: true }).click()
    expect(api.writes).toHaveLength(0)
    api.failures.delete = '起售中的商品不能删除'
    await row.getByRole('button', { name: '删除', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: '删除', exact: true }).click()
    await expect(page.getByRole('alert').filter({ hasText: api.failures.delete })).toBeVisible()
    api.failures.delete = ''
    await row.locator('.el-checkbox').click()
    await page
      .getByRole('row')
      .filter({ has: page.getByRole('cell', { name: `${label}3`, exact: true }) })
      .locator('.el-checkbox')
      .click()
    await page.getByRole('button', { name: '批量删除', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: '删除', exact: true }).click()
    await expect(page.getByText('共 23 条', { exact: true })).toBeVisible()
    expect(api.writes.at(-1)).toMatchObject({ method: 'DELETE', ids: '1,3' })
    const activeRow = productRow(page, `${label}2`)
    await activeRow.getByRole('button', { name: '停售', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: '确定', exact: true }).click()
    await expect(activeRow.getByRole('button', { name: '起售', exact: true })).toBeVisible()
    await activeRow.getByRole('button', { name: '起售', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: '确定', exact: true }).click()
    await expect(activeRow.getByRole('button', { name: '停售', exact: true })).toBeVisible()
  })

  test(`${label}新增校验、图片上传、保存并继续添加`, async ({ page }) => {
    const api = await mockProducts(page)
    await page.goto(`/${kind}/add`)
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect(page.getByText(`请输入${label}名称`, { exact: true })).toBeVisible()
    await expect(page.getByText(`${label}图片不能为空`, { exact: true })).toBeVisible()
    await page.getByLabel(`${label}名称:`, { exact: true }).fill('新增招牌')
    await select(page, `${label}分类`, kind === 'dish' ? '川味招牌' : '单人套餐')
    await page.getByLabel(`${label}价格:`, { exact: true }).fill('0')
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect(
      page.getByText(`${label}价格格式有误，请输入大于零且最多保留两位小数的金额`, { exact: true }),
    ).toBeVisible()
    await page.getByLabel(`${label}价格:`, { exact: true }).fill('18.25')
    await upload(page)
    if (kind === 'dish') {
      await page.getByRole('button', { name: /添加口味/ }).click()
      await select(page, '口味类型1', '甜味')
      await page.getByRole('button', { name: '移除无糖', exact: true }).click()
    } else {
      await page.getByRole('button', { name: /添加菜品/ }).click()
      await page
        .getByRole('dialog', { name: '添加菜品', exact: true })
        .locator('.picker-dish-row')
        .filter({ hasText: '菜品2' })
        .first()
        .click()
      await page.getByRole('dialog').getByRole('button', { name: '添加', exact: true }).click()
      await page.getByRole('spinbutton', { name: '菜品2份数', exact: true }).fill('2')
      await page.getByRole('spinbutton', { name: '菜品2份数', exact: true }).blur()
    }
    await page.getByRole('button', { name: '保存并继续添加', exact: true }).click()
    await expect(page.getByLabel(`${label}名称:`, { exact: true })).toBeEmpty()
    await expect(page.getByAltText('已上传图片')).toHaveCount(0)
    expect(api.writes[0]).toMatchObject({
      kind,
      method: 'POST',
      body: { name: '新增招牌', price: 18.25, status: 0, image: imageURL },
    })
    if (kind === 'dish')
      expect(api.writes[0]!.body!.flavors).toEqual([
        { name: '甜味', value: '["少糖","半糖","多糖","全糖"]' },
      ])
    else
      expect(api.writes[0]!.body!.setmealDishes).toEqual([
        { dishId: 2, name: '菜品2', price: 13.5, copies: 2 },
      ])
  })

  test(`${label}编辑回显、失败保留输入及保存返回`, async ({ page }) => {
    const api = await mockProducts(page)
    await page.goto(`/${kind}/add?id=2`)
    await expect(page.getByLabel(`${label}名称:`, { exact: true })).toHaveValue(`${label}2`)
    await expect(page.getByAltText('已上传图片')).toHaveAttribute('src', imageURL)
    await expect(page.getByRole('button', { name: '保存并继续添加' })).toHaveCount(0)
    if (kind === 'dish')
      await expect(page.getByRole('button', { name: '移除微辣', exact: true })).toBeVisible()
    else
      await expect(page.getByRole('spinbutton', { name: '菜品2份数', exact: true })).toHaveValue(
        '3',
      )
    await page.getByLabel(`${label}名称:`, { exact: true }).fill('修改招牌')
    api.failures.save = '名称已存在'
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect(page.getByRole('alert').filter({ hasText: '名称已存在' })).toBeVisible()
    await expect(page.getByLabel(`${label}名称:`, { exact: true })).toHaveValue('修改招牌')
    api.failures.save = ''
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect(page).toHaveURL(`/${kind}`)
    expect(api.writes.at(-1)).toMatchObject({
      method: 'PUT',
      body: { id: 2, name: '修改招牌', status: 1 },
    })
  })

  test(`${label}详情及列表加载失败支持重试`, async ({ page }) => {
    const api = await mockProducts(page)
    api.failures.list = '列表暂不可用'
    await page.goto(`/${kind}`)
    await expect(page.getByRole('alert')).toContainText('列表暂不可用')
    api.failures.list = ''
    await page.getByRole('button', { name: '重试', exact: true }).click()
    await expect(page.getByText('共 25 条', { exact: true })).toBeVisible()
    api.failures.detail = '详情暂不可用'
    await page.goto(`/${kind}/add?id=2`)
    await expect(page.getByRole('alert')).toContainText('详情暂不可用')
    await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
    api.failures.detail = ''
    await page.getByRole('button', { name: '重试', exact: true }).click()
    await expect(page.getByLabel(`${label}名称:`, { exact: true })).toHaveValue(`${label}2`)
  })
}

test('图片类型、1MB 边界、后端失败、删除及重新上传', async ({ page }) => {
  const api = await mockProducts(page)
  await page.goto('/dish/add')
  const input = page.locator('input[type=file]')
  await input.setInputFiles({ name: 'bad.gif', mimeType: 'image/gif', buffer: imageBuffer })
  await expect(
    page.getByRole('alert').filter({ hasText: '仅能上传 PNG JPEG JPG类型图片' }),
  ).toBeVisible()
  await input.setInputFiles({
    name: 'big.png',
    mimeType: 'image/png',
    buffer: Buffer.alloc(1024 * 1024 + 1),
  })
  await expect(page.getByRole('alert').filter({ hasText: '上传文件大小不能超过1M!' })).toBeVisible()
  expect(api.uploads).toBe(0)
  api.failures.upload = '图片上传失败'
  await input.setInputFiles({ name: 'food.png', mimeType: 'image/png', buffer: imageBuffer })
  await expect(page.getByRole('alert').filter({ hasText: '图片上传失败' })).toBeVisible()
  await expect(page.getByAltText('已上传图片')).toHaveCount(0)
  api.failures.upload = ''
  await input.setInputFiles({
    name: 'limit.png',
    mimeType: 'image/png',
    buffer: Buffer.concat([imageBuffer, Buffer.alloc(1024 * 1024 - imageBuffer.length)]),
  })
  await expect(page.getByAltText('已上传图片')).toHaveAttribute('src', imageURL)
  await page.locator('.upload-preview').hover()
  await page.getByRole('button', { name: '删除图片', exact: true }).click()
  await expect(page.getByAltText('已上传图片')).toHaveCount(0)
  await upload(page)
  await page.locator('.upload-preview').hover()
  const chooser = page.waitForEvent('filechooser')
  await page.getByRole('button', { name: '重新上传', exact: true }).click()
  await (await chooser).setFiles({ name: 'new.jpg', mimeType: 'image/jpeg', buffer: imageBuffer })
  await expect.poll(() => api.uploads).toBe(4)
})

test('套餐选菜跨分类选择、取消回滚、保留份数及按 ID 删除', async ({ page }) => {
  await mockProducts(page)
  await page.goto('/setmeal/add?id=2')
  await page.getByRole('button', { name: /添加菜品/ }).click()
  const dialog = page.getByRole('dialog', { name: '添加菜品', exact: true })
  await dialog.getByRole('button', { name: '家常热菜', exact: true }).click()
  await dialog
    .locator('.picker-dish-row')
    .filter({ has: page.getByText('菜品4', { exact: true }) })
    .click()
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.getByRole('row').filter({ hasText: '菜品4' })).toHaveCount(0)
  await page.getByRole('button', { name: /添加菜品/ }).click()
  await dialog.getByRole('button', { name: '家常热菜', exact: true }).click()
  await dialog
    .locator('.picker-dish-row')
    .filter({ has: page.getByText('菜品4', { exact: true }) })
    .click()
  await dialog.getByRole('button', { name: '添加', exact: true }).click()
  await expect(page.getByRole('spinbutton', { name: '菜品2份数', exact: true })).toHaveValue('3')
  await expect(page.getByRole('spinbutton', { name: '菜品4份数', exact: true })).toHaveValue('1')
  await page
    .getByRole('row')
    .filter({ hasText: '菜品4' })
    .getByRole('button', { name: '删除', exact: true })
    .click()
  await expect(page.getByRole('row').filter({ hasText: '菜品4' })).toHaveCount(0)
})

test('提交期间阻止重复提交，取消、侧栏导航均不可打断保存', async ({ page }) => {
  const api = await mockProducts(page)
  api.delaySave = true
  await page.goto('/dish/add?id=2')
  await expect(page.getByLabel('菜品名称:', { exact: true })).toHaveValue('菜品2')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '取消', exact: true })).toBeDisabled()
  await page.getByRole('menuitem', { name: '分类管理', exact: true }).click()
  await expect(page).toHaveURL('/dish/add?id=2')
  expect(api.writes).toHaveLength(1)
  api.releaseSave()
  await expect(page).toHaveURL('/dish')
})

test('四类口味默认选项、类型去重、移除后重新选择', async ({ page }) => {
  await mockProducts(page)
  await page.goto('/dish/add')
  const presets = [
    ['甜味', ['无糖', '少糖', '半糖', '多糖', '全糖']],
    ['温度', ['热饮', '常温', '去冰', '少冰', '多冰']],
    ['忌口', ['不要葱', '不要蒜', '不要香菜', '不要辣']],
    ['辣度', ['不辣', '微辣', '中辣', '重辣']],
  ] as const
  for (const [index, [name, values]] of presets.entries()) {
    await page.getByRole('button', { name: /添加口味/ }).click()
    await select(page, `口味类型${index + 1}`, name)
    for (const value of values)
      await expect(page.getByRole('button', { name: `移除${value}`, exact: true })).toBeVisible()
  }
  await expect(page.getByRole('button', { name: /添加口味/ })).toHaveCount(0)
  await page.getByRole('button', { name: '移除无糖', exact: true }).click()
  await select(page, '添加甜味选项', '无糖')
  await expect(page.getByRole('button', { name: '移除无糖', exact: true })).toBeVisible()
  await page
    .locator('.el-select')
    .filter({ has: page.getByRole('combobox', { name: '口味类型2', exact: true }) })
    .click()
  await expect(
    page
      .getByRole('listbox', { name: '口味类型2', exact: true })
      .getByRole('option', { name: '甜味', exact: true }),
  ).toHaveAttribute('aria-disabled', 'true')
  await page.keyboard.press('Escape')
})

test('套餐必须包含菜品，份数限制 1–99，分类和选菜加载可重试', async ({ page }) => {
  const api = await mockProducts(page)
  await page.goto('/setmeal/add')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('套餐下菜品不能为空', { exact: true })).toBeVisible()
  api.failures.categories = '分类加载失败'
  await page.getByRole('button', { name: /添加菜品/ }).click()
  const dialog = page.getByRole('dialog', { name: '添加菜品', exact: true })
  await expect(dialog.getByRole('alert')).toContainText('分类加载失败')
  api.failures.categories = ''
  api.failures.dishes = '选菜加载失败'
  await dialog.getByRole('button', { name: '重试', exact: true }).click()
  await expect(dialog.getByRole('alert')).toContainText('选菜加载失败')
  api.failures.dishes = ''
  await dialog.getByRole('button', { name: '重试', exact: true }).click()
  await dialog
    .locator('.picker-dish-row')
    .filter({ has: page.getByText('菜品2', { exact: true }) })
    .click()
  await dialog.getByRole('button', { name: '添加', exact: true }).click()
  const copies = page.getByRole('spinbutton', { name: '菜品2份数', exact: true })
  await copies.fill('100')
  await copies.blur()
  await expect(copies).toHaveValue('99')
  await copies.fill('0')
  await copies.blur()
  await expect(copies).toHaveValue('1')
  expect(api.writes).toHaveLength(0)
})

test('商品分页条数、跳页及末页删除后回到有效页', async ({ page }) => {
  const api = await mockProducts(page, 41)
  await page.goto('/dish')
  const sizes = page.getByRole('navigation', { name: '分页' }).locator('.el-select')
  await sizes.click()
  await page.getByRole('option', { name: '20条/页', exact: true }).click()
  await expect.poll(() => api.queries.at(-1)?.params.get('pageSize')).toBe('20')
  const jumper = page.getByRole('spinbutton', { name: '页', exact: true })
  await jumper.fill('3')
  await jumper.press('Enter')
  await expect(page.getByRole('row').filter({ hasText: '菜品41' })).toBeVisible()
  await page
    .getByRole('row')
    .filter({ hasText: '菜品41' })
    .getByRole('button', { name: '删除', exact: true })
    .click()
  await page.getByRole('dialog').getByRole('button', { name: '删除', exact: true }).click()
  await expect.poll(() => api.queries.at(-1)?.params.get('page')).toBe('2')
  await expect(page.getByText('共 40 条', { exact: true })).toBeVisible()
})
