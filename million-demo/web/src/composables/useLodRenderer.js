/**
 * 百万级点渲染器 v4 —— 懒加载分帧增长（消除 init/resize 假死）
 *
 * v4 改动：不再启动时一次性预分配 N 个 primitive（那会在某个 batch 内
 * 阻塞主线程数百毫秒 → 假死）。改为 init 瞬间完成，render 时按需
 * "每帧长一点"（GROW_PT/帧），把分配摊到 ~40 帧里，单帧 ≤10ms。
 *
 * 两个集合：
 *   _ptCol  PointPrimitiveCollection   MAX_PT=20万   高/中空(>5km)  按告警上色
 *   _bbCol  BillboardCollection        MAX_BB=1万    低空(<5km)    canvas图标+航向旋转+上色
 *   (MAX_BB 降到1万：低空视野小，可见无人机通常 <几千，5万是浪费)
 */
import * as Cesium from 'cesium'

const MAX_PT = 200000
const MAX_BB = 10000
const GROW_PT = 5000 // 每帧最多新增的点数（~12ms，安全）
const GROW_BB = 600 // Billboard.add 更重，每帧少长点

function alertColor(a) {
  if (a >= 2) return Cesium.Color.RED
  if (a >= 1) return Cesium.Color.GOLD
  return Cesium.Color.CORNFLOWERBLUE
}

// cell 密度颜色（log 分桶，避免百万全挤成一两个红点）
function cellColor(cnt) {
  if (cnt >= 100) return Cesium.Color.RED
  if (cnt >= 30) return Cesium.Color.fromCssColorString('#ff8c00') // 橙
  if (cnt >= 10) return Cesium.Color.GOLD
  if (cnt >= 3) return Cesium.Color.fromCssColorString('#00bfff')  // 深天蓝
  return Cesium.Color.CORNFLOWERBLUE
}

function makeDroneIcon() {
  const c = document.createElement('canvas')
  c.width = c.height = 32
  const ctx = c.getContext('2d')
  ctx.fillStyle = '#ffffff'
  ctx.strokeStyle = 'rgba(0,0,0,0.5)'
  ctx.lineWidth = 1.5
  ctx.beginPath()
  ctx.moveTo(16, 3)
  ctx.lineTo(27, 28)
  ctx.lineTo(16, 22)
  ctx.lineTo(5, 28)
  ctx.closePath()
  ctx.fill()
  ctx.stroke()
  return c
}

export function useLodRenderer(viewerRef) {
  let _ptCol = null
  let _bbCol = null
  let _icon = null
  let _scratch = new Cesium.Cartesian3()
  let _ready = false

  let _pos = null
  let _meta = null
  let _ll = null
  let _count = 0
  let _ptAlert = null
  let _lastPtCount = 0
  let _lastBbCount = 0
  let _isCell = false // 服务端帧类型即 LOD：cell=高空聚合(Point)，raw=低空原始(Billboard)

  // init 瞬间完成 —— 不预分配任何 primitive
  function init() {
    const v = viewerRef.value
    if (!v) return
    _icon = makeDroneIcon()
    _ptCol = new Cesium.PointPrimitiveCollection({ scene: v.scene })
    v.scene.primitives.add(_ptCol)
    _bbCol = new Cesium.BillboardCollection({ scene: v.scene })
    v.scene.primitives.add(_bbCol)
    // 互斥：初始只显 Point（cell 帧）
    _ptCol.show = true
    _bbCol.show = false
    _ptAlert = new Uint8Array(MAX_PT)
    _ready = true
    console.log('[renderer] 就绪（懒加载模式，按需分帧增长）')
  }

  // 帧类型即 LOD：cell 帧→只画 Point，raw 帧→只画 Billboard（互斥）
  function render() {
    if (!_ready || !_count || !_pos) return
    if (_isCell) {
      _bbCol.show = false
      hideAllBb()
      renderPoints()
      _ptCol.show = true
    } else {
      _ptCol.show = false
      hideAllPt()
      renderBillboards()
      _bbCol.show = true
    }
  }

  function hideAllPt() {
    if (!_ptCol) return
    for (let j = 0; j < _lastPtCount; j++) {
      const p = _ptCol.get(j)
      if (p && p.show) p.show = false
    }
    _lastPtCount = 0
  }

  // 按需分帧增长 _ptCol，每帧最多 GROW_PT 个 → 单帧 ~12ms，不假死
  function ensurePtCapacity(needed) {
    if (_ptCol.length >= needed) return
    const grow = Math.min(GROW_PT, needed - _ptCol.length)
    for (let j = 0; j < grow; j++) {
      _ptCol.add({
        position: _scratch,
        color: Cesium.Color.CORNFLOWERBLUE,
        pixelSize: 5,
        outlineColor: Cesium.Color.WHITE.withAlpha(0.3),
        outlineWidth: 1,
        disableDepthTestDistance: Number.POSITIVE_INFINITY,
        show: false,
        id: _ptCol.length // 索引即 id，供 pick
      })
    }
  }

  function ensureBbCapacity(needed) {
    if (_bbCol.length >= needed) return
    const grow = Math.min(GROW_BB, needed - _bbCol.length)
    for (let j = 0; j < grow; j++) {
      _bbCol.add({
        position: _scratch,
        image: _icon,
        color: Cesium.Color.WHITE,
        width: 24,
        height: 24,
        rotation: 0,
        alignedAxis: Cesium.Cartesian3.UNIT_Z,
        verticalOrigin: Cesium.VerticalOrigin.CENTER,
        horizontalOrigin: Cesium.HorizontalOrigin.CENTER,
        disableDepthTestDistance: Number.POSITIVE_INFINITY,
        show: false,
        id: -1 // 真实 drone 索引在填充时写入；-1 = 未占用
      })
    }
  }

  function renderPoints() {
    const needed = Math.min(_count, MAX_PT)
    ensurePtCapacity(needed)
    const drawCount = Math.min(needed, _ptCol.length)
    for (let i = 0; i < drawCount; i++) {
      const p = _ptCol.get(i)
      if (!p) continue
      _scratch.x = _pos[i * 3]
      _scratch.y = _pos[i * 3 + 1]
      _scratch.z = _pos[i * 3 + 2]
      p.position = _scratch
      if (!p.show) p.show = true
      const m0 = _meta[i * 2] | 0
      if (_isCell) {
        // cell 帧：meta[0]=count 密度信号
        const cnt = _meta[i * 2]
        const size = 3 + Math.min(20, Math.log2(cnt + 1) * 2.5) // 3~23px，log 压缩
        if (p.pixelSize !== size) p.pixelSize = size
        const col = cellColor(cnt)
        if (_ptAlert[i] !== (cnt | 0)) {
          _ptAlert[i] = cnt | 0
          p.color = col
        }
      } else {
        if (_ptAlert[i] !== m0) {
          _ptAlert[i] = m0
          p.color = alertColor(m0)
        }
      }
    }
    if (drawCount < _lastPtCount) {
      for (let i = drawCount; i < _lastPtCount; i++) {
        const p = _ptCol.get(i)
        if (p) p.show = false
      }
    }
    _lastPtCount = drawCount
  }

  // 低空 raw 帧：服务端已按视野裁剪，直接全部填入 billboard（无需再 bbox 过滤）
  function renderBillboards() {
    const drawCount = Math.min(_count, MAX_BB)
    ensureBbCapacity(drawCount)
    for (let j = 0; j < drawCount; j++) {
      const b = _bbCol.get(j)
      if (!b) continue
      const i = j
      _scratch.x = _pos[i * 3]
      _scratch.y = _pos[i * 3 + 1]
      _scratch.z = _pos[i * 3 + 2]
      b.position = _scratch
      if (!b.show) b.show = true
      b.rotation = -Cesium.Math.toRadians(_meta[i * 2 + 1])
      b.id = i // pick 用真实索引
      b.color = alertColor(_meta[i * 2] | 0)
    }
    if (drawCount < _lastBbCount) {
      for (let j = drawCount; j < _lastBbCount; j++) {
        const b = _bbCol.get(j)
        if (b) { b.show = false; b.id = -1 }
      }
    }
    _lastBbCount = drawCount
  }

  function hideAllBb() {
    if (!_bbCol) return
    for (let j = 0; j < _lastBbCount; j++) {
      const b = _bbCol.get(j)
      if (b && b.show) b.show = false
    }
    _lastBbCount = 0
  }

  function updateBatch(pos, meta, ll, count, isCell) {
    if (!_ready || count <= 0 || !pos || !meta || !ll) return
    _pos = pos
    _meta = meta
    _ll = ll
    _count = count
    _isCell = !!isCell
    render()
  }

  function getPointData(index) {
    if (index == null || index < 0 || index >= _count || !_pos) return null
    return {
      cartesian: new Cesium.Cartesian3(_pos[index * 3], _pos[index * 3 + 1], _pos[index * 3 + 2]),
      alert: _meta ? _meta[index * 2] | 0 : 0,
      heading: _meta ? _meta[index * 2 + 1] : 0,
      index
    }
  }

  function isInitialized() {
    return _ready
  }

  function destroy() {
    const v = viewerRef.value
    const rm = (col) => {
      if (col && v && v.scene) {
        try {
          if (!col.isDestroyed()) {
            v.scene.primitives.remove(col)
            col.destroy()
          }
        } catch (e) {}
      }
    }
    rm(_ptCol)
    rm(_bbCol)
    _ptCol = _bbCol = null
    _icon = null
    _ptAlert = null
    _pos = _meta = _ll = null
    _count = 0
    _lastPtCount = _lastBbCount = 0
    _ready = false
    _scratch = null
  }

  return { init, updateBatch, getPointData, isInitialized, destroy }
}
