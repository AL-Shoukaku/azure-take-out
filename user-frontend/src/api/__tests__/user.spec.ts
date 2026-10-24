import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter } from 'axios'
import { editPassword, logout } from '../auth'
import { getUserInfo, updateUserInfo, uploadAvatar } from '../user'
import { http } from '@/utils/request'

const originalAdapter = http.defaults.adapter
afterEach(() => {
  http.defaults.adapter = originalAdapter
})

function respondWith(data: unknown): AxiosAdapter {
  return async (config) => ({
    data: { code: 1, msg: null, data },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  })
}

describe('个人资料、头像和账号安全接口', () => {
  it('读取当前用户的 ID、账号及完整资料', async () => {
    const profile = {
      id: 7,
      account: 'customer',
      username: '小蓝',
      name: '',
      sex: '',
      phone: '',
      idNumber: '',
      avatar: null,
    }
    http.defaults.adapter = respondWith(profile)
    await expect(getUserInfo()).resolves.toEqual(profile)
  })

  it('保存资料时带上当前用户 ID，并以空字符串清空选填资料', async () => {
    const adapter = vi.fn<AxiosAdapter>(respondWith(null))
    http.defaults.adapter = adapter
    const data = {
      id: 7,
      username: '新昵称',
      name: '',
      sex: '',
      phone: '',
      idNumber: '',
      avatar: '',
    }
    await updateUserInfo(data)
    const config = adapter.mock.calls[0]![0]
    expect(config.url).toBe('/user/update')
    expect(config.method).toBe('put')
    expect(JSON.parse(config.data as string)).toEqual(data)
    expect(JSON.parse(config.data as string)).not.toHaveProperty('account')
  })

  it('通过 multipart 的 file 字段上传头像并读取返回的 URL', async () => {
    const adapter = vi.fn<AxiosAdapter>(respondWith('/images/user/avatar.png'))
    http.defaults.adapter = adapter
    const file = new File(['png'], 'avatar.png', { type: 'image/png' })
    await expect(uploadAvatar(file)).resolves.toBe('/images/user/avatar.png')
    const config = adapter.mock.calls[0]![0]
    expect(config.url).toBe('/common/upload')
    expect(config.method).toBe('post')
    expect((config.data as FormData).get('file')).toBe(file)
  })

  it('修改密码只提交旧密码和新密码，不提交确认密码', async () => {
    const adapter = vi.fn<AxiosAdapter>(respondWith(null))
    http.defaults.adapter = adapter
    const data = { oldPassword: 'OldPass123', newPassword: 'NewPass456' }
    await editPassword(data)
    const config = adapter.mock.calls[0]![0]
    expect(config.url).toBe('/user/editPassword')
    expect(config.method).toBe('put')
    expect(JSON.parse(config.data as string)).toEqual(data)
  })

  it('调用当前用户的退出登录接口', async () => {
    const adapter = vi.fn<AxiosAdapter>(respondWith(null))
    http.defaults.adapter = adapter
    await logout()
    expect(adapter.mock.calls[0]![0].url).toBe('/user/logout')
    expect(adapter.mock.calls[0]![0].method).toBe('post')
  })
})
