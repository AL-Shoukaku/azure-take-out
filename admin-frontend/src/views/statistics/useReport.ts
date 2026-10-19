import { watch, type Ref } from 'vue'
import type { ReportRange } from '@/api/report'
import { useAsyncData } from '@/composables/useAsyncData'

export function useReport<T>(load: (range: ReportRange) => Promise<T>, range: Ref<ReportRange>) {
  const resource = useAsyncData(() => load(range.value))
  watch(range, () => {
    resource.state.data = undefined
    void resource.reload()
  })
  return resource
}
