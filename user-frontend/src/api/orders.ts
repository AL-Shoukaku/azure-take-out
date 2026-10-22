import { request } from '@/utils/request'

export type OrderStatus = 1 | 2 | 3 | 4 | 5 | 6
export interface OrderItem {
  id: number
  orderId: number
  dishId: number | null
  setmealId: number | null
  name: string
  dishFlavor: string | null
  number: number
  amount: number
  image: string | null
}
export interface Order {
  id: number
  number: string
  status: OrderStatus
  userId: number
  addressBookId: number
  orderTime: string
  checkoutTime: string | null
  payMethod: number | null
  payStatus: 0 | 1 | 2
  amount: number
  remark: string | null
  userName: string | null
  phone: string
  address: string
  consignee: string
  cancelReason: string | null
  rejectionReason: string | null
  cancelTime: string | null
  estimatedDeliveryTime: string | null
  deliveryStatus: 0 | 1
  deliveryTime: string | null
  packAmount: number
  tablewareNumber: number
  tablewareStatus: 0 | 1
  orderDishes: string | null
  orderDetailList: OrderItem[]
}
export interface OrderPageResult {
  total: number
  records: Order[]
}
export interface OrderPaymentResult {
  nonceStr: string
  paySign: string
  timeStamp: string
  signType: string
  packageStr: string
}

export function getOrders(
  pageNum: number,
  statuses: readonly OrderStatus[],
): Promise<OrderPageResult> {
  return request<OrderPageResult>({
    url: '/order/historyOrdersByStatusList',
    method: 'GET',
    params: { pageNum, ...(statuses.length ? { statusList: statuses.join(',') } : {}) },
  })
}
export function getOrder(id: number): Promise<Order> {
  return request<Order>({ url: `/order/orderDetail/${id}`, method: 'GET' })
}
export function cancelOrder(id: number): Promise<null> {
  return request<null>({ url: `/order/cancel/${id}`, method: 'PUT' })
}
export function remindOrder(id: number): Promise<null> {
  return request<null>({ url: `/order/reminder/${id}`, method: 'GET' })
}
export function repeatOrder(id: number): Promise<null> {
  return request<null>({ url: `/order/repetition/${id}`, method: 'POST' })
}
export function payOrder(orderNumber: string): Promise<OrderPaymentResult> {
  return request<OrderPaymentResult>({
    url: '/order/payment',
    method: 'PUT',
    data: { orderNumber, payMethod: 1 },
  })
}
