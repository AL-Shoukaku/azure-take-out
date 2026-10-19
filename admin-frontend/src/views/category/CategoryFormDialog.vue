<template>
  <ElDialog
    v-model="visible"
    :title="title"
    width="568px"
    top="15vh"
    class="admin-dialog category-form-dialog"
    append-to-body
    :show-close="!saving"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
  >
    <ElForm
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      :disabled="saving"
      @submit.prevent="save(false)"
    >
      <ElFormItem label="分类名称：" prop="name">
        <ElInput
          v-model="form.name"
          aria-label="分类名称"
          placeholder="请输入分类名称"
          maxlength="20"
        />
      </ElFormItem>
      <ElFormItem label="排序：" prop="sort">
        <ElInput
          v-model="form.sort"
          aria-label="排序"
          placeholder="请输入排序"
          inputmode="numeric"
          @keydown.enter.prevent="save(false)"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton :disabled="saving" @click="visible = false">取消</ElButton>
      <ElButton
        type="primary"
        :class="{ 'management-dark-button': !category }"
        :loading="savingAction === 'save'"
        :disabled="saving"
        @click="save(false)"
        >确定</ElButton
      >
      <ElButton
        v-if="!category"
        type="primary"
        :loading="savingAction === 'continue'"
        :disabled="saving"
        @click="save(true)"
        >保存并继续添加</ElButton
      >
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { createCategory, updateCategory, type Category, type CategoryType } from '@/api/category'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{ type: CategoryType; category: Category | null }>()
const emit = defineEmits<{ saved: [] }>()
const formRef = ref<FormInstance>()
const form = reactive({ name: '', sort: '' })
const saving = ref(false)
const savingAction = ref<'save' | 'continue' | null>(null)
const title = computed(
  () => `${props.category ? '修改' : '新增'}${props.type === 1 ? '菜品分类' : '套餐分类'}`,
)
const rules: FormRules<typeof form> = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { min: 2, max: 20, message: '分类名称输入不符，请输入2-20个字符', trigger: 'blur' },
    { pattern: /^[A-Za-z\u4e00-\u9fa5]+$/, message: '分类名称包含特殊字符', trigger: 'blur' },
  ],
  sort: [
    { required: true, message: '排序不能为空', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (!value) return callback()
        if (!/^\d+$/.test(value)) callback(new Error('排序只能输入数字类型'))
        else if (Number(value) > 99) callback(new Error('排序只能输入0-99数字'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

watch(visible, async (open) => {
  form.name = open && props.category ? props.category.name : ''
  form.sort = open && props.category ? String(props.category.sort) : ''
  await nextTick()
  formRef.value?.clearValidate()
})

async function save(continueAdding: boolean) {
  if (saving.value) return
  saving.value = true
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    saving.value = false
    return
  }
  savingAction.value = continueAdding ? 'continue' : 'save'
  try {
    const data = { type: props.type, name: form.name, sort: Number(form.sort) }
    if (props.category) await updateCategory({ id: props.category.id, ...data })
    else await createCategory(data)
    ElMessage.success(props.category ? '分类修改成功！' : '分类添加成功！')
    emit('saved')
    if (continueAdding) {
      form.name = ''
      form.sort = ''
      await nextTick()
      formRef.value?.clearValidate()
    } else visible.value = false
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
    savingAction.value = null
  }
}
</script>

<style>
.category-form-dialog .el-dialog__body {
  padding: 34px 30px 0;
}

.category-form-dialog .el-form-item {
  margin-bottom: 22px;
}

.category-form-dialog .el-form-item__label,
.category-form-dialog .el-input {
  height: 40px;
  line-height: 40px;
}

.category-form-dialog .el-dialog__footer {
  padding-top: 20px;
}

.category-form-dialog .el-dialog__footer .management-dark-button {
  border-color: #333;
  background: #333;
  color: #fff;
}

.category-form-dialog .el-dialog__footer .management-dark-button:hover {
  border-color: #666;
  background: #666;
}
</style>
