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

    <!-- 安全态势 + 飞行计划 + 快捷操作 -->
    <el-row :gutter="14" class="mid-row">
      <el-col :span="8">
        <el-card shadow="never" class="panel">
          <template #header><b>🚦 告警级别分布</b></template>
          <div v-for="(v, k) in alarmByLevel" :key="k" class="bar-row">
            <span class="bar-k">{{ levelLabel(k) }}</span>
            <div class="bar-track"><div class="bar-fill" :class="levelClass(k)" :style="{ width: barW(v, stats.alarmByLevel) }" /></div>
            <span class="bar-v">{{ v }}</span>
          </div>
          <div v-if="!Object.keys(alarmByLevel).length" class="empty">暂无告警数据</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="panel">
          <template #header><b>⚠️ 告警类型 TOP</b></template>
          <div v-for="(v, k) in topTypes" :key="k" class="bar-row">
            <span class="bar-k">{{ k }}</span>
            <div class="bar-track"><div class="bar-fill orange" :style="{ width: barW(v, topTypes) }" /></div>
            <span class="bar-v">{{ v }}</span>
          </div>
          <div v-if="!Object.keys(topTypes).length" class="empty">暂无告警类型</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="panel">
          <template #header><b>📋 飞行计划状态</b></template>
          <div v-for="(v, k) in planByStatus" :key="k" class="bar-row">
            <span class="bar-k">{{ statusLabel(k) }}</span>
            <div class="bar-track"><div class="bar-fill green" :style="{ width: barW(v, planByStatus) }" /></div>
            <span class="bar-v">{{ v }}</span>
          </div>
          <div v-if="!Object.keys(planByStatus).length" class="empty">暂无飞行计划</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近告警 + 快捷入口 -->
    <el-row :gutter="14" class="bottom-row">
      <el-col :span="16">
        <el-card shadow="never" class="panel">
          <template #header><b>🔴 最新告警</b></template>
          <el-table :data="recentAlarms" border size="small" max-height="240">
            <el-table-column prop="createTime" label="时间" width="120">
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { airspaceApi } from '@/api/airspace'
import { registryApi } from '@/api/registry'
import { pilotApi } from '@/api/pilot'
import { flightPlanApi } from '@/api/flight-plan'
import { alarmApi } from '@/api/alarm'

const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })

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

const topTypes = computed(() => {
  const entries = Object.entries(alarmByType.value).sort((a, b) => (b[1] as number) - (a[1] as number)).slice(0, 5)
  return Object.fromEntries(entries)
})

function barW(v: number, all: Record<string, number>) {
  const max = Math.max(1, ...Object.values(all))
  return Math.max(4, Math.round((v / max) * 100)) + '%'
}
function levelClass(k: string) { return k === 'CRITICAL' ? 'red' : k === 'SERIOUS' ? 'orange' : 'blue' }
function levelLabel(k: string) {
  const map: Record<string, string> = { CRITICAL: '危急', SERIOUS: '严重', GENERAL: '一般', WARNING: '警告', MINOR: '轻微', MAJOR: '重大' }
  return map[k] || k
}
function statusLabel(s: string) {
  const map: Record<string, string> = { DRAFT: '草稿', PENDING_LEVEL1: '一级审批', PENDING_LEVEL2: '二级审批', PENDING_LEVEL3: '三级审批', APPROVED: '已批准', REJECTED: '已拒绝' }
  return map[s] || s
}
function fmtTime(t?: string) { return t ? t.replace('T', ' ').slice(11, 19) : '--' }

let timer: any = null
async function loadAll() {
  try {
    const [drones, owners, pilots, airspaces, plans, stats, alarms] = await Promise.all([
      registryApi.listDrones(), registryApi.listOwners(), pilotApi.list(),
      airspaceApi.list(), flightPlanApi.list(), alarmApi.stats(), alarmApi.list(),
    ])
    regDrones.value = ((drones as any).data || []).length
    pilots.value = ((pilots as any).data || []).filter((p: any) => p.status === 'ACTIVE').length
    airspaces.value = ((airspaces as any).data || []).length
    const planRows = (plans as any).data || []
    plansTotal.value = planRows.length
    planByStatus.value = planRows.reduce((m: any, p: any) => { m[p.planStatus] = (m[p.planStatus] || 0) + 1; return m }, {})
    const st = (stats as any).data || {}
    alarmTotal.value = st.total || 0
    alarmByLevel.value = st.byLevel || {}
    alarmByType.value = st.byType || {}
    stats_source.value = { alarmByLevel: st.byLevel || {}, alarmByType: st.byType || {} }
    const rows = (alarms as any).data || []
    recentAlarms.value = rows.slice(0, 8)
  } catch (e) { console.warn('仪表盘数据加载失败', e) }
}

onMounted(() => { loadAll(); timer = setInterval(loadAll, 15000) })
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.dashboard { padding: 20px; }
.dash-header { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 16px; }
.dash-header h2 { margin: 0; color: #1f2d3d; }
.dash-date { color: #909399; font-size: 13px; }
.stats-row { margin-bottom: 14px; }
.stat-card { text-align: center; cursor: pointer; border-top: 3px solid #409eff; }
.stat-card.blue { border-top-color: #409eff; }
.stat-card.green { border-top-color: #67c23a; }
.stat-card.cyan { border-top-color: #36cfc9; }
.stat-card.orange { border-top-color: #e6a23c; }
.stat-card.red { border-top-color: #f56c6c; }
.stat-value { font-size: 32px; font-weight: 700; color: #1f2d3d; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.panel :deep(.el-card__header) { padding: 10px 16px; background: #f7f9fb; }
.mid-row { margin-bottom: 14px; }
.bottom-row { margin-bottom: 14px; }
.bar-row { display: flex; align-items: center; gap: 10px; padding: 5px 0; }
.bar-k { width: 90px; color: #606266; font-size: 13px; text-align: right; }
.bar-track { flex: 1; height: 12px; background: #f0f2f5; border-radius: 6px; overflow: hidden; }
.bar-fill { height: 100%; border-radius: 6px; background: #409eff; transition: width .6s; }
.bar-fill.orange { background: #e6a23c; }
.bar-fill.green { background: #67c23a; }
.bar-fill.red { background: #f56c6c; }
.bar-v { width: 60px; font-family: monospace; color: #303133; font-size: 13px; }
.empty { color: #909399; font-size: 13px; padding: 8px 0; }
</style>
