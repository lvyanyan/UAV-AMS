/**
 * Cesium Viewer 初始化与管理
 */
import { ref, shallowRef, onUnmounted } from 'vue'
import * as Cesium from 'cesium'
import { CESIUM_CONFIG } from '../config/cesiumConfig.js'

Cesium.Ion.defaultAccessToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJqdGkiOiI5NWYyMDg1Yy1hYjE4LTRiYmItYjM3Yi0yMmZhNjI4OTI5N2MiLCJpZCI6MjU5NjI0LCJpYXQiOjE3MzQ1Mjk0NDZ9.1h9V-8fVJmCFVylc-tFAnz5kFJ9Z2yS6GqZkCElYz7Q'

const viewer = shallowRef(null)
const isReady = ref(false)

/**
 * 根据配置创建底图 ImageryProvider
 * 支持单个 provider 或 provider 数组（叠加）
 */
function createImageryProviders() {
  const cfg = CESIUM_CONFIG.imageryProvider

  // 如果配置了 imageryProviders 数组（多层叠加），逐个创建
  if (CESIUM_CONFIG.imageryProviders && Array.isArray(CESIUM_CONFIG.imageryProviders)) {
    return CESIUM_CONFIG.imageryProviders.map(p => createSingleProvider(p))
  }

  // 单个 provider
  if (cfg) {
    return createSingleProvider(cfg)
  }

  // 默认使用 ArcGIS 卫星影像
  return new Cesium.UrlTemplateImageryProvider({
    url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
    credit: 'Esri, Maxar, Earthstar Geographics',
    maximumLevel: 19
  })
}

function createSingleProvider(cfg) {
  const type = cfg.type || 'urlTemplate'

  // ── WMTS（天地图标准协议） ──
  if (type === 'wmts') {
    return new Cesium.WebMapTileServiceImageryProvider({
      url: cfg.url,
      layer: cfg.layer || 'img',
      style: cfg.style || 'default',
      format: cfg.format || 'tiles',
      tileMatrixSetID: cfg.tileMatrixSetID || 'w',
      credit: cfg.credit || '',
      maximumLevel: cfg.maximumLevel || 18,
      tileWidth: cfg.tileWidth || 256,
      tileHeight: cfg.tileHeight || 256,
      parameters: cfg.parameters || {}
    })
  }

  // ── URL 模板（TMS 风格） ──
  if (type === 'urlTemplate') {
    return new Cesium.UrlTemplateImageryProvider({
      url: cfg.url,
      credit: cfg.credit || '',
      maximumLevel: cfg.maximumLevel || 18
    })
  }

  // ── WMS ──
  if (type === 'wms') {
    return new Cesium.WebMapServiceImageryProvider({
      url: cfg.url,
      layers: cfg.layers || '0',
      credit: cfg.credit || ''
    })
  }

  // 兜底
  return new Cesium.UrlTemplateImageryProvider({
    url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
    credit: 'Esri, Maxar, Earthstar Geographics',
    maximumLevel: 19
  })
}

export function useCesium() {
  let cesiumContainer = null
  let animationFrameId = null
  let postRenderRemoveCallback = null
  let isDestroyed = false

  function initViewer(container) {
    if (viewer.value) return
    cesiumContainer = container

    // 从配置创建底图
    const imageryProvider = createImageryProviders()
    const terrainProvider = new Cesium.EllipsoidTerrainProvider()

    // =================================================================
    // 【Intel GPU 兼容性修复】
    //
    // 问题：某些 Intel 核显 + Chrome 的组合下，WebGL 2 存在驱动 bug，
    //      导致 maxTextureSize = 0，引发：
    //        - "Width must be ≤ max texture size (0)"
    //        - "Vertex texture fetch support is required"
    //
    // 修复：强制使用 WebGL 1（requestWebgl2: false）
    //       浏览器会回退到 WebGL 1，Intel 核显在 WebGL 1 下正常工作。
    // =================================================================
    const v = new Cesium.Viewer(container, {
      imageryProvider,
      terrainProvider,
      timeline: false,
      animation: false,
      baseLayerPicker: false,
      fullscreenButton: false,
      geocoder: false,
      homeButton: false,
      infoBox: false,
      sceneModePicker: false,
      navigationHelpButton: false,
      selectionIndicator: false,
      scene3DOnly: true,
      shadows: false,
      terrainShadows: Cesium.ShadowMode.DISABLED,
      imageCacheResizeDelayInSeconds: 30,
      // ★ 关键修复：强制 WebGL 1，避免 Intel GPU WebGL 2 驱动 bug
      contextOptions: {
        requestWebgl2: false,
        webgl: {
          alpha: true,
          antialias: true,
          depth: true,
          stencil: false,
          premultipliedAlpha: true,
          preserveDrawingBuffer: false,
          failIfMajorPerformanceCaveat: false,
          powerPreference: 'high-performance'
        }
      }
    })

    // ================================================================
    // ★ 叠加标注图层（如高德 style=8 透明标注层）
    // ================================================================
    const overlayCfg = CESIUM_CONFIG.labelOverlay
    if (overlayCfg) {
      const overlayProvider = createSingleProvider(overlayCfg)
      const overlayLayer = v.imageryLayers.addImageryProvider(overlayProvider)
      if (overlayCfg.alpha !== undefined) {
        overlayLayer.alpha = overlayCfg.alpha
      }
    }

    // 关闭不必要的特效
    if (v.scene.fog) v.scene.fog.enabled = false
    if (v.scene.skyAtmosphere) v.scene.skyAtmosphere.show = false
    if (v.scene.globe) {
      v.scene.globe.showGroundAtmosphere = false
      v.scene.globe.enableLighting = false
      v.scene.globe.depthTestAgainstTerrain = false
      v.scene.globe.showWaterEffect = false
    }
    v.shadows = false
    if (v.scene.postProcessStages.fxaa) {
      v.scene.postProcessStages.fxaa.enabled = true
    }

    // 设置默认视角
    const { longitude, latitude, height } = CESIUM_CONFIG.defaultView
    v.camera.setView({
      destination: Cesium.Cartesian3.fromDegrees(longitude, latitude, height),
      orientation: {
        heading: Cesium.Math.toRadians(0),
        pitch: Cesium.Math.toRadians(-30),
        roll: 0
      }
    })

    viewer.value = v
    isReady.value = true
    startFpsMonitor(v)
    return v
  }

  let fps = ref(60)
  let frameCount = 0
  let lastFpsTime = performance.now()

  function startFpsMonitor(v) {
    if (postRenderRemoveCallback) return
    postRenderRemoveCallback = v.scene.postRender.addEventListener(() => {
      frameCount++
      const now = performance.now()
      if (now - lastFpsTime >= 1000) {
        fps.value = Math.round(frameCount * 1000 / (now - lastFpsTime))
        frameCount = 0
        lastFpsTime = now
      }
    })
  }

  function getCameraDistance() {
    if (!viewer.value) return Infinity
    return viewer.value.camera.positionCartographic.height
  }

  function destroyViewer() {
    isDestroyed = true
    if (postRenderRemoveCallback) {
      postRenderRemoveCallback()
      postRenderRemoveCallback = null
    }
    if (animationFrameId) {
      cancelAnimationFrame(animationFrameId)
      animationFrameId = null
    }
    if (viewer.value) {
      viewer.value.scene.primitives.removeAll()
      viewer.value.destroy()
      viewer.value = null
      isReady.value = false
    }
  }

  onUnmounted(() => { destroyViewer() })

  return { viewer, isReady, fps, initViewer, destroyViewer, getCameraDistance }
}
