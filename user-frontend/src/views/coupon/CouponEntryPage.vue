<template>
  <div class="order-entry-page">
    <div class="page-heading">
      <h1>优惠券</h1>
      <p>抢券与我的优惠券，都在这里。</p>
    </div>
    <div class="entry-grid">
      <RouterLink class="entry-card" to="/coupons/take">
        <span class="entry-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none">
            <path
              d="M4 5h16v5a2 2 0 0 0 0 4v5H4v-5a2 2 0 0 0 0-4V5Zm11 0v3m0 3v2m0 3v3"
              stroke="currentColor"
              stroke-width="1.3"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </span>
        <div class="entry-text">
          <h2>抢优惠券</h2>
          <p>领取满减券，购买代金券</p>
        </div>
      </RouterLink>
      <RouterLink class="entry-card" to="/coupons/mine">
        <span class="entry-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none">
            <path
              d="M4 5h16v5a2 2 0 0 0 0 4v5H4v-5a2 2 0 0 0 0-4V5Zm4 5h8M8 14h5"
              stroke="currentColor"
              stroke-width="1.3"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </span>
        <div class="entry-text">
          <h2>我的优惠券</h2>
          <p>查看持有的优惠券与状态</p>
        </div>
      </RouterLink>
    </div>
  </div>
</template>
<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>
<style scoped>
.order-entry-page {
  display: flex;
  flex-direction: column;
  min-height: calc(100dvh - 208px);
}
.page-heading {
  margin-bottom: 28px;
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
.entry-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 32px;
  width: min(100%, 1200px);
  margin: auto;
}
.entry-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 36px;
  min-width: 0;
  min-height: clamp(320px, 48dvh, 520px);
  padding: 48px 40px;
  border: 1px solid #e2e9f4;
  border-radius: 20px;
  color: inherit;
  background: #fff;
  text-align: center;
  text-decoration: none;
  transition:
    border-color 150ms,
    box-shadow 150ms;
}
.entry-card:hover {
  border-color: #bfd2fb;
  box-shadow: 0 10px 28px rgb(40 71 117 / 7%);
}
.entry-card:focus-visible {
  outline: 2px solid var(--user-primary);
  outline-offset: 3px;
}
.entry-icon {
  display: grid;
  width: 128px;
  height: 128px;
  flex-shrink: 0;
  place-items: center;
  border-radius: 32px;
  color: var(--user-primary);
  background: #eff6ff;
  transition: transform 150ms;
}
.entry-card:hover .entry-icon {
  transform: translateY(-4px);
}
.entry-icon svg {
  width: 68px;
  height: 68px;
}
.entry-text {
  min-width: 0;
}
h2 {
  margin: 0;
  font-size: 26px;
  font-weight: 600;
  line-height: 1.5;
}
.entry-text p {
  margin: 12px 0 0;
  color: #8491a6;
  font-size: 15px;
  line-height: 1.6;
}
@media (max-width: 700px) {
  .entry-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .entry-card {
    gap: 20px;
    min-height: 220px;
    padding: 32px 24px;
  }
  .entry-icon {
    width: 76px;
    height: 76px;
    border-radius: 20px;
  }
  .entry-icon svg {
    width: 40px;
    height: 40px;
  }
  h2 {
    font-size: 20px;
  }
}
</style>
