<template>
  <Teleport to="body">
    <div
      class="info-panel"
      :style="{
        left: position.x + 'px',
        top: position.y - 50 + 'px'
      }"
    >
      <div class="panel-arrow"></div>

      <!-- 无人机基本信息 -->
      <div class="panel-header">
        <span class="panel-drone-id">{{ drone.id }}</span>
        <span
          v-if="activePlan"
          class="panel-mission-badge"
          :style="{ background: missionColor }"
        >
          {{ missionLabel }}
        </span>
        <span v-else class="panel-no-mission">无任务</span>
      </div>
      <div class="panel-body">
        <div class="info-row">
          <span class="info-label">经度</span>
          <span class="info-value">{{ drone.lng.toFixed(4) }}°</span>
        </div>
        <div class="info-row">
          <span class="info-label">纬度</span>
          <span class="info-value">{{ drone.lat.toFixed(4) }}°</span>
        </div>
        <div class="info-row">
          <span class="info-label">高度</span>
          <span class="info-value">{{ drone.height.toFixed(0) }} m</span>
        </div>
      </div>

      <!-- ★ 飞行计划列表 -->
      <div v-if="drone.flightPlans && drone.flightPlans.length > 0" class="panel-divider"></div>
      <div v-if="drone.flightPlans && drone.flightPlans.length > 0" class="flightplan-section">
        <div class="section-title">📋 飞行计划 ({{ drone.flightPlans.length }})</div>
        <div
          v-for="fp in drone.flightPlans"
          :key="fp.planId"
          class="fp-item"
          :class="{ 'fp-active': isPlanActive(fp) }"
        >
          <span class="fp-id">{{ fp.planId }}</span>
          <span class="fp-type-badge" :style="{ background: getMissionColor(fp.missionType) }">
            {{ getMissionLabel(fp.missionType) }}
          </span>
          <span class="fp-time">{{ formatTime(fp.startTime) }} → {{ formatTime(fp.endTime) }}</span>
          <span class="fp-desc">{{ fp.description }}</span>
        </div>
      </div>

      <!-- ★ 告警列表（test.json alarm 格式） -->
      <div v-if="drone.alerts && drone.alerts.length > 0" class="panel-divider"></div>
      <div v-if="drone.alerts && drone.alerts.length > 0" class="alert-section">
        <div class="section-title">🚨 告警 ({{ drone.alerts.length }})</div>
        <div
          v-for="(alarm, i) in drone.alerts"
          :key="i"
          class="alert-item"
          :style="{ borderLeftColor: getAlarmColor(alarm) }"
        >
          <span class="alert-icon">{{ getAlarmIcon(alarm) }}</span>
          <div class="alert-detail">
            <span class="alert-type">{{ alarm.alarmType }}</span>
            <span class="alert-level-tag" :style="{ background: getAlarmLevelColor(alarm) }">
              {{ alarm.alarmLevel }}
            </span>
            <span class="alert-reason" v-if="alarm.reason">{{ alarm.reason }}</span>
            <span class="alert-time" v-if="alarm.beginTime">{{ alarm.beginTime }}</span>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed } from 'vue'
import {
  MISSION_TYPES,
  DANGER_ALARM_TYPES,
  WARN_ALARM_TYPES,
  determineMaxAlertLevel,
  ALERT_LEVELS
} from '../utils/constants.js'

const props = defineProps({
  drone: { type: Object, required: true },
  position: { type: Object, default: () => ({ x: 0, y: 0 }) }
})

/**
 * ★ 从飞行计划中获取"当前时间窗口内的第一个计划"作为主任务标识
 *   如果飞行计划都未覆盖当前时间点，取第一个计划
 */
const activePlan = computed(() => {
  const plans = props.drone.flightPlans
  if (!plans || plans.length === 0) return null
  return plans[0]
})

const missionLabel = computed(() => {
  if (!activePlan.value) return '未知'
  const mt = MISSION_TYPES.find(m => m.value === activePlan.value.missionType)
  return mt?.label || activePlan.value.missionType || '未知'
})

const missionColor = computed(() => {
  if (!activePlan.value) return '#888'
  const mt = MISSION_TYPES.find(m => m.value === activePlan.value.missionType)
  return mt?.color || '#888'
})

function getMissionLabel(missionType) {
  const mt = MISSION_TYPES.find(m => m.value === missionType)
  return mt?.label || missionType
}

function getMissionColor(missionType) {
  const mt = MISSION_TYPES.find(m => m.value === missionType)
  return mt?.color || '#888'
}

/**
 * 判断飞行计划是否在当前时间窗口内处于激活状态
 * （_inWindow 标记由 generateFlightPlans 添加）
 */
function isPlanActive(fp) {
  return fp._inWindow === true
}

function formatTime(seconds) {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function getAlarmColor(alarm) {
  if (!alarm) return '#888'
  const level = determineMaxAlertLevel([alarm])
  if (level === ALERT_LEVELS.DANGER) return '#ff4466'
  if (level === ALERT_LEVELS.WARN) return '#ffaa00'
  return '#888'
}

function getAlarmIcon(alarm) {
  if (!alarm) return '⚠️'
  const level = determineMaxAlertLevel([alarm])
  if (level === ALERT_LEVELS.DANGER) return '🛑'
  if (level === ALERT_LEVELS.WARN) return '⚠️'
  return 'ℹ️'
}

function getAlarmLevelColor(alarm) {
  if (!alarm) return '#666'
  if (alarm.alarmLevel === '严重告警') return '#ff4466'
  if (alarm.alarmLevel === '重要告警') return '#ffaa00'
  return '#4488ff'
}
</script>

<style scoped>
.info-panel {
  position: fixed;
  transform: translate(-50%, -100%);
  background: rgba(10, 20, 40, 0.92);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(0, 200, 255, 0.3);
  border-radius: 10px;
  padding: 12px 16px;
  min-width: 240px;
  max-width: 380px;
  z-index: 1000;
  pointer-events: none;
  transition: left 0.05s, top 0.05s;
}

.panel-arrow {
  position: absolute;
  bottom: -8px; left: 50%;
  transform: translateX(-50%);
  width: 0; height: 0;
  border-left: 8px solid transparent;
  border-right: 8px solid transparent;
  border-top: 8px solid rgba(10, 20, 40, 0.92);
}

.panel-header {
  display: flex; align-items: center; gap: 8px;
  margin-bottom: 8px; padding-bottom: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.panel-drone-id {
  color: #ffdd44; font-size: 14px; font-weight: 700;
  font-family: 'Courier New', monospace;
}

.panel-mission-badge {
  font-size: 10px; color: white;
  padding: 2px 8px; border-radius: 10px; font-weight: 500;
}

.panel-no-mission {
  font-size: 10px; color: #667788;
  font-style: italic;
}

.panel-body { display: flex; flex-direction: column; gap: 4px; }

.info-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; }

.info-label { color: #667788; font-size: 12px; }
.info-value { color: #e0f0ff; font-size: 12px; font-family: 'Courier New', monospace; }

.panel-divider {
  height: 1px; background: rgba(255, 255, 255, 0.1); margin: 8px 0;
}

/* ── 飞行计划区域 ── */
.flightplan-section { display: flex; flex-direction: column; gap: 4px; }

.section-title { color: #8899aa; font-size: 11px; font-weight: 600; margin-bottom: 2px; }

.fp-item {
  display: flex; flex-wrap: wrap; align-items: center; gap: 4px;
  padding: 4px 8px; background: rgba(0, 200, 255, 0.06);
  border-left: 3px solid rgba(0, 200, 255, 0.3); border-radius: 3px;
}

.fp-item.fp-active {
  border-left-color: #00ff88;
  background: rgba(0, 255, 136, 0.06);
}

.fp-id {
  color: #aac8e0; font-size: 10px; font-family: 'Courier New', monospace;
  font-weight: 600;
}

.fp-type-badge {
  font-size: 9px; color: white;
  padding: 1px 5px; border-radius: 6px;
}

.fp-time {
  color: #667788; font-size: 9px; font-family: 'Courier New', monospace;
  margin-left: auto;
}

.fp-desc {
  color: #667788; font-size: 9px; width: 100%; margin-top: 2px;
}

/* ── 告警区域 ── */
.alert-section { display: flex; flex-direction: column; gap: 4px; }

.alert-item {
  display: flex; align-items: flex-start; gap: 6px;
  padding: 4px 8px; background: rgba(255, 0, 0, 0.06);
  border-left: 3px solid #ff4466; border-radius: 3px;
}

.alert-icon { font-size: 12px; flex-shrink: 0; margin-top: 1px; }

.alert-detail { display: flex; flex-direction: column; gap: 2px; }

.alert-type { color: #ffcccc; font-size: 12px; font-weight: 600; }

.alert-level-tag {
  display: inline-block; font-size: 9px; color: white;
  padding: 1px 5px; border-radius: 8px; width: fit-content;
}

.alert-reason { color: #999; font-size: 10px; line-height: 1.3; }
.alert-time { color: #667; font-size: 9px; font-family: 'Courier New', monospace; }
</style>
