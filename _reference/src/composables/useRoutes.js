/**
 * 航路/航迹线渲染 — V7.1 后端筛选架构
 * - 直接从预计算 Cartesian3 数组更新航迹（零 fromDegrees）
 * - 环形缓冲区 + 仿真时间采样（每0.5秒仿真时间 push）
 * - 支持 seek 跳转时重置并预填充航迹
 *
 * ★ V7.1: initTrails() 支持重建（安全销毁：设null→scene移除→微任务delay destroy）
 *
 * ★ V5:
 *   - 永久航路从 /api/airspace/data 的 permanent_routes 读取
 *   - 时效性航路变化从 /api/drone/packet/:idx 的 time_routes 读取
 *
 * ★ V6.3:
 *   - 航迹从"每渲染帧 push"改为"每 0.5 秒仿真时间 push"
 *   - TRAIL_LENGTH 200，覆盖 100 秒 / ~1500 米
 */
import { ref } from 'vue'
import * as Cesium from 'cesium'
import { fetchAirspaceData } from '../utils/apiService.js'

export function useRoutes(viewer) {
  let polylineCollection = null
  let trailCollection = null
  let _trailsRebuilding = false   // ★ 重建锁
  const routesVisible = ref(true)
  const trailsVisible = ref(true)

  // ★ V6.3: 航迹覆盖 200 点 × 0.5 秒 = 100 秒 ≈ 1500 米（15m/s）
  const TRAIL_LENGTH = 200
  const TRAIL_SAMPLE_INTERVAL = 0.5

  const _routePolylines = new Map()

  class TrailBuffer {
    constructor() {
      this.length = TRAIL_LENGTH
      this.buf = new Array(TRAIL_LENGTH)
      this.writeIdx = 0
      this.count = 0
      this._lastSampleTime = -999
      for (let i = 0; i < TRAIL_LENGTH; i++) {
        this.buf[i] = new Cesium.Cartesian3()
      }
    }

    push(cart) {
      this.buf[this.writeIdx].x = cart.x
      this.buf[this.writeIdx].y = cart.y
      this.buf[this.writeIdx].z = cart.z
      this.writeIdx = (this.writeIdx + 1) % this.length
      if (this.count < this.length) this.count++
    }

    reset() {
      this.count = 0
      this.writeIdx = 0
      this._lastSampleTime = -999
    }

    getOrdered() {
      if (this.count === 0) return []
      const result = new Array(this.count)
      const start = this.count < this.length ? 0 : this.writeIdx
      for (let i = 0; i < this.count; i++) {
        result[i] = this.buf[(start + i) % this.length]
      }
      return result
    }
  }

  const trailBuffers = new Map()

  async function init() {
    if (!viewer.value) return
    const v = viewer.value

    let routes = []
    try {
      const data = await fetchAirspaceData()
      if (data) {
        routes = data.permanent_routes || data.routes || []
      }
    } catch (e) {
      console.warn('[useRoutes] 获取失败', e)
    }

    if (routes.length === 0) return

    polylineCollection = new Cesium.PolylineCollection()
    v.scene.primitives.add(polylineCollection)

    for (const route of routes) {
      const positions = route.points.map(p =>
        Cesium.Cartesian3.fromDegrees(p.lng, p.lat, p.height)
      )
      const color = Cesium.Color.fromCssColorString(route.color)
      const polyline = polylineCollection.add({
        positions,
        width: 2,
        material: Cesium.Material.fromType('Color', {
          color: color.withAlpha(0.7)
        }),
        zIndex: route.id === 'ROUTE-1' ? 1 : 0
      })
      _routePolylines.set(route.id, {
        polyline,
        defaultColor: color,
        defaultWidth: 2
      })
    }

    routesVisible.value = true
    console.log(`[useRoutes] 已加载 ${routes.length} 条永久航路`)
  }

  // ──────────────────────────────────────────────
  // ★ V5: 时效性航路变化管理
  // ──────────────────────────────────────────────

  function updateTimeRoutes(timeRoutes) {
    if (!timeRoutes || timeRoutes.length === 0) return

    for (const tr of timeRoutes) {
      const entry = _routePolylines.get(tr.routeId)
      if (!entry) continue

      if (tr.status === 'closed') {
        entry.polyline.material = Cesium.Material.fromType('Color', {
          color: Cesium.Color.RED.withAlpha(0.5)
        })
        entry.polyline.width = 3
      } else if (tr.status === 'restricted') {
        entry.polyline.material = Cesium.Material.fromType('Color', {
          color: Cesium.Color.ORANGE.withAlpha(0.6)
        })
        entry.polyline.width = 2
      }
    }

    console.log(`[useRoutes] 已应用 ${timeRoutes.length} 条时效性航路变化`)
  }

  function resetRouteStates() {
    for (const [id, entry] of _routePolylines) {
      entry.polyline.material = Cesium.Material.fromType('Color', {
        color: entry.defaultColor.withAlpha(0.7)
      })
      entry.polyline.width = entry.defaultWidth
    }
  }

  // ──────────────────────────────────────────────
  // ★ V7.1: 安全销毁 + 重建
  // ──────────────────────────────────────────────

  function _destroyTrailsInternal(v) {
    const oldColl = trailCollection
    trailCollection = null   // ★ 先置空，阻断 updateTrails
    trailBuffers.clear()

    if (oldColl && v && v.scene) {
      if (!oldColl.isDestroyed()) {
        v.scene.primitives.remove(oldColl)
        // ★ 微任务延迟 destroy：等 Cesium 当前帧渲染完毕
        const coll = oldColl
        Promise.resolve().then(() => {
          if (!coll.isDestroyed()) {
            coll.destroy()
          }
        })
      }
    }
  }

  function initTrails(count = 500) {
    const v = viewer.value
    if (!v) return

    _trailsRebuilding = true

    // ★ V7.1: 安全销毁旧 trails
    _destroyTrailsInternal(v)

    trailCollection = new Cesium.PolylineCollection()
    v.scene.primitives.add(trailCollection)

    for (let i = 0; i < count; i++) {
      const buf = new TrailBuffer()
      trailBuffers.set(i, buf)

      trailCollection.add({
        positions: [],
        width: 3,
        material: Cesium.Material.fromType('Color', {
          color: new Cesium.Color(0.3, 1.0, 0.3, 0.8)
        }),
        zIndex: 10
      })
    }

    _trailsRebuilding = false
    console.log(`[useRoutes] 已初始化 ${count} 条动态航迹线`)
  }

  /**
   * ★ V6.3: 基于仿真时间采样
   */
  function updateTrails(cartArray, simTimeSec, force = false) {
    if (_trailsRebuilding || !trailCollection || !trailsVisible.value || !cartArray) return
    if (trailCollection.isDestroyed && trailCollection.isDestroyed()) return

    const count = Math.min(trailBuffers.size, cartArray.length)
    for (let i = 0; i < count; i++) {
      const buf = trailBuffers.get(i)
      if (!buf) continue

      if (!force && simTimeSec - buf._lastSampleTime < TRAIL_SAMPLE_INTERVAL) continue

      buf._lastSampleTime = simTimeSec
      buf.push(cartArray[i])

      if (buf.count >= 2) {
        const polyline = trailCollection.get(i)
        if (polyline) polyline.positions = buf.getOrdered()
      }
    }
  }

  function resetTrails() {
    trailBuffers.forEach(buf => buf.reset())
    if (trailCollection) {
      for (let i = 0; i < trailCollection.length; i++) {
        const poly = trailCollection.get(i)
        if (poly) poly.positions = []
      }
    }
  }

  function setRoutesVisible(visible) {
    if (polylineCollection) polylineCollection.show = visible
    routesVisible.value = visible
  }

  function setTrailsVisible(visible) {
    if (trailCollection) {
      trailCollection.show = visible
    }
    trailsVisible.value = visible
  }

  function destroy() {
    const v = viewer.value
    _destroyTrailsInternal(v)
    if (v && polylineCollection) {
      if (!polylineCollection.isDestroyed()) {
        v.scene.primitives.remove(polylineCollection)
      }
      if (!polylineCollection.isDestroyed()) {
        polylineCollection.destroy()
      }
      polylineCollection = null
    }
    _routePolylines.clear()
  }

  return {
    routesVisible,
    trailsVisible,
    init,
    initTrails,
    updateTrails,
    resetTrails,
    setRoutesVisible,
    setTrailsVisible,
    destroy,
    updateTimeRoutes,
    resetRouteStates
  }
}
