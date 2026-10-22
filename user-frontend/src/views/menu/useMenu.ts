import { computed, onScopeDispose, ref } from 'vue'
import { getCategories, getProducts, type Category, type Product } from '@/api/menu'
import { getToken } from '@/utils/auth'
import { menuPageSize } from './menuDisplay'

export function useMenu() {
  const categories = ref<Category[]>([])
  const products = ref<Product[]>([])
  const selectedCategory = ref(0)
  const page = ref(1)
  const loading = ref(false)
  const error = ref('')
  let revision = 0
  let active = true
  onScopeDispose(() => {
    active = false
    revision += 1
  })
  const filtered = computed(() =>
    selectedCategory.value === 0
      ? products.value
      : products.value.filter((item) => item.categoryId === selectedCategory.value),
  )
  const visibleProducts = computed(() =>
    filtered.value.slice((page.value - 1) * menuPageSize, page.value * menuPageSize),
  )
  const categoryName = computed(
    () => categories.value.find((c) => c.id === selectedCategory.value)?.name ?? '全部餐点',
  )
  function selectCategory(id: number): void {
    selectedCategory.value = id
    page.value = 1
  }
  async function load(): Promise<void> {
    const current = ++revision
    const token = getToken()
    loading.value = true
    error.value = ''
    try {
      const groups = await getCategories()
      const result = await Promise.all(groups.map(getProducts))
      if (!active || current !== revision || token !== getToken()) return
      categories.value = groups
      products.value = result.flat()
      page.value = 1
    } catch (cause) {
      if (active && current === revision && token === getToken())
        error.value = cause instanceof Error ? cause.message : '餐点加载失败'
    } finally {
      if (active && current === revision) loading.value = false
    }
  }
  return {
    categories,
    products,
    selectedCategory,
    page,
    loading,
    error,
    filtered,
    visibleProducts,
    categoryName,
    selectCategory,
    load,
  }
}
