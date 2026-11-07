<template>
  <section class="order-entry-page" aria-label="订单管理">
    <div class="entry-grid">
      <RouterLink class="entry-card" to="/order/takeout">
        <span class="entry-icon" aria-hidden="true"><MenuIcon name="dish" /></span>
        <div class="entry-text">
          <h2>外卖订单</h2>
          <p>接单、派送与取消处理</p>
        </div>
      </RouterLink>
      <RouterLink class="entry-card" to="/order/coupon">
        <span class="entry-icon" aria-hidden="true"><MenuIcon name="coupon" /></span>
        <div class="entry-text">
          <h2>优惠券订单</h2>
          <p>购券记录查询与退款</p>
        </div>
      </RouterLink>
    </div>
  </section>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import MenuIcon from '@/layouts/components/MenuIcon.vue'
</script>

<style scoped>
.order-entry-page {
  display: flex;
  min-height: 100%;
}
.entry-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
  width: min(100%, 1100px);
  margin: auto;
}
.entry-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 32px;
  min-width: 0;
  min-height: clamp(280px, 52dvh, 480px);
  padding: 40px 32px;
  border: 1px solid transparent;
  border-radius: 4px;
  color: #333;
  background: var(--admin-surface);
  text-align: center;
  text-decoration: none;
  transition:
    border-color 150ms,
    box-shadow 150ms;
}
.entry-card:hover {
  border-color: var(--admin-border);
  box-shadow: 0 10px 28px rgb(37 99 235 / 10%);
}
.entry-card:focus-visible {
  outline: 2px solid var(--admin-primary);
  outline-offset: 3px;
}
.entry-icon {
  display: grid;
  width: 112px;
  height: 112px;
  flex-shrink: 0;
  place-items: center;
  border-radius: 24px;
  color: var(--admin-primary);
  background: var(--admin-surface-active);
  transition: transform 150ms;
}
.entry-card:hover .entry-icon {
  transform: translateY(-4px);
}
.entry-icon svg {
  width: 60px;
  height: 60px;
}
.entry-text {
  min-width: 0;
}
h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.5;
}
.entry-text p {
  margin: 10px 0 0;
  color: #818693;
  font-size: 14px;
  line-height: 1.6;
}
</style>
