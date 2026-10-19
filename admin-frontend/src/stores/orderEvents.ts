import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useOrderEventsStore = defineStore('order-events', () => {
  const revision = ref(0)
  function received() {
    revision.value++
  }
  return { revision, received }
})
