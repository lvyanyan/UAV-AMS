// WebSocket 连接管理（uav-realtime :8090/ws）
//
// v2 订阅协议：连接后可发送 subscribe 控制消息，由服务端做视锥裁剪与告警抑制过滤：
//   {
//     type: 'subscribe',
//     viewport: { minLat, maxLat, minLon, maxLon, camAlt },   // 视锥 bbox（省流量）
//     alarm: { minLevel, excludeLevels, excludeTypes, excludeSns, viewportScoped },
//     batch: true                                             // 遥测合帧 telemetry-batch
//   }
// 未订阅 = 全量广播（向后兼容）。断线重连后自动重发最近一次订阅条件。

type MessageHandler = (type: string, data: any) => void
type StatusHandler = (connected: boolean) => void

export interface Viewport {
  minLat: number
  maxLat: number
  minLon: number
  maxLon: number
  camAlt?: number
}

export interface AlarmSubscription {
  minLevel?: string
  excludeLevels?: string[]
  excludeTypes?: string[]
  excludeSns?: string[]
  viewportScoped?: boolean
}

export interface SubscribeOptions {
  viewport?: Viewport | null
  alarm?: AlarmSubscription
  batch?: boolean
}

class WsClient {
  private ws: WebSocket | null = null
  private url: string
  private reconnectTimer: any = null
  private handlers: MessageHandler[] = []
  private statusHandlers: StatusHandler[] = []
  private lastSub: SubscribeOptions | null = null
  connected = false
  everConnected = false   // 本会话是否建立过连接（区分"未启用实时链路"与"链路中断"）

  constructor(url: string) {
    this.url = url
  }

  connect() {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) return
    this.ws = new WebSocket(this.url)

    this.ws.onopen = () => {
      this.connected = true
      this.everConnected = true
      console.log('[WS] Connected:', this.url)
      // 重连/首连：重发最近一次订阅条件，保持服务端会话状态一致
      if (this.lastSub) this.subscribe(this.lastSub)
      this.statusHandlers.forEach(h => h(true))
    }

    this.ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data)
        const type = msg.type || 'unknown'
        const data = msg.data !== undefined ? msg.data : msg
        this.handlers.forEach(h => h(type, data))
      } catch (e) {
        // ignore parse errors
      }
    }

    this.ws.onclose = () => {
      const wasConnected = this.connected
      this.connected = false
      if (wasConnected) this.statusHandlers.forEach(h => h(false))
      console.log('[WS] Disconnected, reconnecting in 2s...')
      this.reconnectTimer = setTimeout(() => this.connect(), 2000)
    }

    this.ws.onerror = () => {
      // onclose will handle reconnect
    }
  }

  /** 声明/更新订阅条件（视锥裁剪 + 告警抑制 + 遥测合帧），断线重连后自动重发 */
  subscribe(opts: SubscribeOptions) {
    this.lastSub = { ...this.lastSub, ...opts }
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ type: 'subscribe', ...this.lastSub }))
    }
  }

  onMessage(handler: MessageHandler) {
    this.handlers.push(handler)
    return () => {
      const i = this.handlers.indexOf(handler)
      if (i >= 0) this.handlers.splice(i, 1)
    }
  }

  onStatus(handler: StatusHandler) {
    this.statusHandlers.push(handler)
    return () => {
      const i = this.statusHandlers.indexOf(handler)
      if (i >= 0) this.statusHandlers.splice(i, 1)
    }
  }

  disconnect() {
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer)
    this.ws?.close()
    this.ws = null
    this.connected = false
  }
}

// 单例：优先走 vite 代理（/ws → uav-realtime:8090），可用 VITE_WS_URL 覆盖（生产/nginx 场景）
const wsUrl = import.meta.env.VITE_WS_URL || `ws://${location.host}/ws`
export const wsClient = new WsClient(wsUrl)
