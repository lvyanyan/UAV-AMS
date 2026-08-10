<template>
  <div class="flight-monitor">
    <div ref="cesiumContainer" class="cesium-container" />
    <div class="top-stats">
      <span>🛸 在线: {{ droneCount }}</span>
      <span>⚠️ 告警: {{ alarmCount }}</span>
      <span>🚁 LOD层: {{ currentLevel }}</span>
    </div>
    <div class="toolbar">
      <button @click="toggleAirspace">🗺️ {{ showAirspace ? '隐藏' : '显示' }}空域</button>
      <button @click="toggleFps">📊 FPS</button>
    </div>
    <div v-if="showFps" class="fps-overlay">{{ fpsText }}</div>
    <AlertPanel :alerts="alertList" />
  </div>
</template>

<script setup>
import { ref, shallowRef, onMounted, onUnmounted } from 'vue'
import * as Cesium from 'cesium'
import 'cesium/Build/Cesium/Widgets/widgets.css'
import { CESIUM_CONFIG } from '@/config/cesiumConfig.js'
import { useLodDroneRenderer } from '@/composables/useLodDroneRenderer.js'
import AlertPanel from '@/components/AlertPanel.vue'

const cesiumContainer = ref(null)
const viewerRef = shallowRef(null)
const droneCount = ref(0)
const alarmCount = ref(0)
const currentLevel = ref('--')
const showAirspace = ref(false)
const showFps = ref(false)
const fpsText = ref('')
const alertList = ref([])

let viewer = null
let ws = null
let wsReconnectTimer = null
let statsTimer = null
let fpsTimer = null
let airspaceEntities = []

const renderer = useLodDroneRenderer(viewerRef)
// drone ID → slot index 映射表
const droneMap = new Map()
let nextSlot = 0
// 缓冲：收到遥测但 renderer 还没 init 时暂存
let buffer = []
let initTimer = null
let initStarted = false

// ── 高德底图 ──
function initMap() {
  if (viewer) return
  Cesium.Ion.defaultAccessToken = CESIUM_CONFIG?.ionToken || ''
  viewer = new Cesium.Viewer(cesiumContainer.value, {
    imageryProvider: false,
    terrainProvider: new Cesium.EllipsoidTerrainProvider(),
    timeline: false, animation: false, baseLayerPicker: false,
    fullscreenButton: false, geocoder: false, homeButton: false,
    infoBox: false, sceneModePicker: false, navigationHelpButton: false,
    selectionIndicator: false, scene3DOnly: true, shadows: false,
    terrainShadows: Cesium.ShadowMode.DISABLED,
  })
  viewerRef.value = viewer
  viewer.imageryLayers.removeAll()
  viewer.imageryLayers.addImageryProvider(new Cesium.UrlTemplateImageryProvider({ url: 'https://webst01.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}', maximumLevel: 18 }))
  viewer.imageryLayers.addImageryProvider(new Cesium.UrlTemplateImageryProvider({ url: 'https://webst01.is.autonavi.com/appmaptile?style=8&x={x}&y={y}&z={z}', maximumLevel: 18 }))
  if (viewer.scene.fog) viewer.scene.fog.enabled = false
  if (viewer.scene.skyAtmosphere) viewer.scene.skyAtmosphere.show = false
  viewer.camera.setView({ destination: Cesium.Cartesian3.fromDegrees(116.397, 39.908, 15000), orientation: { heading: 0, pitch: Cesium.Math.toRadians(-30), roll: 0 } })
}

// ── WebSocket ──
function connectWebSocket() {
  if (ws && ws.readyState === WebSocket.OPEN) return
  ws = new WebSocket('ws://localhost:8090/ws')
  ws.onopen = () => console.log('✅ WebSocket :8090')
  ws.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data)
      if (msg.type === 'telemetry' && msg.data) onTelemetry(msg.data)
    } catch (_) {}
  }
  ws.onclose = () => { wsReconnectTimer = setTimeout(connectWebSocket, 2000) }
}

// ── 核心：处理遥测，用唯一 ID 去重计数 ──
function getDroneId(data) {
  return String(data.device_sn ?? data.droneId ?? data.id ?? data.deviceId ?? '')
}

function onTelemetry(data) {
  const id = getDroneId(data)
  if (!id) return

  // 还没 init，缓冲
  if (!renderer.isInitialized()) {
    buffer.push(data)
    if (!initTimer && !initStarted) {
      initTimer = setTimeout(flushBuffer, 1000)  // 1 秒收拢窗口
    }
    return
  }

  // 已 init，直接更新
  applyData(data, id)
}

function applyData(data, id) {
  let slot = droneMap.get(id)
  if (slot === undefined) {
    // 新无人机 → 扩容
    slot = nextSlot++
    droneMap.set(id, slot)
    // 确保 renderer 有足够槽位
    if (slot >= renderer.getCount()) {
      const need = slot - renderer.getCount() + 500  // 一次扩 500
      renderer.addDrones(need)
    }
  }
  renderer.upsertDrone(data, slot)
}

// ── 清空缓冲区：按唯一 ID 计数初始化 ──
async function flushBuffer() {
  initTimer = null
  initStarted = true
  const batch = buffer
  buffer = []
  if (batch.length === 0) { initStarted = false; return }

  // ★ 关键修复：按唯一无人机 ID 计数，不是消息数 ★
  const uniqueIds = new Set()
  for (const d of batch) {
    const id = getDroneId(d)
    if (id) uniqueIds.add(id)
  }
  const uniqueCount = uniqueIds.size
  console.log(`[FlightMonitor] 缓冲 ${batch.length} 条消息 → ${uniqueCount} 架唯一无人机，初始化...`)

  // 建映射表
  droneMap.clear()
  nextSlot = 0
  for (const id of uniqueIds) {
    droneMap.set(id, nextSlot++)
  }

  // 初始化渲染器
  await renderer.init(uniqueCount)

  // 喂第一批数据
  for (const d of batch) {
    const id = getDroneId(d)
    const slot = droneMap.get(id)
    if (slot !== undefined) renderer.upsertDrone(d, slot)
  }

  initStarted = false
}

// ── 点击选中 ──
let highlightIdx = -1
function onLeftClick(click) {
  if (!viewer) return
  const picked = viewer.scene.pick(click.position)
  if (picked && picked.primitive && picked.primitive.id !== undefined) {
    const idx = picked.primitive.id
    highlightIdx = (highlightIdx === idx) ? -1 : idx
    renderer.highlight(highlightIdx)
  } else {
    highlightIdx = -1; renderer.highlight(-1)
  }
}

// ── 空域 ──
async function toggleAirspace() {
  showAirspace.value = !showAirspace.value
  if (showAirspace.value) {
    try {
      const resp = await fetch('/api/airspace/geojson')
      const json = await resp.json()
      const features = json.data?.features || json.features || []
      if (features.length === 0) return
      const ds = await Cesium.GeoJsonDataSource.load(features, { clampToGround: true, stroke: Cesium.Color.YELLOW, fill: Cesium.Color.fromCssColorString('#3388ff44') })
      viewer.dataSources.add(ds); airspaceEntities.push(ds)
      viewer.flyTo(ds)
    } catch (e) { console.error('空域失败:', e) }
  } else {
    airspaceEntities.forEach(ds => viewer.dataSources.remove(ds, true))
    airspaceEntities = []
  }
}

// ── FPS ──
function toggleFps() {
  showFps.value = !showFps.value
  if (showFps.value) {
    if (viewer) viewer.scene.debugShowFramesPerSecond = true
    fpsTimer = setInterval(() => { fpsText.value = viewer?.scene?.fps ? `FPS: ${viewer.scene.fps}` : 'FPS: --' }, 1000)
  } else {
    if (viewer) viewer.scene.debugShowFramesPerSecond = false
    clearInterval(fpsTimer); fpsTimer = null; fpsText.value = ''
  }
}

// ── 统计 ──
function updateStats() {
  if (renderer.isInitialized()) {
    droneCount.value = renderer.getCount()
    currentLevel.value = renderer.getCurrentLevel()
  }
}

// ── 生命周期 ──
onMounted(() => {
  initMap()
  connectWebSocket()
  viewer.screenSpaceEventHandler.setInputAction(onLeftClick, Cesium.ScreenSpaceEventType.LEFT_CLICK)
  statsTimer = setInterval(updateStats, 2000)
})

onUnmounted(() => {
  clearTimeout(wsReconnectTimer); clearTimeout(initTimer); clearInterval(statsTimer); clearInterval(fpsTimer)
  if (ws) ws.close()
  renderer.destroy()
  if (viewer) { viewer.destroy(); viewer = null }
})
</script>

<style scoped>
.flight-monitor { width: 100%; height: 100%; position: relative; }
.cesium-container { width: 100%; height: 100%; }
.top-stats { position: absolute; top: 10px; left: 50%; transform: translateX(-50%); background: rgba(0,0,0,0.75); color: #0f0; padding: 6px 18px; border-radius: 6px; font-size: 14px; display: flex; gap: 24px; z-index: 10; }
.toolbar { position: absolute; top: 10px; right: 10px; z-index: 10; display: flex; gap: 8px; flex-direction: column; }
.toolbar button { background: rgba(0,0,0,0.7); color: #fff; border: 1px solid #555; padding: 6px 12px; border-radius: 4px; cursor: pointer; font-size: 13px; }
.toolbar button:hover { background: rgba(50,50,50,0.8); }
.fps-overlay { position: absolute; top: 10px; left: 10px; z-index: 10; background: rgba(0,0,0,0.7); color: #0f0; font-family: monospace; padding: 4px 8px; border-radius: 4px; font-size: 13px; }
</style>
