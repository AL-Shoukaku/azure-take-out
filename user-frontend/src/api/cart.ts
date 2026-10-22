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
