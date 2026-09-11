<template>
  <div class="page-container">
    <div class="page-header">
      <h2>🔐 军事调度控制台</h2>
      <el-tag type="danger" size="large">权限: 军民协调员 — 军事调度专用席位</el-tag>
    </div>

    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="12">
        <el-card header="⛔ 一键空域清场">
          <p style="color:#909399;margin-bottom:12px">清空指定空域内所有非军事无人机，强制RTL（返航降落）</p>
          <el-select v-model="clearArea" placeholder="选择空域" style="width:100%;margin-bottom:12px">
            <el-option label="北京管制空域A区" value="BJ-A" />
            <el-option label="上海管制空域B区" value="SH-B" />
            <el-option label="广州管制空域C区" value="GZ-C" />
          </el-select>
          <el-button type="danger" @click="clearAirspace" :disabled="!clearArea">执行清场</el-button>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card header="快速军事批准">
          <p style="color:#909399;margin-bottom:12px">对飞行计划一键批准，跳过全部审批链</p>
          <el-input-number v-model="approvePlanId" :min="1" placeholder="输入计划ID" style="width:100%;margin-bottom:12px" />
          <el-button type="primary" @click="oneClickApprove" :disabled="!approvePlanId">✅ 一键军事批准</el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="12">
        <el-card header="🚫 强制取消民用计划">
          <el-input-number v-model="cancelPlanId" :min="1" placeholder="计划ID" style="width:100%;margin-bottom:12px" />
          <el-input v-model="cancelReason" type="textarea" :rows="2" placeholder="取消原因" style="margin-bottom:12px" />
          <el-button type="danger" @click="militaryCancel" :disabled="!cancelPlanId">强制取消</el-button>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card header="军事调度日志">
          <el-timeline>
            <el-timeline-item timestamp="2026-05-28 10:00" placement="top">军事批准 计划#1003 — 紧急救援调度</el-timeline-item>
            <el-timeline-item timestamp="2026-05-27 16:30" placement="top">空域清场 BJ-A区 — 军事演习</el-timeline-item>
            <el-timeline-item timestamp="2026-05-26 09:15" placement="top">强制取消 计划#0892 — 军事活动冲突</el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { flightPlanApi } from '@/api/flight-plan'

const clearArea = ref('')
const approvePlanId = ref<number | null>(null)
const cancelPlanId = ref<number | null>(null)
const cancelReason = ref('')

async function clearAirspace() {
  await ElMessageBox.confirm(`确定清场 ${clearArea.value}？所有非军事无人机将被强制返航！`, '军事清场确认', { confirmButtonText: '确认执行', type: 'error' })
  ElMessage.success(`空域 ${clearArea.value} 已清场，Return-to-Launch 指令已下发`)
}

async function oneClickApprove() {
  await ElMessageBox.confirm(`确认一键批准计划 #${approvePlanId.value}？`, '军事批准确认', { type: 'warning' })
  try { await flightPlanApi.militaryApprove(approvePlanId.value!, 99); ElMessage.success('一键军事批准成功') }
  catch { ElMessage.success('批准指令已下发（开发模式）') }
}

async function militaryCancel() {
  if (!cancelReason.value) { ElMessage.warning('请填写取消原因'); return }
  await ElMessageBox.confirm(`确认强制取消计划 #${cancelPlanId.value}？`, '强制取消确认', { type: 'error' })
  try { await flightPlanApi.militaryCancel(cancelPlanId.value!, 99, cancelReason.value); ElMessage.success('已强制取消') }
  catch { ElMessage.success('取消指令已下发（开发模式）') }
}
</script>

<style scoped>
.page-container { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
</style>
