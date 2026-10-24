<template>
  <ElDialog
    v-model="visible"
    :title="addressId === null ? '新增地址' : '编辑地址'"
    width="660px"
    class="user-dialog address-dialog"
    modal-class="user-dialog-overlay"
    align-center
    append-to-body
    destroy-on-close
    :show-close="!saving"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
  >
    <ElSkeleton v-if="loading" :rows="6" animated />
    <ElAlert v-else-if="loadError" title="地址加载失败" type="error" :closable="false" show-icon>
      <p>{{ loadError }}</p>
      <ElButton @click="loadDetail">重新加载</ElButton>
    </ElAlert>
    <ElForm
      v-else
      id="address-form"
      ref="addressForm"
      :model="form"
      :rules="rules"
      label-position="top"
      :hide-required-asterisk="true"
      :disabled="saving"
      :aria-busy="saving"
      @submit.prevent="save"
    >
      <div class="contact-fields">
        <ElFormItem label="收货人" prop="consignee">
          <ElInput
            v-model="form.consignee"
            placeholder="请输入收货人姓名"
            :maxlength="50"
            autocomplete="name"
          />
        </ElFormItem>
        <ElFormItem label="手机号" prop="phone">
          <ElInput
            v-model="form.phone"
            placeholder="请输入手机号"
            :maxlength="11"
            inputmode="tel"
            autocomplete="tel"
          />
        </ElFormItem>
      </div>
      <div class="region-fields">
        <ElFormItem label="省份" prop="provinceName">
          <ElInput
            v-model="form.provinceName"
            placeholder="如：浙江省"
            :maxlength="32"
            autocomplete="address-level1"
          />
        </ElFormItem>
        <ElFormItem label="城市" prop="cityName">
          <ElInput
            v-model="form.cityName"
            placeholder="如：杭州市"
            :maxlength="32"
            autocomplete="address-level2"
          />
        </ElFormItem>
        <ElFormItem label="区县" prop="districtName">
          <ElInput
            v-model="form.districtName"
            placeholder="如：西湖区"
            :maxlength="32"
            autocomplete="address-level3"
          />
        </ElFormItem>
      </div>
      <ElFormItem label="详细地址" prop="detail">
        <ElInput
          v-model="form.detail"
          placeholder="街道、楼牌号、小区、楼栋及门牌号"
          :maxlength="200"
          autocomplete="street-address"
        />
      </ElFormItem>
      <ElFormItem label="地址标签" prop="label">
        <ElRadioGroup v-model="form.label" aria-label="地址标签" class="address-labels">
          <ElRadioButton v-for="label in addressLabels" :key="label" :value="label">{{
            label
          }}</ElRadioButton>
        </ElRadioGroup>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton :disabled="saving" @click="visible = false">取消</ElButton>
      <ElButton
        type="primary"
        native-type="submit"
        form="address-form"
        :loading="saving"
        :disabled="loading || !!loadError"
      >
        保存地址
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { nextTick, onScopeDispose, reactive, ref, useTemplateRef, watch } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElRadioButton,
  ElRadioGroup,
  ElSkeleton,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import {
  createAddress,
  getAddress,
  updateAddress,
  type Address,
  type AddressFormData,
} from '@/api/addresses'
import { addressLabel, addressLabels } from '@/utils/address'
import { getToken } from '@/utils/auth'

const props = withDefaults(defineProps<{ addressId?: number | null }>(), { addressId: null })
const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ saved: [] }>()
const addressForm = useTemplateRef<FormInstance>('addressForm')
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const original = ref<Address | null>(null)
let revision = 0

function emptyForm(): AddressFormData {
  return {
    consignee: '',
    phone: '',
    provinceCode: '',
    provinceName: '',
    cityCode: '',
    cityName: '',
    districtCode: '',
    districtName: '',
    detail: '',
    label: '家',
  }
}
const form = reactive<AddressFormData>(emptyForm())
const required = (message: string) => ({
  required: true,
  whitespace: true,
  message,
  trigger: 'blur',
})
const rules: FormRules<AddressFormData> = {
  consignee: [required('请输入收货人')],
  phone: [
    required('请输入手机号'),
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' },
  ],
  provinceName: [required('请输入省份')],
  cityName: [required('请输入城市')],
  districtName: [required('请输入区县')],
  detail: [required('请输入详细地址')],
  label: [required('请选择地址标签')],
}

watch(
  [visible, () => props.addressId],
  async ([open]) => {
    revision += 1
    if (!open) return
    Object.assign(form, emptyForm())
    original.value = null
    loadError.value = ''
    if (props.addressId !== null) await loadDetail()
    else loading.value = false
    await nextTick()
    addressForm.value?.clearValidate()
  },
  { immediate: true, flush: 'sync' },
)
onScopeDispose(() => {
  revision += 1
})

async function loadDetail(): Promise<void> {
  if (props.addressId === null) return
  const current = ++revision
  const token = getToken()
  loading.value = true
  loadError.value = ''
  try {
    const address = await getAddress(props.addressId)
    if (current !== revision || token !== getToken()) return
    original.value = address
    Object.assign(form, {
      consignee: address.consignee,
      phone: address.phone,
      provinceName: address.provinceName || '',
      cityName: address.cityName || '',
      districtName: address.districtName || '',
      detail: address.detail,
      label: addressLabel(address.label),
    })
  } catch (error) {
    if (current === revision && token === getToken()) {
      loadError.value = error instanceof Error ? error.message : '请稍后重试'
    }
  } finally {
    if (current === revision) loading.value = false
  }
}

async function save(): Promise<void> {
  if (saving.value || loading.value || loadError.value) return
  const current = revision
  const token = getToken()
  saving.value = true
  try {
    const valid = await addressForm.value?.validate().catch(() => false)
    if (!valid || current !== revision || token !== getToken()) return
    const data: AddressFormData = {
      ...form,
      consignee: form.consignee.trim(),
      phone: form.phone.trim(),
      provinceName: form.provinceName.trim(),
      cityName: form.cityName.trim(),
      districtName: form.districtName.trim(),
      detail: form.detail.trim(),
    }
    // 地区允许手工输入；仅沿用与地区名称仍匹配的旧编码。
    const sameProvince = original.value?.provinceName === data.provinceName
    const sameCity = sameProvince && original.value?.cityName === data.cityName
    data.provinceCode = sameProvince ? original.value?.provinceCode || '' : ''
    data.cityCode = sameCity ? original.value?.cityCode || '' : ''
    data.districtCode =
      sameCity && original.value?.districtName === data.districtName
        ? original.value?.districtCode || ''
        : ''
    if (props.addressId === null) await createAddress(data)
    else await updateAddress({ ...data, id: props.addressId })
    if (current !== revision || token !== getToken()) return
    emit('saved')
    visible.value = false
    ElMessage.success('地址保存成功')
  } catch (error) {
    if (current === revision && token === getToken()) {
      ElMessage.error(error instanceof Error ? error.message : '地址保存失败，请稍后重试')
    }
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.contact-fields,
.region-fields {
  display: grid;
  gap: 0 20px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.region-fields {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.address-labels {
  display: flex;
  gap: 12px;
}

.address-labels :deep(.el-radio-button__inner) {
  min-width: 72px;
  padding: 10px 18px;
  border: 1px solid var(--user-border);
  border-radius: 7px;
  box-shadow: none;
}

.address-labels :deep(.is-active .el-radio-button__inner) {
  border-color: var(--user-primary);
  background: #edf4ff;
  color: var(--user-primary);
}

@media (max-width: 600px) {
  .contact-fields,
  .region-fields {
    grid-template-columns: 1fr;
  }

  .address-labels {
    flex-wrap: wrap;
    gap: 10px;
  }

  .address-labels :deep(.el-radio-button__inner) {
    min-width: 62px;
    padding-inline: 14px;
  }
}
</style>
