export function getToken(): string {
  const cookie = document.cookie.split(';').find((item) => item.trim().startsWith('token='))
  return cookie ? decodeURIComponent(cookie.trim().slice('token='.length)) : ''
}

export function setToken(token: string): void {
  // 与后端 application.yml 中的 admin-ttl（7200000 毫秒）保持一致。
  const secure = window.location.protocol === 'https:' ? '; Secure' : ''
  document.cookie = `token=${encodeURIComponent(token)}; Path=/; Max-Age=7200; SameSite=Lax${secure}`
}

export function removeToken(): void {
  document.cookie = 'token=; Path=/; Max-Age=0; SameSite=Lax'
}
