import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import cesium from 'vite-plugin-cesium'
import path from 'path'
import { pathToFileURL } from 'url'
import { randomUUID } from 'crypto'

// ================================================================
// Vite 代理中间件 — Mock 数据接口（V7.9 统一单次 init）
//
//   ① POST   /api/init              — 初始化/筛选（统一入口，一次调用）
//   ② GET    /api/airspace/data     — 永久数据
//   ③ GET    /api/drone/packet/{idx}?planId=xxx  — 轨迹帧 + alarms + flightPlans + 时效数据
//   ④ DELETE /api/plan/{planId}     — 释放 plan 临时数据
//
// ★ 数据职责：
//   init   → plan + drones 摘要 + alarmTypes 摘要
//            + alarms[]（仅当 filters 含 alarmTypes/alarmLevels 时）
//   packet → 完整 alarms[] + flight_plans[]（含 startTime/endTime 等细节）
// ================================================================

const DRONE_COUNT = 300
const TOTAL_DURATION = 3600
const FRAME_INTERVAL = 2
const TIME_ORIGIN_MS = new Date('2026-05-21T08:00:00+08:00').getTime()

const TARGET_PACKET_BYTES = 3_000_000
const BYTES_PER_FRAME = 300
const BYTES_OVERHEAD = 50_000
const VALID_PACKET_DURATIONS = [10, 15, 20, 30, 60, 120, 180, 300, 600]

function calculatePacketPlan(totalDurationSec, droneCount, frameInterval) {
  const totalFrames = droneCount * (totalDurationSec / frameInterval)
  const estimatedTotalBytes = totalFrames * BYTES_PER_FRAME
  const idealPackets = Math.max(1, Math.ceil(estimatedTotalBytes / TARGET_PACKET_BYTES))
  const idealDuration = totalDurationSec / idealPackets
  let packetDuration = VALID_PACKET_DURATIONS[0]
  let bestDiff = Math.abs(packetDuration - idealDuration)
  for (const d of VALID_PACKET_DURATIONS) {
    const diff = Math.abs(d - idealDuration)
    if (diff < bestDiff) { bestDiff = diff; packetDuration = d }
  }
  const totalPackets = Math.ceil(totalDurationSec / packetDuration)
  const estimatedBytesPerPacket = Math.ceil(estimatedTotalBytes / totalPackets) + BYTES_OVERHEAD
  return { totalDuration: totalDurationSec, packetDuration, totalPackets, droneCount, frameInterval, estimatedBytesPerPacket }
}

const DEFAULT_PLAN = calculatePacketPlan(TOTAL_DURATION, DRONE_COUNT, FRAME_INTERVAL)

let _metaCache = null
const _packetCache = new Map()
let _globalAlarmsCache = null
let _globalFlightPlansCache = null

const _planStore = new Map()

export default defineConfig({
  plugins: [
    vue(),
    cesium(),
    {
      name: 'mock-middleware',
      async configureServer(server) {
        const mockDataPath = pathToFileURL(
          path.resolve(__dirname, 'src/utils/mockData.js')
        ).href

        const {
          generateDrones,
          generateTrackRecords,
          generateAlarms,
          generateTimeAirspaces,
          generateTimeRoutes,
          generateWeather,
          generateFlightPlans
        } = await import(mockDataPath)

        console.log(`[Mock] 生成 ${DRONE_COUNT} 架无人机...`)
        _metaCache = generateDrones(DRONE_COUNT)
        _packetCache.clear()
        _globalAlarmsCache = null
        _globalFlightPlansCache = null
        _planStore.clear()
        console.log(`[Mock] 就绪。默认方案: ${DEFAULT_PLAN.totalPackets} 包 × ${DEFAULT_PLAN.packetDuration}s`)

        function ensureGlobalCaches() {
          if (!_globalAlarmsCache) {
            _globalAlarmsCache = generateAlarms(_metaCache, 0, TOTAL_DURATION, TIME_ORIGIN_MS)
          }
          if (!_globalFlightPlansCache) {
            _globalFlightPlansCache = generateFlightPlans(0, TOTAL_DURATION, DRONE_COUNT)
          }
        }

        function readBody(req) {
          return new Promise((resolve, reject) => {
            let body = ''
            req.on('data', chunk => { body += chunk })
            req.on('end', () => {
              try { resolve(body ? JSON.parse(body) : {}) }
              catch (e) { resolve({}) }
            })
            req.on('error', reject)
          })
        }

        function releasePlan(planId) {
          if (!planId || !_planStore.has(planId)) return false
          _planStore.delete(planId)
          for (const pk of _packetCache.keys()) {
            if (pk.startsWith(planId)) _packetCache.delete(pk)
          }
          return true
        }

        function _buildAlarmInfoMap() {
          const map = new Map()
          for (const a of _globalAlarmsCache) {
            if (!map.has(a.droneId)) {
              map.set(a.droneId, { count: 0, types: new Set(), levels: new Set(), firstTime: null })
            }
            const info = map.get(a.droneId)
            info.count++
            info.types.add(a.alarmType)
            info.levels.add(a.alarmLevel)
            if (info.firstTime === null || a.startTime < info.firstTime) info.firstTime = a.startTime
          }
          return map
        }

        function _buildMissionMap() {
          const map = new Map()
          for (const fp of _globalFlightPlansCache) {
            if (!map.has(fp.droneId)) map.set(fp.droneId, new Set())
            map.get(fp.droneId).add(fp.missionType)
          }
          return map
        }

        // ★ 获取时间范围内出现的告警类型（供筛选面板下拉框）
        function _buildAlarmTypesSummary(timeStartSec, timeEndSec) {
          const types = new Set()
          for (const a of _globalAlarmsCache) {
            if (a.endTime > timeStartSec && a.startTime < timeEndSec) {
              types.add(a.alarmType)
            }
          }
          return [...types].sort()
        }

        function buildDroneMetaList(drones, filters) {
          ensureGlobalCaches()
          const droneMissions = _buildMissionMap()
          const droneAlarmInfo = _buildAlarmInfoMap()

          let filtered = [...drones]

          if (filters) {
            if (filters.keyword) {
              const kw = filters.keyword.toUpperCase().trim()
              filtered = filtered.filter(d => d.id.includes(kw))
            }
            if (filters.missionTypes && filters.missionTypes.length > 0) {
              filtered = filtered.filter(d => {
                const types = droneMissions.get(d.id)
                return types && filters.missionTypes.some(mt => types.has(mt))
              })
            }
            if (filters.alarmTypes && filters.alarmTypes.length > 0) {
              filtered = filtered.filter(d => {
                const info = droneAlarmInfo.get(d.sn)
                return info && filters.alarmTypes.some(at => info.types.has(at))
              })
            }
            if (filters.alarmLevels && filters.alarmLevels.length > 0) {
              filtered = filtered.filter(d => {
                const info = droneAlarmInfo.get(d.sn)
                return info && filters.alarmLevels.some(lv => info.levels.has(lv))
              })
            }
            if (typeof filters.hasAlarm === 'boolean') {
              filtered = filtered.filter(d => {
                const info = droneAlarmInfo.get(d.sn)
                return filters.hasAlarm ? (info && info.count > 0) : (!info || info.count === 0)
              })
            }
          }

          return filtered.map(d => {
            const alarmInfo = droneAlarmInfo.get(d.sn)
            let firstActive = null
            const plans = _globalFlightPlansCache.filter(fp => fp.droneId === d.id)
            if (plans.length > 0) firstActive = Math.min(...plans.map(p => p.startTime))
            if (alarmInfo && alarmInfo.firstTime !== null) {
              if (firstActive === null || alarmInfo.firstTime < firstActive) firstActive = alarmInfo.firstTime
            }

            return {
              droneId: d.id,
              deviceSn: d.sn || `SN-${String(d.id).padStart(5, '0')}`,
              type: d.type || 'rotor',
              model: d.model || 'MD-4',
              initialLng: d.waypoints?.[0]?.lng ?? 116.397,
              initialLat: d.waypoints?.[0]?.lat ?? 39.908,
              initialHeight: d.altitudes?.[0] ?? 120,
              waypoints: d.waypoints || [],
              altitudes: d.altitudes || [],
              period: d.period || 60,
              missionTypes: [...(droneMissions.get(d.id) || new Set())],
              alarmCount: alarmInfo ? alarmInfo.count : 0,
              alarmTypes: alarmInfo ? [...alarmInfo.types] : [],
              firstActiveTime: firstActive
            }
          })
        }

        function filterAlarms(alarms, filters) {
          if (!filters) return alarms
          let result = [...alarms]
          if (filters.alarmTypes && filters.alarmTypes.length > 0) {
            result = result.filter(a => filters.alarmTypes.includes(a.alarmType))
          }
          if (filters.alarmLevels && filters.alarmLevels.length > 0) {
            result = result.filter(a => filters.alarmLevels.includes(a.alarmLevel))
          }
          if (filters.keyword) {
            const kw = filters.keyword.toUpperCase().trim()
            result = result.filter(a => a.droneId.includes(kw))
          }
          return result
        }

        // ★ 构建精简的 plan 对象
        function buildPlanObj(plan) {
          return {
            totalDuration: plan.totalDuration,
            packetDuration: plan.packetDuration,
            totalPackets: plan.totalPackets,
            droneCount: plan.droneCount,
            frameInterval: plan.frameInterval,
            timeOriginMs: plan.timeOriginMs,
            estimatedBytesPerPacket: plan.estimatedBytesPerPacket
          }
        }

        server.middlewares.use(async function (req, res, next) {
          const url = new URL(req.url, `http://${req.headers.host}`)
          const pathname = url.pathname

          // ═══════════════════════════════════════════════
          // ④ DELETE /api/plan/{planId}
          // ═══════════════════════════════════════════════
          const deletePlanMatch = pathname.match(/^\/api\/plan\/([a-f0-9-]+)$/)
          if (deletePlanMatch && req.method === 'DELETE') {
            const planId = deletePlanMatch[1]
            const existed = releasePlan(planId)
            console.log(`[Mock] DELETE /api/plan/${planId.slice(0, 8)}... ${existed ? '✅ 已释放' : '⚠️ 不存在'} (剩余 ${_planStore.size} 个 plan)`)
            res.setHeader('Content-Type', 'application/json')
            res.end(JSON.stringify({ code: 0, message: existed ? 'released' : 'not_found' }))
            return
          }

          // ═══════════════════════════════════════════════
          // ① POST /api/init — 统一入口，一次调用
          //   ★ 所有 init 调用都创建 plan（不再区分 search/filter 模式）
          //   ★ alarms[] 仅当 filters 含 alarmTypes/alarmLevels 时返回
          // ═══════════════════════════════════════════════
          if (pathname === '/api/init' && req.method === 'POST') {
            ensureGlobalCaches()
            const body = await readBody(req)

            const timeStartMs = body.timeStart || TIME_ORIGIN_MS
            const timeEndMs = body.timeEnd || (TIME_ORIGIN_MS + TOTAL_DURATION * 1000)
            const totalDurationSec = Math.ceil((timeEndMs - timeStartMs) / 1000)
            const timeStartSec = Math.round((timeStartMs - TIME_ORIGIN_MS) / 1000)
            const timeEndSec = Math.round((timeEndMs - TIME_ORIGIN_MS) / 1000)

            const filters = body.filters || null
            const hasAnyFilter = !!(filters && Object.keys(filters).length > 0)

            // ★ 判断是否需要返回 alarms（仅告警类筛选时）
            const needsAlarms = hasAnyFilter && (
              (filters.alarmTypes && filters.alarmTypes.length > 0) ||
              (filters.alarmLevels && filters.alarmLevels.length > 0)
            )

            const droneList = buildDroneMetaList(_metaCache, filters)

            // ★ 统一创建 plan
            const effectiveDroneCount = droneList.length
            const plan = calculatePacketPlan(totalDurationSec, effectiveDroneCount, FRAME_INTERVAL)
            plan.timeOriginMs = TIME_ORIGIN_MS

            const planId = randomUUID()
            const filterDroneIds = hasAnyFilter ? droneList.map(d => d.droneId) : null

            _planStore.set(planId, {
              plan,
              droneMeta: droneList,
              timeStartSec,
              timeEndSec,
              isFiltered: hasAnyFilter,
              filterDroneIds
            })

            // 限制 plan 数量
            if (_planStore.size > 20) {
              const keys = [..._planStore.keys()]
              for (let i = 0; i < keys.length - 20; i++) releasePlan(keys[i])
            }

            const alarmTypes = _buildAlarmTypesSummary(timeStartSec, timeEndSec)

            const result = {
              planId,
              plan: buildPlanObj(plan),
              drones: droneList,
              alarmTypes
            }

            // ★ 告警检索时返回 alarms[]（供面板结果列表展示）
            if (needsAlarms) {
              const allAlarms = _globalAlarmsCache.filter(a =>
                a.endTime >= timeStartSec && a.startTime <= timeEndSec
              )
              result.alarms = filterAlarms(allAlarms, filters)
            }

            const tag = hasAnyFilter ? ' (筛选)' : ''
            console.log(`[Mock] POST /api/init${tag}: planId=${planId.slice(0, 8)}... ${plan.totalPackets}p×${plan.packetDuration}s, ${droneList.length} drones, ${alarmTypes.length} alarmTypes${needsAlarms ? `, ${result.alarms.length} alarms` : ''} (当前 ${_planStore.size} 个 plan)`)

            res.setHeader('Content-Type', 'application/json')
            res.end(JSON.stringify({ code: 0, message: 'ok', data: result }))
            return
          }

          // ═══════════════════════════════════════════════
          // ② GET /api/airspace/data
          // ═══════════════════════════════════════════════
          if (pathname === '/api/airspace/data' && req.method === 'GET') {
            const permanentZones = [
              { name: 'No-Fly Zone', desc: 'Permanent no-fly zone', type: 'poly', fill: 'red',
                vertices: [{lng:116.3835,lat:39.9215},{lng:116.4015,lat:39.9215},{lng:116.4015,lat:39.9035},{lng:116.3835,lat:39.9035}], h:400, permanent: true },
              { name: 'Restricted Zone', desc: 'Permanent restricted zone', type: 'ell', fill: 'orange',
                centerLng:116.3880, centerLat:39.8972, radius:1500, h:300, permanent: true },
              { name: 'Open Zone', desc: 'Permanent open zone', type: 'poly', fill: 'green',
                vertices: [{lng:116.4015,lat:39.9107},{lng:116.4168,lat:39.9107},{lng:116.4168,lat:39.8954},{lng:116.4015,lat:39.8954}], h:500, permanent: true },
              { name: 'Training Area', desc: 'Permanent training area', type: 'ell', fill: 'blue',
                centerLng:116.4078, centerLat:39.9188, radius:1200, h:200, permanent: true },
              { name: 'Special Zone', desc: 'Permanent special zone', type: 'poly', fill: 'purple',
                vertices: [{lng:116.3934,lat:39.9044},{lng:116.3997,lat:39.9017},{lng:116.4033,lat:39.9053},{lng:116.4015,lat:39.9107},{lng:116.3961,lat:39.9125},{lng:116.3925,lat:39.9089}], h:350, permanent: true }
            ]

            const fenceCenterLng = 116.397, fenceCenterLat = 39.908
            const fencePts = []
            for (let i = 0; i <= 24; i++) {
              const a = (i / 24) * Math.PI * 2
              fencePts.push({
                lng: +(fenceCenterLng + (-600 + Math.cos(a) * 700) / 111320).toFixed(6),
                lat: +(fenceCenterLat + (600 + Math.sin(a) * 700) / 111320).toFixed(6)
              })
            }

            const routeColors = ['#ff4466','#44aaff','#ffaa00','#88ff44','#ff66aa','#66ffcc','#aa88ff','#ff8844','#44ffaa','#ff4488']
            const permanentRoutes = Array.from({ length: 10 }, (_, i) => {
              const pts = []
              const segments = 4 + Math.floor(Math.random() * 6)
              for (let j = 0; j <= segments; j++) {
                pts.push({ lng: 116.35 + Math.random() * 0.1, lat: 39.86 + Math.random() * 0.1, height: 80 + Math.random() * 400 })
              }
              return {
                id: `ROUTE-${i+1}`, name: `Route ${String.fromCharCode(65+i)}`,
                points: pts, color: routeColors[i],
                type: i%3===0?'inbound':i%3===1?'outbound':'cruise', permanent: true
              }
            })

            const hotspots = [
              {lng:116.38,lat:39.92,radius:0.02,intensity:80},
              {lng:116.42,lat:39.88,radius:0.03,intensity:60},
              {lng:116.35,lat:39.95,radius:0.025,intensity:70},
              {lng:116.45,lat:39.85,radius:0.02,intensity:50}
            ]
            const heatmapPoints = Array.from({ length: 300 }, () => {
              const isHot = Math.random() < 0.8
              if (isHot) {
                const h = hotspots[Math.floor(Math.random() * hotspots.length)]
                return { lng: h.lng+(Math.random()-0.5)*h.radius*2, lat: h.lat+(Math.random()-0.5)*h.radius*2, height: 100+Math.random()*300, value: h.intensity*(0.5+Math.random()*0.5) }
              }
              return { lng: 116.35+Math.random()*0.1, lat: 39.86+Math.random()*0.1, height: 50+Math.random()*450, value: 5+Math.random()*20 }
            })

            res.setHeader('Content-Type', 'application/json')
            res.end(JSON.stringify({
              code: 0, message: 'ok',
              data: {
                permanent_zones: permanentZones,
                permanent_routes: permanentRoutes,
                fencePts,
                heatmapPoints,
                gridConfig: { extent: 2000, gridSize: 250, gridHeight: 120 },
                initial_weather: generateWeather(0, 60)
              }
            }))
            return
          }

          // ═══════════════════════════════════════════════
          // ③ GET /api/drone/packet/{idx}?planId=xxx
          // ═══════════════════════════════════════════════
          const packetMatch = pathname.match(/^\/api\/drone\/packet\/(\d+)$/)
          if (packetMatch && req.method === 'GET') {
            const idx = parseInt(packetMatch[1], 10)
            const planId = url.searchParams.get('planId')

            if (!planId) {
              res.statusCode = 400
              res.setHeader('Content-Type', 'application/json')
              res.end(JSON.stringify({ code: 4001, message: 'Missing planId parameter' }))
              return
            }

            const planEntry = _planStore.get(planId)
            if (!planEntry) {
              res.statusCode = 404
              res.setHeader('Content-Type', 'application/json')
              res.end(JSON.stringify({ code: 4004, message: 'Invalid or expired planId' }))
              return
            }

            const { plan, droneMeta, timeStartSec, isFiltered, filterDroneIds } = planEntry
            const { packetDuration, totalDuration, totalPackets } = plan

            if (idx < 0 || idx >= totalPackets) {
              res.statusCode = 400
              res.setHeader('Content-Type', 'application/json')
              res.end(JSON.stringify({ code: 4002, message: `Packet index out of range: 0~${totalPackets - 1}` }))
              return
            }

            const cacheKey = isFiltered ? null : `${planId}:${idx}`
            if (cacheKey && _packetCache.has(cacheKey)) {
              res.setHeader('Content-Type', 'application/json')
              res.setHeader('X-Cache', 'HIT')
              res.end(_packetCache.get(cacheKey))
              return
            }

            const startSec = timeStartSec + idx * packetDuration
            const endSec = Math.min(timeStartSec + (idx + 1) * packetDuration, timeStartSec + totalDuration)

            const filterTag = isFiltered ? ` [${droneMeta.length} drones]` : ''
            console.log(`[Mock] Packet ${idx}/${totalPackets} (${startSec}~${endSec}s)${filterTag} planId=${planId.slice(0, 8)}...`)

            const effectiveMeta = droneMeta.map(d => _metaCache.find(m => m.id === d.droneId) || d)

            const dataList = generateTrackRecords(effectiveMeta, startSec - timeStartSec, endSec - timeStartSec, TIME_ORIGIN_MS)
            const allAlarms = generateAlarms(effectiveMeta, startSec - timeStartSec, endSec - timeStartSec, TIME_ORIGIN_MS)
            const alarms = isFiltered
              ? allAlarms.filter(a => filterDroneIds.includes(a.droneId))
              : allAlarms

            const timeAirspaces = generateTimeAirspaces(startSec - timeStartSec, endSec - timeStartSec)
            const timeRoutes = generateTimeRoutes(startSec - timeStartSec, endSec - timeStartSec)
            const weather = generateWeather(startSec - timeStartSec, endSec - timeStartSec)

            const allFlightPlans = generateFlightPlans(startSec - timeStartSec, endSec - timeStartSec, isFiltered ? effectiveMeta.length : DRONE_COUNT)
            const flightPlans = isFiltered
              ? allFlightPlans.filter(fp => filterDroneIds.includes(fp.droneId))
              : allFlightPlans

            const jsonStr = JSON.stringify({
              code: 0, message: 'ok',
              data: {
                packetIndex: idx,
                startTime: startSec - timeStartSec,
                endTime: endSec - timeStartSec,
                data_list: dataList,
                alarms,
                time_airspaces: timeAirspaces,
                time_routes: timeRoutes,
                weather,
                flight_plans: flightPlans
              }
            })

            if (!isFiltered) _packetCache.set(cacheKey, jsonStr)

            console.log(`[Mock]   → ${dataList.length} frames + ${alarms.length} alarms + ${flightPlans.length} flightPlans${isFiltered ? '' : ', cached'}`)

            res.setHeader('Content-Type', 'application/json')
            res.setHeader('X-Cache', isFiltered ? 'FILTERED' : 'MISS')
            res.end(jsonStr)
            return
          }

          next()
        })
      }
    }
  ],
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') },
    extensions: ['.js', '.vue', '.json'],
  },
  server: { port: 3000 },
  optimizeDeps: { include: ['cesium'], noDiscovery: true }
})
