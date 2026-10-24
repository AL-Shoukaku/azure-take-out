import { expect, type Page } from '@playwright/test'
import type { Order, OrderStatus } from '../../src/api/orders'

export function makeOrder(id: number, status: OrderStatus): Order {
  return {
    id,
    number: `ORDER-${String(id).padStart(4, '0')}`,
    status,
    userId: 7,
    addressBookId: 1,
    orderTime: '2026-10-22 11:30',
    checkoutTime: status === 1 ? null : '2026-10-22 11:31',
    payMethod: 1,
    payStatus: status === 1 ? 0 : status === 6 ? 2 : 1,
    amount: 64,
    remark: '少放辣，谢谢',
    userName: '小蓝',
    phone: '13800000000',
    consignee: '小蓝',
    address: '文三路 88 号 2 幢 301 室',
    cancelReason: status === 6 ? '用户取消' : null,
    rejectionReason: null,
    cancelTime: status === 6 ? '2026-10-22 11:35' : null,
    estimatedDeliveryTime: '2026-10-22 12:00',
    deliveryStatus: 1,
    deliveryTime: status === 5 ? '2026-10-22 11:58' : null,
    packAmount: 3,
    tablewareNumber: 0,
    tablewareStatus: 1,
    orderDishes: null,
    orderDetailList: [
      {
        id: id * 10,
        orderId: id,
        dishId: 1,
        setmealId: null,
        name: '香煎照烧鸡腿饭',
        dishFlavor: '不辣',
        number: 2,
        amount: 26,
        image: '/test-dish.svg',
      },
      {
        id: id * 10 + 1,
        orderId: id,
        dishId: 2,
        setmealId: null,
        name: '白米饭',
        dishFlavor: null,
        number: 1,
        amount: 3,
        image: null,
      },
    ],
  }
}

export async function mockOrders(
  page: Page,
  initial = [
    makeOrder(1, 1),
    makeOrder(2, 2),
    makeOrder(3, 3),
    makeOrder(4, 4),
    makeOrder(5, 5),
    makeOrder(6, 6),
    makeOrder(7, 5),
  ],
) {
  let orders = structuredClone(initial)
  const writes: { url: string; method: string; data: unknown }[] = []
  const queries: { page: number; statuses: string | null }[] = []
  let cart: Order['orderDetailList'] = []
  await page.route('**/test-dish.svg', (route) =>
    route.fulfill({
      contentType: 'image/svg+xml',
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><rect width="80" height="80" rx="12" fill="#e5eddf"/><circle cx="40" cy="40" r="28" fill="#d3a56c"/></svg>',
    }),
  )
  await page.route('**/api/shoppingCart/list', (route) =>
    route.fulfill({ json: { code: 1, data: cart, msg: null } }),
  )
  await page.route('**/api/order/**', (route) => {
    const request = route.request()
    expect(request.headers().authentication).toBe('e2e-user-token')
    const url = new URL(request.url())
    const path = url.pathname
    if (path.endsWith('/historyOrdersByStatusList')) {
      const statuses = url.searchParams.get('statusList')
      const pageNum = Number(url.searchParams.get('pageNum'))
      queries.push({ page: pageNum, statuses })
      const filtered = orders.filter(
        (order) => !statuses || statuses.split(',').includes(String(order.status)),
      )
      return route.fulfill({
        json: {
          code: 1,
          data: { total: filtered.length, records: filtered.slice((pageNum - 1) * 6, pageNum * 6) },
          msg: null,
        },
      })
    }
    const id = Number(path.split('/').at(-1))
    if (path.includes('/orderDetail/'))
      return route.fulfill({
        json: { code: 1, data: orders.find((order) => order.id === id), msg: null },
      })
    const data: unknown = request.postData() ? request.postDataJSON() : null
    writes.push({ url: path, method: request.method(), data })
    if (path.includes('/cancel/'))
      orders = orders.map((order) =>
        order.id === id
          ? {
              ...order,
              status: 6,
              payStatus: order.payStatus === 1 ? 2 : 0,
              cancelReason: '用户在接单前取消',
              cancelTime: '2026-10-22 11:35',
            }
          : order,
      )
    if (path.includes('/repetition/'))
      cart = [...cart, ...orders.find((order) => order.id === id)!.orderDetailList]
    if (path.endsWith('/payment')) {
      const number = (data as { orderNumber: string }).orderNumber
      orders = orders.map((order) =>
        order.number === number
          ? { ...order, status: 2, payStatus: 1, checkoutTime: '2026-10-22 11:35' }
          : order,
      )
    }
    return route.fulfill({
      json: {
        code: 1,
        data: path.endsWith('/payment') ? { packageStr: 'prepay_id=fake_prepay_id' } : null,
        msg: null,
      },
    })
  })
  return { queries, writes }
}
