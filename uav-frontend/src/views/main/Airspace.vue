<template>
  <div class="page-container">
    <div class="page-header">
      <h2>空域管理</h2>
      <el-button type="primary" @click="showCreate">新增空域</el-button>
    </div>

    <el-table :data="list" border stripe v-loading="loading">
      <el-table-column prop="name" label="名称" width="160" />
      <el-table-column prop="type" label="类型" width="120">
        <template #default="{ row }">
          <el-tag :type="typeTag(row.type)">{{ typeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lowerAltitude" label="底高(m)" width="100" />
      <el-table-column prop="upperAltitude" label="顶高(m)" width="100" />
      <el-table-column prop="effectiveTime" label="生效时间" width="160" />
      <el-table-column prop="expireTime" label="失效时间" width="160" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="editItem(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="deleteItem(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing.id ? '编辑空域' : '新增空域'" width="550px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width:100%">
            <el-option label="管制空域" value="CONTROLLED" />
            <el-option label="适飞空域" value="SUITABLE" />
            <el-option label="禁飞区" value="NO_FLY" />
            <el-option label="临时禁飞" value="TEMP_NO_FLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="底高(m)"><el-input-number v-model="form.lowerAltitude" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="顶高(m)"><el-input-number v-model="form.upperAltitude" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="多边形(GeoJSON)"><el-input v-model="form.polygon" type="textarea" :rows="3" placeholder='{"type":"Polygon","coordinates":[[[lng,lat],...]]}' /></el-form-item>
        <el-form-item label="生效时间"><el-date-picker v-model="form.effectiveTime" type="datetime" style="width:100%" /></el-form-item>
        <el-form-item label="失效时间"><el-date-picker v-model="form.expireTime" type="datetime" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="saveItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { airspaceApi, type Airspace } from '@/api/airspace'

const list = ref<Airspace[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref<Airspace>({})

const form = ref<Airspace>({
  name: '', type: 'SUITABLE', lowerAltitude: 0, upperAltitude: 500,
  polygon: '', effectiveTime: '', expireTime: ''
})

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try { const res = await airspaceApi.list(); list.value = (res as any).data || [] }
  finally { loading.value = false }
}

function showCreate() { editing.value = {}; form.value = { name:'',type:'SUITABLE',lowerAltitude:0,upperAltitude:500,polygon:'',effectiveTime:'',expireTime:'' }; dialogVisible.value = true }
function editItem(row: Airspace) { editing.value = { ...row }; form.value = { ...row }; dialogVisible.value = true }

async function saveItem() {
  if (editing.value.id) { await airspaceApi.update(editing.value.id!, form.value); ElMessage.success('更新成功') }
  else { await airspaceApi.create(form.value); ElMessage.success('创建成功') }
  dialogVisible.value = false
  loadData()
}

async function deleteItem(row: Airspace) {
  await ElMessageBox.confirm('确定删除该空域？', '确认', { type: 'warning' })
  await airspaceApi.delete(row.id!)
  ElMessage.success('已删除')
  loadData()
}

function typeTag(t: string): any {
  const map: Record<string,string> = { CONTROLLED:'warning', SUITABLE:'success', NO_FLY:'danger', TEMP_NO_FLY:'danger' }
  return map[t] || 'info'
}
function typeLabel(t: string) {
  const map: Record<string,string> = { CONTROLLED:'管制', SUITABLE:'适飞', NO_FLY:'禁飞', TEMP_NO_FLY:'临时禁飞' }
  return map[t] || t
}
</script>

<style scoped>
.page-container { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
