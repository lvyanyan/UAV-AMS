/**
 * API 服务（V7.9 — 统一单次 init）
 *
 *   ① POST   /api/init              — 初始化/筛选（统一入口，一次调用）
 *   ② GET    /api/airspace/data     — 永久数据（共享缓存）
 *   ③ GET    /api/drone/packet/{idx}?planId=xxx  — 轨迹帧 + alarms + flightPlans + 时效数据
 *   ④ DELETE /api/plan/{planId}     — 通知后端释放 plan 资源
 *
 * ★ 数据职责：
 *   init   → plan + drones 摘要 + alarmTypes 摘要
 *            + alarms[]（仅当 filters 含 alarmTypes/alarmLevels 时，供告警检索结果列表）
 *   packet → 完整 alarms[] + flight_plans[]（含 startTime/endTime 等细节）
 */

// ── 全局状态 ──

let _currentPlanId = null

export function getCurrentPlanId() { return _currentPlanId }

// ── ④ 释放 plan ──

export async function deletePlan(planId) {
  if (!planId) return
  try {
    console.log(`[API] 🗑️ DELETE /api/plan/${planId.slice(0, 8)}...`)
    const resp = await fetch(`/api/plan/${planId}`, { method: 'DELETE' })
    if (resp.ok) {
      console.log(`[API] ✅ plan ${planId.slice(0, 8)}... 已释放`)
    }
  } catch (e) {
    console.warn(`[API] ⚠️ plan ${planId.slice(0, 8)}... 释放失败:`, e.message)
  }
}

// ── ② 永久数据（共享 Promise 去重）──

let _airspaceDataPromise = null

export async function fetchAirspaceData() {
  if (_airspaceDataPromise) return _airspaceDataPromise
  _airspaceDataPromise = (async () => {
    try {
      console.log('[API] 🌐 GET /api/airspace/data ...')
      const resp = await fetch('/api/airspace/data')
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
      const json = await resp.json()
      if (json.code === 0) {
        console.log('[API] ✅ /api/airspace/data:', {
          zones: (json.data.permanent_zones || []).length,
          routes: (json.data.permanent_routes || []).length,
          heatmap: (json.data.heatmapPoints || []).length,
          fence: (json.data.fencePts || []).length
        })
        return json.data
      }
      throw new Error('Invalid response')
    } catch (e) {
      _airspaceDataPromise = null
      throw e
    }
  })()
  return _airspaceDataPromise
}

// ── ① 初始化 / 筛选（统一入口，一次调用）──

/**
 * POST /api/init
 *
 * ★ 请求：
 *   { timeStart, timeEnd, filters?: { keyword, missionTypes, hasAlarm, alarmTypes, alarmLevels } }
 *
 * ★ 返回：
 *   {
 *     planId, plan, drones[],
 *     alarmTypes (全局类型摘要，供筛选面板下拉框),
 *     alarms? (仅当 filters 含 alarmTypes/alarmLevels 时，供告警检索 Tab)
 *   }
 *   ★ 不含 flightPlans — 飞行计划细节由 packet 提供
 */
export async function fetchInit(options = {}) {
  try {
    const body = {}
    if (options.timeStart) body.timeStart = options.timeStart
    if (options.timeEnd) body.timeEnd = options.timeEnd
    if (options.filters) body.filters = options.filters

    const resp = await fetch('/api/init', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const json = await resp.json()
    if (json.code === 0 && json.data) {
      const prevPlanId = _currentPlanId
      _currentPlanId = json.data.planId

      // ★ 自动释放旧 plan
      if (prevPlanId && prevPlanId !== _currentPlanId) {
        deletePlan(prevPlanId)
      }

      const hasFilters = !!(options.filters && Object.keys(options.filters).length > 0)
      const tag = hasFilters ? ' (筛选)' : ''
      console.log(`[API] ✅ /api/init${tag}:`, {
        planId: json.data.planId?.slice(0, 8) + '...',
        plan: `${json.data.plan.totalPackets}包 × ${json.data.plan.packetDuration}s`,
        drones: json.data.drones.length,
        alarmTypes: json.data.alarmTypes?.length || 0,
        alarms: (json.data.alarms || []).length
      })
      return json.data
    }
    throw new Error('Invalid init response')
  } catch (err) {
    console.error('[API] ❌ /api/init 失败:', err.message)
    return null
  }
}

// ── ③ 包数据加载 ──

export async function fetchPacket(packetIndex) {
  if (!_currentPlanId) {
    console.error('[API] ❌ 无法加载包：planId 未设置')
    return null
  }
  try {
    const url = `/api/drone/packet/${packetIndex}?planId=${_currentPlanId}`
    const resp = await fetch(url)
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const json = await resp.json()
    if (json.code === 0) {
      if (json.data) json.data.packetIndex = packetIndex
      return json.data || json
    }
    throw new Error('Invalid packet response')
  } catch (err) {
    console.error(`[API] ❌ 包 ${packetIndex} 加载失败:`, err.message)
    return null
  }
}

// ── 页面卸载时释放 plan ──

export async function releaseCurrentPlan() {
  if (_currentPlanId) {
    await deletePlan(_currentPlanId)
    _currentPlanId = null
  }
}
