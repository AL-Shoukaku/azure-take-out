import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSessionStore } from '../session'
import { getToken, removeToken, setToken } from '@/utils/auth'
import { getUserInfo } from '@/api/user'

vi.mock('@/api/user', () => ({ getUserInfo: vi.fn<typeof getUserInfo>() }))

beforeEach(() => setActivePinia(createPinia()))
afterEach(removeToken)

describe('用户会话', () => {
  it('从 Cookie 恢复登录凭证', () => {
    setToken('saved-token')
    expect(useSessionStore().token).toBe('saved-token')
  })

  it('登录后将 token 写入 Cookie，资料存入 Pinia', () => {
    const session = useSessionStore()
    session.startSession({ id: 7, username: '用户7', avatar: null, token: 'new-token' })
    expect(getToken()).toBe('new-token')
    expect(session.user).toEqual({ id: 7, username: '用户7', avatar: null })
    setActivePinia(createPinia())
    expect(useSessionStore().token).toBe('new-token')
  })

  it('清除会话时同步清除 Cookie 和用户资料', () => {
    const session = useSessionStore()
    session.startSession({ id: 7, username: '用户7', avatar: null, token: 'new-token' })
    session.clearSession()
    expect(getToken()).toBe('')
    expect(session.token).toBe('')
    expect(session.user).toBeNull()
  })

  it('刷新后通过用户信息接口恢复昵称和头像', async () => {
    setToken('saved-token')
    vi.mocked(getUserInfo).mockResolvedValue({
      id: 7,
      account: 'customer',
      username: '小蓝',
      avatar: '/images/user/avatar.png',
      name: null,
      phone: null,
      sex: null,
      idNumber: null,
    })
    const session = useSessionStore()
    await session.restoreUser()
    expect(session.user).toEqual({ id: 7, username: '小蓝', avatar: '/images/user/avatar.png' })
    expect(session.profile?.account).toBe('customer')
    expect(getToken()).toBe('saved-token')
  })
})
