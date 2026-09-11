<template>
  <div class="page-container">
    <div class="page-header">
      <h2>飞行计划管理</h2>
      <div class="header-actions">
        <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width:160px" @change="loadPlans">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="待一级审批" value="PENDING_LEVEL1" />
          <el-option label="待二级审批" value="PENDING_LEVEL2" />
          <el-option label="待三级审批" value="PENDING_LEVEL3" />
          <el-option label="已批准" value="APPROVED" />
          <el-option label="已拒绝" value="REJECTED" />
        </el-select>
        <el-button type="primary" @click="showCreateDialog">新建计划</el-button>
      </div>
    </div>

    <el-table :data="plans" border stripe v-loading="loading" style="width:100%">
      <el-table-column prop="planCode" label="计划编号" width="140" />
      <el-table-column prop="pilotName" label="飞手" width="100">
        <template #default="{ row }">{{ pilotNames[row.pilotId] || row.pilotName || '--' }}</template>
      </el-table-column>
      <el-table-column prop="droneSn" label="无人机SN" width="130" />
      <el-table-column prop="departure" label="起飞点" min-width="120" />
      <el-table-column prop="destination" label="降落点" min-width="120" />
      <el-table-column prop="plannedStart" label="开始时间" width="160" />
      <el-table-column prop="plannedEnd" label="结束时间" width="160" />
      <el-table-column prop="altCeilingM" label="最大高度(m)" width="110" />
      <el-table-column prop="planStatus" label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.planStatus)">{{ statusLabel(row.planStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="showDetail(row)">详情</el-button>
          <el-button v-if="row.planStatus==='DRAFT'" size="small" type="success" @click="submitPlan(row)">提交</el-button>
          <el-button v-if="pendingLevel(row.planStatus)" size="small" type="warning" @click="approvePlan(row)">审批</el-button>
          <el-button size="small" type="danger" @click="rejectPlan(row)">拒绝</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingPlan.id ? '编辑计划' : '新建计划'" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="计划编号"><el-input v-model="form.planNo" placeholder="自动生成" disabled /></el-form-item>
        <el-form-item label="飞手"><el-input v-model="form.pilotName" placeholder="飞手姓名" /></el-form-item>
        <el-form-item label="无人机SN"><el-input v-model="form.droneSn" placeholder="无人机序列号" /></el-form-item>
        <el-form-item label="飞行区域"><el-input v-model="form.flightArea" placeholder="e.g. 北京市朝阳区" /></el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="form.startTime" type="datetime" style="width:100%" /></el-form-item>
        <el-form-item label="结束时间"><el-date-picker v-model="form.endTime" type="datetime" style="width:100%" /></el-form-item>
        <el-form-item label="最大高度(m)"><el-input-number v-model="form.maxAltitude" :min="0" :max="5000" style="width:100%" /></el-form-item>
        <el-form-item label="飞行目的"><el-input v-model="form.flightPurpose" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="savePlan">保存</el-button>
      </template>
    </el-dialog>

    <!-- 审批对话框 -->
    <el-dialog v-model="approveDialog" title="审批意见" width="400px">
      <el-input v-model="approveComment" type="textarea" :rows="3" placeholder="审批意见（拒绝时必填）" />
      <template #footer>
        <el-button @click="approveDialog=false">取消</el-button>
        <el-button type="primary" @click="doApprove(true)">通过</el-button>
        <el-button type="danger" @click="doApprove(false)">拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { flightPlanApi, type FlightPlan } from '@/api/flight-plan'
import { pilotApi } from '@/api/pilot'

const plans = ref<FlightPlan[]>([])
const loading = ref(false)
const statusFilter = ref('')
const dialogVisible = ref(false)
const approveDialog = ref(false)
const editingPlan = ref<FlightPlan>({})
const currentPlan = ref<FlightPlan>({})
const approveComment = ref('')

const form = ref<FlightPlan>({
  pilotName: '', droneSn: '', flightArea: '', maxAltitude: 120, flightPurpose: '',
  startTime: '', endTime: '', planNo: ''
})

const pilotNames = ref<Record<number, string>>({})

onMounted(async () => {
  loadPlans()
  try {
    const res = await pilotApi.list()
    const m: Record<number, string> = {}
    for (const p of ((res as any).data || [])) m[p.id] = p.pilotName
    pilotNames.value = m
  } catch (e) {}
})

async function loadPlans() {
  loading.value = true
  try {
    const res = statusFilter.value
      ? await flightPlanApi.listByStatus(statusFilter.value)
      : await flightPlanApi.list()
    plans.value = (res as any).data || []
  } finally { loading.value = false }
}

function showCreateDialog() {
  editingPlan.value = {}
  form.value = { pilotName: '', droneSn: '', flightArea: '', maxAltitude: 120, flightPurpose: '', startTime: '', endTime: '', planNo: '' }
  dialogVisible.value = true
}

function showDetail(row: FlightPlan) {
  editingPlan.value = { ...row }
  form.value = { ...row }
  dialogVisible.value = true
}

async function savePlan() {
  if (editingPlan.value.id) {
    await flightPlanApi.update(editingPlan.value.id!, form.value)
    ElMessage.success('更新成功')
  } else {
    await flightPlanApi.create(form.value)
    ElMessage.success('创建成功')
  }
  dialogVisible.value = false
  loadPlans()
}

async function submitPlan(row: FlightPlan) {
  await flightPlanApi.submit(row.id!)
  ElMessage.success('已提交审批')
  loadPlans()
}

function approvePlan(row: FlightPlan) {
  currentPlan.value = row
  approveComment.value = ''
  approveDialog.value = true
}

function rejectPlan(row: FlightPlan) {
  currentPlan.value = row
  approveComment.value = ''
  approveDialog.value = true
}

async function doApprove(pass: boolean) {
  const id = currentPlan.value.id!
  if (pass) {
    await flightPlanApi.approve(id, 1, approveComment.value)
    ElMessage.success('审批通过')
  } else {
    if (!approveComment.value) { ElMessage.warning('拒绝需填写理由'); return }
    await flightPlanApi.reject(id, 1, approveComment.value)
    ElMessage.success('已拒绝')
  }
  approveDialog.value = false
  loadPlans()
}

function pendingLevel(s: string) { return s && s.startsWith('PENDING_LEVEL') }
function statusTag(s: string): any {
  const map: Record<string,string> = { DRAFT:'info', PENDING_LEVEL1:'warning', PENDING_LEVEL2:'warning', PENDING_LEVEL3:'warning', APPROVED:'success', REJECTED:'danger', MILITARY_CANCELLED:'danger' }
  return map[s] || 'info'
}
function statusLabel(s: string) {
  const map: Record<string,string> = { DRAFT:'草稿', PENDING_LEVEL1:'一级审批中', PENDING_LEVEL2:'二级审批中', PENDING_LEVEL3:'三级审批中', APPROVED:'已批准', REJECTED:'已拒绝', MILITARY_CANCELLED:'军事取消' }
  return map[s] || s
}
</script>

<style scoped>
.page-container { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.header-actions { display: flex; gap: 12px; }
</style>
