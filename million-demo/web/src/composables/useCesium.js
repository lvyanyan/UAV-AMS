/**
 * Cesium viewer 初始化 —— 复用主项目 _useCesium.js 模式
 *   - imageryProvider:false + 手动加高德卫星(style=6) + 标注(style=8)
 *   - EllipsoidTerrainProvider（扁球，避免 Intel GPU 地形坑）
 *   - contextOptions 强制 WebGL1（Intel 核显兼容）
 *   - 关 fog / skyAtmosphere / 阴影，最大化渲染性能
 *   - 暗黑科技风：skyBox 关闭、深色背景、无光照
 */
import * as Cesium from 'cesium'

export function useCesium(viewerRef) {
  let _fpsOff = null

  async function initViewer(container) {
    if (!container) return
    const viewer = new Cesium.Viewer(container, {
      imageryProvider: false,
      terrainProvider: new Cesium.EllipsoidTerrainProvider(),
      creditContainer: document.createElement('div'),
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
      contextOptions: {
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
    viewerRef.value = viewer

    // 高德卫星底图 + 标注层（无需 Ion token）
    viewer.imageryLayers.removeAll()
    viewer.imageryLayers.addImageryProvider(
      new Cesium.UrlTemplateImageryProvider({
        url: 'https://webst0{s}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}',
        subdomains: ['1', '2', '3', '4'],
        maximumLevel: 18
      })
    )
    viewer.imageryLayers.addImageryProvider(
      new Cesium.UrlTemplateImageryProvider({
        url: 'https://webst0{s}.is.autonavi.com/appmaptile?style=8&x={x}&y={y}&z={z}',
        subdomains: ['1', '2', '3', '4'],
        maximumLevel: 18
      })
    )

    if (viewer.scene.fog) viewer.scene.fog.enabled = false
    if (viewer.scene.skyAtmosphere) viewer.scene.skyAtmosphere.show = false
    if (viewer.scene.globe) {
      viewer.scene.globe.showGroundAtmosphere = false
      viewer.scene.globe.enableLighting = false
    }
    if (viewer.scene.globe.dynamicAtmosphereLighting) {
      viewer.scene.globe.dynamicAtmosphereLighting = false
    }
    viewer.scene.light = new Cesium.DirectionalLight({
      direction: new Cesium.Cartesian3(1, 0, 0),
      intensity: 0.5
    })

    if (viewer.scene.postProcessStages.fxaa) {
      viewer.scene.postProcessStages.fxaa.enabled = true
    }

    // === 暗黑科技风 ===
    viewer.scene.skyBox.show = false
    viewer.scene.backgroundColor = Cesium.Color.fromCssColorString('#0d1117')
    viewer.scene.globe.baseColor = Cesium.Color.fromCssColorString('#0a0e15')

    // Cesium 原生 debug FPS（左上角，比自定义 rAF 计数更准）
    viewer.scene.debugShowFramesPerSecond = true

    // 默认视角：北京上空 15km，俯视 -30°
    viewer.camera.setView({
      destination: Cesium.Cartesian3.fromDegrees(116.4074, 39.9042, 15000),
      orientation: {
        heading: Cesium.Math.toRadians(0),
        pitch: Cesium.Math.toRadians(-30),
        roll: 0
      }
    })

    return viewer
  }

  function setDebugFps(enabled) {
    const v = viewerRef.value
    if (v && v.scene) v.scene.debugShowFramesPerSecond = !!enabled
  }

  function destroyViewer() {
    const v = viewerRef.value
    if (_fpsOff) { _fpsOff(); _fpsOff = null }
    if (v) {
      try { v.destroy() } catch (e) {}
      viewerRef.value = null
    }
  }

  return { initViewer, destroyViewer, setDebugFps }
}
