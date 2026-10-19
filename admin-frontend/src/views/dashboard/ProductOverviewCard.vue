<template>
  <DashboardCard
    :title="`${label}总览`"
    :loading="loading"
    :error="error"
    :more="{ label: `${label}管理`, to: `/${kind}` }"
    @retry="$emit('retry')"
  >
    <div v-if="data" class="product-overview">
      <div class="product-status">
        <img :src="sellingIcon" alt="" aria-hidden="true" /><span>已启售</span
        ><strong aria-label="已启售数量">{{ data.sold }}</strong>
      </div>
      <div class="product-status">
        <img :src="stoppedIcon" alt="" aria-hidden="true" /><span>已停售</span
        ><strong aria-label="已停售数量">{{ data.discontinued }}</strong>
      </div>
      <RouterLink :to="`/${kind}/add`" class="product-add"
        ><img :src="addIcon" alt="" aria-hidden="true" /><span>新增{{ label }}</span></RouterLink
      >
    </div>
  </DashboardCard>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { ProductOverview } from '@/api/workspace'
import DashboardCard from './DashboardCard.vue'
import sellingIcon from '@/assets/images/dashboard/selling.png'
import stoppedIcon from '@/assets/images/dashboard/stopped.png'
import addIcon from '@/assets/images/dashboard/add.png'
const props = defineProps<{
  kind: 'dish' | 'setmeal'
  data: ProductOverview | undefined
  loading: boolean
  error: string
}>()
defineEmits<{ retry: [] }>()
const label = computed(() => (props.kind === 'dish' ? '菜品' : '套餐'))
</script>

<style scoped>
.product-overview {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 100px;
  gap: 20px;
}
.product-status {
  display: flex;
  min-width: 0;
  min-height: 80px;
  align-items: center;
  gap: 5px;
  padding: 20px;
  border-radius: 4px;
  background: var(--admin-surface-soft);
  color: #333;
  font-size: 14px;
}
.product-status img {
  width: 28px;
  height: 28px;
  object-fit: contain;
}
.product-status span {
  white-space: nowrap;
}
.product-status strong {
  margin-left: auto;
  font-size: 28px;
}
.product-add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 10px;
  border-radius: 4px;
  background: var(--admin-primary);
  color: var(--admin-on-primary);
  text-decoration: none;
  font-size: 14px;
}
.product-add img {
  width: 22px;
  height: 22px;
  filter: brightness(0) invert(1);
}
.product-add:hover {
  background: var(--admin-primary-hover);
}
@media (max-width: 1250px) {
  .product-overview {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 88px;
    gap: 12px;
  }
  .product-status {
    flex-wrap: wrap;
    padding: 14px;
  }
  .product-status strong {
    width: 100%;
    font-size: 24px;
  }
}
</style>
