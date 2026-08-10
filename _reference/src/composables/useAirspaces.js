/**
 * 多种空域类型渲染（Entity 方式，兼容 WebGL 1）
 * 绘制：禁飞区(红)、限飞区(橙)、适飞区(绿)、圆形空域(蓝)、多边形空域(紫)、电子围栏(黄)
 *
 * ★ V5:
 *   - 永久空域从 /api/airspace/data 的 permanent_zones 读取
 *   - 时效性空域从 /api/drone/packet/:idx 的 time_airspaces 读取
 *   - 支持按时间激活/停用时效性空域
 *
 * ★ V7.3:
 *   - 所有坐标均为 WGS84 绝对经纬度，直接传给 Cesium.fromDegrees
 *   - 移除旧版 "中心点 + 米偏移" 的 dx/dy 转换
 */
import { ref } from 'vue'
import * as Cesium from 'cesium'
import { fetchAirspaceData } from '../utils/apiService.js'

const C = {
  red:    [1, 0.15, 0.15],
  orange: [1, 0.6, 0.1],
  green:  [0.1, 1, 0.4],
  blue:   [0.2, 0.5, 1],
  purple: [0.6, 0.2, 1],
  yellow: [1, 0.9, 0.1]
}

export function useAirspaces(viewer) {
  const entities = []
  const timeAirspaceEntities = []  // ★ 时效性空域实体（独立管理）
  const isVisible = ref(true)

  function mc(key, a) {
    const c = C[key] || C.red
    return new Cesium.Color(c[0], c[1], c[2], a)
  }

  /**
   * ★ V7.3: 添加空域 — 坐标均为 WGS84 绝对经纬度，无需转换
   * @param {Object} z — 空域定义
   *   poly:  { type:'poly', vertices: [{lng, lat}, ...], h, fill, name, desc }
   *   ell:   { type:'ell', centerLng, centerLat, radius, h, fill, name, desc }
   */
  function addZone(z) {
    if (z.type === 'poly') {
      const pos = z.vertices.map(v => Cesium.Cartesian3.fromDegrees(v.lng, v.lat, 0))
      const entity = viewer.value.entities.add({
        name: z.name, description: z.desc,
        polygon: {
          hierarchy: new Cesium.PolygonHierarchy(pos),
          extrudedHeight: z.h,
          material: mc(z.fill, 0.25),
          outline: true,
          outlineColor: mc(z.fill, 0.8),
          outlineWidth: 2
        }
      })
      entities.push(entity)
      return entity
    } else if (z.type === 'ell') {
      const entity = viewer.value.entities.add({
        name: z.name, description: z.desc,
        position: Cesium.Cartesian3.fromDegrees(z.centerLng, z.centerLat, 0),
        ellipse: {
          semiMajorAxis: z.radius, semiMinorAxis: z.radius,
          extrudedHeight: z.h,
          material: mc(z.fill, 0.25),
          outline: true,
          outlineColor: mc(z.fill, 0.8),
          outlineWidth: 2
        }
      })
      entities.push(entity)
      return entity
    }
    return null
  }

  /**
   * ★ V7.3: 添加电子围栏 — fencePts 为 [{lng, lat}, ...]
   */
  function addFence(fencePts) {
    const wpos = fencePts.map(p => Cesium.Cartesian3.fromDegrees(p.lng, p.lat, 0))
    entities.push(viewer.value.entities.add({
      name: '电子围栏', description: '越界报警',
      wall: {
        positions: wpos,
        minimumHeights: wpos.map(() => 0),
        maximumHeights: wpos.map(() => 250),
        material: mc('yellow', 0.25),
        outline: true,
        outlineColor: mc('yellow', 0.8),
        outlineWidth: 2
      }
    }))
  }

  async function init() {
    if (!viewer.value) return

    let zones = []
    let fencePts = []

    try {
      const data = await fetchAirspaceData()
      if (data) {
        zones = data.permanent_zones || data.zones || []
        fencePts = data.fencePts || []
      }
    } catch (e) {
      console.warn('[useAirspaces] 获取失败', e)
    }

    zones.forEach(z => addZone(z))
    if (fencePts.length > 0) addFence(fencePts)

    isVisible.value = true
    console.log('[useAirspaces] 已创建 ' + entities.length + ' 个永久空域（WGS84 绝对坐标）')
  }

  // ──────────────────────────────────────────────
  // ★ V5: 时效性空域管理
  // ──────────────────────────────────────────────

  /**
   * 根据当前包数据更新时效性空域
   * @param {Array} airspaces — 来自 packet 的 time_airspaces 数组
   */
  function updateTimeAirspaces(airspaces) {
    // 清除旧的时效性空域
    clearTimeAirspaces()

    if (!airspaces || airspaces.length === 0) return

    for (const z of airspaces) {
      const entity = addZone(z)
      if (entity) {
        // 标记为时效性实体
        entity._timeSensitive = true
        entity._activeFrom = z.activeFrom
        entity._activeTo = z.activeTo
        timeAirspaceEntities.push(entity)
      }
    }

    if (airspaces.length > 0) {
      console.log(`[useAirspaces] 已加载 ${airspaces.length} 个时效性空域`)
    }
  }

  /**
   * 根据当前回放时间更新时效性空域的可见性
   * @param {number} currentTimeSec — 当前回放秒数
   */
  function updateTimeAirspaceVisibility(currentTimeSec) {
    for (const entity of timeAirspaceEntities) {
      if (!entity || entity.isDestroyed?.()) continue
      const active = currentTimeSec >= entity._activeFrom && currentTimeSec < entity._activeTo
      entity.show = active && isVisible.value
    }
  }

  function clearTimeAirspaces() {
    if (viewer.value) {
      for (const entity of timeAirspaceEntities) {
        if (entity && !entity.isDestroyed?.()) {
          viewer.value.entities.remove(entity)
        }
      }
    }
    timeAirspaceEntities.length = 0
  }

  // ──────────────────────────────────────────────

  function setVisible(v) {
    isVisible.value = v
    entities.forEach(e => { if(e) e.show = v })
    timeAirspaceEntities.forEach(e => {
      if (e) {
        const active = _currentSimTime >= e._activeFrom && _currentSimTime < e._activeTo
        e.show = v && active
      }
    })
  }

  let _currentSimTime = 0
  function setCurrentTime(t) { _currentSimTime = t }

  function destroy() {
    if (viewer.value) {
      entities.forEach(e => viewer.value.entities.remove(e))
      timeAirspaceEntities.forEach(e => {
        if (e && !e.isDestroyed?.()) viewer.value.entities.remove(e)
      })
    }
    entities.length = 0
    timeAirspaceEntities.length = 0
    isVisible.value = false
  }

  return {
    isVisible,
    init,
    setVisible,
    destroy,
    updateTimeAirspaces,
    updateTimeAirspaceVisibility,
    clearTimeAirspaces,
    setCurrentTime
  }
}
