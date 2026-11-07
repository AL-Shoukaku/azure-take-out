<template>
  <ElDialog
    v-model="visible"
    title="联系商家"
    width="400px"
    class="user-dialog"
    modal-class="user-dialog-overlay"
    align-center
    append-to-body
    destroy-on-close
  >
    <div class="merchant-contact">
      <p>碧蓝外卖 · 演示手机号</p>
      <a href="tel:13800000000">138 0000 0000</a>
      <p>订单、配送或售后问题，可以联系商家咨询。</p>
    </div>
    <template #footer><ElButton type="primary" @click="visible = false">知道了</ElButton></template>
  </ElDialog>
</template>

<script setup lang="ts">
import { ElButton, ElDialog } from 'element-plus'

const visible = defineModel<boolean>({ required: true })
</script>

<style scoped>
.merchant-contact {
  padding-block: 8px;
  text-align: center;
}
.merchant-contact p {
  color: #8491a6;
  font-size: 14px;
  line-height: 1.8;
}
.merchant-contact a {
  display: inline-block;
  margin-block: 8px;
  color: var(--user-primary);
  font-size: 28px;
  text-decoration: none;
}
</style>
