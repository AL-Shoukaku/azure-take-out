import { describe, expect, it } from 'vitest'
import { cardAddress, itemCount, itemTotal, money, orderTabs, statusNames } from '../orderDisplay'
import type { Order } from '@/api/orders'
import type { Address } from '@/api/addresses'

describe('订单展示', () => {
  it('五个筛选项与进行中状态列表符合后端状态定义', () => {
    expect(orderTabs.map((tab) => [tab.label, tab.statuses])).toEqual([
      ['全部订单', []],
      ['待付款', [1]],
      ['进行中', [2, 3, 4]],
      ['已完成', [5]],
      ['已取消', [6]],
    ])
    expect(statusNames[3]).toBe('待派送')
  })
  it('数量按件相加，明细金额按单价乘数量而非只显示单价', () => {
    const order = {
      orderDetailList: [
        { number: 2, amount: 19.9 },
        { number: 1, amount: 3 },
      ],
    } as Order
    expect(itemCount(order)).toBe(3)
    expect(money(itemTotal(order.orderDetailList[0]!))).toBe('¥39.80')
    expect(money(0)).toBe('¥0.00')
  })
  it('仅当历史地址与地址簿完整匹配时去除省市区，变更或缺失时保留快照', () => {
    const address = {
      id: 1,
      provinceName: '浙江省',
      cityName: '杭州市',
      districtName: '西湖区',
      detail: '文三路 88 号',
    } as Address
    const order = { addressBookId: 1, address: '浙江省杭州市西湖区文三路 88 号' } as Order
    expect(cardAddress(order, [address])).toBe('文三路 88 号')
    expect(cardAddress(order, [{ ...address, detail: '新地址' }])).toBe(order.address)
    expect(cardAddress(order, [])).toBe(order.address)
    expect(cardAddress({ ...order, address: '文三路 88 号' }, [address])).toBe('文三路 88 号')
  })
})
