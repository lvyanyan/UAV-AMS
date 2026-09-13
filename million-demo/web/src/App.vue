<template>
  <div class="app">
    <div ref="cesiumContainer" class="cesium-container" />
    <ControlPanel
      :connected="connected"
      :drone-count="droneCount"
      :drawn-count="drawnCount"
      :fps="fps"
      :dropped="dropped"
      :debug-fps="debugFps"
      :mode="mode"
      :full="fullMode"
      @reconnect="reconnect"
      @resize="onResizeHint"
      @set-decimate="onSetDecimate"
      @toggle-debug-fps="onToggleDebugFps"
      @set-full="onSetFull"
    />
    <div v-if="dropped > 0" class="drop-overlay">
      丢弃过时帧 {{ dropped }}（Worker/渲染跟不上）
    </div>
  </div>
</template>

<script setup>
import { ref, shallowRef, onMounted, onUnmounted } from 'vue'
import * as Cesium from 'cesium'
import { useCesium } from './composables/useCesium.js'
import { useLodRenderer } from './composables/useLodRenderer.js'
import { useBoundary } from './composables/useBoundary.js'
import ControlPanel from './components/ControlPanel.vue'

// 同源动态拼接：本地 dev 连 5173（vite 代理 /stress），线上经 Caddy 反代 /stress
const WS_URL = (location.protocol === 'https:' ? 'wss' : 'ws') + '://' + location.host + '/stress'

const cesiumContainer = ref(null)
const viewerRef = shallowRef(null)

const useC = useCesium(viewerRef)
const renderer = useLodRenderer(viewerRef)
const boundary = useBoundary(viewerRef)

const connected = ref(false)
const droneCount = ref(0)
const drawnCount = ref(0)
const fullMode = ref(false)
const fps = ref(0)
const dropped = ref(0)
const debugFps = ref(true)
const mode = ref('--')
let _lastVpSend = 0

let ws = null
let worker = null
let workerBusy = false       // 同一时刻只让 Worker 处理一帧，丢弃来的新帧
let pending = null          // Worker 回传、待 rAF 消费的最新一帧
let rafId = 0
let reconnectTimer = 0
let frameCount = 0
let lastFpsTime = 0
let decimate = 1
let _camListener = null
let _clickHandler = null
let _labelEntity = null

// ── Worker：二进制解析 + ECEF 转换 ──
function startWorker() {
  worker = new Worker(new URL('./worker/telemetry.worker.js', import.meta.url), { type: 'module' })
  worker.onmessage = (e) => {
    workerBusy = false
    pending = e.data // 只保留最新，旧的被覆盖即丢弃
  }
}

// ── WebSocket：收 ArrayBuffer，transfer 给 Worker ──
function connect() {
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return
  ws = new WebSocket(WS_URL)
  ws.binaryType = 'arraybuffer'
  ws.onopen = () => {
    connected.value = true
    mode.value = 'live'
    reconnectTimer = 0
    updateViewport()
  }
  ws.onclose = () => {
    connected.value = false
    mode.value = 'disconnected'
    scheduleReconnect()
  }
  ws.onerror = () => ws && ws.close()
  ws.onmessage = (e) => {
    if (workerBusy) {
      dropped.value++
      return
    }
    workerBusy = true
    worker.postMessage(e.data, [e.data])
  }
}

function scheduleReconnect() {
  if (reconnectTimer) return
  reconnectTimer = setTimeout(() => { reconnectTimer = 0; connect() }, 3000)
}

function reconnect() {
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = 0 }
  if (ws) try { ws.close() } catch (e) {}
  connect()
}

// ── Camera 变动 → 更新 Viewport ──
let _lastVp = ''

function updateViewport() {
  const v = viewerRef.value
  if (!v) return
  const cam = v.camera
  const rect = cam.computeViewRectangle(v.scene.globe.ellipsoid)
  if (!rect) return
  // 服务端 Viewport 协议：{h, minLat, maxLat, minLon, maxLon, full}
  _lastVp = JSON.stringify({
    h: Math.round(cam.positionCartographic.height),
    minLat: +Cesium.Math.toDegrees(rect.south).toFixed(5),
    maxLat: +Cesium.Math.toDegrees(rect.north).toFixed(5),
    minLon: +Cesium.Math.toDegrees(rect.west).toFixed(5),
    maxLon: +Cesium.Math.toDegrees(rect.east).toFixed(5),
    full: fullMode.value
  })
  const now = Date.now()
  if (ws && ws.readyState === WebSocket.OPEN && now - _lastVpSend > 200) {
    _lastVpSend = now
    ws.send(_lastVp)
  }
}

// ── 外部 Event：切换 聚合/全量 推流模式 ──
function onSetFull(v) {
  fullMode.value = !!v
  _lastVpSend = 0
  updateViewport()
}

// ── 主渲染循环 ──
function renderLoop() {
  rafId = requestAnimationFrame(renderLoop)
  const v = viewerRef.value
  if (!v) return

  // FPS 统计（每 4 秒更新）
  const now = performance.now()
  frameCount++
  if (now - lastFpsTime >= 4000) {
    fps.value = Math.round(frameCount / ((now - lastFpsTime) / 1000))
    frameCount = 0
    lastFpsTime = now
  }

  if (connected.value && pending) {
    // pending 中有 Worker 解析好的最新一帧（v5: pos/meta/ll + isCell 双协议）
    const data = pending
    pending = null
    droneCount.value = data.serverCount || data.count || 0
    drawnCount.value = data.count || 0
    mode.value = data.isCell ? '聚合' : '原始'
    renderer.updateBatch(data.pos, data.meta, data.ll, data.count, data.isCell)
  }

  // canvas 尺寸自适应（仅尺寸变化时，减少无效重绘）
  const canvas = v.scene.canvas
  if (canvas && (canvas.clientWidth !== canvas.width || canvas.clientHeight !== canvas.height)) {
    v.resize()
  }
}

// ── 外部 Event：Resize ──
function onResizeHint() { updateViewport() }

// ── 外部 Event：Decimate ──
function onSetDecimate(val) {
  decimate = val
  if (worker) worker.postMessage({ setDecimate: val })
}

// ── 外部 Event：Debug FPS ──
function onToggleDebugFps() {
  debugFps.value = !debugFps.value
  useC.setDebugFps(debugFps.value)
}

// ── 生命周期 ──
onMounted(async () => {
  await useC.initViewer(cesiumContainer.value)

  // 加载行政区划边界（悬浮半空科技面板）——随 base 路径相对寻址；失败不阻断主链路
  try {
    await boundary.loadBoundary(import.meta.env.BASE_URL + 'GEOJSON/北京市_市.geojson')
  } catch (e) {
    console.warn('[demo] 行政区边界加载失败，跳过（不影响渲染与推流）', e)
  }

  // 自定义相机点击 → 添加临时 label（业务演示）
  const v = viewerRef.value
  if (v) {
    _clickHandler = v.screenSpaceEventHandler.setInputAction((e) => {
      const cartesian = v.camera.pickEllipsoid(e.position)
      if (!cartesian) return
      const carto = Cesium.Cartographic.fromCartesian(cartesian)
      const lon = Cesium.Math.toDegrees(carto.longitude).toFixed(6)
      const lat = Cesium.Math.toDegrees(carto.latitude).toFixed(6)

      if (_labelEntity) { v.entities.remove(_labelEntity); _labelEntity = null }
      _labelEntity = v.entities.add({
        position: Cesium.Cartesian3.fromDegrees(parseFloat(lon), parseFloat(lat)),
        label: {
          text: `${lon}, ${lat}`,
          font: '14px monospace',
          fillColor: Cesium.Color.YELLOW,
          style: Cesium.LabelStyle.FILL_AND_OUTLINE,
          outlineWidth: 2,
          outlineColor: Cesium.Color.BLACK,
          verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
          pixelOffset: new Cesium.Cartesian2(0, -20),
          disableDepthTestDistance: Number.POSITIVE_INFINITY
        }
      })
    }, Cesium.ScreenSpaceEventType.LEFT_CLICK)
  }

  renderer.init()
  startWorker()
  updateViewport()
  _camListener = v.camera.changed.addEventListener(() => updateViewport())
  connect()
  renderLoop()
})

onUnmounted(() => {
  cancelAnimationFrame(rafId)
  if (ws) try { ws.close() } catch (e) {}
  if (worker) worker.terminate()
  if (_camListener) { _camListener(); _camListener = null }
  const v = viewerRef.value
  if (v && _clickHandler) {
    v.screenSpaceEventHandler.removeInputAction(Cesium.ScreenSpaceEventType.LEFT_CLICK)
  }
  if (_labelEntity && v) { v.entities.remove(_labelEntity); _labelEntity = null }
  boundary.destroy()
  renderer.destroy()
  useC.destroyViewer()
})
</script>

<style>
html, body, #app { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; }
.app { width: 100%; height: 100%; position: relative; }
.cesium-container { width: 100%; height: 100%; }
.drop-overlay {
  position: absolute; top: 10px; left: 50%; transform: translateX(-50%);
  background: rgba(120,0,0,0.8); color: #fff; padding: 6px 14px;
  border-radius: 6px; font-size: 12px; z-index: 10; font-family: monospace;
}
</style>
