<template>
  <ElDescriptions :column="2" border class="coupon-info">
    <ElDescriptionsItem label="优惠券ID">{{ coupon.id }}</ElDescriptionsItem>
    <ElDescriptionsItem label="优惠券名称">{{ coupon.name }}</ElDescriptionsItem>
    <ElDescriptionsItem label="优惠券类型">{{
      coupon.type === 1 ? '代金券' : '满减券'
    }}</ElDescriptionsItem>
    <ElDescriptionsItem label="起售状态">{{
      coupon.status === 1 ? '起售' : '停售'
    }}</ElDescriptionsItem>
    <ElDescriptionsItem label="剩余库存">{{ coupon.stock }}</ElDescriptionsItem>
    <ElDescriptionsItem label="总库存">{{ coupon.totalStock }}</ElDescriptionsItem>
    <ElDescriptionsItem label="抵扣金额"
      >￥{{ coupon.discountAmount.toFixed(2) }}</ElDescriptionsItem
    >
    <ElDescriptionsItem label="门槛金额"
      >￥{{ coupon.thresholdAmount.toFixed(2) }}</ElDescriptionsItem
    >
    <ElDescriptionsItem label="购买价格">￥{{ coupon.price.toFixed(2) }}</ElDescriptionsItem>
    <ElDescriptionsItem label="有效期">{{ coupon.validTime }} 天</ElDescriptionsItem>
    <ElDescriptionsItem label="活动开始时间">{{ formatTime(coupon.startTime) }}</ElDescriptionsItem>
    <ElDescriptionsItem label="活动结束时间">{{ formatTime(coupon.endTime) }}</ElDescriptionsItem>
    <ElDescriptionsItem label="创建时间">{{ formatTime(coupon.createTime) }}</ElDescriptionsItem>
    <ElDescriptionsItem label="更新时间">{{ formatTime(coupon.updateTime) }}</ElDescriptionsItem>
  </ElDescriptions>
</template>

<script setup lang="ts">
import { ElDescriptions, ElDescriptionsItem } from 'element-plus'
import type { Coupon } from '@/api/coupon'

defineProps<{ coupon: Coupon }>()
const formatTime = (value: string | null) => value?.replace('T', ' ') || '—'
</script>

<style scoped>
.coupon-info :deep(.el-descriptions__cell) {
  overflow-wrap: anywhere;
}
.coupon-info :deep(.el-descriptions__label) {
  width: 120px;
  white-space: nowrap;
}
</style>
