/**
 * WebSocket 工具 — 连接 seckill-service 的实时推送
 *
 * 用法：
 *   const ws = createSeckillWs(activityId, {
 *     onMessage(data) { ... },
 *     onError(err) { ... }
 *   })
 *   ws.close() // 页面离开时断开
 */

type WsCallbacks = {
  onMessage?: (data: any) => void
  onError?: (err: Event) => void
}

export function createSeckillWs(activityId: number, callbacks: WsCallbacks): WebSocket {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  const token = localStorage.getItem('token') || ''
  const userId = localStorage.getItem('userId') || '0'
  const ws = new WebSocket(`${protocol}//${host}/ws/seckill?userId=${userId}&token=${token}`)

  ws.onopen = () => {
    ws.send(JSON.stringify({ type: 'subscribe', activityId }))
  }

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      callbacks.onMessage?.(data)
    } catch {
      callbacks.onMessage?.(event.data)
    }
  }

  ws.onerror = (err) => {
    console.warn('WebSocket error:', err)
    callbacks.onError?.(err)
  }

  ws.onclose = () => {
    // silent
  }

  return ws
}
