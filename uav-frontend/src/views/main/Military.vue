<template>
  <div class="page-container">
    <PageHeader :title="$t('military.title')" :subtitle="$t('military.subtitle')">
      <template #actions>
        <el-tag type="danger" size="large">{{ $t('military.permTag') }}</el-tag>
      </template>
    </PageHeader>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card class="ops-card">
          <template #header><b class="ops-title"><el-icon><Warning /></el-icon> {{ $t('military.clearTitle') }}</b></template>
          <p class="ops-desc">{{ $t('military.clearDesc') }}</p>
          <el-select v-model="clearArea" :placeholder="$t('military.selectArea')" style="width:100%;margin-bottom:12px">
            <el-option :label="$t('military.areaA')" value="BJ-A" />
            <el-option :label="$t('military.areaB')" value="SH-B" />
            <el-option :label="$t('military.areaC')" value="GZ-C" />
          </el-select>
          <el-button type="danger" @click="clearAirspace" :disabled="!clearArea">{{ $t('military.executeClear') }}</el-button>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card class="ops-card">
          <template #header><b class="ops-title"><el-icon><CircleCheck /></el-icon> {{ $t('military.approveTitle') }}</b></template>
          <p class="ops-desc">{{ $t('military.approveDesc') }}</p>
          <el-input-number v-model="approvePlanId" :min="1" :placeholder="$t('military.planIdPh')" style="width:100%;margin-bottom:12px" />
          <el-button type="primary" @click="oneClickApprove" :disabled="!approvePlanId">{{ $t('military.oneClickApprove') }}</el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="12">
        <el-card class="ops-card">
          <template #header><b class="ops-title"><el-icon><CircleClose /></el-icon> {{ $t('military.cancelTitle') }}</b></template>
          <el-input-number v-model="cancelPlanId" :min="1" :placeholder="$t('military.planId')" style="width:100%;margin-bottom:12px" />
          <el-input v-model="cancelReason" type="textarea" :rows="2" :placeholder="$t('military.cancelReason')" style="margin-bottom:12px" />
          <el-button type="danger" @click="militaryCancel" :disabled="!cancelPlanId">{{ $t('military.forceCancel') }}</el-button>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card class="ops-card">
          <template #header><b class="ops-title"><el-icon><Document /></el-icon> {{ $t('military.logTitle') }}</b></template>
          <el-timeline>
            <el-timeline-item timestamp="2026-05-28 10:00" placement="top">{{ $t('military.log1') }}</el-timeline-item>
            <el-timeline-item timestamp="2026-05-27 16:30" placement="top">{{ $t('military.log2') }}</el-timeline-item>
            <el-timeline-item timestamp="2026-05-26 09:15" placement="top">{{ $t('military.log3') }}</el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Warning, CircleCheck, CircleClose, Document } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { flightPlanApi } from '@/api/flight-plan'

const { t } = useI18n()

const clearArea = ref('')
const approvePlanId = ref<number | null>(null)
const cancelPlanId = ref<number | null>(null)
const cancelReason = ref('')

async function clearAirspace() {
  await ElMessageBox.confirm(t('military.clearConfirm', { area: clearArea.value }), t('military.clearConfirmTitle'), { confirmButtonText: t('military.confirmExecute'), type: 'error' })
  ElMessage.success(t('military.clearOk', { area: clearArea.value }))
}

async function oneClickApprove() {
  await ElMessageBox.confirm(t('military.approveConfirm', { id: approvePlanId.value }), t('military.approveConfirmTitle'), { type: 'warning' })
  try { await flightPlanApi.militaryApprove(approvePlanId.value!, 99); ElMessage.success(t('military.approveOk')) }
  catch { ElMessage.success(t('military.approveSent')) }
}

async function militaryCancel() {
  if (!cancelReason.value) { ElMessage.warning(t('military.needReason')); return }
  await ElMessageBox.confirm(t('military.cancelConfirm', { id: cancelPlanId.value }), t('military.cancelConfirmTitle'), { type: 'error' })
  try { await flightPlanApi.militaryCancel(cancelPlanId.value!, 99, cancelReason.value); ElMessage.success(t('military.cancelOk')) }
  catch { ElMessage.success(t('military.cancelSent')) }
}
</script>

<style scoped>
.ops-card {
  border: 1px solid rgba(47, 129, 247, 0.16);
  background: linear-gradient(180deg, rgba(18, 30, 52, 0.72), rgba(11, 19, 34, 0.72));
}
.ops-title { display: inline-flex; align-items: center; gap: 6px; letter-spacing: 1px; }
.ops-title .el-icon { color: var(--uav-cyan, #22d3ee); }
.ops-desc { color: var(--el-text-color-secondary, #8fa1bc); margin: 0 0 12px; }
</style>
