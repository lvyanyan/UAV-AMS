<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">飞手管理</h2>
        <div class="page-subtitle">执照资质 / 体检记录 / 停飞恢复 · 共 {{ total }} 名飞手</div>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="showCreate">新增飞手</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="paged" border stripe v-loading="loading">
        <el-table-column prop="pilotName" label="姓名" width="100" />
        <el-table-column prop="idNumber" label="身份证号" width="180" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="licenseNo" label="执照号" width="180" />
        <el-table-column prop="licenseLevel" label="执照等级" width="160" />
        <el-table-column prop="licenseExpire" label="执照有效期" width="130">
          <template #default="{ row }">{{ fmtDate(row.licenseExpire) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status==='ACTIVE'?'success':'danger'">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="editItem(row)">编辑</el-button>
            <el-button size="small" @click="showMedical(row)">体检</el-button>
            <el-button v-if="row.status==='ACTIVE'" size="small" type="danger" @click="suspendItem(row)">停飞</el-button>
            <el-button v-else size="small" type="success" @click="reactivateItem(row)">恢复</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无飞手数据" /></template>
      </el-table>
      <div class="table-footer">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
          layout="total, prev, pager, next, sizes" :page-sizes="[10, 20, 50]" background />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing.id?'编辑飞手':'新增飞手'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="姓名"><el-input v-model="form.pilotName" /></el-form-item>
        <el-form-item label="身份证号"><el-input v-model="form.idNumber" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="执照号"><el-input v-model="form.licenseNo" /></el-form-item>
        <el-form-item label="执照等级">
          <el-select v-model="form.licenseLevel" style="width:100%">
            <el-option label="视距内驾驶员" value="视距内驾驶员" />
            <el-option label="超视距驾驶员" value="超视距驾驶员" />
            <el-option label="超视距教员" value="超视距教员" />
          </el-select>
        </el-form-item>
        <el-form-item label="执照有效期"><el-date-picker v-model="form.licenseExpire" type="date" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="saveItem">保存</el-button>
      </template>
    </el-dialog>

    <!-- 体检记录对话框 -->
    <el-dialog v-model="medicalDialog" title="体检记录" width="500px">
      <el-table :data="medicalList" border size="small">
        <el-table-column prop="examDate" label="体检日期" width="120" />
        <el-table-column prop="examOrg" label="体检机构" min-width="150" />
        <el-table-column prop="result" label="结果" width="80" />
        <el-table-column prop="expireDate" label="有效期至" width="120" />
        <template #empty><el-empty description="暂无体检记录" :image-size="60" /></template>
      </el-table>
      <div style="margin-top:12px">
        <el-form :model="medicalForm" label-width="80px" inline>
          <el-form-item label="体检日期"><el-date-picker v-model="medicalForm.examDate" type="date" /></el-form-item>
          <el-form-item label="机构"><el-input v-model="medicalForm.examOrg" style="width:160px" /></el-form-item>
          <el-form-item label="结果"><el-select v-model="medicalForm.result" style="width:100px"><el-option label="合格" value="PASS" /><el-option label="不合格" value="FAIL" /></el-select></el-form-item>
          <el-form-item><el-button type="primary" @click="uploadMedical">上传</el-button></el-form-item>
        </el-form>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { pilotApi, type UavPilot, type UavPilotMedical } from '@/api/pilot'
import { usePaging } from '@/composables/usePaging'
import { useDict } from '@/composables/useDict'
import { fmtDate } from '@/utils/format'

const list = ref<UavPilot[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const medicalDialog = ref(false)
const editing = ref<UavPilot>({})
const currentPilotId = ref(0)
const medicalList = ref<UavPilotMedical[]>([])
const form = ref<UavPilot>({ pilotName:'', idNumber:'', phone:'', licenseNo:'', licenseLevel:'视距内驾驶员', licenseExpire:'' })
const medicalForm = ref<UavPilotMedical>({ pilotId:0, examDate:'', examOrg:'', result:'PASS' })
const { page, size, total, paged } = usePaging(list)
const { label: statusLabel } = useDict('pilot_status')

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try { const res = await pilotApi.list(); list.value = (res as any).data || [] }
  finally { loading.value = false }
}

function showCreate() { editing.value = {}; form.value = { pilotName:'', idNumber:'', phone:'', licenseNo:'', licenseLevel:'视距内驾驶员', licenseExpire:'' }; dialogVisible.value = true }
function editItem(row: UavPilot) { editing.value = { ...row }; form.value = { ...row }; dialogVisible.value = true }

async function saveItem() {
  if (editing.value.id) { await pilotApi.update(editing.value.id!, form.value); ElMessage.success('更新成功') }
  else { await pilotApi.create(form.value); ElMessage.success('创建成功') }
  dialogVisible.value = false
  loadData()
}

async function suspendItem(row: UavPilot) { await pilotApi.suspend(row.id!); ElMessage.success('已停飞'); loadData() }
async function reactivateItem(row: UavPilot) { await pilotApi.reactivate(row.id!); ElMessage.success('已恢复'); loadData() }

async function showMedical(row: UavPilot) {
  currentPilotId.value = row.id!
  const res = await pilotApi.listMedical(row.id!)
  medicalList.value = (res as any).data || []
  medicalForm.value = { pilotId: row.id!, examDate:'', examOrg:'', result:'PASS' }
  medicalDialog.value = true
}

async function uploadMedical() {
  await pilotApi.uploadMedical(currentPilotId.value, medicalForm.value)
  ElMessage.success('上传成功')
  showMedical({ id: currentPilotId.value } as UavPilot)
}

</script>
