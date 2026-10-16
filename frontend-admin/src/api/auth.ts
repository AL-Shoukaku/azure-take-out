import axios from 'axios'

// 提交给后端的数据
interface LoginParams {
  username: string
  password: string
}

// 登录成功后返回的数据
interface LoginUser {
  id: number
  userName: string
  name: string
  token: string
}

// 后端统一响应结构
interface ApiResult<T> {
  code: number
  msg?: string | null
  data?: T | null
}

export async function login(params: LoginParams): Promise<LoginUser> {
  const response = await axios.post<ApiResult<LoginUser>>('/api/employee/login', params, {
    timeout: 10000,
  })

  const result = response.data

  if (result.code !== 1) {
    throw new Error(result.msg || '登录失败')
  }

  if (!result.data?.token) {
    throw new Error('登录响应缺少 token')
  }

  return result.data
}
