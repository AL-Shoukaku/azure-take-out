import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { getAddresses, setDefaultAddress, deleteAddress, type Address } from '@/api/addresses'
import { useSessionStore } from './session'

export const useAddressesStore = defineStore('addresses', () => {
  const session = useSessionStore()
  const addresses = ref<Address[]>([])
  const selectedId = ref<number | null>(null)
  const loaded = ref(false)
  const loading = ref(false)
  const loadError = ref('')
  const pendingId = ref<number | null>(null)
  let readRevision = 0
  let sessionRevision = 0

  watch(
    () => session.token,
    () => {
      sessionRevision += 1
      readRevision += 1
      addresses.value = []
      selectedId.value = null
      loaded.value = false
      loading.value = false
      loadError.value = ''
      pendingId.value = null
    },
    { flush: 'sync' },
  )

  function reconcileSelection(): void {
    if (!addresses.value.some((address) => address.id === selectedId.value)) {
      selectedId.value = addresses.value.find((address) => address.isDefault === 1)?.id ?? null
    }
  }

  function select(id: number): void {
    selectedId.value = id
  }

  async function load(): Promise<void> {
    const revision = ++readRevision
    loading.value = true
    loadError.value = ''
    try {
      const result = await getAddresses()
      if (revision !== readRevision) return
      addresses.value = result
      loaded.value = true
      reconcileSelection()
    } catch (error) {
      if (revision === readRevision) {
        loadError.value = error instanceof Error ? error.message : '请稍后重试'
      }
    } finally {
      if (revision === readRevision) loading.value = false
    }
  }

  async function mutate(id: number, action: 'default' | 'delete'): Promise<boolean> {
    if (pendingId.value !== null) return false
    const revision = sessionRevision
    pendingId.value = id
    try {
      if (action === 'default') await setDefaultAddress(id)
      else await deleteAddress(id)
      if (revision !== sessionRevision) return false
      // 已确认的写入优先于此前发出的列表请求。
      readRevision += 1
      loading.value = false
      loadError.value = ''
      if (action === 'default') {
        addresses.value = addresses.value.map((address) => ({
          ...address,
          isDefault: address.id === id ? 1 : 0,
        }))
        selectedId.value = id
      } else {
        addresses.value = addresses.value.filter((address) => address.id !== id)
        reconcileSelection()
      }
      return true
    } finally {
      if (revision === sessionRevision) pendingId.value = null
    }
  }

  function makeDefault(id: number): Promise<boolean> {
    return mutate(id, 'default')
  }
  function remove(id: number): Promise<boolean> {
    return mutate(id, 'delete')
  }

  return {
    addresses,
    selectedId,
    loaded,
    loading,
    loadError,
    pendingId,
    load,
    select,
    makeDefault,
    remove,
  }
})
