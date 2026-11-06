<template>
  <section class="coupon-form-page">
    <header class="coupon-form-heading">
      <button type="button" :disabled="saving" @click="cancel">← 返回</button>
      <h1>{{ couponId ? '修改优惠券' : '新建优惠券' }}</h1>
    </header>
    <div v-loading="loading" class="coupon-form-panel">
      <div v-if="loadError" class="management-load-error" role="alert">
        {{ loadError }} <ElButton @click="loadForm">重试</ElButton>
      </div>
      <template v-else-if="!loading">
        <p class="coupon-form-note">
          {{
            couponId
              ? '发布后仅支持修改优惠券名称和代金券购买价格，其余信息仅供查看。'
              : '新建优惠券默认为停售，保存后可在列表中起售。'
          }}
        </p>
        <ElForm
          id="coupon-form"
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="150px"
          :disabled="loading || saving"
          novalidate
          @submit.prevent="save"
        >
          <div class="coupon-form-grid">
            <ElFormItem label="优惠券名称:" prop="name">
              <ElInput v-model="form.name" placeholder="请填写优惠券名称" />
            </ElFormItem>
            <ElFormItem label="优惠券类型:" prop="type">
              <ElSelect
                v-model="form.type"
                aria-label="优惠券类型"
                :disabled="saving || !!couponId"
              >
                <ElOption label="代金券" :value="1" /><ElOption label="满减券" :value="2" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="总库存:" prop="totalStock">
              <ElInputNumber
                v-model="form.totalStock"
                aria-label="总库存"
                :min="1"
                :max="2147483647"
                :precision="0"
                :disabled="saving || !!couponId"
                controls-position="right"
              />
            </ElFormItem>
            <ElFormItem label="抵扣金额(元):" prop="discountAmount">
              <ElInputNumber
                v-model="form.discountAmount"
                aria-label="抵扣金额"
                :min="0.01"
                :step="0.01"
                :precision="2"
                :disabled="saving || !!couponId"
                controls-position="right"
              />
            </ElFormItem>
            <ElFormItem label="门槛金额(元):" prop="thresholdAmount">
              <ElInputNumber
                :key="form.type"
                v-model="form.thresholdAmount"
                aria-label="门槛金额"
                :min="0"
                :step="0.01"
                :precision="2"
                :disabled="saving || !!couponId || form.type !== 2"
                controls-position="right"
              />
            </ElFormItem>
            <ElFormItem label="购买价格(元):" prop="price">
              <ElInputNumber
                :key="form.type"
                v-model="form.price"
                aria-label="购买价格"
                :min="0"
                :step="0.01"
                :precision="2"
                :disabled="saving || form.type !== 1"
                controls-position="right"
              />
            </ElFormItem>
            <ElFormItem label="有效期(天):" prop="validTime">
              <ElInputNumber
                v-model="form.validTime"
                aria-label="有效期"
                :min="1"
                :max="2147483647"
                :precision="0"
                :disabled="saving || !!couponId"
                controls-position="right"
              />
            </ElFormItem>
            <ElFormItem label="活动开始时间:" prop="startTime" @keydown.enter.prevent>
              <ElDatePicker
                v-model="form.startTime"
                aria-label="活动开始时间"
                type="datetime"
                format="YYYY-MM-DD HH:mm"
                value-format="YYYY-MM-DD HH:mm"
                placeholder="请选择活动开始时间"
                :disabled="saving || !!couponId"
              />
            </ElFormItem>
            <ElFormItem label="活动结束时间:" prop="endTime" @keydown.enter.prevent>
              <ElDatePicker
                v-model="form.endTime"
                aria-label="活动结束时间"
                type="datetime"
                format="YYYY-MM-DD HH:mm"
                value-format="YYYY-MM-DD HH:mm"
                placeholder="请选择活动结束时间"
                :disabled="saving || !!couponId"
              />
            </ElFormItem>
          </div>
        </ElForm>
        <ElDescriptions v-if="loadedCoupon" :column="2" border class="coupon-form-meta">
          <ElDescriptionsItem label="优惠券ID">{{ loadedCoupon.id }}</ElDescriptionsItem>
          <ElDescriptionsItem label="剩余库存">{{ loadedCoupon.stock }}</ElDescriptionsItem>
          <ElDescriptionsItem label="起售状态">{{
            loadedCoupon.status === 1 ? '起售' : '停售'
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="创建时间">{{
            loadedCoupon.createTime?.replace('T', ' ') || '—'
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="更新时间">{{
            loadedCoupon.updateTime?.replace('T', ' ') || '—'
          }}</ElDescriptionsItem>
        </ElDescriptions>
      </template>
      <div class="coupon-form-footer">
        <ElButton :disabled="saving" @click="cancel">取消</ElButton>
        <ElButton
          type="primary"
          :loading="saving"
          :disabled="loading || saving || !!loadError"
          native-type="submit"
          form="coupon-form"
          >保存</ElButton
        >
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRouter } from 'vue-router'
import {
  ElButton,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  vLoading,
  type FormInstance,
  type FormItemRule,
  type FormRules,
} from 'element-plus'
import { couponAPI, type Coupon, type CouponPublishDTO, type CouponType } from '@/api/coupon'
import '@/assets/styles/management.css'

const props = defineProps<{ couponId?: number }>()
const router = useRouter()
interface CouponForm {
  name: string
  type: CouponType
  totalStock?: number
  discountAmount?: number
  thresholdAmount?: number
  price?: number
  validTime?: number
  startTime: string
  endTime: string
}
const emptyForm = (): CouponForm => ({
  name: '',
  type: 1,
  totalStock: undefined,
  discountAmount: undefined,
  thresholdAmount: 0,
  price: 0,
  validTime: undefined,
  startTime: '',
  endTime: '',
})
const form = reactive<CouponForm>(emptyForm())
const formRef = ref<FormInstance>()
const loadedCoupon = ref<Coupon | null>(null)
const loading = ref(false),
  saving = ref(false),
  loadError = ref('')
let latestLoad = 0

function numberRule(label: string, positive: boolean, integer = false): FormItemRule {
  return {
    required: true,
    validator: (_rule, value: unknown, callback) => {
      if (
        typeof value !== 'number' ||
        !Number.isFinite(value) ||
        (positive ? value <= 0 : value < 0) ||
        (integer && (!Number.isInteger(value) || value > 2147483647))
      ) {
        callback(
          new Error(
            `${label}必须为${positive ? '大于' : '大于等于'} 0 的${integer ? '整数' : '金额'}`,
          ),
        )
      } else callback()
    },
    trigger: ['blur', 'change'],
  }
}
const rules = computed<FormRules<CouponForm>>(() => ({
  name: [{ required: true, whitespace: true, message: '请输入优惠券名称', trigger: 'blur' }],
  ...(form.type === 1 ? { price: [numberRule('购买价格', false)] } : {}),
  ...(!props.couponId
    ? ({
        type: [{ required: true, message: '请选择优惠券类型', trigger: 'change' }],
        totalStock: [numberRule('总库存', true, true)],
        discountAmount: [numberRule('抵扣金额', true)],
        validTime: [numberRule('有效期', true, true)],
        ...(form.type === 2 ? { thresholdAmount: [numberRule('门槛金额', false)] } : {}),
        startTime: [{ required: true, message: '请选择活动开始时间', trigger: 'change' }],
        endTime: [
          {
            required: true,
            validator: (_rule, value: string, callback) => {
              if (!value) callback(new Error('请选择活动结束时间'))
              else if (
                !form.startTime ||
                new Date(value.replace(' ', 'T')).getTime() <=
                  new Date(form.startTime.replace(' ', 'T')).getTime()
              )
                callback(new Error('活动结束时间必须晚于开始时间'))
              else callback()
            },
            trigger: 'change',
          },
        ],
      } satisfies FormRules<CouponForm>)
    : {}),
}))

async function loadForm() {
  const requestId = ++latestLoad
  loading.value = true
  loadError.value = ''
  loadedCoupon.value = null
  Object.assign(form, emptyForm())
  try {
    if (props.couponId) {
      const coupon = await couponAPI.detail(props.couponId)
      if (requestId !== latestLoad) return
      loadedCoupon.value = coupon
      Object.assign(form, {
        name: coupon.name,
        type: coupon.type,
        totalStock: coupon.totalStock,
        discountAmount: coupon.discountAmount,
        thresholdAmount: coupon.thresholdAmount,
        price: coupon.price,
        validTime: coupon.validTime,
        startTime: coupon.startTime?.replace('T', ' '),
        endTime: coupon.endTime?.replace('T', ' '),
      })
    }
  } catch (error) {
    if (requestId === latestLoad) loadError.value = (error as Error).message
  } finally {
    if (requestId === latestLoad) {
      loading.value = false
      await nextTick()
      formRef.value?.clearValidate()
    }
  }
}

async function save() {
  if (loading.value || saving.value || loadError.value) return
  saving.value = true
  try {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    if (props.couponId) {
      await couponAPI.update({
        id: props.couponId,
        name: form.name.trim(),
        ...(form.type === 1 ? { price: form.price! } : {}),
      })
    } else {
      const data: CouponPublishDTO = {
        name: form.name.trim(),
        type: form.type,
        totalStock: form.totalStock!,
        discountAmount: form.discountAmount!,
        validTime: form.validTime!,
        thresholdAmount: form.type === 2 ? form.thresholdAmount! : 0,
        price: form.type === 1 ? form.price! : 0,
        startTime: form.startTime,
        endTime: form.endTime,
      }
      await couponAPI.create(data)
    }
    ElMessage.success(`优惠券${props.couponId ? '修改' : '新建'}成功！`)
    saving.value = false
    await router.push('/coupon')
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
  }
}
function cancel() {
  void router.push('/coupon')
}
onBeforeRouteLeave((to) => !saving.value || to.name === 'login')
onBeforeRouteUpdate(() => !saving.value)
watch(() => props.couponId, loadForm, { immediate: true })
watch(
  () => form.type,
  (type) => {
    if (props.couponId || loading.value) return
    if (type === 1) form.thresholdAmount = 0
    else form.price = 0
    formRef.value?.clearValidate(['price', 'thresholdAmount'])
  },
)
watch(
  () => form.startTime,
  () => {
    if (!props.couponId && !loading.value && form.endTime)
      void formRef.value?.validateField('endTime').catch(() => {})
  },
)
</script>

<style scoped>
.coupon-form-heading {
  display: flex;
  align-items: center;
  height: 44px;
  margin-bottom: 20px;
  gap: 14px;
  color: #333;
}
.coupon-form-heading button {
  padding: 0 14px 0 0;
  border: 0;
  border-right: 1px solid #d8dde3;
  background: none;
  color: inherit;
  font-size: 16px;
  cursor: pointer;
}
.coupon-form-heading button:disabled {
  opacity: 0.5;
  cursor: default;
}
.coupon-form-heading h1 {
  margin: 0;
  font-size: 16px;
}
.coupon-form-panel {
  min-height: 180px;
  padding: 30px;
  border-radius: 4px;
  background: var(--admin-surface);
}
.coupon-form-note {
  margin: 0 0 28px;
  color: #818693;
  font-size: 14px;
}
.coupon-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  max-width: 1150px;
  gap: 0 28px;
}
.coupon-form-panel :deep(.el-form-item) {
  margin-bottom: 24px;
}
.coupon-form-panel :deep(.el-form-item__label) {
  height: 40px;
  line-height: 40px;
}
.coupon-form-panel :deep(.el-input),
.coupon-form-panel :deep(.el-select__wrapper) {
  min-height: 40px;
}
.coupon-form-panel :deep(.el-input-number),
.coupon-form-panel :deep(.el-date-editor),
.coupon-form-panel :deep(.el-select) {
  width: 100%;
  min-width: 0;
}
.coupon-form-panel :deep(.el-input-number .el-input__inner) {
  text-align: left;
}
.coupon-form-panel :deep(.el-button:not(.is-link)) {
  height: 40px;
}
.coupon-form-panel :deep(.el-button--primary) {
  border-color: var(--admin-primary);
  background: var(--admin-primary);
  color: var(--admin-on-primary);
}
.coupon-form-meta {
  margin: 6px 0 24px;
}
.coupon-form-meta :deep(.el-descriptions__cell) {
  overflow-wrap: anywhere;
}
.coupon-form-footer {
  display: flex;
  justify-content: center;
  padding-top: 30px;
  border-top: 1px solid #ebeef5;
}
.coupon-form-footer .el-button {
  min-width: 70px;
}
@media (max-width: 1000px) {
  .coupon-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
