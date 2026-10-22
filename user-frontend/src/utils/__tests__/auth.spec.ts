import { afterEach, describe, expect, it } from 'vitest'
import { getToken, removeToken, setToken } from '../auth'

afterEach(() => {
  for (const name of ['admin_token', 'user_token', 'token'])
    document.cookie = `${name}=; Path=/; Max-Age=0`
})

describe('用户端 Cookie 隔离', () => {
  it('只读取用户端凭证，忽略管理端及旧的同名凭证', () => {
    document.cookie = 'admin_token=admin-session; Path=/'
    document.cookie = 'token=legacy-session; Path=/'
    expect(getToken()).toBe('')
    document.cookie = 'user_token=user-session; Path=/'
    expect(getToken()).toBe('user-session')
  })

  it('登录或更新用户端凭证不覆盖管理端凭证', () => {
    document.cookie = 'admin_token=admin-session; Path=/'
    setToken('user-session')
    expect(document.cookie).toContain('user_token=user-session')
    setToken('new-user-session')
    expect(getToken()).toBe('new-user-session')
    expect(document.cookie).toContain('admin_token=admin-session')
  })

  it('退出用户端只清除用户端凭证', () => {
    document.cookie = 'admin_token=admin-session; Path=/'
    setToken('user-session')
    removeToken()
    expect(getToken()).toBe('')
    expect(document.cookie).not.toContain('user_token=')
    expect(document.cookie).toContain('admin_token=admin-session')
  })
})
