// 与 OrdersServiceImpl 的服务端计价规则保持一致。
export const deliveryFee = 6
export const packagingFee = 1
export function money(value: number): string {
  return `¥${value.toFixed(2)}`
}
export function lineTotal(amount: number, number: number): number {
  return (Math.round(amount * 100) * number) / 100
}
