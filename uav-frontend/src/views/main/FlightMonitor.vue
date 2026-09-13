<template>
  <div class="flight-monitor">
    <div ref="cesiumContainer" class="cesium-container" />
    <div class="top-stats">
      <span>{{ $t('monitor.online') }}: {{ droneCount.toLocaleString() }}</span>
      <span>{{ $t('monitor.alarms') }}: {{ alarmCount }}</span>
      <span v-if="mode === 'standard'">🚁 {{ $t('monitor.lodLevel') }}: {{ currentLevel }}</span>
      <span v-else>{{ $t('monitor.millionMode') }} · {{ $t(millionFrameMode) }}</span>
    </div>
    <div class="toolbar">
      <button @click="toggleMode" :disabled="switching" :class="{ active: mode === 'million' }">
        <img class="tb-ic" src="@/assets/icons/ic-rocket.png" alt="" />
        {{ mode === 'million' ? $t('monitor.backStandard') : $t('monitor.millionMode') }}
      </button>
      <div v-if="mode === 'million'" class="scale-group">
        <button v-for="s in SCALES" :key="s.count" :class="{ active: fleetScale === s.count }" @click="setScale(s.count)">{{ $t(s.label) }}</button>
      </div>
      <button @click="toggleAirspace"><img class="tb-ic" src="@/assets/icons/ic-polygon.png" alt="" />{{ showAirspace ? $t('monitor.hideAirspace') : $t('monitor.showAirspace') }}</button>
      <button @click="toggleFps"><img class="tb-ic" src="@/assets/icons/ic-gauge.png" alt="" />FPS</button>
      <button :class="{ active: activePanel === 'airspace' }" @click="togglePanel('airspace')"><img class="tb-ic" src="@/assets/icons/ic-polygon.png" alt="" />{{ $t('monitor.panel.airspace') }}</button>
      <button :class="{ active: activePanel === 'route' }" @click="togglePanel('route')"><img class="tb-ic" src="@/assets/icons/ic-route.png" alt="" />{{ $t('monitor.panel.route') }}</button>
      <button :class="{ active: activePanel === 'airport' }" @click="togglePanel('airport')"><img class="tb-ic" src="@/assets/icons/ic-tower.png" alt="" />{{ $t('monitor.panel.airport') }}</button>
      <button :class="{ active: activePanel === 'suppress' }" @click="togglePanel('suppress')"><img class="tb-ic" src="@/assets/icons/ic-funnel.png" alt="" />{{ $t('monitor.panel.suppress') }}</button>
    </div>
    <!-- 图层 / 消息重放 / 告警抑制 面板 -->
    <div v-if="activePanel" class="side-panel">
      <div class="sp-head">
        <b>{{ panelTitle }}</b>
        <el-icon class="sp-close" @click="activePanel = null"><Close /></el-icon>
      </div>
      <div class="sp-body">
        <template v-if="['airspace', 'route', 'airport'].includes(activePanel)">
          <el-input v-model="layerFilter" :placeholder="$t('monitor.filterByName')" size="small" clearable style="margin-bottom:8px" />
          <div v-for="g in layerGroups" :key="g.name" class="layer-group">
            <div class="lg-title">
              <label class="layer-item" style="padding:0 2px">
                <input type="checkbox" :checked="groupAllChecked(g)" @change="toggleGroup(g, $event.target.checked)" />
                <b>{{ $t('monitor.panel.' + g.name) }}</b>
              </label>
            </div>
            <label v-for="it in g.items" :key="it.key" class="layer-item">
              <input type="checkbox" v-model="it.visible" @change="renderOverlays" />
              <span class="layer-name" :class="{ nofly: it.kind === 'no_fly' }">{{ it.label() }}</span>
            </label>
          </div>
          <div v-if="!layerGroups.length" class="empty-hint">{{ $t('monitor.noLayerData') }}</div>
        </template>
        <template v-else-if="activePanel === 'suppress'">
          <div class="empty-hint">
            {{ $t('monitor.suppressSummary', suppressCount) }}
          </div>
          <el-button type="primary" size="small" style="width:100%;margin-top:8px" @click="suppressOpen = true">{{ $t('monitor.configSuppress') }}</el-button>
        </template>
      </div>
    </div>
    <div v-if="showFps" class="fps-overlay">{{ fpsText }}</div>
    <div v-if="showDropped" class="drop-overlay">{{ $t('monitor.dropped', { n: millionDropped }) }}</div>
    <AlertPanel :alerts="alertList" @focus="focusAlarm" @clear="alertList = []" />
    <!-- 百万模式：点击聚合点/原始点出现的简化标牌 -->
    <div v-if="millionLabel.visible" class="drone-label" :style="{ left: millionLabel.x + 'px', top: millionLabel.y + 'px' }" @click.stop>
      <div class="dl-head"><span class="dl-sn">{{ $t('monitor.fleetTarget', { n: millionLabel.index }) }}</span><span class="dl-close" @click="closeMillionLabel">✕</span></div>
      <div class="dl-row"><span class="dl-k">{{ $t('monitor.lon') }}</span><span>{{ millionLabel.lon }}</span></div>
      <div class="dl-row"><span class="dl-k">{{ $t('monitor.lat') }}</span><span>{{ millionLabel.lat }}</span></div>
      <div class="dl-row"><span class="dl-k">{{ $t('monitor.alt') }}</span><span>{{ millionLabel.alt }} m</span></div>
      <div class="dl-row"><span class="dl-k">{{ $t('monitor.alert') }}</span><span :class="'alv-' + millionLabel.alertClass">{{ dt(millionLabel.alertText) }}</span></div>
    </div>
    <!-- 点击无人机出现的 DOM 标牌 -->
    <div v-if="label.visible" class="drone-label" :style="{ left: label.x + 'px', top: label.y + 'px' }" @click.stop>
      <div class="dl-head"><span class="dl-sn">{{ label.sn }}</span><span class="dl-close" @click="closeLabel">✕</span></div>
      <div class="dl-row"><span class="dl-k">{{ $t('monitor.heading') }}</span><span>{{ label.heading }}°</span></div>
      <div class="dl-row"><span class="dl-k">{{ $t('monitor.phase') }}</span><span class="dl-phase">{{ phaseText(label.phase) }}</span></div>
      <div class="dl-row"><span class="dl-k">{{ $t('monitor.alert') }}</span><span :class="'alv-' + label.alertLevel">{{ dt(label.alertText) }}</span></div>
      <div class="dl-row"><span class="dl-k">{{ $t('monitor.planNo') }}</span><span>{{ label.planCode }}</span></div>
      <div class="dl-row"><span class="dl-k">{{ $t('monitor.purpose') }}</span><span>{{ label.purpose }}</span></div>
      <div class="dl-row"><span class="dl-k">{{ $t('monitor.operator') }}</span><span>{{ label.operator }}</span></div>
    </div>
      <!-- 告警抑制配置（弹出面板） -->
    <el-dialog v-model="suppressOpen" :title="$t('monitor.suppressTitle')" width="560px" @open="loadSuppressRules">
      <div class="suppress-group">
        <div class="suppress-group-title">{{ $t('monitor.byType') }}</div>
        <el-checkbox-group :model-value="checkedTypes" @update:model-value="setTypes">
          <el-checkbox-button v-for="d in alarmTypeItems" :key="d.value" :value="d.value">{{ d.label }}</el-checkbox-button>
        </el-checkbox-group>
      </div>
      <div class="suppress-group">
        <div class="suppress-group-title">{{ $t('monitor.byLevel') }}</div>
        <el-checkbox-group :model-value="checkedLevels" @update:model-value="setLevels">
          <el-checkbox-button v-for="d in alarmLevelItems" :key="d.value" :value="d.value">{{ d.label }}</el-checkbox-button>
        </el-checkbox-group>
      </div>
      <div class="suppress-group">
        <div class="suppress-group-title">{{ $t('monitor.bySn') }}</div>
        <div style="display:flex;gap:8px;margin-bottom:8px">
          <el-input v-model="newSn" size="small" :placeholder="$t('monitor.snPlaceholder')" @keyup="onSnKeyup" />
          <el-button size="small" @click="addSn">{{ $t('common.add') }}</el-button>
        </div>
        <div class="sn-tags">
          <el-tag v-for="r in snRules" :key="r.id" closable size="small" style="margin:0 6px 6px 0" @close="removeSuppressRule({ droneSn: r.droneSn })">{{ r.droneSn }}</el-tag>
          <span v-if="!snRules.length" style="color:var(--el-text-color-secondary);font-size:12px">{{ $t('common.none') }}</span>
        </div>
      </div>
      <div style="color:var(--el-text-color-secondary);font-size:12px">
        {{ $t('monitor.suppressNote') }}
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, shallowRef, computed, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import * as Cesium from 'cesium'
import 'cesium/Build/Cesium/Widgets/widgets.css'
import { CESIUM_CONFIG } from '@/config/cesiumConfig.js'
import { useLodDroneRenderer } from '@/composables/useLodDroneRenderer.js'
import { useMillionRenderer } from '@/composables/useMillionRenderer.js'
import AlertPanel from '@/components/AlertPanel.vue'
import { wsClient } from '@/api/websocket'
import { ElMessage } from 'element-plus'
import { useDict } from '@/composables/useDict'
import { locale } from '@/locales'
import { useUserStore } from '@/stores/user'
import { airspaceApi } from '@/api/airspace'
import { routeApi } from '@/api/route'
import { airportApi } from '@/api/airport'

const { t, te } = useI18n()
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
  { label: 'monitor.scale.100k', count: 100000 },
  { label: 'monitor.scale.500k', count: 500000 },
  { label: 'monitor.scale.1m', count: 1000000 },
]
const mode = ref('standard')            // 'standard' | 'million'
const switching = ref(false)            // 切换中禁用按钮，防止两个渲染器半初始化
const millionFrameMode = ref('monitor.frame.connecting')   // 聚合 | 原始 | 连接中 | 断开（i18n key）
const millionDropped = ref(0)
const fleetScale = ref(0)               // 当前机群规模（/stats 轮询回填，命中 SCALES 才亮）
const millionLabel = ref({ visible: false, x: 0, y: 0, index: -1, lon: '--', lat: '--', alt: '--', alertClass: 'NONE', alertText: 'monitor.alertLevel.NONE' })
const showDropped = ref(false)
let dropHideTimer = null

// ── 图层 / 消息重放 / 告警抑制 ──
const { items: alarmTypeItems, label: typeLabel } = useDict('alarm_type')
const { items: alarmLevelItems, label: levelLabel } = useDict('alarm_level')
const { items: directionItems, label: directionLabel } = useDict('route_direction')

const userStore = useUserStore()
const activePanel = ref(null)
const panelTitle = computed(() => ({
  airspace: t('monitor.panelTitle.airspace'),
  route: t('monitor.panelTitle.route'),
  airport: t('monitor.panelTitle.airport'),
  suppress: t('monitor.suppressTitle'),
}[activePanel.value] || ''))
function togglePanel(name) { activePanel.value = activePanel.value === name ? null : name }

const airspaceList = ref([])
const routeList = ref([])
const airportList = ref([])
const layerItems = ref([])   // { group, key, id, label(), kind, visible, data }

const layerFilter = ref('')
const layerGroups = computed(() => {
  if (!['airspace', 'route', 'airport'].includes(activePanel.value)) return []
  const items = layerItems.value.filter(i => i.group === activePanel.value)
  return items.length ? [{ name: activePanel.value, items }] : []
})
function groupAllChecked(g) { return g.items.every(i => i.visible) }
function toggleGroup(g, checked) { g.items.forEach(i => { i.visible = checked }); renderOverlays() }

let overlayEntities = []
function renderOverlays() {
  if (!viewer) return
  overlayEntities.forEach(e => { try { viewer.entities.remove(e) } catch (err) {} })
  overlayEntities = []
  for (const it of layerItems.value) {
    if (!it.visible) continue
    try {
      if (it.group === 'airspace') {
        const ring = JSON.parse(it.data.geoJson).coordinates[0]
        overlayEntities.push(viewer.entities.add({
          polygon: {
            hierarchy: Cesium.Cartesian3.fromDegreesArray(ring.flat()),
            material: (it.kind === 'no_fly' ? Cesium.Color.RED : Cesium.Color.DODGERBLUE).withAlpha(0.25),
            outline: true, outlineColor: Cesium.Color.WHITE.withAlpha(0.6),
          },
        }))
        const cLon = ring.reduce((sum, q) => sum + q[0], 0) / ring.length
        const cLat = ring.reduce((sum, q) => sum + q[1], 0) / ring.length
        overlayEntities.push(viewer.entities.add({
          position: Cesium.Cartesian3.fromDegrees(cLon, cLat),
          label: { text: it.data.airspaceName, font: '12px sans-serif', fillColor: Cesium.Color.WHITE,
                   pixelOffset: new Cesium.Cartesian2(0, -10), disableDepthTestDistance: Number.POSITIVE_INFINITY },
        }))
      } else if (it.group === 'route') {
        const wp = JSON.parse(it.data.waypoints || '[]')
        if (wp.length >= 2) {
          overlayEntities.push(viewer.entities.add({
            polyline: { positions: Cesium.Cartesian3.fromDegreesArray(wp.flat()), width: 3,
                        material: Cesium.Color.ORANGE, clampToGround: true },
          }))
        }
        wp.forEach(pnt => overlayEntities.push(viewer.entities.add({
          position: Cesium.Cartesian3.fromDegrees(pnt[0], pnt[1]),
          point: { pixelSize: 8, color: Cesium.Color.ORANGE },
        })))
      } else if (it.group === 'airport') {
        overlayEntities.push(viewer.entities.add({
          position: Cesium.Cartesian3.fromDegrees(it.data.lon, it.data.lat),
          point: { pixelSize: 12, color: Cesium.Color.LIME, outlineColor: Cesium.Color.BLACK, outlineWidth: 1 },
          label: { text: it.data.airportName, font: '12px sans-serif', fillColor: Cesium.Color.WHITE,
                   pixelOffset: new Cesium.Cartesian2(0, -14), disableDepthTestDistance: Number.POSITIVE_INFINITY },
        }))
      }
    } catch (e) { /* 跳过坏数据 */ }
  }
}

async function loadLayers() {
  try {
    const results = await Promise.all([airspaceApi.list(), routeApi.list(), airportApi.list()])
    airspaceList.value = results[0].data || []
    routeList.value = results[1].data || []
    airportList.value = results[2].data || []
    const items = []
    for (const a of airspaceList.value) {
      if (!a.geoJson) continue
      items.push({ group: 'airspace', key: 'a' + a.id, id: a.id, data: a,
                   label: () => `${a.airspaceName}（${typeLabel(a.airspaceType)}）`,
                   kind: ['NO_FLY', 'TEMP_NO_FLY'].includes(a.airspaceType) ? 'no_fly' : '', visible: false })
    }
    for (const r of routeList.value) {
      items.push({ group: 'route', key: 'r' + r.id, id: r.id, data: r,
                   label: () => `${r.routeName}（${directionLabel(r.direction)}）`, kind: '', visible: false })
    }
    for (const ap of airportList.value) {
      items.push({ group: 'airport', key: 'p' + ap.id, id: ap.id, data: ap,
                   label: () => `${ap.airportName}（${typeLabel(ap.airportType)}）`, kind: '', visible: false })
    }
    layerItems.value = items
    renderOverlays()
  } catch (e) { console.warn('[FlightMonitor] 图层数据加载失败', e) }
}

// ── 在飞快照：新连接立即获得当前在飞无人机态势 ──
async function loadSnapshot() {
  try {
    const res = await fetch('http://localhost:8090/snapshot')
    const json = await res.json()
    let n = 0
    for (const d of json.drones || []) {
      applyData({ device_sn: d.sn, position: { lat: d.lat, lon: d.lon, alt_m: d.alt, heading: d.heading } }, d.sn)
      n++
    }
    console.log('[FlightMonitor] 快照接入在飞无人机', n)
  } catch (e) { console.warn('快照加载失败', e) }
}

// ── 告警重放（初始化一次性）：接口拉取在飞无人机的活跃告警，驱动告警点着色；
// ── 此后实时告警由 WS 推送增量更新（开关语义下事件量很小）──
async function replayActiveAlarms() {
  try {
    const token = localStorage.getItem('token')
    const res = await fetch('http://localhost:18080/api/alarm/active', { headers: { Authorization: 'Bearer ' + token } })
    const json = await res.json()
    for (const a of json.data || []) {
      const sn = a.droneSn
      const meta = droneMeta.get(sn) || {}
      meta.alertLevel = a.alarmLevel
      meta.alertType = a.alarmType
      meta.alertText = levelLabel(a.alarmLevel) + ' · ' + typeLabel(a.alarmType)
      droneMeta.set(sn, meta)
      const slot = droneMap.get(sn)
      if (slot !== undefined) renderer.setAlertLevel(slot, a.alarmLevel, a.alarmType)
    }
  } catch (e) {}
}


// ── 告警抑制（服务端记录规则，生成侧过滤；前端只负责配置界面）──
const suppressOpen = ref(false)
const suppressRules = ref([])
const newSn = ref('')
const suppressCount = computed(() => ({
  types: new Set(suppressRules.value.filter(r => r.alarmType).map(r => r.alarmType)).size,
  levels: new Set(suppressRules.value.filter(r => r.alarmLevel).map(r => r.alarmLevel)).size,
  sns: new Set(suppressRules.value.filter(r => r.droneSn).map(r => r.droneSn)).size,
}))
const checkedTypes = computed(() => [...new Set(suppressRules.value.filter(r => r.alarmType).map(r => r.alarmType))])
const checkedLevels = computed(() => [...new Set(suppressRules.value.filter(r => r.alarmLevel).map(r => r.alarmLevel))])
const snRules = computed(() => suppressRules.value.filter(r => r.droneSn))
function setTypes(list) {
  for (const v of list) if (!checkedTypes.value.includes(v)) addSuppressRule({ alarmType: v })
  for (const v of checkedTypes.value) if (!list.includes(v)) removeSuppressRule({ alarmType: v })
}
function setLevels(list) {
  for (const v of list) if (!checkedLevels.value.includes(v)) addSuppressRule({ alarmLevel: v })
  for (const v of checkedLevels.value) if (!list.includes(v)) removeSuppressRule({ alarmLevel: v })
}
function addSn() {
  const v = newSn.value.trim()
  if (v) { addSuppressRule({ droneSn: v }); newSn.value = '' }
}
// 手写 Enter 判断：withKeys 修饰符在 vite 预构建 chunk 下可能拿到 undefined 处理器
function onSnKeyup(e) {
  if (e && e.key === 'Enter') addSn()
}
async function loadSuppressRules() {
  try {
    const res = await fetch('http://localhost:18080/api/alarm/suppress?userId=' + encodeURIComponent(userStore.username))
    suppressRules.value = (await res.json()).data || []
  } catch (e) {}
  syncAlarmSubscription()
}

// 告警抑制规则同步到 WS 订阅（推送侧过滤），与告警引擎生成侧落库过滤双保险
function syncAlarmSubscription() {
  const uniq = (k) => [...new Set(suppressRules.value.filter(r => r[k]).map(r => r[k]))]
  wsClient.subscribe({
    alarm: {
      excludeTypes: uniq('alarmType'),
      excludeLevels: uniq('alarmLevel'),
      excludeSns: uniq('droneSn'),
    },
    batch: true,
  })
}
async function addSuppressRule(rule) {
  await fetch('http://localhost:18080/api/alarm/suppress', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userId: userStore.username, ...rule })
  })
  loadSuppressRules()
}
async function removeSuppressRule(rule) {
  const hit = suppressRules.value.find(r =>
    (rule.alarmType && r.alarmType === rule.alarmType) ||
    (rule.alarmLevel && r.alarmLevel === rule.alarmLevel) ||
    (rule.droneSn && r.droneSn === rule.droneSn))
  if (hit) await fetch('http://localhost:18080/api/alarm/suppress/' + hit.id + '?userId=' + encodeURIComponent(userStore.username), { method: 'DELETE' })
  loadSuppressRules()
}

// 丢帧提示：少于 5 帧不打扰，展示 3 秒后自动隐藏
watch(millionDropped, (v) => {
  showDropped.value = v >= 5
  if (dropHideTimer) clearTimeout(dropHideTimer)
  if (showDropped.value) dropHideTimer = setTimeout(() => { showDropped.value = false }, 3000)
})

let viewer = null
let statsTimer = null
let labelTimer = null
let airspaceEntities = []
let selectedSlot = -1
let slotToSn = []                 // 槽位 → SN
const droneMeta = new Map()       // SN → { heading, alertLevel, alertType, alertText, flightPlanId }
const planByDrone = new Map()     // drone_sn → 飞行计划（plan_code / flight_purpose）
const ownerByDrone = new Map()    // drone_sn → 运营主体名称

// 点击标牌状态（alertText 为 i18n key，渲染处 $t 翻译）
const label = ref({
  visible: false, x: 0, y: 0, sn: '--',
  heading: '--', phase: 'FLYING',
  alertLevel: 'NONE', alertText: 'monitor.alertLevel.NONE',
  planCode: '--', purpose: '', operator: '--'
})

// 飞行阶段 → 展示文案（未知阶段原样透出，便于发现漏维护的枚举）
function phaseText(p) {
  const key = 'monitor.phaseText.' + (p || 'FLYING')
  return te(key) ? t(key) : (p || t('monitor.phaseText.FLYING'))
}
// 动态展示翻译：是 i18n key 就翻译，否则原样返回（如运行期拼接的告警文案）
function dt(key) { return te(key) ? t(key) : key }

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
  // 俯视机位：视锥上报按此矩形裁剪遥测，倾斜视角会把足迹推到机群以北导致"在线: 0"
  viewer.camera.setView({ destination: Cesium.Cartesian3.fromDegrees(116.35, 39.75, 60000), orientation: { heading: 0, pitch: Cesium.Math.toRadians(-90), roll: 0 } })
}

// ── WebSocket（v2 订阅：视锥裁剪 + 告警抑制 + 遥测合帧，服务端按会话过滤）──
let unsubscribeWs = null
let offWsStatus = null
let lastWsMsgAt = 0   // 最近一次收到 WS 消息的时间，用于自愈检测

function connectWebSocket() {
  if (unsubscribeWs) return
  lastWsMsgAt = Date.now()
  unsubscribeWs = wsClient.onMessage((type, data) => {
    lastWsMsgAt = Date.now()
    if (type === 'telemetry' && data) {
      onTelemetry(data)
    } else if (type === 'telemetry-batch' && Array.isArray(data)) {
      // 服务端 batch item 是完整信封 {type:'telemetry', data:{...}}，需解包再喂遥测
      for (const d of data) onTelemetry(d && d.data !== undefined ? d.data : d)
    } else if (type === 'uav.alarm.event' && data) {
      onAlarmEvent(data)
    } else if (type === 'subscribe-ack') {
      console.log('[FlightMonitor] 服务端订阅生效:', data)
    }
  })
  // 重连成功后补发订阅条件（视锥 + 抑制规则），避免服务端会话状态丢失
  offWsStatus = wsClient.onStatus((ok) => {
    if (ok) { sendViewport(true); syncAlarmSubscription() }
  })
  wsClient.connect()
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
    millionFrameMode.value = 'monitor.frame.cell'
    clearTimeout(fleetWsReconnectTimer)
    sendViewport(true)
  }
  fleetWs.onclose = () => {
    if (mode.value !== 'million') return
    millionFrameMode.value = 'monitor.frame.off'
    fleetWsReconnectTimer = setTimeout(connectFleetWs, 3000)
  }
  fleetWs.onerror = () => fleetWs && fleetWs.close()
  fleetWs.onmessage = (e) => {
    if (workerBusy) { millionDropped.value++; return }
    workerBusy = true
    fleetWorker.postMessage(e.data, [e.data])
  }
}

// 相机变动 → 上报视锥：:8090 订阅裁剪（标准链路）+ :8091 聚合裁剪（百万链路），200ms 节流
function sendViewport(force) {
  if (!viewer) return
  const now = Date.now()
  if (!force && now - _lastVpSend < 200) return
  _lastVpSend = now
  const rect = viewer.camera.computeViewRectangle(viewer.scene.globe.ellipsoid)
  if (!rect) return
  const vp = {
    minLat: +Cesium.Math.toDegrees(rect.south).toFixed(5),
    maxLat: +Cesium.Math.toDegrees(rect.north).toFixed(5),
    minLon: +Cesium.Math.toDegrees(rect.west).toFixed(5),
    maxLon: +Cesium.Math.toDegrees(rect.east).toFixed(5),
    camAlt: Math.round(viewer.camera.positionCartographic.height),
  }
  // 未连接时 wsClient 缓存条件，连上自动重发
  wsClient.subscribe({ viewport: vp })
  if (fleetWs && fleetWs.readyState === WebSocket.OPEN) {
    fleetWs.send(JSON.stringify({ ...vp, h: vp.camAlt, full: false }))
  }
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
    millionFrameMode.value = data.isCell ? 'monitor.frame.cell' : 'monitor.frame.raw'
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
  activePanel.value = null

  // 2) 起百万链路：Worker + WS + 视锥上报 + 在线数轮询
  millionRenderer.init()
  startFleetWorker()
  connectFleetWs()
  sendViewport(true)
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
  clearInterval(statsPollTimer); statsPollTimer = null
  if (fleetWs) { try { fleetWs.close() } catch (e) {} fleetWs = null }
  if (fleetWorker) { fleetWorker.terminate(); fleetWorker = null }
  workerBusy = false
  pendingFrame = null
  millionDropped.value = 0
  millionFrameMode.value = 'monitor.frame.connecting'
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
  const alertKeys = { 0: ['NONE', 'monitor.alertLevel.NONE'], 1: ['WARNING', 'monitor.alertLevel.WARNING'], 2: ['CRITICAL', 'monitor.alertLevel.CRITICAL'] }
  const [cls, key] = alertKeys[d.alert] || alertKeys[0]
  millionLabel.value = {
    visible: true, index: idx,
    lon: Cesium.Math.toDegrees(carto.longitude).toFixed(5),
    lat: Cesium.Math.toDegrees(carto.latitude).toFixed(5),
    alt: Math.round(carto.height),
    alertClass: cls, alertText: key,
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
  meta.alertType = d.alarmType || ''
  meta.alertText = `${levelLabel(d.alarmLevel || '')} · ${typeLabel(d.alarmType || '')}`
  droneMeta.set(sn, meta)

  alertList.value.unshift({
    time: new Date().toLocaleTimeString(locale.value === 'en' ? 'en-US' : 'zh-CN', { hour12: false }),
    level: meta.alertLevel,
    levelLabel: levelLabel(meta.alertLevel),
    sn,
    type: meta.alertType,
    typeLabel: typeLabel(meta.alertType),
    desc: d.title || d.description || ''
  })
  if (alertList.value.length > 50) alertList.value.pop()
  alarmCount.value = alertList.value.length

  // 标牌若正打开着该机，刷新告警字段（黑飞用专属 HF 黑色徽标）
  if (label.value.visible && label.value.sn === sn) {
    label.value.alertLevel = meta.alertType === 'NO_FLIGHT_PLAN' ? 'HF' : meta.alertLevel
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
    phase: meta.flightPhase || 'FLYING',
    alertLevel: meta.alertType === 'NO_FLIGHT_PLAN' ? 'HF' : (meta.alertLevel || 'NONE'),
    alertText: meta.alertText || 'monitor.alertLevel.NONE',
    planCode: plan ? plan.plan_code : t('monitor.none'),
    purpose: plan ? (plan.flight_purpose || t('monitor.areaPatrol')) : t('monitor.noPlanFlight'),
    operator: ownerByDrone.get(sn) || '--',
    x: screenPos.x + 16, y: screenPos.y - 12
  }
  if (!labelTimer) labelTimer = setInterval(updateLabelPos, 300)
}

function focusAlarm(a) {
  const slot = droneMap.get(a.sn)
  if (slot === undefined) { ElMessage.warning(t('monitor.noPosition')); return }
  const cart = renderer.getPosition(slot)
  if (!cart) return
  const carto = Cesium.Cartographic.fromCartesian(cart)
  viewer.camera.flyTo({
    destination: Cesium.Cartesian3.fromDegrees(
      Cesium.Math.toDegrees(carto.longitude), Cesium.Math.toDegrees(carto.latitude), carto.height + 2500),
    duration: 1.0,
  })
  selectedSlot = slot
  renderer.highlight(slot)
  const p = viewer.scene.cartesianToCanvasCoordinates(cart, new Cesium.Cartesian2())
  if (p) openLabelFor(slot, { x: p.x, y: p.y })
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
    for (const p of (planRes.data || [])) {
      // 含放行/在飞状态：计划生命周期全程可在标牌溯源
      if (['APPROVED', 'RELEASED', 'IN_FLIGHT'].includes(p.plan_status)) planByDrone.set(p.drone_sn, p)
    }
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
    // 消息重放：滚动录制最近 10 分钟遥测；重放期间挂起实时渲染
    const id = getDroneId(data)
    if (!id) return

    // 元数据：航向 / 计划编号（标牌展示用）
    const pos = data.position || {}
    const meta = droneMeta.get(id) || {}
    meta.heading = Math.round(pos.heading ?? data.heading ?? 0)
    meta.flightPlanId = data.flight_plan_id || ''
    meta.flightPhase = data.flight_phase || meta.flightPhase || ''
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

const MAX_DRONES = 4000        // 槽位上限：仿真器 SN 随机生成，无上限会撑爆浏览器
const lastSeen = new Map()     // sn -> 最后遥测时间（LRU 驱逐依据）

function applyData(data, id) {
  // 告警事件积累的等级合入遥测，供渲染器按级别着色（黑飞 NO_FLIGHT_PLAN 走专属黑色）
  const meta = droneMeta.get(id)
  if (meta && meta.alertLevel) data.alertLevel = meta.alertLevel
  if (meta && meta.alertType) data.alertType = meta.alertType

  const now = Date.now()
  lastSeen.set(id, now)

  let slot = droneMap.get(id)
  if (slot === undefined) {
    if (nextSlot < MAX_DRONES) {
      // 未达上限：分配新槽位，不足则批量扩容
      slot = nextSlot++
      droneMap.set(id, slot)
      slotToSn[slot] = id
      if (slot >= renderer.getCount()) renderer.addDrones(Math.min(500, MAX_DRONES - renderer.getCount()))
    } else {
      // 已达上限：复用最久未更新的槽位（LRU）
      let oldestSn = null, oldestT = Infinity
      for (const [sn2, s2] of droneMap) {
        const t = lastSeen.get(sn2) || 0
        if (t < oldestT) { oldestT = t; oldestSn = sn2; slot = s2 }
      }
      if (oldestSn) {
        droneMap.delete(oldestSn)
        lastSeen.delete(oldestSn)
        droneMeta.delete(oldestSn)
      }
      droneMap.set(id, slot)
      slotToSn[slot] = id
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
  if (mode.value === 'standard') {
    // 自愈：WS 消息流中断（handler 被生命周期竞态摘除）时强制重挂并补发订阅
    if (lastWsMsgAt && Date.now() - lastWsMsgAt > 10000) {
      console.warn('[FlightMonitor] WS 消息流中断，重挂订阅')
      if (unsubscribeWs) unsubscribeWs()
      unsubscribeWs = null
      if (offWsStatus) { offWsStatus(); offWsStatus = null }
      connectWebSocket()
      sendViewport(true)
    }
    if (!renderer.isInitialized()) {
      // 自愈：快照/竞态可能绕过缓冲初始化，只要槽位映射存在就按槽位数重试 init
      if (droneMap.size > 0 && !initStarted && !initTimer) {
        initStarted = true
        renderer.init(droneMap.size).catch(() => {}).finally(() => { initStarted = false })
      }
      return
    }
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
  // 标准模式也上报视锥：服务端按视野裁剪遥测/告警，省带宽省渲染
  camListener = viewer.camera.changed.addEventListener(() => sendViewport(false))
  statsTimer = setInterval(updateStats, 2000)
  loadLayers()
  loadSnapshot()
  replayActiveAlarms()
  loadSuppressRules()
  sendViewport(true)
  mainLoop()
})

onUnmounted(() => {
  cancelAnimationFrame(rafId)
  clearTimeout(initTimer); clearInterval(statsTimer)
  clearTimeout(fleetWsReconnectTimer)
  if (camListener) { camListener(); camListener = null }
  if (unsubscribeWs) { unsubscribeWs(); unsubscribeWs = null }
  if (offWsStatus) { offWsStatus(); offWsStatus = null }
  clearInterval(statsPollTimer)
  if (fleetWs) fleetWs.close()
  if (fleetWorker) fleetWorker.terminate()
  if (labelTimer) { clearInterval(labelTimer); labelTimer = null }
  millionRenderer.destroy()
  renderer.destroy()
  if (viewer) { viewer.destroy(); viewer = null }
})
</script>

<style scoped>
.flight-monitor { width: 100%; height: 100%; position: relative; }
.cesium-container { width: 100%; height: 100%; }
.top-stats { position: absolute; top: 10px; left: 50%; transform: translateX(-50%); background: rgba(6, 12, 22, 0.78); color: #22d3ee; border: 1px solid rgba(34, 211, 238, 0.35); text-shadow: 0 0 8px rgba(34, 211, 238, 0.5); padding: 6px 18px; border-radius: 6px; font-size: 14px; display: flex; gap: 24px; z-index: 10; backdrop-filter: blur(6px); }
.toolbar { position: absolute; top: 10px; right: 10px; z-index: 10; display: flex; gap: 8px; flex-direction: column; }
.toolbar button { background: rgba(6, 12, 22, 0.72); color: #c8d5e6; border: 1px solid #223350; padding: 6px 12px; border-radius: 4px; cursor: pointer; font-size: 13px; transition: border-color .2s, color .2s; backdrop-filter: blur(6px); }
.toolbar button:hover { border-color: #2f81f7; color: #fff; }
.toolbar button.active { background: rgba(47, 129, 247, 0.25); border-color: #22d3ee; color: #7ce8ff; box-shadow: 0 0 10px rgba(34, 211, 238, 0.35); }
.toolbar button:disabled { opacity: 0.5; cursor: wait; }
/* 生图工具图标：黑底图用 screen 混合融入按钮 */
.tb-ic { width: 15px; height: 15px; mix-blend-mode: screen; border-radius: 3px; vertical-align: -3px; margin-right: 4px; }
.scale-group { display: flex; flex-direction: column; gap: 4px; }
.scale-group button { background: rgba(6, 12, 22, 0.72); color: #9ecbff; border: 1px solid #223350; padding: 5px 12px; border-radius: 4px; cursor: pointer; font-size: 13px; }
.scale-group button.active { background: rgba(47, 129, 247, 0.25); border-color: #2f81f7; color: #fff; }
.side-panel {
  position: absolute; top: 120px; left: 10px; width: 300px; max-height: 72vh;
  background: rgba(0,0,0,0.82); border: 1px solid #3a4a5f; border-radius: 8px;
  z-index: 15; color: #dfe8f3; display: flex; flex-direction: column;
}
.sp-head { display: flex; justify-content: space-between; align-items: center;
  padding: 8px 12px; border-bottom: 1px solid #3a4a5f; font-size: 13px; }
.sp-close { cursor: pointer; }
.sp-close:hover { color: #fff; }
.sp-body { padding: 10px 12px; overflow-y: auto; }
.layer-group { margin-bottom: 10px; }
.lg-title { font-size: 12px; color: #8fa2ba; margin-bottom: 4px; }
.lg-count { color: #5f7189; }
.layer-item { display: flex; align-items: center; gap: 6px; padding: 3px 4px;
  border-radius: 4px; cursor: pointer; font-size: 12px; }
.layer-item:hover { background: rgba(255,255,255,.06); }
.layer-name.nofly { color: #ff6b6b; }
.empty-hint { color: #5f7189; font-size: 12px; padding: 4px 0; }
.replay-stat { font-size: 12px; color: #8fa2ba; margin-bottom: 8px; }
.replay-btns { display: flex; gap: 6px; margin: 8px 0; }
.suppress-form { margin-bottom: 10px; }
.suppress-group { margin-bottom: 16px; }
.suppress-group-title { font-size: 13px; color: var(--el-text-color-secondary); margin-bottom: 8px; }
.suppress-item { display: flex; justify-content: space-between; align-items: center;
  font-size: 12px; padding: 4px 6px; border-radius: 4px; margin-bottom: 4px;
  background: rgba(255,255,255,.04); }
.drop-overlay { position: absolute; top: 44px; left: 50%; transform: translateX(-50%); background: rgba(120,0,0,0.8); color: #fff; padding: 4px 12px; border-radius: 6px; font-size: 12px; z-index: 10; font-family: monospace; }
.fps-overlay { position: absolute; top: 10px; left: 10px; z-index: 10; background: rgba(0,0,0,0.7); color: #0f0; font-family: monospace; padding: 4px 8px; border-radius: 4px; font-size: 13px; }
/* Cesium 自带 FPS 调试层默认在右上角(top:50px,right:10px)被工具栏遮挡，挪到左上角 rAF 旁 */
.flight-monitor :deep(.cesium-performanceDisplay-defaultContainer) { top: 10px !important; left: 150px !important; right: auto !important; z-index: 9; }

/* 点击无人机弹出的 DOM 标牌 */
.drone-label {
  position: absolute; z-index: 30; min-width: 230px;
  background: rgba(6, 13, 24, 0.92); border: 1px solid rgba(47, 129, 247, 0.45); border-left: 3px solid #22d3ee;
  border-radius: 8px; color: #dff3ff; font-size: 12px; padding: 8px 10px;
  box-shadow: 0 4px 18px rgba(0,0,0,0.5), 0 0 12px rgba(34, 211, 238, 0.18);
  backdrop-filter: blur(8px);
}
.dl-head { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid rgba(47, 129, 247, 0.4); padding-bottom: 4px; margin-bottom: 4px; }
.dl-sn { color: #6fd3ff; font-weight: bold; }
.dl-close { cursor: pointer; color: #89a; padding: 0 4px; }
.dl-close:hover { color: #fff; }
.dl-row { display: flex; gap: 8px; line-height: 1.8; }
.dl-k { color: #7ba0b5; min-width: 56px; }
.dl-phase { color: #6fd3ff; }
.alv-CRITICAL, .alv-MAJOR, .alv-EMERGENCY { color: #ff5252; font-weight: bold; }
.alv-WARNING, .alv-MINOR, .alv-SERIOUS { color: #ffb74d; }
.alv-NONE { color: #7ecb7e; }
/* 黑飞专属：黑色徽标（深色面板上以黑底白字呈现，与无人机黑点/黑图标呼应） */
.alv-HF {
  display: inline-block; background: #0d0d0d; color: #fff;
  border: 1px solid #4a4a4a; border-radius: 3px; padding: 0 6px; font-weight: bold;
}
</style>
