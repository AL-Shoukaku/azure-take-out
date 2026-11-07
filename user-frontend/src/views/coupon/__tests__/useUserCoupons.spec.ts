import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, type EffectScope } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises } from '@vue/test-utils'
import { useUserCoupons } from '../useUserCoupons'
import {
  getUserCoupon,
  getUserCoupons,
  type UserCoupon,
  type UserCouponPageResult,
} from '@/api/userCoupons'
import { useSessionStore } from '@/stores/session'
import { removeToken } from '@/utils/auth'

vi.mock('@/api/userCoupons', () => ({
  getUserCoupons: vi.fn<typeof getUserCoupons>(),
  getUserCoupon: vi.fn<typeof getUserCoupon>(),
}))
let scope: EffectScope
const first = { id: 1, number: 'COUPON-001', status: 0 } as UserCoupon
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
  vi.mocked(getUserCoupons).mockResolvedValue({ total: 1, records: [first] })
  vi.mocked(getUserCoupon).mockResolvedValue(first)
})
afterEach(() => {
  scope.stop()
  removeToken()
})

describe('我的优惠券列表与详情请求', () => {
  it('全部优惠券不传状态，切换筛选时回到第一页并传入对应状态', async () => {
    const coupons = scope.run(useUserCoupons)!
    await flushPromises()
    expect(getUserCoupons).toHaveBeenLastCalledWith(1, 6, null)
    vi.mocked(getUserCoupons).mockResolvedValue({ total: 12, records: [first] })
    coupons.page.value = 2
    await flushPromises()
    expect(getUserCoupons).toHaveBeenLastCalledWith(2, 6, null)
    coupons.selectTab('unused')
    await flushPromises()
    expect(coupons.page.value).toBe(1)
    expect(getUserCoupons).toHaveBeenLastCalledWith(1, 6, 0)
  })
  it('快速切换筛选时旧响应不能覆盖新列表', async () => {
    const old = deferred<UserCouponPageResult>()
    vi.mocked(getUserCoupons).mockReturnValueOnce(old.promise)
    const coupons = scope.run(useUserCoupons)!
    coupons.selectTab('expired')
    vi.mocked(getUserCoupons).mockResolvedValueOnce({ total: 0, records: [] })
    await flushPromises()
    old.resolve({ total: 1, records: [first] })
    await flushPromises()
    expect(coupons.records.value).toEqual([])
  })
  it('最后一页为空时，回退到仍存在的页码', async () => {
    vi.mocked(getUserCoupons).mockResolvedValue({ total: 7, records: [first] })
    const coupons = scope.run(useUserCoupons)!
    await flushPromises()
    coupons.page.value = 2
    await flushPromises()
    vi.mocked(getUserCoupons).mockResolvedValue({ total: 6, records: [first] })
    await coupons.load()
    await flushPromises()
    expect(coupons.page.value).toBe(1)
    expect(getUserCoupons).toHaveBeenLastCalledWith(1, 6, null)
  })
  it('列表失败保留后端错误，重新加载成功后清除错误', async () => {
    vi.mocked(getUserCoupons).mockRejectedValueOnce(new Error('优惠券暂时不可用'))
    const coupons = scope.run(useUserCoupons)!
    await flushPromises()
    expect(coupons.error.value).toBe('优惠券暂时不可用')
    expect(coupons.loading.value).toBe(false)
    await coupons.load()
    expect(coupons.error.value).toBe('')
    expect(coupons.records.value).toEqual([first])
  })
  it('关闭后旧详情不能覆盖重新打开的另一张券', async () => {
    const old = deferred<UserCoupon>()
    vi.mocked(getUserCoupon).mockReturnValueOnce(old.promise)
    const coupons = scope.run(useUserCoupons)!
    void coupons.openDetail(1)
    coupons.detailOpen.value = false
    await nextTick()
    vi.mocked(getUserCoupon).mockResolvedValueOnce({ ...first, id: 2 })
    await coupons.openDetail(2)
    old.resolve(first)
    await flushPromises()
    expect(coupons.detail.value?.id).toBe(2)
  })
  it('退出会话或离开页面后迟到的列表不能写回', async () => {
    const old = deferred<UserCouponPageResult>()
    vi.mocked(getUserCoupons).mockReturnValueOnce(old.promise)
    const coupons = scope.run(useUserCoupons)!
    useSessionStore().clearSession()
    scope.stop()
    old.resolve({ total: 1, records: [first] })
    await flushPromises()
    expect(coupons.records.value).toEqual([])
    expect(coupons.detail.value).toBeNull()
  })
})
