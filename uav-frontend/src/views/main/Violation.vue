<template>
  <div class="page-container">
    <PageHeader title="违规处置" :subtitle="`由危急/严重告警派生的违规台账（只读），处置动作联动告警闭环 · 共 ${total} 条`">
      <template #actions>
        <el-tag size="large" type="warning">待处理: {{ pendingCount }}</el-tag>
        <el-tag size="large" type="success">已结案: {{ closedCount }}</el-tag>
      </template>
    </PageHeader>

    <el-card shadow="never" class="table-card">
      <el-table :data="paged" border stripe v-loading="loading">
        <el-table-column prop="droneSn" label="无人机SN" width="140" />
        <el-table-column prop="violationType" label="违规类型" width="140">
          <template #default="{ row }">{{ typeLabel(row.violationType) }}</template>
        </el-table-column>
        <el-table-column prop="violationLevel" label="等级" width="100">
          <template #default="{ row }">
            <el-tag :type="row.violationLevel==='CRITICAL'?'danger':'warning'">{{ levelLabel(row.violationLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" />
        <el-table-column prop="status" label="处理状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status==='CLOSED'?'success':'warning'">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="发生时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
            <el-button v-if="row.status==='PENDING'" size="small" type="primary" @click="processItem(row)">处理</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无违规记录" /></template>
      </el-table>
      <TablePagination v-model:page="page" v-model:size="size" :total="total" />
    </el-card>

    <el-dialog v-model="detailDialog" title="违规详情/处置" width="500px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="无人机SN">{{ detail.droneSn }}</el-descriptions-item>
        <el-descriptions-item label="违规类型">{{ typeLabel(detail.violationType) }}</el-descriptions-item>
        <el-descriptions-item label="等级">{{ levelLabel(detail.violationLevel) }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ detail.description }}</el-descriptions-item>
        <el-descriptions-item label="发生时间">{{ fmtTime(detail.createTime) }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="detail.status==='PENDING'" style="margin-top:12px">
        <el-input v-model="handleNote" type="textarea" :rows="2" placeholder="处理备注（将标记关联告警为已处理）" />
        <el-button type="primary" style="margin-top:8px" @click="doProcess">确认处置</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { alarmApi } from '@/api/alarm'
import { usePaging } from '@/composables/usePaging'
import { useDict } from '@/composables/useDict'
import { fmtDateTime as fmtTime } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import TablePagination from '@/components/TablePagination.vue'

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
  ElMessage.success('违规已处置')
  detailDialog.value = false
  loadData()
}

</script>
