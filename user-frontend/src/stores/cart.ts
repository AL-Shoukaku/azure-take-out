import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { getCart, type CartItem } from '@/api/cart'
import { useSessionStore } from './session'

export const useCartStore = defineStore('cart', () => {
  const session = useSessionStore()
  const items = ref<CartItem[]>([])
  let revision = 0
  const count = computed(() => items.value.reduce((sum, item) => sum + item.number, 0))
  const amount = computed(
    () =>
      items.value.reduce((sum, item) => sum + Math.round(item.amount * 100) * item.number, 0) / 100,
  )
  watch(
    () => session.token,
    () => {
      revision += 1
      items.value = []
    },
    { flush: 'sync' },
  )

  async function load(): Promise<void> {
    const current = ++revision
    const result = await getCart()
    if (current === revision) items.value = result
  }
  return { items, count, amount, load }
})
