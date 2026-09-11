<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">告警中心</h2>
        <div class="page-subtitle">实时告警接入与处置闭环 · 近 200 条 · 共危急 {{ criticalCount }} / 严重 {{ seriousCount }} / 一般 {{ generalCount }}</div>
      </div>
      <div class="header-actions">
        <el-tag size="large" type="danger">危急: {{ criticalCount }}</el-tag>
        <el-tag size="large" type="warning">严重: {{ seriousCount }}</el-tag>
        <el-tag size="large" type="info">一般: {{ generalCount }}</el-tag>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="paged" border stripe v-loading="loading" :row-class-name="rowClass">
        <el-table-column prop="droneSn" label="无人机SN" width="140" />
        <el-table-column prop="alarmType" label="告警类型" width="140">
          <template #default="{ row }">{{ typeLabel(row.alarmType) }}</template>
        </el-table-column>
        <el-table-column prop="alarmLevel" label="等级" width="100">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.alarmLevel)" size="small">{{ levelLabel(row.alarmLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="消息内容" min-width="250" />
        <el-table-column prop="sourceModule" label="来源模块" width="120" />
        <el-table-column prop="createTime" label="时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="!row.handled" size="small" type="primary" @click="handleAlarm(row)">处理</el-button>
            <el-tag v-else size="small" type="success">已处理</el-tag>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无告警" /></template>
      </el-table>
      <div class="table-footer">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
          layout="total, prev, pager, next, sizes" :page-sizes="[10, 20, 50]" background />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { alarmApi, type AlarmEvent } from '@/api/alarm'
import { usePaging } from '@/composables/usePaging'
import { useDict } from '@/composables/useDict'
import { fmtDateTime as fmtTime } from '@/utils/format'

const list = ref<AlarmEvent[]>([])
const loading = ref(false)
const { page, size, total, paged } = usePaging(list)
const { label: levelLabel } = useDict('alarm_level')
const { label: typeLabel } = useDict('alarm_type')

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
</script>

<style scoped>
:deep(.row-critical td.el-table__cell) { background: rgba(245, 108, 108, 0.10) !important; }
:deep(.row-serious td.el-table__cell) { background: rgba(230, 162, 60, 0.10) !important; }
</style>
