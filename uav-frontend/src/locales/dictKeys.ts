/**
 * 字典值 → i18n key 映射表。
 * value 与后端 DictInitializer 种子 / useDict 内置 FALLBACK 一致（已核对）。
 * 命中的字典值展示时优先走 t(key)；未命中的值回退后端/内置中文 label。
 * plan_status 的 RELEASED / IN_FLIGHT / EXPIRED / CANCELLED 为前端补齐的
 * 生命周期状态（后端字典暂未收录），两语言均提供词条。
 */
export const DICT_I18N_KEYS: Record<string, Record<string, string>> = {
  airspace_type: {
    CONTROL: 'dict.airspace_type.CONTROL',
    CTR: 'dict.airspace_type.CTR',
    OPERATION: 'dict.airspace_type.OPERATION',
    CORRIDOR: 'dict.airspace_type.CORRIDOR',
    DEMO: 'dict.airspace_type.DEMO',
    NO_FLY: 'dict.airspace_type.NO_FLY',
    RESTRICTED: 'dict.airspace_type.RESTRICTED',
    TEST: 'dict.airspace_type.TEST',
    TEMP_NO_FLY: 'dict.airspace_type.TEMP_NO_FLY',
  },
  drone_type: {
    MULTIROTOR: 'dict.drone_type.MULTIROTOR',
    FIXED_WING: 'dict.drone_type.FIXED_WING',
    HELICOPTER: 'dict.drone_type.HELICOPTER',
    VTOL: 'dict.drone_type.VTOL',
  },
  register_status: {
    PENDING: 'dict.register_status.PENDING',
    APPROVED: 'dict.register_status.APPROVED',
    REJECTED: 'dict.register_status.REJECTED',
  },
  pilot_status: {
    ACTIVE: 'dict.pilot_status.ACTIVE',
    SUSPENDED: 'dict.pilot_status.SUSPENDED',
  },
  plan_status: {
    DRAFT: 'dict.plan_status.DRAFT',
    PENDING_LEVEL1: 'dict.plan_status.PENDING_LEVEL1',
    PENDING_LEVEL2: 'dict.plan_status.PENDING_LEVEL2',
    PENDING_LEVEL3: 'dict.plan_status.PENDING_LEVEL3',
    APPROVED: 'dict.plan_status.APPROVED',
    REJECTED: 'dict.plan_status.REJECTED',
    MILITARY_CANCELLED: 'dict.plan_status.MILITARY_CANCELLED',
    COMPLETED: 'dict.plan_status.COMPLETED',
    RELEASED: 'dict.plan_status.RELEASED',
    IN_FLIGHT: 'dict.plan_status.IN_FLIGHT',
    EXPIRED: 'dict.plan_status.EXPIRED',
    CANCELLED: 'dict.plan_status.CANCELLED',
  },
  alarm_level: {
    CRITICAL: 'dict.alarm_level.CRITICAL',
    MAJOR: 'dict.alarm_level.MAJOR',
    SERIOUS: 'dict.alarm_level.SERIOUS',
    WARNING: 'dict.alarm_level.WARNING',
    GENERAL: 'dict.alarm_level.GENERAL',
    MINOR: 'dict.alarm_level.MINOR',
  },
  route_direction: {
    ONE_WAY: 'dict.route_direction.ONE_WAY',
    TWO_WAY: 'dict.route_direction.TWO_WAY',
  },
  airport_type: {
    TAKEOFF: 'dict.airport_type.TAKEOFF',
    LANDING: 'dict.airport_type.LANDING',
    ALL: 'dict.airport_type.ALL',
  },
  alarm_type: {
    TERRAIN_COLLISION: 'dict.alarm_type.TERRAIN_COLLISION',
    NO_FLIGHT_PLAN: 'dict.alarm_type.NO_FLIGHT_PLAN',
    AIRSPACE: 'dict.alarm_type.AIRSPACE',
    NO_PLAN: 'dict.alarm_type.NO_PLAN',
    ALTITUDE: 'dict.alarm_type.ALTITUDE',
    SPEED: 'dict.alarm_type.SPEED',
    GEOFENCE: 'dict.alarm_type.GEOFENCE',
    CONFLICT: 'dict.alarm_type.CONFLICT',
    ROUTE: 'dict.alarm_type.ROUTE',
    WEATHER: 'dict.alarm_type.WEATHER',
    EQUIPMENT: 'dict.alarm_type.EQUIPMENT',
    TERRAIN: 'dict.alarm_type.TERRAIN',
  },
  user_role: {
    ADMIN: 'dict.user_role.ADMIN',
    REGULATOR: 'dict.user_role.REGULATOR',
    OPERATOR: 'dict.user_role.OPERATOR',
    PILOT: 'dict.user_role.PILOT',
    MILITARY: 'dict.user_role.MILITARY',
  },
  violation_status: {
    PENDING: 'dict.violation_status.PENDING',
    PROCESSING: 'dict.violation_status.PROCESSING',
    CLOSED: 'dict.violation_status.CLOSED',
  },
}
