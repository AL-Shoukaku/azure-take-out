import { computed, onMounted, onScopeDispose, ref, watch } from 'vue'
import type { UserCoupon } from '@/api/userCoupons'
import { useCartStore } from '@/stores/cart'
import { useSessionStore } from '@/stores/session'
import { couponDisabledReason, previewCoupons } from './couponPreview'

export function useCouponPreview() {
  const cart = useCartStore()
  const session = useSessionStore()
  const selectedCoupons = ref<UserCoupon[]>([])
  const pickerOpen = ref(false)
  const now = ref(Date.now())
  const fees = computed(() => cart.packAmount + cart.deliveryAmount)
  const couponPreview = computed(() =>
    previewCoupons(selectedCoupons.value, cart.amount, fees.value, now.value),
  )
  let timer: ReturnType<typeof setInterval> | undefined
  onMounted(() => {
    timer = setInterval(() => {
      now.value = Date.now()
    }, 1000)
  })
  onScopeDispose(() => {
    clearInterval(timer)
  })
  watch([() => cart.amount, now], () => {
    selectedCoupons.value = selectedCoupons.value.filter(
      (coupon) => !couponDisabledReason(coupon, cart.amount, now.value),
    )
  })
  watch(
    () => session.token,
    () => {
      selectedCoupons.value = []
      pickerOpen.value = false
    },
    { flush: 'sync' },
  )
  function openPicker(): void {
    now.value = Date.now()
    pickerOpen.value = true
  }
  return { selectedCoupons, pickerOpen, now, fees, couponPreview, openPicker }
}
