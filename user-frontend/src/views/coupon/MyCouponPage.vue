<template>
  <div class="orders-page">
    <div class="page-heading">
      <div>
        <h1>我的优惠券</h1>
        <p>每一张券，都是实打实的优惠。</p>
      </div>
      <div class="heading-actions">
        <ElButton type="primary" @click="router.push('/coupons/take')">去抢优惠券</ElButton>
        <ElButton class="refresh-button" :loading="loading" @click="load">刷新优惠券</ElButton>
      </div>
    </div>
    <ElTabs class="order-tabs" :model-value="tab" @tab-change="selectTab($event as UserCouponTab)">
      <ElTabPane
        v-for="option in userCouponTabs"
        :key="option.key"
        :label="option.label"
        :name="option.key"
      />
    </ElTabs>

    <div v-if="loading" class="order-grid" aria-label="正在加载优惠券" aria-busy="true">
      <ElSkeleton v-for="index in 6" :key="index" :rows="5" class="loading-card" animated />
    </div>
    <ElAlert v-else-if="error" title="优惠券加载失败" type="error" :closable="false" show-icon>
      <p>{{ error }}</p>
      <ElButton @click="load">重新加载</ElButton>
    </ElAlert>
    <div v-else-if="records.length" class="order-grid">
      <MyCouponCard
        v-for="coupon in records"
        :key="coupon.id"
        :coupon="coupon"
        :busy="detailLoading"
        @detail="openDetail(coupon.id)"
      />
    </div>
    <div v-else class="empty-state">
      <svg viewBox="0 0 48 48" fill="none" aria-hidden="true">
        <path
          d="M8 12h32v9a4 4 0 0 0 0 8v9H8v-9a4 4 0 0 0 0-8v-9Zm22 0v5m0 6v4m0 6v5"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
      <h2>还没有相关优惠券</h2>
      <p>去抢一张，下单时即可抵扣。</p>
    </div>

    <nav
      v-if="total > userCouponPageSize && !loading && !error"
      class="order-pagination"
      aria-label="优惠券分页"
    >
      <ElConfigProvider :locale="zhCn">
        <ElPagination
          v-model:current-page="page"
          :page-size="userCouponPageSize"
          :total="total"
          :pager-count="5"
          layout="total, prev, pager, next, jumper"
        />
      </ElConfigProvider>
    </nav>

    <MyCouponDetailDialog
      v-model="detailOpen"
      :coupon="detail"
      :loading="detailLoading"
      :error="detailError"
      @retry="loadDetail"
    />
  </div>
</template>
<script setup lang="ts">
import { useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElConfigProvider,
  ElPagination,
  ElSkeleton,
  ElTabPane,
  ElTabs,
} from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import MyCouponCard from './components/MyCouponCard.vue'
import MyCouponDetailDialog from './components/MyCouponDetailDialog.vue'
import { userCouponPageSize, userCouponTabs, type UserCouponTab } from './myCouponDisplay'
import { useUserCoupons } from './useUserCoupons'

const router = useRouter()
const {
  tab,
  page,
  records,
  total,
  loading,
  error,
  load,
  selectTab,
  detailOpen,
  detail,
  detailLoading,
  detailError,
  openDetail,
  loadDetail,
} = useUserCoupons()
</script>
<style scoped>
.page-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
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
.heading-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
}
.heading-actions .el-button {
  height: 42px;
  margin: 0;
  padding-inline: 20px;
  border-radius: 8px;
}
.refresh-button {
  color: #526580;
}
.order-tabs {
  margin-bottom: 24px;
}
.order-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}
.order-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: #e0e8f3;
}
.order-tabs :deep(.el-tabs__item) {
  height: 54px;
  color: #8491a6;
  font-weight: 400;
}
.order-tabs :deep(.el-tabs__item.is-active) {
  color: var(--user-primary);
  font-weight: 500;
}
.order-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
}
.loading-card,
.empty-state {
  padding: 28px;
  border: 1px solid #e2e9f4;
  border-radius: 12px;
  background: #fff;
}
.order-pagination {
  display: flex;
  justify-content: center;
  overflow: auto;
  margin-top: 30px;
}
.empty-state {
  padding-block: 60px;
  text-align: center;
}
.empty-state svg {
  width: 52px;
  height: 52px;
  margin-bottom: 20px;
  color: #98b4e4;
}
.empty-state h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 500;
}
.empty-state p {
  margin: 12px 0 0;
  color: #8491a6;
  font-size: 14px;
  line-height: 1.8;
}
@media (max-width: 1199px) {
  .order-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 700px) {
  .order-grid {
    grid-template-columns: 1fr;
    gap: 20px;
  }
  .page-heading {
    align-items: flex-start;
    flex-wrap: wrap;
  }
  .heading-actions {
    margin-left: auto;
  }
  .heading-actions .el-button {
    height: 38px;
    padding-inline: 14px;
  }
  .order-tabs :deep(.el-tabs__item) {
    padding-inline: 14px;
    font-size: 13px;
  }
  .order-pagination {
    justify-content: flex-start;
  }
}
</style>
