import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSessionStore } from '../session'
import { getToken, removeToken, setToken } from '@/utils/auth'

const employee = { id: 7, userName: 'admin', name: '店长', token: 'new-token' }

beforeEach(() => {
  localStorage.clear()
  setActivePinia(createPinia())
})
afterEach(() => {
  removeToken()
  localStorage.clear()
})

describe('登录会话', () => {
  it('仅 admin 账号具有员工管理权限，会随会话和账号变化更新', () => {
    const session = useSessionStore()
    expect(session.isAdmin).toBe(false)
    session.startSession(employee)
    expect(session.isAdmin).toBe(true)
    session.updateEmployeeProfile({ id: 7, userName: 'store-admin', name: '店员' })
    expect(session.isAdmin).toBe(false)
    session.startSession(employee)
    session.clearSession()
    expect(session.isAdmin).toBe(false)
  })

  it('初始化时恢复 Cookie 中的登录凭证', () => {
    setToken('saved-token')
    expect(useSessionStore().token).toBe('saved-token')
  })

  it('登录成功将凭证写入 Cookie，并能在新应用中恢复', () => {
    useSessionStore().startSession(employee)
    expect(getToken()).toBe('new-token')
    setActivePinia(createPinia())
    expect(useSessionStore().token).toBe('new-token')
    expect(useSessionStore().employee).toEqual({ id: 7, userName: 'admin', name: '店长' })
    expect(localStorage.getItem('azure-admin-employee')).not.toContain('new-token')
  })

  it('清除会话时同步清除 Cookie 和共享状态', () => {
    const session = useSessionStore()
    session.startSession(employee)
    session.clearSession()
    expect(session.token).toBe('')
    expect(getToken()).toBe('')
    expect(session.employee).toBeNull()
    expect(localStorage.getItem('azure-admin-employee')).toBeNull()
  })

  it('token 已失效时，不恢复上次员工资料', () => {
    localStorage.setItem(
      'azure-admin-employee',
      JSON.stringify({ id: 7, userName: 'admin', name: '店长' }),
    )
    expect(useSessionStore().employee).toBeNull()
  })

  it('更新本人资料后，新应用恢复更新后的姓名和账号，不改变登录凭证', () => {
    const session = useSessionStore()
    session.startSession(employee)
    session.updateEmployeeProfile({ id: 7, userName: 'newmanager', name: '新店长' })
    setActivePinia(createPinia())
    expect(useSessionStore().employee).toEqual({ id: 7, userName: 'newmanager', name: '新店长' })
    expect(getToken()).toBe('new-token')
  })
})
