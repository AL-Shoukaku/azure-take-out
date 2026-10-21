<template>
  <div class="profile-page">
    <div class="page-heading">
      <h1>我的</h1>
      <p>个人资料与账号设置</p>
    </div>

    <ElSkeleton
      v-if="loading && !session.profile"
      :rows="8"
      animated
      class="profile-card loading-card"
    />
    <ElAlert
      v-else-if="loadError"
      title="个人资料加载失败"
      type="error"
      :closable="false"
      show-icon
    >
      <p>{{ loadError }}</p>
      <ElButton :loading="loading" @click="loadProfile">重新加载</ElButton>
    </ElAlert>

    <template v-else-if="session.profile">
      <section class="profile-card profile-summary" aria-label="个人资料">
        <div class="summary-user">
          <UserAvatar :src="session.profile.avatar" :size="76" alt="个人头像" />
          <div class="summary-text">
            <h2>{{ session.profile.username }}</h2>
            <p>账号：{{ session.profile.account }}</p>
          </div>
        </div>
        <ElButton class="edit-button" type="primary" @click="editDialogOpen = true">
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="m16 3 5 5M4 16 17 3a2 2 0 0 1 4 4L8 20l-5 1 1-5Z"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
          编辑资料
        </ElButton>
      </section>

      <div class="profile-details">
        <section class="profile-card basic-card" aria-labelledby="basic-title">
          <h2 id="basic-title" class="card-title">
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <circle cx="12" cy="7" r="4" stroke="currentColor" stroke-width="1.6" />
              <path
                d="M4 21v-2a8 8 0 0 1 16 0v2"
                stroke="currentColor"
                stroke-width="1.6"
                stroke-linecap="round"
              />
            </svg>
            基本信息
          </h2>
          <dl class="info-grid">
            <div>
              <dt>昵称</dt>
              <dd>{{ session.profile.username }}</dd>
            </div>
            <div>
              <dt>姓名</dt>
              <dd>{{ session.profile.name || '未填写' }}</dd>
            </div>
            <div>
              <dt>性别</dt>
              <dd>{{ genderLabel }}</dd>
            </div>
            <div>
              <dt>手机号</dt>
              <dd>{{ session.profile.phone || '未填写' }}</dd>
            </div>
            <div>
              <dt>身份证号</dt>
              <dd>{{ session.profile.idNumber || '未填写' }}</dd>
            </div>
            <div>
              <dt>地址</dt>
              <dd><RouterLink to="/addresses">管理收货地址</RouterLink></dd>
            </div>
          </dl>
        </section>

        <section class="profile-card security-card" aria-labelledby="security-title">
          <h2 id="security-title" class="card-title">
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="m12 3 8 4v6c0 5-8 9-8 9s-8-4-8-9V7l8-4Z"
                stroke="currentColor"
                stroke-width="1.6"
                stroke-linejoin="round"
              />
              <path
                d="m8 12 3 3 5-5"
                stroke="currentColor"
                stroke-width="1.6"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
            账号安全
          </h2>
          <div class="security-item">
            <div>
              <h3>登录密码</h3>
              <p>使用当前密码验证后修改</p>
            </div>
            <ElButton type="primary" text @click="passwordDialogOpen = true">修改密码</ElButton>
          </div>
          <div class="security-item">
            <div>
              <h3>当前账号</h3>
              <p>{{ session.profile.account }}</p>
            </div>
            <ElButton class="logout-button" :loading="loggingOut" @click="handleLogout"
              >退出登录</ElButton
            >
          </div>
        </section>
      </div>

      <ProfileEditDialog v-model="editDialogOpen" :profile="session.profile" @saved="handleSaved" />
    </template>
    <PasswordDialog v-model="passwordDialogOpen" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElMessage, ElSkeleton } from 'element-plus'
import { logout } from '@/api/auth'
import type { UserInfoDTO } from '@/api/user'
import { useSessionStore } from '@/stores/session'
import UserAvatar from '@/components/UserAvatar.vue'
import ProfileEditDialog from './components/ProfileEditDialog.vue'
import PasswordDialog from './components/PasswordDialog.vue'

const router = useRouter()
const session = useSessionStore()
const loading = ref(true)
const loadError = ref('')
const editDialogOpen = ref(false)
const passwordDialogOpen = ref(false)
const loggingOut = ref(false)
const genderLabel = computed(() =>
  session.profile?.sex === '1' ? '男' : session.profile?.sex === '0' ? '女' : '未填写',
)

async function loadProfile(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    await session.restoreUser()
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '请稍后重试'
  } finally {
    loading.value = false
  }
}

function handleSaved(value: UserInfoDTO): void {
  session.updateProfile({ ...session.profile!, ...value })
}

async function handleLogout(): Promise<void> {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await logout()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '退出登录失败')
  } finally {
    session.clearSession()
    await router.replace('/login')
    loggingOut.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped>
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

.profile-card {
  padding: 28px 32px;
  border: 1px solid #e3eaf5;
  border-radius: 12px;
  background: #fff;
}

.profile-summary,
.summary-user {
  display: flex;
  align-items: center;
}

.profile-summary {
  justify-content: space-between;
  gap: 24px;
  min-height: 152px;
}

.summary-user {
  gap: 24px;
  min-width: 0;
}

.summary-text {
  min-width: 0;
  overflow-wrap: anywhere;
}

.summary-text h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 500;
  line-height: 1.5;
}

.summary-text p {
  margin: 8px 0 0;
  color: #8491a6;
  font-size: 14px;
}

.edit-button {
  flex-shrink: 0;
  height: 40px;
  border-radius: 8px;
}

.edit-button svg {
  width: 18px;
  height: 18px;
  margin-right: 8px;
}

.profile-details {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  align-items: start;
  gap: 24px;
  margin-top: 24px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0 0 28px;
  font-size: 18px;
  font-weight: 500;
  line-height: 28px;
}

.card-title svg {
  width: 21px;
  height: 21px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 28px 32px;
  margin: 0;
}

dt {
  color: #8491a6;
  font-size: 14px;
  line-height: 22px;
}

dd {
  margin: 8px 0 0;
  color: #465872;
  font-size: 15px;
  line-height: 24px;
  overflow-wrap: anywhere;
}

dd a {
  color: var(--user-primary);
  text-decoration: none;
}

dd a:hover {
  text-decoration: underline;
}

.security-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-width: 0;
}

.security-item + .security-item {
  margin-top: 28px;
  padding-top: 28px;
  border-top: 1px solid #e3eaf5;
}

.security-item > div {
  min-width: 0;
}

.security-item h3 {
  margin: 0;
  color: #465872;
  font-size: 15px;
  font-weight: 400;
  line-height: 24px;
}

.security-item p {
  margin: 8px 0 0;
  color: #8491a6;
  font-size: 14px;
  line-height: 22px;
  overflow-wrap: anywhere;
}

.security-item .el-button {
  flex-shrink: 0;
}

.logout-button {
  height: 40px;
  border-radius: 8px;
  color: var(--el-color-danger);
}

@media (max-width: 1000px) {
  .profile-details {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .profile-card {
    padding: 24px 20px;
  }

  .profile-summary {
    flex-wrap: wrap;
    gap: 20px;
  }

  .summary-user {
    gap: 16px;
  }

  .info-grid {
    gap: 24px 20px;
  }

  .security-item {
    gap: 8px;
  }

  .security-item p {
    font-size: 12px;
  }
}
</style>
