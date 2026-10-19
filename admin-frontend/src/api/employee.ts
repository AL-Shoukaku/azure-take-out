import { request } from '@/utils/request'
import type { PageResult } from '@/types/api'

export type EmployeeStatus = 0 | 1

export interface EmployeeFormDTO {
  username: string
  name: string
  phone: string
  sex: '0' | '1'
  idNumber: string
}

export interface Employee extends EmployeeFormDTO {
  id: number
  status: EmployeeStatus
  updateTime: string | null
}

export interface EmployeePageQuery {
  page: number
  pageSize: number
  name?: string
}

export function getEmployeePage(params: EmployeePageQuery): Promise<PageResult<Employee>> {
  return request({ url: '/employee/page', method: 'GET', params })
}

export function getEmployee(id: number): Promise<Employee> {
  return request({ url: `/employee/${id}`, method: 'GET' })
}

export function createEmployee(data: EmployeeFormDTO): Promise<null> {
  return request({ url: '/employee', method: 'POST', data })
}

export function updateEmployee(data: EmployeeFormDTO & { id: number }): Promise<null> {
  return request({ url: '/employee', method: 'PUT', data })
}

export function setEmployeeStatus(id: number, status: EmployeeStatus): Promise<null> {
  return request({ url: `/employee/status/${status}`, method: 'POST', params: { id } })
}
