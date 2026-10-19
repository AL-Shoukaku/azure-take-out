import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { ElMessage } from 'element-plus'
import PasswordDialog from '../PasswordDialog.vue'
import { editPassword } from '@/api/auth'

vi.mock('@/api/auth', () => ({
  editPassword: vi.fn<(payload: { oldPassword: string; newPassword: string }) => Promise<null>>(),
}))

let wrapper: VueWrapper | undefined

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  ElMessage.closeAll()
  vi.resetAllMocks()
})

describe('修改密码弹窗', () => {
  it('保存期间阻止重复请求，成功后关闭弹窗', async () => {
    let finish!: () => void
    vi.mocked(editPassword).mockImplementation(
      () =>
        new Promise<null>((resolve) => {
          finish = () => resolve(null)
        }),
    )
    wrapper = mount(PasswordDialog, {
      props: { modelValue: true },
      global: { stubs: { teleport: true } },
    })
    await flushPromises()
    await wrapper.get('input[aria-label="原始密码"]').setValue('old$ password')
    await wrapper.get('input[aria-label="新密码"]').setValue('NewPass123')
    await wrapper.get('input[aria-label="确认密码"]').setValue('NewPass123')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    expect(editPassword).toHaveBeenCalledExactlyOnceWith({
      oldPassword: 'old$ password',
      newPassword: 'NewPass123',
    })
    expect(
      wrapper
        .findAll('button')
        .filter((button) => button.text() === '取消')[0]!
        .attributes('disabled'),
    ).toBeDefined()
    finish()
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')).toContainEqual([false])
  })

  it('取消后重新打开会清空密码和校验状态', async () => {
    wrapper = mount(PasswordDialog, {
      props: { modelValue: true },
      global: { stubs: { teleport: true } },
    })
    await flushPromises()
    await wrapper.get('input[aria-label="原始密码"]').setValue('private-password')
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    expect((wrapper.get('input[aria-label="原始密码"]').element as HTMLInputElement).value).toBe('')
    expect(wrapper.find('.el-form-item__error').exists()).toBe(false)
  })
})
