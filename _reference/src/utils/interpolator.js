/**
 * 位置插值工具
 * 根据时间计算多个无人机的批量位置
 */

import * as Cesium from 'cesium'
import { computePosition } from './mockData.js'

/**
 * 批量计算所有无人机在给定时间的位置
 * 返回 Float32Array: [lng, lat, height, lng, lat, height, ...]
 */
export function computeBatchPositions(drones, time) {
  const count = drones.length
  const positions = new Float32Array(count * 3)

  for (let i = 0; i < count; i++) {
    const pos = computePosition(drones[i], time)
    positions[i * 3] = pos.lng
    positions[i * 3 + 1] = pos.lat
    positions[i * 3 + 2] = pos.height
  }

  return positions
}

/**
 * 批量计算航向
 * 返回 Float32Array: [heading, heading, ...]
 */
export function computeBatchHeadings(drones, time) {
  const count = drones.length
  const headings = new Float32Array(count)

  for (let i = 0; i < count; i++) {
    const pos = computePosition(drones[i], time)
    headings[i] = pos.heading
  }

  return headings
}

/**
 * 将 positions Float32Array 转换为 Cartesian3 数组
 * 用于某些需要 Cartesian3 的 Cesium API
 */
export function toCartesianArray(positions, result) {
  const len = positions.length / 3
  if (!result || result.length !== len) {
    result = new Array(len)
  }

  for (let i = 0; i < len; i++) {
    const idx = i * 3
    result[i] = Cesium.Cartesian3.fromDegrees(
      positions[idx],
      positions[idx + 1],
      positions[idx + 2]
    )
  }

  return result
}
