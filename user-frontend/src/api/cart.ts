import { request } from '@/utils/request'

export interface CartItem {
  id: number
  userId: number
  dishId: number | null
  setmealId: number | null
  name: string
  dishFlavor: string | null
  number: number
  amount: number
  image: string | null
  createTime: string
}
export function getCart(): Promise<CartItem[]> {
  return request<CartItem[]>({ url: '/shoppingCart/list', method: 'GET' })
}
export interface CartProduct {
  dishId?: number
  setmealId?: number
  dishFlavor: string | null
}
export function addCartItem(data: CartProduct): Promise<null> {
  return request<null>({ url: '/shoppingCart/add', method: 'POST', data })
}
export function subtractCartItem(data: CartProduct): Promise<null> {
  return request<null>({ url: '/shoppingCart/sub', method: 'POST', data })
}
export function cleanCart(): Promise<null> {
  return request<null>({ url: '/shoppingCart/clean', method: 'DELETE' })
}
