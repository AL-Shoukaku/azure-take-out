import { request } from '@/utils/request'
import type { PageResult } from '@/types/api'

export type OrderStatus = 1 | 2 | 3 | 4 | 5 | 6
export type OrderTab = 0 | 2 | 3 | 4 | 5 | 6
export interface OrderSummary {
  id: number
  number: string
  status: OrderStatus
  userName: string | null
  consignee: string | null
  phone: string
  address: string
  orderTime: string
  checkoutTime: string | null
  estimatedDeliveryTime: string | null
  deliveryTime: string | null
  cancelTime: string | null
  cancelReason: string | null
  rejectionReason: string | null
  amount: number
  packAmount: number
  payMethod: 1 | 2
  payStatus: 0 | 1 | 2
  remark: string | null
  deliveryStatus: 0 | 1
  tablewareNumber: number
  tablewareStatus: 0 | 1
  orderDishes: string | null
}
export interface OrderItem {
  id: number
  name: string
  dishFlavor: string | null
  number: number
  amount: number
  image: string
}
export interface OrderDetail extends OrderSummary {
  orderDetailList: OrderItem[]
}
export interface OrderStatistics {
  toBeConfirmed: number
  confirmed: number
  deliveryInProgress: number
}
export interface OrderPageQuery {
  page: number
  pageSize: number
  number?: string
  phone?: string
  status?: OrderStatus
  beginTime?: string
  endTime?: string
}

export function getOrderPage(params: OrderPageQuery): Promise<PageResult<OrderSummary>> {
  return request({ url: '/order/conditionSearch', method: 'GET', params })
}
export function getOrderStatistics(): Promise<OrderStatistics> {
  return request({ url: '/order/statistics', method: 'GET' })
}
export function getOrderDetail(id: number): Promise<OrderDetail> {
  return request({ url: `/order/details/${id}`, method: 'GET' })
}
export function confirmOrder(id: number): Promise<null> {
  return request({ url: '/order/confirm', method: 'PUT', data: { id } })
}
export function rejectOrder(id: number, rejectionReason: string): Promise<null> {
  return request({ url: '/order/rejection', method: 'PUT', data: { id, rejectionReason } })
}
export function cancelOrder(id: number, cancelReason: string): Promise<null> {
  return request({ url: '/order/cancel', method: 'PUT', data: { id, cancelReason } })
}
export function deliverOrder(id: number): Promise<null> {
  return request({ url: `/order/delivery/${id}`, method: 'PUT' })
}
export function completeOrder(id: number): Promise<null> {
  return request({ url: `/order/complete/${id}`, method: 'PUT' })
}
