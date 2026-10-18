<template>
  <ElDialog
    v-model="visible"
    title="营业状态设置"
    width="390px"
    top="15vh"
    class="admin-dialog shop-status-dialog"
    append-to-body
    :show-close="false"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
  >
    <ElRadioGroup
      v-model="draftStatus"
      :disabled="saving"
      aria-label="营业状态"
      class="shop-status-options"
    >
      <ElRadio :value="1" border>
        <strong>营业中</strong>
        <span class="status-description">当前餐厅处于营业状态，可点击打烊切换店铺的营业状态。</span>
      </ElRadio>
      <ElRadio :value="0" border>
        <strong>打烊中</strong>
        <span class="status-description">当前餐厅处于打烊状态，可点击营业中手动恢复营业状态。</span>
      </ElRadio>
    </ElRadioGroup>
    <template #footer>
      <ElButton :disabled="saving" @click="visible = false">取消</ElButton>
      <ElButton type="primary" :loading="saving" @click="save">确定</ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElButton, ElDialog, ElMessage, ElRadio, ElRadioGroup } from 'element-plus'
import { setShopStatus, type ShopStatus } from '@/api/shop'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{ status: ShopStatus }>()
const emit = defineEmits<{ saved: [status: ShopStatus] }>()
const draftStatus = ref<ShopStatus>(props.status)
const saving = ref(false)

watch(visible, (open) => {
  if (open) draftStatus.value = props.status
})

async function save(): Promise<void> {
  if (saving.value) return
  saving.value = true
  try {
    await setShopStatus(draftStatus.value)
    emit('saved', draftStatus.value)
    visible.value = false
    ElMessage.success('营业状态设置成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '营业状态保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.shop-status-options {
  display: grid;
  width: 100%;
  gap: 20px;
}

.shop-status-options :deep(.el-radio) {
  display: flex;
  width: 100%;
  height: auto;
  min-height: 98px;
  align-items: flex-start;
  margin: 0;
  padding: 14px 22px;
  background: #fbfbfa;
  white-space: normal;
}

.shop-status-options :deep(.el-radio__input) {
  margin-top: 3px;
}

.shop-status-options :deep(.el-radio__label) {
  padding-left: 8px;
  color: #333;
  font-size: 14px;
  line-height: 20px;
}

.shop-status-options :deep(.el-radio__input.is-checked .el-radio__inner::after) {
  background: #333;
}

.status-description {
  display: block;
  padding-top: 12px;
  color: #666;
  font-weight: 400;
  line-height: 20px;
}
</style>
