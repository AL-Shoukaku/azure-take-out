<template>
  <ElDialog
    v-model="visible"
    title="确认订单"
    width="700px"
    class="user-dialog checkout-dialog"
    modal-class="user-dialog-overlay"
    align-center
    append-to-body
    destroy-on-close
    :show-close="!busy"
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
  >
    <ElForm
      id="checkout-form"
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      :disabled="busy"
      :hide-required-asterisk="true"
      @submit.prevent="confirm"
    >
      <h3 class="section-heading">送到你身边</h3>
      <ElAlert
        v-if="addresses.loadError"
        :title="addresses.loadError"
        type="error"
        :closable="false"
        ><ElButton type="primary" link @click="addresses.load">重新加载地址</ElButton></ElAlert
      >
      <ElAlert
        v-else-if="!addresses.loading && !addresses.addresses.length"
        title="请先添加收货地址"
        type="info"
        :closable="false"
      />
      <ElFormItem label="收货地址" prop="addressBookId"
        ><ElSelect
          v-model="form.addressBookId"
          placeholder="请选择收货地址"
          :loading="addresses.loading"
          aria-label="收货地址"
          ><ElOption
            v-for="address in addresses.addresses"
            :key="address.id"
            :value="address.id"
            :label="`${address.consignee} · ${address.phone} · ${fullAddress(address)}`" /></ElSelect
      ></ElFormItem>
      <RouterLink class="address-manage" to="/addresses"
        >管理 / 新增地址 <span aria-hidden="true">↗</span></RouterLink
      >
      <div class="checkout-fields">
        <ElFormItem label="配送时间" prop="deliveryStatus"
          ><ElSelect v-model="form.deliveryStatus" aria-label="配送时间"
            ><ElOption :value="1" label="立即送出" /><ElOption
              :value="0"
              label="选择送达时间" /></ElSelect></ElFormItem
        ><ElFormItem label="餐具数量" prop="tableware"
          ><ElSelect v-model="form.tableware" aria-label="餐具数量"
            ><ElOption value="auto" label="按餐量提供" /><ElOption
              :value="0"
              label="不需要餐具" /><ElOption
              v-for="n in 10"
              :key="n"
              :value="n"
              :label="`${n} 套`" /></ElSelect
        ></ElFormItem>
      </div>
      <ElFormItem v-if="form.deliveryStatus === 0" label="送达时间" prop="estimatedDeliveryTime"
        ><ElDatePicker
          v-model="form.estimatedDeliveryTime"
          type="datetime"
          placeholder="选择期望送达时间"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DD HH:mm:ss"
          :disabled-date="disabledDate"
          :clearable="false"
      /></ElFormItem>
      <section class="checkout-order">
        <h3>碧蓝外卖</h3>
        <div v-for="item in groupedItems" :key="item.id" class="checkout-item">
          <DishImage :src="item.image" :name="item.name" />
          <div class="checkout-item-info">
            <b>{{ item.name }}</b>
            <p>{{ item.dishFlavor || '默认口味' }} × {{ item.number }}</p>
          </div>
          <span>{{ money(lineTotal(item.amount, item.number)) }}</span>
        </div>
        <div class="fee-row">
          <span>打包费（¥1 / 件）</span><span>{{ money(cart.packAmount) }}</span>
        </div>
        <div class="fee-row">
          <span>配送费</span><span>{{ money(cart.deliveryAmount) }}</span>
        </div>
      </section>
      <ElFormItem label="订单备注" prop="remark"
        ><ElInput
          v-model="form.remark"
          type="textarea"
          :rows="3"
          :maxlength="100"
          show-word-limit
          placeholder="有什么想告诉我们的吗？"
      /></ElFormItem>
    </ElForm>
    <template #footer
      ><div class="checkout-footer">
        <span
          >合计 <strong>{{ money(cart.total) }}</strong></span
        ><ElButton
          form="checkout-form"
          native-type="submit"
          type="primary"
          :loading="busy"
          :disabled="
            !cart.count || !addresses.addresses.length || !!addresses.loadError || addresses.loading
          "
          >提交订单 <span aria-hidden="true">→</span></ElButton
        >
      </div></template
    >
  </ElDialog>
</template>
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { useAddressesStore } from '@/stores/addresses'
import { useCartStore } from '@/stores/cart'
import type { Address } from '@/api/addresses'
import type { OrderSubmission } from '@/api/orders'
import DishImage from '@/components/DishImage.vue'
import { lineTotal, money } from '@/utils/pricing'
import { deliveryDateTime, groupCartItems } from '../menuDisplay'
defineProps<{ busy: boolean }>()
const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ submit: [data: OrderSubmission] }>()
const addresses = useAddressesStore()
const cart = useCartStore()
const formRef = ref<FormInstance>()
const form = reactive({
  addressBookId: null as number | null,
  deliveryStatus: 1 as 0 | 1,
  estimatedDeliveryTime: '',
  tableware: 'auto' as 'auto' | number,
  remark: '',
})
const groupedItems = computed(() => groupCartItems(cart.items))
const rules: FormRules<typeof form> = {
  addressBookId: [{ required: true, message: '请选择收货地址', trigger: 'change' }],
  estimatedDeliveryTime: [
    {
      validator: (_rule, value: string, callback) => {
        callback(
          form.deliveryStatus === 0 &&
            (!value || new Date(value.replace(' ', 'T')).getTime() <= Date.now())
            ? new Error('请选择未来的送达时间')
            : undefined,
        )
      },
      trigger: 'change',
    },
  ],
}
watch(visible, (open) => {
  if (open) {
    Object.assign(form, {
      addressBookId: addresses.selectedId,
      deliveryStatus: 1,
      estimatedDeliveryTime: '',
      tableware: 'auto',
      remark: '',
    })
    formRef.value?.clearValidate()
  }
})
function fullAddress(address: Address): string {
  return [address.provinceName, address.cityName, address.districtName, address.detail].join('')
}
function disabledDate(date: Date): boolean {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}
async function confirm(): Promise<void> {
  if (!(await formRef.value?.validate().catch(() => false))) return
  addresses.select(form.addressBookId!)
  emit('submit', {
    addressBookId: form.addressBookId!,
    payMethod: 1,
    deliveryStatus: form.deliveryStatus,
    estimatedDeliveryTime:
      form.deliveryStatus === 1 ? deliveryDateTime(new Date()) : form.estimatedDeliveryTime,
    tablewareStatus: form.tableware === 'auto' ? 1 : 0,
    tablewareNumber: form.tableware === 'auto' ? 0 : form.tableware,
    remark: form.remark.trim(),
  })
}
</script>
<style scoped>
h3 {
  margin: 8px 0 22px;
  color: #465872;
  font-size: 18px;
  font-weight: 600;
}
.section-heading {
  margin-bottom: 24px;
}
.address-manage {
  display: inline-block;
  margin: -3px 0 24px;
  color: var(--user-primary);
  font-size: 13px;
  text-decoration: none;
}
.checkout-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
.checkout-fields .el-select,
:deep(.el-date-editor.el-input) {
  width: 100%;
}
.checkout-order {
  margin: 6px 0 24px;
  padding: 25px 0;
  border-top: 1px solid var(--user-border);
  border-bottom: 1px solid var(--user-border);
}
.checkout-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
}
.checkout-item :deep(.dish-image) {
  width: 50px;
  height: 50px;
}
.checkout-item-info {
  flex: 1;
  min-width: 0;
}
.checkout-item b {
  font-size: 14px;
  font-weight: 500;
}
.checkout-item p {
  margin: 6px 0 0;
  color: #94a3b8;
  font-size: 12px;
}
.checkout-item > span {
  flex-shrink: 0;
  font-size: 14px;
}
.fee-row {
  display: flex;
  justify-content: space-between;
  margin-top: 18px;
  color: #94a3b8;
  font-size: 13px;
}
.checkout-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  color: #536580;
  font-size: 14px;
}
.checkout-footer strong {
  margin-left: 8px;
  color: var(--user-primary);
  font-size: 30px;
  font-weight: 500;
}
.checkout-footer .el-button {
  gap: 20px;
}
.checkout-footer .el-button span span {
  margin-left: 20px;
  font-size: 22px;
}
@media (max-width: 600px) {
  .checkout-fields {
    grid-template-columns: 1fr;
    gap: 0;
  }
  .checkout-footer {
    gap: 10px;
  }
  .checkout-footer strong {
    font-size: 24px;
  }
  .checkout-footer .el-button span span {
    margin-left: 8px;
  }
}
</style>
