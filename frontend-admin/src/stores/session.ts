import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getToken, removeToken, setToken } from '@/utils/auth'

export const useSessionStore = defineStore('session', () => {
  const token = ref(getToken())

  function startSession(value: string): void {
    setToken(value)
    token.value = value
  }

  function clearSession(): void {
    removeToken()
    token.value = ''
  }

  return { token, startSession, clearSession }
})
