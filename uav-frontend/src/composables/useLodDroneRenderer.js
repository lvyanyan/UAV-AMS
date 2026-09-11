/**
 * 无人机 LOD 分层渲染器 v3 —— 支持动态扩容 + 槽位上限防膨胀
 *
 *   🏙️ 中高空 (>5km)  → PointPrimitiveCollection（万级，真实遥测位置实时更新 + 告警等级着色）
 *   🚁 低空 (<5km)     → BillboardCollection（带预警色+航向，按需惰性创建）
 *
 * 注：BufferPointCollection 静态缓冲层不适配「每秒全量改位置」的动态机队，已移除。
 */

import * as Cesium from 'cesium'

const IMG_PATHS = {
  NORMAL: '/images/drones/default-fusion.png',
  WARNING: '/images/drones/warn-fusion.png',
  MINOR: '/images/drones/warn-fusion.png',
  MAJOR: '/images/drones/danger-fusion.png',
  CRITICAL: '/images/drones/danger-fusion.png',
  EMERGENCY: '/images/drones/danger-fusion.png',
  HF: '/images/drones/hf-fusion.png',
}

const LOD = { MID_ALT: 5000 }
const CENTER_LON = 116.4
const CENTER_LAT = 39.9
const SPREAD_RADIUS_DEG = 0.8

// 告警等级 → 颜色（缓存实例，避免频繁创建）
const _alertColorCache = {}
function alertPointColor(level) {
  const key = level || 'NORMAL'
  if (!_alertColorCache[key]) {
    if (['CRITICAL', 'MAJOR', 'EMERGENCY'].includes(key)) _alertColorCache[key] = Cesium.Color.fromCssColorString('#f56c6c')
    else if (['SERIOUS', 'WARNING'].includes(key)) _alertColorCache[key] = Cesium.Color.fromCssColorString('#e6a23c')
    else if (key === 'MINOR') _alertColorCache[key] = Cesium.Color.fromCssColorString('#409eff')
    else _alertColorCache[key] = Cesium.Color.CORNFLOWERBLUE
  }
  return _alertColorCache[key]
}

function spreadPos(i, total) {
  if (total <= 1) return Cesium.Cartesian3.fromDegrees(CENTER_LON, CENTER_LAT, 300)
  const angle = (i / total) * 2 * Math.PI
  const r = Math.sqrt(i / total) * SPREAD_RADIUS_DEG
  return Cesium.Cartesian3.fromDegrees(
    CENTER_LON + r * Math.cos(angle),
    CENTER_LAT + r * Math.sin(angle),
    100 + Math.random() * 400
  )
}

function loadImg(url) {
  return new Promise((resolve) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => resolve(img)
    img.onerror = () => { console.warn('[LOD] 图片加载失败:', url); resolve(null) }
    img.src = url
  })
}

function getCamH(v) {
  if (!v || v.isDestroyed()) return 0
  return Cesium.Cartographic.fromCartesian(v.camera.position).height
}

function safeDestroy(col) {
  if (!col) return false
  try { if (!col.isDestroyed()) { col.destroy(); return true } } catch (e) {}
  return false
}

export function useLodDroneRenderer(viewerRef) {
  let _ptCol = null    // PointPrimitiveCollection（中高空）
  let _bbCol = null    // BillboardCollection（低空，条目惰性创建）
  let _camOff = null

  let _level = 'mid'
  let _ready = false
  let _busy = false

  let _count = 0
  let _pos = []        // Cartesian3[]
  let _hdg = []        // number[]
  let _alvl = []       // string[]
  let _hf = []         // boolean[]
  let _imgN = null, _imgW = null, _imgD = null, _imgH = null
  let _bbNext = 0      // 低空 billboard 惰性创建游标

  function viewer() {
    const v = viewerRef.value
    return v && !v.isDestroyed() ? v : null
  }

  // ---- helpers ----
  function getPt(i) {
    try { if (_ptCol && !_ptCol.isDestroyed() && i < _ptCol.length) return _ptCol.get(i) } catch (e) {}
    return null
  }
  function getBb(i) {
    try { if (_bbCol && !_bbCol.isDestroyed() && i < _bbCol.length) return _bbCol.get(i) } catch (e) {}
    return null
  }

  function alarmImage(level, hf) {
    if (hf) return _imgH || _imgN
    if (['MAJOR', 'CRITICAL', 'EMERGENCY'].includes(level)) return _imgD || _imgN
    if (['SERIOUS', 'WARNING', 'MINOR'].includes(level)) return _imgW || _imgN
    return _imgN
  }

  // ---- 图元创建 ----
  function addPoint(i) {
    const p = getPt(i)
    if (!p) return
    p.position = _pos[i]
    p.color = alertPointColor(_alvl[i])
  }
  function addBillboard(i) {
    const b = getBb(i)
    if (!b) return
    b.position = _pos[i]
    b.image = alarmImage(_alvl[i], _hf[i])
  }

  /** 低空层惰性补齐 billboard（限批，避免一次创建上千个阻塞主线程） */
  function ensureBillboards() {
    if (!_bbCol || _bbCol.isDestroyed()) return
    let budget = 800
    for (let i = _bbNext; i < _count && budget > 0; i++, budget--) {
      _bbCol.add({
        position: _pos[i],
        image: alarmImage(_alvl[i], _hf[i]),
        width: 28, height: 28, scale: 1.0,
        color: Cesium.Color.WHITE, rotation: 0,
        alignedAxis: Cesium.Cartesian3.UNIT_Z,
        verticalOrigin: Cesium.VerticalOrigin.CENTER,
        horizontalOrigin: Cesium.HorizontalOrigin.CENTER,
        disableDepthTestDistance: Number.POSITIVE_INFINITY,
        translucencyByDistance: new Cesium.NearFarScalar(1.5e5, 1.0, 8e5, 0.0),
        pixelOffsetScaleByDistance: new Cesium.NearFarScalar(1.5e5, 3.0, 8e5, 0.5),
        id: i,
      })
      _bbNext++
    }
  }

  // ---- public API ----

  async function init(count) {
    if (_busy) { console.warn('[LOD] 忙，跳过'); return }
    _busy = true
    const v = viewer()
    if (!v) { _busy = false; return }

    destroyInternal()

    ;[_imgN, _imgW, _imgD, _imgH] = await Promise.all([
      loadImg(IMG_PATHS.NORMAL), loadImg(IMG_PATHS.WARNING),
      loadImg(IMG_PATHS.MAJOR), loadImg(IMG_PATHS.HF),
    ])
    // 图片加载期间页面可能已卸载（Viewer 已销毁），此时中止初始化
    if (v.isDestroyed()) { _busy = false; return }

    _ptCol = new Cesium.PointPrimitiveCollection({ scene: v.scene })
    v.scene.primitives.add(_ptCol)
    _bbCol = new Cesium.BillboardCollection({ scene: v.scene })
    v.scene.primitives.add(_bbCol)
    _bbNext = 0

    _count = count
    _pos = new Array(count); _hdg = new Array(count).fill(0)
    _alvl = new Array(count).fill('NORMAL'); _hf = new Array(count).fill(false)

    // 批量预创建中高空 Point（默认视角 >5km 走 Point 层）
    for (let i = 0; i < count; i++) {
      _pos[i] = spreadPos(i, count)
      _ptCol.add({
        position: _pos[i],
        color: alertPointColor('NORMAL'),
        pixelSize: 9,
        outlineColor: Cesium.Color.WHITE,
        outlineWidth: 1,
        disableDepthTestDistance: Number.POSITIVE_INFINITY,
        id: i,
      })
    }

    applyLod(getCamH(v))
    _camOff = v.camera.changed.addEventListener(() => applyLod(getCamH(v)))

    _ready = true
    _busy = false
    console.log(`[LOD] ✅ init ${count} 架, 层: ${_level}`)
  }

  /** 动态扩容 n 架（不销毁已有） */
  function addDrones(n) {
    if (!_ready || _busy || n <= 0) return
    _busy = true
    const old = _count
    _count = old + n
    _pos.length = _count; _hdg.length = _count; _alvl.length = _count; _hf.length = _count
    for (let i = old; i < _count; i++) {
      _pos[i] = spreadPos(i, _count)
      _hdg[i] = 0; _alvl[i] = 'NORMAL'; _hf[i] = false
      addPoint(i)
      if (_bbNext > i) addBillboard(i) // 低空层已启用则同步补齐
    }
    _busy = false
  }

  function upsertDrone(data, idx) {
    if (!_ready || _busy || idx === undefined || idx >= _count) return
    const pos = data.position || data
    const lon = Number(pos.lon ?? pos.longitude ?? pos.lng ?? 0)
    const lat = Number(pos.lat ?? pos.latitude ?? 0)
    const alt = Number(pos.alt ?? pos.altitude ?? pos.height ?? pos.alt_m ?? 100)
    if (!lon || !lat) return

    const cart = Cesium.Cartesian3.fromDegrees(lon, lat, alt)
    _pos[idx] = cart
    _hdg[idx] = Number(pos.heading ?? data.heading ?? 0)
    _hf[idx] = !!data.isHf

    const newLevel = data.alertLevel || 'NORMAL'
    const levelChanged = _alvl[idx] !== newLevel
    _alvl[idx] = newLevel

    try {
      if (_level === 'low') {
        const b = getBb(idx)
        if (!b) return
        b.position = cart
        b.rotation = -Cesium.Math.toRadians(_hdg[idx])
        if (levelChanged) {
          b.image = alarmImage(newLevel, _hf[idx])
        }
      } else {
        const p = getPt(idx)
        if (!p) return
        p.position = cart
        if (levelChanged) p.color = alertPointColor(newLevel)
      }
    } catch (e) {}
  }

  function highlight(idx) {
    try {
      if (_level === 'low') {
        for (let i = 0; i < _count; i++) { const b = getBb(i); if (b) b.scale = (i === idx) ? 1.5 : 1.0 }
      } else {
        for (let i = 0; i < _count; i++) { const p = getPt(i); if (p) p.pixelSize = (i === idx) ? 18 : 9 }
      }
    } catch (e) {}
  }

  /** 外部按 SN 拉取活跃告警后，刷新某槽位的告警等级与配色 */
  function setAlertLevel(idx, level) {
    if (!_ready || idx === undefined || idx >= _count) return
    const lvl = level || 'NORMAL'
    const changed = _alvl[idx] !== lvl
    _alvl[idx] = lvl
    try {
      if (changed && _level === 'mid') {
        const p = getPt(idx)
        if (p) p.color = alertPointColor(lvl)
      } else if (changed && _level === 'low') {
        const b = getBb(idx)
        if (b) b.image = alarmImage(lvl, _hf[idx])
      }
    } catch (e) {}
  }

  // ---- LOD 切换 ----
  function applyLod(h) {
    const lv = h > LOD.MID_ALT ? 'mid' : 'low'
    const changed = lv !== _level
    _level = lv
    try {
      if (_ptCol && !_ptCol.isDestroyed()) _ptCol.show = lv !== 'low'
      if (_bbCol && !_bbCol.isDestroyed()) _bbCol.show = lv === 'low'
    } catch (e) {}
    if (changed && lv === 'low') { ensureBillboards(); syncBb() }
    if (changed) console.log(`[LOD] ➡️ ${lv} (${(h / 1000).toFixed(1)}km)`)
  }

  function syncBb() {
    if (!_bbCol || _bbCol.isDestroyed()) return
    for (let i = 0; i < _count; i++) {
      const b = getBb(i); if (!b) continue
      b.position = _pos[i]
      b.rotation = -Cesium.Math.toRadians(_hdg[i])
      b.image = alarmImage(_alvl[i], _hf[i])
    }
  }

  // ---- 销毁 ----
  function destroyInternal() {
    const v = viewerRef.value
    if (_camOff) { try { _camOff(); } catch (e) {} _camOff = null }
    if (v && !v.isDestroyed()) {
      safeRm(v.scene, _ptCol); safeRm(v.scene, _bbCol)
    }
    safeDestroy(_ptCol); _ptCol = null
    safeDestroy(_bbCol); _bbCol = null
    _pos = []; _hdg = []; _alvl = []; _hf = []
    _count = 0; _ready = false; _level = 'mid'; _bbNext = 0
  }

  function safeRm(scene, col) {
    if (!col || !scene) return
    try { if (!col.isDestroyed()) scene.primitives.remove(col) } catch (e) {}
  }

  function destroy() { _busy = false; destroyInternal(); console.log('[LOD] 🗑️ 已销毁') }

  // 读取指定槽位无人机当前的三维坐标（供 DOM 标牌跟随投影）
  function getPosition(idx) {
    try {
      if (_level === 'low') { const b = _bbCol && !_bbCol.isDestroyed() ? _bbCol.get(idx) : null; return b ? b.position : null }
      const p = _ptCol && !_ptCol.isDestroyed() ? _ptCol.get(idx) : null
      return p ? p.position : null
    } catch (e) { return null }
  }

  return {
    init, addDrones, upsertDrone, highlight, setAlertLevel, destroy, getPosition,
    getCount: () => _count,
    getCurrentLevel: () => _level,
    isInitialized: () => _ready,
  }
}
