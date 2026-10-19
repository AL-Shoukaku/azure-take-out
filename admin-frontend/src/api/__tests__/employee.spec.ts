import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter } from 'axios'
import {
  createEmployee,
  getEmployee,
  getEmployeePage,
  setEmployeeStatus,
  updateEmployee,
} from '../employee'
import { http } from '@/utils/request'

const originalAdapter = http.defaults.adapter
const form = {
  username: 'staff123',
  name: '新员工',
  phone: '13800000000',
  sex: '0' as const,
  idNumber: '11010119900101123X',
}

afterEach(() => {
  http.defaults.adapter = originalAdapter
})

function respond(data: unknown) {
  const adapter = vi.fn<AxiosAdapter>(async (config) => ({
    data,
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  }))
  http.defaults.adapter = adapter
  return adapter
}

describe('员工管理接口', () => {
  it('分页查询将姓名、页码、每页条数传到查询参数', async () => {
    const result = { total: 21, records: [{ id: 2, ...form, status: 1 }] }
    const adapter = respond({ code: 1, msg: null, data: result })
    await expect(getEmployeePage({ page: 2, pageSize: 20, name: '新' })).resolves.toEqual(result)
    expect(adapter.mock.calls[0]![0]).toMatchObject({
      url: '/employee/page',
      method: 'get',
      params: { page: 2, pageSize: 20, name: '新' },
    })
  })

  it('根据员工 ID 查询回显详情', async () => {
    const employee = { id: 8, ...form, password: '****', status: 0 }
    const adapter = respond({ code: 1, msg: null, data: employee })
    await expect(getEmployee(8)).resolves.toEqual(employee)
    expect(adapter.mock.calls[0]![0]).toMatchObject({ url: '/employee/8', method: 'get' })
  })

  it('新增只提交员工资料，不设置密码或账号状态', async () => {
    const adapter = respond({ code: 1, msg: null, data: null })
    await expect(createEmployee(form)).resolves.toBeNull()
    const config = adapter.mock.calls[0]![0]
    expect(config).toMatchObject({ url: '/employee', method: 'post' })
    expect(JSON.parse(config.data as string)).toEqual(form)
  })

  it('修改资料提交 ID 和五个可编辑字段', async () => {
    const adapter = respond({ code: 1, msg: null, data: null })
    await updateEmployee({ id: 8, ...form })
    const config = adapter.mock.calls[0]![0]
    expect(config).toMatchObject({ url: '/employee', method: 'put' })
    expect(JSON.parse(config.data as string)).toEqual({ id: 8, ...form })
  })

  it.each([0, 1] as const)('切换状态 %s 使用 POST 路径和查询参数 id', async (status) => {
    const adapter = respond({ code: 1, msg: null, data: null })
    await setEmployeeStatus(8, status)
    expect(adapter.mock.calls[0]![0]).toMatchObject({
      url: `/employee/status/${status}`,
      method: 'post',
      params: { id: 8 },
    })
  })

  it('保留后端的账号重复报错', async () => {
    respond({ code: 0, msg: "'staff123'已存在", data: null })
    await expect(createEmployee(form)).rejects.toThrow("'staff123'已存在")
  })
})
