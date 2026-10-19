export type ApiResult<T> =
  { code: 1; msg: string | null; data: T } | { code: 0; msg: string; data: null }

export interface PageResult<T> {
  total: number
  records: T[]
}
