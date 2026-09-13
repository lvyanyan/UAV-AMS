<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">{{ $t('replay.title') }}</h2>
        <div class="page-subtitle">{{ $t('replay.subtitle', { loaded: msgs.length, drones: snCount }) }}</div>
      </div>
      <div class="header-actions">
        <el-radio-group v-model="windowSec" @change="loadHistory">
          <el-radio-button :value="60">{{ $t('replay.last1') }}</el-radio-button>
          <el-radio-button :value="300">{{ $t('replay.last5') }}</el-radio-button>
          <el-radio-button :value="600">{{ $t('replay.last10') }}</el-radio-button>
        </el-radio-group>
        <el-button type="primary" :disabled="msgs.length < 2" @click="start">{{ playing ? $t('replay.pause') : (idx > 0 && idx < msgs.length ? $t('replay.resume') : $t('replay.start')) }}</el-button>
        <el-select :model-value="speed" style="width:90px" @update:model-value="changeSpeed">
          <el-option label="1x" :value="1" /><el-option label="4x" :value="4" />
          <el-option label="16x" :value="16" /><el-option label="64x" :value="64" />
        </el-select>
        <el-button type="danger" @click="stop">{{ $t('replay.stop') }}</el-button>
      </div>
    </div>

    <div ref="mapEl" class="replay-map" />

    <el-card shadow="never" class="table-card" style="margin-top:12px">
      <el-slider v-model="progress" :min="0" :max="100" :step="0.1" @input="seek" />
      <div class="mr-stats">
        <span>{{ $t('replay.virtualTime') }}：{{ virtualTime }}</span>
        <span>{{ $t('replay.progress') }}：{{ progress.toFixed(1) }}%</span>
        <span>{{ $t('replay.applied') }}：{{ idx }} / {{ msgs.length }}</span>
        <span>{{ $t('replay.status') }}：{{ $t(statusKey, { n: statusN }) }}</span>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import * as Cesium from 'cesium'
import 'cesium/Build/Cesium/Widgets/widgets.css'
import { CESIUM_CONFIG } from '@/config/cesiumConfig.js'

const { t } = useI18n()

const REALTIME = 'http://localhost:8090'
const windowSec = ref(60)
const msgs = ref([])
const idx = ref(0)
const progress = ref(0)
const speed = ref(16)
const playing = ref(false)
// 回放状态文案：存 i18n key + 可选参数 n，模板处 $t 渲染（语言切换即时生效）
const statusKey = ref('replay.st.notLoaded')
const statusN = ref(0)

const mapEl = ref(null)
let viewer = null
let ptCol = null
const snPoints = new Map()   // sn -> PointPrimitive

let timer = null
let startReal = 0
let startVirtual = 0
let pauseElapsed = 0

const snCountRef = ref(0)
const snCount = computed(() => snCountRef.value)
const virtualTime = computed(() => {
  if (!msgs.value.length) return '--'
  const span = msgs.value[msgs.value.length - 1].ts - msgs.value[0].ts || 1
  const v = msgs.value[0].ts + span * (progress.value / 100)
  const d = new Date(v)
  const p = n => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
})

// ── 地图 ──
function initMap() {
  Cesium.Ion.defaultAccessToken = CESIUM_CONFIG?.ionToken || ''
  viewer = new Cesium.Viewer(mapEl.value, {
    imageryProvider: false,
    terrainProvider: new Cesium.EllipsoidTerrainProvider(),
    creditContainer: document.createElement('div'),
    timeline: false, animation: false, baseLayerPicker: false,
    fullscreenButton: false, geocoder: false, homeButton: false,
    infoBox: false, sceneModePicker: false, navigationHelpButton: false,
    selectionIndicator: false, scene3DOnly: true, shadows: false,
  })
  viewer.imageryLayers.removeAll()
  viewer.imageryLayers.addImageryProvider(new Cesium.UrlTemplateImageryProvider({
    url: 'https://webst0{s}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}',
    subdomains: ['1', '2', '3', '4'], maximumLevel: 18,
  }))
  ptCol = new Cesium.PointPrimitiveCollection({ scene: viewer.scene })
  viewer.scene.primitives.add(ptCol)
  viewer.camera.setView({
    destination: Cesium.Cartesian3.fromDegrees(116.4, 39.9, 60000),
  })
}

function ensurePoint(sn) {
  let p = snPoints.get(sn)
  if (!p) {
    p = ptCol.add({
      position: Cesium.Cartesian3.fromDegrees(0, 0),
      color: Cesium.Color.CORNFLOWERBLUE,
      pixelSize: 8,
      show: false,
      disableDepthTestDistance: Number.POSITIVE_INFINITY,
    })
    snPoints.set(sn, p)
  }
  return p
}

function applyMsg(m) {
  const p = ensurePoint(m.sn)
  p.position = Cesium.Cartesian3.fromDegrees(m.lon, m.lat, m.alt || 0)
  if (!p.show) p.show = true
}

// ── 数据与回放 ──
async function loadHistory() {
  stop()
  try {
    const res = await fetch(`${REALTIME}/history?seconds=${windowSec.value}`)
    const json = await res.json()
    const list = (json.msgs || []).slice().sort((a, b) => a.ts - b.ts)
    snCountRef.value = new Set(list.map(m => m.sn)).size
    msgs.value = list
    idx.value = 0
    progress.value = 0
    statusN.value = list.length
    statusKey.value = 'replay.st.loaded'
  } catch (e) { statusKey.value = 'replay.st.loadFailed' }
}

function start() {
  if (msgs.value.length < 2) return
  if (idx.value >= msgs.value.length) { idx.value = 0; progress.value = 0 }
  playing.value = true
  startReal = performance.now()
  startVirtual = msgs.value[Math.max(0, idx.value - 1)].ts + 1
  pauseElapsed = 0
  if (!timer) timer = setInterval(tick, 60)
  statusKey.value = 'replay.st.playing'
}
function tick() {
  if (!playing.value) return
  const virtual = startVirtual + pauseElapsed + (performance.now() - startReal) * speed.value
  const first = msgs.value[0].ts
  const span = msgs.value[msgs.value.length - 1].ts - first || 1
  while (idx.value < msgs.value.length && msgs.value[idx.value].ts <= virtual) {
    applyMsg(msgs.value[idx.value])
    idx.value++
  }
  progress.value = Math.min(100, ((virtual - first) / span) * 100)
  if (idx.value >= msgs.value.length) { playing.value = false; pauseElapsed += span; statusKey.value = 'replay.st.done' }
}
function pause() { playing.value = false; pauseElapsed += (performance.now() - startReal) * speed.value; startReal = performance.now(); statusKey.value = 'replay.st.paused' }
function resume() { playing.value = true; startReal = performance.now(); statusKey.value = 'replay.st.playing' }
function changeSpeed(v) {
  if (playing.value) {
    pauseElapsed += (performance.now() - startReal) * speed.value
    startReal = performance.now()
  }
  speed.value = v
}
function seek() {
  const span = msgs.value.length ? msgs.value[msgs.value.length - 1].ts - msgs.value[0].ts : 1
  const target = msgs.value[0].ts + span * (progress.value / 100)
  idx.value = 0
  while (idx.value < msgs.value.length && msgs.value[idx.value].ts <= target) {
    applyMsg(msgs.value[idx.value]); idx.value++
  }
  startVirtual = target
  startReal = performance.now()
}
function stop() {
  playing.value = false
  idx.value = 0
  progress.value = 0
  pauseElapsed = 0
  if (timer) { clearInterval(timer); timer = null }
  snPoints.forEach(p => { p.show = false })
  statusKey.value = 'replay.st.notLoaded'
}

onMounted(() => {
  initMap()
  loadHistory()
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (viewer) { try { viewer.destroy() } catch (e) {} viewer = null }
})
</script>

<style scoped>
.replay-map { height: calc(100vh - 300px); min-height: 420px; border-radius: 10px; overflow: hidden; border: 1px solid var(--el-border-color-lighter); }
.mr-stats { display: flex; gap: 24px; color: var(--el-text-color-secondary); font-size: 13px; }
</style>
