<template>
  <main class="login-page">
    <div class="login-background" aria-hidden="true">
      <img :src="loginBackground" alt="" fetchpriority="high" />
    </div>

    <section class="login-panel" aria-labelledby="welcome-title">
      <div class="login-content">
        <img class="login-logo" :src="logo" alt="碧蓝外卖" width="208" height="52" />
        <h1 id="welcome-title">{{ isRegister ? '欢迎加入碧蓝外卖' : '欢迎回来' }}</h1>

        <div class="auth-tabs" role="tablist" aria-label="登录或注册" @keydown="handleTabKeydown">
          <button
            id="login-tab"
            ref="loginTab"
            type="button"
            role="tab"
            :aria-selected="!isRegister"
            aria-controls="auth-panel"
            :tabindex="isRegister ? -1 : 0"
            :disabled="loading"
            @click="changeMode('login')"
          >
            登录
          </button>
          <button
            id="register-tab"
            ref="registerTab"
            type="button"
            role="tab"
            :aria-selected="isRegister"
            aria-controls="auth-panel"
            :tabindex="isRegister ? 0 : -1"
            :disabled="loading"
            @click="changeMode('register')"
          >
            注册
          </button>
        </div>

        <div id="auth-panel" role="tabpanel" :aria-labelledby="`${mode}-tab`">
          <ElForm
            ref="authForm"
            class="auth-form"
            :model="form"
            :rules="rules"
            label-position="top"
            :hide-required-asterisk="true"
            :disabled="loading"
            :aria-busy="loading"
            @submit.prevent="handleSubmit"
          >
            <ElFormItem label="账号" prop="account">
              <ElInput
                v-model="form.account"
                name="account"
                :maxlength="32"
                placeholder="请输入账号"
                autocomplete="username"
                :spellcheck="false"
                autocapitalize="none"
              >
                <template #prefix>
                  <svg class="input-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <circle cx="12" cy="8" r="4" stroke="currentColor" stroke-width="1.6" />
                    <path
                      d="M4 21v-2a8 8 0 0 1 16 0v2"
                      stroke="currentColor"
                      stroke-width="1.6"
                      stroke-linecap="round"
                    />
                  </svg>
                </template>
              </ElInput>
            </ElFormItem>

            <ElFormItem label="密码" prop="password">
              <ElInput
                v-model="form.password"
                name="password"
                type="password"
                placeholder="请输入密码"
                :autocomplete="isRegister ? 'new-password' : 'current-password'"
                show-password
                @input="revalidateConfirmation"
              >
                <template #prefix>
                  <svg class="input-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <rect
                      x="5"
                      y="10"
                      width="14"
                      height="11"
                      rx="2"
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

            <ElFormItem v-if="isRegister" label="确认密码" prop="confirmPassword">
              <ElInput
                v-model="form.confirmPassword"
                name="confirmPassword"
                type="password"
                placeholder="请再次输入密码"
                autocomplete="new-password"
                show-password
              >
                <template #prefix>
                  <svg class="input-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <rect
                      x="5"
                      y="10"
                      width="14"
                      height="11"
                      rx="2"
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

            <ElButton class="submit-button" type="primary" native-type="submit" :loading="loading">
              {{
                loading ? (isRegister ? '注册中...' : '登录中...') : isRegister ? '注册' : '登录'
              }}
            </ElButton>
          </ElForm>
        </div>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, useTemplateRef } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { login, register, type UserCredentials } from '@/api/auth'
import { useSessionStore } from '@/stores/session'
import logo from '@/assets/images/brand/logo-with-name.webp'
import loginBackground from '@/assets/images/login/background.webp'

type AuthMode = 'login' | 'register'
interface AuthForm extends UserCredentials {
  confirmPassword: string
}

const router = useRouter()
const session = useSessionStore()
const mode = ref<AuthMode>('login')
const isRegister = computed(() => mode.value === 'register')
const loading = ref(false)
const form = reactive<AuthForm>({ account: '', password: '', confirmPassword: '' })
const authForm = useTemplateRef<FormInstance>('authForm')
const loginTab = useTemplateRef<HTMLButtonElement>('loginTab')
const registerTab = useTemplateRef<HTMLButtonElement>('registerTab')
const rules: FormRules<AuthForm> = {
  account: [
    { required: true, whitespace: true, message: '请输入账号', trigger: 'blur' },
    {
      pattern: /^[A-Za-z][A-Za-z0-9]{2,31}$/,
      transform: (value: string) => value.trim(),
      message: '账号长度为 3–32 位，只能包含字母和数字，且以字母开头',
      trigger: 'blur',
    },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9]{6,}$/,
      message: '密码至少 6 位，且只能包含字母和数字',
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        callback(value === form.password ? undefined : new Error('两次输入的密码不一致'))
      },
      trigger: 'blur',
    },
  ],
}

async function changeMode(value: AuthMode): Promise<void> {
  if (loading.value || mode.value === value) return
  mode.value = value
  form.password = ''
  form.confirmPassword = ''
  await nextTick()
  authForm.value?.clearValidate()
}

async function handleTabKeydown(event: KeyboardEvent): Promise<void> {
  if (loading.value) return
  let target: AuthMode
  switch (event.key) {
    case 'ArrowLeft':
    case 'ArrowRight':
      target = isRegister.value ? 'login' : 'register'
      break
    case 'Home':
      target = 'login'
      break
    case 'End':
      target = 'register'
      break
    default:
      return
  }
  event.preventDefault()
  await changeMode(target)
  const activeTab = target === 'login' ? loginTab.value : registerTab.value
  activeTab?.focus()
}

function revalidateConfirmation(): void {
  if (isRegister.value && form.confirmPassword) {
    void authForm.value?.validateField('confirmPassword').catch(() => undefined)
  }
}

async function handleSubmit(): Promise<void> {
  if (loading.value) return
  loading.value = true

  try {
    const valid = await authForm.value?.validate().catch(() => false)
    if (!valid) return

    const credentials: UserCredentials = { account: form.account.trim(), password: form.password }
    if (isRegister.value) {
      await register(credentials)
      form.account = credentials.account
      // 注册接口不返回 token，成功后由用户使用新账号登录。
      loading.value = false
      await changeMode('login')
      ElMessage.success('注册成功，请登录')
    } else {
      const user = await login(credentials)
      session.startSession(user)
      await router.replace('/menu')
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  min-height: 100dvh;
  grid-template-columns: 3fr 2fr;
}

.login-background {
  position: relative;
  min-width: 0;
  overflow: hidden;
  background: #fff;
}

.login-background img {
  position: absolute;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: right center;
}

.login-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  padding: 48px clamp(32px, 4vw, 80px);
  background: var(--user-surface);
}

.login-content {
  width: 360px;
  max-width: 100%;
}

.login-logo {
  display: block;
  max-width: 100%;
  height: auto;
  margin-inline: auto;
}

h1 {
  margin: 24px 0 32px;
  font-size: 28px;
  font-weight: 600;
  line-height: 1.5;
  letter-spacing: 1px;
}

.auth-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  margin-bottom: 30px;
  padding: 5px;
  border-radius: 10px;
  background: #eaf0f9;
}

.auth-tabs button {
  height: 42px;
  padding: 0 12px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #73829a;
  font-size: 15px;
  cursor: pointer;
  transition:
    color 150ms,
    background-color 150ms;
}

.auth-tabs button[aria-selected='true'] {
  background: #fff;
  color: var(--user-primary);
  font-weight: 600;
}

.auth-tabs button:focus-visible {
  outline: 2px solid var(--user-primary);
  outline-offset: 1px;
}

.auth-tabs button:disabled {
  cursor: wait;
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 28px;
}

.auth-form :deep(.el-form-item__label) {
  margin-bottom: 10px;
  padding: 0;
  color: #64748b;
  font-size: 14px;
  line-height: 22px;
}

.auth-form :deep(.el-input__wrapper) {
  min-height: 50px;
  padding: 0 16px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 0 0 1px var(--user-border) inset;
}

.auth-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--user-primary) inset;
}

.auth-form :deep(.el-form-item.is-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}

.auth-form :deep(.el-input__inner) {
  color: #354661;
  font-size: 14px;
}

.auth-form :deep(.el-input__inner::placeholder) {
  color: #a4b0c2;
}

.auth-form :deep(.el-form-item__error) {
  position: static;
  padding-top: 6px;
  line-height: 1.5;
}

.input-icon {
  width: 18px;
  height: 20px;
  margin-right: 6px;
  color: #a6b7d0;
}

.submit-button {
  width: 100%;
  height: 50px;
  margin-top: 4px;
  border-radius: 8px;
  font-size: 16px;
  letter-spacing: 2px;
}

.submit-button:focus-visible {
  outline: 2px solid var(--user-primary);
  outline-offset: 3px;
}

@media (max-width: 767px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-background {
    display: none;
  }

  .login-panel {
    padding: 40px 24px;
  }

  h1 {
    font-size: 26px;
    margin-bottom: 24px;
  }
}
</style>
