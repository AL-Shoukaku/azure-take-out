import { onScopeDispose, shallowReactive } from 'vue'

export interface AsyncDataState<T> {
  data: T | undefined
  loading: boolean
  error: string
}

export function useAsyncData<T>(load: () => Promise<T>) {
  const state = shallowReactive<AsyncDataState<T>>({ data: undefined, loading: false, error: '' })
  let sequence = 0
  async function reload() {
    const current = ++sequence
    state.loading = true
    state.error = ''
    try {
      const data = await load()
      if (current === sequence) state.data = data
    } catch (error) {
      if (current === sequence) state.error = (error as Error).message
    } finally {
      if (current === sequence) state.loading = false
    }
  }
  onScopeDispose(() => sequence++)
  void reload()
  return { state, reload }
}
