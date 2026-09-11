<template>
  <div class="flight-monitor">
    <div ref="cesiumContainer" class="cesium-container" />
    <div class="top-stats">
      <span>🛸 在线: {{ droneCount.toLocaleString() }}</span>
      <span>⚠️ 告警: {{ alarmCount }}</span>
      <span v-if="mode === 'standard'">🚁 LOD层: {{ currentLevel }}</span>
      <span v-else>🚀 百万模式 · {{ millionFrameMode }}</span>
    </div>
    <div class="toolbar">
      <button @click="toggleMode" :disabled="switching" :class="{ active: mode === 'million' }">
        🚀 {{ mode === 'million' ? '返回标准模式' : '百万模式' }}
      </button>
      <div v-if="mode === 'million'" class="scale-group">
        <button v-for="s in SCALES" :key="s.count" :class="{ active: fleetScale === s.count }" @click="setScale(s.count)">{{ s.label }}</button>
      </div>
      <button @click="toggleAirspace">🗺️ {{ showAirspace ? '隐藏' : '显示' }}空域</button>
      <button @click="toggleFps">📊 FPS</button>
    </div>
    <div v-if="showFps" class="fps-overlay">{{ fpsText }}</div>
    <div v-if="millionDropped > 0" class="drop-overlay">丢弃过时帧 {{ millionDropped }}（Worker/渲染跟不上）</div>
    <AlertPanel :alerts="alertList" />
    <!-- 百万模式：点击聚合点/原始点出现的简化标牌 -->
    <div v-if="millionLabel.visible" class="drone-label" :style="{ left: millionLabel.x + 'px', top: millionLabel.y + 'px' }" @click.stop>
      <div class="dl-head"><span class="dl-sn">🛸 机群目标 #{{ millionLabel.index }}</span><span class="dl-close" @click="closeMillionLabel">✕</span></div>
      <div class="dl-row"><span class="dl-k">经度</span><span>{{ millionLabel.lon }}</span></div>
      <div class="dl-row"><span class="dl-k">纬度</span><span>{{ millionLabel.lat }}</span></div>
      <div class="dl-row"><span class="dl-k">高度</span><span>{{ millionLabel.alt }} m</span></div>
      <div class="dl-row"><span class="dl-k">告警</span><span :class="'alv-' + millionLabel.alertClass">{{ millionLabel.alertText }}</span></div>
    </div>
    <!-- 点击无人机出现的 DOM 标牌 -->
    <div v-if="label.visible" class="drone-label" :style="{ left: label.x + 'px', top: label.y + 'px' }" @click.stop>
      <div class="dl-head"><span class="dl-sn">🛸 {{ label.sn }}</span><span class="dl-close" @click="closeLabel">✕</span></div>
      <div class="dl-row"><span class="dl-k">航向</span><span>{{ label.heading }}°</span></div>
      <div class="dl-row"><span class="dl-k">告警</span><span :class="'alv-' + label.alertLevel">{{ label.alertText }}</span></div>
      <div class="dl-row"><span class="dl-k">计划编号</span><span>{{ label.planCode }}</span></div>
      <div class="dl-row"><span class="dl-k">任务性质</span><span>{{ label.purpose }}</span></div>
      <div class="dl-row"><span class="dl-k">运营主体</span><span>{{ label.operator }}</span></div>
    </div>
  </div>
</template>

<script setup>
import { ref, shallowRef, onMounted, onUnmounted } from 'vue'
import * as Cesium from 'cesium'
import 'cesium/Build/Cesium/Widgets/widgets.css'
import { CESIUM_CONFIG } from '@/config/cesiumConfig.js'
import { useLodDroneRenderer } from '@/composables/useLodDroneRenderer.js'
import { useMillionRenderer } from '@/composables/useMillionRenderer.js'
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

// ── 百万模式状态 ──
const FLEET_PORT = 8091
const SCALES = [
  { label: '10万', count: 100000 },
  { label: '50万', count: 500000 },
  { label: '100万', count: 1000000 },
]
const mode = ref('standard')            // 'standard' | 'million'
const switching = ref(false)            // 切换中禁用按钮，防止两个渲染器半初始化
const millionFrameMode = ref('连接中')   // 聚合 | 原始 | 连接中 | 断开
const millionDropped = ref(0)
const fleetScale = ref(0)               // 当前机群规模（/stats 轮询回填，命中 SCALES 才亮）
const millionLabel = ref({ visible: false, x: 0, y: 0, index: -1, lon: '--', lat: '--', alt: '--', alertClass: 'NONE', alertText: '无' })

let viewer = null
let ws = null
let wsReconnectTimer = null
let statsTimer = null
let labelTimer = null
let airspaceEntities = []
let selectedSlot = -1
let slotToSn = []                 // 槽位 → SN
const droneMeta = new Map()       // SN → { heading, alertLevel, alertType, alertText, flightPlanId }
const planByDrone = new Map()     // drone_sn → 飞行计划（plan_code / flight_purpose）
const ownerByDrone = new Map()    // drone_sn → 运营主体名称

// 点击标牌状态
const label = ref({
  visible: false, x: 0, y: 0, sn: '--',
  heading: '--', alertLevel: 'NONE', alertText: '无',
  planCode: '无（空域巡逻）', purpose: '空域巡逻', operator: '--'
})

const renderer = useLodDroneRenderer(viewerRef)
const millionRenderer = useMillionRenderer(viewerRef)
// drone ID → slot index 映射表
const droneMap = new Map()
let nextSlot = 0
// 缓冲：收到遥测但 renderer 还没 init 时暂存
let buffer = []
let initTimer = null
let initStarted = false

// 百万模式运行时句柄
let fleetWs = null
let fleetWsReconnectTimer = null
let fleetWorker = null
let workerBusy = false      // 同一时刻只让 Worker 处理一帧，丢弃来的新帧
let pendingFrame = null     // Worker 回传、待 rAF 消费的最新一帧
let camListener = null
let statsPollTimer = null
let rafId = 0
let _lastVpSend = 0
let rafFrameCount = 0
let rafLastFpsTime = 0

// ── 高德底图 ──
function initMap() {
  if (viewer) return
  Cesium.Ion.defaultAccessToken = CESIUM_CONFIG?.ionToken || ''
  viewer = new Cesium.Viewer(cesiumContainer.value, {
    imageryProvider: false,
    terrainProvider: new Cesium.EllipsoidTerrainProvider(),
    creditContainer: document.createElement('div'),
    timeline: false, animation: false, baseLayerPicker: false,
    fullscreenButton: false, geocoder: false, homeButton: false,
    infoBox: false, sceneModePicker: false, navigationHelpButton: false,
    selectionIndicator: false, scene3DOnly: true, shadows: false,
    terrainShadows: Cesium.ShadowMode.DISABLED,
  })
  viewerRef.value = viewer
  viewer.imageryLayers.removeAll()
  viewer.imageryLayers.addImageryProvider(new Cesium.UrlTemplateImageryProvider({
    url: 'https://webst0{s}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}',
    subdomains: ['1', '2', '3', '4'],
    maximumLevel: 18,
  }))
  viewer.imageryLayers.addImageryProvider(new Cesium.UrlTemplateImageryProvider({
    url: 'https://webst0{s}.is.autonavi.com/appmaptile?style=8&x={x}&y={y}&z={z}',
    subdomains: ['1', '2', '3', '4'],
    maximumLevel: 18,
  }))
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
      else if (msg.type === 'uav.alarm.event' && msg.data) onAlarmEvent(msg.data)
    } catch (_) {}
  }
  ws.onclose = () => { wsReconnectTimer = setTimeout(connectWebSocket, 2000) }
}

// ══ 百万模式：二进制聚合通道（uav-realtime :8091/fleet）══

// Worker：二进制帧解析 + ECEF 转换（零拷贝 Transferable）
function startFleetWorker() {
  fleetWorker = new Worker(new URL('../../worker/fleetWorker.js', import.meta.url), { type: 'module' })
  fleetWorker.onmessage = (e) => {
    workerBusy = false
    pendingFrame = e.data // 只保留最新，旧的被覆盖即丢弃
  }
}

function connectFleetWs() {
  if (fleetWs && (fleetWs.readyState === WebSocket.OPEN || fleetWs.readyState === WebSocket.CONNECTING)) return
  fleetWs = new WebSocket(`ws://localhost:${FLEET_PORT}/fleet`)
  fleetWs.binaryType = 'arraybuffer'
  fleetWs.onopen = () => {
    millionFrameMode.value = '聚合'
    clearTimeout(fleetWsReconnectTimer)
    sendViewport(true)
  }
  fleetWs.onclose = () => {
    if (mode.value !== 'million') return
    millionFrameMode.value = '断开'
    fleetWsReconnectTimer = setTimeout(connectFleetWs, 3000)
  }
  fleetWs.onerror = () => fleetWs && fleetWs.close()
  fleetWs.onmessage = (e) => {
    if (workerBusy) { millionDropped.value++; return }
    workerBusy = true
    fleetWorker.postMessage(e.data, [e.data])
  }
}

// 相机变动 → 上报视锥（服务端按视野聚合/裁剪），200ms 节流
function sendViewport(force) {
  if (!viewer || !fleetWs || fleetWs.readyState !== WebSocket.OPEN) return
  const rect = viewer.camera.computeViewRectangle(viewer.scene.globe.ellipsoid)
  if (!rect) return
  const now = Date.now()
  if (!force && now - _lastVpSend < 200) return
  _lastVpSend = now
  fleetWs.send(JSON.stringify({
    h: Math.round(viewer.camera.positionCartographic.height),
    minLat: +Cesium.Math.toDegrees(rect.south).toFixed(5),
    maxLat: +Cesium.Math.toDegrees(rect.north).toFixed(5),
    minLon: +Cesium.Math.toDegrees(rect.west).toFixed(5),
    maxLon: +Cesium.Math.toDegrees(rect.east).toFixed(5),
    full: false
  }))
}

// 机群规模调整 + 在线数轮询（cell 帧的 count 是网格数，真实在线数以 /stats 为准）
async function resizeFleet(count) {
  try { await fetch(`http://localhost:${FLEET_PORT}/resize?count=${count}`) } catch (e) {}
  pollFleetStats()
}

function setScale(count) {
  fleetScale.value = count
  resizeFleet(count)
}

function pollFleetStats() {
  fetch(`http://localhost:${FLEET_PORT}/stats`).then(r => r.json()).then(s => {
    if (mode.value !== 'million') return
    if (s.count) droneCount.value = s.count
    if (SCALES.some(x => x.count === s.count)) fleetScale.value = s.count
  }).catch(() => {})
}

// 主渲染循环：消费 Worker 解析好的最新一帧 + FPS 统计 + 百万标牌跟随
function mainLoop() {
  rafId = requestAnimationFrame(mainLoop)
  const now = performance.now()
  if (showFps.value) {
    rafFrameCount++
    if (now - rafLastFpsTime >= 1000) {
      fpsText.value = `rAF: ${Math.round(rafFrameCount / ((now - rafLastFpsTime) / 1000))}`
      rafFrameCount = 0
      rafLastFpsTime = now
    }
  }
  if (mode.value !== 'million') return
  if (pendingFrame) {
    const data = pendingFrame
    pendingFrame = null
    millionFrameMode.value = data.isCell ? '聚合' : '原始'
    millionRenderer.updateBatch(data.pos, data.meta, data.ll, data.count, data.isCell)
  }
  if (millionLabel.value.visible) updateMillionLabelPos()
}

// ── 模式切换 ──
async function toggleMode() {
  if (switching.value) return
  switching.value = true
  try {
    if (mode.value === 'standard') await enterMillionMode()
    else await exitMillionMode()
  } finally {
    switching.value = false
  }
}

async function enterMillionMode() {
  // 1) 停标准渲染链路（清缓冲，防止半初始化状态遗留）
  clearTimeout(initTimer); initTimer = null
  initStarted = false
  buffer = []
  droneMap.clear(); slotToSn = []; nextSlot = 0
  renderer.destroy()
  closeLabel()

  // 2) 起百万链路：Worker + WS + 视锥上报 + 在线数轮询
  millionRenderer.init()
  startFleetWorker()
  connectFleetWs()
  camListener = viewer.camera.changed.addEventListener(() => sendViewport(false))
  statsPollTimer = setInterval(pollFleetStats, 2000)
  // 百万模式即上百万规模，工具栏可切 10万/50万/100万
  fleetScale.value = 1000000
  resizeFleet(1000000)
  // 拉高到 50km 俯瞰全机群：视角适配百万规模，且高空走轻量聚合帧
  viewer.camera.flyTo({
    destination: Cesium.Cartesian3.fromDegrees(116.4074, 39.9042, 50000),
    orientation: { heading: 0, pitch: Cesium.Math.toRadians(-90), roll: 0 },
    duration: 1.5
  })
  mode.value = 'million'
  currentLevel.value = '--'
  console.log('[FlightMonitor] 🚀 进入百万模式（二进制 :8091/fleet，100万）')
}

async function exitMillionMode() {
  clearTimeout(fleetWsReconnectTimer)
  if (camListener) { camListener(); camListener = null }
  clearInterval(statsPollTimer); statsPollTimer = null
  if (fleetWs) { try { fleetWs.close() } catch (e) {} fleetWs = null }
  if (fleetWorker) { fleetWorker.terminate(); fleetWorker = null }
  workerBusy = false
  pendingFrame = null
  millionDropped.value = 0
  millionFrameMode.value = '连接中'
  millionRenderer.destroy()
  closeMillionLabel()
  fleetScale.value = 0
  resizeFleet(100000) // 退出百万模式恢复默认规模，服务端常驻开销回到低位

  // 重建标准链路：清空映射，遥测缓冲 1 秒后自动 flushBuffer 重建
  droneMap.clear(); slotToSn = []; nextSlot = 0
  buffer = []
  initStarted = false
  droneCount.value = 0
  mode.value = 'standard'
  console.log('[FlightMonitor] 🛬 返回标准模式（JSON :8090/ws）')
}

// ── 百万模式标牌 ──
function openMillionLabel(idx, screenPos) {
  const d = millionRenderer.getPointData(idx)
  if (!d) return
  const carto = Cesium.Cartographic.fromCartesian(d.cartesian)
  const alertNames = { 0: ['NONE', '无'], 1: ['WARNING', '警告'], 2: ['CRITICAL', '危急'] }
  const [cls, text] = alertNames[d.alert] || alertNames[0]
  millionLabel.value = {
    visible: true, index: idx,
    lon: Cesium.Math.toDegrees(carto.longitude).toFixed(5),
    lat: Cesium.Math.toDegrees(carto.latitude).toFixed(5),
    alt: Math.round(carto.height),
    alertClass: cls, alertText: text,
    x: screenPos.x + 16, y: screenPos.y - 12
  }
}

function updateMillionLabelPos() {
  const idx = millionLabel.value.index
  if (idx < 0 || !viewer) return
  const d = millionRenderer.getPointData(idx)
  if (!d) return
  const p = viewer.scene.cartesianToCanvasCoordinates(d.cartesian, new Cesium.Cartesian2())
  if (p) { millionLabel.value.x = p.x + 16; millionLabel.value.y = p.y - 12 }
}

function closeMillionLabel() {
  millionLabel.value = { ...millionLabel.value, visible: false, index: -1 }
}

// ── 告警事件：uav.alarm.event 经实时服务推送到 WS ──
function onAlarmEvent(d) {
  const sn = d.droneSn || '--'
  const meta = droneMeta.get(sn) || {}
  meta.alertLevel = d.alarmLevel || 'GENERAL'
  meta.alertType = d.alarmType || '告警'
  meta.alertText = `${d.alarmLevel} · ${d.alarmType}`
  droneMeta.set(sn, meta)

  alertList.value.unshift({
    time: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
    level: meta.alertLevel,
    sn,
    type: meta.alertType,
    desc: d.title || d.description || ''
  })
  if (alertList.value.length > 50) alertList.value.pop()
  alarmCount.value = alertList.value.length

  // 标牌若正打开着该机，刷新告警字段
  if (label.value.visible && label.value.sn === sn) {
    label.value.alertLevel = meta.alertLevel
    label.value.alertText = meta.alertText
  }
}

// ── 点击标牌 ──
function openLabelFor(idx, screenPos) {
  const sn = slotToSn[idx] || '--'
  const meta = droneMeta.get(sn) || {}
  const plan = planByDrone.get(sn)
  selectedSlot = idx
  label.value = {
    visible: true,
    sn,
    heading: meta.heading ?? '--',
    alertLevel: meta.alertLevel || 'NONE',
    alertText: meta.alertText || '无',
    planCode: plan ? plan.plan_code : '无（空域巡逻）',
    purpose: plan ? (plan.flight_purpose || '空域巡逻') : '空域巡逻',
    operator: ownerByDrone.get(sn) || '--',
    x: screenPos.x + 16, y: screenPos.y - 12
  }
  if (!labelTimer) labelTimer = setInterval(updateLabelPos, 300)
}

function updateLabelPos() {
  if (selectedSlot < 0 || !label.value.visible || !viewer) return
  const cart = renderer.getPosition(selectedSlot)
  if (!cart) return
  const p = viewer.scene.cartesianToCanvasCoordinates(cart, new Cesium.Cartesian2())
  if (p) {
    label.value.x = p.x + 16
    label.value.y = p.y - 12
  }
}

function closeLabel() {
  label.value.visible = false
  selectedSlot = -1
  if (labelTimer) { clearInterval(labelTimer); labelTimer = null }
}

// ── 计划与运营主体元数据（标牌展示用），每次进入页面拉取一次 ──
async function loadPlanMeta() {
  try {
    const headers = { Authorization: 'Bearer ' + (localStorage.getItem('token') || '') }
    const [planRes, regRes, ownerRes] = await Promise.all([
      fetch('/api/flight-plan/list', { headers }).then(r => r.json()),
      fetch('/api/registry/drone/list', { headers }).then(r => r.json()),
      fetch('/api/registry/owner/list', { headers }).then(r => r.json())
    ])
    for (const p of (planRes.data || [])) planByDrone.set(p.drone_sn, p)
    const ownerName = new Map((ownerRes.data || []).map(o => [o.id, o.owner_name]))
    for (const r of (regRes.data || [])) ownerByDrone.set(r.drone_sn, ownerName.get(r.owner_id) || '--')
  } catch (e) { console.warn('[FlightMonitor] 计划元数据加载失败', e) }
}

// ── 核心：处理遥测，用唯一 ID 去重计数 ──
function getDroneId(data) {
  return String(data.device_sn ?? data.droneId ?? data.id ?? data.deviceId ?? '')
}

function onTelemetry(data) {
    // 百万模式：JSON 遥测不喂渲染器也不缓冲（防内存积压），WS 保持连接只为告警事件
    if (mode.value === 'million') return
    const id = getDroneId(data)
    if (!id) return

    // 元数据：航向 / 计划编号（标牌展示用）
    const pos = data.position || {}
    const meta = droneMeta.get(id) || {}
    meta.heading = Math.round(pos.heading ?? data.heading ?? 0)
    meta.flightPlanId = data.flight_plan_id || ''
    droneMeta.set(id, meta)

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
    slotToSn[slot] = id
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
    droneMap.set(id, nextSlot)
    slotToSn[nextSlot] = id
    nextSlot++
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

// ── 点击选中 → 高亮 + DOM 标牌 ──
let highlightIdx = -1
function onLeftClick(click) {
  if (!viewer) return
  const picked = viewer.scene.pick(click.position)
  if (picked && picked.primitive && picked.primitive.id !== undefined) {
    const idx = picked.primitive.id
    if (mode.value === 'million') {
      // 百万模式：显示简化标牌（索引/经纬度/告警级）
      if (millionLabel.value.visible && millionLabel.value.index === idx) closeMillionLabel()
      else openMillionLabel(idx, click.position)
      return
    }
    highlightIdx = (highlightIdx === idx) ? -1 : idx
    renderer.highlight(highlightIdx)
    if (highlightIdx === idx) openLabelFor(idx, click.position)
    else closeLabel()
  } else {
    if (mode.value === 'million') { closeMillionLabel(); return }
    highlightIdx = -1
    renderer.highlight(-1)
    closeLabel()
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

// ── FPS：Cesium 自带调试层（真实渲染帧率）+ rAF 实测叠加层 ──
function toggleFps() {
  showFps.value = !showFps.value
  if (showFps.value) {
    if (viewer) viewer.scene.debugShowFramesPerSecond = true
    rafFrameCount = 0
    rafLastFpsTime = performance.now()
  } else {
    if (viewer) viewer.scene.debugShowFramesPerSecond = false
    fpsText.value = ''
  }
}

// ── 统计（仅标准模式；百万模式在线数走 /stats 轮询）──
function updateStats() {
  if (mode.value === 'standard' && renderer.isInitialized()) {
    droneCount.value = renderer.getCount()
    currentLevel.value = renderer.getCurrentLevel()
  }
}

// ── 生命周期 ──
onMounted(() => {
  initMap()
  connectWebSocket()
  loadPlanMeta()
  viewer.screenSpaceEventHandler.setInputAction(onLeftClick, Cesium.ScreenSpaceEventType.LEFT_CLICK)
  statsTimer = setInterval(updateStats, 2000)
  mainLoop()
})

onUnmounted(() => {
  cancelAnimationFrame(rafId)
  clearTimeout(wsReconnectTimer); clearTimeout(initTimer); clearInterval(statsTimer)
  clearTimeout(fleetWsReconnectTimer)
  if (camListener) { camListener(); camListener = null }
  clearInterval(statsPollTimer)
  if (fleetWs) fleetWs.close()
  if (fleetWorker) fleetWorker.terminate()
  if (labelTimer) { clearInterval(labelTimer); labelTimer = null }
  if (ws) ws.close()
  millionRenderer.destroy()
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
.toolbar button.active { background: #b45309; border-color: #f59e0b; }
.toolbar button:disabled { opacity: 0.5; cursor: wait; }
.scale-group { display: flex; flex-direction: column; gap: 4px; }
.scale-group button { background: rgba(0,0,0,0.7); color: #9ecbff; border: 1px solid #555; padding: 5px 12px; border-radius: 4px; cursor: pointer; font-size: 13px; }
.scale-group button.active { background: #0b3a66; border-color: #409eff; color: #fff; }
.drop-overlay { position: absolute; top: 44px; left: 50%; transform: translateX(-50%); background: rgba(120,0,0,0.8); color: #fff; padding: 4px 12px; border-radius: 6px; font-size: 12px; z-index: 10; font-family: monospace; }
.fps-overlay { position: absolute; top: 10px; left: 10px; z-index: 10; background: rgba(0,0,0,0.7); color: #0f0; font-family: monospace; padding: 4px 8px; border-radius: 4px; font-size: 13px; }
/* Cesium 自带 FPS 调试层默认在右上角(top:50px,right:10px)被工具栏遮挡，挪到左上角 rAF 旁 */
.flight-monitor :deep(.cesium-performanceDisplay-defaultContainer) { top: 10px !important; left: 150px !important; right: auto !important; z-index: 9; }

/* 点击无人机弹出的 DOM 标牌 */
.drone-label {
  position: absolute; z-index: 30; min-width: 230px;
  background: rgba(8,18,30,0.92); border: 1px solid #2E86AB; border-left: 3px solid #2E86AB;
  border-radius: 6px; color: #dff3ff; font-size: 12px; padding: 8px 10px;
  box-shadow: 0 4px 14px rgba(0,0,0,0.45);
}
.dl-head { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid rgba(46,134,171,.5); padding-bottom: 4px; margin-bottom: 4px; }
.dl-sn { color: #6fd3ff; font-weight: bold; }
.dl-close { cursor: pointer; color: #89a; padding: 0 4px; }
.dl-close:hover { color: #fff; }
.dl-row { display: flex; gap: 8px; line-height: 1.8; }
.dl-k { color: #7ba0b5; min-width: 56px; }
.alv-CRITICAL, .alv-MAJOR, .alv-EMERGENCY { color: #ff5252; font-weight: bold; }
.alv-WARNING, .alv-MINOR, .alv-SERIOUS { color: #ffb74d; }
.alv-NONE { color: #7ecb7e; }
</style>
