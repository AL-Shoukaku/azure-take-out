import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import {
  getCart,
  addCartItem,
  subtractCartItem,
  cleanCart,
  type CartItem,
  type CartProduct,
} from '@/api/cart'
import { deliveryFee, packagingFee } from '@/utils/pricing'
import { useSessionStore } from './session'

export const useCartStore = defineStore('cart', () => {
  const session = useSessionStore()
  const items = ref<CartItem[]>([])
  const loading = ref(false)
  const busy = ref(false)
  const error = ref('')
  let revision = 0
  let sessionRevision = 0
  const count = computed(() => items.value.reduce((sum, item) => sum + item.number, 0))
  const amount = computed(
    () =>
      items.value.reduce((sum, item) => sum + Math.round(item.amount * 100) * item.number, 0) / 100,
  )
  const packAmount = computed(() => count.value * packagingFee)
  const deliveryAmount = computed(() => (count.value ? deliveryFee : 0))
  const total = computed(
    () =>
      (Math.round(amount.value * 100) + packAmount.value * 100 + deliveryAmount.value * 100) / 100,
  )
  watch(
    () => session.token,
    () => {
      revision += 1
      sessionRevision += 1
      items.value = []
      loading.value = false
      busy.value = false
      error.value = ''
    },
    { flush: 'sync' },
  )

  async function load(): Promise<void> {
    const current = ++revision
    loading.value = true
    try {
      const result = await getCart()
      if (current === revision) {
        items.value = result
        error.value = ''
      }
    } catch (cause) {
      if (current === revision)
        error.value = cause instanceof Error ? cause.message : '购物车加载失败'
      throw cause
    } finally {
      if (current === revision) loading.value = false
    }
  }
  async function mutate(
    action: 'add' | 'subtract' | 'clean',
    product?: CartProduct,
  ): Promise<boolean> {
    if (busy.value || loading.value || error.value) return false
    const current = sessionRevision
    revision += 1
    busy.value = true
    try {
      if (action === 'clean') await cleanCart()
      else if (action === 'add') await addCartItem(product!)
      else await subtractCartItem(product!)
      if (current !== sessionRevision) return false
      await load()
      return current === sessionRevision
    } finally {
      if (current === sessionRevision) busy.value = false
    }
  }
  function reset(): void {
    revision += 1
    items.value = []
    error.value = ''
    loading.value = false
  }
  return {
    items,
    count,
    amount,
    packAmount,
    deliveryAmount,
    total,
    loading,
    busy,
    error,
    load,
    mutate,
    reset,
  }
})
