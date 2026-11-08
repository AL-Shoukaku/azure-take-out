import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { UserCoupon } from '@/api/userCoupons'
import type { CartItem } from '@/api/cart'
import { useCartStore } from '@/stores/cart'
import { useSessionStore } from '@/stores/session'
import { removeToken } from '@/utils/auth'
import { useCouponPreview } from '../useCouponPreview'

let wrapper: VueWrapper
let preview: ReturnType<typeof useCouponPreview>
const coupon: UserCoupon = {
  id: 1,
  couponId: 101,
  number: 'OWNED-1',
  name: '满减券',
  type: 2,
  status: 0,
  discountAmount: 8,
  thresholdAmount: 30,
  expireTime: '2026-11-08 10:01',
  createTime: '2026-11-07 10:00',
  useTime: null,
  refundTime: null,
}
beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-11-08T10:00:00+08:00'))
  setActivePinia(createPinia())
  wrapper = mount(
    defineComponent({
      setup() {
        preview = useCouponPreview()
        return () => null
      },
    }),
  )
})
afterEach(() => {
  wrapper.unmount()
  vi.useRealTimers()
  removeToken()
})

it('餐袋金额改变时重算，低于门槛或清空后移除选择，不改变购物车真实金额', async () => {
  const cart = useCartStore()
  cart.items = [{ id: 1, amount: 16, number: 2 }] as CartItem[]
  preview.selectedCoupons.value = [coupon]
  await nextTick()
  expect(preview.couponPreview.value.total).toBe(32)
  expect(cart.total).toBe(40)
  cart.items[0]!.number = 1
  await nextTick()
  expect(preview.selectedCoupons.value).toEqual([])
  expect(preview.couponPreview.value.total).toBe(23)
  preview.selectedCoupons.value = [{ ...coupon, thresholdAmount: 0 }]
  cart.reset()
  await nextTick()
  expect(preview.selectedCoupons.value).toEqual([])
})

it('过期后自动移除试算，切换用户关闭弹窗并清除选择', async () => {
  useCartStore().items = [{ id: 1, amount: 16, number: 2 }] as CartItem[]
  preview.selectedCoupons.value = [coupon]
  await vi.advanceTimersByTimeAsync(60_000)
  expect(preview.selectedCoupons.value).toEqual([])
  preview.selectedCoupons.value = [{ ...coupon, expireTime: '2099-01-01 10:00' }]
  preview.openPicker()
  useSessionStore().startSession({
    id: 8,
    username: '另一个用户',
    avatar: null,
    token: 'another-token',
  })
  expect(preview.selectedCoupons.value).toEqual([])
  expect(preview.pickerOpen.value).toBe(false)
})
