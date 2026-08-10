/**
 * 3D 体素热力图渲染
 * 使用 Entity.box 实现（简单可靠，避免 Primitive + PerInstanceColorAppearance 的 WebGL 兼容问题）
 * ★ 数据通过共享缓存 fetchAirspaceData() 获取
 */

import { ref } from 'vue'
import * as Cesium from 'cesium'
import { fetchAirspaceData } from '../utils/apiService.js'

export function useHeatmap(viewer) {
  let heatmapEntities = []
  const isVisible = ref(false)

  // 热力颜色渐变
  const heatColors = [
    { stop: 0.0, color: new Cesium.Color(0, 0, 1, 0) },
    { stop: 0.2, color: new Cesium.Color(0, 0.5, 1, 0.35) },
    { stop: 0.4, color: new Cesium.Color(0, 1, 0.5, 0.5) },
    { stop: 0.6, color: new Cesium.Color(1, 1, 0, 0.6) },
    { stop: 0.8, color: new Cesium.Color(1, 0.5, 0, 0.7) },
    { stop: 1.0, color: new Cesium.Color(1, 0, 0, 0.8) }
  ]

  /**
   * 根据值获取颜色
   */
  function getHeatColor(value, min, max) {
    const range = max - min
    if (range === 0) return heatColors[0].color.clone()
    const normalized = (value - min) / range
    const clamped = Math.max(0, Math.min(1, normalized))

    for (let i = 0; i < heatColors.length - 1; i++) {
      const lower = heatColors[i]
      const upper = heatColors[i + 1]
      if (clamped >= lower.stop && clamped <= upper.stop) {
        const t = (clamped - lower.stop) / (upper.stop - lower.stop)
        return new Cesium.Color(
          Cesium.Math.lerp(lower.color.red, upper.color.red, t),
          Cesium.Math.lerp(lower.color.green, upper.color.green, t),
          Cesium.Math.lerp(lower.color.blue, upper.color.blue, t),
          Cesium.Math.lerp(lower.color.alpha, upper.color.alpha, t)
        )
      }
    }
    return heatColors[heatColors.length - 1].color.clone()
  }

  /**
   * 初始化热力图
   * @param {number} resolution - 体素分辨率（米）
   */
  async function init(resolution = 200) {
    if (!viewer.value) return
    const v = viewer.value

    let points = []
    try {
      const data = await fetchAirspaceData()
      if (data) {
        points = data.heatmapPoints || []
      }
    } catch (e) {
      console.warn('[Heatmap] 获取失败', e)
    }

    if (points.length === 0) return

    // 空间离散化
    const voxelMap = new Map()

    for (const p of points) {
      const cx = Math.round(p.lng * 111320 / resolution)
      const cy = Math.round(p.lat * 111320 / resolution)
      const cz = Math.round(p.height / resolution)
      const key = `${cx},${cy},${cz}`

      if (voxelMap.has(key)) {
        voxelMap.get(key).value += p.value
        voxelMap.get(key).count++
      } else {
        voxelMap.set(key, {
          cx: cx * resolution / 111320,
          cy: cy * resolution / 111320,
          cz: cz * resolution,
          value: p.value,
          count: 1
        })
      }
    }

    // 限制体素数量，避免 Entity 过多影响性能
    const MAX_VOXELS = 120
    const voxels = Array.from(voxelMap.values())
    if (voxels.length > MAX_VOXELS) {
      // 按密度排序，保留高密度区域
      voxels.sort((a, b) => (b.value / b.count) - (a.value / a.count))
      voxels.length = MAX_VOXELS
    }

    // 计算密度范围
    let maxDensity = 0
    let minDensity = Infinity
    for (const vx of voxels) {
      vx.density = vx.value / vx.count
      maxDensity = Math.max(maxDensity, vx.density)
      minDensity = Math.min(minDensity, vx.density)
    }

    // 创建体素盒子（使用 Entity 方式，兼容性好）
    const halfSize = resolution / 2

    for (const vx of voxels) {
      const color = getHeatColor(vx.density, minDensity, maxDensity)
      const position = Cesium.Cartesian3.fromDegrees(vx.cx, vx.cy, vx.cz)

      const entity = v.entities.add({
        position: position,
        box: {
          dimensions: new Cesium.Cartesian3(halfSize * 2, halfSize * 2, halfSize * 2),
          material: color,
          outline: false,
          fill: true
        },
        show: true
      })

      heatmapEntities.push(entity)
    }

    isVisible.value = true
    console.log(`[Heatmap] 生成 ${voxels.length} 个体素`)
  }

  function setVisible(visible) {
    for (const entity of heatmapEntities) {
      entity.show = visible
    }
    isVisible.value = visible
  }

  function destroy() {
    if (viewer.value) {
      for (const entity of heatmapEntities) {
        viewer.value.entities.remove(entity)
      }
    }
    heatmapEntities = []
    isVisible.value = false
  }

  return {
    isVisible,
    init,
    setVisible,
    destroy
  }
}
