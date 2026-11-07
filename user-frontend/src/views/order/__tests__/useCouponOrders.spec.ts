import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, type EffectScope } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises } from '@vue/test-utils'
import { useCouponOrders } from '../useCouponOrders'
import {
  getCouponOrder,
  getCouponOrders,
  type CouponOrderDetail,
  type CouponOrderPageResult,
  type CouponOrderSummary,
} from '@/api/couponOrders'
import { useSessionStore } from '@/stores/session'
import { removeToken } from '@/utils/auth'

vi.mock('@/api/couponOrders', () => ({
  getCouponOrders: vi.fn<typeof getCouponOrders>(),
  getCouponOrder: vi.fn<typeof getCouponOrder>(),
}))
let scope: EffectScope
const first = { id: 1, number: 'COUPON-ORDER-1', status: 0 } as CouponOrderSummary
const firstDetail = { ...first, couponNumber: 'COUPON-1' } as CouponOrderDetail
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
  vi.mocked(getCouponOrders).mockResolvedValue({ total: 1, records: [first] })
  vi.mocked(getCouponOrder).mockResolvedValue(firstDetail)
})
afterEach(() => {
  scope.stop()
  removeToken()
})

describe('优惠券订单列表与详情请求', () => {
  it('全部订单不传状态，切换筛选时回到第一页并传入对应状态', async () => {
    const orders = scope.run(useCouponOrders)!
    await flushPromises()
    expect(getCouponOrders).toHaveBeenLastCalledWith(1, 6, null)
    vi.mocked(getCouponOrders).mockResolvedValue({ total: 12, records: [first] })
    orders.page.value = 2
    await flushPromises()
    expect(getCouponOrders).toHaveBeenLastCalledWith(2, 6, null)
    orders.selectTab('unpaid')
    await flushPromises()
    expect(orders.page.value).toBe(1)
    expect(getCouponOrders).toHaveBeenLastCalledWith(1, 6, 0)
  })
  it('快速切换筛选时旧响应不能覆盖新列表', async () => {
    const old = deferred<CouponOrderPageResult>()
    vi.mocked(getCouponOrders).mockReturnValueOnce(old.promise)
    const orders = scope.run(useCouponOrders)!
    orders.selectTab('closed')
    vi.mocked(getCouponOrders).mockResolvedValueOnce({ total: 0, records: [] })
    await flushPromises()
    old.resolve({ total: 1, records: [first] })
    await flushPromises()
    expect(orders.records.value).toEqual([])
  })
  it('超时关闭导致最后一页为空时，回退到仍存在的页码', async () => {
    vi.mocked(getCouponOrders).mockResolvedValue({ total: 7, records: [first] })
    const orders = scope.run(useCouponOrders)!
    await flushPromises()
    orders.page.value = 2
    await flushPromises()
    vi.mocked(getCouponOrders).mockResolvedValue({ total: 6, records: [first] })
    await orders.load()
    await flushPromises()
    expect(orders.page.value).toBe(1)
    expect(getCouponOrders).toHaveBeenLastCalledWith(1, 6, null)
  })
  it('列表失败保留后端错误，重新加载成功后清除错误', async () => {
    vi.mocked(getCouponOrders).mockRejectedValueOnce(new Error('订单暂时不可用'))
    const orders = scope.run(useCouponOrders)!
    await flushPromises()
    expect(orders.error.value).toBe('订单暂时不可用')
    expect(orders.loading.value).toBe(false)
    await orders.load()
    expect(orders.error.value).toBe('')
    expect(orders.records.value).toEqual([first])
  })
  it('支付成功后的刷新重读列表和已打开的详情，详情关闭时只重读列表', async () => {
    const orders = scope.run(useCouponOrders)!
    await flushPromises()
    await orders.openDetail(1)
    vi.mocked(getCouponOrders).mockClear()
    vi.mocked(getCouponOrder).mockClear()
    await orders.refresh()
    expect(getCouponOrders).toHaveBeenCalledTimes(1)
    expect(getCouponOrder).toHaveBeenCalledWith(1)
    orders.detailOpen.value = false
    vi.mocked(getCouponOrder).mockClear()
    await orders.refresh()
    expect(getCouponOrder).not.toHaveBeenCalled()
  })
  it('关闭后旧详情不能覆盖重新打开的另一订单', async () => {
    const old = deferred<CouponOrderDetail>()
    vi.mocked(getCouponOrder).mockReturnValueOnce(old.promise)
    const orders = scope.run(useCouponOrders)!
    void orders.openDetail(1)
    orders.detailOpen.value = false
    await nextTick()
    vi.mocked(getCouponOrder).mockResolvedValueOnce({ ...firstDetail, id: 2 })
    await orders.openDetail(2)
    old.resolve(firstDetail)
    await flushPromises()
    expect(orders.detail.value?.id).toBe(2)
  })
  it('退出会话或离开页面后迟到的列表不能写回', async () => {
    const old = deferred<CouponOrderPageResult>()
    vi.mocked(getCouponOrders).mockReturnValueOnce(old.promise)
    const orders = scope.run(useCouponOrders)!
    useSessionStore().clearSession()
    scope.stop()
    old.resolve({ total: 1, records: [first] })
    await flushPromises()
    expect(orders.records.value).toEqual([])
    expect(orders.detail.value).toBeNull()
  })
})
