import { request } from '@/utils/request'

export interface BusinessData {
  turnover: number
  validOrderCount: number
  orderCompletionRate: number
  unitPrice: number
  newUsers: number
}
export interface OrderOverview {
  waitingOrders: number
  deliveredOrders: number
  completedOrders: number
  cancelledOrders: number
  allOrders: number
}
export interface ProductOverview {
  sold: number
  discontinued: number
}

export function getBusinessData(): Promise<BusinessData> {
  return request({ url: '/workspace/businessData', method: 'GET' })
}
export function getOrderOverview(): Promise<OrderOverview> {
  return request({ url: '/workspace/overviewOrders', method: 'GET' })
}
export function getDishOverview(): Promise<ProductOverview> {
  return request({ url: '/workspace/overviewDishes', method: 'GET' })
}
export function getSetmealOverview(): Promise<ProductOverview> {
  return request({ url: '/workspace/overviewSetmeals', method: 'GET' })
}
