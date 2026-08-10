/**
 * Cesium 全局配置
 *
 * 当前启用：高德卫星影像（style=6）+ 标注叠加层（useCesium.js 中自动叠加）
 *
 * ── 如需用天地图 ──
 * 1. 去 https://console.tianditu.gov.cn 申请 token
 * 2. 取消下方「天地图 WMTS 卫星影像」注释
 * 3. 注释掉当前高德卫星配置
 * 4. 重启
 *
 * ── 如需用 ArcGIS ──
 * 取消 ArcGIS 卫星影像注释，注释掉当前高德卫星配置
 */
export const CESIUM_CONFIG = {
  // 默认视角：北京中心区域
  defaultView: {
    longitude: 116.397,
    latitude: 39.908,
    height: 15000
  },

  // 地形服务（关闭，使用平面地形避免 Intel GPU 兼容问题）
  terrain: null,

  // ================================================================
  // 当前底图 — 高德卫星影像（style=6，纯卫星无标注）
  //   标注叠加层在 useCesium.js 中自动叠加 style=8（透明标注层）
  // ================================================================
  imageryProvider: {
    type: 'urlTemplate',
    url: 'https://webst01.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}',
    credit: '高德卫星影像',
    maximumLevel: 18
  },

  // 标注叠加层配置（自动叠加在卫星影像上方）
  labelOverlay: {
    type: 'urlTemplate',
    url: 'https://webst01.is.autonavi.com/appmaptile?style=8&x={x}&y={y}&z={z}',
    credit: '高德标注',
    maximumLevel: 18,
    alpha: 1.0
  },

  // ================================================================
  // ArcGIS 卫星影像（免 token，全球高清，19 级）
  // ================================================================
  // imageryProvider: {
  //   type: 'urlTemplate',
  //   url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
  //   credit: 'Esri, Maxar, Earthstar Geographics',
  //   maximumLevel: 19
  // },

  // ── 天地图 WMTS 卫星影像（需有效 token，否则 418） ──
  // imageryProvider: {
  //   type: 'wmts',
  //   url: 'https://t0.tianditu.gov.cn/img_w/wmts',
  //   layer: 'img',
  //   style: 'default',
  //   format: 'tiles',
  //   tileMatrixSetID: 'w',
  //   credit: '天地图卫星影像',
  //   maximumLevel: 18,
  //   parameters: {
  //     tk: '请替换成你在 console.tianditu.gov.cn 申请的 token'
  //   }
  // },

  // 系统参数
  system: {
    maxDrones: 10000,         // 最大无人机数量
    heatmapResolution: 50,    // 热力图体素分辨率
    gridSize: 500,            // 空域网格大小(米)
    gridHeight: 50,           // 空域网格高度(米)
    gridExtent: 10000,        // 网格覆盖范围(米)
    autoRotate: false,        // 是否自动旋转
    showFps: true             // 显示FPS
  }
}
