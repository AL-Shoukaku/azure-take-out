import { expect, test, type Page } from '@playwright/test'
import { restoreSession } from './helpers/session'

const initialProfile = {
  id: 7,
  account: 'customer',
  username: '小蓝',
  name: '蓝同学',
  sex: '1',
  phone: '13800000000',
  idNumber: '110101199001010011',
  avatar: null as string | null,
}

test.beforeEach(async ({ page }) => {
  await restoreSession(page)
  await page.route('**/api/user/info', (route) =>
    route.fulfill({ json: { code: 1, msg: null, data: initialProfile } }),
  )
})

async function openEditor(page: Page) {
  await page.goto('/profile')
  await page.getByRole('button', { name: '编辑资料', exact: true }).click()
  return page.getByRole('dialog', { name: '编辑资料', exact: true })
}

test('显示个人资料、当前账号和三个白色板块，地址入口可跳转', async ({ page }) => {
  await page.goto('/profile')
  await expect(page.getByRole('heading', { name: '我的', exact: true })).toBeVisible()
  await expect(page.getByText('个人资料与账号设置')).toBeVisible()
  const basic = page.getByRole('region', { name: '基本信息' })
  await expect(basic).toContainText('蓝同学')
  await expect(basic).toContainText('男')
  await expect(basic).toContainText('13800000000')
  await expect(basic).toContainText('110101199001010011')
  await expect(page.getByRole('region', { name: '账号安全' })).toContainText('customer')
  await expect(page.locator('.profile-card')).toHaveCount(3)
  await basic.getByRole('link', { name: '管理收货地址' }).click()
  await expect(page).toHaveURL('/addresses')
})

test('编辑资料回显全部字段，按指定顺序排列并虚化背景', async ({ page }) => {
  const dialog = await openEditor(page)
  await expect(dialog.getByLabel('昵称', { exact: true })).toHaveValue('小蓝')
  await expect(dialog.getByLabel('真实姓名', { exact: true })).toHaveValue('蓝同学')
  await expect(dialog.getByText('男', { exact: true })).toBeVisible()
  await expect(dialog.getByLabel('手机号', { exact: true })).toHaveValue('13800000000')
  await expect(dialog.getByLabel('身份证号', { exact: true })).toHaveValue('110101199001010011')
  await expect(dialog.locator('.el-form-item__label')).toHaveText([
    '头像',
    '昵称',
    '真实姓名',
    '性别',
    '手机号',
    '身份证号',
  ])
  await expect(page.locator('.user-dialog-overlay').last()).toHaveCSS(
    'backdrop-filter',
    'blur(6px)',
  )
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(dialog).toBeHidden()
})

test('保存资料后同步更新页面和导航栏，刷新保留服务器结果', async ({ page }) => {
  let profile = { ...initialProfile }
  await page.route('**/api/user/info', (route) =>
    route.fulfill({ json: { code: 1, msg: null, data: profile } }),
  )
  await page.route('**/api/user/update', async (route) => {
    const data = route.request().postDataJSON()
    expect(data).toMatchObject({
      id: 7,
      username: '新的昵称',
      name: '',
      phone: '',
      idNumber: '',
      sex: '0',
    })
    expect(data).not.toHaveProperty('account')
    profile = { ...profile, ...data }
    await route.fulfill({ json: { code: 1, msg: null, data: null } })
  })
  const dialog = await openEditor(page)
  await dialog.getByLabel('昵称', { exact: true }).fill('新的昵称')
  await dialog.getByLabel('真实姓名', { exact: true }).fill('')
  await dialog.getByLabel('手机号', { exact: true }).fill('')
  await dialog.getByLabel('身份证号', { exact: true }).fill('')
  await dialog.getByRole('combobox', { name: '性别', exact: true }).press('ArrowDown')
  await page.getByRole('option', { name: '女', exact: true }).click()
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('link', { name: '你好，新的昵称', exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('region', { name: '基本信息' })).toContainText('新的昵称')
  await expect(page.getByRole('region', { name: '基本信息' })).toContainText('未填写')
})

test('取消编辑不会更改资料，再次打开恢复原始值', async ({ page }) => {
  const dialog = await openEditor(page)
  await dialog.getByLabel('昵称', { exact: true }).fill('尚未保存')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.getByRole('link', { name: '你好，小蓝', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '编辑资料', exact: true }).click()
  await expect(dialog.getByLabel('昵称', { exact: true })).toHaveValue('小蓝')
})

test('资料校验失败不提交，保存失败保留输入以便重试', async ({ page }) => {
  let submissions = 0
  await page.route('**/api/user/update', async (route) => {
    submissions++
    await route.fulfill({ json: { code: 0, msg: '保存失败，请稍后重试', data: null } })
  })
  const dialog = await openEditor(page)
  await dialog.getByLabel('昵称', { exact: true }).fill('   ')
  await dialog.getByLabel('手机号', { exact: true }).fill('123')
  await dialog.getByLabel('身份证号', { exact: true }).fill('123')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog.getByText('请输入昵称', { exact: true })).toBeVisible()
  await expect(dialog.getByText('请输入正确的手机号')).toBeVisible()
  await expect(dialog.getByText('请输入正确的身份证号')).toBeVisible()
  expect(submissions).toBe(0)
  await dialog.getByLabel('昵称', { exact: true }).fill('重试昵称')
  await dialog.getByLabel('手机号', { exact: true }).fill('')
  await dialog.getByLabel('身份证号', { exact: true }).fill('')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('保存失败，请稍后重试', { exact: true })).toBeVisible()
  await expect(dialog.getByLabel('昵称', { exact: true })).toHaveValue('重试昵称')
  await expect(dialog.getByRole('button', { name: '保存', exact: true })).toBeEnabled()
})

test('头像上传使用 file 字段，保存前不修改页面头像', async ({ page }) => {
  await page.route('**/api/common/upload', async (route) => {
    expect(route.request().headers()['content-type']).toContain('multipart/form-data; boundary=')
    expect(route.request().postDataBuffer()!.toString()).toContain('name="file"')
    await route.fulfill({ json: { code: 1, msg: null, data: '/images/user/new-avatar.png' } })
  })
  await page.route('**/images/user/new-avatar.png', (route) =>
    route.fulfill({
      contentType: 'image/png',
      body: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6N8AAAAASUVORK5CYII=',
        'base64',
      ),
    }),
  )
  const dialog = await openEditor(page)
  await dialog
    .getByLabel('选择头像文件')
    .setInputFiles({ name: 'avatar.png', mimeType: 'image/png', buffer: Buffer.from('image') })
  await expect(dialog.getByRole('img', { name: '头像预览' })).toHaveAttribute(
    'src',
    '/images/user/new-avatar.png',
  )
  await expect(page.getByRole('banner').getByRole('img', { name: '用户头像' })).toHaveCount(0)
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('button', { name: '编辑资料', exact: true }).click()
  await expect(dialog.getByRole('img', { name: '头像预览' })).toHaveCount(0)
})

test('头像限制 PNG/JPEG 和 1 MB，错误文件不上传', async ({ page }) => {
  let uploads = 0
  await page.route('**/api/common/upload', async (route) => {
    uploads++
    await route.fulfill({ json: { code: 1, msg: null, data: '/avatar.png' } })
  })
  const dialog = await openEditor(page)
  await dialog
    .getByLabel('选择头像文件')
    .setInputFiles({ name: 'avatar.gif', mimeType: 'image/gif', buffer: Buffer.from('gif') })
  await expect(page.getByText('仅支持 PNG、JPEG 格式的头像')).toBeVisible()
  await dialog.getByLabel('选择头像文件').setInputFiles({
    name: 'avatar.png',
    mimeType: 'image/png',
    buffer: Buffer.alloc(1024 * 1024 + 1),
  })
  await expect(page.getByText('头像大小不能超过 1 MB')).toBeVisible()
  expect(uploads).toBe(0)
})

test('修改密码校验格式和确认输入，后端错误保留弹窗，成功后清空密码', async ({ page }) => {
  let submissions = 0
  await page.route('**/api/user/editPassword', async (route) => {
    submissions++
    expect(route.request().postDataJSON()).toEqual({
      oldPassword: 'OldPass123',
      newPassword: 'NewPass456',
    })
    await route.fulfill({
      json:
        submissions === 1
          ? { code: 0, msg: '原密码错误', data: null }
          : { code: 1, msg: null, data: null },
    })
  })
  await page.goto('/profile')
  await page.getByRole('button', { name: '修改密码', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '修改密码', exact: true })
  await dialog.getByLabel('旧密码', { exact: true }).fill('OldPass123')
  await dialog.getByLabel('新密码', { exact: true }).fill('short')
  await dialog.getByRole('button', { name: '确认修改', exact: true }).click()
  await expect(dialog.getByText('密码至少 6 位，且只能包含字母和数字')).toBeVisible()
  await dialog.getByLabel('新密码', { exact: true }).fill('NewPass456')
  await dialog.getByLabel('确认新密码', { exact: true }).fill('Different123')
  await dialog.getByRole('button', { name: '确认修改', exact: true }).click()
  await expect(dialog.getByText('两次输入的密码不一致')).toBeVisible()
  expect(submissions).toBe(0)
  await dialog.getByLabel('确认新密码', { exact: true }).fill('NewPass456')
  await dialog.getByRole('button', { name: '确认修改', exact: true }).click()
  await expect(page.getByText('原密码错误', { exact: true })).toBeVisible()
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: '确认修改', exact: true }).click()
  await expect(dialog).toBeHidden()
  await page.getByRole('button', { name: '修改密码', exact: true }).click()
  await expect(dialog.getByLabel('旧密码', { exact: true })).toHaveValue('')
  await expect(dialog.getByLabel('新密码', { exact: true })).toHaveValue('')
  await expect(dialog.getByLabel('确认新密码', { exact: true })).toHaveValue('')
})

test('退出登录调用接口并清除 Cookie，无法返回已登录页面', async ({ page, context }) => {
  await page.route('**/api/user/logout', (route) =>
    route.fulfill({ json: { code: 1, msg: null, data: null } }),
  )
  await page.goto('/profile')
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page).toHaveURL('/login')
  expect((await context.cookies()).some((cookie) => cookie.name === 'token')).toBe(false)
  await page.goto('/profile')
  await expect(page).toHaveURL('/login')
})

test('小屏幕和弹窗不产生横向滚动，长表单可以滚动保存', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 667 })
  const dialog = await openEditor(page)
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole('button', { name: '保存', exact: true })).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375)
})
