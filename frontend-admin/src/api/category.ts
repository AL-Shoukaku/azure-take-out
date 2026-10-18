import { request } from '@/utils/request'
import type { PageResult } from '@/types/api'

export type CategoryType = 1 | 2
export type CategoryStatus = 0 | 1

export interface CategoryFormDTO {
  type: CategoryType
  name: string
  sort: number
}

export interface Category extends CategoryFormDTO {
  id: number
  status: CategoryStatus
  updateTime: string | null
}

export interface CategoryPageQuery {
  page: number
  pageSize: number
  name?: string
  type?: CategoryType
}

export function getCategoryPage(params: CategoryPageQuery): Promise<PageResult<Category>> {
  return request({ url: '/category/page', method: 'GET', params })
}

export function getCategoryList(type: CategoryType): Promise<Category[]> {
  return request({ url: '/category/list', method: 'GET', params: { type } })
}

export function createCategory(data: CategoryFormDTO): Promise<null> {
  return request({ url: '/category', method: 'POST', data })
}

export function updateCategory(data: CategoryFormDTO & { id: number }): Promise<null> {
  return request({ url: '/category', method: 'PUT', data })
}

export function deleteCategory(id: number): Promise<null> {
  return request({ url: '/category', method: 'DELETE', params: { id } })
}

export function setCategoryStatus(id: number, status: CategoryStatus): Promise<null> {
  return request({ url: `/category/status/${status}`, method: 'POST', params: { id } })
}
