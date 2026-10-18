<template>
  <section class="product-form-page">
    <header class="product-form-heading">
      <button type="button" :disabled="saving || uploading" @click="cancel">← 返回</button>
      <h1>{{ productId ? '修改' : '新增' }}{{ label }}</h1>
    </header>
    <div v-loading="loading" class="product-form-panel">
      <div v-if="loadError" class="management-load-error" role="alert">
        {{ loadError }} <ElButton @click="loadForm">重试</ElButton>
      </div>
      <ElForm
        id="product-form"
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="180px"
        :disabled="loading || saving || !!loadError"
        @submit.prevent="save(false)"
      >
        <div class="product-form-grid">
          <ElFormItem :label="`${label}名称:`" prop="name"
            ><ElInput v-model="form.name" :placeholder="`请填写${label}名称`" maxlength="20"
          /></ElFormItem>
          <ElFormItem :label="`${label}分类:`" prop="categoryId"
            ><ElSelect
              v-model="form.categoryId"
              :aria-label="`${label}分类`"
              :placeholder="`请选择${label}分类`"
              ><ElOption
                v-for="category in categoryOptions"
                :key="category.id"
                :label="category.name"
                :value="category.id"
                :disabled="category.status === 0" /></ElSelect
          ></ElFormItem>
          <ElFormItem :label="`${label}价格:`" prop="price"
            ><ElInput v-model="form.price" :placeholder="`请设置${label}价格`" inputmode="decimal"
          /></ElFormItem>
        </div>
        <ElFormItem v-if="kind === 'dish'" label="口味做法配置:" class="product-wide-field"
          ><FlavorEditor v-model="form.flavors" :disabled="saving || loading || !!loadError"
        /></ElFormItem>
        <ElFormItem v-else label="套餐菜品:" prop="setmealDishes" class="product-wide-field">
          <div class="setmeal-dishes" :class="{ 'has-dishes': form.setmealDishes.length }">
            <ElButton type="primary" @click="pickerVisible = true">+ 添加菜品</ElButton>
            <ElTable
              v-if="form.setmealDishes.length"
              :data="form.setmealDishes"
              class="setmeal-dish-table"
              row-key="dishId"
            >
              <ElTableColumn prop="name" label="名称" align="center" min-width="150" />
              <!-- @vue-generic {SetmealDish} -->
              <ElTableColumn label="原价" align="center" min-width="100"
                ><template #default="{ row }">{{
                  Number(row.price).toFixed(2)
                }}</template></ElTableColumn
              >
              <!-- @vue-generic {SetmealDish} -->
              <ElTableColumn label="份数" align="center" min-width="180"
                ><template #default="{ row }"
                  ><ElInputNumber
                    v-model="row.copies"
                    :min="1"
                    :max="99"
                    :precision="0"
                    :value-on-clear="1"
                    :aria-label="`${row.name}份数`" /></template
              ></ElTableColumn>
              <!-- @vue-generic {SetmealDish} -->
              <ElTableColumn label="操作" align="center" width="100"
                ><template #default="{ row }"
                  ><ElButton
                    link
                    class="management-action-red"
                    @click="
                      form.setmealDishes = form.setmealDishes.filter(
                        (dish) => dish.dishId !== row.dishId,
                      )
                    "
                    >删除</ElButton
                  ></template
                ></ElTableColumn
              >
            </ElTable>
          </div>
        </ElFormItem>
        <ElFormItem
          :label="`${label}图片:`"
          prop="image"
          class="product-wide-field product-image-field"
          ><ProductImageUpload
            v-model="form.image"
            v-model:uploading="uploading"
            :disabled="loading || saving || !!loadError"
        /></ElFormItem>
        <ElFormItem :label="`${label}描述:`" class="product-wide-field"
          ><ElInput
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="200"
            :placeholder="`${label}描述，最长200字`"
        /></ElFormItem>
      </ElForm>
      <div class="product-form-footer">
        <ElButton :disabled="saving || uploading" @click="cancel">取消</ElButton>
        <ElButton
          type="primary"
          :class="{ 'management-dark-button': !productId }"
          :loading="savingAction === 'save'"
          :disabled="loading || saving || uploading || !!loadError"
          native-type="submit"
          form="product-form"
          >保存</ElButton
        >
        <ElButton
          v-if="!productId"
          type="primary"
          :disabled="loading || saving || uploading || !!loadError"
          :loading="savingAction === 'continue'"
          @click="save(true)"
          >保存并继续添加</ElButton
        >
      </div>
    </div>
    <SetmealDishDialog
      v-if="kind === 'setmeal'"
      v-model="pickerVisible"
      :dishes="form.setmealDishes"
      @confirm="setDishes"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRouter } from 'vue-router'
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  vLoading,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { getCategoryList, type Category } from '@/api/category'
import {
  dishAPI,
  setmealAPI,
  type ProductFields,
  type ProductKind,
  type SaleStatus,
  type SetmealDish,
} from '@/api/products'
import ProductImageUpload from '@/components/ProductImageUpload.vue'
import FlavorEditor from './FlavorEditor.vue'
import SetmealDishDialog from './SetmealDishDialog.vue'
import type { EditableFlavor } from './flavors'
import '@/assets/styles/management.css'

const props = defineProps<{ kind: ProductKind; productId?: number }>()
const router = useRouter()
const label = computed(() => (props.kind === 'dish' ? '菜品' : '套餐'))
interface ProductForm {
  name: string
  categoryId?: number
  price: string
  image: string
  description: string
  status: SaleStatus
  flavors: EditableFlavor[]
  setmealDishes: SetmealDish[]
}
const emptyForm = (): ProductForm => ({
  name: '',
  categoryId: undefined,
  price: '',
  image: '',
  description: '',
  status: 0,
  flavors: [],
  setmealDishes: [],
})
const form = reactive<ProductForm>(emptyForm())
const formRef = ref<FormInstance>()
const categories = ref<Category[]>([]),
  currentCategoryName = ref('')
const categoryOptions = computed(() =>
  form.categoryId && !categories.value.some((category) => category.id === form.categoryId)
    ? [...categories.value, { id: form.categoryId, name: currentCategoryName.value, status: 0 }]
    : categories.value,
)
const loading = ref(false),
  loadError = ref(''),
  saving = ref(false),
  uploading = ref(false),
  pickerVisible = ref(false)
const savingAction = ref<'save' | 'continue' | null>(null)
let latestLoad = 0
const rules = computed<FormRules<ProductForm>>(() => ({
  name: [
    { required: true, message: `请输入${label.value}名称`, trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9\u4e00-\u9fa5]{2,20}$/,
      message: `${label.value}名称输入不符，请输入2-20个字符`,
      trigger: 'blur',
    },
  ],
  categoryId: [{ required: true, message: `请选择${label.value}分类`, trigger: 'change' }],
  image: [{ required: true, message: `${label.value}图片不能为空`, trigger: 'change' }],
  price: [
    {
      required: true,
      validator: (_rule, value: string, callback) => {
        if (!/^([1-9]\d{0,5}|0)(\.\d{1,2})?$/.test(value) || Number(value) <= 0)
          callback(new Error(`${label.value}价格格式有误，请输入大于零且最多保留两位小数的金额`))
        else callback()
      },
      trigger: 'blur',
    },
  ],
  setmealDishes: [
    {
      type: 'array',
      required: props.kind === 'setmeal',
      min: 1,
      message: '套餐下菜品不能为空',
      trigger: 'change',
    },
  ],
}))

async function loadForm() {
  const id = ++latestLoad
  Object.assign(form, emptyForm())
  currentCategoryName.value = ''
  loadError.value = ''
  loading.value = true
  try {
    const categoryResult = getCategoryList(props.kind === 'dish' ? 1 : 2)
    const detailResult = props.productId
      ? props.kind === 'dish'
        ? dishAPI.detail(props.productId)
        : setmealAPI.detail(props.productId)
      : Promise.resolve(null)
    const [options, detail] = await Promise.all([categoryResult, detailResult])
    if (id !== latestLoad) return
    categories.value = options
    if (detail) {
      Object.assign(form, {
        name: detail.name,
        categoryId: detail.categoryId,
        price: String(detail.price),
        image: detail.image,
        description: detail.description || '',
        status: detail.status,
      })
      currentCategoryName.value = detail.categoryName || '原分类'
      if ('flavors' in detail)
        form.flavors = detail.flavors.map((flavor) => ({
          name: flavor.name,
          values: JSON.parse(flavor.value) as string[],
        }))
      if ('setmealDishes' in detail)
        form.setmealDishes = detail.setmealDishes.map(({ dishId, name, price, copies }) => ({
          dishId,
          name,
          price,
          copies,
        }))
    }
  } catch (error) {
    if (id === latestLoad) loadError.value = (error as Error).message
  } finally {
    if (id === latestLoad) {
      loading.value = false
      await nextTick()
      formRef.value?.clearValidate()
    }
  }
}

function setDishes(dishes: SetmealDish[]) {
  form.setmealDishes = dishes
  formRef.value?.clearValidate('setmealDishes')
}

async function save(continueAdding: boolean) {
  if (loading.value || saving.value || uploading.value || loadError.value) return
  saving.value = true
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    saving.value = false
    return
  }
  if (
    props.kind === 'dish' &&
    form.flavors.some((flavor) => !flavor.name || !flavor.values.length)
  ) {
    ElMessage.error('请选择口味类型及具体口味')
    saving.value = false
    return
  }
  savingAction.value = continueAdding ? 'continue' : 'save'
  const data: ProductFields = {
    name: form.name,
    categoryId: form.categoryId!,
    price: Number(form.price),
    image: form.image,
    description: form.description,
    status: form.status,
  }
  try {
    if (props.kind === 'dish') {
      const payload = {
        ...data,
        flavors: form.flavors.map((flavor) => ({
          name: flavor.name,
          value: JSON.stringify(flavor.values),
        })),
      }
      if (props.productId) await dishAPI.update({ ...payload, id: props.productId })
      else await dishAPI.create(payload)
    } else {
      const payload = {
        ...data,
        setmealDishes: form.setmealDishes.map(({ dishId, name, price, copies }) => ({
          dishId,
          name,
          price,
          copies,
        })),
      }
      if (props.productId) await setmealAPI.update({ ...payload, id: props.productId })
      else await setmealAPI.create(payload)
    }
    ElMessage.success(`${label.value}${props.productId ? '修改' : '添加'}成功！`)
    if (continueAdding) {
      Object.assign(form, emptyForm())
      await nextTick()
      formRef.value?.clearValidate()
    } else {
      saving.value = false
      await router.push(`/${props.kind}`)
    }
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
    savingAction.value = null
  }
}
function cancel() {
  void router.push(`/${props.kind}`)
}
onBeforeRouteLeave((to) => (!saving.value && !uploading.value) || to.name === 'login')
onBeforeRouteUpdate(() => !saving.value && !uploading.value)
watch(() => [props.kind, props.productId], loadForm, { immediate: true })
watch(
  () => form.image,
  (image) => {
    if (image) formRef.value?.clearValidate('image')
  },
)
</script>

<style scoped>
.product-form-heading {
  display: flex;
  align-items: center;
  height: 44px;
  margin-bottom: 20px;
  gap: 14px;
  color: #333;
}
.product-form-heading button {
  padding: 0 14px 0 0;
  border: 0;
  border-right: 1px solid #d8dde3;
  background: none;
  color: inherit;
  font-size: 16px;
  cursor: pointer;
}
.product-form-heading button:disabled {
  opacity: 0.5;
  cursor: default;
}
.product-form-heading h1 {
  margin: 0;
  font-size: 16px;
}
.product-form-panel {
  padding: 30px;
  border-radius: 4px;
  background: white;
}
.product-form-panel .el-form {
  max-width: 1150px;
}
.product-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 20px;
}
.product-form-panel :deep(.el-form-item) {
  margin-bottom: 22px;
}
.product-form-panel :deep(.el-form-item__label) {
  height: 40px;
  line-height: 40px;
}
.product-form-panel :deep(.el-input),
.product-form-panel :deep(.el-select__wrapper) {
  min-height: 40px;
}
.product-form-panel :deep(.el-button:not(.is-link)) {
  height: 40px;
}
.product-form-panel :deep(.el-button--primary) {
  border-color: #ffc200;
  background: #ffc200;
  color: #333;
}
.product-form-panel :deep(.management-dark-button) {
  border-color: #333;
  background: #333;
  color: white;
}
.product-wide-field {
  max-width: 957px;
}
.product-image-field {
  padding-bottom: 15px;
}
.product-form-footer {
  display: flex;
  justify-content: center;
  padding-top: 30px;
  border-top: 1px solid #ebeef5;
}
.product-form-footer .el-button {
  min-width: 70px;
}
.setmeal-dishes {
  width: 100%;
}
.setmeal-dishes.has-dishes {
  padding: 20px;
  border: 1px solid #dfe2e8;
  border-radius: 3px;
  background: #fafafb;
}
.setmeal-dish-table {
  width: 100%;
  margin-top: 20px;
  border: 1px solid #ebeef5;
}
.setmeal-dish-table :deep(.el-table__cell) {
  height: 48px;
}
.setmeal-dish-table :deep(.el-input-number) {
  width: 130px;
}
.setmeal-dish-table :deep(.el-input-number__decrease),
.setmeal-dish-table :deep(.el-input-number__increase) {
  background: #fffbf0;
}
.setmeal-dish-table :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #fbe396 inset;
}
@media (max-width: 1200px) {
  .product-form-panel :deep(.el-form-item__label) {
    width: 130px !important;
  }
}
@media (max-width: 1000px) {
  .product-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
