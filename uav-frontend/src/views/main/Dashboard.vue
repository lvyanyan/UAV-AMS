<template>
  <div class="dashboard">
    <!-- 平台标题 -->
    <div class="dash-header">
      <h2>{{ $t('app.title') }}</h2>
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
          <template #header><b class="panel-title"><el-icon><Warning /></el-icon> {{ $t('dashboard.levelDist') }}</b></template>
          <div ref="levelChartEl" class="chart" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="panel">
          <template #header><b class="panel-title"><el-icon><Histogram /></el-icon> {{ $t('dashboard.typeTop5') }}</b></template>
          <div ref="typeChartEl" class="chart" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="panel">
          <template #header><b class="panel-title"><el-icon><Document /></el-icon> {{ $t('dashboard.planStatus') }}</b></template>
          <div ref="planChartEl" class="chart" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势 + 快捷入口 -->
    <el-row :gutter="14" class="bottom-row">
      <el-col :span="16">
        <el-card shadow="never" class="panel">
          <template #header><b class="panel-title"><el-icon><TrendCharts /></el-icon> {{ $t('dashboard.trend30') }}</b></template>
          <div ref="trendChartEl" class="chart trend" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="panel">
          <template #header><b class="panel-title"><el-icon><Lightning /></el-icon> {{ $t('dashboard.quickActions') }}</b></template>
          <div class="quick-actions">
            <el-button type="primary" @click="$router.push('/flight-monitor')"><img class="qa-ic" src="@/assets/icons/ic-radar.png" alt="" />&nbsp;{{ $t('dashboard.qa.monitor') }}</el-button>
            <el-button type="success" @click="$router.push('/flight-plan')"><img class="qa-ic" src="@/assets/icons/ic-plan.png" alt="" />&nbsp;{{ $t('dashboard.qa.plan') }}</el-button>
            <el-button type="warning" @click="$router.push('/alarm')"><img class="qa-ic" src="@/assets/icons/ic-alarm.png" alt="" />&nbsp;{{ $t('dashboard.qa.alarm') }}</el-button>
            <el-button type="info" @click="$router.push('/airspace')"><img class="qa-ic" src="@/assets/icons/ic-airspace.png" alt="" />&nbsp;{{ $t('dashboard.qa.airspace') }}</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最新告警 -->
    <el-row :gutter="14" class="bottom-row">
      <el-col :span="24">
        <el-card shadow="never" class="panel">
          <template #header><b class="panel-title"><el-icon><Bell /></el-icon> {{ $t('dashboard.latestAlarms') }}</b></template>
          <el-table :data="recentAlarms" border size="small" max-height="240">
            <el-table-column prop="createTime" :label="$t('dashboard.col.time')" width="150">
              <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column prop="alarmLevel" :label="$t('dashboard.col.level')" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.alarmType === 'NO_FLIGHT_PLAN'" class="hf-tag" size="small">{{ levelLabel(row.alarmLevel) }}</el-tag>
                <el-tag v-else :type="row.alarmLevel === 'CRITICAL' ? 'danger' : 'warning'" size="small">{{ levelLabel(row.alarmLevel) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="droneSn" :label="$t('dashboard.col.sn')" width="140" />
            <el-table-column prop="alarmType" :label="$t('dashboard.col.type')" min-width="150">
              <template #default="{ row }">{{ typeLabel(row.alarmType) }}</template>
            </el-table-column>
            <el-table-column prop="message" :label="$t('dashboard.col.content')" min-width="160" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import * as echarts from 'echarts'
import { airspaceApi } from '@/api/airspace'
import { registryApi } from '@/api/registry'
import { pilotApi } from '@/api/pilot'
import { flightPlanApi } from '@/api/flight-plan'
import { alarmApi } from '@/api/alarm'
import { useDict } from '@/composables/useDict'
import { fmtDateTime as fmtTime } from '@/utils/format'
import { locale } from '@/locales'

const { t } = useI18n()

const today = computed(() => new Date().toLocaleDateString(locale.value === 'en' ? 'en-US' : 'zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }))

// 配色统一：品牌蓝 / 青高亮 / 红危急 / 橙警告 / 绿正常
const C = { blue: '#2f81f7', cyan: '#22d3ee', red: '#f0524f', orange: '#f5a623', green: '#34d399', gray: '#8fa1bc' }
const LEVEL_COLOR: Record<string, string> = {
  EMERGENCY: C.red, CRITICAL: C.red,
  MAJOR: C.orange, SERIOUS: C.orange, WARNING: C.orange,
  GENERAL: C.blue, MINOR: C.gray,
}
const STATUS_COLOR: Record<string, string> = {
  APPROVED: C.green, RELEASED: C.cyan, IN_FLIGHT: C.blue,
  COMPLETED: C.gray, EXPIRED: C.gray, CANCELLED: C.gray, REJECTED: C.red,
  PENDING_LEVEL1: C.blue, PENDING_LEVEL2: C.orange, PENDING_LEVEL3: C.orange,
  DRAFT: C.gray, MILITARY_CANCELLED: C.gray,
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
  { label: t('dashboard.card.drones'), value: regDrones.value, accent: 'blue', to: '/registry' },
  { label: t('dashboard.card.pilots'), value: pilots.value, accent: 'green', to: '/pilot' },
  { label: t('dashboard.card.airspaces'), value: airspaces.value, accent: 'cyan', to: '/airspace' },
  { label: t('dashboard.card.plans'), value: plansTotal.value, accent: 'blue', to: '/flight-plan' },
  { label: t('dashboard.card.alarms'), value: alarmTotal.value, accent: 'orange', to: '/alarm' },
  { label: t('dashboard.card.critical'), value: alarmByLevel.value.CRITICAL || 0, accent: 'red', to: '/alarm' },
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
const { label: typeLabel } = useDict('alarm_type')
const { label: statusLabel } = useDict('plan_status')

function emptyOption(text: string): echarts.EChartsCoreOption {
  return { graphic: [{ type: 'text', left: 'center', top: 'middle', style: { text, fill: '#5f7189', fontSize: 13 } }] }
}

function donutOption(data: { name: string; value: number; key: string }[], colorMap: Record<string, string>, defaultColor: string): echarts.EChartsCoreOption {
  return {
    tooltip: { trigger: 'item', formatter: (p: any) => t('dashboard.donutTip', { name: p.name, value: p.value, pct: p.percent }) },
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 12, color: '#8fa1bc' } },
    series: [{
      type: 'pie', radius: ['46%', '70%'], center: ['50%', '44%'],
      itemStyle: { borderColor: '#0b1322', borderWidth: 2 },
      label: { show: true, formatter: '{b} {c}', fontSize: 11, color: '#c8d5e6' },
      labelLine: { length: 8, length2: 6, lineStyle: { color: '#3a4f74' } },
      data: data.map(d => ({ ...d, itemStyle: { color: colorMap[d.key] || defaultColor } })),
    }],
  }
}

function renderLevelChart() {
  const rows = Object.entries(alarmByLevel.value).map(([k, v]) => ({ name: levelLabel(k), value: v, key: k }))
  if (!rows.length) { levelChart?.setOption(emptyOption(t('dashboard.noAlarmData')), true); return }
  levelChart?.setOption(donutOption(rows, LEVEL_COLOR, C.blue), true)
}

function renderTypeChart() {
  const top = Object.entries(alarmByType.value).sort((a, b) => b[1] - a[1]).slice(0, 5)
    .map(([code, count]) => ({ code, label: typeLabel(code), count }))
  if (!top.length) { typeChart?.setOption(emptyOption(t('dashboard.noAlarmType')), true); return }
  typeChart?.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 40, top: 8, bottom: 0, containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#192742' } } },
    yAxis: {
      type: 'category', inverse: true,
      data: top.map(t => t.label),
      axisLabel: { color: '#c8d5e6', fontSize: 11 },
      axisLine: { show: false }, axisTick: { show: false },
    },
    series: [{
      type: 'bar', barWidth: 14, data: top.map(t => t.count),
      itemStyle: {
        borderRadius: [0, 7, 7, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#2f81f7' }, { offset: 1, color: '#22d3ee' },
        ]),
      },
      label: { show: true, position: 'right', color: '#e8f0fb', fontSize: 11 },
    }],
  }, true)
}

function renderPlanChart() {
  const rows = Object.entries(planByStatus.value).map(([k, v]) => ({ name: statusLabel(k), value: v, key: k }))
  if (!rows.length) { planChart?.setOption(emptyOption(t('dashboard.noPlan')), true); return }
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
    trendChart?.setOption(emptyOption(t('dashboard.noTrend')), true)
    return
  }
  trendChart?.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 8, right: 16, top: 20, bottom: 0, containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: labels, axisLabel: { color: '#8fa1bc', fontSize: 11, interval: 4 }, axisLine: { lineStyle: { color: '#223350' } } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#192742' } }, axisLabel: { color: '#8fa1bc' } },
    series: [{
      name: t('dashboard.alarmCount'), type: 'line', smooth: true, data: buckets, symbol: 'circle', symbolSize: 5,
      lineStyle: { color: C.red, width: 2, shadowColor: 'rgba(240, 82, 79, 0.5)', shadowBlur: 8 },
      itemStyle: { color: C.red },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(240,82,79,0.38)' }, { offset: 1, color: 'rgba(240,82,79,0.02)' }]) },
    }],
  }, true)
}

function renderCharts() {
  renderLevelChart(); renderTypeChart(); renderPlanChart(); renderTrendChart()
}

// 语言切换后图表文案（系列名/空态提示/字典标签）即时刷新
watch(locale, () => renderCharts())

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
.dash-header h2 {
  margin: 0; font-size: 24px; font-weight: 700; letter-spacing: 2px;
  background: linear-gradient(100deg, #eaf3ff 20%, #7cc7ff 55%, #22d3ee 90%);
  -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
}
.dash-date { color: #8fa1bc; font-size: 13px; }
.stats-row { margin-bottom: 14px; }
.stat-card {
  position: relative; text-align: center; cursor: pointer; overflow: hidden;
  border: 1px solid rgba(47, 129, 247, 0.16);
  background: linear-gradient(180deg, rgba(18, 30, 52, 0.72), rgba(11, 19, 34, 0.72));
  backdrop-filter: blur(8px);
  transition: transform 0.2s ease, box-shadow 0.25s ease, border-color 0.25s ease;
}
.stat-card::after {
  content: ''; position: absolute; left: 0; right: 0; top: 0; height: 3px;
  background: var(--accent, #2f81f7);
  box-shadow: 0 0 12px var(--accent, #2f81f7);
}
.stat-card:hover { transform: translateY(-3px); border-color: rgba(34, 211, 238, 0.5); box-shadow: 0 0 20px rgba(34, 211, 238, 0.18); }
.stat-card.blue   { --accent: #2f81f7; }
.stat-card.green  { --accent: #34d399; }
.stat-card.cyan   { --accent: #22d3ee; }
.stat-card.orange { --accent: #f5a623; }
.stat-card.red    { --accent: #f0524f; }
.stat-value { font-size: 32px; font-weight: 700; letter-spacing: 1px; }
.stat-card .stat-value { text-shadow: 0 0 18px color-mix(in srgb, var(--accent, #2f81f7) 60%, transparent); }
.stat-label { font-size: 13px; color: #8fa1bc; margin-top: 4px; letter-spacing: 1px; }
.panel {
  border: 1px solid rgba(47, 129, 247, 0.16);
  background: linear-gradient(180deg, rgba(18, 30, 52, 0.72), rgba(11, 19, 34, 0.72));
  backdrop-filter: blur(8px);
}
.panel :deep(.el-card__header) {
  padding: 10px 16px;
  background: linear-gradient(180deg, rgba(47, 129, 247, 0.10), rgba(47, 129, 247, 0.02));
  border-bottom: 1px solid rgba(47, 129, 247, 0.16);
}
.panel-title { display: inline-flex; align-items: center; gap: 6px; color: var(--el-text-color-primary); letter-spacing: 1px; }
.panel-title .el-icon { color: var(--uav-cyan, #22d3ee); filter: drop-shadow(0 0 4px rgba(34, 211, 238, 0.6)); }
.chart { height: 265px; width: 100%; }
.chart.trend { height: 225px; }
.mid-row { margin-bottom: 14px; }
.bottom-row { margin-bottom: 14px; }
.quick-actions { display: flex; flex-direction: column; gap: 8px; }
.quick-actions :deep(.el-button) { width: 100%; margin-left: 0; }
/* 生图按钮图标：黑底图用 screen 混合融入按钮 */
.qa-ic { width: 20px; height: 20px; mix-blend-mode: screen; border-radius: 4px; vertical-align: -5px; }
/* 黑飞专属：黑色徽标 */
.hf-tag {
  background: #0d0d0d; color: #fff; border: 1px solid #4a4a4a; font-weight: 600;
}
</style>
