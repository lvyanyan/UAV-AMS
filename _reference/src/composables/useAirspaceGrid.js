/**
 * 空域网格渲染
 * 使用 PolylineCollection 批量绘制线框网格
 * ★ 数据通过共享缓存 fetchAirspaceData() 获取
 */

import { ref } from 'vue'
import * as Cesium from 'cesium'
import { CESIUM_CONFIG } from '../config/cesiumConfig.js'
import { fetchAirspaceData } from '../utils/apiService.js'

export function useAirspaceGrid(viewer) {
  let gridCollection = null
  const isVisible = ref(true)

  async function init() {
    if (!viewer.value) return
    const v = viewer.value

    let gridConfig = { extent: 2000, gridSize: 250, gridHeight: 120 }
    let centerLng = CESIUM_CONFIG.defaultView.longitude
    let centerLat = CESIUM_CONFIG.defaultView.latitude

    try {
      const data = await fetchAirspaceData()
      if (data) {
        centerLng = data.centerLng || centerLng
        centerLat = data.centerLat || centerLat
        gridConfig = data.gridConfig || gridConfig
      }
    } catch (e) {
      console.warn('[AirspaceGrid] 获取失败，使用默认配置', e)
    }

    const { extent, gridSize, gridHeight } = gridConfig

    // 计算网格范围（度数）
    const halfExtent = extent / 2
    const minLng = centerLng - halfExtent / 111320
    const maxLng = centerLng + halfExtent / 111320
    const minLat = centerLat - halfExtent / 111320
    const maxLat = centerLat + halfExtent / 111320

    const dLng = gridSize / 111320
    const dLat = gridSize / 111320

    // 创建 PolylineCollection
    gridCollection = new Cesium.PolylineCollection()
    v.scene.primitives.add(gridCollection)

    const levels = 3
    const gridColor = new Cesium.Color(0.0, 0.8, 1.0, 0.35)
    const gridColorDim = new Cesium.Color(0.0, 0.6, 0.9, 0.2)

    for (let level = 0; level < levels; level++) {
      const minH = 50 + level * gridHeight
      const maxH = minH + gridHeight
      const color = level === 0 ? gridColor : gridColorDim

      for (let lng = minLng; lng < maxLng; lng += dLng) {
        for (let lat = minLat; lat < maxLat; lat += dLat) {
          const lng2 = Math.min(lng + dLng, maxLng)
          const lat2 = Math.min(lat + dLat, maxLat)

          addRectEdges(lng, lng2, lat, lat2, minH, color)
          addRectEdges(lng, lng2, lat, lat2, maxH, color)
          addVerticalEdges(lng, lat, lng2, lat2, minH, maxH, color)
        }
      }
    }

    isVisible.value = true

    function addEdge(x1, y1, z1, x2, y2, z2, color) {
      gridCollection.add({
        positions: [
          Cesium.Cartesian3.fromDegrees(x1, y1, z1),
          Cesium.Cartesian3.fromDegrees(x2, y2, z2)
        ],
        width: 1.5,
        material: Cesium.Material.fromType('Color', {
          color: color
        })
      })
    }

    function addRectEdges(minX, maxX, minY, maxY, height, color) {
      addEdge(minX, minY, height, maxX, minY, height, color)
      addEdge(maxX, minY, height, maxX, maxY, height, color)
      addEdge(maxX, maxY, height, minX, maxY, height, color)
      addEdge(minX, maxY, height, minX, minY, height, color)
    }

    function addVerticalEdges(minX, minY, maxX, maxY, minH, maxH, color) {
      addEdge(minX, minY, minH, minX, minY, maxH, color)
      addEdge(maxX, minY, minH, maxX, minY, maxH, color)
      addEdge(maxX, maxY, minH, maxX, maxY, maxH, color)
      addEdge(minX, maxY, minH, minX, maxY, maxH, color)
    }
  }

  function setVisible(visible) {
    if (gridCollection) {
      gridCollection.show = visible
      isVisible.value = visible
    }
  }

  function destroy() {
    if (gridCollection && viewer.value) {
      viewer.value.scene.primitives.remove(gridCollection)
      gridCollection = gridCollection.destroy()
      gridCollection = null
    }
  }

  return {
    isVisible,
    init,
    setVisible,
    destroy
  }
}
