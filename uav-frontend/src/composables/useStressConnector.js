/**
 * WebSocket 压力测试连接器
 * 连接 Go ws-stress 服务，接收批量无人机数据，喂给 LOD 渲染器
 */

import * as Cesium from 'cesium'
import { ref } from 'vue'

export function useStressConnector(viewerRef, renderer) {
  const connected = ref(false)
  const droneCount = ref(0)
  const fps = ref(0)
  let ws = null
  let frameCount = 0
  let lastFpsTime = 0

  // 查询参数
  const urlParams = new URLSearchParams(window.location.search)
  const stressCount = parseInt(urlParams.get('stress') || '0')

  function connect(host = 'localhost', port = 8099) {
    if (ws) disconnect()

    const url = `ws://${host}:${port}/stress`
    console.log(`[StressConnector] 连接 ${url} ...`)
    ws = new WebSocket(url)

    ws.onopen = () => {
      connected.value = true
      console.log('[StressConnector] ✅ 已连接')
    }

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data)
        if (!msg.p || !Array.isArray(msg.p)) return

        const batch = msg.p
        droneCount.value = msg.c || batch.length

        // 将紧凑格式转为 Cartesian3[]
        const positions = new Array(batch.length)
        for (let i = 0; i < batch.length; i++) {
          const [lon, lat, alt, heading, alertCode] = batch[i]
          positions[i] = Cesium.Cartesian3.fromDegrees(lon, lat, alt)
        }

        // 喂给 LOD 渲染器
        if (renderer && renderer.updatePositions) {
          renderer.updatePositions(positions)
        }

        // 统计 FPS
        frameCount++
        const now = Date.now()
        if (now - lastFpsTime > 1000) {
          fps.value = Math.round(frameCount * 1000 / (now - lastFpsTime))
          frameCount = 0
          lastFpsTime = now
        }
      } catch (e) {
        // 忽略解析错误
      }
    }

    ws.onclose = () => {
      connected.value = false
      console.log('[StressConnector] 🔌 断开')
      // 3秒后自动重连
      setTimeout(() => connect(host, port), 3000)
    }

    ws.onerror = () => {}
  }

  function disconnect() {
    if (ws) {
      ws.close()
      ws = null
    }
    connected.value = false
  }

  // 自动连接（如果 URL 有 ?stress=xxx 参数）
  function autoConnect() {
    if (stressCount > 0) {
      console.log(`[StressConnector] 检测到 ?stress=${stressCount}，自动连接压力测试`)
      connect()
    }
  }

  return {
    connected,
    droneCount,
    fps,
    connect,
    disconnect,
    autoConnect,
    stressCount,
  }
}
