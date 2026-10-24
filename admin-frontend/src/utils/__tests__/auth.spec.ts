import { afterEach, describe, expect, it } from 'vitest'
import { getToken, removeToken, setToken } from '../auth'

afterEach(() => {
  for (const name of ['admin_token', 'user_token', 'token'])
    document.cookie = `${name}=; Path=/; Max-Age=0`
})

describe('管理端 Cookie 隔离', () => {
  it('只读取管理端凭证，忽略用户端及旧的同名凭证', () => {
    document.cookie = 'user_token=user-session; Path=/'
    document.cookie = 'token=legacy-session; Path=/'
    expect(getToken()).toBe('')
    document.cookie = 'admin_token=admin-session; Path=/'
    expect(getToken()).toBe('admin-session')
  })

  it('登录或更新管理端凭证不覆盖用户端凭证', () => {
    document.cookie = 'user_token=user-session; Path=/'
    setToken('admin-session')
    expect(document.cookie).toContain('admin_token=admin-session')
    setToken('new-admin-session')
    expect(getToken()).toBe('new-admin-session')
    expect(document.cookie).toContain('user_token=user-session')
  })

  it('退出管理端只清除管理端凭证', () => {
    document.cookie = 'user_token=user-session; Path=/'
    setToken('admin-session')
    removeToken()
    expect(getToken()).toBe('')
    expect(document.cookie).not.toContain('admin_token=')
    expect(document.cookie).toContain('user_token=user-session')
  })
})
