<template>
  <div class="page-container">
    <div class="page-header">
      <h2>驾驶员管理</h2>
      <el-button type="primary" @click="showCreate">新增驾驶员</el-button>
    </div>

    <el-table :data="list" border stripe v-loading="loading">
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column prop="idCardNo" label="身份证号" width="180" />
      <el-table-column prop="phone" label="电话" width="130" />
      <el-table-column prop="licenseNo" label="执照号" width="140" />
      <el-table-column prop="licenseType" label="执照类型" width="120" />
      <el-table-column prop="licenseExpire" label="执照有效期" width="120" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status==='ACTIVE'?'success':'danger'">{{ row.status==='ACTIVE'?'正常':'停飞' }}</el-tag>
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
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing.id?'编辑驾驶员':'新增驾驶员'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="身份证号"><el-input v-model="form.idCardNo" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="执照号"><el-input v-model="form.licenseNo" /></el-form-item>
        <el-form-item label="执照类型">
          <el-select v-model="form.licenseType" style="width:100%">
            <el-option label="视距内" value="VLOS" />
            <el-option label="超视距" value="BVLOS" />
            <el-option label="教员" value="INSTRUCTOR" />
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

const list = ref<UavPilot[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const medicalDialog = ref(false)
const editing = ref<UavPilot>({})
const currentPilotId = ref(0)
const medicalList = ref<UavPilotMedical[]>([])
const form = ref<UavPilot>({ name:'', idCardNo:'', phone:'', licenseNo:'', licenseType:'VLOS', licenseExpire:'' })
const medicalForm = ref<UavPilotMedical>({ pilotId:0, examDate:'', examOrg:'', result:'PASS' })

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try { const res = await pilotApi.list(); list.value = (res as any).data || [] }
  finally { loading.value = false }
}

function showCreate() { editing.value = {}; form.value = { name:'', idCardNo:'', phone:'', licenseNo:'', licenseType:'VLOS', licenseExpire:'' }; dialogVisible.value = true }
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

<style scoped>
.page-container { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
