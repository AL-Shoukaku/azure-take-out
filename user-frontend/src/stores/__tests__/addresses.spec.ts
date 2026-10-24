import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAddressesStore } from '../addresses'
import { useSessionStore } from '../session'
import { getAddresses, setDefaultAddress, deleteAddress, type Address } from '@/api/addresses'
import { removeToken } from '@/utils/auth'

vi.mock('@/api/addresses', () => ({
  getAddresses: vi.fn<typeof getAddresses>(),
  setDefaultAddress: vi.fn<typeof setDefaultAddress>(),
  deleteAddress: vi.fn<typeof deleteAddress>(),
}))

const home = { id: 1, detail: '家', isDefault: 1 } as Address
const school = { id: 2, detail: '学校', isDefault: 0 } as Address
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((complete) => {
    resolve = complete
  })
  return { promise, resolve }
}
beforeEach(() => {
  setActivePinia(createPinia())
  vi.resetAllMocks()
  useSessionStore().startSession({ id: 7, username: '小蓝', avatar: null, token: 'token-A' })
  vi.mocked(getAddresses).mockResolvedValue([home, school])
})
afterEach(removeToken)

describe('地址列表及配送选择', () => {
  it('首次选中默认地址，手动选择不修改默认状态且刷新后保留', async () => {
    const store = useAddressesStore()
    await store.load()
    expect(store.selectedId).toBe(1)
    store.select(2)
    await store.load()
    expect(store.selectedId).toBe(2)
    expect(store.addresses[0]?.isDefault).toBe(1)
    expect(setDefaultAddress).not.toHaveBeenCalled()
  })

  it('没有默认地址时保持未选中', async () => {
    vi.mocked(getAddresses).mockResolvedValue([school])
    const store = useAddressesStore()
    await store.load()
    expect(store.selectedId).toBeNull()
  })

  it('设默认成功后同步标记并立即选中，旧列表响应不能撤销结果', async () => {
    const store = useAddressesStore()
    await store.load()
    const old = deferred<Address[]>()
    vi.mocked(getAddresses).mockReturnValueOnce(old.promise)
    const pending = store.load()
    await store.makeDefault(2)
    old.resolve([home, school])
    await pending
    expect(store.selectedId).toBe(2)
    expect(store.addresses.map((a) => a.isDefault)).toEqual([0, 1])
  })

  it('删除选中的地址后回退到默认地址，删除默认地址后不擅自指定默认', async () => {
    const store = useAddressesStore()
    await store.load()
    store.select(2)
    await store.remove(2)
    expect(store.selectedId).toBe(1)
    await store.remove(1)
    expect(store.addresses).toEqual([])
    expect(store.selectedId).toBeNull()
  })

  it('写入失败保持列表和选择，允许重试', async () => {
    const store = useAddressesStore()
    await store.load()
    vi.mocked(setDefaultAddress).mockRejectedValueOnce(new Error('设置失败'))
    await expect(store.makeDefault(2)).rejects.toThrow('设置失败')
    vi.mocked(deleteAddress).mockRejectedValueOnce(new Error('删除失败'))
    await expect(store.remove(1)).rejects.toThrow('删除失败')
    expect(store.selectedId).toBe(1)
    expect(store.addresses).toEqual([home, school])
  })

  it('切换会话清空地址和选择，旧读取及旧操作均不能写入新会话', async () => {
    const store = useAddressesStore()
    await store.load()
    const read = deferred<Address[]>()
    const write = deferred<null>()
    vi.mocked(getAddresses).mockReturnValueOnce(read.promise)
    vi.mocked(setDefaultAddress).mockReturnValueOnce(write.promise)
    const pendingRead = store.load()
    const pendingWrite = store.makeDefault(2)
    useSessionStore().clearSession()
    useSessionStore().startSession({ id: 8, username: '用户 B', avatar: null, token: 'token-B' })
    read.resolve([home, school])
    write.resolve(null)
    await Promise.all([pendingRead, pendingWrite])
    expect(store.addresses).toEqual([])
    expect(store.selectedId).toBeNull()
    expect(store.loading).toBe(false)
  })

  it('保存后的重新读取先完成时，迟到的旧读取不能覆盖新资料', async () => {
    const store = useAddressesStore()
    const old = deferred<Address[]>()
    vi.mocked(getAddresses).mockReturnValueOnce(old.promise)
    const pending = store.load()
    vi.mocked(getAddresses).mockResolvedValueOnce([{ ...home, detail: '新地址' }])
    await store.load()
    old.resolve([home])
    await pending
    expect(store.addresses[0]?.detail).toBe('新地址')
  })
})
