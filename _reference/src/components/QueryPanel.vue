<template>
  <Teleport to="body">
    <div
      class="query-toggle-btn"
      :class="{ 'is-open': isOpen }"
      @click="togglePanel"
      title="查询面板"
    >
      <span class="toggle-icon">🔍</span>
    </div>

    <transition name="slide-right">
      <div v-if="isOpen" class="query-panel">
        <div class="panel-header">
          <span class="panel-title">🔍 数据查询</span>
          <button class="close-btn" @click="isOpen = false">✕</button>
        </div>

        <div class="tab-bar">
          <button class="tab-btn" :class="{ active: activeTab === 'drone' }" @click="activeTab = 'drone'">
            🛸 无人机检索
          </button>
          <button class="tab-btn" :class="{ active: activeTab === 'alarm' }" @click="activeTab = 'alarm'">
            🚨 告警检索
          </button>
        </div>

        <!-- ───── 无人机检索 ───── -->
        <div v-if="activeTab === 'drone'" class="tab-content">
          <div class="filter-row">
            <label class="filter-label">无人机 ID</label>
            <input
              v-model="droneFilter.keyword"
              class="filter-input"
              placeholder="输入 ID 关键词，如 UAV-0001"
            />
          </div>

          <div class="filter-row">
            <label class="filter-label">任务类型</label>
            <div class="chip-group">
              <button
                v-for="mt in MISSION_TYPES" :key="mt.value"
                class="chip" :class="{ active: droneFilter.missionTypes.includes(mt.value) }"
                @click="toggleMissionType(mt.value)"
                :style="droneFilter.missionTypes.includes(mt.value) ? { background: mt.color, borderColor: mt.color } : {}"
              >{{ mt.label }}</button>
            </div>
          </div>

          <div class="filter-row">
            <label class="filter-label">告警状态</label>
            <div class="chip-group">
              <button
                v-for="opt in alarmStatusOptions" :key="opt.value"
                class="chip" :class="{ active: droneFilter.alarmStatus === opt.value }"
                @click="droneFilter.alarmStatus = opt.value"
              >{{ opt.label }}</button>
            </div>
          </div>

          <div class="action-row">
            <button class="action-btn query-btn" @click="applyDroneFilter" :disabled="droneSearching">
              {{ droneSearching ? '搜索中...' : '🔍 查询' }}
            </button>
            <button class="action-btn reset-btn" @click="resetDroneFilter">
              🔄 重置
            </button>
          </div>

          <div v-if="droneResults.length > 0" class="result-section">
            <div class="result-header">共找到 {{ droneResults.length }} 架无人机</div>
            <div class="result-list">
              <div
                v-for="d in droneResults" :key="d.droneId"
                class="result-item"
                @click="emit('select-drone', d.droneId)"
              >
                <div class="result-main">
                  <span class="result-id">{{ d.droneId }}</span>
                  <span v-for="mt in d.missionTypes" :key="mt" class="mission-tag" :style="{ background: getMissionColor(mt) }">
                    {{ getMissionLabel(mt) }}
                  </span>
                </div>
                <div class="result-sub">
                  <span v-if="d.alarmCount > 0" class="alarm-indicator">🚨 {{ d.alarmCount }} 条告警</span>
                  <span v-else class="no-alarm">✅ 无告警</span>
                  <button class="jump-btn" @click.stop="jumpToDrone(d)">📍 定位</button>
                </div>
              </div>
            </div>
          </div>
          <div v-else-if="droneSearched" class="empty-result">未找到匹配的无人机</div>
        </div>

        <!-- ───── 告警检索 ───── -->
        <div v-if="activeTab === 'alarm'" class="tab-content">
          <div class="filter-row">
            <label class="filter-label">告警类型</label>
            <div class="chip-group scrollable">
              <button
                v-for="at in ALL_ALARM_TYPES" :key="at"
                class="chip" :class="{ active: alarmFilter.alarmTypes.includes(at) }"
                @click="toggleAlarmType(at)"
              >{{ at }}</button>
            </div>
          </div>

          <div class="filter-row">
            <label class="filter-label">告警等级</label>
            <div class="chip-group">
              <button
                v-for="lv in ['严重告警', '一般告警']" :key="lv"
                class="chip" :class="{ active: alarmFilter.alarmLevels.includes(lv) }"
                @click="toggleAlarmLevel(lv)"
              >{{ lv }}</button>
            </div>
          </div>

          <div class="filter-row">
            <label class="filter-label">关联无人机 ID（可选）</label>
            <input
              v-model="alarmFilter.droneId"
              class="filter-input"
              placeholder="如 UAV-0042"
            />
          </div>

          <div class="action-row">
            <button class="action-btn query-btn" @click="applyAlarmFilter" :disabled="alarmSearching">
              {{ alarmSearching ? '搜索中...' : '🔍 查询' }}
            </button>
            <button class="action-btn reset-btn" @click="resetAlarmFilter">
              🔄 重置
            </button>
          </div>

          <div v-if="filteredAlarms.length > 0" class="result-section">
            <div class="result-header">共找到 {{ filteredAlarms.length }} 条告警（涉及 {{ alarmDroneIds.length }} 架无人机）</div>
            <div class="result-list">
              <div
                v-for="a in paginatedAlarms" :key="a.alarmId"
                class="result-item alarm-item"
                :style="{ borderLeftColor: getAlarmLevelColor(a) }"
                @click="emit('select-drone', a.droneId)"
              >
                <div class="result-main">
                  <span class="result-id">{{ a.droneId }}</span>
                  <span class="alarm-type-tag" :style="{ background: getAlarmLevelColor(a) }">{{ a.alarmType }}</span>
                </div>
                <div class="result-sub">
                  <span class="alarm-level-text">{{ a.alarmLevel }}</span>
                  <span class="alarm-time-range">{{ formatTime(a.startTime) }} → {{ formatTime(a.endTime) }}</span>
                  <button class="jump-btn" @click.stop="jumpToAlarm(a)">📍 定位</button>
                </div>
                <div class="alarm-reason" v-if="a.reason">{{ a.reason }}</div>
              </div>
            </div>
            <div v-if="filteredAlarms.length > ALARM_PAGE_SIZE" class="alarm-pagination">
              <button :disabled="alarmPage === 0" @click="alarmPage--">◀</button>
              <span>{{ alarmPage + 1 }} / {{ Math.ceil(filteredAlarms.length / ALARM_PAGE_SIZE) }}</span>
              <button :disabled="(alarmPage + 1) * ALARM_PAGE_SIZE >= filteredAlarms.length" @click="alarmPage++">▶</button>
            </div>
          </div>
          <div v-else-if="alarmSearched" class="empty-result">未找到匹配的告警</div>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { MISSION_TYPES, ALL_DISPLAY_ALARM_TYPES, DANGER_ALARM_TYPES } from '../utils/constants.js'
import { fetchInit } from '../utils/apiService.js'

const props = defineProps({
  timeStartMs: { type: Number, required: true },
  timeEndMs: { type: Number, required: true }
})

const emit = defineEmits(['select-drone', 'fly-to-drone', 'apply-filter'])

const isOpen = ref(false)
const activeTab = ref('drone')

function togglePanel() { isOpen.value = !isOpen.value }

// ── 无人机检索 ──

const droneFilter = reactive({ keyword: '', missionTypes: [], alarmStatus: 'all' })
const alarmStatusOptions = [
  { label: '全部', value: 'all' },
  { label: '有告警', value: 'has' },
  { label: '无告警', value: 'none' }
]

const droneResults = ref([])
const droneSearching = ref(false)
const droneSearched = ref(false)

function buildDroneFilters() {
  const f = {}
  if (droneFilter.keyword) f.keyword = droneFilter.keyword
  if (droneFilter.missionTypes.length > 0) f.missionTypes = [...droneFilter.missionTypes]
  if (droneFilter.alarmStatus === 'has') f.hasAlarm = true
  else if (droneFilter.alarmStatus === 'none') f.hasAlarm = false
  return Object.keys(f).length > 0 ? f : null
}

/** ★ 查询：一次 fetchInit，同时更新面板结果 + 重建地图 */
async function applyDroneFilter() {
  const filters = buildDroneFilters()
  droneSearching.value = true
  droneSearched.value = false

  try {
    const initData = await fetchInit({
      timeStart: props.timeStartMs,
      timeEnd: props.timeEndMs,
      filters
    })
    if (!initData) return

    droneResults.value = initData.drones
    droneSearched.value = true
    emit('apply-filter', initData)
  } finally {
    droneSearching.value = false
  }
}

/** ★ 重置：全量 init */
async function resetDroneFilter() {
  droneFilter.keyword = ''
  droneFilter.missionTypes = []
  droneFilter.alarmStatus = 'all'
  droneResults.value = []
  droneSearched.value = false

  const initData = await fetchInit({
    timeStart: props.timeStartMs,
    timeEnd: props.timeEndMs
  })
  if (initData) emit('apply-filter', initData)
}

function toggleMissionType(val) {
  const idx = droneFilter.missionTypes.indexOf(val)
  if (idx >= 0) droneFilter.missionTypes.splice(idx, 1)
  else droneFilter.missionTypes.push(val)
}

function jumpToDrone(d) {
  emit('select-drone', d.droneId)
  emit('fly-to-drone', d.droneId)
}

// ── 告警检索 ──

const ALL_ALARM_TYPES = ALL_DISPLAY_ALARM_TYPES
const ALARM_PAGE_SIZE = 50

const alarmFilter = reactive({ alarmTypes: [], alarmLevels: [], droneId: '' })
const alarmPage = ref(0)

const alarmResults = ref([])
const alarmSearching = ref(false)
const alarmSearched = ref(false)

const filteredAlarms = computed(() => alarmResults.value)
const paginatedAlarms = computed(() => {
  const start = alarmPage.value * ALARM_PAGE_SIZE
  return filteredAlarms.value.slice(start, start + ALARM_PAGE_SIZE)
})
const alarmDroneIds = computed(() => [...new Set(filteredAlarms.value.map(a => a.droneId))])

function buildAlarmFilters() {
  const f = {}
  if (alarmFilter.alarmTypes.length > 0) f.alarmTypes = [...alarmFilter.alarmTypes]
  if (alarmFilter.alarmLevels.length > 0) f.alarmLevels = [...alarmFilter.alarmLevels]
  if (alarmFilter.droneId) f.keyword = alarmFilter.droneId
  return Object.keys(f).length > 0 ? f : null
}

/** ★ 告警查询：一次 fetchInit，同时更新告警结果列表 + 筛选地图 */
async function applyAlarmFilter() {
  const filters = buildAlarmFilters()
  alarmSearching.value = true
  alarmSearched.value = false
  alarmPage.value = 0

  try {
    const initData = await fetchInit({
      timeStart: props.timeStartMs,
      timeEnd: props.timeEndMs,
      filters
    })
    if (!initData) return

    alarmResults.value = initData.alarms || []
    alarmSearched.value = true
    emit('apply-filter', initData)
  } finally {
    alarmSearching.value = false
  }
}

/** ★ 告警重置：全量 init */
async function resetAlarmFilter() {
  alarmFilter.alarmTypes = []
  alarmFilter.alarmLevels = []
  alarmFilter.droneId = ''
  alarmResults.value = []
  alarmSearched.value = false

  const initData = await fetchInit({
    timeStart: props.timeStartMs,
    timeEnd: props.timeEndMs
  })
  if (initData) emit('apply-filter', initData)
}

watch(() => [alarmFilter.alarmTypes, alarmFilter.alarmLevels, alarmFilter.droneId], () => {
  alarmPage.value = 0
}, { deep: true })

function toggleAlarmType(val) {
  const idx = alarmFilter.alarmTypes.indexOf(val)
  if (idx >= 0) alarmFilter.alarmTypes.splice(idx, 1)
  else alarmFilter.alarmTypes.push(val)
}

function toggleAlarmLevel(val) {
  const idx = alarmFilter.alarmLevels.indexOf(val)
  if (idx >= 0) alarmFilter.alarmLevels.splice(idx, 1)
  else alarmFilter.alarmLevels.push(val)
}

function jumpToAlarm(a) {
  emit('select-drone', a.droneId)
  emit('fly-to-drone', a.droneId)
}

// ── 工具 ──

function getMissionLabel(val) {
  return MISSION_TYPES.find(m => m.value === val)?.label || val
}
function getMissionColor(val) {
  return MISSION_TYPES.find(m => m.value === val)?.color || '#888'
}
function getAlarmLevelColor(alarm) {
  return DANGER_ALARM_TYPES.has(alarm.alarmType) ? '#ff4466' : '#ffaa00'
}
function formatTime(seconds) {
  const m = Math.floor(seconds / 60), s = seconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}
</script>

<style scoped>
.query-toggle-btn {
  position: fixed; right: 4px; top: 50%; transform: translateY(-50%);
  width: 36px; height: 80px;
  background: rgba(10, 20, 40, 0.9);
  border: 1px solid rgba(0, 200, 255, 0.3); border-right: none;
  border-radius: 10px 0 0 10px;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; z-index: 500;
  transition: right 0.3s ease, background 0.2s; user-select: none;
}
.query-toggle-btn:hover { background: rgba(0, 200, 255, 0.15); }
.query-toggle-btn.is-open { right: 384px; }
.toggle-icon { font-size: 18px; }

.query-panel {
  position: fixed; right: 0; top: 0; bottom: 0; width: 380px; z-index: 600;
  background: rgba(10, 20, 40, 0.95); backdrop-filter: blur(20px);
  border-left: 1px solid rgba(0, 200, 255, 0.25);
  display: flex; flex-direction: column;
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.4);
}
.slide-right-enter-active, .slide-right-leave-active { transition: transform 0.3s ease; }
.slide-right-enter-from, .slide-right-leave-to { transform: translateX(100%); }

.panel-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px; border-bottom: 1px solid rgba(255,255,255,0.1);
}
.panel-title { color: #e0f0ff; font-size: 15px; font-weight: 600; }
.close-btn {
  background: none; border: none; color: #667788;
  font-size: 18px; cursor: pointer; padding: 4px 8px; border-radius: 4px;
}
.close-btn:hover { color: #ff4466; }

.tab-bar { display: flex; border-bottom: 1px solid rgba(255,255,255,0.08); }
.tab-btn {
  flex: 1; background: none; border: none;
  padding: 12px 0; font-size: 13px; color: #667788;
  cursor: pointer; border-bottom: 2px solid transparent;
}
.tab-btn.active { color: #00c8ff; border-bottom-color: #00c8ff; }
.tab-btn:hover { color: #aac8e0; }

.tab-content {
  flex: 1; overflow-y: auto; padding: 16px 20px;
  display: flex; flex-direction: column; gap: 14px;
}
.filter-row { display: flex; flex-direction: column; gap: 6px; }
.filter-label { color: #8899aa; font-size: 11px; font-weight: 600; }
.filter-input {
  background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.12);
  border-radius: 6px; padding: 8px 12px; color: #e0f0ff; font-size: 12px; outline: none;
}
.filter-input:focus { border-color: rgba(0,200,255,0.5); }

.chip-group { display: flex; flex-wrap: wrap; gap: 6px; }
.chip-group.scrollable { max-height: 120px; overflow-y: auto; }
.chip {
  background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.12);
  border-radius: 14px; padding: 4px 12px; color: #aac8e0; font-size: 11px;
  cursor: pointer; transition: all 0.2s; white-space: nowrap;
}
.chip:hover { border-color: rgba(0,200,255,0.3); }
.chip.active { color: white; border-color: #00c8ff; background: rgba(0,200,255,0.2); }

.action-row { display: flex; gap: 10px; }
.action-btn {
  flex: 1; padding: 10px 0; border-radius: 8px; font-size: 13px; font-weight: 600;
  cursor: pointer; border: 1px solid transparent; transition: all 0.2s;
}
.query-btn {
  background: rgba(0, 200, 255, 0.15); border-color: rgba(0, 200, 255, 0.35);
  color: #00c8ff;
}
.query-btn:hover:not(:disabled) { background: rgba(0, 200, 255, 0.3); }
.query-btn:disabled { opacity: 0.35; cursor: not-allowed; }
.reset-btn {
  background: rgba(255, 255, 255, 0.06); border-color: rgba(255, 255, 255, 0.15);
  color: #8899aa;
}
.reset-btn:hover { background: rgba(255, 255, 255, 0.12); color: #aac8e0; }

.result-section { flex: 1; display: flex; flex-direction: column; gap: 6px; min-height: 0; }
.result-header { color: #8899aa; font-size: 11px; font-weight: 600; }
.result-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 4px; }

.result-item {
  background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.08);
  border-left: 3px solid rgba(0,200,255,0.3); border-radius: 6px;
  padding: 10px 12px; cursor: pointer;
}
.result-item:hover { background: rgba(0,200,255,0.08); }
.result-item.alarm-item { border-left-width: 3px; }

.result-main { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-bottom: 4px; }
.result-id { color: #ffdd44; font-size: 12px; font-weight: 700; font-family: 'Courier New', monospace; }
.mission-tag { font-size: 9px; color: white; padding: 1px 6px; border-radius: 8px; }
.alarm-type-tag { font-size: 9px; color: white; padding: 2px 8px; border-radius: 8px; }

.result-sub { display: flex; align-items: center; gap: 8px; }
.alarm-indicator { color: #ff8888; font-size: 10px; }
.no-alarm { color: #88cc88; font-size: 10px; }
.alarm-level-text { color: #aac8e0; font-size: 10px; }
.alarm-time-range { color: #667788; font-size: 10px; font-family: 'Courier New', monospace; }

.jump-btn {
  margin-left: auto; background: rgba(0,200,255,0.15);
  border: 1px solid rgba(0,200,255,0.3); border-radius: 10px;
  padding: 2px 10px; color: #00c8ff; font-size: 10px; cursor: pointer;
}
.jump-btn:hover { background: rgba(0,200,255,0.3); }

.alarm-reason { color: #667788; font-size: 10px; margin-top: 4px; }
.empty-result { text-align: center; color: #556; padding: 24px; font-size: 13px; }

.alarm-pagination {
  display: flex; align-items: center; justify-content: center; gap: 12px;
  padding: 8px 0; color: #8899aa; font-size: 11px;
}
.alarm-pagination button {
  background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.12);
  border-radius: 4px; color: #aac8e0; padding: 2px 10px; cursor: pointer;
}
.alarm-pagination button:disabled { opacity: 0.3; cursor: not-allowed; }
.alarm-pagination button:hover:not(:disabled) { background: rgba(0,200,255,0.15); }
</style>
