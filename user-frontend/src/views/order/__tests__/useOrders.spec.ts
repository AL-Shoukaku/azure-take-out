import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, type EffectScope } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises } from '@vue/test-utils'
import { useOrders } from '../useOrders'
import { getOrders, getOrder, type Order } from '@/api/orders'
import { useSessionStore } from '@/stores/session'
import { removeToken } from '@/utils/auth'

vi.mock('@/api/orders', () => ({
  getOrders: vi.fn<typeof getOrders>(),
  getOrder: vi.fn<typeof getOrder>(),
}))
let scope: EffectScope
const first = { id: 1, number: 'ORDER-1', status: 1 } as Order
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((r) => {
    resolve = r
  })
  return { promise, resolve }
}
beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  scope = effectScope()
  useSessionStore().startSession({ id: 7, username: '小蓝', avatar: null, token: 'token-A' })
  vi.mocked(getOrders).mockResolvedValue({ total: 1, records: [first] })
  vi.mocked(getOrder).mockResolvedValue(first)
})
afterEach(() => {
  scope.stop()
  removeToken()
})

describe('订单列表与详情请求', () => {
  it('筛选时回到第一页，进行中一次查询三个状态', async () => {
    const orders = scope.run(useOrders)!
    await flushPromises()
    vi.mocked(getOrders).mockResolvedValue({ total: 12, records: [first] })
    orders.page.value = 2
    await flushPromises()
    orders.selectTab('active')
    await flushPromises()
    expect(orders.page.value).toBe(1)
    expect(getOrders).toHaveBeenLastCalledWith(1, [2, 3, 4])
  })
  it('快速切换筛选时旧响应不能覆盖新列表', async () => {
    const old = deferred<{ total: number; records: Order[] }>()
    vi.mocked(getOrders).mockReturnValueOnce(old.promise)
    const orders = scope.run(useOrders)!
    orders.selectTab('completed')
    vi.mocked(getOrders).mockResolvedValueOnce({ total: 0, records: [] })
    await flushPromises()
    old.resolve({ total: 1, records: [first] })
    await flushPromises()
    expect(orders.records.value).toEqual([])
  })
  it('取消导致最后一页为空时，回退到仍存在的页码', async () => {
    vi.mocked(getOrders).mockResolvedValue({ total: 7, records: [first] })
    const orders = scope.run(useOrders)!
    await flushPromises()
    orders.page.value = 2
    await flushPromises()
    vi.mocked(getOrders).mockResolvedValue({ total: 6, records: [first] })
    await orders.load()
    await flushPromises()
    expect(orders.page.value).toBe(1)
    expect(getOrders).toHaveBeenLastCalledWith(1, [])
  })
  it('关闭后旧详情不能覆盖重新打开的另一订单', async () => {
    const old = deferred<Order>()
    vi.mocked(getOrder).mockReturnValueOnce(old.promise)
    const orders = scope.run(useOrders)!
    void orders.openDetail(1)
    orders.detailOpen.value = false
    await nextTick()
    vi.mocked(getOrder).mockResolvedValueOnce({ ...first, id: 2 })
    await orders.openDetail(2)
    old.resolve(first)
    await flushPromises()
    expect(orders.detail.value?.id).toBe(2)
  })
  it('退出会话或离开页面后迟到的列表不能写回', async () => {
    const old = deferred<{ total: number; records: Order[] }>()
    vi.mocked(getOrders).mockReturnValueOnce(old.promise)
    const orders = scope.run(useOrders)!
    useSessionStore().clearSession()
    scope.stop()
    old.resolve({ total: 1, records: [first] })
    await flushPromises()
    expect(orders.records.value).toEqual([])
    expect(orders.detail.value).toBeNull()
  })
})
