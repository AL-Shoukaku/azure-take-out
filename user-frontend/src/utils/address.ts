export const addressLabels = ['家', '公司', '学校', '其它'] as const

export function addressLabel(value: string | null): string {
  // 原小程序用数字保存标签；新用户端统一显示中文标签。
  const legacyLabels: Record<string, string> = { '1': '公司', '2': '家', '3': '学校' }
  if (value && legacyLabels[value]) return legacyLabels[value]
  return addressLabels.find((label) => label === value) ?? '其它'
}
