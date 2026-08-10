/**
 * Mock 数据生成器（V7.9 纯净架构）
 *
 * 所有坐标均为 WGS84 绝对经纬度 (EPSG:4326)
 * 告警类型严格使用 constants.js 中定义的 13 种
 */
import {
  MISSION_TYPES,
  SIMULATION_BOUNDS,
  DANGER_ALARM_TYPES,
  WARN_ALARM_TYPES,
  ALL_DISPLAY_ALARM_TYPES
} from './constants.js'

// ============================================================
// 工具函数
// ============================================================

/** 确定性伪随机（种子: index * 7 + 13） */
function seededRandom(seed) {
  let x = Math.sin(seed + 1) * 10000
  return x - Math.floor(x)
}

/** 线性插值 */
function lerp(a, b, t) { return a + (b - a) * t }

/** WGS84 经纬度 → 近似米（仅用于 radius/高度等小范围计算） */
function degToMeter(deg, lat) {
  const latR = lat * Math.PI / 180
  return deg * 111320 * Math.cos(latR)
}

// ============================================================
// 飞行路径模板（16 个绝对 GPS 航点）
// ============================================================

const WAYPOINTS = [
  { lng: 116.340, lat: 39.840 },
  { lng: 116.380, lat: 39.845 },
  { lng: 116.440, lat: 39.940 },
  { lng: 116.460, lat: 39.920 },
  { lng: 116.450, lat: 39.860 },
  { lng: 116.420, lat: 39.830 },
  { lng: 116.370, lat: 39.880 },
  { lng: 116.350, lat: 39.910 },
  { lng: 116.390, lat: 39.960 },
  { lng: 116.430, lat: 39.970 },
  { lng: 116.480, lat: 39.910 },
  { lng: 116.470, lat: 39.890 },
  { lng: 116.410, lat: 39.850 },
  { lng: 116.360, lat: 39.870 },
  { lng: 116.330, lat: 39.930 },
  { lng: 116.400, lat: 39.990 }
]

/** 12 条飞行路径（每条取 2~5 个连续航点） */
const FLIGHT_PATHS = [
  { name: '南北走廊', waypoints: [0, 1, 2] },
  { name: '东西走廊', waypoints: [3, 4, 5] },
  { name: 'SW-NE对角线', waypoints: [6, 7, 8] },
  { name: '矩形巡逻', waypoints: [9, 10, 11] },
  { name: '之字测绘', waypoints: [12, 13, 14, 15] },
  { name: '星形穿越', waypoints: [2, 7, 10, 13, 0] },
  { name: '环形航道', waypoints: [1, 3, 5, 8, 2] },
  { name: '快速通道', waypoints: [4, 9, 12] },
  { name: '绕城巡检', waypoints: [0, 6, 11, 15, 4] },
  { name: '跨江航线', waypoints: [7, 14, 1, 8] },
  { name: '山区测绘', waypoints: [13, 3, 10, 5] },
  { name: '海岸巡逻', waypoints: [2, 9, 6, 12] }
]

// ============================================================
// 1. 无人机元数据生成
// ============================================================

export function generateDrones(count) {
  const drones = []
  for (let i = 0; i < count; i++) {
    const seed = i * 7 + 13
    const pathIdx = i % FLIGHT_PATHS.length
    const path = FLIGHT_PATHS[pathIdx]
    const waypoints = path.waypoints.map(j => ({ ...WAYPOINTS[j] }))

    // 每架机略有不同的高度
    const altitudes = waypoints.map((_, k) =>
      80 + Math.floor(seededRandom(seed + k * 31) * 400)
    )

    // 周期 30~120s（飞完一轮的时间）
    const period = 30 + Math.floor(seededRandom(seed + 17) * 90)

    drones.push({
      id: `UAV-${String(i + 1).padStart(4, '0')}`,
      sn: `SN-${String(i + 1).padStart(5, '0')}`,
      type: i % 5 === 0 ? 'fixed_wing' : 'rotor',
      model: i % 3 === 0 ? 'MD-8' : i % 3 === 1 ? 'MD-4' : 'MD-20',
      waypoints,
      altitudes,
      period,
      pathName: path.name
    })
  }
  return drones
}

// ============================================================
// 2. 轨迹帧生成
// ============================================================

export function generateTrackRecords(drones, startSec, endSec, timeOriginMs) {
  const frameInterval = 2 // 秒
  const records = []

  for (const drone of drones) {
    const { waypoints, altitudes, period } = drone
    const wpCount = waypoints.length
    if (wpCount < 2) continue

    // 每段航点间的时长
    const segmentDuration = period / wpCount

    for (let t = startSec; t < endSec; t += frameInterval) {
      const cycleT = ((t % period) + period) % period

      // 确定当前在哪段航点之间
      const segIdxFloat = cycleT / segmentDuration
      const segIdx = Math.min(Math.floor(segIdxFloat), wpCount - 2)
      const segT = (cycleT - segIdx * segmentDuration) / segmentDuration

      const wpA = waypoints[segIdx]
      const wpB = waypoints[segIdx + 1]
      const altA = altitudes[segIdx]
      const altB = altitudes[segIdx + 1]

      const lng = lerp(wpA.lng, wpB.lng, segT)
      const lat = lerp(wpA.lat, wpB.lat, segT)
      const height = lerp(altA, altB, segT)

      // 速度（米/秒 近似）
      const dLng = Math.abs(wpB.lng - wpA.lng) * 111320 * Math.cos((lat * Math.PI) / 180)
      const dLat = Math.abs(wpB.lat - wpA.lat) * 111320
      const dist = Math.sqrt(dLng * dLng + dLat * dLat)
      const speed = segmentDuration > 0 ? dist / segmentDuration : 0

      const timems = timeOriginMs + Math.round(t * 1000)

      records.push({
        uniqueid: `TRAJ${String(drone.sn).padStart(12, '0')}H${String(Math.floor(t)).padStart(2, '0')}`,
        timems,
        longitude: +lng.toFixed(6),
        latitude: +lat.toFixed(6),
        height: +height.toFixed(1),
        speed: +speed.toFixed(5),
        speedOfPitch: +((Math.random() - 0.5) * 0.001).toFixed(6),
        speedOfYaw: +(Math.random() * 0.0005).toFixed(6),
        traceType: 0,
        device_sn: drone.sn,
        source: 'FUSION'
      })
    }
  }
  return records
}

// ============================================================
// 3. 告警生成（使用 constants.js 中的 13 种告警类型）
// ============================================================

const DANGER_LIST = [...DANGER_ALARM_TYPES]
const WARN_LIST = [...WARN_ALARM_TYPES]

/** 每种告警类型的默认持续时长（秒） */
const ALARM_DURATION_MAP = {
  '黑飞告警': 600,
  '禁飞区告警': 900,
  '限制区告警': 600,
  '危险区告警': 600,
  '碰撞告警': 120,
  '无人机冲突告警': 180,
  '近地告警': 300,
  '唯一识别码丢失告警': 480,
  '无人机故障告警': 360,
  '出界告警': 300,
  '偏航告警': 240,
  '多重匹配告警': 180,
  '电子围栏告警': 600
}

const ALARM_REASONS = {
  '黑飞告警': '侦测到无飞行计划无人机',
  '禁飞区告警': '无人机进入禁飞区',
  '限制区告警': '无人机进入限制区域',
  '危险区告警': '无人机接近危险区域',
  '碰撞告警': '检测到碰撞风险',
  '无人机冲突告警': '两架无人机距离过近',
  '近地告警': '无人机飞行高度过低',
  '唯一识别码丢失告警': '无法获取无人机唯一识别码',
  '无人机故障告警': '无人机上报故障状态',
  '出界告警': '无人机越过电子围栏边界',
  '偏航告警': '无人机偏离预定航线',
  '多重匹配告警': '同一无人机匹配到多条航迹',
  '电子围栏告警': '无人机接近或越过电子围栏'
}

export function generateAlarms(drones, startSec, endSec, _timeOriginMs) {
  const alarms = []
  const windowStart = startSec
  const windowEnd = endSec

  for (const drone of drones) {
    const seed = drone.sn ? parseInt(drone.sn.replace(/\D/g, '').slice(-5) || '0') : 0
    const alarmCount = Math.floor(seededRandom(seed * 31 + 7) * 3) // 0~2 条/架

    for (let ai = 0; ai < alarmCount; ai++) {
      // 随机选择 danger 或 warn
      const isDanger = seededRandom(seed + ai * 41) < 0.65
      const pool = isDanger ? DANGER_LIST : WARN_LIST
      const alarmType = pool[Math.floor(seededRandom(seed + ai * 53) * pool.length)]

      const dur = ALARM_DURATION_MAP[alarmType] || 300
      const startOffset = Math.floor(seededRandom(seed + ai * 67) * 3000)
      const alarmStart = startOffset
      const alarmEnd = alarmStart + dur

      // 时间窗口交集检查
      if (alarmEnd <= windowStart || alarmStart >= windowEnd) continue

      const alarmId = `ALARM-${String(drone.sn || 'UNKN').slice(-5)}-${String(ai).padStart(2, '0')}`
      const alarmLevel = isDanger ? '严重告警' : '一般告警'

      alarms.push({
        alarmId,
        droneId: drone.sn || drone.id,
        alarmType,
        alarmLevel,
        alarmStage: 1,
        startTime: alarmStart,
        endTime: alarmEnd,
        reason: ALARM_REASONS[alarmType] || '未知告警原因',
        handleType: isDanger ? '立即处置' : '持续监控'
      })
    }
  }
  return alarms
}

// ============================================================
// 4. 时效性空域生成
// ============================================================

const TIME_AIRSPACE_TEMPLATES = [
  { name: '临时禁飞区-A', desc: 'VIP活动保障', type: 'poly', fill: 'red',
    vertices: [{lng:116.385,lat:39.925},{lng:116.400,lat:39.925},{lng:116.400,lat:39.910},{lng:116.385,lat:39.910}],
    h: 300, activeFrom: 0, activeTo: 900 },
  { name: '临时训练空域-A', desc: '编队训练', type: 'ell', fill: 'orange',
    centerLng:116.410, centerLat:39.900, radius: 1200, h: 200, activeFrom: 300, activeTo: 1500 },
  { name: '临时限飞区-A', desc: '市政施工', type: 'poly', fill: 'yellow',
    vertices: [{lng:116.360,lat:39.880},{lng:116.375,lat:39.880},{lng:116.375,lat:39.865},{lng:116.360,lat:39.865}],
    h: 150, activeFrom: 600, activeTo: 1800 },
  { name: '临时警戒区-A', desc: '重大活动保障', type: 'ell', fill: 'red',
    centerLng:116.395, centerLat:39.915, radius: 800, h: 400, activeFrom: 900, activeTo: 2100 },
  { name: '临时禁飞区-B', desc: '赛事保障', type: 'poly', fill: 'red',
    vertices: [{lng:116.420,lat:39.890},{lng:116.440,lat:39.890},{lng:116.440,lat:39.870},{lng:116.420,lat:39.870}],
    h: 250, activeFrom: 1200, activeTo: 2400 },
  { name: '临时限飞区-B', desc: '军事演习', type: 'ell', fill: 'orange',
    centerLng:116.370, centerLat:39.945, radius: 1000, h: 300, activeFrom: 1800, activeTo: 3000 },
  { name: '临时训练空域-B', desc: '应急演练', type: 'poly', fill: 'blue',
    vertices: [{lng:116.450,lat:39.930},{lng:116.465,lat:39.930},{lng:116.465,lat:39.915},{lng:116.450,lat:39.915}],
    h: 200, activeFrom: 2100, activeTo: 3300 },
  { name: '雷暴禁飞区', desc: '极端天气禁飞', type: 'ell', fill: 'red',
    centerLng:116.400, centerLat:39.890, radius: 2000, h: 500, activeFrom: 2400, activeTo: 3600 }
]

export function generateTimeAirspaces(startSec, endSec) {
  return TIME_AIRSPACE_TEMPLATES.filter(
    z => z.activeTo >= startSec && z.activeFrom <= endSec
  )
}

// ============================================================
// 5. 时效性航路生成
// ============================================================

const TIME_ROUTE_TEMPLATES = [
  { routeId: 'ROUTE-1', changeType: 'closed', activeFrom: 0, activeTo: 600, reason: '临时封闭' },
  { routeId: 'ROUTE-3', changeType: 'limited', activeFrom: 300, activeTo: 1200, reason: '流量限制' },
  { routeId: 'ROUTE-5', changeType: 'closed', activeFrom: 900, activeTo: 1800, reason: '演习封闭' },
  { routeId: 'ROUTE-2', changeType: 'limited', activeFrom: 1200, activeTo: 2100, reason: '施工限流' },
  { routeId: 'ROUTE-7', changeType: 'closed', activeFrom: 1500, activeTo: 2400, reason: '赛事封闭' },
  { routeId: 'ROUTE-4', changeType: 'limited', activeFrom: 1800, activeTo: 2700, reason: '天气限流' },
  { routeId: 'ROUTE-9', changeType: 'limited', activeFrom: 2100, activeTo: 3000, reason: '流量限制' },
  { routeId: 'ROUTE-6', changeType: 'closed', activeFrom: 2400, activeTo: 3300, reason: '临时封闭' }
]

export function generateTimeRoutes(startSec, endSec) {
  return TIME_ROUTE_TEMPLATES.filter(
    r => r.activeTo >= startSec && r.activeFrom <= endSec
  )
}

// ============================================================
// 6. 气象生成
// ============================================================

export function generateWeather(startSec, endSec) {
  if (startSec >= endSec) return null

  const weather = {
    windSpeed: 3 + Math.random() * 8,
    windDirection: Math.random() * 360,
    visibility: 8000 + Math.random() * 2000,
    precipitation: Math.random() < 0.3 ? Math.random() * 5 : 0,
    weatherZones: []
  }

  // 雾区（600~1600s）
  if (startSec < 1600 && endSec > 600) {
    weather.visibility = Math.min(weather.visibility, 2000 + Math.random() * 1000)
    weather.weatherZones.push({
      type: 'fog', desc: '低能见度雾区',
      centerLng: 116.380, centerLat: 39.920, radius: 3000
    })
  }

  // 风暴区（2000~3400s，自西向东移动）
  if (startSec < 3400 && endSec > 2000) {
    const progress = Math.max(0, Math.min(1, (startSec + endSec) / 2 / 3400))
    weather.windSpeed = 12 + Math.random() * 8
    weather.weatherZones.push({
      type: 'storm', desc: '强风暴区域',
      centerLng: 116.35 + progress * 0.1, centerLat: 39.88, radius: 2500
    })
  }

  // 降雨区（3000~3600s）
  if (startSec < 3600 && endSec > 3000) {
    weather.precipitation = 3 + Math.random() * 5
    weather.weatherZones.push({
      type: 'rain', desc: '中到大雨区域',
      centerLng: 116.420, centerLat: 39.860, radius: 2000
    })
  }

  return weather
}

// ============================================================
// 7. 飞行计划生成
// ============================================================

export function generateFlightPlans(startSec, endSec, droneCount) {
  const plans = []
  const missionLabels = MISSION_TYPES.map(m => m.value)

  for (let i = 0; i < droneCount; i++) {
    const seed = i * 7 + 13
    const droneId = `UAV-${String(i + 1).padStart(4, '0')}`

    // A 组 (0~49): 3 个任务；B 组 (50~199): 2 个；C 组 (200~299): 1 个
    const taskCount = i < 50 ? 3 : i < 200 ? 2 : 1

    for (let ti = 0; ti < taskCount; ti++) {
      const startOffset = Math.floor(seededRandom(seed + ti * 101) * 3000)
      const dur = 400 + Math.floor(seededRandom(seed + ti * 97) * 500)
      const planStart = startOffset
      const planEnd = planStart + dur

      // 时间窗口交集
      if (planEnd <= startSec || planStart >= endSec) continue

      const missionType = missionLabels[Math.floor(seededRandom(seed + ti * 71) * missionLabels.length)]
      const planId = `FP-${String(i + 1).padStart(4, '0')}`
      const desc = `${MISSION_TYPES.find(m => m.value === missionType)?.label || missionType}任务 - 第${ti + 1}次`

      plans.push({
        planId: `${planId}-${ti + 1}`,
        droneId,
        missionType,
        startTime: planStart,
        endTime: planEnd,
        description: desc
      })
    }
  }
  return plans
}
