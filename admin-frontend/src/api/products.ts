import { request } from '@/utils/request'
import type { PageResult } from '@/types/api'

export type ProductKind = 'dish' | 'setmeal'
export type SaleStatus = 0 | 1

export interface ProductFields {
  name: string
  categoryId: number
  price: number
  image: string
  description: string
  status: SaleStatus
}

export interface ProductSummary extends ProductFields {
  id: number
  categoryName: string | null
  updateTime: string | null
}

export interface DishFlavor {
  name: string
  value: string
}

export interface SetmealDish {
  dishId: number
  name: string
  price: number
  copies: number
}

export interface DishFormDTO extends ProductFields {
  flavors: DishFlavor[]
}

export interface SetmealFormDTO extends ProductFields {
  setmealDishes: SetmealDish[]
}

export interface ProductPageQuery {
  page: number
  pageSize: number
  name?: string
  categoryId?: number
  status?: SaleStatus
}

function productAPI<T extends ProductFields>(kind: ProductKind) {
  return {
    page: (params: ProductPageQuery): Promise<PageResult<ProductSummary>> =>
      request({ url: `/${kind}/page`, method: 'GET', params }),
    detail: (id: number): Promise<ProductSummary & T> =>
      request({ url: `/${kind}/${id}`, method: 'GET' }),
    create: (data: T): Promise<null> => request({ url: `/${kind}`, method: 'POST', data }),
    update: (data: T & { id: number }): Promise<null> =>
      request({ url: `/${kind}`, method: 'PUT', data }),
    remove: (ids: number[]): Promise<null> =>
      request({ url: `/${kind}`, method: 'DELETE', params: { ids: ids.join(',') } }),
    status: (id: number, status: SaleStatus): Promise<null> =>
      request({ url: `/${kind}/status/${status}`, method: 'POST', params: { id } }),
  }
}

export const dishAPI = productAPI<DishFormDTO>('dish')
export const setmealAPI = productAPI<SetmealFormDTO>('setmeal')

export function getAvailableDishes(categoryId: number): Promise<ProductSummary[]> {
  return request({ url: '/dish/list', method: 'GET', params: { categoryId } })
}

export function uploadImage(file: File): Promise<string> {
  const data = new FormData()
  data.append('file', file)
  return request({ url: '/common/upload', method: 'POST', data })
}
