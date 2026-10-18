import { expect, test } from '@playwright/test'
import { readFile } from 'node:fs/promises'
import { mockAdminSession } from './helpers/admin.js'
import { mockReportAPIs, reportData } from './helpers/reports.js'

test.use({ timezoneId: 'Asia/Shanghai' })

test.beforeEach(async ({ page, context, baseURL }) => {
  await page.clock.setFixedTime(new Date('2026-10-18T12:00:00+08:00'))
  await mockAdminSession(page, context, baseURL!)
})

test('默认近 7 日，四张图表展示真实数值，折叠和刷新后仍能正确绘制', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  const requests = await mockReportAPIs(page)
  await page.goto('/statistics')
  await expect(page.getByRole('tab', { name: '近7日', exact: true })).toHaveAttribute(
    'aria-selected',
    'true',
  )
  await expect(page.getByText('已选时间：2026-10-11 至 2026-10-17', { exact: true })).toBeVisible()
  for (const title of ['营业额统计', '用户统计', '订单统计', '销量排名TOP10']) {
    const card = page.getByRole('region', { name: title, exact: true })
    const chart = card.getByRole('img', {
      name: `${title}${title === '销量排名TOP10' ? '柱状图' : '折线图'}`,
      exact: true,
    })
    await expect(chart).toBeVisible()
    await expect(chart.locator('svg')).toBeVisible()
  }
  expect(requests).toHaveLength(4)
  expect(
    requests.every((request) => request.begin === '2026-10-11' && request.end === '2026-10-17'),
  ).toBe(true)
  const orderCard = page.getByRole('region', { name: '订单统计', exact: true })
  await expect(orderCard).toContainText('75.0%')
  await expect(orderCard.getByLabel('有效订单数量')).toHaveText('9')
  await expect(orderCard.getByLabel('订单总数')).toHaveText('12')
  const top = page.getByRole('region', { name: '销量排名TOP10', exact: true })
  const ranking = top.getByRole('img', { name: '销量排名TOP10柱状图', exact: true })
  await expect(ranking.locator('svg')).toContainText('宫保鸡丁单人套餐')
  await expect(ranking.locator('svg')).toContainText('9')
  const chart = page.getByRole('img', { name: '营业额统计折线图' })
  const width = (await chart.boundingBox())!.width
  await page.getByRole('button', { name: '折叠菜单', exact: true }).click()
  await expect
    .poll(async () => Number(await chart.locator('svg').getAttribute('width')))
    .toBeGreaterThan(width + 40)
  await page.reload()
  await expect(orderCard).toContainText('75.0%')
  expect(errors).toEqual([])
})

test('五个时间选项同步更新四个接口参数，本周按周一至周日计算', async ({ page }) => {
  const requests = await mockReportAPIs(page)
  await page.goto('/statistics')
  await expect.poll(() => requests.length).toBe(4)
  for (const [label, begin, end] of [
    ['昨日', '2026-10-17', '2026-10-17'],
    ['近30日', '2026-09-18', '2026-10-17'],
    ['本周', '2026-10-12', '2026-10-18'],
    ['本月', '2026-10-01', '2026-10-31'],
    ['近7日', '2026-10-11', '2026-10-17'],
  ]) {
    const count = requests.length
    await page.getByRole('tab', { name: label, exact: true }).click()
    await expect(page.getByText(`已选时间：${begin} 至 ${end}`, { exact: true })).toBeVisible()
    await expect.poll(() => requests.length).toBe(count + 4)
    expect(
      requests.slice(-4).every((request) => request.begin === begin && request.end === end),
    ).toBe(true)
  }
})

test('单个统计失败可独立重试，销量为空时不绘制虚假条目', async ({ page }) => {
  await mockReportAPIs(page)
  let attempts = 0
  await page.route('**/api/report/userStatistics?**', (route) => {
    attempts++
    return route.fulfill({
      json:
        attempts === 1
          ? { code: 0, msg: '用户统计读取失败', data: null }
          : { code: 1, msg: null, data: reportData.userStatistics },
    })
  })
  await page.route('**/api/report/top10?**', (route) =>
    route.fulfill({ json: { code: 1, msg: null, data: { nameList: '', numberList: '' } } }),
  )
  await page.goto('/statistics')
  const users = page.getByRole('region', { name: '用户统计', exact: true })
  await expect(users.getByRole('alert')).toContainText('用户统计读取失败')
  await expect(page.getByRole('region', { name: '订单统计', exact: true })).toContainText('75.0%')
  await users.getByRole('button', { name: '重试', exact: true }).click()
  await expect(users.getByRole('img', { name: '用户统计折线图', exact: true })).toBeVisible()
  await expect(page.getByRole('region', { name: '销量排名TOP10', exact: true })).toContainText(
    '暂无销量数据',
  )
  expect(attempts).toBe(2)
})

test('快速切换日期时忽略旧响应，无订单时使用后端返回的完成率', async ({ page }) => {
  await mockReportAPIs(page)
  let release!: () => void
  const pending = new Promise<void>((resolve) => {
    release = resolve
  })
  let waiting = false
  await page.route('**/api/report/ordersStatistics?**', async (route) => {
    const begin = new URL(route.request().url()).searchParams.get('begin')
    if (begin === '2026-10-11') {
      waiting = true
      await pending
      await route.fulfill({ json: { code: 1, msg: null, data: reportData.ordersStatistics } })
    } else {
      await route.fulfill({
        json: {
          code: 1,
          msg: null,
          data: {
            dateList: '2026-10-17',
            orderCountList: '0',
            validOrderCountList: '0',
            totalOrderCount: 0,
            validOrderCount: 0,
            orderCompletionRate: 1,
          },
        },
      })
    }
  })
  const stale = page.waitForResponse((response) => {
    const url = new URL(response.url())
    return (
      url.pathname === '/api/report/ordersStatistics' &&
      url.searchParams.get('begin') === '2026-10-11'
    )
  })
  await page.goto('/statistics')
  await expect.poll(() => waiting).toBe(true)
  await page.getByRole('tab', { name: '昨日', exact: true }).click()
  const orders = page.getByRole('region', { name: '订单统计', exact: true })
  await expect(orders).toContainText('100.0%')
  release()
  await (await stale).finished()
  await orders.evaluate(
    () =>
      new Promise<void>((resolve) =>
        requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
      ),
  )
  await expect(orders.getByLabel('订单总数')).toHaveText('0')
  await expect(orders).toContainText('100.0%')
  await expect(orders).not.toContainText('NaN')
})

test('导出先确认固定近 30 日，取消不请求，确定后下载完整二进制文件', async ({ page }) => {
  await mockReportAPIs(page)
  const bytes = Buffer.from([80, 75, 3, 4, 0, 255, 128, 10])
  let calls = 0
  let token: string | undefined
  let params = ''
  let release!: () => void
  const pending = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route('**/api/report/export', async (route) => {
    calls++
    token = route.request().headers().token
    params = new URL(route.request().url()).search
    await pending
    await route.fulfill({ body: bytes, contentType: 'application/octet-stream' })
  })
  await page.goto('/statistics')
  const button = page.getByRole('button', { name: '数据导出', exact: true })
  await button.click()
  const confirm = page.getByRole('dialog', { name: '提示', exact: true })
  await expect(confirm).toContainText('是否确认导出最近30天运营数据?')
  await confirm.getByRole('button', { name: '取消', exact: true }).click()
  expect(calls).toBe(0)
  await button.click()
  await confirm.getByRole('button', { name: '确定', exact: true }).click()
  await expect(button).toBeDisabled()
  const downloading = page.waitForEvent('download')
  release()
  const download = await downloading
  expect(download.suggestedFilename()).toBe('运营数据统计报表.xlsx')
  expect(await readFile((await download.path())!)).toEqual(bytes)
  expect(token).toBe('layout-test-token')
  expect(params).toBe('')
  expect(calls).toBe(1)
  await expect(button).toBeEnabled()
})

test('导出 JSON 报错不下载错误文件，显示后端原因并允许重试', async ({ page }) => {
  await mockReportAPIs(page)
  const downloads: string[] = []
  page.on('download', (download) => downloads.push(download.suggestedFilename()))
  await page.route('**/api/report/export', (route) =>
    route.fulfill({ json: { code: 0, msg: '报表生成失败', data: null } }),
  )
  await page.goto('/statistics')
  const button = page.getByRole('button', { name: '数据导出', exact: true })
  await button.click()
  await page
    .getByRole('dialog', { name: '提示', exact: true })
    .getByRole('button', { name: '确定', exact: true })
    .click()
  await expect(page.getByRole('alert')).toContainText('报表生成失败')
  await expect(button).toBeEnabled()
  expect(downloads).toEqual([])
})
