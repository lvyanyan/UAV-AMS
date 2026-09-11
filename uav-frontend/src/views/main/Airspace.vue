<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">空域管理</h2>
        <div class="page-subtitle">管制区 / 作业区 / 走廊等空域划设与启停管理 · 共 {{ total }} 个</div>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="showCreate">新增空域</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="paged" border stripe v-loading="loading">
        <el-table-column prop="airspaceName" label="名称" min-width="150" />
        <el-table-column prop="airspaceCode" label="编码" width="150" />
        <el-table-column prop="airspaceType" label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.airspaceType)">{{ typeLabel(row.airspaceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="altFloorM" label="底高(m)" width="90" />
        <el-table-column prop="altCeilingM" label="顶高(m)" width="90" />
        <el-table-column prop="startTime" label="生效时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column prop="endTime" label="失效时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column prop="isActive" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'info'">{{ row.isActive ? '启用中' : '已停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="editItem(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteItem(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无空域数据" /></template>
      </el-table>
      <div class="table-footer">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
          layout="total, prev, pager, next, sizes" :page-sizes="[10, 20, 50]" background />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing.id ? '编辑空域' : '新增空域'" width="550px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="名称"><el-input v-model="form.airspaceName" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.airspaceType" style="width:100%">
            <el-option v-for="d in airspaceTypeItems" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="底高(m)"><el-input-number v-model="form.altFloorM" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="顶高(m)"><el-input-number v-model="form.altCeilingM" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="多边形(GeoJSON)"><el-input v-model="form.geoJson" type="textarea" :rows="3" placeholder='{"type":"Polygon","coordinates":[[[lng,lat],...]]}' /></el-form-item>
        <el-form-item label="生效时间"><el-date-picker v-model="form.startTime" type="datetime" style="width:100%" /></el-form-item>
        <el-form-item label="失效时间"><el-date-picker v-model="form.endTime" type="datetime" style="width:100%" /></el-form-item>
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
import { usePaging } from '@/composables/usePaging'
import { useDict } from '@/composables/useDict'
import { fmtDateTime as fmtTime } from '@/utils/format'

const list = ref<Airspace[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref<Airspace>({})
const { page, size, total, paged } = usePaging(list)

const form = ref<Airspace>({
  airspaceName: '', airspaceType: 'DEMO', altFloorM: 0, altCeilingM: 120,
  geoJson: '', startTime: '', endTime: ''
})

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try { const res = await airspaceApi.list(); list.value = (res as any).data || [] }
  finally { loading.value = false }
}

function showCreate() { editing.value = {}; form.value = { airspaceName:'', airspaceType:'DEMO', altFloorM:0, altCeilingM:120, geoJson:'', startTime:'', endTime:'' }; dialogVisible.value = true }
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
  const map: Record<string,string> = { CONTROL:'danger', NO_FLY:'danger', OPERATION:'warning', CORRIDOR:'success', DEMO:'primary', CONTROLLED:'warning', SUITABLE:'success', TEMP_NO_FLY:'danger' }
  return map[t] || 'info'
}
const { items: airspaceTypeItems, label: typeLabel } = useDict('airspace_type')
</script>
