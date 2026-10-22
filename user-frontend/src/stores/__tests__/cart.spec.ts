import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { getCart, addCartItem, subtractCartItem, cleanCart, type CartItem } from '@/api/cart'
import { useCartStore } from '../cart'
import { useSessionStore } from '../session'
import { removeToken } from '@/utils/auth'

vi.mock('@/api/cart', () => ({
  getCart: vi.fn<typeof getCart>(),
  addCartItem: vi.fn<typeof addCartItem>(),
  subtractCartItem: vi.fn<typeof subtractCartItem>(),
  cleanCart: vi.fn<typeof cleanCart>(),
}))
beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
})
afterEach(removeToken)

describe('购物车共享状态', () => {
  it('空购物车没有配送和打包费，单价按件计算后统一计费', async () => {
    const cart = useCartStore()
    expect(cart.total).toBe(0)
    vi.mocked(getCart).mockResolvedValue([{ id: 1, number: 3, amount: 19.9 }] as CartItem[])
    await cart.load()
    expect(cart.packAmount).toBe(3)
    expect(cart.deliveryAmount).toBe(6)
    expect(cart.total).toBe(68.7)
    cart.reset()
    expect(cart.total).toBe(0)
  })
  it('写入完成再重新读取，等待中禁止重复提交，不丢失口味', async () => {
    let resolve!: (value: null) => void
    vi.mocked(addCartItem).mockReturnValue(
      new Promise((r) => {
        resolve = r
      }),
    )
    vi.mocked(getCart).mockResolvedValue([{ id: 1, number: 1, amount: 20 }] as CartItem[])
    const cart = useCartStore()
    const pending = cart.mutate('add', { dishId: 2, dishFlavor: '微辣' })
    expect(await cart.mutate('add', { dishId: 2, dishFlavor: null })).toBe(false)
    resolve(null)
    expect(await pending).toBe(true)
    expect(addCartItem).toHaveBeenCalledExactlyOnceWith({ dishId: 2, dishFlavor: '微辣' })
    expect(cart.count).toBe(1)
    await cart.mutate('subtract', { dishId: 2, dishFlavor: '微辣' })
    expect(subtractCartItem).toHaveBeenCalledExactlyOnceWith({ dishId: 2, dishFlavor: '微辣' })
    await cart.mutate('clean')
    expect(cleanCart).toHaveBeenCalledOnce()
  })
  it('写入成功但刷新失败后禁用继续加购，直到读取恢复', async () => {
    vi.mocked(getCart).mockRejectedValueOnce(new Error('刷新失败')).mockResolvedValueOnce([])
    const cart = useCartStore()
    await expect(cart.mutate('add', { dishId: 1, dishFlavor: null })).rejects.toThrow('刷新失败')
    expect(cart.error).toBe('刷新失败')
    expect(await cart.mutate('add', { dishId: 1, dishFlavor: null })).toBe(false)
    await cart.load()
    expect(cart.error).toBe('')
    expect(cart.busy).toBe(false)
  })
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
