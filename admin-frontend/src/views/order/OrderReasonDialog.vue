<template>
  <ElDialog
    v-model="visible"
    :title="`${label}原因`"
    class="admin-dialog order-reason-dialog"
    width="568px"
    top="20vh"
    append-to-body
    :show-close="!busy"
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
    @open="reset"
  >
    <ElForm
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      :disabled="busy"
      @submit.prevent="submit"
    >
      <ElFormItem :label="`${label}原因：`" prop="reason"
        ><ElSelect
          v-model="form.reason"
          :aria-label="`${label}原因选择`"
          :placeholder="`请选择${label}原因`"
          ><ElOption
            v-for="option in options"
            :key="option"
            :label="option"
            :value="option" /></ElSelect
      ></ElFormItem>
      <ElFormItem v-if="form.reason === '其他原因'" label="原因：" prop="custom"
        ><ElInput
          v-model="form.custom"
          type="textarea"
          :rows="3"
          maxlength="20"
          :placeholder="`请填写您${label}的原因（限20字内）`"
      /></ElFormItem>
    </ElForm>
    <template #footer
      ><ElButton :disabled="busy" @click="visible = false">取消</ElButton
      ><ElButton type="primary" :loading="busy" :disabled="busy" @click="submit"
        >确定</ElButton
      ></template
    >
  </ElDialog>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import type { OrderStatus } from '@/api/order'

const props = defineProps<{ mode: 'reject' | 'cancel'; status: OrderStatus; busy: boolean }>()
const emit = defineEmits<{ confirm: [reason: string] }>()
const visible = defineModel<boolean>({ required: true })
const form = reactive({ reason: '', custom: '' })
const formRef = ref<FormInstance>()
const label = computed(() => (props.mode === 'reject' ? '拒绝' : '取消'))
const cancellationReasons: Partial<Record<OrderStatus, string[]>> = {
  1: ['客户要求取消', '重复下单', '门店无法履约'],
  3: ['客户协商取消', '食材不足', '设备故障', '无法安排配送'],
  4: ['餐品损坏或遗失', '配送事故', '无法送达且已协商取消'],
  5: ['客户申请退款', '餐品质量问题', '配送服务问题', '经用户协商后退款'],
}
const options = computed(() => [
  ...(props.mode === 'reject'
    ? ['订单量较多，暂时无法接单', '菜品已销售完，暂时无法接单', '餐厅已打烊，暂时无法接单']
    : (cancellationReasons[props.status] ?? [])),
  '其他原因',
])
const rules = computed<FormRules>(() => ({
  reason: [{ required: true, message: `请选择${label.value}原因`, trigger: 'change' }],
  custom: [
    { required: true, whitespace: true, message: `请输入${label.value}原因`, trigger: 'blur' },
  ],
}))
async function reset() {
  form.reason = ''
  form.custom = ''
  await nextTick()
  formRef.value?.clearValidate()
}
async function submit() {
  if (props.busy) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid) emit('confirm', form.reason === '其他原因' ? form.custom.trim() : form.reason)
}
</script>

<style>
.order-reason-dialog .el-dialog__body {
  padding: 35px 40px 0;
}
.order-reason-dialog .el-select__wrapper {
  min-height: 40px;
}
.order-reason-dialog .el-form-item__label {
  line-height: 40px;
}
</style>
