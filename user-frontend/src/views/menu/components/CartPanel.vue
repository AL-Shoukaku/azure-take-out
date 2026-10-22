<template>
  <aside class="cart-panel" aria-label="我的餐袋" :aria-busy="cart.busy || cart.loading">
    <header class="cart-heading">
      <h2>
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path
            d="M5 7h14l2 14H3L5 7Zm3 0V5a4 4 0 0 1 8 0v2"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linejoin="round"
          /></svg
        >我的餐袋 <span>{{ cart.count }}</span>
      </h2>
      <ElButton link :disabled="!cart.count || locked" aria-label="清空餐袋" @click="$emit('clean')"
        ><svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path
            d="M4 6h16M9 3h6M6 6l1 15h10l1-15M10 10v7m4-7v7"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
          /></svg
      ></ElButton>
    </header>
    <ElAlert v-if="cart.error" :title="cart.error" type="error" :closable="false"
      ><ElButton link type="primary" @click="$emit('retry')">重新加载购物车</ElButton></ElAlert
    >
    <ElSkeleton v-else-if="cart.loading && !cart.count" :rows="5" animated class="cart-skeleton" />
    <div v-else-if="!cart.count" class="empty-cart">
      <span class="empty-icon"
        ><svg viewBox="0 0 64 64" fill="none" aria-hidden="true">
          <path
            d="m17 22 31-4 8 36-39 5V22Zm10-1v-8a8 8 0 0 1 16 0v6"
            stroke="currentColor"
            stroke-width="2.5"
            stroke-linejoin="round"
          />
          <path
            d="M29 35c-5-7-12 4 7 12 14-13 3-20-1-11"
            stroke="currentColor"
            stroke-width="1.5"
          /></svg
      ></span>
      <h3>餐袋还在等一份心动</h3>
      <p>选一点喜欢的，<br />把今天照顾好。</p>
    </div>
    <ul v-else class="cart-items">
      <li v-for="item in groupedItems" :key="item.id" class="cart-item">
        <DishImage :src="item.image" :name="item.name" />
        <div class="cart-item-info">
          <b>{{ item.name }}</b>
          <p>{{ item.dishFlavor || '默认口味' }}</p>
          <div class="cart-item-bottom">
            <span>{{ money(lineTotal(item.amount, item.number)) }}</span>
            <div class="quantity">
              <ElButton
                :disabled="locked"
                :aria-label="`减少${item.name}`"
                @click="$emit('subtract', item)"
                >−</ElButton
              ><span :aria-label="`${item.name}数量`">{{ item.number }}</span
              ><ElButton
                :disabled="locked || closed"
                :aria-label="`增加${item.name}`"
                @click="$emit('add', item)"
                >+</ElButton
              >
            </div>
          </div>
        </div>
      </li>
    </ul>
    <div class="cart-summary">
      <div>
        <span>餐品小计</span><b>{{ money(cart.amount) }}</b>
      </div>
      <div>
        <span>打包费</span><span>{{ money(cart.packAmount) }}</span>
      </div>
      <div>
        <span>配送费</span><span>{{ money(cart.deliveryAmount) }}</span>
      </div>
    </div>
    <div class="cart-total">
      <span>合计</span><strong>{{ money(cart.total) }}</strong>
    </div>
    <ElButton
      class="checkout-button"
      type="primary"
      :disabled="!cart.count || locked || closed"
      :loading="checking"
      @click="$emit('checkout')"
      >去结算 <span aria-hidden="true">→</span></ElButton
    >
    <p class="cart-note">一点烟火气，温柔一整天。</p>
  </aside>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { ElAlert, ElButton, ElSkeleton } from 'element-plus'
import { useCartStore } from '@/stores/cart'
import type { CartItem } from '@/api/cart'
import DishImage from '@/components/DishImage.vue'
import { money, lineTotal } from '@/utils/pricing'
import { groupCartItems } from '../menuDisplay'
const props = defineProps<{ closed: boolean; checking: boolean }>()
defineEmits<{
  add: [item: CartItem]
  subtract: [item: CartItem]
  clean: []
  retry: []
  checkout: []
}>()
const cart = useCartStore()
const groupedItems = computed(() => groupCartItems(cart.items))
const locked = computed(() => cart.busy || cart.loading || !!cart.error || props.checking)
</script>
<style scoped>
.cart-panel {
  position: sticky;
  top: 108px;
  display: flex;
  flex-direction: column;
  min-width: 0;
  max-height: calc(100dvh - 128px);
  padding: 22px;
  border: 1px solid var(--user-border);
  border-radius: 16px;
  background: #fff;
}
.cart-heading {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 20px;
  border-bottom: 1px solid #e6edf7;
}
h2 {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  color: #354661;
  font-size: 17px;
}
h2 svg {
  width: 23px;
  height: 23px;
  color: var(--user-primary);
}
h2 span {
  padding: 3px 7px;
  border-radius: 4px;
  background: #edf3ff;
  color: var(--user-primary);
  font-size: 11px;
  font-weight: 400;
}
.cart-heading .el-button svg {
  width: 17px;
  height: 17px;
}
.cart-heading .el-button {
  color: #93a2b8;
}
.empty-cart {
  flex: 1;
  padding: 32px 0 28px;
  text-align: center;
  color: #9eafc6;
}
.empty-icon {
  display: grid;
  width: 86px;
  height: 86px;
  margin: 0 auto 20px;
  place-items: center;
  border-radius: 50%;
  background: #f0f5ff;
  color: #adc3e9;
}
.empty-icon svg {
  width: 43px;
  height: 43px;
}
h3 {
  margin: 0 0 12px;
  font-size: 13px;
  font-weight: 500;
}
.empty-cart p {
  margin: 0;
  font-size: 12px;
  line-height: 2;
}
.cart-items {
  min-height: 75px;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  list-style: none;
}
.cart-item {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding: 18px 0;
  border-bottom: 1px dashed #e5ecf7;
}
.cart-item :deep(.dish-image) {
  width: 56px;
  height: 56px;
  border-radius: 7px;
}
.cart-item-info {
  flex: 1;
  min-width: 0;
}
.cart-item-info b {
  display: block;
  color: #465872;
  font-size: 13px;
  font-weight: 500;
  overflow-wrap: anywhere;
}
.cart-item-info p {
  margin: 7px 0 10px;
  color: #95a3b8;
  font-size: 11px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.cart-item-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--user-primary);
  font-size: 13px;
}
.quantity {
  display: flex;
  align-items: center;
  gap: 9px;
  color: #536580;
}
.quantity .el-button {
  width: 22px;
  height: 22px;
  margin: 0;
  padding: 0;
  border-color: #dce6f7;
  border-radius: 4px;
  color: var(--user-primary);
  font-size: 17px;
}
.cart-summary {
  display: grid;
  flex-shrink: 0;
  gap: 15px;
  padding: 22px 0;
  border-bottom: 1px dashed #e5ecf7;
  color: #93a2b8;
  font-size: 12px;
}
.cart-summary div,
.cart-total {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.cart-summary b {
  color: #536580;
  font-weight: 400;
}
.cart-total {
  flex-shrink: 0;
  padding: 20px 0;
  font-size: 14px;
}
.cart-total strong {
  color: var(--user-primary);
  font-size: 28px;
  font-weight: 500;
}
.checkout-button {
  display: flex;
  flex-shrink: 0;
  justify-content: space-between;
  width: 100%;
  height: 44px;
  border-radius: 8px;
}
.checkout-button :deep(span) {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
}
.checkout-button span span {
  flex: initial;
  font-size: 23px;
}
.cart-note {
  flex-shrink: 0;
  margin: 18px 0 0;
  color: #b1bdce;
  font-size: 10px;
  text-align: center;
}
.cart-skeleton {
  margin-top: 24px;
}
@media (max-width: 900px) {
  .cart-panel {
    position: static;
    max-height: none;
  }
  .cart-items {
    max-height: 320px;
  }
}
@media (max-height: 720px) and (min-width: 901px) {
  .empty-cart {
    padding: 15px 0;
  }
  .empty-icon {
    width: 50px;
    height: 50px;
    margin-bottom: 10px;
  }
  .empty-icon svg {
    width: 30px;
    height: 30px;
  }
  .cart-note {
    display: none;
  }
  .cart-summary {
    padding-block: 14px;
    gap: 9px;
  }
  .cart-total {
    padding-block: 14px;
  }
}
</style>
