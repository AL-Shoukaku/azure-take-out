import type { OrderStatus, OrderSummary, OrderTab } from '@/api/order'

export const orderTabs = [
  { status: 0, label: '全部订单' },
  { status: 2, label: '待接单' },
  { status: 3, label: '待派送' },
  { status: 4, label: '派送中' },
  { status: 5, label: '已完成' },
  { status: 6, label: '已取消' },
] as const
export const statusNames: Record<OrderStatus, string> = {
  1: '待付款',
  2: '待接单',
  3: '待派送',
  4: '派送中',
  5: '已完成',
  6: '已取消',
}
export type OrderAction = 'confirm' | 'reject' | 'deliver' | 'complete' | 'cancel'
export interface ActionOption {
  action: OrderAction
  label: string
}
export const primaryActions: Partial<Record<OrderStatus, ActionOption>> = {
  2: { action: 'confirm', label: '接单' },
  3: { action: 'deliver', label: '派送' },
  4: { action: 'complete', label: '完成' },
}
export function secondaryAction(status: OrderStatus): ActionOption | undefined {
  if (status === 2) return { action: 'reject', label: '拒单' }
  if ([1, 3, 4, 5].includes(status)) return { action: 'cancel', label: '取消' }
}
export function formatTime(time: string | null) {
  return time?.replace('T', ' ').slice(0, 16) || '—'
}
export function money(amount: number) {
  return `￥${amount.toFixed(2)}`
}
// 当前 OrdersServiceImpl 中的固定配送费，订单总价已包含该费用及打包费。
export const deliveryFee = 6

interface OrderColumn {
  key: string
  label: string
  width: number
  value: (order: OrderSummary) => string
}
const fields = {
  number: { key: 'number', label: '订单号', width: 170, value: (o: OrderSummary) => o.number },
  status: {
    key: 'status',
    label: '订单状态',
    width: 95,
    value: (o: OrderSummary) => statusNames[o.status],
  },
  name: {
    key: 'name',
    label: '用户名',
    width: 100,
    value: (o: OrderSummary) => o.consignee || o.userName || '—',
  },
  phone: { key: 'phone', label: '手机号', width: 130, value: (o: OrderSummary) => o.phone },
  address: { key: 'address', label: '地址', width: 150, value: (o: OrderSummary) => o.address },
  orderTime: {
    key: 'orderTime',
    label: '下单时间',
    width: 165,
    value: (o: OrderSummary) => formatTime(o.orderTime),
  },
  amount: {
    key: 'amount',
    label: '实收金额',
    width: 110,
    value: (o: OrderSummary) => money(o.amount),
  },
  dishes: {
    key: 'dishes',
    label: '订单菜品',
    width: 180,
    value: (o: OrderSummary) => o.orderDishes || '—',
  },
  estimated: {
    key: 'estimated',
    label: '预计送达时间',
    width: 165,
    value: (o: OrderSummary) => formatTime(o.estimatedDeliveryTime),
  },
  remark: { key: 'remark', label: '备注', width: 130, value: (o: OrderSummary) => o.remark || '—' },
  tableware: {
    key: 'tableware',
    label: '餐具数量',
    width: 100,
    value: (o: OrderSummary) => String(o.tablewareNumber),
  },
  delivered: {
    key: 'delivered',
    label: '送达时间',
    width: 165,
    value: (o: OrderSummary) => formatTime(o.deliveryTime),
  },
  cancelTime: {
    key: 'cancelTime',
    label: '取消时间',
    width: 165,
    value: (o: OrderSummary) => formatTime(o.cancelTime),
  },
  reason: {
    key: 'reason',
    label: '取消原因',
    width: 170,
    value: (o: OrderSummary) => o.cancelReason || o.rejectionReason || '—',
  },
} satisfies Record<string, OrderColumn>

export function orderColumns(tab: OrderTab): OrderColumn[] {
  const f = fields
  if (tab === 0) return [f.number, f.status, f.name, f.phone, f.address, f.orderTime, f.amount]
  if (tab === 5) return [f.number, f.name, f.phone, f.address, f.delivered, f.amount, f.remark]
  if (tab === 6) return [f.number, f.name, f.phone, f.address, f.orderTime, f.cancelTime, f.reason]
  return [
    f.number,
    f.dishes,
    f.address,
    f.estimated,
    ...(tab === 2 ? [f.amount] : []),
    f.remark,
    f.tableware,
  ]
}
