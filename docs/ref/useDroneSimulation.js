/**
 * useDroneSimulation — V7.9 告警/飞行计划仅由 packet 提供
 *
 * ★ 坐标模型：WGS84 绝对经纬度 → Cesium.Cartesian3.fromDegrees → ECEF
 *
 * ★ 数据职责：
 *   init   → 配置 + drone 摘要（alarmCount/alarmTypes 仅用于面板展示）
 *   packet → 轨迹帧 + 完整 alarms[] + flight_plans[] + 时效空域/航路/气象
 */

import { ref, computed } from 'vue'
import * as Cesium from 'cesium'
import { fetchPacket } from '../utils/apiService.js'

export function useDroneSimulation() {
  let _config = {
    planId: '',
    totalDuration: 3600,
    packetDuration: 60,
    totalPackets: 60,
    droneCount: 300,
    frameInterval: 2,
    timeOriginMs: new Date('2026-05-21T08:00:00+08:00').getTime()
  }

  const droneCount = ref(_config.droneCount)
  const droneMeta = ref([])
  const selectedDroneId = ref(null)

  // 时效性数据（随包加载）
  const timeAirspaces = ref([])
  const timeRoutes = ref([])
  const weather = ref(null)
  const flightPlans = ref([])
  const alarms = ref([])
  const filterDroneIds = ref(null)

  let _cartPool = []
  let _deviceSnList = []
  let _deviceSnToSlot = new Map()
  let _timePositionMap = new Map()
  let _sortedTimes = []
  let _loadedPacketIndex = -1
  let _packetStartTime = 0
  let _timeOriginMs = 0
  let _isReady = false
  let _isFiltered = false
  let _currentFrames = []
  let _currentSimTime = 0
  let _pendingLoad = false

  // 预加载
  let _preloadQueue = [], _preloadController = null, _isPreloading = false

  function _cancelPreload() {
    _isPreloading = false; _preloadQueue = []
    if (_preloadController) { _preloadController.abort(); _preloadController = null }
  }

  function _startPreload(currentIdx) {
    if (_isPreloading || _isFiltered) return
    _preloadQueue = [currentIdx + 1, currentIdx + 2].filter(i => i >= 0 && i < _config.totalPackets)
    if (_preloadQueue.length === 0) return
    _preloadNext()
  }

  async function _preloadNext() {
    if (!_isPreloading && _preloadQueue.length > 0) {
      _isPreloading = true
      while (_preloadQueue.length > 0) {
        const idx = _preloadQueue.shift()
        try {
          _preloadController = new AbortController()
          const resp = await fetch(`/api/drone/packet/${idx}?planId=${_config.planId}`, { signal: _preloadController.signal })
          if (resp.ok) await resp.json()
        } catch (e) {
          if (e.name !== 'AbortError') console.warn(`预加载包 ${idx} 失败:`, e)
        }
      }
      _isPreloading = false
    }
  }

  function _buildSlotMapping(frames, forceRebuild = false) {
    if (!forceRebuild && _deviceSnList.length > 0) return
    const snSet = new Set()
    for (const f of frames) snSet.add(f.device_sn)
    _deviceSnList = Array.from(snSet).sort()
    _deviceSnToSlot.clear()
    _deviceSnList.forEach((sn, i) => _deviceSnToSlot.set(sn, i))
  }

  function _buildPositionMap(frames) {
    _timePositionMap.clear()
    for (const f of frames) {
      let map = _timePositionMap.get(f.timems)
      if (!map) { map = new Map(); _timePositionMap.set(f.timems, map) }
      map.set(f.device_sn, { lng: f.longitude, lat: f.latitude, height: f.height })
    }
    _sortedTimes = Array.from(_timePositionMap.keys()).sort((a, b) => a - b)
    if (_sortedTimes.length > 0) {
      _timeOriginMs = _sortedTimes[0] - _packetStartTime * 1000
    }
  }

  function _buildFrameAtTime(targetMs, out) {
    const count = _deviceSnList.length
    if (!out || out.length < count * 3) out = new Float64Array(count * 3)

    const clampLeft = _sortedTimes.length === 0 || targetMs <= _sortedTimes[0]
    const hiStart = _sortedTimes.length - 1
    const clampRight = targetMs >= _sortedTimes[hiStart]

    if (clampLeft) {
      const map = _timePositionMap.get(_sortedTimes[0])
      for (let i = 0; i < count; i++) {
        const p = map ? map.get(_deviceSnList[i]) : undefined
        const idx = i * 3
        if (p) { out[idx] = p.lng; out[idx + 1] = p.lat; out[idx + 2] = p.height }
        else { out[idx] = 0; out[idx + 1] = 0; out[idx + 2] = 0 }
      }
      return out
    }
    if (clampRight) {
      const map = _timePositionMap.get(_sortedTimes[hiStart])
      for (let i = 0; i < count; i++) {
        const p = map.get(_deviceSnList[i])
        const idx = i * 3
        if (p) { out[idx] = p.lng; out[idx + 1] = p.lat; out[idx + 2] = p.height }
        else { out[idx] = 0; out[idx + 1] = 0; out[idx + 2] = 0 }
      }
      return out
    }

    let lo = 0, hi = hiStart
    while (hi - lo > 1) {
      const mid = (lo + hi) >> 1
      if (_sortedTimes[mid] <= targetMs) lo = mid
      else hi = mid
    }

    const t0 = _sortedTimes[lo], t1 = _sortedTimes[hi]
    const frac = (targetMs - t0) / (t1 - t0)
    const map0 = _timePositionMap.get(t0), map1 = _timePositionMap.get(t1)

    for (let i = 0; i < count; i++) {
      const sn = _deviceSnList[i]
      const p0 = map0.get(sn), p1 = map1.get(sn)
      const idx = i * 3
      if (p0 && p1) {
        out[idx]     = p0.lng + (p1.lng - p0.lng) * frac
        out[idx + 1] = p0.lat + (p1.lat - p0.lat) * frac
        out[idx + 2] = p0.height + (p1.height - p0.height) * frac
      } else if (p0) {
        out[idx] = p0.lng; out[idx + 1] = p0.lat; out[idx + 2] = p0.height
      } else if (p1) {
        out[idx] = p1.lng; out[idx + 1] = p1.lat; out[idx + 2] = p1.height
      } else {
        out[idx] = 0; out[idx + 1] = 0; out[idx + 2] = 0
      }
    }
    return out
  }

  function _rebuildCartPool(frame, count) {
    const pool = new Array(count)
    for (let i = 0; i < count; i++) {
      const idx = i * 3
      pool[i] = Cesium.Cartesian3.fromDegrees(frame[idx], frame[idx + 1], frame[idx + 2])
    }
    return pool
  }

  // ── 告警查询 API ──

  function getAlarmsByDrone(droneId) {
    return alarms.value.filter(a => a.droneId === droneId)
  }

  function getActiveAlarms(timeSec) {
    return alarms.value.filter(a => a.startTime <= timeSec && a.endTime > timeSec)
  }

  function getActiveAlarmsByDrone(droneId, timeSec) {
    return alarms.value.filter(a => a.droneId === droneId && a.startTime <= timeSec && a.endTime > timeSec)
  }

  function diffAlarmsAfterJump(prevAlarmIds, newTimeSec) {
    const prevSet = new Set(prevAlarmIds)
    const nowActive = getActiveAlarms(newTimeSec)
    const nowSet = new Set(nowActive.map(a => a.alarmId))
    return {
      newAlarms: nowActive.filter(a => !prevSet.has(a.alarmId)),
      endedAlarms: prevAlarmIds.filter(id => !nowSet.has(id)),
      activeAlarms: nowActive,
    }
  }

  function getFlightPlansByDrone(droneId) {
    return flightPlans.value.filter(p => p.droneId === droneId)
  }

  function getActiveFlightPlan(droneId, currentTimeSec) {
    return flightPlans.value.find(p =>
      p.droneId === droneId && p.startTime <= currentTimeSec && p.endTime > currentTimeSec
    )
  }

  function getFlightPlansByDroneMap() {
    const map = new Map()
    for (const p of flightPlans.value) {
      if (!map.has(p.droneId)) map.set(p.droneId, [])
      map.get(p.droneId).push(p)
    }
    return map
  }

  function getCurrentFrames() { return _currentFrames }
  function getDeviceSnToSlot() { return _deviceSnToSlot }
  function getDeviceSnList() { return _deviceSnList }

  // ── 包加载 ──

  async function _loadWindow(packetIndex) {
    const data = await fetchPacket(packetIndex)
    if (!data) throw new Error(`包 ${packetIndex} 加载失败`)

    _packetStartTime = packetIndex * _config.packetDuration

    const frames = data.data_list || []
    _currentFrames = frames
    _buildSlotMapping(frames, true)
    _buildPositionMap(frames)

    // ★ 告警 + 飞行计划仅从 packet 获取
    timeAirspaces.value = data.time_airspaces || []
    timeRoutes.value = data.time_routes || []
    weather.value = data.weather || null
    flightPlans.value = data.flight_plans || []
    alarms.value = data.alarms || []

    _loadedPacketIndex = packetIndex
    _isReady = true

    droneCount.value = _deviceSnList.length
  }

  // ── 初始化 / 重新筛选 ──

  /**
   * initFromData — 全量初始化或筛选重建
   *
   * initData = { planId, plan, drones, alarmTypes? }
   * ★ alarms/flightPlans 由 _loadWindow 从 packet 获取
   */
  async function initFromData(initData) {
    _cancelPreload()
    _pendingLoad = false

    const { planId, plan, drones } = initData

    _config = {
      planId,
      totalDuration: plan.totalDuration,
      packetDuration: plan.packetDuration,
      totalPackets: plan.totalPackets,
      droneCount: plan.droneCount,
      frameInterval: plan.frameInterval,
      timeOriginMs: plan.timeOriginMs
    }
    droneCount.value = _config.droneCount
    droneMeta.value = drones

    _isFiltered = plan.droneCount < 300
    filterDroneIds.value = _isFiltered ? drones.map(d => d.droneId) : null

    _loadedPacketIndex = -1
    _deviceSnList = []
    _deviceSnToSlot.clear()
    _timePositionMap.clear()
    _sortedTimes = []
    _cartPool = []
    _currentFrames = []

    console.log(`[useDroneSimulation] initFromData${_isFiltered ? ' (筛选)' : ''}:`, {
      planId: planId?.slice(0, 8) + '...',
      totalDuration: _config.totalDuration,
      packetDuration: _config.packetDuration,
      totalPackets: _config.totalPackets,
      droneCount: _config.droneCount
    })

    await _loadWindow(0)
    if (!_isFiltered) _startPreload(0)

    return _config.droneCount
  }

  // ── 时间跳转 ──

  function _clampPacketIndex(time) {
    return Math.min(Math.floor(time / _config.packetDuration), _config.totalPackets - 1)
  }

  async function seekToTime(time) {
    const packetIndex = _clampPacketIndex(time)
    if (packetIndex !== _loadedPacketIndex) {
      _cancelPreload()
      _pendingLoad = false
      try {
        await _loadWindow(packetIndex)
      } catch (e) {
        console.warn(`Seek 加载包 ${packetIndex} 失败:`, e)
        return
      }
      if (!_isFiltered) _startPreload(packetIndex)
    }

    const targetMs = _timeOriginMs + Math.round(time * 1000)
    const frame = _buildFrameAtTime(targetMs, null)
    _cartPool = _rebuildCartPool(frame, _deviceSnList.length)
    _currentSimTime = time
  }

  function checkPacketBoundary(time) {
    if (!_isReady || _pendingLoad) return false
    return _clampPacketIndex(time) !== _loadedPacketIndex
  }

  function updatePositions(time) {
    if (!_isReady) return null

    const idx = _clampPacketIndex(time)
    if (idx !== _loadedPacketIndex && !_pendingLoad) {
      _pendingLoad = true
      _packetStartTime = idx * _config.packetDuration
      _loadWindow(idx).then(() => {
        _pendingLoad = false
        if (!_isFiltered) _startPreload(idx)
      }).catch(() => { _pendingLoad = false })
    }

    const targetMs = _timeOriginMs + Math.round(time * 1000)
    const frame = _buildFrameAtTime(targetMs, null)
    _cartPool = _rebuildCartPool(frame, _deviceSnList.length)
    _currentSimTime = time
    return _cartPool
  }

  function getCartArray() { return _cartPool.length > 0 ? _cartPool : null }
  function getLoadedPacketIndex() { return _loadedPacketIndex }
  function getConfig() { return _config }

  // ── 选中无人机信息 ──

  const selectedDroneInfo = computed(() => {
    if (!selectedDroneId.value || !_isReady || _currentFrames.length === 0) return null

    const sn = selectedDroneId.value
    const frame = _currentFrames.find(f => f.device_sn === sn)
    if (!frame) return null

    const activeAlarms = getActiveAlarmsByDrone(sn, _currentSimTime)
    const activePlan = getActiveFlightPlan(sn, _currentSimTime)
    const allPlans = getFlightPlansByDrone(sn)

    return {
      droneId: sn,
      longitude: frame.longitude,
      latitude: frame.latitude,
      height: frame.height,
      speed: frame.speed,
      source: frame.source,
      mission: activePlan ? activePlan.missionType : null,
      alarms: activeAlarms,
      flightPlans: allPlans
    }
  })

  return {
    droneCount, droneMeta, selectedDroneId,
    timeAirspaces, timeRoutes, weather, flightPlans, alarms,
    filterDroneIds,
    initFromData,
    seekToTime, updatePositions, checkPacketBoundary,
    getCartArray, getLoadedPacketIndex, getConfig,
    getAlarmsByDrone, getActiveAlarms, getActiveAlarmsByDrone,
    diffAlarmsAfterJump,
    getFlightPlansByDrone, getActiveFlightPlan, getFlightPlansByDroneMap,
    getCurrentFrames, getDeviceSnToSlot, getDeviceSnList,
    selectedDroneInfo
  }
}
