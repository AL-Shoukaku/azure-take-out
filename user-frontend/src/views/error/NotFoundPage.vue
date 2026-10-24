<template>
  <section class="not-found-page" aria-labelledby="not-found-title">
    <img :src="notFoundImage" alt="页面未找到插画" width="280" height="280" decoding="async" />
    <p class="error-code">404</p>
    <h1 id="not-found-title">页面走丢啦</h1>
    <p class="description">这个地址暂时找不到，先回去看看今天吃什么吧。</p>
    <ElButton type="primary" @click="router.replace('/menu')">返回点餐</ElButton>
  </section>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElButton } from 'element-plus'
import notFoundImage from '@/assets/images/status/not-found.webp'

const router = useRouter()
</script>

<style scoped>
.not-found-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: calc(100dvh - 208px);
  padding: 16px 0 32px;
  text-align: center;
}

.not-found-page img {
  width: 280px;
  max-width: 70vw;
  height: auto;
  margin-bottom: 20px;
  border-radius: 50%;
  object-fit: contain;
}

.error-code {
  margin: 0 0 8px;
  color: var(--user-primary);
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 5px;
}

h1 {
  margin: 0;
  color: #354661;
  font-size: 28px;
  line-height: 1.5;
}

.description {
  margin: 14px 0 26px;
  color: #8495ae;
  font-size: 14px;
  line-height: 1.8;
}

.el-button {
  height: 42px;
  padding-inline: 28px;
  border-radius: 8px;
}

@media (max-width: 600px) {
  .not-found-page img {
    width: 220px;
  }
}
</style>
