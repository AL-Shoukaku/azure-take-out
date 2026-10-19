<template>
  <ElDialog
    v-model="visible"
    title="添加菜品"
    class="admin-dialog setmeal-dish-dialog"
    width="900px"
    top="12vh"
    append-to-body
    @open="open"
  >
    <div v-if="categoryError" class="management-load-error" role="alert">
      {{ categoryError }} <ElButton @click="open">重试</ElButton>
    </div>
    <div v-else class="dish-picker">
      <nav class="picker-categories" aria-label="菜品分类">
        <ElButton
          v-for="category in categories"
          :key="category.id"
          text
          :class="{ 'is-current': category.id === categoryId }"
          @click="categoryId = category.id"
          >{{ category.name }}</ElButton
        >
        <ElEmpty
          v-if="!loadingCategories && !categories.length"
          description="暂无菜品分类"
          :image-size="50"
        />
      </nav>
      <div v-loading="loading" class="picker-dishes">
        <div v-if="loadError" role="alert">
          {{ loadError }} <ElButton @click="loadDishes">重试</ElButton>
        </div>
        <ElEmpty
          v-else-if="!loading && !availableDishes.length"
          description="暂无在售菜品"
          :image-size="80"
        />
        <template v-else>
          <div v-for="dish in availableDishes" :key="dish.id" class="picker-dish-row">
            <ElCheckbox
              :model-value="selected.some((item) => item.dishId === dish.id)"
              @change="(checked) => toggle(dish, !!checked)"
              ><span class="picker-dish-name">{{ dish.name }}</span
              ><span>在售</span><span>￥{{ dish.price }}</span></ElCheckbox
            >
          </div>
        </template>
      </div>
      <div class="picker-selected">
        <h3>已选菜品({{ selected.length }})</h3>
        <div v-for="(dish, index) in selected" :key="dish.dishId" class="picker-selected-row">
          <span>{{ dish.name }}</span
          ><span>￥{{ dish.price }}</span
          ><button
            type="button"
            :aria-label="`移除${dish.name}`"
            @click="selected.splice(index, 1)"
          >
            ×
          </button>
        </div>
      </div>
    </div>
    <template #footer
      ><ElButton @click="visible = false">取消</ElButton
      ><ElButton type="primary" @click="confirm">添加</ElButton></template
    >
  </ElDialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElButton, ElCheckbox, ElDialog, ElEmpty, vLoading } from 'element-plus'
import { getCategoryList, type Category } from '@/api/category'
import { getAvailableDishes, type ProductSummary, type SetmealDish } from '@/api/products'

const props = defineProps<{ dishes: SetmealDish[] }>()
const emit = defineEmits<{ confirm: [dishes: SetmealDish[]] }>()
const visible = defineModel<boolean>({ required: true })
const categories = ref<Category[]>([]),
  categoryId = ref<number>()
const availableDishes = ref<ProductSummary[]>([]),
  selected = ref<SetmealDish[]>([])
const loading = ref(false),
  loadingCategories = ref(false),
  loadError = ref(''),
  categoryError = ref('')
let requestId = 0

async function open() {
  selected.value = props.dishes.map((dish) => ({ ...dish }))
  categories.value = []
  categoryId.value = undefined
  availableDishes.value = []
  categoryError.value = ''
  loadingCategories.value = true
  try {
    categories.value = await getCategoryList(1)
    categoryId.value = categories.value[0]?.id
  } catch (error) {
    categoryError.value = (error as Error).message
  } finally {
    loadingCategories.value = false
  }
}
async function loadDishes() {
  const id = ++requestId
  availableDishes.value = []
  loadError.value = ''
  if (!categoryId.value) return
  loading.value = true
  try {
    const result = await getAvailableDishes(categoryId.value)
    if (id === requestId) availableDishes.value = result
  } catch (error) {
    if (id === requestId) loadError.value = (error as Error).message
  } finally {
    if (id === requestId) loading.value = false
  }
}
function toggle(dish: ProductSummary, checked: boolean) {
  if (checked && !selected.value.some((item) => item.dishId === dish.id))
    selected.value.unshift({ dishId: dish.id, name: dish.name, price: dish.price, copies: 1 })
  else if (!checked) selected.value = selected.value.filter((item) => item.dishId !== dish.id)
}
function confirm() {
  emit(
    'confirm',
    selected.value.map((dish) => ({ ...dish })),
  )
  visible.value = false
}
watch(categoryId, loadDishes)
</script>

<style>
.setmeal-dish-dialog .el-dialog__body {
  padding: 0;
}
.dish-picker {
  display: grid;
  grid-template-columns: 125px minmax(0, 1fr) 300px;
  min-height: 400px;
}
.picker-categories {
  padding: 15px 0;
  border-right: 2px solid #f4f4f4;
  overflow-y: auto;
  max-height: 400px;
}
.picker-categories .el-button {
  display: flex;
  width: 100%;
  height: 40px;
  justify-content: center;
  margin: 0;
  border-radius: 0;
  color: #333;
  font-weight: 700;
}
.picker-categories .is-current {
  border-right: 2px solid var(--admin-primary);
  color: var(--admin-primary);
}
.picker-dishes {
  padding: 15px;
  border-right: 1px solid #efefef;
  max-height: 400px;
  overflow-y: auto;
}
.picker-dish-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  padding: 0 10px;
  border: 1px solid #f4f4f4;
  cursor: pointer;
  color: #666;
  font-size: 12px;
}
.picker-dish-row + .picker-dish-row {
  border-top: 0;
}
.picker-dish-name {
  flex: 1;
  overflow-wrap: anywhere;
}
.picker-dish-row .el-checkbox {
  width: 100%;
  height: auto;
  min-height: 40px;
}
.picker-dish-row .el-checkbox__label {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 10px;
  min-width: 0;
  color: #666;
  font-size: 12px;
  font-weight: 400;
}
.picker-selected {
  padding: 15px;
  max-height: 400px;
  overflow-y: auto;
}
.picker-selected h3 {
  margin: 0 0 16px;
  font-size: 14px;
}
.picker-selected-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  padding: 10px;
  box-shadow: 0 1px 4px 3px rgb(0 0 0 / 3%);
  color: #666;
}
.picker-selected-row > span:first-child {
  flex: 1;
}
.picker-selected-row button {
  padding: 0;
  border: 0;
  background: none;
  font-size: 20px;
  color: #999;
  cursor: pointer;
}
@media (max-width: 1000px) {
  .dish-picker {
    grid-template-columns: 100px minmax(0, 1fr) 220px;
  }
}
</style>
