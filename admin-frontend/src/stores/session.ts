import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getToken, removeToken, setToken } from '@/utils/auth'
import type { EmployeeLoginVO } from '@/api/auth'

type EmployeeProfile = Omit<EmployeeLoginVO, 'token'>
const employeeStorageKey = 'sky-admin-employee'

export const useSessionStore = defineStore('session', () => {
  const token = ref(getToken())
  const employee = ref<EmployeeProfile | null>(
    token.value
      ? (JSON.parse(localStorage.getItem(employeeStorageKey) || 'null') as EmployeeProfile | null)
      : null,
  )
  const isAdmin = computed(() => employee.value?.userName === 'admin')

  function startSession(value: EmployeeLoginVO): void {
    setToken(value.token)
    token.value = value.token
    employee.value = { id: value.id, userName: value.userName, name: value.name }
    localStorage.setItem(employeeStorageKey, JSON.stringify(employee.value))
  }

  function clearSession(): void {
    removeToken()
    token.value = ''
    employee.value = null
    localStorage.removeItem(employeeStorageKey)
  }

  function updateEmployeeProfile(profile: EmployeeProfile): void {
    employee.value = profile
    localStorage.setItem(employeeStorageKey, JSON.stringify(profile))
  }

  return { token, employee, isAdmin, startSession, clearSession, updateEmployeeProfile }
})
