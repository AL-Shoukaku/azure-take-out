import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSessionStore } from '../session'
import { getToken, removeToken, setToken } from '@/utils/auth'

beforeEach(() => setActivePinia(createPinia()))
afterEach(removeToken)

describe('登录会话', () => {
  it('初始化时恢复 Cookie 中的登录凭证', () => {
    setToken('saved-token')
    expect(useSessionStore().token).toBe('saved-token')
  })

  it('登录成功将凭证写入 Cookie，并能在新应用中恢复', () => {
    useSessionStore().startSession('new-token')
    expect(getToken()).toBe('new-token')
    setActivePinia(createPinia())
    expect(useSessionStore().token).toBe('new-token')
  })

  it('清除会话时同步清除 Cookie 和共享状态', () => {
    const session = useSessionStore()
    session.startSession('new-token')
    session.clearSession()
    expect(session.token).toBe('')
    expect(getToken()).toBe('')
  })
})
