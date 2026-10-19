import type { Page } from '@playwright/test'

export const employeeForm = {
  username: 'newstaff',
  name: '新员工',
  phone: '13800000000',
  sex: '1',
  idNumber: '11010119900101123X',
}

export async function mockEmployees(page: Page) {
  let records = Array.from({ length: 45 }, (_, index) => ({
    ...employeeForm,
    id: index + 1,
    username: `staff${index + 1}`,
    name: `员工${index + 1}`,
    status: 1,
    sex: '1',
    updateTime: '2026-10-18 12:34:56',
    password: '****',
  }))
  Object.assign(records[0]!, { username: 'admin', name: '管理员' })
  Object.assign(records[1]!, { name: '演示店员', sex: '0' })
  Object.assign(records[2]!, { name: '已禁用店员', status: 0 })
  Object.assign(records[6]!, { username: 'store-admin', name: '测试店长' })
  const queries: URLSearchParams[] = []
  const writes: { method: string; path: string; body: unknown; id: string | null }[] = []
  const failures = { list: '', detail: '', save: '', status: '' }
  let holdSave: (() => void) | undefined
  const controls = {
    failures,
    queries,
    writes,
    delaySave: false,
    releaseSave: () => holdSave?.(),
  }
  await page.route(/\/api\/employee(?:\/page\?|\/\d+$|\/status\/|$)/, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const method = request.method()
    const success = (data: unknown) => route.fulfill({ json: { code: 1, msg: null, data } })
    const fail = (msg: string) => route.fulfill({ json: { code: 0, msg, data: null } })
    if (url.pathname.endsWith('/page')) {
      queries.push(url.searchParams)
      if (failures.list) return fail(failures.list)
      const size = Number(url.searchParams.get('pageSize'))
      const current = Number(url.searchParams.get('page'))
      const name = url.searchParams.get('name') || ''
      const filtered = records.filter((record) => record.name.includes(name))
      return success({
        total: filtered.length,
        records: filtered.slice((current - 1) * size, current * size),
      })
    }
    if (method === 'GET') {
      if (failures.detail) return fail(failures.detail)
      return success(records.find((record) => record.id === Number(url.pathname.split('/').pop())))
    }
    writes.push({
      method,
      path: url.pathname,
      body: request.postDataJSON(),
      id: url.searchParams.get('id'),
    })
    if (url.pathname.includes('/status/')) {
      if (failures.status) return fail(failures.status)
      records.find((record) => record.id === Number(url.searchParams.get('id')))!.status = Number(
        url.pathname.split('/').pop(),
      )
    } else {
      if (controls.delaySave)
        await new Promise<void>((resolve) => {
          holdSave = resolve
        })
      if (failures.save) return fail(failures.save)
      const body = request.postDataJSON()
      if (method === 'PUT')
        Object.assign(
          records.find((record) => record.id === body.id)!,
          body,
        )
      else records = [{ ...records[1]!, ...body, id: records.length + 1 }, ...records]
    }
    return success(null)
  })
  return controls
}

export async function fillEmployee(page: Page) {
  await page.getByLabel('账号:', { exact: true }).fill(employeeForm.username)
  await page.getByLabel('员工姓名:', { exact: true }).fill(employeeForm.name)
  await page.getByLabel('手机号:', { exact: true }).fill(employeeForm.phone)
  await page.getByLabel('身份证号:', { exact: true }).fill(employeeForm.idNumber)
}
