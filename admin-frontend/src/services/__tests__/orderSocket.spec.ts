import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { connectOrderSocket, getOrderSocketURL, type OrderNotification } from '../orderSocket'

class FakeWebSocket {
  static instances: FakeWebSocket[] = []
  onopen: (() => void) | null = null
  onmessage: ((event: MessageEvent<string>) => void) | null = null
  onerror: (() => void) | null = null
  onclose: (() => void) | null = null
  close = vi.fn<() => void>()

  constructor(public url: string) {
    FakeWebSocket.instances.push(this)
  }
}

let dispose: (() => void) | undefined

beforeEach(() => {
  vi.useFakeTimers()
  FakeWebSocket.instances = []
  vi.stubGlobal('WebSocket', FakeWebSocket)
})

afterEach(() => {
  dispose?.()
  dispose = undefined
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('订单消息连接', () => {
  it('使用后端 /ws/{sid} 地址，HTTPS 页面使用 wss', () => {
    expect(getOrderSocketURL('https://example.com/dashboard', 'client-id')).toBe(
      'wss://example.com/ws/client-id',
    )
    expect(getOrderSocketURL('http://localhost:5173/order', 'client-id')).toBe(
      'ws://localhost:5173/ws/client-id',
    )
  })

  it('分别接收来单和催单消息，忽略非业务消息及无效 JSON', () => {
    const onMessage = vi.fn<(message: OrderNotification) => void>()
    dispose = connectOrderSocket({ onMessage })
    const socket = FakeWebSocket.instances[0]!
    for (const data of ['ping', 'null', '{"type":3}', '{"type":1}', '{"type":2,"orderId":1}']) {
      socket.onmessage?.(new MessageEvent('message', { data }))
    }
    const incoming: OrderNotification = { type: 1, orderId: 101, content: '订单号 20261018001' }
    const reminder: OrderNotification = { type: 2, orderId: 102, content: '订单号 20261018002' }
    socket.onmessage?.(new MessageEvent('message', { data: JSON.stringify(incoming) }))
    socket.onmessage?.(new MessageEvent('message', { data: JSON.stringify(reminder) }))
    expect(onMessage.mock.calls).toEqual([[incoming], [reminder]])
  })

  it('连接中断后重连，并在成功恢复时通知调用方', () => {
    const onDisconnected = vi.fn<() => void>()
    const onConnected = vi.fn<() => void>()
    dispose = connectOrderSocket({
      onMessage: vi.fn<(message: OrderNotification) => void>(),
      onDisconnected,
      onConnected,
    })
    FakeWebSocket.instances[0]!.onclose?.()
    expect(onDisconnected).toHaveBeenCalledOnce()
    vi.advanceTimersByTime(3000)
    expect(FakeWebSocket.instances).toHaveLength(2)
    FakeWebSocket.instances[1]!.onopen?.()
    expect(onConnected).toHaveBeenCalledOnce()
  })

  it('离开后台时关闭连接，取消重连并移除消息回调', () => {
    const onMessage = vi.fn<(message: OrderNotification) => void>()
    dispose = connectOrderSocket({ onMessage })
    const socket = FakeWebSocket.instances[0]!
    socket.onclose?.()
    dispose()
    expect(socket.close).toHaveBeenCalledOnce()
    expect(socket.onmessage).toBeNull()
    vi.advanceTimersByTime(10_000)
    expect(FakeWebSocket.instances).toHaveLength(1)
    expect(onMessage).not.toHaveBeenCalled()
    dispose = undefined
  })
})
