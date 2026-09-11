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
    highlightIdx = (highlightIdx === idx) ? -1 : idx
    renderer.highlight(highlightIdx)
    if (highlightIdx === idx) openLabelFor(idx, click.position)
    else closeLabel()
  } else {
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
  loadPlanMeta()
  viewer.screenSpaceEventHandler.setInputAction(onLeftClick, Cesium.ScreenSpaceEventType.LEFT_CLICK)
  statsTimer = setInterval(updateStats, 2000)
})

onUnmounted(() => {
  clearTimeout(wsReconnectTimer); clearTimeout(initTimer); clearInterval(statsTimer); clearInterval(fpsTimer)
  if (labelTimer) { clearInterval(labelTimer); labelTimer = null }
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
