<template>
  <div class="page-container">
    <PageHeader :title="$t('alarm.title')" :subtitle="$t('alarm.subtitle')">
      <template #actions>
        <el-tag size="large" type="danger">{{ $t('dict.alarm_level.CRITICAL') }}: {{ criticalCount }}</el-tag>
        <el-tag size="large" type="warning">{{ $t('dict.alarm_level.SERIOUS') }}: {{ seriousCount }}</el-tag>
        <el-tag size="large" type="info">{{ $t('dict.alarm_level.GENERAL') }}: {{ generalCount }}</el-tag>
      </template>
    </PageHeader>

    <el-card shadow="never" class="table-card">
      <el-table :data="paged" border stripe v-loading="loading" :row-class-name="rowClass">
        <el-table-column prop="droneSn" :label="$t('common.sn')" width="140" />
        <el-table-column prop="alarmType" :label="$t('alarm.type')" width="140">
          <template #default="{ row }">{{ typeLabel(row.alarmType) }}</template>
        </el-table-column>
        <el-table-column prop="alarmLevel" :label="$t('common.level')" width="100">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.alarmLevel)" size="small">{{ levelLabel(row.alarmLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" :label="$t('alarm.content')" min-width="250" />
        <el-table-column prop="sourceModule" :label="$t('alarm.source')" width="120">
          <template #default="{ row }">{{ row.sourceModule || $t('alarm.sourceEngine') }}</template>
        </el-table-column>
        <el-table-column prop="createTime" :label="$t('common.time')" width="160">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column :label="$t('common.operation')" width="100">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'CLOSED'" size="small" type="primary" @click="handleAlarm(row)">{{ $t('common.close') }}</el-button>
            <el-tag v-else size="small" type="success">{{ $t('alarm.closed') }}</el-tag>
          </template>
        </el-table-column>
        <template #empty><el-empty :description="$t('alarm.empty')" /></template>
      </el-table>
      <TablePagination v-model:page="page" v-model:size="size" :total="total" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { alarmApi, type AlarmEvent } from '@/api/alarm'
import { usePaging } from '@/composables/usePaging'
import { useDict } from '@/composables/useDict'
import { fmtDateTime as fmtTime } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import TablePagination from '@/components/TablePagination.vue'

const { t } = useI18n()

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
  ElMessage.success(t('alarm.closeOk'))
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
