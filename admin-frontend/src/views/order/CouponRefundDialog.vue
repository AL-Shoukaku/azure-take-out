<template>
  <ElDialog
    v-model="visible"
    title="退款原因"
    class="admin-dialog coupon-refund-dialog"
    width="568px"
    top="20vh"
    append-to-body
    :show-close="!busy"
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
    @open="reset"
  >
    <p class="coupon-refund-hint">退款后购券款将退回给用户，对应的代金券立即失效。</p>
    <ElForm
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      :disabled="busy"
      @submit.prevent="submit"
    >
      <ElFormItem label="退款原因：" prop="reason"
        ><ElSelect v-model="form.reason" aria-label="退款原因选择" placeholder="请选择退款原因"
          ><ElOption
            v-for="option in refundReasons"
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
          placeholder="请填写您退款的原因（限20字内）"
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
import { nextTick, reactive, ref } from 'vue'
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
import { refundReasons } from './couponOrderDisplay'

const props = defineProps<{ busy: boolean }>()
const emit = defineEmits<{ confirm: [reason: string] }>()
const visible = defineModel<boolean>({ required: true })
const form = reactive({ reason: '', custom: '' })
const formRef = ref<FormInstance>()
const rules: FormRules = {
  reason: [{ required: true, message: '请选择退款原因', trigger: 'change' }],
  custom: [{ required: true, whitespace: true, message: '请输入退款原因', trigger: 'blur' }],
}
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
.coupon-refund-dialog .el-dialog__body {
  padding: 25px 40px 0;
}
.coupon-refund-dialog .coupon-refund-hint {
  margin: 0 0 22px;
  color: #818693;
  font-size: 14px;
}
.coupon-refund-dialog .el-select__wrapper {
  min-height: 40px;
}
.coupon-refund-dialog .el-form-item__label {
  line-height: 40px;
}
</style>
