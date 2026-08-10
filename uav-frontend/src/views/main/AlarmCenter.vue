<template>
  <div class="page-container">
    <div class="page-header">
      <h2>告警中心</h2>
      <div class="stats-row">
        <el-tag size="large" type="danger">危急: {{ criticalCount }}</el-tag>
        <el-tag size="large" type="warning">严重: {{ seriousCount }}</el-tag>
        <el-tag size="large" type="info">一般: {{ generalCount }}</el-tag>
      </div>
    </div>

    <el-table :data="list" border stripe v-loading="loading" :row-class-name="rowClass">
      <el-table-column prop="droneSn" label="无人机SN" width="140" />
      <el-table-column prop="alarmType" label="告警类型" width="120">
        <template #default="{ row }">{{ typeLabel(row.alarmType) }}</template>
      </el-table-column>
      <el-table-column prop="alarmLevel" label="等级" width="100">
        <template #default="{ row }">
          <el-tag :type="levelTag(row.alarmLevel)" size="small">{{ levelLabel(row.alarmLevel) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="message" label="消息内容" min-width="250" />
      <el-table-column prop="sourceModule" label="来源模块" width="120" />
      <el-table-column prop="createTime" label="时间" width="160" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button v-if="!row.handled" size="small" type="primary" @click="handleAlarm(row)">处理</el-button>
          <el-tag v-else size="small" type="success">已处理</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { alarmApi, type AlarmEvent } from '@/api/alarm'

const list = ref<AlarmEvent[]>([])
const loading = ref(false)

const criticalCount = computed(() => list.value.filter(a => a.alarmLevel === 'CRITICAL').length)
const seriousCount = computed(() => list.value.filter(a => a.alarmLevel === 'SERIOUS').length)
const generalCount = computed(() => list.value.filter(a => a.alarmLevel === 'GENERAL').length)

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try { const res = await alarmApi.list(); list.value = (res as any).data || [] }
  finally { loading.value = false }
}

async function handleAlarm(row: AlarmEvent) {
  await alarmApi.handle(row.id!)
  ElMessage.success('已标记为已处理')
  loadData()
}

function rowClass({ row }: { row: AlarmEvent }) {
  if (row.alarmLevel === 'CRITICAL') return 'row-critical'
  if (row.alarmLevel === 'SERIOUS') return 'row-serious'
  return ''
}

function levelTag(l: string): any {
  const map: Record<string,string> = { GENERAL:'info', SERIOUS:'warning', CRITICAL:'danger' }
  return map[l] || 'info'
}
function levelLabel(l: string) {
  const map: Record<string,string> = { GENERAL:'一般', SERIOUS:'严重', CRITICAL:'危急' }
  return map[l] || l
}
function typeLabel(t: string) {
  const map: Record<string,string> = { AIRSPACE:'空域违规', WEATHER:'气象风险', EQUIPMENT:'设备异常', TERRAIN:'地形风险', CONFLICT:'飞行冲突', ROUTE:'航路偏离' }
  return map[t] || t
}
</script>

<style scoped>
.page-container { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.stats-row { display: flex; gap: 12px; }
:deep(.row-critical) { background-color: #fff0f0 !important; }
:deep(.row-serious) { background-color: #fff8e6 !important; }
</style>
