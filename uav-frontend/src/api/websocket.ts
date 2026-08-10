// WebSocket 连接管理
type MessageHandler = (type: string, data: any) => void

class WsClient {
  private ws: WebSocket | null = null
  private url: string
  private reconnectTimer: any = null
  private handlers: MessageHandler[] = []

  constructor(url: string) {
    this.url = url
  }

  connect() {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) return
    this.ws = new WebSocket(this.url)

    this.ws.onopen = () => {
      console.log('[WS] Connected')
    }

    this.ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data)
        const type = msg.type || 'unknown'
        const data = msg.data || msg
        this.handlers.forEach(h => h(type, data))
      } catch (e) {
        // ignore parse errors
      }
    }

    this.ws.onclose = () => {
      console.log('[WS] Disconnected, reconnecting in 3s...')
      this.reconnectTimer = setTimeout(() => this.connect(), 3000)
    }

    this.ws.onerror = () => {
      // onclose will handle reconnect
    }
  }

  onMessage(handler: MessageHandler) {
    this.handlers.push(handler)
  }

  disconnect() {
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer)
    if (this.ws) this.ws.close()
  }
}

// 单例：连接 uav-realtime WebSocket
const wsUrl = `ws://${location.hostname}:8081/ws`
export const wsClient = new WsClient(wsUrl)
