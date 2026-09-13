<template>
  <div class="page-container">
    <PageHeader :title="$t('violation.title')" :subtitle="$t('violation.subtitle', { total })">
      <template #actions>
        <el-tag size="large" type="warning">{{ $t('dict.violation_status.PENDING') }}: {{ pendingCount }}</el-tag>
        <el-tag size="large" type="success">{{ $t('dict.violation_status.CLOSED') }}: {{ closedCount }}</el-tag>
      </template>
    </PageHeader>

    <el-card shadow="never" class="table-card">
      <el-table :data="paged" border stripe v-loading="loading">
        <el-table-column prop="droneSn" :label="$t('common.sn')" width="140" />
        <el-table-column prop="violationType" :label="$t('violation.type')" width="140">
          <template #default="{ row }">{{ typeLabel(row.violationType) }}</template>
        </el-table-column>
        <el-table-column prop="violationLevel" :label="$t('common.level')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.violationLevel==='CRITICAL'?'danger':'warning'">{{ levelLabel(row.violationLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="$t('common.desc')" min-width="200" />
        <el-table-column prop="status" :label="$t('violation.handleStatus')" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status==='CLOSED'?'success':'warning'">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="$t('violation.time')" width="160">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column :label="$t('common.operation')" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">{{ $t('common.detail') }}</el-button>
            <el-button v-if="row.status==='PENDING'" size="small" type="primary" @click="processItem(row)">{{ $t('violation.process') }}</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty :description="$t('violation.empty')" /></template>
      </el-table>
      <TablePagination v-model:page="page" v-model:size="size" :total="total" />
    </el-card>

    <el-dialog v-model="detailDialog" :title="$t('violation.detailTitle')" width="500px">
      <el-descriptions :column="1" border>
        <el-descriptions-item :label="$t('common.sn')">{{ detail.droneSn }}</el-descriptions-item>
        <el-descriptions-item :label="$t('violation.type')">{{ typeLabel(detail.violationType) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('common.level')">{{ levelLabel(detail.violationLevel) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('common.desc')">{{ detail.description }}</el-descriptions-item>
        <el-descriptions-item :label="$t('violation.time')">{{ fmtTime(detail.createTime) }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="detail.status==='PENDING'" style="margin-top:12px">
        <el-input v-model="handleNote" type="textarea" :rows="2" :placeholder="$t('violation.notePh')" />
        <el-button type="primary" style="margin-top:8px" @click="doProcess">{{ $t('violation.confirmProcess') }}</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { alarmApi } from '@/api/alarm'
import { usePaging } from '@/composables/usePaging'
import { useDict } from '@/composables/useDict'
import { fmtDateTime as fmtTime } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import TablePagination from '@/components/TablePagination.vue'

const { t } = useI18n()

interface Violation {
  id?: number; droneSn: string; violationType: string; violationLevel: string
  description: string; status: string; createTime: string
}

const list = ref<Violation[]>([])
const loading = ref(false)
const detailDialog = ref(false)
const detail = ref<Violation>({} as Violation)
const handleNote = ref('')
const { page, size, total, paged } = usePaging(list)
const { label: typeLabel } = useDict('alarm_type')
const { label: levelLabel } = useDict('alarm_level')
const { label: statusLabel } = useDict('violation_status')

const pendingCount = computed(() => list.value.filter(v => v.status === 'PENDING').length)
const closedCount = computed(() => list.value.filter(v => v.status === 'CLOSED').length)

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try { const res = await alarmApi.violations(); list.value = (res as any).data || [] }
  finally { loading.value = false }
}

function viewDetail(row: Violation) { detail.value = { ...row }; detailDialog.value = true }
function processItem(row: Violation) { detail.value = { ...row }; handleNote.value = ''; detailDialog.value = true }

async function doProcess() {
  // 违规台账只读，处置动作落到关联告警的 handled 闭环
  await alarmApi.handle(detail.value.id!)
  ElMessage.success(t('violation.processedOk'))
  detailDialog.value = false
  loadData()
}

</script>
