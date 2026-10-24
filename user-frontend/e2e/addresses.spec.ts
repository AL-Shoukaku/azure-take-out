import { expect, test, type Page } from '@playwright/test'
import type { Address, AddressFormData } from '../src/api/addresses'
import { restoreSession } from './helpers/session'

const home: Address = {
  id: 1,
  userId: 7,
  consignee: '小蓝',
  phone: '13800000000',
  sex: '0',
  provinceCode: '330000',
  provinceName: '浙江省',
  cityCode: '330100',
  cityName: '杭州市',
  districtCode: '330106',
  districtName: '西湖区',
  detail: '文三路 88 号 2 幢 301 室',
  label: '家',
  isDefault: 1,
}
const school: Address = { ...home, id: 2, label: '3', detail: '学府路 8 号宿舍楼', isDefault: 0 }

async function mockAddresses(page: Page, initial = [home, school]) {
  let addresses = structuredClone(initial)
  const writes: { method: string; data: Record<string, unknown> }[] = []
  await page.route('**/api/addressBook**', async (route) => {
    const request = route.request()
    expect(request.headers().authentication).toBe('e2e-user-token')
    const url = new URL(request.url())
    const method = request.method()
    if (method === 'GET') {
      const data = url.pathname.endsWith('/list')
        ? addresses
        : addresses.find((a) => a.id === Number(url.pathname.split('/').at(-1)))
      return route.fulfill({ json: { code: 1, data, msg: null } })
    }
    const data =
      method === 'DELETE' ? { id: Number(url.searchParams.get('id')) } : request.postDataJSON()
    writes.push({ method, data })
    if (method === 'POST') addresses.push({ ...data, id: 3, userId: 7, sex: null, isDefault: 0 })
    else if (method === 'DELETE') addresses = addresses.filter((a) => a.id !== data.id)
    else if (url.pathname.endsWith('/default')) {
      addresses = addresses.map((a) => ({ ...a, isDefault: a.id === data.id ? 1 : 0 }))
    } else addresses = addresses.map((a) => (a.id === data.id ? { ...a, ...data } : a))
    return route.fulfill({ json: { code: 1, data: null, msg: null } })
  })
  return writes
}

async function fillAddress(page: Page, overrides: Partial<AddressFormData> = {}) {
  const data = {
    consignee: '测试收货人',
    phone: '13900000000',
    provinceName: '江苏省',
    cityName: '南京市',
    districtName: '鼓楼区',
    detail: '中山路 18 号 502 室',
    ...overrides,
  }
  const dialog = page.getByRole('dialog')
  for (const [label, value] of [
    ['收货人', data.consignee],
    ['手机号', data.phone],
    ['省份', data.provinceName],
    ['城市', data.cityName],
    ['区县', data.districtName],
    ['详细地址', data.detail],
  ])
    await dialog.getByLabel(label!, { exact: true }).fill(value!)
}

test.beforeEach(async ({ page }) => restoreSession(page))

test('两列地址展示完整信息，默认与配送选择独立，切换路由保留选择', async ({ page }) => {
  const writes = await mockAddresses(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/addresses')
  await expect(page.getByRole('heading', { name: '收货地址', exact: true })).toBeVisible()
  await expect(page.getByText('把一份热乎，送到你身边。')).toBeVisible()
  const cards = page.getByRole('article')
  await expect(cards).toHaveCount(2)
  await expect(cards.nth(0)).toContainText('浙江省 杭州市 西湖区')
  await expect(cards.nth(0)).toContainText('小蓝')
  await expect(cards.nth(0)).toContainText('13800000000')
  await expect(cards.nth(1)).toContainText('学校')
  expect((await cards.nth(0).boundingBox())!.y).toBe((await cards.nth(1).boundingBox())!.y)
  await expect(cards.nth(0).getByRole('button', { name: '已选中' })).toHaveAttribute(
    'aria-pressed',
    'true',
  )
  await cards.nth(1).getByRole('button', { name: '配送到这里' }).click()
  await expect(cards.nth(1)).toHaveCSS('border-top-color', 'rgb(37, 99, 235)')
  await expect(cards.nth(0).getByText('默认', { exact: true })).toBeVisible()
  expect(writes).toHaveLength(0)
  await page.getByRole('navigation').getByRole('link', { name: '在线点餐' }).click()
  await page.getByRole('navigation').getByRole('link', { name: '收货地址' }).click()
  await expect(cards.nth(1).getByRole('button', { name: '已选中' })).toBeVisible()
})

test('设为默认成功后立即选中且只显示一个默认标记', async ({ page }) => {
  const writes = await mockAddresses(page)
  await page.goto('/addresses')
  const card = page.getByRole('article').nth(1)
  await card.getByRole('button', { name: '设为默认', exact: true }).click()
  await expect(card.getByRole('button', { name: '已选中' })).toBeVisible()
  await expect(page.getByRole('article').getByText('默认', { exact: true })).toHaveCount(1)
  expect(writes).toEqual([{ method: 'PUT', data: { id: 2 } }])
  await page.reload()
  await expect(card.getByRole('button', { name: '已选中' })).toBeVisible()
})

test('新增表单校验必填和手机格式，无默认复选框，保存后列表回显', async ({ page }) => {
  const writes = await mockAddresses(page)
  await page.goto('/addresses')
  await page.getByRole('button', { name: '新增地址', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '新增地址', exact: true })
  await expect(dialog.getByRole('checkbox')).toHaveCount(0)
  await dialog.getByRole('button', { name: '保存地址' }).click()
  await expect(dialog.getByText('请输入收货人', { exact: true })).toBeVisible()
  expect(writes).toHaveLength(0)
  await fillAddress(page, { phone: '12345' })
  await dialog.getByRole('button', { name: '保存地址' }).click()
  await expect(dialog.getByText('请输入正确的手机号', { exact: true })).toBeVisible()
  await dialog.getByLabel('手机号', { exact: true }).fill('13900000000')
  await dialog.getByRole('radiogroup').getByText('公司', { exact: true }).click()
  await expect(dialog.getByRole('radio', { name: '公司', exact: true })).toBeChecked()
  await dialog.getByRole('button', { name: '保存地址' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('article')).toHaveCount(3)
  await expect(page.getByRole('article').nth(2)).toContainText('公司')
  expect(writes[0]).toMatchObject({
    method: 'POST',
    data: { label: '公司', consignee: '测试收货人' },
  })
  expect(writes[0]!.data).not.toHaveProperty('isDefault')
  expect(writes[0]!.data).not.toHaveProperty('sex')
  expect(writes[0]!.data).not.toHaveProperty('userId')
})

test('编辑读取最新详情，保留默认标记及未修改的省市区编码', async ({ page }) => {
  const writes = await mockAddresses(page)
  await page.goto('/addresses')
  await page.getByRole('article').first().getByRole('button', { name: '编辑', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '编辑地址', exact: true })
  await expect(dialog.getByLabel('收货人', { exact: true })).toHaveValue('小蓝')
  await expect(dialog.getByRole('radio', { name: '家', exact: true })).toBeChecked()
  await dialog.getByLabel('详细地址', { exact: true }).fill('文三路 88 号 2 幢 302 室')
  await dialog.getByRole('button', { name: '保存地址' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('article').first()).toContainText('302 室')
  await expect(page.getByRole('article').first().getByText('默认', { exact: true })).toBeVisible()
  expect(writes[0]!.data).toMatchObject({
    id: 1,
    provinceCode: '330000',
    cityCode: '330100',
    districtCode: '330106',
  })
  expect(writes[0]!.data).not.toHaveProperty('isDefault')
})

test('修改地区后清除不再匹配的编码，关闭再新增不残留编辑资料', async ({ page }) => {
  const writes = await mockAddresses(page)
  await page.goto('/addresses')
  await page.getByRole('article').first().getByRole('button', { name: '编辑', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByLabel('省份', { exact: true })).toHaveValue('浙江省')
  await fillAddress(page)
  await dialog.getByRole('button', { name: '保存地址' }).click()
  await expect(dialog).toBeHidden()
  expect(writes[0]!.data).toMatchObject({ provinceCode: '', cityCode: '', districtCode: '' })
  await page.getByRole('button', { name: '新增地址', exact: true }).click()
  await expect(dialog.getByLabel('收货人', { exact: true })).toHaveValue('')
  await expect(dialog.getByLabel('详细地址', { exact: true })).toHaveValue('')
})

test('删除需确认，取消保留地址；删除选中地址后选回默认地址', async ({ page }) => {
  const writes = await mockAddresses(page)
  await page.goto('/addresses')
  const card = page.getByRole('article').nth(1)
  await card.getByRole('button', { name: '配送到这里' }).click()
  await card.getByRole('button', { name: '删除', exact: true }).click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  expect(writes).toHaveLength(0)
  await card.getByRole('button', { name: '删除', exact: true }).click()
  await page.getByRole('button', { name: '确认删除', exact: true }).click()
  await expect(page.getByRole('article')).toHaveCount(1)
  await expect(
    page.getByRole('article').first().getByRole('button', { name: '已选中' }),
  ).toBeVisible()
  expect(writes).toEqual([{ method: 'DELETE', data: { id: 2 } }])
})

test('空列表、加载失败和重新加载', async ({ page }) => {
  await mockAddresses(page, [])
  await page.route('**/api/addressBook/list', (route) =>
    route.fulfill({ json: { code: 0, msg: '地址暂时不可用' } }),
  )
  await page.goto('/addresses')
  await expect(page.getByText('地址暂时不可用', { exact: true })).toBeVisible()
  const illustration = page.getByRole('img', { name: '暂无收货地址插画' })
  await expect(illustration).toHaveCount(0)
  await page.unroute('**/api/addressBook/list')
  await page.getByRole('button', { name: '重新加载' }).click()
  await expect(page.getByText('还没有收货地址', { exact: true })).toBeVisible()
  await expect(illustration).toHaveJSProperty('naturalWidth', 640)
  await page.setViewportSize({ width: 375, height: 667 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
  await page.getByRole('button', { name: '新增地址', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await fillAddress(page)
  await dialog.getByRole('button', { name: '保存地址' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('article')).toHaveCount(1)
  await expect(illustration).toHaveCount(0)
})

test('保存或设置默认失败时保留输入和原选择，允许重试', async ({ page }) => {
  await mockAddresses(page)
  await page.route('**/api/addressBook/default', (route) =>
    route.fulfill({ json: { code: 0, msg: '默认设置失败' } }),
  )
  await page.goto('/addresses')
  await page.getByRole('button', { name: '设为默认', exact: true }).click()
  await expect(page.getByText('默认设置失败', { exact: true })).toBeVisible()
  await expect(
    page.getByRole('article').first().getByRole('button', { name: '已选中' }),
  ).toBeVisible()
  await page.route('**/api/addressBook', (route) =>
    route.fulfill({ json: { code: 0, msg: '保存失败，请重试' } }),
  )
  await page.getByRole('button', { name: '新增地址', exact: true }).click()
  await fillAddress(page)
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: '保存地址' }).click()
  await expect(page.getByText('保存失败，请重试', { exact: true })).toBeVisible()
  await expect(dialog.getByLabel('收货人', { exact: true })).toHaveValue('测试收货人')
  await page.unroute('**/api/addressBook')
  await dialog.getByRole('button', { name: '保存地址' }).click()
  await expect(dialog).toBeHidden()
})

test('小屏单列、长地址不横向溢出，弹窗背景虚化且表单可滚动', async ({ page }) => {
  await mockAddresses(page, [{ ...home, detail: '一条很长的收货地址'.repeat(20) }, school])
  await page.setViewportSize({ width: 375, height: 667 })
  await page.goto('/addresses')
  const cards = page.getByRole('article')
  await expect(cards).toHaveCount(2)
  expect((await cards.nth(1).boundingBox())!.y).toBeGreaterThan(
    (await cards.nth(0).boundingBox())!.y,
  )
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
  await page.getByRole('button', { name: '新增地址', exact: true }).click()
  await expect(page.locator('.user-dialog-overlay').filter({ visible: true })).toHaveCSS(
    'backdrop-filter',
    'blur(6px)',
  )
  const dialog = page.getByRole('dialog')
  await fillAddress(page)
  await dialog.getByRole('radiogroup').getByText('其它', { exact: true }).click()
  await expect(dialog.getByRole('button', { name: '保存地址' })).toBeInViewport()
  expect((await dialog.locator('.el-dialog').boundingBox())!.width).toBeLessThanOrEqual(343)
  await dialog.getByRole('button', { name: '保存地址' }).click()
  await expect(dialog).toBeHidden()
})

test('编辑详情加载失败可重试，取消编辑保留原资料', async ({ page }) => {
  const writes = await mockAddresses(page)
  await page.route(
    '**/api/addressBook/1',
    (route) => route.fulfill({ json: { code: 0, msg: '地址详情暂时不可用' } }),
    { times: 1 },
  )
  await page.goto('/addresses')
  await page.getByRole('article').first().getByRole('button', { name: '编辑', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText('地址详情暂时不可用', { exact: true })).toBeVisible()
  await expect(dialog.getByRole('button', { name: '保存地址' })).toBeDisabled()
  await dialog.getByRole('button', { name: '重新加载' }).click()
  await expect(dialog.getByLabel('收货人', { exact: true })).toHaveValue('小蓝')
  await dialog.getByLabel('收货人', { exact: true }).fill('未保存的名字')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('article').first()).toContainText('小蓝')
  expect(writes).toHaveLength(0)
})

test('关闭编辑后迟到的详情不能覆盖重新打开的新增表单', async ({ page }) => {
  await mockAddresses(page)
  let release!: () => void
  let notify!: () => void
  const started = new Promise<void>((resolve) => {
    notify = resolve
  })
  await page.route('**/api/addressBook/1', async (route) => {
    await new Promise<void>((resolve) => {
      release = resolve
      notify()
    })
    await route.fulfill({ json: { code: 1, msg: null, data: home } })
  })
  await page.goto('/addresses')
  await page.getByRole('article').first().getByRole('button', { name: '编辑', exact: true }).click()
  await started
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(dialog).toBeHidden()
  await page.getByRole('button', { name: '新增地址', exact: true }).click()
  await dialog.getByLabel('收货人', { exact: true }).fill('新的收货人')
  const response = page.waitForResponse('**/api/addressBook/1')
  release()
  await response
  await expect(dialog.getByLabel('收货人', { exact: true })).toHaveValue('新的收货人')
  await expect(dialog.getByLabel('详细地址', { exact: true })).toHaveValue('')
})
