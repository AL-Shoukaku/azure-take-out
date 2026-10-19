export interface OrderNotification {
  type: 1 | 2
  orderId: number
  content: string
}

interface OrderSocketOptions {
  onMessage: (message: OrderNotification) => void
  onConnected?: () => void
  onDisconnected?: () => void
}

export function getOrderSocketURL(
  baseURL = window.location.href,
  clientId: string = crypto.randomUUID(),
): string {
  const url = new URL(`/ws/${clientId}`, baseURL)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.toString()
}

function parseNotification(data: string): OrderNotification | null {
  let value: unknown
  try {
    value = JSON.parse(data)
  } catch {
    return null
  }
  if (typeof value !== 'object' || value === null) return null
  const message = value as Record<string, unknown>
  if (
    (message.type !== 1 && message.type !== 2) ||
    typeof message.orderId !== 'number' ||
    typeof message.content !== 'string'
  )
    return null
  return { type: message.type, orderId: message.orderId, content: message.content }
}

export function connectOrderSocket(options: OrderSocketOptions): () => void {
  let socket: WebSocket
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined
  let disposed = false
  const url = getOrderSocketURL()

  function connect(): void {
    socket = new WebSocket(url)
    socket.onopen = () => options.onConnected?.()
    socket.onmessage = (event: MessageEvent<string>) => {
      const message = parseNotification(event.data)
      if (message) options.onMessage(message)
    }
    socket.onclose = () => {
      if (disposed) return
      options.onDisconnected?.()
      reconnectTimer = setTimeout(connect, 3000)
    }
  }

  connect()

  return () => {
    disposed = true
    clearTimeout(reconnectTimer)
    socket.onopen = null
    socket.onmessage = null
    socket.onclose = null
    socket.close()
  }
}
