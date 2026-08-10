/**
 * 系统常量定义（V7.9 纯净架构）
 *
 * ★ 所有运行参数从 POST /api/init 动态获取
 *   FALLBACK_CONFIG 仅在 API 完全不可用时兜底
 */

// ── Feature Flags ──

/** 查询面板开关：false = 暂时关闭，不显示入口 */
export const FEATURE_FLAGS = {
  QUERY_PANEL: false
}

// ── 无人机任务类型 ──

export const MISSION_TYPES = [
  { label: '物流配送', value: 'delivery', color: '#00ff88' },
  { label: '巡检监测', value: 'inspection', color: '#ffaa00' },
  { label: '安防巡逻', value: 'security', color: '#ff4466' },
  { label: '测绘建模', value: 'mapping', color: '#4488ff' },
  { label: '农业植保', value: 'agriculture', color: '#88ff44' },
  { label: '应急响应', value: 'emergency', color: '#ff2200' }
]

// 兜底配置（仅 API 不可用时使用）
export const FALLBACK_CONFIG = {
  totalDuration: 3600,
  packetDuration: 60,
  totalPackets: 60,
  droneCount: 300,
  frameInterval: 2,
  timeOriginMs: new Date('2026-05-21T08:00:00+08:00').getTime()
}

// 倍速选项
export const SPEED_OPTIONS = [
  { label: '0.5x', value: 0.5 },
  { label: '1x', value: 1 },
  { label: '2x', value: 2 },
  { label: '5x', value: 5 },
  { label: '10x', value: 10 }
]

// 图层类型
export const LAYER_TYPES = {
  DRONES: 'drones',
  GRID: 'grid',
  HEATMAP: 'heatmap',
  ROUTES: 'routes',
  TRAILS: 'trails',
  AIRSPACES: 'airspaces',
  TILES_3D: 'tiles3d'
}

export const LAYER_DEFAULTS = {
  [LAYER_TYPES.DRONES]:   { visible: true,  label: '无人机', icon: '🛸' },
  [LAYER_TYPES.GRID]:     { visible: true,  label: '空域网格', icon: '🔲' },
  [LAYER_TYPES.HEATMAP]:  { visible: true , label: '热力图', icon: '🌡️' },
  [LAYER_TYPES.ROUTES]:   { visible: true,  label: '航路', icon: '✈️' },
  [LAYER_TYPES.TRAILS]:   { visible: true,  label: '航迹', icon: '〰️' },
  [LAYER_TYPES.AIRSPACES]:{ visible: true,  label: '空域分区', icon: '🗺️' },
  [LAYER_TYPES.TILES_3D]: { visible: false, label: '3D Tiles 示例', icon: '🏗️' }
}

// 告警等级
export const ALERT_LEVELS = {
  SAFE:   'safe',
  WARN:   'warn',
  DANGER: 'danger'
}

// —— 告警类型定义（13 种） ——

// 高风险告警 → danger（8 种）
export const DANGER_ALARM_TYPES = new Set([
  '黑飞告警',
  '禁飞区告警',
  '电子围栏告警',
  '碰撞告警',
  '无人机冲突告警',
  '近地告警',
  '唯一识别码丢失告警',
  '无人机故障告警'
])

// 中风险告警 → warn（5 种）
export const WARN_ALARM_TYPES = new Set([
  '出界告警',
  '偏航告警',
  '多重匹配告警',
  '限制区告警',
  '危险区告警'
])

// 全部 13 种可展示的告警类型
export const ALL_DISPLAY_ALARM_TYPES = [
  ...DANGER_ALARM_TYPES,
  ...WARN_ALARM_TYPES
]

/**
 * 根据告警列表确定当前最高告警等级
 * @param {Array<{alarmType: string}>} alarms
 * @returns {'safe'|'warn'|'danger'}
 */
export function determineMaxAlertLevel(alarms) {
  if (!alarms || alarms.length === 0) return ALERT_LEVELS.SAFE

  const hasDanger = alarms.some(a => DANGER_ALARM_TYPES.has(a.alarmType))
  if (hasDanger) return ALERT_LEVELS.DANGER

  const hasWarn = alarms.some(a => WARN_ALARM_TYPES.has(a.alarmType))
  if (hasWarn) return ALERT_LEVELS.WARN

  return ALERT_LEVELS.SAFE
}

export const DEFAULT_DRONE_ICON = '/default-fusion.png'

export const ALERT_ICON_MAP = {
  [ALERT_LEVELS.WARN]:   '/warn-fusion.png',
  [ALERT_LEVELS.DANGER]: '/hf-fusion.png'
}

export const BEIJING_CENTER = {
  lng: 116.397,
  lat: 39.908,
  height: 300
}

export const SIMULATION_BOUNDS = {
  minLng: 116.30,
  maxLng: 116.50,
  minLat: 39.82,
  maxLat: 40.00,
  minHeight: 50,
  maxHeight: 500
}
