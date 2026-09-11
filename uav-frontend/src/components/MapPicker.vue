<template>
  <div ref="container" class="map-picker" :style="{ height }" />
</template>

<script setup>
/**
 * 轻量 Cesium 地图组件（高德底图，与飞行监控大屏同源）
 *  mode: 'point' 选点 | 'route' 折线 | 'polygon' 多边形
 *  modelValue: [[lon,lat], ...]
 *  editable: 点击地图加点；配合 expose 的 undo()/clear() 使用
 *  zones: [{ name, geoJson, kind }] 空域叠加层（no_fly 红色高亮）
 *  track: [[lon,lat], ...] 只读折线叠加（如计划航路）
 *  marks: [{ lon, lat, label }] 只读点叠加（如起降场）
 */
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as Cesium from 'cesium'
import 'cesium/Build/Cesium/Widgets/widgets.css'
import { CESIUM_CONFIG } from '@/config/cesiumConfig.js'

const props = defineProps({
  mode: { type: String, default: 'point' },
  modelValue: { type: Array, default: () => [] },
  editable: { type: Boolean, default: false },
  height: { type: String, default: '360px' },
  zones: { type: Array, default: () => [] },
  track: { type: Array, default: () => [] },
  marks: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue'])

const container = ref(null)
let viewer = null
let entities = null
let destroyed = false

function toCartesian3Arr(lonLats, alt = 0) {
  return lonLats.map(([lon, lat]) => Cesium.Cartesian3.fromDegrees(lon, lat, alt))
}

function render() {
  if (!viewer || !entities) return
  entities.removeAll()

  // 空域叠加层
  for (const z of props.zones || []) {
    try {
      const ring = JSON.parse(z.geoJson)?.coordinates?.[0]
      if (!Array.isArray(ring) || ring.length < 3) continue
      entities.add({
        polygon: {
          hierarchy: Cesium.Cartesian3.fromDegreesArray(ring.flat()),
          material: (z.kind === 'no_fly' ? Cesium.Color.RED : Cesium.Color.DODGERBLUE).withAlpha(0.22),
          outline: true,
          outlineColor: (z.kind === 'no_fly' ? Cesium.Color.RED : Cesium.Color.DODGERBLUE).withAlpha(0.9),
        },
      })
    } catch (e) { /* 忽略坏数据 */ }
  }

  // 只读折线叠加（计划航路）
  if (props.track?.length >= 2) {
    entities.add({
      polyline: {
        positions: toCartesian3Arr(props.track),
        width: 4,
        material: Cesium.Color.LIME,
        clampToGround: true,
      },
    })
  }

  // 只读点叠加（起降场）
  for (const m of props.marks || []) {
    entities.add({
      position: Cesium.Cartesian3.fromDegrees(m.lon, m.lat),
      point: { pixelSize: 12, color: Cesium.Color.LIME, outlineColor: Cesium.Color.BLACK, outlineWidth: 1 },
      label: { text: m.label || '', font: '12px sans-serif', fillColor: Cesium.Color.WHITE, pixelOffset: new Cesium.Cartesian2(0, -16), disableDepthTestDistance: Number.POSITIVE_INFINITY },
    })
  }

  // 自身点位
  const pts = props.modelValue || []
  if (props.mode === 'polygon' && pts.length >= 3) {
    entities.add({
      polygon: {
        hierarchy: Cesium.Cartesian3.fromDegreesArray(pts.flat()),
        material: Cesium.Color.ORANGE.withAlpha(0.25),
        outline: true,
        outlineColor: Cesium.Color.ORANGE,
      },
    })
  }
  if ((props.mode === 'route' || props.mode === 'polygon') && pts.length >= 2) {
    entities.add({
      polyline: {
        positions: toCartesian3Arr(props.mode === 'polygon' ? [...pts, pts[0]] : pts),
        width: 3,
        material: Cesium.Color.ORANGE,
        clampToGround: true,
      },
    })
  }
  pts.forEach(([lon, lat], i) => {
    entities.add({
      position: Cesium.Cartesian3.fromDegrees(lon, lat),
      point: {
        pixelSize: props.mode === 'point' ? 14 : 10,
        color: Cesium.Color.ORANGE,
        outlineColor: Cesium.Color.WHITE,
        outlineWidth: 2,
        disableDepthTestDistance: Number.POSITIVE_INFINITY,
      },
      label: props.mode === 'point' ? undefined : {
        text: String(i + 1),
        font: '12px sans-serif',
        fillColor: Cesium.Color.WHITE,
        pixelOffset: new Cesium.Cartesian2(0, -16),
        disableDepthTestDistance: Number.POSITIVE_INFINITY,
      },
    })
  })
}

function fitView() {
  if (!viewer) return
  const all = [
    ...(props.modelValue || []),
    ...(props.track || []),
    ...(props.marks || []).map(m => [m.lon, m.lat]),
  ]
  if (!all.length) return
  const lons = all.map(p => p[0]), lats = all.map(p => p[1])
  const west = Math.min(...lons), east = Math.max(...lons)
  const south = Math.min(...lats), north = Math.max(...lats)
  const span = Math.max(east - west, north - south, 0.02)
  viewer.camera.setView({
    destination: Cesium.Cartesian3.fromDegrees(
      (west + east) / 2, (south + north) / 2 - span * 0.2, Math.max(span * 3e5, 8000)),
  })
}

function initViewer() {
  Cesium.Ion.defaultAccessToken = CESIUM_CONFIG?.ionToken || ''
  viewer = new Cesium.Viewer(container.value, {
    imageryProvider: false,
    terrainProvider: new Cesium.EllipsoidTerrainProvider(),
    creditContainer: document.createElement('div'),
    timeline: false, animation: false, baseLayerPicker: false,
    fullscreenButton: false, geocoder: false, homeButton: false,
    infoBox: false, sceneModePicker: false, navigationHelpButton: false,
    selectionIndicator: false, scene3DOnly: true, shadows: false,
  })
  entities = viewer.entities
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

  viewer.screenSpaceEventHandler.setInputAction((click) => {
    if (!props.editable) return
    const cartesian = viewer.camera.pickEllipsoid(click.position, Cesium.Ellipsoid.WGS84)
    if (!cartesian) return
    const carto = Cesium.Cartographic.fromCartesian(cartesian)
    const lon = +Cesium.Math.toDegrees(carto.longitude).toFixed(5)
    const lat = +Cesium.Math.toDegrees(carto.latitude).toFixed(5)
    if (props.mode === 'point') emit('update:modelValue', [[lon, lat]])
    else emit('update:modelValue', [...(props.modelValue || []), [lon, lat]])
  }, Cesium.ScreenSpaceEventType.LEFT_CLICK)
}

onMounted(() => {
  initViewer()
  render()
  fitView()
})

onUnmounted(() => {
  destroyed = true
  if (viewer) { try { viewer.destroy() } catch (e) {} viewer = null }
})

watch(() => props.modelValue, render, { deep: true })
watch(() => [props.zones, props.track, props.marks], render, { deep: true })

defineExpose({
  undo() {
    const pts = props.modelValue || []
    emit('update:modelValue', pts.slice(0, -1))
  },
  clear() {
    emit('update:modelValue', [])
  },
  fit: fitView,
})
</script>

<style scoped>
.map-picker { width: 100%; border-radius: 8px; overflow: hidden; border: 1px solid var(--el-border-color-lighter); }
</style>
