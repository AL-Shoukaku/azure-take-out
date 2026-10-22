import type { CartItem, CartProduct } from '@/api/cart'
import type { DishFlavor } from '@/api/menu'

export const menuPageSize = 6
export function flavorGroups(
  flavors: DishFlavor[],
): { id: number; name: string; options: string[] }[] {
  return flavors.map((flavor) => ({
    id: flavor.id,
    name: flavor.name,
    options: JSON.parse(flavor.value) as string[],
  }))
}
export function cartProduct(item: CartItem): CartProduct {
  return {
    ...(item.dishId !== null ? { dishId: item.dishId } : { setmealId: item.setmealId! }),
    dishFlavor: item.dishFlavor,
  }
}
// 再来一单会追加相同组合的记录；合并展示后仍使用后端的逐件加减接口。
export function groupCartItems(items: CartItem[]): CartItem[] {
  const groups = new Map<string, CartItem>()
  for (const item of items) {
    const key = JSON.stringify([item.dishId, item.setmealId, item.dishFlavor, item.amount])
    const group = groups.get(key)
    if (group) group.number += item.number
    else groups.set(key, { ...item })
  }
  return [...groups.values()]
}
export function deliveryDateTime(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:00`
}
