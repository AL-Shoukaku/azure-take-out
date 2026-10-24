import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import LoginPage from '../LoginPage.vue'
import { login, register } from '@/api/auth'
import { createAppRouter } from '@/router'
import { getToken, removeToken } from '@/utils/auth'

vi.mock('@/api/auth', () => ({ login: vi.fn<typeof login>(), register: vi.fn<typeof register>() }))
vi.mock('@/layouts/UserLayout.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/views/menu/MenuPage.vue', () => ({ default: { template: '<div />' } }))

let wrapper: VueWrapper
beforeEach(() => {
  vi.mocked(login).mockReset()
  vi.mocked(register).mockReset()
})
afterEach(() => {
  wrapper.unmount()
  ElMessage.closeAll()
  removeToken()
})

async function renderPage() {
  const router = createAppRouter(createMemoryHistory())
  await router.push('/login')
  wrapper = mount(LoginPage, {
    global: { plugins: [createPinia(), router] },
    attachTo: document.body,
  })
  return router
}

async function fillCredentials(password = 'abc123') {
  await wrapper.get('input[name="account"]').setValue(' customer ')
  await wrapper.get('input[name="password"]').setValue(password)
}

describe('登录与注册表单', () => {
  it('展示管理端 logo、欢迎标语和登录选项卡', async () => {
    await renderPage()
    expect(wrapper.get('.login-logo').attributes('alt')).toBe('碧蓝外卖')
    expect(wrapper.get('h1').text()).toBe('欢迎回来')
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('登录')
    expect(wrapper.get('input[name="account"]').attributes('maxlength')).toBe('32')
    expect(wrapper.find('input[name="confirmPassword"]').exists()).toBe(false)
  })

  it('空表单不能提交，显示字段校验提示', async () => {
    await renderPage()
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(login).not.toHaveBeenCalled()
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('请输入账号')
      expect(wrapper.text()).toContain('请输入密码')
    })
  })

  it.each([
    ['ab', 'abc123', '账号长度为 3–32 位，只能包含字母和数字，且以字母开头'],
    ['1customer', 'abc123', '账号长度为 3–32 位，只能包含字母和数字，且以字母开头'],
    ['customer_', 'abc123', '账号长度为 3–32 位，只能包含字母和数字，且以字母开头'],
    ['a'.repeat(33), 'abc123', '账号长度为 3–32 位，只能包含字母和数字，且以字母开头'],
    ['customer', 'abc12', '密码至少 6 位，且只能包含字母和数字'],
    ['customer', 'abc123!', '密码至少 6 位，且只能包含字母和数字'],
    ['customer', ' abc123', '密码至少 6 位，且只能包含字母和数字'],
  ])('阻止不符合规则的账号或密码：%s / %s', async (account, password, message) => {
    await renderPage()
    await wrapper.get('input[name="account"]').setValue(account)
    await wrapper.get('input[name="password"]').setValue(password)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(login).not.toHaveBeenCalled()
    await vi.waitFor(() => expect(wrapper.text()).toContain(message))
  })

  it('允许 32 位账号登录，去除两端空格后写入 Cookie 并进入点餐页', async () => {
    const router = await renderPage()
    vi.mocked(login).mockResolvedValue({
      id: 7,
      username: '用户7',
      avatar: null,
      token: 'new-token',
    })
    await fillCredentials()
    const account = 'a'.repeat(32)
    await wrapper.get('input[name="account"]').setValue(` ${account} `)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(login).toHaveBeenCalledWith({ account, password: 'abc123' })
    expect(getToken()).toBe('new-token')
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/menu'))
  })

  it('切换注册时更新标语，确认密码不一致时阻止注册', async () => {
    await renderPage()
    await wrapper.get('#register-tab').trigger('click')
    expect(wrapper.get('h1').text()).toBe('欢迎加入碧蓝外卖')
    await fillCredentials()
    await wrapper.get('input[name="confirmPassword"]').setValue('abc456')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(register).not.toHaveBeenCalled()
    await vi.waitFor(() => expect(wrapper.text()).toContain('两次输入的密码不一致'))
  })

  it('阻止超过 32 位的账号注册，不请求接口', async () => {
    await renderPage()
    await wrapper.get('#register-tab').trigger('click')
    await fillCredentials()
    await wrapper.get('input[name="account"]').setValue('a'.repeat(33))
    await wrapper.get('input[name="confirmPassword"]').setValue('abc123')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(register).not.toHaveBeenCalled()
    await vi.waitFor(() =>
      expect(wrapper.text()).toContain('账号长度为 3–32 位，只能包含字母和数字，且以字母开头'),
    )
  })

  it('允许 32 位账号注册，成功后返回登录、保留账号并清空密码', async () => {
    const router = await renderPage()
    vi.mocked(register).mockResolvedValue(null)
    await wrapper.get('#register-tab').trigger('click')
    await fillCredentials()
    const account = 'a'.repeat(32)
    await wrapper.get('input[name="account"]').setValue(account)
    await wrapper.get('input[name="confirmPassword"]').setValue('abc123')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(register).toHaveBeenCalledWith({ account, password: 'abc123' })
    expect(wrapper.get('#login-tab').attributes('aria-selected')).toBe('true')
    expect((wrapper.get('input[name="account"]').element as HTMLInputElement).value).toBe(account)
    expect((wrapper.get('input[name="password"]').element as HTMLInputElement).value).toBe('')
    expect(getToken()).toBe('')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(document.body.textContent).toContain('注册成功，请登录')
  })

  it('登录失败后显示后端错误，并可再次提交', async () => {
    await renderPage()
    vi.mocked(login).mockRejectedValue(new Error('账号被锁定'))
    await fillCredentials()
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(document.body.textContent).toContain('账号被锁定')
    expect(getToken()).toBe('')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
  })

  it('提交期间禁止重复提交和切换模式', async () => {
    await renderPage()
    let complete!: (value: null) => void
    vi.mocked(register).mockReturnValue(
      new Promise((resolve) => {
        complete = resolve
      }),
    )
    await wrapper.get('#register-tab').trigger('click')
    await fillCredentials()
    await wrapper.get('input[name="confirmPassword"]').setValue('abc123')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    expect(register).toHaveBeenCalledOnce()
    expect(wrapper.get('#login-tab').attributes('disabled')).toBeDefined()
    complete(null)
    await flushPromises()
  })

  it('键盘方向键可切换选项卡并聚焦', async () => {
    await renderPage()
    await wrapper.get('#login-tab').trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.get('#register-tab').attributes('aria-selected')).toBe('true')
    expect(document.activeElement?.id).toBe('register-tab')
  })
})
