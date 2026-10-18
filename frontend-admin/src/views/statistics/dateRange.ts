import type { ReportRange } from '@/api/report'

export const reportPresets = [
  { value: 'yesterday', label: '昨日' },
  { value: 'last7', label: '近7日' },
  { value: 'last30', label: '近30日' },
  { value: 'week', label: '本周' },
  { value: 'month', label: '本月' },
] as const
export type ReportPreset = (typeof reportPresets)[number]['value']

function formatDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export function getReportRange(preset: ReportPreset, now = new Date()): ReportRange {
  const year = now.getFullYear()
  const month = now.getMonth()
  const day = now.getDate()
  let begin: Date
  let end: Date
  if (preset === 'month') {
    begin = new Date(year, month, 1)
    end = new Date(year, month + 1, 0)
  } else if (preset === 'week') {
    const monday = day - ((now.getDay() + 6) % 7)
    begin = new Date(year, month, monday)
    end = new Date(year, month, monday + 6)
  } else {
    const days = preset === 'last30' ? 30 : preset === 'last7' ? 7 : 1
    begin = new Date(year, month, day - days)
    end = new Date(year, month, day - 1)
  }
  return { begin: formatDate(begin), end: formatDate(end) }
}
