<template>
  <div class="page-container">
    <PageHeader :title="$t('pilot.title')" :subtitle="$t('pilot.subtitle', { total })">
      <template #actions>
        <el-button type="primary" @click="showCreate">{{ $t('pilot.create') }}</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="table-card">
      <el-table :data="paged" border stripe v-loading="loading">
        <el-table-column prop="pilotName" :label="$t('common.name')" width="100" />
        <el-table-column prop="idNumber" :label="$t('pilot.idNumber')" width="180" />
        <el-table-column prop="phone" :label="$t('common.phone')" width="130" />
        <el-table-column prop="licenseNo" :label="$t('pilot.licenseNo')" width="180" />
        <el-table-column prop="licenseLevel" :label="$t('pilot.licenseLevel')" width="160" />
        <el-table-column prop="licenseExpire" :label="$t('pilot.licenseExpire')" width="130">
          <template #default="{ row }">{{ fmtDate(row.licenseExpire) }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status==='ACTIVE'?'success':'danger'">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.operation')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="editItem(row)">{{ $t('common.edit') }}</el-button>
            <el-button size="small" @click="showMedical(row)">{{ $t('pilot.medical') }}</el-button>
            <el-button v-if="row.status==='ACTIVE'" size="small" type="danger" @click="suspendItem(row)">{{ $t('pilot.suspend') }}</el-button>
            <el-button v-else size="small" type="success" @click="reactivateItem(row)">{{ $t('pilot.restore') }}</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty :description="$t('pilot.empty')" /></template>
      </el-table>
      <TablePagination v-model:page="page" v-model:size="size" :total="total" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing.id ? $t('pilot.edit') : $t('pilot.create')" width="500px">
      <el-form :model="form" :label-width="labelWidth">
        <el-form-item :label="$t('common.name')"><el-input v-model="form.pilotName" /></el-form-item>
        <el-form-item :label="$t('pilot.idNumber')"><el-input v-model="form.idNumber" /></el-form-item>
        <el-form-item :label="$t('common.phone')"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item :label="$t('pilot.licenseNo')"><el-input v-model="form.licenseNo" /></el-form-item>
        <el-form-item :label="$t('pilot.licenseLevel')">
          <el-select v-model="form.licenseLevel" style="width:100%">
            <!-- 选项值 = 后端存储的中文字符串，展示 label 走 i18n 词条 -->
            <el-option :label="$t('pilot.level.VLOS')" value="CAAC 视距内驾驶员" />
            <el-option :label="$t('pilot.level.BVLOS')" value="CAAC 超视距驾驶员" />
            <el-option :label="$t('pilot.level.BVLOS_TEACHER')" value="CAAC 超视距教员" />
            <el-option :label="$t('pilot.level.INSTRUCTOR')" value="CAAC 教员级" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('pilot.licenseExpire')"><el-date-picker v-model="form.licenseExpire" type="date" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveItem">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 体检记录对话框 -->
    <el-dialog v-model="medicalDialog" :title="$t('pilot.medicalRecord')" width="640px">
      <el-table :data="medicalList" border size="small">
        <el-table-column prop="examDate" :label="$t('pilot.examDate')" width="110">
          <template #default="{ row }">{{ fmtDate(row.examDate) }}</template>
        </el-table-column>
        <el-table-column prop="examOrg" :label="$t('pilot.examOrg')" min-width="140" />
        <el-table-column prop="examResult" :label="$t('common.result')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.examResult==='PASS' ? 'success' : 'danger'" size="small">
              {{ row.examResult==='PASS' ? $t('pilot.pass') : $t('pilot.fail') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expireDate" :label="$t('pilot.validUntil')" width="110">
          <template #default="{ row }">{{ fmtDate(row.expireDate) }}</template>
        </el-table-column>
        <el-table-column prop="source" :label="$t('pilot.source')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.source==='CENTER' ? 'primary' : 'info'" size="small">
              {{ row.source==='CENTER' ? $t('pilot.sourceCenter') : $t('pilot.sourceManual') }}
            </el-tag>
          </template>
        </el-table-column>
        <template #empty><el-empty :description="$t('pilot.emptyMedical')" :image-size="60" /></template>
      </el-table>
      <div style="margin-top:12px">
        <el-form :model="medicalForm" :label-width="labelWidth" inline>
          <el-form-item :label="$t('pilot.examDate')"><el-date-picker v-model="medicalForm.examDate" type="date" /></el-form-item>
          <el-form-item :label="$t('pilot.examOrg')"><el-input v-model="medicalForm.examOrg" style="width:170px" /></el-form-item>
          <el-form-item :label="$t('common.result')">
            <el-select v-model="medicalForm.examResult" style="width:110px">
              <el-option :label="$t('pilot.pass')" value="PASS" />
              <el-option :label="$t('pilot.fail')" value="FAIL" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('pilot.validUntil')"><el-date-picker v-model="medicalForm.expireDate" type="date" /></el-form-item>
          <el-form-item>
            <el-button type="primary" @click="uploadMedical">{{ $t('pilot.upload') }}</el-button>
            <el-button :loading="syncing" @click="syncMedicalCenter">{{ $t('pilot.syncCenter') }}</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { pilotApi, type UavPilot, type UavPilotMedical } from '@/api/pilot'
import { usePaging } from '@/composables/usePaging'
import { useDict } from '@/composables/useDict'
import { fmtDate } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import TablePagination from '@/components/TablePagination.vue'
import { locale } from '@/locales'

const { t } = useI18n()
// 表单标签宽度：中英文标签长度不同，英文适当放宽
const labelWidth = computed(() => (locale.value === 'en' ? '140px' : '100px'))

const list = ref<UavPilot[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const medicalDialog = ref(false)
const editing = ref<UavPilot>({})
const currentPilotId = ref(0)
const medicalList = ref<UavPilotMedical[]>([])
const syncing = ref(false)
const form = ref<UavPilot>({ pilotName:'', idNumber:'', phone:'', licenseNo:'', licenseLevel:'CAAC 视距内驾驶员', licenseExpire:'' })
const medicalForm = ref<UavPilotMedical>({ pilotId:0, examDate:'', examOrg:'', examResult:'PASS', expireDate:'' })
const { page, size, total, paged } = usePaging(list)
const { label: statusLabel } = useDict('pilot_status')

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try { const res = await pilotApi.list(); list.value = (res as any).data || [] }
  finally { loading.value = false }
}

function showCreate() { editing.value = {}; form.value = { pilotName:'', idNumber:'', phone:'', licenseNo:'', licenseLevel:'CAAC 视距内驾驶员', licenseExpire:'' }; dialogVisible.value = true }
function editItem(row: UavPilot) { editing.value = { ...row }; form.value = { ...row }; dialogVisible.value = true }

async function saveItem() {
  if (editing.value.id) { await pilotApi.update(editing.value.id!, form.value); ElMessage.success(t('common.updateSuccess')) }
  else { await pilotApi.create(form.value); ElMessage.success(t('common.createSuccess')) }
  dialogVisible.value = false
  loadData()
}

async function suspendItem(row: UavPilot) { await pilotApi.suspend(row.id!); ElMessage.success(t('pilot.suspendOk')); loadData() }
async function reactivateItem(row: UavPilot) { await pilotApi.reactivate(row.id!); ElMessage.success(t('pilot.restoreOk')); loadData() }

async function showMedical(row: UavPilot) {
  currentPilotId.value = row.id!
  const res = await pilotApi.listMedical(row.id!)
  medicalList.value = (res as any).data || []
  medicalForm.value = { pilotId: row.id!, examDate:'', examOrg:'', examResult:'PASS', expireDate:'' }
  medicalDialog.value = true
}

async function uploadMedical() {
  await pilotApi.uploadMedical(currentPilotId.value, medicalForm.value)
  ElMessage.success(t('common.uploadOk'))
  showMedical({ id: currentPilotId.value } as UavPilot)
}

/** 手动触发一次体检中心同步（定时任务每 5 分钟也会自动同步） */
async function syncMedicalCenter() {
  syncing.value = true
  try {
    const res: any = await pilotApi.syncMedicalCenter()
    const s = res?.data || {}
    ElMessage.success(t('pilot.syncOk', { pulled: s.pulled ?? 0, inserted: s.inserted ?? 0 }))
    showMedical({ id: currentPilotId.value } as UavPilot)
  } finally {
    syncing.value = false
  }
}

</script>
