<template>
  <article class="address-card" :class="{ selected }" :aria-label="`${label}：${address.detail}`">
    <div class="card-heading">
      <span class="address-tag">
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path
            :d="tagIcon"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
        {{ label }}
      </span>
      <span v-if="address.isDefault === 1" class="default-tag">默认</span>
    </div>
    <h2>{{ address.detail }}</h2>
    <p class="region">
      {{ [address.provinceName, address.cityName, address.districtName].filter(Boolean).join(' ') }}
    </p>
    <p class="recipient">
      <span>{{ address.consignee }}</span
      ><span>{{ address.phone }}</span>
    </p>
    <div class="card-footer">
      <ElButton
        class="delivery-button"
        type="primary"
        plain
        :aria-pressed="selected"
        :disabled="disabled"
        @click="emit('select')"
      >
        <svg v-if="selected" viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path
            d="m4 10 4 4 8-8"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
        {{ selected ? '已选中' : '配送到这里' }}
      </ElButton>
      <div class="card-actions">
        <ElButton
          v-if="address.isDefault !== 1"
          type="primary"
          link
          :disabled="disabled"
          @click="emit('default')"
          >设为默认</ElButton
        >
        <ElButton type="primary" link :disabled="disabled" @click="emit('edit')">编辑</ElButton>
        <ElButton type="danger" link :disabled="disabled" @click="emit('delete')">删除</ElButton>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElButton } from 'element-plus'
import type { Address } from '@/api/addresses'
import { addressLabel } from '@/utils/address'

const props = defineProps<{ address: Address; selected: boolean; disabled: boolean }>()
const emit = defineEmits<{ select: []; default: []; edit: []; delete: [] }>()
const label = computed(() => addressLabel(props.address.label))
const tagIcon = computed(() => {
  if (label.value === '家') return 'm3 10 9-7 9 7M5 9v12h14V9M9 21v-8h6v8'
  if (label.value === '公司') return 'M4 21V7h16v14M9 7V3h6v4M8 11h1m6 0h1m-8 4h1m6 0h1m-5 6v-3h2v3'
  if (label.value === '学校') return 'm2 8 10-5 10 5-10 5-10-5Zm4 2v7c4 3 8 3 12 0v-7m4-2v9'
  return 'M20 10c0 6-8 11-8 11S4 16 4 10a8 8 0 1 1 16 0ZM15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z'
})
</script>

<style scoped>
.address-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 24px 28px;
  border: 1px solid #e0e8f3;
  border-radius: 12px;
  background: #fff;
  transition:
    border-color 0.18s,
    box-shadow 0.18s;
}

.address-card.selected {
  border-color: var(--user-primary);
  box-shadow:
    0 0 0 1px var(--user-primary),
    0 6px 20px rgb(37 99 235 / 5%);
}

.card-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.address-tag {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #526580;
  font-size: 15px;
}

.address-tag svg {
  width: 20px;
  height: 20px;
  color: var(--user-primary);
}

.default-tag {
  padding: 3px 9px;
  border-radius: 5px;
  background: #eaf2ff;
  color: var(--user-primary);
  font-size: 12px;
  line-height: 18px;
}

h2 {
  margin: 0;
  color: #354661;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.region {
  margin: 8px 0 0;
  color: #8491a6;
  font-size: 14px;
  line-height: 22px;
  overflow-wrap: anywhere;
}

.recipient {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  margin: 16px 0 24px;
  color: #62738c;
  font-size: 14px;
  line-height: 22px;
  overflow-wrap: anywhere;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 18px 12px;
  margin-top: auto;
  padding-top: 20px;
  border-top: 1px solid #edf1f7;
}

.delivery-button {
  height: 36px;
  margin: 0;
  border-radius: 7px;
  background: #fff;
}

.selected .delivery-button {
  background: #edf4ff;
}

.delivery-button svg {
  width: 16px;
  height: 16px;
  margin-right: 6px;
}

.card-actions {
  display: flex;
  gap: 18px;
  margin-left: auto;
}

.card-actions .el-button {
  margin: 0;
  font-weight: 400;
}

@media (max-width: 600px) {
  .address-card {
    padding: 22px 20px;
  }

  .card-actions {
    gap: 16px;
  }
}
</style>
