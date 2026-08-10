/**
 * CesiumViewer.vue — V7.9 统一单次 init
 *
 * ★ 初始化：   POST /api/init → initFromData() → 全部图层
 * ★ 查询/筛选：QueryPanel 调 fetchInit → emit initData → onApplyFilter → 重建（暂关闭）
 * ★ plan 释放：fetchInit 内部自动 DELETE 旧 planId；
 *              离开页面时 onUnmounted + beforeunload 双重释放
 */
<template>
  <div class="cesium-viewer-container">
    <div ref="cesiumContainer" class="cesium-container"></div>

    <!-- 顶部操作栏 -->
    <div class="top-bar">
      <div class="system-title">
        <span class="title-icon">🛸</span>
        <span class="title-text">无人机全空域管控数字孪生系统</span>
      </div>
      <div class="fps-display" v-if="showFps">
        <span class="fps-dot" :class="{ 'fps-good': fps >= 50, 'fps-ok': fps >= 30 && fps < 50, 'fps-bad': fps < 30 }"></span>
        {{ fps }} FPS
      </div>
      <div v-if="filterDroneCount > 0" class="filter-badge" title="点击清除筛选" @click="clearDroneFilter">
        🔍 筛选 {{ filterDroneCount }} 架
        <span class="filter-clear">✕</span>
      </div>
    </div>

    <LayerManager :layers="layerStates" @toggle="onLayerToggle" />

    <TimelineControl
      :current-time="displayTime"
      :is-playing="displayIsPlaying"
      :speed-index="displaySpeedIndex"
      :progress="displayProgress"
      :drone-count="droneCount"
      :disabled="displayIsSeeking"
      :total-duration="timeline.totalDuration.value"
      @toggle-play="onTogglePlay"
      @set-speed="onSetSpeed"
      @seek="onSeek"
    />

    <QueryPanel
      v-if="FEATURE_FLAGS.QUERY_PANEL"
      ref="queryPanelRef"
      :time-start-ms="_timeStartMs"
      :time-end-ms="_timeEndMs"
      @select-drone="onQuerySelectDrone"
      @fly-to-drone="onFlyToDrone"
      @apply-filter="onApplyFilter"
    />

    <InfoPanel
      v-if="selectedDroneInfo && selection.isSelected.value"
      :drone="selectedDroneInfo"
      :position="labelPosition"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import * as Cesium from 'cesium'
import { useCesium } from '../composables/useCesium.js'
import { useTimeline } from '../composables/useTimeline.js'
import { useDroneSimulation } from '../composables/useDroneSimulation.js'
import { useGpuPoints } from '../composables/useGpuPoints.js'
import { useAirspaceGrid } from '../composables/useAirspaceGrid.js'
import { useAirspaces } from '../composables/useAirspaces.js'
import { useHeatmap } from '../composables/useHeatmap.js'
import { useRoutes } from '../composables/useRoutes.js'
import { useSelection } from '../composables/useSelection.js'
import { use3DTiles } from '../composables/use3DTiles.js'
import { LAYER_TYPES, LAYER_DEFAULTS, FEATURE_FLAGS } from '../utils/constants.js'
import { fetchInit, releaseCurrentPlan } from '../utils/apiService.js'
import { CESIUM_CONFIG } from '../config/cesiumConfig.js'

import LayerManager from './LayerManager.vue'
import TimelineControl from './TimelineControl.vue'
import InfoPanel from './InfoPanel.vue'
import QueryPanel from './QueryPanel.vue'

const cesiumContainer = ref(null)
const queryPanelRef = ref(null)

const { viewer, isReady, fps, initViewer, destroyViewer } = useCesium()
const timeline = useTimeline()
const droneSim = useDroneSimulation()
const gpuPoints = useGpuPoints(viewer)
const airspaceGrid = useAirspaceGrid(viewer)
const airspaces = useAirspaces(viewer)
const heatmap = useHeatmap(viewer)
const routes = useRoutes(viewer)
const selection = useSelection(viewer)
const tiles3d = use3DTiles(viewer)

const { droneCount, selectedDroneInfo } = droneSim

const displayTime = ref(0)
const displayProgress = ref(0)
const displayIsPlaying = ref(false)
const displaySpeedIndex = ref(1)
const displayIsSeeking = ref(false)

const filterDroneCount = computed(() => {
  const ids = droneSim.filterDroneIds.value
  return ids ? ids.length : 0
})

const layerStates = ref([
  { ...LAYER_DEFAULTS[LAYER_TYPES.DRONES], key: LAYER_TYPES.DRONES },
  { ...LAYER_DEFAULTS[LAYER_TYPES.GRID], key: LAYER_TYPES.GRID },
  { ...LAYER_DEFAULTS[LAYER_TYPES.HEATMAP], key: LAYER_TYPES.HEATMAP },
  { ...LAYER_DEFAULTS[LAYER_TYPES.ROUTES], key: LAYER_TYPES.ROUTES },
  { ...LAYER_DEFAULTS[LAYER_TYPES.TRAILS], key: LAYER_TYPES.TRAILS },
  { ...LAYER_DEFAULTS[LAYER_TYPES.AIRSPACES], key: LAYER_TYPES.AIRSPACES },
  { ...LAYER_DEFAULTS[LAYER_TYPES.TILES_3D], key: LAYER_TYPES.TILES_3D }
])

const labelPosition = ref({ x: 0, y: 0 })
const showFps = ref(CESIUM_CONFIG.system.showFps)

let postRenderRemoveCallback = null
let isDestroyed = false
let _lastFrameTime = performance.now()
let _lastPacketIndex = -1

let _timeStartMs = 0
let _timeEndMs = 0

// ★ beforeunload：标签页关闭时释放 plan（fetch + keepalive 保证可靠性）
let _planIdForUnload = null

function _setupBeforeUnload() {
  window.addEventListener('beforeunload', _handleBeforeUnload)
}
function _handleBeforeUnload() {
  if (_planIdForUnload) {
    fetch(`/api/plan/${_planIdForUnload}`, { method: 'DELETE', keepalive: true })
  }
}

function syncDisplay() {
  displayTime.value = timeline.currentTime.value
  displayProgress.value = timeline.progress.value
  displayIsPlaying.value = timeline.isPlaying.value
  displaySpeedIndex.value = timeline.speedIndex.value
  displayIsSeeking.value = timeline.isSeeking.value
}

function onPostRender() {
  if (isDestroyed || !viewer.value) return

  const now = performance.now()
  const realDelta = (now - _lastFrameTime) / 1000
  _lastFrameTime = now

  try {
    timeline.update(realDelta)
    syncDisplay()
    droneSim.checkPacketBoundary(timeline.currentTime.value)

    const currentPacketIdx = droneSim.getLoadedPacketIndex()
    if (currentPacketIdx !== _lastPacketIndex) {
      _lastPacketIndex = currentPacketIdx
      airspaces.updateTimeAirspaces(droneSim.timeAirspaces.value)
      routes.updateTimeRoutes(droneSim.timeRoutes.value)
    }

    airspaces.updateTimeAirspaceVisibility(timeline.currentTime.value)
    droneSim.updatePositions(timeline.currentTime.value)

    const cartArray = droneSim.getCartArray()
    if (cartArray) {
      gpuPoints.updatePositions(cartArray)

      const snToSlot = droneSim.getDeviceSnToSlot()
      const alarmList = droneSim.alarms.value
      if (alarmList && snToSlot.size > 0) {
        gpuPoints.applyAlertIcons(alarmList, timeline.currentTime.value, snToSlot)
      }

      routes.updateTrails(cartArray, timeline.currentTime.value)
    }

    if (selection.isSelected.value && selectedDroneInfo.value) {
      const snToSlot = droneSim.getDeviceSnToSlot()
      const slotIdx = snToSlot.get(droneSim.selectedDroneId.value)
      const ca = droneSim.getCartArray()
      if (slotIdx !== undefined && ca && ca[slotIdx]) {
        selection.updateLabelPosition(ca[slotIdx])
        labelPosition.value = { ...selection.selectedPosition.value }
      }
    }
  } catch (e) { /* 静默 */ }
}

function onPick(slotIndex) {
  gpuPoints.highlight(slotIndex)
  const snList = droneSim.getDeviceSnList()
  if (slotIndex >= 0 && slotIndex < snList.length) {
    droneSim.selectedDroneId.value = snList[slotIndex]
  } else {
    droneSim.selectedDroneId.value = null
  }
}

function onLayerToggle(key, visible) {
  switch (key) {
    case LAYER_TYPES.DRONES:
      if (!visible) gpuPoints.destroy()
      else if (isReady.value && viewer.value) gpuPoints.init(droneCount.value)
      break
    case LAYER_TYPES.GRID:    airspaceGrid.setVisible(visible); break
    case LAYER_TYPES.HEATMAP: heatmap.setVisible(visible); break
    case LAYER_TYPES.ROUTES:  routes.setRoutesVisible(visible); break
    case LAYER_TYPES.TRAILS:  routes.setTrailsVisible(visible); break
    case LAYER_TYPES.AIRSPACES: airspaces.setVisible(visible); break
    case LAYER_TYPES.TILES_3D:
      tiles3d.setVisible(visible)
      if (visible) tiles3d.load()
      break
  }
}

function onTogglePlay() { timeline.togglePlay() }
function onSetSpeed(index) { timeline.setSpeed(index) }

async function onSeek(percent) {
  if (timeline.isSeeking.value) return
  timeline.isSeeking.value = true

  timeline.seekTo(percent)
  syncDisplay()

  await droneSim.seekToTime(timeline.currentTime.value)

  _lastPacketIndex = droneSim.getLoadedPacketIndex()
  airspaces.updateTimeAirspaces(droneSim.timeAirspaces.value)
  routes.updateTimeRoutes(droneSim.timeRoutes.value)
  airspaces.updateTimeAirspaceVisibility(timeline.currentTime.value)

  if (gpuPoints.isInitialized()) {
    routes.resetTrails()
    const seekTime = timeline.currentTime.value
    const steps = 40
    const stepSec = 0.5
    for (let i = 0; i <= steps; i++) {
      const t = Math.max(0, seekTime - (steps - i) * stepSec)
      droneSim.updatePositions(t)
      const arr = droneSim.getCartArray()
      if (arr) routes.updateTrails(arr, t, true)
    }

    droneSim.updatePositions(timeline.currentTime.value)
    const finalArr = droneSim.getCartArray()
    if (finalArr) gpuPoints.updatePositions(finalArr)

    const snToSlot = droneSim.getDeviceSnToSlot()
    const alarmList = droneSim.alarms.value
    if (alarmList && snToSlot.size > 0) {
      gpuPoints.applyAlertIcons(alarmList, timeline.currentTime.value, snToSlot)
    }
  }

  nextTick(() => { timeline.isSeeking.value = false; syncDisplay() })
}

// ── 查询面板事件（暂关闭，FEATURE_FLAGS.QUERY_PANEL = false）──

/** ★ 定位：飞到无人机当前位置，相机拉低 */
function onFlyToDrone(droneId) {
  if (!viewer.value) return

  const snToSlot = droneSim.getDeviceSnToSlot()
  const slotIdx = snToSlot.get(droneId)
  const ca = droneSim.getCartArray()

  if (slotIdx !== undefined && ca && ca[slotIdx]) {
    const cartographic = Cesium.Cartographic.fromCartesian(ca[slotIdx])
    const lng = Cesium.Math.toDegrees(cartographic.longitude)
    const lat = Cesium.Math.toDegrees(cartographic.latitude)

    viewer.value.camera.flyTo({
      destination: Cesium.Cartesian3.fromDegrees(lng, lat, 800),
      orientation: {
        heading: Cesium.Math.toRadians(0),
        pitch: Cesium.Math.toRadians(-60),
        roll: 0
      },
      duration: 1.2
    })
  }
}

async function onQuerySelectDrone(droneId) {
  const snToSlot = droneSim.getDeviceSnToSlot()
  const slotIdx = snToSlot.get(droneId)

  if (slotIdx !== undefined) {
    droneSim.selectedDroneId.value = droneId
    gpuPoints.highlight(slotIdx)

    const ca = droneSim.getCartArray()
    if (ca && ca[slotIdx]) {
      selection.isSelected.value = true
      selection.updateLabelPosition(ca[slotIdx])
      labelPosition.value = { ...selection.selectedPosition.value }
    }
  } else {
    droneSim.selectedDroneId.value = droneId
  }
}

/**
 * ★ V7.9 统一单次 init：接收 QueryPanel 传来的完整 initData，直接重建
 *   fetchInit 已由 QueryPanel 调用（内部自动 DELETE 旧 planId）
 */
async function onApplyFilter(initData) {
  if (!initData) return

  _planIdForUnload = initData.planId

  const actualCount = await droneSim.initFromData(initData)
  await gpuPoints.init(actualCount)
  routes.initTrails(actualCount)

  _lastPacketIndex = 0
  airspaces.updateTimeAirspaces(droneSim.timeAirspaces.value)
  routes.updateTimeRoutes(droneSim.timeRoutes.value)

  // 清除被筛选掉的无人机的选中状态
  if (droneSim.selectedDroneId.value) {
    const snList = droneSim.getDeviceSnList()
    if (!snList.includes(droneSim.selectedDroneId.value)) {
      droneSim.selectedDroneId.value = null
      gpuPoints.highlight(-1)
      selection.isSelected.value = false
    }
  }

  await _reseedAfterReinit()
}

async function _reseedAfterReinit() {
  const currentTime = timeline.currentTime.value

  airspaces.updateTimeAirspaceVisibility(currentTime)

  droneSim.updatePositions(currentTime)
  const finalArr = droneSim.getCartArray()
  if (finalArr) gpuPoints.updatePositions(finalArr)

  routes.resetTrails()
  const steps = 40
  const stepSec = 0.5
  for (let i = 0; i <= steps; i++) {
    const t = Math.max(0, currentTime - (steps - i) * stepSec)
    droneSim.updatePositions(t)
    const arr = droneSim.getCartArray()
    if (arr) routes.updateTrails(arr, t, true)
  }

  droneSim.updatePositions(currentTime)
  const arr = droneSim.getCartArray()
  if (arr) gpuPoints.updatePositions(arr)

  const snToSlot = droneSim.getDeviceSnToSlot()
  const alarmList = droneSim.alarms.value
  if (alarmList && snToSlot.size > 0) {
    gpuPoints.applyAlertIcons(alarmList, currentTime, snToSlot)
  }
}

/** ★ 清除筛选：调 fetchInit 无筛选 → 全量重建 */
async function clearDroneFilter() {
  const initData = await fetchInit({
    timeStart: _timeStartMs,
    timeEnd: _timeEndMs
  })
  if (initData) await onApplyFilter(initData)
}

// ── 初始化 ──

onMounted(async () => {
  if (!cesiumContainer.value) return

  console.log('[CesiumViewer] 🚀 初始化，调用 POST /api/init ...')
  const initData = await fetchInit({})
  if (!initData) {
    console.error('[CesiumViewer] ❌ 初始化失败')
    return
  }

  _planIdForUnload = initData.planId
  _timeStartMs = initData.plan.timeOriginMs
  _timeEndMs = _timeStartMs + initData.plan.totalDuration * 1000

  console.log('[CesiumViewer] ✅ 初始化数据:', {
    planId: initData.planId?.slice(0, 8) + '...',
    plan: `${initData.plan.totalPackets}包 × ${initData.plan.packetDuration}s`,
    drones: initData.drones.length,
    timeRange: `${new Date(_timeStartMs).toISOString()} ~ ${new Date(_timeEndMs).toISOString()}`
  })

  _setupBeforeUnload()
  timeline.setTotalDuration(initData.plan.totalDuration)

  initViewer(cesiumContainer.value)
  await new Promise(r => setTimeout(r, 200))
  if (!viewer.value) return

  const actualCount = await droneSim.initFromData(initData)

  _lastFrameTime = performance.now()
  await gpuPoints.init(actualCount)

  await Promise.all([
    airspaceGrid.init(),
    airspaces.init(),
    heatmap.init(150),
    routes.init()
  ])
  routes.initTrails(actualCount)

  _lastPacketIndex = 0
  airspaces.updateTimeAirspaces(droneSim.timeAirspaces.value)
  routes.updateTimeRoutes(droneSim.timeRoutes.value)

  selection.init(onPick)

  postRenderRemoveCallback = viewer.value.scene.postRender.addEventListener(onPostRender)
  syncDisplay()
})

onUnmounted(() => {
  isDestroyed = true

  releaseCurrentPlan()
  _planIdForUnload = null
  window.removeEventListener('beforeunload', _handleBeforeUnload)

  if (postRenderRemoveCallback) { postRenderRemoveCallback(); postRenderRemoveCallback = null }
  selection.destroy()
  gpuPoints.destroy()
  airspaceGrid.destroy()
  airspaces.destroy()
  heatmap.destroy()
  routes.destroy()
  tiles3d.destroy()
  destroyViewer()
})
</script>

<style scoped>
.cesium-viewer-container { width: 100vw; height: 100vh; position: relative; overflow: hidden; }
.cesium-container { width: 100%; height: 100%; }

.top-bar {
  position: absolute; top: 20px; left: 50%; transform: translateX(-50%);
  display: flex; align-items: center; gap: 16px;
  background: rgba(10, 20, 40, 0.85); backdrop-filter: blur(12px);
  border: 1px solid rgba(0, 200, 255, 0.3); border-radius: 12px;
  padding: 10px 24px; z-index: 100; user-select: none; white-space: nowrap;
}
.system-title { display: flex; align-items: center; gap: 10px; }
.title-icon { font-size: 24px; }
.title-text {
  color: #e0f0ff; font-size: 18px; font-weight: 600; letter-spacing: 2px;
  background: linear-gradient(90deg, #00c8ff, #00ff88);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text;
}
.fps-display {
  display: flex; align-items: center; gap: 6px; color: #8ab4f8;
  font-size: 13px; font-family: 'Courier New', monospace;
  padding-left: 16px; border-left: 1px solid rgba(255,255,255,0.15);
}
.fps-dot { width: 8px; height: 8px; border-radius: 50%; background: #00ff88; }
.fps-good { background: #00ff88; }
.fps-ok { background: #ffaa00; }
.fps-bad { background: #ff4466; }

.filter-badge {
  display: flex; align-items: center; gap: 6px;
  padding: 4px 12px; border-radius: 14px;
  background: rgba(255, 100, 68, 0.15);
  border: 1px solid rgba(255, 100, 68, 0.35);
  color: #ffaa88; font-size: 12px; cursor: pointer;
  transition: background 0.2s;
}
.filter-badge:hover { background: rgba(255, 100, 68, 0.3); }
.filter-clear { font-size: 10px; opacity: 0.7; }
</style>
