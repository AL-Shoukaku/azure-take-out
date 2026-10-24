<template>
  <ElDialog
    v-model="visible"
    :title="product.type === 2 ? '套餐详情' : '菜品详情与口味'"
    width="580px"
    class="user-dialog product-dialog"
    modal-class="user-dialog-overlay"
    align-center
    append-to-body
    destroy-on-close
    :show-close="!busy"
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
  >
    <DishImage :src="product.image" :name="product.name" class="detail-photo" />
    <div class="detail-heading">
      <h2>{{ product.name }}</h2>
      <strong>{{ money(product.price) }}</strong>
    </div>
    <p class="description">{{ product.description || product.name }}</p>
    <template v-if="product.type === 2">
      <h3>套餐包含</h3>
      <ElSkeleton v-if="loading" :rows="2" animated />
      <ElAlert v-else-if="error" :title="error" type="error" :closable="false"
        ><ElButton link type="primary" @click="loadItems">重新加载</ElButton></ElAlert
      >
      <div v-else class="setmeal-items">
        <div v-for="(item, index) in items" :key="index" class="setmeal-item">
          <DishImage :src="item.image" :name="item.name" />
          <div>
            <b>{{ item.name }} × {{ item.copies }}</b>
            <p>{{ item.description }}</p>
          </div>
        </div>
      </div>
    </template>
    <div v-for="group in groups" :key="group.id" class="flavor-group">
      <h3>{{ group.name }}</h3>
      <ElRadioGroup v-model="selections[group.id]" :aria-label="group.name" :disabled="busy"
        ><ElRadio v-for="option in group.options" :key="option" :value="option" border>{{
          option
        }}</ElRadio></ElRadioGroup
      >
    </div>
    <p v-if="groups.length" class="flavor-help">每项可选一种，未选择时使用默认口味。</p>
    <template #footer
      ><ElButton
        type="primary"
        class="confirm-add"
        :loading="busy"
        :disabled="disabled"
        @click="confirm"
        >确认加入</ElButton
      ></template
    >
  </ElDialog>
</template>
<script setup lang="ts">
import { computed, onScopeDispose, ref, watch } from 'vue'
import { ElAlert, ElButton, ElDialog, ElRadio, ElRadioGroup, ElSkeleton } from 'element-plus'
import { getSetmealItems, type Product, type SetmealItem } from '@/api/menu'
import DishImage from '@/components/DishImage.vue'
import { money } from '@/utils/pricing'
import { flavorGroups } from '../menuDisplay'
const props = defineProps<{ product: Product; busy: boolean; disabled: boolean }>()
const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ add: [product: Product, flavor: string | null] }>()
const groups = computed(() => flavorGroups(props.product.flavors))
const selections = ref<Record<number, string>>({})
const items = ref<SetmealItem[]>([])
const loading = ref(false)
const error = ref('')
let revision = 0
onScopeDispose(() => {
  revision += 1
})
async function loadItems(): Promise<void> {
  const current = ++revision
  loading.value = true
  error.value = ''
  try {
    const result = await getSetmealItems(props.product.id)
    if (current === revision) items.value = result
  } catch (cause) {
    if (current === revision)
      error.value = cause instanceof Error ? cause.message : '套餐明细加载失败'
  } finally {
    if (current === revision) loading.value = false
  }
}
watch(
  () => [visible.value, props.product],
  () => {
    revision += 1
    selections.value = {}
    items.value = []
    if (visible.value && props.product.type === 2) void loadItems()
  },
  { immediate: true },
)
function confirm(): void {
  emit(
    'add',
    props.product,
    groups.value
      .map((group) => selections.value[group.id])
      .filter(Boolean)
      .join(',') || null,
  )
}
</script>
<style scoped>
.detail-photo {
  width: 100%;
  height: auto;
  aspect-ratio: 1.9;
  border-radius: 11px;
}
.detail-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 20px;
  margin-top: 22px;
}
h2 {
  margin: 0;
  font-size: 24px;
}
.detail-heading strong {
  color: var(--user-primary);
  font-size: 26px;
  font-weight: 500;
}
.description {
  margin: 14px 0 28px;
  color: #8998ad;
  line-height: 1.8;
}
h3 {
  margin: 20px 0 13px;
  font-size: 15px;
  font-weight: 500;
}
.flavor-group .el-radio-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.flavor-group .el-radio {
  margin: 0;
  height: 39px;
  border-radius: 7px;
}
.flavor-help {
  margin: 20px 0 0;
  color: #8998ad;
  font-size: 12px;
}
.confirm-add {
  width: 100%;
}
.setmeal-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid #edf1f7;
}
.setmeal-item p {
  margin: 7px 0 0;
  color: #8998ad;
  font-size: 12px;
  line-height: 1.6;
}
.setmeal-item b {
  font-weight: 500;
}
</style>
