/**
 * 无人机 LOD 分层渲染器 v2 — 支持动态扩容
 *
 *   🏙️ 中高空 (>5km)  → PointPrimitiveCollection（万级，真实遥测位置实时更新）
 *   🚁 低空 (<5km)     → BillboardCollection（带预警色+航向）
 *
 *   注：BufferPointCollection 静态缓冲层不适配「每秒全量改位置」的动态机队
 *   （写入不生效，会退化成 spreadPos 初始螺旋），万级规模直接用 Point 层。
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

const LOD = { HIGH_ALT: 50000, MID_ALT: 5000 }
const CENTER_LON = 116.4
const CENTER_LAT = 39.9
const SPREAD_RADIUS_DEG = 0.8

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
  if (!v || !v.camera) return 0
  return Cesium.Cartographic.fromCartesian(v.camera.position).height
}

function safeDestroy(col) {
  if (!col) return false
  try { if (!col.isDestroyed()) { col.destroy(); return true } } catch (e) {}
  return false
}

export function useLodDroneRenderer(viewerRef) {
  let _bufCol = null   // BufferPointCollection
  let _ptCol = null    // PointPrimitiveCollection
  let _bbCol = null    // BillboardCollection
  let _blueMat = null

  let _level = 'mid'
  let _ready = false
  let _busy = false

  let _count = 0
  let _pos = []        // Cartesian3[]
  let _hdg = []        // number[]
  let _alvl = []       // string[]
  let _hf = []         // boolean[]

  let _imgN = null, _imgW = null, _imgD = null, _imgH = null
  let _camOff = null

  // ---- helpers ----
  function safeRm(scene, col) {
    if (!col || !scene) return
    try { if (!col.isDestroyed()) scene.primitives.remove(col) } catch (e) {}
  }

  function getBp(i) {
    try { if (_bufCol && !_bufCol.isDestroyed() && i < _bufCol.length) return _bufCol.get(i) } catch (e) {}
    return null
  }
  function getPt(i) {
    try { if (_ptCol && !_ptCol.isDestroyed() && i < _ptCol.length) return _ptCol.get(i) } catch (e) {}
    return null
  }
  function getBb(i) {
    try { if (_bbCol && !_bbCol.isDestroyed() && i < _bbCol.length) return _bbCol.get(i) } catch (e) {}
    return null
  }

  // ---- add one drone to all 3 layers ----
  function addOne(i) {
    const p = spreadPos(i, _count)
    if (_bufCol && !_bufCol.isDestroyed() && _blueMat) {
      _bufCol.add({ position: p.clone(), material: _blueMat, show: true })
    }
    if (_ptCol && !_ptCol.isDestroyed()) {
      _ptCol.add({ position: p.clone(), color: Cesium.Color.CORNFLOWERBLUE, pixelSize: 9, outlineColor: Cesium.Color.WHITE, outlineWidth: 1, disableDepthTestDistance: Number.POSITIVE_INFINITY, id: i })
    }
    if (_bbCol && !_bbCol.isDestroyed()) {
      _bbCol.add({ position: p.clone(), image: _imgN || IMG_PATHS.NORMAL, width: 28, height: 28, scale: 1.0, color: Cesium.Color.WHITE, rotation: 0, alignedAxis: Cesium.Cartesian3.UNIT_Z, verticalOrigin: Cesium.VerticalOrigin.CENTER, horizontalOrigin: Cesium.HorizontalOrigin.CENTER, disableDepthTestDistance: Number.POSITIVE_INFINITY, translucencyByDistance: new Cesium.NearFarScalar(1.5e5, 1.0, 8e5, 0.0), pixelOffsetScaleByDistance: new Cesium.NearFarScalar(1.5e5, 3.0, 8e5, 0.5), id: i })
    }
  }

  // ---- public API ----

  async function init(count) {
    if (_busy) { console.warn('[LOD] 忙，跳过'); return }
    _busy = true
    const v = viewerRef.value
    if (!v) { _busy = false; return }

    destroyInternal()
    const sc = v.scene

    ;[_imgN, _imgW, _imgD, _imgH] = await Promise.all([
      loadImg(IMG_PATHS.NORMAL), loadImg(IMG_PATHS.WARNING),
      loadImg(IMG_PATHS.MAJOR), loadImg(IMG_PATHS.HF)
    ])

    _blueMat = new Cesium.BufferPointMaterial({ color: Cesium.Color.CORNFLOWERBLUE, size: 5, outlineWidth: 0 })

    // 1) BufferPointCollection
    _bufCol = new Cesium.BufferPointCollection({ primitiveCountMax: Math.max(count * 2, 2048), show: true, allowPicking: false })
    sc.primitives.add(_bufCol)

    // 2) PointPrimitiveCollection
    _ptCol = new Cesium.PointPrimitiveCollection({ scene: sc })
    sc.primitives.add(_ptCol)

    // 3) BillboardCollection
    _bbCol = new Cesium.BillboardCollection({ scene: sc })
    sc.primitives.add(_bbCol)

    // fill
    _count = count
    _pos = new Array(count); _hdg = new Array(count).fill(0)
    _alvl = new Array(count).fill('NORMAL'); _hf = new Array(count).fill(false)
    for (let i = 0; i < count; i++) {
      _pos[i] = spreadPos(i, count)
      addOne(i)
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
      addOne(i)
    }
    // 更新 BufferPointCollection 的容量上限
    if (_bufCol && !_bufCol.isDestroyed()) {
      // BufferPointCollection 没有 resize，但 add 时会自动扩容
    }
    _busy = false
    console.log(`[LOD] ➕ 扩容 +${n}，总计 ${_count}`)
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
    _alvl[idx] = data.alertLevel || 'NORMAL'
    _hf[idx] = !!data.isHf

    try {
      if (_level === 'low') {
        const b = getBb(idx)
        if (!b) return
        b.position = cart
        b.rotation = -Cesium.Math.toRadians(_hdg[idx])
        let img = _imgN
        if (_hf[idx]) img = _imgH
        else if (['MAJOR','CRITICAL','EMERGENCY'].includes(_alvl[idx])) img = _imgD
        else if (['WARNING','MINOR'].includes(_alvl[idx])) img = _imgW
        if (img && b.image !== img) b.image = img
      } else {
        // 万级机队：中高空统一走 PointPrimitiveCollection（真实遥测位置）
        const p = getPt(idx)
        if (p) p.position = cart
      }
    } catch (e) {}
  }

  function highlight(idx) {
    try {
      if (_level === 'low') {
        for (let i = 0; i < _count; i++) { const b = getBb(i); if (b) b.scale = (i === idx) ? 1.5 : 1.0 }
      } else if (_level === 'mid') {
        for (let i = 0; i < _count; i++) { const p = getPt(i); if (p) p.pixelSize = (i === idx) ? 18 : 9 }
      }
    } catch (e) {}
  }

  // ---- LOD 切换 ----
  // 万级机队：>5km 一律沿用 PointPrimitiveCollection（真实遥测位置实时更新）；
  // BufferPointCollection 静态缓冲层不再启用——其点位无法逐帧改写，切换后
  // 会退化成 spreadPos 初始螺旋（蛇形）。
  function applyLod(h) {
    let lv = h > LOD.MID_ALT ? 'mid' : 'low'
    const changed = lv !== _level
    _level = lv
    // 三集合互斥：每次都必须执行（首次进入时集合构造默认 show:true）
    try {
      if (_bufCol && !_bufCol.isDestroyed()) _bufCol.show = false
      if (_ptCol && !_ptCol.isDestroyed()) _ptCol.show = (lv === 'mid')
      if (_bbCol && !_bbCol.isDestroyed()) _bbCol.show = (lv === 'low')
    } catch (e) {}
    if (changed && lv === 'low') syncBb()
    if (changed) console.log(`[LOD] ➡️ ${lv} (${(h/1000).toFixed(1)}km)`)
  }

  function syncBb() {
    if (!_bbCol || _bbCol.isDestroyed()) return
    for (let i = 0; i < _count; i++) {
      const b = getBb(i); if (!b) continue
      b.position = _pos[i]
      b.rotation = -Cesium.Math.toRadians(_hdg[i])
      let img = _imgN
      if (_hf[i]) img = _imgH
      else if (['MAJOR','CRITICAL','EMERGENCY'].includes(_alvl[i])) img = _imgD
      else if (['WARNING','MINOR'].includes(_alvl[i])) img = _imgW
      if (img && b.image !== img) b.image = img
    }
  }

  // ---- 销毁 ----
  function destroyInternal() {
    const v = viewerRef.value
    if (_camOff) { _camOff(); _camOff = null }
    if (v) {
      safeRm(v.scene, _bufCol); safeRm(v.scene, _ptCol); safeRm(v.scene, _bbCol)
    }
    safeDestroy(_bufCol); _bufCol = null
    safeDestroy(_ptCol); _ptCol = null
    safeDestroy(_bbCol); _bbCol = null
    _blueMat = null
    _pos = []; _hdg = []; _alvl = []; _hf = []
    _count = 0; _ready = false; _level = 'mid'
  }

  function destroy() { _busy = false; destroyInternal(); console.log('[LOD] 🗑️ 已销毁') }

  // 读取指定槽位无人机当前的三维坐标（供 DOM 标牌跟随投影）
  function getPosition(idx) {
    try {
      if (_level === 'low') { const b = getBb(idx); return b ? b.position : null }
      const p = getPt(idx); return p ? p.position : null
      const b = getBb(idx); return b ? b.position : null
    } catch (e) { return null }
  }

  return {
    init, addDrones, upsertDrone, highlight, destroy, getPosition,
    getCount: () => _count,
    getCurrentLevel: () => _level,
    isInitialized: () => _ready,
    getCollection: () => _level === 'high' ? _bufCol : _level === 'mid' ? _ptCol : _bbCol,
  }
}
