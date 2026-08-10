<template>
  <div class="page-container">
    <div class="page-header">
      <h2>违规处置</h2>
      <div class="stats-row">
        <span>告警触发后自动生成违规记录，支持自定罚则规则</span>
      </div>
    </div>

    <el-table :data="list" border stripe>
      <el-table-column prop="droneSn" label="无人机SN" width="140" />
      <el-table-column prop="violationType" label="违规类型" width="140">
        <template #default="{ row }">{{ typeLabel(row.violationType) }}</template>
      </el-table-column>
      <el-table-column prop="violationLevel" label="等级" width="100">
        <template #default="{ row }">
          <el-tag :type="row.violationLevel==='CRITICAL'?'danger':row.violationLevel==='SERIOUS'?'warning':'info'">{{ row.violationLevel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="200" />
      <el-table-column prop="penalty" label="处罚" width="180" />
      <el-table-column prop="status" label="处理状态" width="120">
        <template #default="{ row }">
          <el-tag :type="row.status==='CLOSED'?'success':row.status==='PENDING'?'warning':'info'">
            {{ row.status==='CLOSED'?'已结案':row.status==='PENDING'?'待处理':'处理中' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="发生时间" width="160" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="viewDetail(row)">详情</el-button>
          <el-button v-if="row.status==='PENDING'" size="small" type="primary" @click="processItem(row)">处理</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="detailDialog" title="违规详情/处置" width="500px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="无人机SN">{{ detail.droneSn }}</el-descriptions-item>
        <el-descriptions-item label="违规类型">{{ typeLabel(detail.violationType) }}</el-descriptions-item>
        <el-descriptions-item label="等级">{{ detail.violationLevel }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ detail.description }}</el-descriptions-item>
        <el-descriptions-item label="处罚">{{ detail.penalty }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="detail.status==='PENDING'" style="margin-top:12px">
        <el-input v-model="handleNote" type="textarea" :rows="2" placeholder="处理备注" />
        <el-button type="primary" style="margin-top:8px" @click="doProcess">确认处置</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

interface Violation {
  id?: number; droneSn: string; violationType: string; violationLevel: string
  description: string; penalty: string; status: string; createTime: string
}

const list = ref<Violation[]>([
  { id:1, droneSn:'SIM-00001', violationType:'AIRSPACE', violationLevel:'SERIOUS', description:'进入管制空域未报备', penalty:'罚款5000元', status:'PENDING', createTime:'2026-05-28 10:30:00' },
  { id:2, droneSn:'SIM-00012', violationType:'ALTITUDE', violationLevel:'CRITICAL', description:'超限飞行高度达850m(限制500m)', penalty:'强制返航+禁飞30天', status:'PENDING', createTime:'2026-05-28 11:15:00' },
  { id:3, droneSn:'SIM-00008', violationType:'NO_PLAN', violationLevel:'GENERAL', description:'无飞行计划起飞', penalty:'警告通知', status:'CLOSED', createTime:'2026-05-27 15:00:00' },
])

const detailDialog = ref(false)
const detail = ref<Violation>({} as Violation)
const handleNote = ref('')

function viewDetail(row: Violation) { detail.value = { ...row }; detailDialog.value = true }
function processItem(row: Violation) { detail.value = { ...row }; handleNote.value = ''; detailDialog.value = true }

function doProcess() {
  const idx = list.value.findIndex(v => v.id === detail.value.id)
  if (idx >= 0) list.value[idx].status = 'CLOSED'
  detail.value.status = 'CLOSED'
  ElMessage.success('违规已处置')
}

function typeLabel(t: string) {
  const map: Record<string,string> = { AIRSPACE:'空域违规', ALTITUDE:'高度超限', NO_PLAN:'无计划飞行', SPEED:'速度违规', GEOFENCE:'围栏闯入' }
  return map[t] || t
}
</script>

<style scoped>
.page-container { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.stats-row { color: #909399; font-size: 14px; }
</style>
