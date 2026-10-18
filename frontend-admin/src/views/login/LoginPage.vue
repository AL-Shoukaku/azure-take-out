<template>
  <main class="login-page">
    <section class="login-card" aria-label="管理员登录">
      <img class="login-image" :src="loginImage" alt="" aria-hidden="true" />

      <div class="login-panel">
        <ElForm
          ref="loginForm"
          class="login-form"
          :model="form"
          :rules="rules"
          :aria-busy="loading"
          @submit.prevent="handleLogin"
        >
          <h1 class="login-title">
            <img :src="logo" alt="瑞吉外卖" width="149" height="38" />
          </h1>

          <ElFormItem prop="username">
            <ElInput
              v-model="form.username"
              name="username"
              placeholder="账号"
              aria-label="账号"
              autocomplete="username"
            >
              <template #prefix>
                <svg class="input-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path
                    d="M16 8a4 4 0 1 0-8 0c0 2 1 3 1 4-1 2-6 3-6 6v2h18v-2c0-3-5-4-6-6 0-1 1-2 1-4Z"
                    stroke="currentColor"
                    stroke-width="1.6"
                    stroke-linejoin="round"
                  />
                </svg>
              </template>
            </ElInput>
          </ElFormItem>

          <ElFormItem prop="password">
            <ElInput
              v-model="form.password"
              type="password"
              name="password"
              placeholder="密码"
              aria-label="密码"
              autocomplete="current-password"
            >
              <template #prefix>
                <svg class="input-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <rect
                    x="5"
                    y="10"
                    width="14"
                    height="11"
                    rx="1"
                    stroke="currentColor"
                    stroke-width="1.6"
                  />
                  <path
                    d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3"
                    stroke="currentColor"
                    stroke-width="1.6"
                    stroke-linecap="round"
                  />
                </svg>
              </template>
            </ElInput>
          </ElFormItem>

          <ElButton class="login-button" native-type="submit" :loading="loading">
            {{ loading ? '登录中...' : '登录' }}
          </ElButton>
        </ElForm>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref, useTemplateRef } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { login, type LoginCredentials } from '@/api/auth'
import { useSessionStore } from '@/stores/session'
import loginImage from '@/assets/images/login/login-l.png'
import logo from '@/assets/images/login/icon_logo.png'

const router = useRouter()
const session = useSessionStore()
const loginForm = useTemplateRef<FormInstance>('loginForm')
const form = reactive<LoginCredentials>({ username: '', password: '' })
const loading = ref(false)
const rules: FormRules<LoginCredentials> = {
  username: [{ required: true, whitespace: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码必须在6位以上', trigger: 'blur' },
  ],
}

async function handleLogin(): Promise<void> {
  if (loading.value) return
  loading.value = true

  try {
    const valid = await loginForm.value?.validate().catch(() => false)
    if (!valid) return

    const employee = await login({ username: form.username.trim(), password: form.password })
    session.startSession(employee)
    await router.replace('/dashboard')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
  min-height: 100dvh;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: #333;
}

.login-card {
  display: grid;
  width: 1000px;
  max-width: 100%;
  height: 474.38px;
  grid-template-columns: 3fr 2fr;
  overflow: hidden;
  border-radius: 8px;
  background: #fff;
}

.login-image {
  width: 100%;
  height: 100%;
  min-height: 0;
  object-fit: cover;
}

.login-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  padding: 24px;
}

.login-form {
  width: 214px;
  max-width: 100%;
  height: 307px;
}

.login-title {
  display: flex;
  height: 38px;
  align-items: center;
  justify-content: center;
  margin: 0 0 40px;
}

.login-title img {
  display: block;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 38px;
}

.login-form :deep(.el-input__wrapper) {
  height: 32px;
  padding: 0;
  border-bottom: 1px solid #e9e9e8;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  border-bottom-color: #ffc200;
  box-shadow: none;
}

.login-form :deep(.el-form-item.is-error .el-input__wrapper) {
  border-bottom-color: #fd7065;
  box-shadow: none;
}

.login-form :deep(.el-input__inner) {
  height: 32px;
  color: #333;
  font-size: 12px;
}

.login-form :deep(.el-input__inner::placeholder) {
  color: #aeb5c4;
}

.input-icon {
  width: 18px;
  height: 20px;
  color: #b7bfd0;
}

.login-button {
  width: 100%;
  height: 34px;
  margin-top: 4px;
  border: 0;
  border-radius: 18px;
  background: #ffc200;
  color: #333;
  font-size: 12px;
  font-weight: 500;
}

.login-button:hover,
.login-button:focus-visible {
  background: #f7c744;
  color: #333;
}

.login-button:focus-visible {
  outline: 2px solid #b38600;
  outline-offset: 3px;
}

@media (max-width: 600px) {
  .login-page {
    padding: 24px;
  }

  .login-card {
    width: 400px;
    height: auto;
    grid-template-columns: 1fr;
  }

  .login-image {
    height: 200px;
    object-position: center 45%;
  }

  .login-panel {
    padding: 40px 32px 24px;
  }
}
</style>
