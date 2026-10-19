import { describe, expect, it } from 'vitest'
import { getReportRange } from '../dateRange'

describe('统计日期范围', () => {
  const sunday = new Date(2026, 9, 18, 15, 30)
  it.each([
    ['yesterday', '2026-10-17', '2026-10-17'],
    ['last7', '2026-10-11', '2026-10-17'],
    ['last30', '2026-09-18', '2026-10-17'],
    ['week', '2026-10-12', '2026-10-18'],
    ['month', '2026-10-01', '2026-10-31'],
  ] as const)('%s 按本地日期生成完整范围', (preset, begin, end) => {
    expect(getReportRange(preset, sunday)).toEqual({ begin, end })
  })
  it('本周从周一到周日，跨年时也不跳到下一周', () => {
    expect(getReportRange('week', new Date(2023, 0, 1))).toEqual({
      begin: '2022-12-26',
      end: '2023-01-01',
    })
    expect(getReportRange('week', new Date(2026, 9, 19))).toEqual({
      begin: '2026-10-19',
      end: '2026-10-25',
    })
  })
  it('正确处理闰月月末和跨年近 30 日', () => {
    expect(getReportRange('month', new Date(2024, 1, 15))).toEqual({
      begin: '2024-02-01',
      end: '2024-02-29',
    })
    expect(getReportRange('last30', new Date(2026, 0, 1))).toEqual({
      begin: '2025-12-02',
      end: '2025-12-31',
    })
  })
})
