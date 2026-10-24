import { request } from '@/utils/request'

export interface Category {
  id: number
  type: 1 | 2
  name: string
}
export interface DishFlavor {
  id: number
  dishId: number
  name: string
  value: string
}
export interface Product {
  id: number
  type: 1 | 2
  categoryId: number
  name: string
  price: number
  image: string | null
  description: string | null
  status: 0 | 1
  flavors: DishFlavor[]
}
export interface SetmealItem {
  name: string
  copies: number
  image: string | null
  description: string | null
}
export function getCategories(): Promise<Category[]> {
  return request<Category[]>({ url: '/category/list', method: 'GET' })
}
export async function getProducts(category: Category): Promise<Product[]> {
  const items = await request<Omit<Product, 'type'>[]>({
    url: category.type === 1 ? '/dish/list' : '/setmeal/list',
    method: 'GET',
    params: { categoryId: category.id },
  })
  return items.map((item) => ({ ...item, type: category.type, flavors: item.flavors ?? [] }))
}
export function getSetmealItems(id: number): Promise<SetmealItem[]> {
  return request<SetmealItem[]>({ url: `/setmeal/dish/${id}`, method: 'GET' })
}
export function getShopStatus(): Promise<0 | 1> {
  return request<0 | 1>({ url: '/shop/status', method: 'GET' })
}
