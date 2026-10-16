<template>
  <div class="login">
    <div class="login-box">
      <img class="login-illustration" src="@/assets/img/login/login-l.png" alt="" />

      <div class="login-panel">
        <el-form
          ref="loginFormRef"
          class="login-form"
          :model="loginForm"
          :rules="loginRules"
          label-position="top"
          size="large"
          @submit.prevent="handleLogin"
        >
          <img class="login-logo" src="@/assets/img/login/icon_logo.png" alt="外卖管理系统" />

          <el-form-item label="账号" prop="username">
            <el-input
              v-model="loginForm.username"
              placeholder="请输入账号"
              autocomplete="username"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
              show-password
            />
          </el-form-item>

          <el-form-item>
            <el-button
              class="login-button"
              type="primary"
              color="#ffc200"
              native-type="submit"
              :loading="loading"
              :disabled="loading"
            >
              {{ loading ? '登录中…' : '登录' }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { login } from '@/api/auth'

interface LoginForm {
  username: string
  password: string
}

// 表单中实际填写的数据
const loginForm = reactive<LoginForm>({
  username: '',
  password: '',
})

// 表单组件的引用，用于调用它的校验方法
const loginFormRef = ref<FormInstance | null>(null)

// 是否正在提交
const loading = ref(false)

// 路由器
const router = useRouter()

// 校验规则
const loginRules: FormRules<LoginForm> = {
  username: [
    {
      required: true,
      whitespace: true,
      message: '请输入账号',
      trigger: 'blur',
    },
  ],
  password: [
    {
      required: true,
      message: '请输入密码',
      trigger: 'blur',
    },
  ],
}

async function handleLogin() {
  // 提交中，或者表单组件还没准备好，就结束本次操作
  if (loading.value || !loginFormRef.value) {
    return
  }

  // 校验不通过时，validate 会拒绝 Promise
  // 这里将这种情况转换为 false
  const valid = await loginFormRef.value.validate().catch(() => false)

  // 校验未通过，或者等待校验期间已经有其他提交，就结束
  if (!valid || loading.value) {
    return
  }

  loading.value = true

  try {
    const user = await login({
      username: loginForm.username.trim(),
      password: loginForm.password,
    })

    sessionStorage.setItem('sky_token', user.token)

    ElMessage.success('登录成功')

    await router.replace('/')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* 整个登录背景，根据 class 找元素 */
.login {
  min-height: 100vh;
  padding: 24px;

  /* 使用弹性布局，水平，垂直居中 */
  display: flex;
  justify-content: center;
  align-items: center;

  background-color: #333;
}

/* 登录盒子 */
.login-box {
  display: flex;

  width: 1000px;
  max-width: 100%;

  border-radius: 12px;
  overflow: hidden;

  background-color: #fff;
}

/* 左侧插画 */
.login-illustration {
  display: block;

  width: 60%;
  height: 480px;
  min-width: 0;

  object-fit: cover;
}

/* 右侧白色区域 */
.login-panel {
  width: 40%;
  padding: 48px 40px;

  display: flex;
  justify-content: center;
  align-items: center;
}

/* 实际放输入框的表单 */
.login-form {
  width: 100%;
  max-width: 280px;
}
/* 标志图片 */
.login-logo {
  display: block;

  width: 149px;
  height: 38px;

  margin: 0 auto 36px;
}

/* 登录按钮 */
.login-button {
  width: 100%;
  height: 42px;
  border-radius: 21px;
}

/* 窗口比较窄时，只显示表单 */
@media (max-width: 700px) {
  .login-illustration {
    display: none;
  }

  .login-box {
    max-width: 420px;
  }

  .login-panel {
    width: 100%;
    padding: 40px 24px;
  }
}
</style>
