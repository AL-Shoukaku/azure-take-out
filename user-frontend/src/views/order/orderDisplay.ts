import type { Order, OrderItem, OrderStatus } from '@/api/orders'
import type { Address } from '@/api/addresses'

export const orderTabs = [
  { key: 'all', label: '全部订单', statuses: [] },
  { key: 'unpaid', label: '待付款', statuses: [1] },
  { key: 'active', label: '进行中', statuses: [2, 3, 4] },
  { key: 'completed', label: '已完成', statuses: [5] },
  { key: 'cancelled', label: '已取消', statuses: [6] },
] as const satisfies readonly { key: string; label: string; statuses: readonly OrderStatus[] }[]
export type OrderTab = (typeof orderTabs)[number]['key']
export type OrderAction = 'pay' | 'cancel' | 'remind' | 'repeat' | 'contact'
export const statusNames: Record<OrderStatus, string> = {
  1: '待付款',
  2: '待接单',
  3: '待派送',
  4: '派送中',
  5: '已完成',
  6: '已取消',
}
export const statusDescriptions: Record<OrderStatus, string> = {
  1: '订单已提交，请完成付款。',
  2: '已收到你的订单，正在等待商家接单。',
  3: '商家正在准备美食，请耐心等待配送。',
  4: '美味正在路上，请留意配送电话。',
  5: '订单已送达，感谢你的支持，期待下次相见。',
  6: '订单已取消，期待再次为你服务。',
}
// 与当前 OrdersServiceImpl 的固定配送费一致。
export const deliveryFee = 6
export const orderPageSize = 6
export function money(value: number): string {
  return `¥${value.toFixed(2)}`
}
export function formatTime(value: string | null): string {
  return value?.replace('T', ' ').slice(0, 16) || '—'
}
export function itemCount(order: Order): number {
  return order.orderDetailList.reduce((count, item) => count + item.number, 0)
}
export function itemTotal(item: OrderItem): number {
  return (Math.round(item.amount * 100) * item.number) / 100
}

export function cardAddress(order: Order, addresses: Address[]): string {
  const address = addresses.find((address) => address.id === order.addressBookId)
  if (address) {
    const snapshot = [
      address.provinceName,
      address.cityName,
      address.districtName,
      address.detail,
    ].join('')
    if (order.address === snapshot) return address.detail
  }
  return order.address
}
