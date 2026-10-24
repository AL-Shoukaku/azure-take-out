const TOKEN_COOKIE_NAME = 'user_token'

export function getToken(): string {
  const prefix = `${TOKEN_COOKIE_NAME}=`
  const cookie = document.cookie.split(';').find((item) => item.trim().startsWith(prefix))
  return cookie ? decodeURIComponent(cookie.trim().slice(prefix.length)) : ''
}

export function setToken(token: string): void {
  // 与后端 application.yml 中的 user-ttl（7200000 毫秒）一致。
  const secure = window.location.protocol === 'https:' ? '; Secure' : ''
  document.cookie = `${TOKEN_COOKIE_NAME}=${encodeURIComponent(token)}; Path=/; Max-Age=7200; SameSite=Lax${secure}`
}

export function removeToken(): void {
  document.cookie = `${TOKEN_COOKIE_NAME}=; Path=/; Max-Age=0; SameSite=Lax`
}
