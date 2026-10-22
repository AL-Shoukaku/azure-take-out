import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, type EffectScope } from 'vue'
import { getCategories, getProducts, type Product } from '@/api/menu'
import { useMenu } from '../useMenu'
import { removeToken, setToken } from '@/utils/auth'

vi.mock('@/api/menu', () => ({
  getCategories: vi.fn<typeof getCategories>(),
  getProducts: vi.fn<typeof getProducts>(),
}))
let scope: EffectScope
beforeEach(() => {
  scope = effectScope()
  vi.resetAllMocks()
  setToken('A')
})
afterEach(() => {
  scope.stop()
  removeToken()
})
const categories = [
  { id: 1, name: '热菜', type: 1 as const },
  { id: 2, name: '套餐', type: 2 as const },
]
describe('餐点分类及分页', () => {
  it('菜品与套餐一起展示，每页六件，换分类回到第一页', async () => {
    vi.mocked(getCategories).mockResolvedValue(categories)
    vi.mocked(getProducts).mockImplementation(async (c) =>
      Array.from(
        { length: c.type === 1 ? 8 : 2 },
        (_, index) => ({ id: index + 1, categoryId: c.id, type: c.type }) as Product,
      ),
    )
    const menu = scope.run(useMenu)!
    await menu.load()
    expect(menu.filtered.value).toHaveLength(10)
    expect(menu.visibleProducts.value).toHaveLength(6)
    menu.page.value = 2
    expect(menu.visibleProducts.value).toHaveLength(4)
    menu.selectCategory(2)
    expect(menu.page.value).toBe(1)
    expect(menu.categoryName.value).toBe('套餐')
    expect(menu.visibleProducts.value).toHaveLength(2)
  })
  it('旧会话或离开页面后的响应不能写入当前页面', async () => {
    let resolve!: (value: Product[]) => void
    vi.mocked(getCategories).mockResolvedValue([categories[0]!])
    vi.mocked(getProducts).mockReturnValue(
      new Promise((r) => {
        resolve = r
      }),
    )
    const menu = scope.run(useMenu)!
    const pending = menu.load()
    await Promise.resolve()
    setToken('B')
    resolve([{ id: 1 }] as Product[])
    await pending
    expect(menu.products.value).toEqual([])
    const next = menu.load()
    await Promise.resolve()
    scope.stop()
    resolve([{ id: 2 }] as Product[])
    await next
    expect(menu.products.value).toEqual([])
  })
  it('列表加载失败保留错误，重试成功后清除', async () => {
    vi.mocked(getCategories).mockRejectedValueOnce(new Error('网络断开')).mockResolvedValueOnce([])
    const menu = scope.run(useMenu)!
    await menu.load()
    expect(menu.error.value).toBe('网络断开')
    await menu.load()
    expect(menu.error.value).toBe('')
  })
})
