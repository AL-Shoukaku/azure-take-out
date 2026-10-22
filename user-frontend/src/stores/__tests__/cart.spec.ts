import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { getCart, type CartItem } from '@/api/cart'
import { useCartStore } from '../cart'
import { useSessionStore } from '../session'
import { removeToken } from '@/utils/auth'

vi.mock('@/api/cart', () => ({ getCart: vi.fn<typeof getCart>() }))
beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
})
afterEach(removeToken)

describe('购物车共享状态', () => {
  it('读取再来一单后的真实购物车数量和金额，退出清空', async () => {
    const session = useSessionStore()
    session.startSession({ id: 7, username: '小蓝', avatar: null, token: 'token-A' })
    const cart = useCartStore()
    vi.mocked(getCart).mockResolvedValue([{ id: 1, number: 2, amount: 19.9 }] as CartItem[])
    await cart.load()
    expect(cart.count).toBe(2)
    expect(cart.amount).toBe(39.8)
    session.clearSession()
    expect(cart.items).toEqual([])
  })
  it('旧会话的购物车读取不能写入新会话', async () => {
    const session = useSessionStore()
    session.startSession({ id: 7, username: 'A', avatar: null, token: 'token-A' })
    let resolve!: (value: CartItem[]) => void
    vi.mocked(getCart).mockReturnValue(
      new Promise((r) => {
        resolve = r
      }),
    )
    const cart = useCartStore()
    const pending = cart.load()
    session.startSession({ id: 8, username: 'B', avatar: null, token: 'token-B' })
    resolve([{ id: 1, number: 2, amount: 19.9 }] as CartItem[])
    await pending
    expect(cart.items).toEqual([])
  })
})
