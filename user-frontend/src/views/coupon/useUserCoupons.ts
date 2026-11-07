import { onScopeDispose, ref, watch } from 'vue'
import { getUserCoupon, getUserCoupons, type UserCoupon } from '@/api/userCoupons'
import { useSessionStore } from '@/stores/session'
import { getToken } from '@/utils/auth'
import { userCouponPageSize, userCouponTabs, type UserCouponTab } from './myCouponDisplay'

export function useUserCoupons() {
  const session = useSessionStore()
  const tab = ref<UserCouponTab>('all')
  const page = ref(1)
  const records = ref<UserCoupon[]>([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref('')
  const detailOpen = ref(false)
  const detailId = ref<number | null>(null)
  const detail = ref<UserCoupon | null>(null)
  const detailLoading = ref(false)
  const detailError = ref('')
  let listRevision = 0
  let detailRevision = 0
  let active = true

  function invalidate(): void {
    listRevision += 1
    detailRevision += 1
  }
  watch(
    () => session.token,
    () => {
      invalidate()
      records.value = []
      total.value = 0
      detailOpen.value = false
      detail.value = null
      loading.value = false
    },
    { flush: 'sync' },
  )
  onScopeDispose(() => {
    active = false
    invalidate()
  })
  watch(
    detailOpen,
    (open) => {
      if (!open) {
        detailRevision += 1
        detailId.value = null
        detail.value = null
      }
    },
    { flush: 'sync' },
  )

  async function load(): Promise<void> {
    const revision = ++listRevision
    const token = getToken()
    const current = () => active && revision === listRevision && token === getToken()
    loading.value = true
    error.value = ''
    try {
      const status = userCouponTabs.find((option) => option.key === tab.value)!.status
      const result = await getUserCoupons(page.value, userCouponPageSize, status)
      if (!current()) return
      const maxPage = Math.max(1, Math.ceil(result.total / userCouponPageSize))
      if (page.value > maxPage) {
        page.value = maxPage
        return
      }
      records.value = result.records
      total.value = result.total
    } catch (cause) {
      if (current()) error.value = cause instanceof Error ? cause.message : '请稍后重试'
    } finally {
      if (current()) loading.value = false
    }
  }

  async function loadDetail(): Promise<void> {
    if (detailId.value === null) return
    const revision = ++detailRevision
    const token = getToken()
    const current = () => active && revision === detailRevision && token === getToken()
    detailLoading.value = true
    detailError.value = ''
    try {
      const result = await getUserCoupon(detailId.value)
      if (current()) detail.value = result
    } catch (cause) {
      if (current()) detailError.value = cause instanceof Error ? cause.message : '请稍后重试'
    } finally {
      if (current()) detailLoading.value = false
    }
  }
  function openDetail(id: number): Promise<void> {
    detail.value = null
    detailId.value = id
    detailOpen.value = true
    return loadDetail()
  }
  function selectTab(value: UserCouponTab): void {
    tab.value = value
    page.value = 1
  }
  watch(
    [tab, page],
    () => {
      void load()
    },
    { immediate: true },
  )
  return {
    tab,
    page,
    records,
    total,
    loading,
    error,
    load,
    selectTab,
    detailOpen,
    detail,
    detailLoading,
    detailError,
    openDetail,
    loadDetail,
  }
}
