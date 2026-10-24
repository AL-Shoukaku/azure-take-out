<template>
  <article class="product-card" :aria-label="product.name">
    <button
      class="product-photo"
      :aria-label="`查看${product.name}详情`"
      @click="$emit('detail', product)"
    >
      <DishImage :src="product.image" :name="product.name" />
      <span v-if="product.type === 2" class="setmeal-badge">精选套餐</span>
    </button>
    <div class="product-body">
      <button class="product-name" @click="$emit('detail', product)">{{ product.name }}</button>
      <p :title="product.description || product.name">{{ product.description || product.name }}</p>
      <div class="product-footer">
        <span class="product-price">{{ money(product.price) }}<small> / 份</small></span>
        <ElButton
          v-if="product.flavors.length"
          type="primary"
          plain
          :disabled="disabled"
          @click="$emit('detail', product)"
          >选规格 <span class="plus">+</span></ElButton
        >
        <ElButton
          v-else
          type="primary"
          class="add-button"
          :aria-label="`添加${product.name}`"
          :disabled="disabled"
          @click="$emit('add', product)"
          >+</ElButton
        >
      </div>
    </div>
  </article>
</template>
<script setup lang="ts">
import { ElButton } from 'element-plus'
import type { Product } from '@/api/menu'
import DishImage from '@/components/DishImage.vue'
import { money } from '@/utils/pricing'
defineProps<{ product: Product; disabled: boolean }>()
defineEmits<{ detail: [product: Product]; add: [product: Product] }>()
</script>
<style scoped>
.product-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--user-border);
  border-radius: 15px;
  background: #fff;
  transition: box-shadow 0.2s;
}
.product-card:hover {
  box-shadow: 0 10px 28px rgb(40 71 117 / 7%);
}
.product-photo {
  position: relative;
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  background: #eaf0f9;
  cursor: pointer;
}
.product-photo :deep(.dish-image) {
  width: 100%;
  height: auto;
  aspect-ratio: 1.35;
  border-radius: 0;
}
.product-photo :deep(img) {
  display: block;
  transition: transform 0.25s;
}
.product-photo:hover :deep(img) {
  transform: scale(1.035);
}
.setmeal-badge {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 5px 9px;
  border-radius: 5px;
  background: rgb(255 255 255 / 94%);
  color: #586a84;
  font-size: 11px;
}
.product-body {
  padding: 18px;
}
.product-name {
  max-width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: #354661;
  font-size: 17px;
  font-weight: 600;
  line-height: 1.5;
  text-align: left;
  cursor: pointer;
}
.product-name:hover {
  color: var(--user-primary);
}
.product-body p {
  display: -webkit-box;
  min-height: 38px;
  margin: 8px 0 20px;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  color: #8998ad;
  font-size: 12px;
  line-height: 19px;
}
.product-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.product-price {
  color: var(--user-primary);
  font-size: 25px;
  font-weight: 500;
}
small {
  color: #99a7b9;
  font-size: 11px;
  font-weight: 400;
}
.product-footer .el-button {
  margin: 0;
  border-radius: 8px;
}
.plus {
  margin-left: 8px;
  font-size: 20px;
  font-weight: 300;
}
.product-footer .add-button {
  width: 34px;
  height: 34px;
  padding: 0;
  font-size: 28px;
  font-weight: 300;
}
</style>
