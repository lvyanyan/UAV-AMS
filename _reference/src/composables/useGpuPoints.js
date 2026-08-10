/**
 * GPU 实例化无人机图标渲染 — V7.1 后端筛选架构
 *
 * ★ V7.1: init() 支持重建（先设 null → scene移除 → 微任务延迟destroy）
 *         移除 applyDroneFilter/getVisibleDroneIds（改为后端筛选）
 *
 * ★ V6: applyAlertIcons 接收 alarms 数组（独立告警实体）
 *
 * ★ 告警驱动图标切换：
 *    从 alarms（独立告警时间段实体）中提取每架无人机的告警列表
 *    调用 determineMaxAlertLevel(alarms) → 'safe' | 'warn' | 'danger'
 *      safe   → default-fusion.png
 *      warn   → warn-fusion.png
 *      danger → hf-fusion.png
 *     高亮选中 → 仅 scale=1.5，不替换图片
 */

import { shallowRef } from 'vue'
import * as Cesium from 'cesium'
import {
  ALERT_ICON_MAP,
  DEFAULT_DRONE_ICON,
  determineMaxAlertLevel,
  ALERT_LEVELS
} from '../utils/constants.js'

export function useGpuPoints(viewerRef) {
  let collection = null
  let _initialized = false
  let _imgDefault = null
  let _rebuilding = false      // ★ 重建锁：防止 onPostRender 在销毁间隙访问

  const _alertImages = {}            // alertLevel → Image
  const _alertOverrides = new Map()  // droneIndex → alertLevel

  const highlightIndex = shallowRef(-1)

  function loadImage(url) {
    return new Promise((resolve, reject) => {
      const img = new Image()
      img.crossOrigin = 'anonymous'
      img.onload = () => resolve(img)
      img.onerror = () => reject(new Error('图片加载失败: ' + url))
      img.src = url
    })
  }

  /**
   * ★ V7.1: init 支持重建
   *   - 先设 collection=null 阻断 updatePositions
   *   - 从 scene.primitives 移除旧 collection
   *   - 微任务延迟 destroy（等 Cesium 当前帧渲染完毕）
   */
  async function init(count) {
    const v = viewerRef.value
    if (!v) {
      console.warn('[useGpuPoints] init: viewerRef 为空，跳过')
      return
    }

    // ★ 重建锁：阻止 onPostRender 在销毁间隙访问
    _rebuilding = true

    // ★ V7.1: 如果已初始化，先安全销毁旧数据
    if (_initialized) {
      console.log(`[useGpuPoints] 销毁旧 ${collection ? collection.length : '?'} 个 billboard...`)
      _safeDestroy(v)
    }

    try { _imgDefault = await loadImage(DEFAULT_DRONE_ICON) } catch (e) { /* 静默 */ }

    // 预加载告警图标
    for (const [level, url] of Object.entries(ALERT_ICON_MAP)) {
      try { _alertImages[level] = await loadImage(url) } catch (e) { _alertImages[level] = null }
    }

    collection = new Cesium.BillboardCollection({ scene: v.scene })
    v.scene.primitives.add(collection)

    const startPos = Cesium.Cartesian3.fromDegrees(116.4, 39.9, 300)
    for (let i = 0; i < count; i++) {
      collection.add({
        position: startPos.clone(),
        image: _imgDefault || DEFAULT_DRONE_ICON,
        width: 28, height: 28,
        scale: 1.0,
        color: Cesium.Color.WHITE,
        translucencyByDistance: new Cesium.NearFarScalar(1.5e5, 1.0, 8e5, 0.0),
        pixelOffsetScaleByDistance: new Cesium.NearFarScalar(1.5e5, 3.0, 8e5, 0.5),
        id: i
      })
    }

    _initialized = true
    _rebuilding = false
    console.log(`[useGpuPoints] ✅ 初始化 ${count} 个 billboard（scene 中现有 ${v.scene.primitives.length} 个 primitive）`)
  }

  /**
   * ★ 安全销毁：collection=null 先行 → scene 移除 → 微任务延迟 destroy
   *   避免 Cesium 渲染循环中访问已销毁对象导致 "This object was destroyed" 崩溃
   */
  function _safeDestroy(v) {
    const oldColl = collection
    collection = null  // ★ 先置空，阻断 updatePositions/applyAlertIcons

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
    _alertOverrides.clear()
    highlightIndex.value = -1
    applyAlertIcons._prevAlertDroneIndices = null
    _initialized = false
  }

  function updatePositions(cartArray) {
    if (_rebuilding || !collection || !cartArray) return
    if (collection.isDestroyed && collection.isDestroyed()) return
    const len = Math.min(cartArray.length, collection.length)
    for (let i = 0; i < len; i++) {
      const b = collection.get(i)
      if (!b) continue
      b.position = cartArray[i]
    }
  }

  function highlight(index) {
    if (!collection) return
    highlightIndex.value = index
    const count = collection.length
    for (let i = 0; i < count; i++) {
      const b = collection.get(i)
      if (!b) continue
      if (i === index) {
        b.scale = 1.5
      } else {
        b.scale = 1.0
        _applyIconForDrone(b, i)
      }
    }
  }

  // ──────────────────────────────────────────────
  // ★ V6: 告警图标切换 — 使用独立 alarms 数组
  // ──────────────────────────────────────────────

  function applyAlertIcons(alarms, currentTimeSec, deviceSnToSlot) {
    if (_rebuilding || !collection || !alarms || !deviceSnToSlot || deviceSnToSlot.size === 0) return

    const droneAlarmMap = new Map()
    for (const alarm of alarms) {
      if (alarm.startTime > currentTimeSec || alarm.endTime < currentTimeSec) continue
      const droneId = alarm.droneId
      if (!droneAlarmMap.has(droneId)) droneAlarmMap.set(droneId, [])
      droneAlarmMap.get(droneId).push(alarm)
    }

    _alertOverrides.clear()

    for (const [droneId, droneAlarms] of droneAlarmMap) {
      const slotIdx = deviceSnToSlot.get(droneId)
      if (slotIdx === undefined) continue
      const level = determineMaxAlertLevel(droneAlarms)
      if (level !== ALERT_LEVELS.SAFE) {
        _alertOverrides.set(slotIdx, level)
      }
    }

    for (const [droneIdx, level] of _alertOverrides) {
      const b = collection.get(droneIdx)
      if (b && droneIdx !== highlightIndex.value) {
        _applyIconForDrone(b, droneIdx)
      }
    }

    if (applyAlertIcons._prevAlertDroneIndices) {
      for (const idx of applyAlertIcons._prevAlertDroneIndices) {
        if (!_alertOverrides.has(idx)) {
          const b = collection.get(idx)
          if (b && idx !== highlightIndex.value) {
            b.image = _imgDefault || DEFAULT_DRONE_ICON
            b.color = Cesium.Color.WHITE
            b.scale = 1.0
          }
        }
      }
    }

    applyAlertIcons._prevAlertDroneIndices = new Set(_alertOverrides.keys())
  }

  function _applyIconForDrone(billboard, droneIndex) {
    const level = _alertOverrides.get(droneIndex)
    if (level && _alertImages[level]) {
      billboard.image = _alertImages[level]
    } else {
      billboard.image = _imgDefault || DEFAULT_DRONE_ICON
    }
    billboard.color = Cesium.Color.WHITE
    billboard.scale = 1.0
  }

  function clearAlertOverrides() {
    _alertOverrides.clear()
    if (!collection) return
    const count = collection.length
    for (let i = 0; i < count; i++) {
      const b = collection.get(i)
      if (!b) continue
      b.image = _imgDefault || DEFAULT_DRONE_ICON
      b.color = Cesium.Color.WHITE
      b.scale = 1.0
    }
  }

  function getCollection() { return collection }

  function destroy() {
    const v = viewerRef.value
    _safeDestroy(v)
    _imgDefault = null
    for (const key of Object.keys(_alertImages)) {
      delete _alertImages[key]
    }
  }

  return {
    init, updatePositions, highlight,
    getCollection, destroy,
    isInitialized: () => _initialized,
    highlightIndex,
    applyAlertIcons, clearAlertOverrides
  }
}
