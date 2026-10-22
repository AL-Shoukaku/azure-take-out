import { expect, it } from 'vitest'
import { flavorGroups, cartProduct, groupCartItems, deliveryDateTime } from '../menuDisplay'
import type { CartItem } from '@/api/cart'

it('口味保持服务端顺序，避免不同选择顺序产生重复组合', () => {
  expect(flavorGroups([{ id: 1, dishId: 2, name: '辣度', value: '["不辣","微辣"]' }])).toEqual([
    { id: 1, name: '辣度', options: ['不辣', '微辣'] },
  ])
})
it('历史再来一单的相同口味条目合并显示，单价按件计费', () => {
  const item = {
    id: 1,
    dishId: 2,
    setmealId: null,
    dishFlavor: '微辣',
    number: 2,
    amount: 19.9,
  } as CartItem
  const grouped = groupCartItems([
    item,
    { ...item, id: 3, number: 1 },
    { ...item, id: 4, dishFlavor: null },
  ])
  expect(grouped).toHaveLength(2)
  expect(grouped[0]!.number).toBe(3)
  expect(cartProduct(grouped[1]!)).toEqual({ dishId: 2, dishFlavor: null })
})
it('配送时间使用本地日期和后端要求的秒格式', () => {
  expect(deliveryDateTime(new Date(2026, 9, 22, 18, 5))).toBe('2026-10-22 18:05:00')
})
