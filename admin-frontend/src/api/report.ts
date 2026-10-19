import { http, request } from '@/utils/request'
import type { ApiResult } from '@/types/api'

export interface ReportRange {
  begin: string
  end: string
}
export interface TurnoverReport {
  dateList: string
  turnoverList: string
}
export interface UserReport {
  dateList: string
  totalUserList: string
  newUserList: string
}
export interface OrderReport {
  dateList: string
  orderCountList: string
  validOrderCountList: string
  totalOrderCount: number
  validOrderCount: number
  orderCompletionRate: number
}
export interface SalesTop10Report {
  nameList: string
  numberList: string
}

export function getTurnoverReport(params: ReportRange): Promise<TurnoverReport> {
  return request({ url: '/report/turnoverStatistics', method: 'GET', params })
}
export function getUserReport(params: ReportRange): Promise<UserReport> {
  return request({ url: '/report/userStatistics', method: 'GET', params })
}
export function getOrderReport(params: ReportRange): Promise<OrderReport> {
  return request({ url: '/report/ordersStatistics', method: 'GET', params })
}
export function getSalesTop10(params: ReportRange): Promise<SalesTop10Report> {
  return request({ url: '/report/top10', method: 'GET', params })
}
export async function exportBusinessReport(): Promise<Blob> {
  // 后端固定生成截至昨日的近 30 天报表，不接收日期参数，也未设置下载文件名。
  const { data } = await http.get<Blob>('/report/export', {
    responseType: 'blob',
    timeout: 60_000,
  })
  if (data.type.includes('json')) {
    const result = JSON.parse(await data.text()) as ApiResult<null>
    throw new Error(result.msg || '报表导出失败，请稍后重试')
  }
  return data
}
