import { expect, it } from 'vitest'
import { paymentSeconds } from '../couponOrders'

const order = { payDeadline: '2026-11-07 10:15' }

it('支付倒计时按北京时间计算，精确截止后不能支付', () => {
  expect(paymentSeconds(order.payDeadline, Date.parse('2026-11-07T10:00:00+08:00'))).toBe(900)
  expect(paymentSeconds(order.payDeadline, Date.parse('2026-11-07T10:14:59.900+08:00'))).toBe(1)
  expect(paymentSeconds(order.payDeadline, Date.parse('2026-11-07T10:15:00+08:00'))).toBe(0)
  expect(paymentSeconds('', Date.now())).toBe(0)
})
