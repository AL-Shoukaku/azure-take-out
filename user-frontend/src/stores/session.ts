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
  let profileRevision = 0
  let profileRequest: { token: string; promise: Promise<void> } | undefined

  function invalidateProfileRequest(): void {
    profileRevision += 1
    profileRequest = undefined
  }

  function startSession(value: UserLoginVO): void {
    invalidateProfileRequest()
    setToken(value.token)
    token.value = value.token
    user.value = { id: value.id, username: value.username, avatar: value.avatar }
    profile.value = null
  }

  function clearSession(): void {
    invalidateProfileRequest()
    removeToken()
    token.value = ''
    user.value = null
    profile.value = null
  }

  function updateProfile(value: UserInfoVO): void {
    invalidateProfileRequest()
    profile.value = value
    user.value = { id: value.id, username: value.username, avatar: value.avatar }
  }

  function restoreUser(): Promise<void> {
    const requestToken = getToken()
    // 同一会话内共用请求；会话切换或资料保存后，旧响应不能写回。
    if (profileRequest?.token === requestToken) return profileRequest.promise

    const revision = profileRevision
    const promise: Promise<void> = getUserInfo()
      .then((value) => {
        if (revision === profileRevision && requestToken === getToken()) updateProfile(value)
      })
      .finally(() => {
        if (profileRequest?.promise === promise) profileRequest = undefined
      })
    profileRequest = { token: requestToken, promise }
    return promise
  }

  return { token, user, profile, startSession, clearSession, updateProfile, restoreUser }
})
