<template>
  <div class="address-page">
    <div class="page-heading">
      <div>
        <h1>收货地址</h1>
        <p>把一份热乎，送到你身边。</p>
      </div>
      <ElButton
        class="add-button"
        type="primary"
        :disabled="store.pendingId !== null"
        @click="openForm(null)"
      >
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path
            d="M10 4v12M4 10h12"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
          />
        </svg>
        新增地址
      </ElButton>
    </div>

    <ElAlert
      v-if="store.loadError"
      class="load-error"
      title="地址加载失败"
      type="error"
      :closable="false"
      show-icon
    >
      <p>{{ store.loadError }}</p>
      <ElButton :loading="store.loading" @click="store.load">重新加载</ElButton>
    </ElAlert>
    <div
      v-if="store.loading && !store.loaded"
      class="address-grid"
      aria-label="正在加载地址"
      aria-busy="true"
    >
      <ElSkeleton v-for="index in 2" :key="index" :rows="5" animated class="loading-card" />
    </div>
    <div
      v-else-if="store.addresses.length"
      class="address-grid"
      :aria-busy="store.loading || store.pendingId !== null"
    >
      <AddressCard
        v-for="address in store.addresses"
        :key="address.id"
        :address="address"
        :selected="store.selectedId === address.id"
        :disabled="store.pendingId !== null || confirmingDelete"
        @select="store.select(address.id)"
        @default="makeDefault(address.id)"
        @edit="openForm(address.id)"
        @delete="remove(address)"
      />
    </div>
    <div v-else-if="store.loaded && !store.loadError" class="empty-state">
      <div class="empty-icon" aria-hidden="true">
        <svg viewBox="0 0 48 48" fill="none">
          <path
            d="M38 20c0 11-14 23-14 23S10 31 10 20a14 14 0 1 1 28 0Z"
            stroke="currentColor"
            stroke-width="2"
          />
          <circle cx="24" cy="20" r="5" stroke="currentColor" stroke-width="2" />
        </svg>
      </div>
      <h2>还没有收货地址</h2>
      <p>点击右上角“新增地址”，让美味准时送达。</p>
    </div>

    <AddressFormDialog v-model="formOpen" :address-id="editingId" @saved="store.load" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElMessage, ElMessageBox, ElSkeleton } from 'element-plus'
import type { Address } from '@/api/addresses'
import { useAddressesStore } from '@/stores/addresses'
import { getToken } from '@/utils/auth'
import AddressFormDialog from '@/components/AddressFormDialog.vue'
import AddressCard from './components/AddressCard.vue'

const store = useAddressesStore()
const formOpen = ref(false)
const editingId = ref<number | null>(null)
const confirmingDelete = ref(false)

function openForm(id: number | null): void {
  editingId.value = id
  formOpen.value = true
}

async function makeDefault(id: number): Promise<void> {
  try {
    if (await store.makeDefault(id)) ElMessage.success('已设为默认地址并选中')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '设置失败，请稍后重试')
  }
}

async function remove(address: Address): Promise<void> {
  confirmingDelete.value = true
  const token = getToken()
  try {
    await ElMessageBox.confirm(`确定删除“${address.detail}”这个收货地址吗？`, '删除地址', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning',
      modalClass: 'user-dialog-overlay',
    })
  } catch {
    return
  } finally {
    confirmingDelete.value = false
  }
  if (token !== getToken()) return
  try {
    if (await store.remove(address.id)) ElMessage.success('地址已删除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败，请稍后重试')
  }
}

onMounted(store.load)
</script>

<style scoped>
.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 32px;
}

h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 600;
  line-height: 1.4;
}

.page-heading p {
  margin: 10px 0 0;
  color: #8491a6;
  font-size: 14px;
}

.add-button {
  flex-shrink: 0;
  height: 42px;
  padding-inline: 20px;
  border-radius: 8px;
}

.add-button svg {
  width: 18px;
  height: 18px;
  margin-right: 8px;
}

.address-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}

.loading-card,
.empty-state {
  padding: 32px;
  border: 1px solid #e0e8f3;
  border-radius: 12px;
  background: #fff;
}

.load-error {
  margin-bottom: 24px;
}

.empty-state {
  padding-block: 72px;
  text-align: center;
}

.empty-icon {
  display: grid;
  width: 80px;
  height: 80px;
  margin: 0 auto 24px;
  place-items: center;
  border-radius: 50%;
  background: #eff5ff;
  color: #7ea5ee;
}

.empty-icon svg {
  width: 42px;
  height: 42px;
}

.empty-state h2 {
  margin: 0;
  color: #526580;
  font-size: 18px;
  font-weight: 500;
}

.empty-state p {
  margin: 12px 0 0;
  color: #8491a6;
  font-size: 14px;
  line-height: 1.7;
}

@media (max-width: 1000px) {
  .address-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .page-heading {
    align-items: flex-start;
    flex-wrap: wrap;
    gap: 20px;
    margin-bottom: 24px;
  }

  .add-button {
    height: 38px;
    margin-left: auto;
    padding-inline: 14px;
  }

  .empty-state {
    padding: 48px 20px;
  }
}
</style>
