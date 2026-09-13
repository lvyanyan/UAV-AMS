<template>
  <div class="page-container">
    <PageHeader :title="$t('registry.title')" :subtitle="$t('registry.subtitle', { owners: owners.length, drones: drones.length })">
      <template #actions>
        <el-button @click="showUomLogs">{{ $t('registry.uomLogs') }}</el-button>
        <el-button :loading="uomReportingAll" @click="uomReportAll">{{ $t('registry.uomReportAll') }}</el-button>
        <el-button v-if="tab==='owner'" type="primary" @click="showOwnerDialog">{{ $t('registry.addOwner') }}</el-button>
        <el-button v-else type="primary" @click="showDroneDialog">{{ $t('registry.addDrone') }}</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="table-card">
      <el-tabs v-model="tab">
        <el-tab-pane :label="$t('registry.tabOwner')" name="owner" />
        <el-tab-pane :label="$t('registry.tabDrone')" name="drone" />
      </el-tabs>

      <!-- 所有人登记 -->
      <el-table v-if="tab==='owner'" :data="ownerPage.paged.value" border stripe>
        <el-table-column prop="ownerName" :label="$t('registry.nameUnit')" width="140" />
        <el-table-column prop="idNumber" :label="$t('registry.idNumber')" width="180" />
        <el-table-column prop="phone" :label="$t('common.phone')" width="130" />
        <el-table-column prop="address" :label="$t('common.address')" min-width="150" />
        <el-table-column prop="registerStatus" :label="$t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.registerStatus==='APPROVED'?'success':row.registerStatus==='REJECTED'?'danger':'warning'">
              {{ regStatusLabel(row.registerStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="uomStatus" :label="$t('registry.uomStatus')" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.uomStatus==='REPORTED'" type="success" effect="plain">{{ $t('registry.uomReported') }}</el-tag>
            <el-tag v-else-if="row.uomStatus==='FAILED'" type="danger" effect="plain">{{ $t('registry.uomFailed') }}</el-tag>
            <el-tag v-else type="info" effect="plain">{{ $t('registry.uomPending') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.operation')" width="280">
          <template #default="{ row }">
            <el-button v-if="row.registerStatus==='PENDING'" size="small" type="success" @click="approveOwner(row)">{{ $t('common.pass') }}</el-button>
            <el-button v-if="row.registerStatus==='PENDING'" size="small" type="danger" @click="rejectOwner(row)">{{ $t('common.reject') }}</el-button>
            <el-button v-if="row.registerStatus==='APPROVED' && row.uomStatus!=='REPORTED'" size="small" @click="uomReportOwner(row)">{{ $t('registry.uomReport') }}</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty :description="$t('registry.emptyOwner')" /></template>
      </el-table>
      <div v-if="tab==='owner'" class="table-footer">
        <TablePagination v-model:page="ownerPage.page.value" v-model:size="ownerPage.size.value" :total="ownerPage.total.value" layout="total, prev, pager, next" />
      </div>

      <!-- 无人机登记 -->
      <el-table v-if="tab==='drone'" :data="dronePage.paged.value" border stripe>
        <el-table-column prop="droneSn" label="SN" width="150" />
        <el-table-column prop="droneModel" :label="$t('registry.model')" width="120" />
        <el-table-column prop="droneType" :label="$t('common.type')" width="110">
          <template #default="{ row }">{{ droneTypeLabel(row.droneType) }}</template>
        </el-table-column>
        <el-table-column prop="weightG" :label="$t('registry.weight')" width="100" />
        <el-table-column prop="registrationId" :label="$t('registry.regId')" width="180" />
        <el-table-column prop="registerStatus" :label="$t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.registerStatus==='APPROVED'?'success':row.registerStatus==='REJECTED'?'danger':'warning'">
              {{ regStatusLabel(row.registerStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="uomStatus" :label="$t('registry.uomStatus')" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.uomStatus==='REPORTED'" type="success" effect="plain">{{ $t('registry.uomReported') }}</el-tag>
            <el-tag v-else-if="row.uomStatus==='FAILED'" type="danger" effect="plain">{{ $t('registry.uomFailed') }}</el-tag>
            <el-tag v-else type="info" effect="plain">{{ $t('registry.uomPending') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.operation')" width="280">
          <template #default="{ row }">
            <el-button v-if="row.registerStatus==='PENDING'" size="small" type="success" @click="approveDrone(row)">{{ $t('common.pass') }}</el-button>
            <el-button v-if="row.registerStatus==='PENDING'" size="small" type="danger" @click="rejectDrone(row)">{{ $t('common.reject') }}</el-button>
            <el-button v-if="row.registerStatus==='APPROVED' && row.uomStatus!=='REPORTED'" size="small" @click="uomReportDrone(row)">{{ $t('registry.uomReport') }}</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty :description="$t('registry.emptyDrone')" /></template>
      </el-table>
      <div v-if="tab==='drone'" class="table-footer">
        <TablePagination v-model:page="dronePage.page.value" v-model:size="dronePage.size.value" :total="dronePage.total.value" layout="total, prev, pager, next" />
      </div>
    </el-card>

    <!-- Owner Dialog -->
    <el-dialog v-model="ownerDialog" :title="$t('registry.addOwner')" width="450px">
      <el-form :model="ownerForm" :label-width="labelWidth">
        <el-form-item :label="$t('registry.nameUnit')"><el-input v-model="ownerForm.ownerName" /></el-form-item>
        <el-form-item :label="$t('registry.idNumber')"><el-input v-model="ownerForm.idNumber" /></el-form-item>
        <el-form-item :label="$t('common.phone')"><el-input v-model="ownerForm.phone" /></el-form-item>
        <el-form-item :label="$t('common.email')"><el-input v-model="ownerForm.email" /></el-form-item>
        <el-form-item :label="$t('common.address')"><el-input v-model="ownerForm.address" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ownerDialog=false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveOwner">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- Drone Dialog -->
    <el-dialog v-model="droneDialog" :title="$t('registry.addDrone')" width="450px">
      <el-form :model="droneForm" :label-width="labelWidth">
        <el-form-item label="SN"><el-input v-model="droneForm.droneSn" /></el-form-item>
        <el-form-item :label="$t('registry.model')"><el-input v-model="droneForm.droneModel" /></el-form-item>
        <el-form-item :label="$t('common.type')">
          <el-select v-model="droneForm.droneType" style="width:100%">
            <el-option v-for="d in droneTypeItems" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('registry.weight')"><el-input-number v-model="droneForm.weightG" :min="0" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('registry.ownerId')"><el-input-number v-model="droneForm.ownerId" :min="1" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="droneDialog=false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveDrone">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- UOM 对接日志 -->
    <el-dialog v-model="uomLogsDialog" :title="$t('registry.uomLogs')" width="760px">
      <el-table :data="uomLogs" border size="small" v-loading="uomLogsLoading" max-height="420">
        <el-table-column prop="createTime" :label="$t('common.time')" width="160">
          <template #default="{ row }">{{ fmtDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="eventType" :label="$t('registry.uomEvent')" width="130">
          <template #default="{ row }">{{ row.eventType==='OWNER_REGISTER' ? $t('registry.eventOwner') : $t('registry.eventDrone') }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('common.status')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status==='SUCCESS' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" :label="$t('registry.uomRetryCount')" width="80" />
        <el-table-column prop="errorMsg" :label="$t('registry.uomError')" min-width="140" show-overflow-tooltip />
        <template #empty><el-empty :description="$t('common.empty')" :image-size="60" /></template>
      </el-table>
      <template #footer>
        <el-button @click="uomRetry">{{ $t('registry.uomRetryFailed') }}</el-button>
        <el-button type="primary" @click="uomLogsDialog=false">{{ $t('common.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { registryApi, type UavOwner, type UavRegistration, type UomIntegrationLog } from '@/api/registry'
import { usePaging } from '@/composables/usePaging'
import { useDict } from '@/composables/useDict'
import { fmtDateTime } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import TablePagination from '@/components/TablePagination.vue'
import { locale } from '@/locales'

const { t } = useI18n()
// 表单标签宽度：中英文标签长度不同，英文适当放宽
const labelWidth = computed(() => (locale.value === 'en' ? '110px' : '80px'))

const tab = ref('owner')
const owners = ref<UavOwner[]>([])
const drones = ref<UavRegistration[]>([])
const ownerDialog = ref(false)
const droneDialog = ref(false)
const ownerForm = ref<UavOwner>({ ownerName:'', idNumber:'', phone:'', email:'', address:'' })
const droneForm = ref<UavRegistration>({ ownerId:1, droneSn:'', droneModel:'', droneType:'MULTIROTOR', weightG:0 })
const { label: regStatusLabel } = useDict('register_status')
const { items: droneTypeItems, label: droneTypeLabel } = useDict('drone_type')
const ownerPage = usePaging(owners, 10)
const dronePage = usePaging(drones, 10)

onMounted(() => { loadOwners(); loadDrones() })

async function loadOwners() { const res = await registryApi.listOwners(); owners.value = (res as any).data || [] }
async function loadDrones() { const res = await registryApi.listDrones(); drones.value = (res as any).data || [] }

function showOwnerDialog() { ownerForm.value = { ownerName:'', idNumber:'', phone:'', email:'', address:'' }; ownerDialog.value = true }
async function saveOwner() { await registryApi.registerOwner(ownerForm.value); ElMessage.success(t('registry.regOk')); ownerDialog.value = false; loadOwners() }
async function approveOwner(row: UavOwner) { await registryApi.approveOwner(row.id!); ElMessage.success(t('common.approveOk')); loadOwners() }
async function rejectOwner(row: UavOwner) { await registryApi.rejectOwner(row.id!); ElMessage.success(t('common.rejectOk')); loadOwners() }

function showDroneDialog() { droneForm.value = { ownerId:1, droneSn:'', droneModel:'', droneType:'MULTIROTOR', weightG:0 }; droneDialog.value = true }
async function saveDrone() { await registryApi.registerDrone(droneForm.value); ElMessage.success(t('registry.regOk')); droneDialog.value = false; loadDrones() }
async function approveDrone(row: UavRegistration) { await registryApi.approveDrone(row.id!); ElMessage.success(t('common.approveOk')); loadDrones() }
async function rejectDrone(row: UavRegistration) { await registryApi.rejectDrone(row.id!); ElMessage.success(t('common.rejectOk')); loadDrones() }

// ===== UOM 对接 =====
const uomReportingAll = ref(false)
const uomLogsDialog = ref(false)
const uomLogsLoading = ref(false)
const uomLogs = ref<UomIntegrationLog[]>([])

async function uomReportOwner(row: UavOwner) {
  await registryApi.uomReportOwner(row.id!)
  ElMessage.success(t('registry.uomOk'))
  loadOwners()
}

async function uomReportDrone(row: UavRegistration) {
  await registryApi.uomReportDrone(row.id!)
  ElMessage.success(t('registry.uomOk'))
  loadDrones()
}

async function uomReportAll() {
  uomReportingAll.value = true
  try {
    const res: any = await registryApi.uomReportAll()
    const s = res?.data || {}
    ElMessage.success(t('registry.uomAllOk', { n: s.reported ?? 0 }))
    loadOwners(); loadDrones()
  } finally {
    uomReportingAll.value = false
  }
}

async function showUomLogs() {
  uomLogsDialog.value = true
  uomLogsLoading.value = true
  try {
    const res: any = await registryApi.uomLogs(100)
    uomLogs.value = res?.data || []
  } finally {
    uomLogsLoading.value = false
  }
}

async function uomRetry() {
  const res: any = await registryApi.uomRetry()
  const s = res?.data || {}
  ElMessage.success(t('registry.uomRetryOk', { n: s.retried ?? 0 }))
  showUomLogs()
}
</script>
