/**
 * 字典组合式：全局缓存一次拉取（/api/dict/all），
 * 字典服务不可用时回退到内置种子（与后端 DictInitializer 保持一致）。
 */
import { computed, ref } from 'vue'
import { dictApi, type DictItem } from '@/api/dict'
import { i18n } from '@/locales'
import { DICT_I18N_KEYS } from '@/locales/dictKeys'

// 内置回退字典（dict 服务挂了页面也不裸奔英文）
const FALLBACK: Record<string, DictItem[]> = {
  airspace_type: [
    { value: 'CONTROL', label: '管制区' }, { value: 'CTR', label: '机场管制区' },
    { value: 'OPERATION', label: '作业区' }, { value: 'CORRIDOR', label: '走廊' },
    { value: 'DEMO', label: '示范区' }, { value: 'NO_FLY', label: '禁飞区' },
    { value: 'RESTRICTED', label: '限制区' }, { value: 'TEST', label: '试验区' },
    { value: 'TEMP_NO_FLY', label: '临时禁飞' },
  ],
  drone_type: [
    { value: 'MULTIROTOR', label: '多旋翼' }, { value: 'FIXED_WING', label: '固定翼' },
    { value: 'HELICOPTER', label: '直升机' }, { value: 'VTOL', label: '垂直起降' },
  ],
  register_status: [
    { value: 'PENDING', label: '待审核' }, { value: 'APPROVED', label: '已通过' },
    { value: 'REJECTED', label: '已拒绝' },
  ],
  pilot_status: [
    { value: 'ACTIVE', label: '正常' }, { value: 'SUSPENDED', label: '停飞' },
  ],
  plan_status: [
    { value: 'DRAFT', label: '草稿' }, { value: 'PENDING_LEVEL1', label: '一级审批中' },
    { value: 'PENDING_LEVEL2', label: '二级审批中' }, { value: 'PENDING_LEVEL3', label: '三级审批中' },
    { value: 'APPROVED', label: '已批准' }, { value: 'REJECTED', label: '已拒绝' },
    { value: 'MILITARY_CANCELLED', label: '军事取消' },
    { value: 'COMPLETED', label: '已完成' },
  ],
  alarm_level: [
    { value: 'CRITICAL', label: '危急' }, { value: 'MAJOR', label: '重大' },
    { value: 'SERIOUS', label: '严重' }, { value: 'WARNING', label: '警告' },
    { value: 'GENERAL', label: '一般' }, { value: 'MINOR', label: '轻微' },
  ],
  route_direction: [
    { value: 'ONE_WAY', label: '单向' }, { value: 'TWO_WAY', label: '双向' },
  ],
  airport_type: [
    { value: 'TAKEOFF', label: '起飞场' }, { value: 'LANDING', label: '降落场' },
    { value: 'ALL', label: '综合起降场' },
  ],
  alarm_type: [
    { value: 'TERRAIN_COLLISION', label: '地形碰撞' },
    { value: 'NO_FLIGHT_PLAN', label: '无计划飞行' }, { value: 'AIRSPACE', label: '空域违规' },
    { value: 'NO_PLAN', label: '无计划飞行' }, { value: 'ALTITUDE', label: '高度超限' },
    { value: 'SPEED', label: '超速飞行' }, { value: 'GEOFENCE', label: '围栏闯入' },
    { value: 'CONFLICT', label: '飞行冲突' }, { value: 'ROUTE', label: '航路偏离' },
    { value: 'WEATHER', label: '气象风险' }, { value: 'EQUIPMENT', label: '设备异常' },
    { value: 'TERRAIN', label: '地形风险' },
  ],
  user_role: [
    { value: 'ADMIN', label: '系统管理员' }, { value: 'REGULATOR', label: '监管员' },
    { value: 'OPERATOR', label: '操作员' }, { value: 'PILOT', label: '飞手' },
    { value: 'MILITARY', label: '军民协调员' },
  ],
  violation_status: [
    { value: 'PENDING', label: '待处理' }, { value: 'PROCESSING', label: '处理中' },
    { value: 'CLOSED', label: '已结案' },
  ],
}

const cache = ref<Record<string, DictItem[]>>({})
let pending: Promise<void> | null = null

/** 全量拉取一次（应用内首个 useDict 触发） */
export function loadDicts(): Promise<void> {
  if (pending) return pending
  pending = dictApi.all().then((res: any) => {
    cache.value = (res && res.data) || {}
  }).catch(() => {
    // 字典服务不可用 → 用内置种子兜底
    cache.value = { ...FALLBACK }
  })
  return pending
}

/**
 * 字典值 → 展示标签：命中 dictKeys i18n 映射时返回 t(key)（跟随 locale），
 * 否则回退后端中文 label / 内置种子 label。
 */
function translateLabel(dictType: string, value: string, fallback: string): string {
  const key = DICT_I18N_KEYS[dictType]?.[value]
  if (key && i18n.global.te(key)) return i18n.global.t(key)
  return fallback
}

export function useDict(dictType: string) {
  loadDicts()
  const items = computed<DictItem[]>(() =>
    (cache.value[dictType] || FALLBACK[dictType] || []).map(i => ({
      ...i,
      label: translateLabel(dictType, String(i.value), i.label),
    }))
  )
  /** 值 → 标签；未登记的值原样透出（便于发现漏维护的枚举） */
  function label(value?: string | number | null): string {
    if (value === null || value === undefined || value === '') return '--'
    const hit = items.value.find(i => i.value === String(value))
    return hit ? hit.label : String(value)
  }
  return { items, label }
}

/** 字典管理页使用：可变引用（编辑后写回，全局即时生效） */
export function dictCache() {
  return cache
}
