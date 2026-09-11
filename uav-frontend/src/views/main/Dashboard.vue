<template>
  <div class="dashboard">
    <!-- 平台标题 -->
    <div class="dash-header">
      <h2>低空飞行服务管控平台</h2>
      <span class="dash-date">{{ today }}</span>
    </div>

    <!-- 核心指标 -->
    <el-row :gutter="14" class="stats-row">
      <el-col :span="4" v-for="c in cards" :key="c.label">
        <el-card shadow="hover" class="stat-card" :class="c.accent" @click="c.to && $router.push(c.to)">
          <div class="stat-value">{{ c.value }}</div>
          <div class="stat-label">{{ c.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表行：告警级别 / 告警类型 TOP5 / 飞行计划状态 -->
    <el-row :gutter="14" class="mid-row">
      <el-col :span="8">
        <el-card shadow="never" class="panel">
          <template #header><b>🚦 告警级别分布</b></template>
          <div ref="levelChartEl" class="chart" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="panel">
          <template #header><b>⚠️ 告警类型 TOP5</b></template>
          <div ref="typeChartEl" class="chart" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="panel">
          <template #header><b>📋 飞行计划状态</b></template>
          <div ref="planChartEl" class="chart" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势 + 快捷入口 -->
    <el-row :gutter="14" class="bottom-row">
      <el-col :span="16">
        <el-card shadow="never" class="panel">
          <template #header><b>📈 近 30 分钟告警趋势</b></template>
          <div ref="trendChartEl" class="chart trend" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="panel">
          <template #header><b>⚡ 快捷操作</b></template>
          <el-button type="primary" style="width:100%;margin-bottom:8px" @click="$router.push('/flight-monitor')">🛰️ 飞行监控大屏</el-button>
          <el-button type="success" style="width:100%;margin-bottom:8px" @click="$router.push('/flight-plan')">📋 飞行计划审批</el-button>
          <el-button type="warning" style="width:100%;margin-bottom:8px" @click="$router.push('/alarm')">🔔 告警中心</el-button>
          <el-button type="info" style="width:100%" @click="$router.push('/airspace')">🗺️ 空域配置</el-button>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最新告警 -->
    <el-row :gutter="14" class="bottom-row">
      <el-col :span="24">
        <el-card shadow="never" class="panel">
          <template #header><b>🔴 最新告警</b></template>
          <el-table :data="recentAlarms" border size="small" max-height="240">
            <el-table-column prop="createTime" label="时间" width="150">
              <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column prop="alarmLevel" label="级别" width="100">
              <template #default="{ row }">
                <el-tag :type="row.alarmLevel === 'CRITICAL' ? 'danger' : 'warning'" size="small">{{ row.alarmLevel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="droneSn" label="无人机SN" width="140" />
            <el-table-column prop="alarmType" label="类型" min-width="150" />
            <el-table-column prop="message" label="内容" min-width="160" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { airspaceApi } from '@/api/airspace'
import { registryApi } from '@/api/registry'
import { pilotApi } from '@/api/pilot'
import { flightPlanApi } from '@/api/flight-plan'
import { alarmApi } from '@/api/alarm'
import { useDict } from '@/composables/useDict'
import { fmtDateTime as fmtTime } from '@/utils/format'

const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })

// 配色统一：蓝主色 / 红危急 / 橙警告 / 绿正常
const C = { blue: '#409eff', red: '#f56c6c', orange: '#e6a23c', green: '#67c23a', gray: '#909399' }
const LEVEL_COLOR: Record<string, string> = {
  EMERGENCY: C.red, CRITICAL: C.red,
  MAJOR: C.orange, SERIOUS: C.orange, WARNING: C.orange,
  GENERAL: C.blue, MINOR: C.gray,
}
const STATUS_COLOR: Record<string, string> = {
  APPROVED: C.green, REJECTED: C.red,
  PENDING_LEVEL1: C.blue, PENDING_LEVEL2: C.orange, PENDING_LEVEL3: C.orange,
  DRAFT: C.gray,
}

const regDrones = ref(0)
const pilots = ref(0)
const airspaces = ref(0)
const plansTotal = ref(0)
const alarmTotal = ref(0)
const alarmByLevel = ref<Record<string, number>>({})
const alarmByType = ref<Record<string, number>>({})
const planByStatus = ref<Record<string, number>>({})
const recentAlarms = ref<any[]>([])

const cards = computed(() => [
  { label: '登记无人机', value: regDrones.value, accent: 'blue', to: '/registry' },
  { label: '注册飞手', value: pilots.value, accent: 'green', to: '/pilot' },
  { label: '管理空域', value: airspaces.value, accent: 'cyan', to: '/airspace' },
  { label: '飞行计划', value: plansTotal.value, accent: 'blue', to: '/flight-plan' },
  { label: '累计告警', value: alarmTotal.value, accent: 'orange', to: '/alarm' },
  { label: '危急告警', value: alarmByLevel.value.CRITICAL || 0, accent: 'red', to: '/alarm' },
])

// ── ECharts ──
const levelChartEl = ref<HTMLDivElement>()
const typeChartEl = ref<HTMLDivElement>()
const planChartEl = ref<HTMLDivElement>()
const trendChartEl = ref<HTMLDivElement>()
let levelChart: echarts.ECharts | null = null
let typeChart: echarts.ECharts | null = null
let planChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null

const { label: levelLabel } = useDict('alarm_level')
function statusLabel(s: string) {
  const map: Record<string, string> = { DRAFT: '草稿', PENDING_LEVEL1: '一级审批', PENDING_LEVEL2: '二级审批', PENDING_LEVEL3: '三级审批', APPROVED: '已批准', REJECTED: '已拒绝' }
  return map[s] || s
}

function emptyOption(text: string): echarts.EChartsCoreOption {
  return { graphic: [{ type: 'text', left: 'center', top: 'middle', style: { text, fill: '#5f7189', fontSize: 13 } }] }
}

function donutOption(data: { name: string; value: number; key: string }[], colorMap: Record<string, string>, defaultColor: string): echarts.EChartsCoreOption {
  return {
    tooltip: { trigger: 'item', formatter: '{b}：{c}（{d}%）' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 12, color: '#93a4bd' } },
    series: [{
      type: 'pie', radius: ['42%', '68%'], center: ['50%', '44%'],
      itemStyle: { borderColor: '#101a2c', borderWidth: 2 },
      label: { show: true, formatter: '{b} {c}', fontSize: 11, color: '#c6d2e2' },
      labelLine: { length: 8, length2: 6 },
      data: data.map(d => ({ ...d, itemStyle: { color: colorMap[d.key] || defaultColor } })),
    }],
  }
}

function renderLevelChart() {
  const rows = Object.entries(alarmByLevel.value).map(([k, v]) => ({ name: levelLabel(k), value: v, key: k }))
  if (!rows.length) { levelChart?.setOption(emptyOption('暂无告警数据'), true); return }
  levelChart?.setOption(donutOption(rows, LEVEL_COLOR, C.blue), true)
}

function renderTypeChart() {
  const top = Object.entries(alarmByType.value).sort((a, b) => b[1] - a[1]).slice(0, 5)
  if (!top.length) { typeChart?.setOption(emptyOption('暂无告警类型'), true); return }
  typeChart?.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 40, top: 8, bottom: 0, containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#1f2e47' } } },
    yAxis: {
      type: 'category', inverse: true,
      data: top.map(t => t[0]),
      axisLabel: { color: '#c6d2e2', fontSize: 11 },
      axisLine: { show: false }, axisTick: { show: false },
    },
    series: [{
      type: 'bar', barWidth: 14, data: top.map(t => t[1]),
      itemStyle: { color: C.orange, borderRadius: [0, 7, 7, 0] },
      label: { show: true, position: 'right', color: '#e6edf8', fontSize: 11 },
    }],
  }, true)
}

function renderPlanChart() {
  const rows = Object.entries(planByStatus.value).map(([k, v]) => ({ name: statusLabel(k), value: v, key: k }))
  if (!rows.length) { planChart?.setOption(emptyOption('暂无飞行计划'), true); return }
  planChart?.setOption(donutOption(rows, STATUS_COLOR, C.blue), true)
}

// 近 30 分钟告警按分钟分桶
function renderTrendChart() {
  const now = Date.now()
  const WINDOW_MIN = 30
  const buckets: number[] = new Array(WINDOW_MIN).fill(0)
  const labels: string[] = []
  for (let i = WINDOW_MIN - 1; i >= 0; i--) {
    const t = new Date(now - i * 60000)
    labels.push(`${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')}`)
  }
  let inWindow = 0
  for (const a of recentAllAlarms) {
    if (!a.createTime) continue
    const ts = new Date(a.createTime).getTime()
    if (isNaN(ts) || ts < now - WINDOW_MIN * 60000 || ts > now) continue
    const idx = WINDOW_MIN - 1 - Math.floor((now - ts) / 60000)
    if (idx >= 0 && idx < WINDOW_MIN) { buckets[idx]++; inWindow++ }
  }
  if (!inWindow) {
    trendChart?.setOption(emptyOption('近 30 分钟无告警'), true)
    return
  }
  trendChart?.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 8, right: 16, top: 20, bottom: 0, containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: labels, axisLabel: { color: '#93a4bd', fontSize: 11, interval: 4 }, axisLine: { lineStyle: { color: '#24344f' } } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#1f2e47' } }, axisLabel: { color: '#93a4bd' } },
    series: [{
      name: '告警数', type: 'line', smooth: true, data: buckets,
      lineStyle: { color: C.red, width: 2 }, itemStyle: { color: C.red },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(245,108,108,0.35)' }, { offset: 1, color: 'rgba(245,108,108,0.02)' }]) },
    }],
  }, true)
}

function renderCharts() {
  renderLevelChart(); renderTypeChart(); renderPlanChart(); renderTrendChart()
}

function initCharts() {
  if (levelChartEl.value) levelChart = echarts.init(levelChartEl.value)
  if (typeChartEl.value) typeChart = echarts.init(typeChartEl.value)
  if (planChartEl.value) planChart = echarts.init(planChartEl.value)
  if (trendChartEl.value) trendChart = echarts.init(trendChartEl.value)
  window.addEventListener('resize', onResize)
}
function onResize() { [levelChart, typeChart, planChart, trendChart].forEach(c => c?.resize()) }


// 趋势图用全量告警列表（表格只展示最新 8 条）
let recentAllAlarms: any[] = []

let timer: any = null
async function loadAll() {
  try {
    const [drones, owners, pilotsRes, airspacesRes, plans, stats, alarms] = await Promise.all([
      registryApi.listDrones(), registryApi.listOwners(), pilotApi.list(),
      airspaceApi.list(), flightPlanApi.list(), alarmApi.stats(), alarmApi.list(),
    ])
    regDrones.value = ((drones as any).data || []).length
    pilots.value = ((pilotsRes as any).data || []).filter((p: any) => p.status === 'ACTIVE').length
    airspaces.value = ((airspacesRes as any).data || []).length
    const planRows = (plans as any).data || []
    plansTotal.value = planRows.length
    planByStatus.value = planRows.reduce((m: any, p: any) => { m[p.planStatus] = (m[p.planStatus] || 0) + 1; return m }, {})
    const st = (stats as any).data || {}
    alarmTotal.value = st.total || 0
    alarmByLevel.value = st.byLevel || {}
    alarmByType.value = st.byType || {}
    const rows = (alarms as any).data || []
    recentAllAlarms = rows
    recentAlarms.value = rows.slice(0, 8)
    renderCharts()
  } catch (e) { console.warn('仪表盘数据加载失败', e) }
}

onMounted(async () => {
  await nextTick()
  initCharts()
  loadAll()
  timer = setInterval(loadAll, 15000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
  window.removeEventListener('resize', onResize)
  ;[levelChart, typeChart, planChart, trendChart].forEach(c => c?.dispose())
  levelChart = typeChart = planChart = trendChart = null
})
</script>

<style scoped>
.dashboard { padding: 20px; }
.dash-header { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 16px; }
.dash-header h2 { margin: 0; color: var(--el-text-color-primary); }
.dash-date { color: #909399; font-size: 13px; }
.stats-row { margin-bottom: 14px; }
.stat-card { text-align: center; cursor: pointer; border-top: 3px solid #409eff; }
.stat-card.blue { border-top-color: #409eff; }
.stat-card.green { border-top-color: #67c23a; }
.stat-card.cyan { border-top-color: #36cfc9; }
.stat-card.orange { border-top-color: #e6a23c; }
.stat-card.red { border-top-color: #f56c6c; }
.stat-value { font-size: 32px; font-weight: 700; color: var(--el-text-color-primary); }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.panel :deep(.el-card__header) { padding: 10px 16px; background: var(--el-fill-color-lighter); }
.chart { height: 265px; width: 100%; }
.chart.trend { height: 225px; }
.mid-row { margin-bottom: 14px; }
.bottom-row { margin-bottom: 14px; }
</style>
