import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { UserLoginVO } from '@/api/auth'
import { getUserInfo, type UserInfoVO } from '@/api/user'
import { getToken, removeToken, setToken } from '@/utils/auth'

type UserSummary = Omit<UserLoginVO, 'token'>

export const useSessionStore = defineStore('session', () => {
  const token = ref(getToken())
  const user = ref<UserSummary | null>(null)
  const profile = ref<UserInfoVO | null>(null)
  let profileRequest: Promise<void> | undefined

  function startSession(value: UserLoginVO): void {
    setToken(value.token)
    token.value = value.token
    user.value = { id: value.id, username: value.username, avatar: value.avatar }
    profile.value = null
  }

  function clearSession(): void {
    removeToken()
    token.value = ''
    user.value = null
    profile.value = null
  }

  function updateProfile(value: UserInfoVO): void {
    profile.value = value
    user.value = { id: value.id, username: value.username, avatar: value.avatar }
  }

  function restoreUser(): Promise<void> {
    // 公共布局和个人资料页会同时请求当前资料，共用这一次请求。
    profileRequest ??= getUserInfo()
      .then(updateProfile)
      .finally(() => {
        profileRequest = undefined
      })
    return profileRequest
  }

  return { token, user, profile, startSession, clearSession, updateProfile, restoreUser }
})
