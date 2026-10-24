import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSessionStore } from '../session'
import { getToken, removeToken, setToken } from '@/utils/auth'
import { getUserInfo } from '@/api/user'
import type { UserInfoVO } from '@/api/user'

vi.mock('@/api/user', () => ({ getUserInfo: vi.fn<typeof getUserInfo>() }))

const profileA: UserInfoVO = {
  id: 7,
  account: 'customerA',
  username: '用户 A',
  avatar: null,
  name: null,
  phone: null,
  sex: null,
  idNumber: null,
}
const profileB: UserInfoVO = { ...profileA, id: 8, account: 'customerB', username: '用户 B' }

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((complete) => {
    resolve = complete
  })
  return { promise, resolve }
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.mocked(getUserInfo).mockReset()
})
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

  it('布局和个人资料页同时读取资料时共用请求，完成后仍可重新加载', async () => {
    const profile = {
      id: 7,
      account: 'customer',
      username: '小蓝',
      avatar: null,
      name: null,
      phone: null,
      sex: null,
      idNumber: null,
    }
    let complete!: (value: typeof profile) => void
    vi.mocked(getUserInfo).mockReturnValue(
      new Promise((resolve) => {
        complete = resolve
      }),
    )
    const session = useSessionStore()
    const first = session.restoreUser()
    const second = session.restoreUser()
    expect(getUserInfo).toHaveBeenCalledOnce()
    complete(profile)
    await Promise.all([first, second])
    expect(session.profile).toEqual(profile)
    vi.mocked(getUserInfo).mockResolvedValue({ ...profile, username: '新昵称' })
    await session.restoreUser()
    expect(getUserInfo).toHaveBeenCalledTimes(2)
    expect(session.user?.username).toBe('新昵称')
    session.clearSession()
    expect(session.profile).toBeNull()
  })

  it('退出后到达的旧资料不能恢复已清除的会话', async () => {
    const response = deferred<UserInfoVO>()
    vi.mocked(getUserInfo).mockReturnValue(response.promise)
    const session = useSessionStore()
    session.startSession({ ...profileA, token: 'token-A' })
    const pending = session.restoreUser()
    session.clearSession()
    response.resolve(profileA)
    await pending
    expect(session.user).toBeNull()
    expect(session.profile).toBeNull()
    expect(getToken()).toBe('')
  })

  it('切换账号后立即读取新账号资料，迟到的 A 响应不能覆盖 B', async () => {
    const responseA = deferred<UserInfoVO>()
    const responseB = deferred<UserInfoVO>()
    vi.mocked(getUserInfo)
      .mockReturnValueOnce(responseA.promise)
      .mockReturnValueOnce(responseB.promise)
    const session = useSessionStore()
    session.startSession({ ...profileA, token: 'token-A' })
    const pendingA = session.restoreUser()
    session.clearSession()
    session.startSession({ ...profileB, token: 'token-B' })
    const pendingB = session.restoreUser()
    expect(getUserInfo).toHaveBeenCalledTimes(2)
    responseB.resolve(profileB)
    await pendingB
    responseA.resolve(profileA)
    await pendingA
    expect(session.profile).toEqual(profileB)
    expect(session.user).toMatchObject({ id: 8, username: '用户 B' })
    expect(getToken()).toBe('token-B')
  })

  it('重新开始会话时，即使 token 相同也不接受上一次会话的响应', async () => {
    const response = deferred<UserInfoVO>()
    vi.mocked(getUserInfo).mockReturnValue(response.promise)
    const session = useSessionStore()
    session.startSession({ ...profileA, token: 'same-token' })
    const pending = session.restoreUser()
    session.startSession({ ...profileA, username: '重新登录的昵称', token: 'same-token' })
    response.resolve(profileA)
    await pending
    expect(session.user?.username).toBe('重新登录的昵称')
    expect(session.profile).toBeNull()
  })

  it('Cookie 中的凭证变化后，不接受旧凭证对应的资料', async () => {
    const response = deferred<UserInfoVO>()
    vi.mocked(getUserInfo).mockReturnValue(response.promise)
    const session = useSessionStore()
    session.startSession({ ...profileA, token: 'token-A' })
    const pending = session.restoreUser()
    setToken('different-token')
    response.resolve(profileA)
    await pending
    expect(session.profile).toBeNull()
    expect(getToken()).toBe('different-token')
  })

  it('保存成功后，旧资料响应不能覆盖新昵称和头像', async () => {
    const response = deferred<UserInfoVO>()
    vi.mocked(getUserInfo).mockReturnValue(response.promise)
    const session = useSessionStore()
    session.startSession({ ...profileA, token: 'token-A' })
    const pending = session.restoreUser()
    const saved = { ...profileA, username: '保存后的昵称', avatar: '/images/user/new.png' }
    session.updateProfile(saved)
    response.resolve(profileA)
    await pending
    expect(session.profile).toEqual(saved)
    expect(session.user).toMatchObject({ username: saved.username, avatar: saved.avatar })
  })

  it('旧请求结束时不能清除保存后新建的请求，相同版本的读取仍共用请求', async () => {
    const oldResponse = deferred<UserInfoVO>()
    const newResponse = deferred<UserInfoVO>()
    vi.mocked(getUserInfo)
      .mockReturnValueOnce(oldResponse.promise)
      .mockReturnValueOnce(newResponse.promise)
    const session = useSessionStore()
    session.startSession({ ...profileA, token: 'token-A' })
    const oldPending = session.restoreUser()
    const saved = { ...profileA, username: '保存后的昵称' }
    session.updateProfile(saved)
    const newPending = session.restoreUser()
    expect(getUserInfo).toHaveBeenCalledTimes(2)
    oldResponse.resolve(profileA)
    await oldPending
    const sharedPending = session.restoreUser()
    expect(getUserInfo).toHaveBeenCalledTimes(2)
    newResponse.resolve(saved)
    await Promise.all([newPending, sharedPending])
    expect(session.profile).toEqual(saved)
  })
})
