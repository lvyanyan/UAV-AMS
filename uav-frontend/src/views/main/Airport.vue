<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">起降场管理</h2>
        <div class="page-subtitle">垂直起降设施台账 · 共 {{ total }} 个起降场</div>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="showCreate">新增起降场</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="paged" border stripe v-loading="loading">
        <el-table-column prop="airportName" label="名称" min-width="180" />
        <el-table-column prop="airportCode" label="编码" width="130" />
        <el-table-column prop="airportType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ typeLabel(row.airportType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lon" label="经度" width="110" />
        <el-table-column prop="lat" label="纬度" width="110" />
        <el-table-column prop="capacity" label="机位容量" width="90" />
        <el-table-column prop="remark" label="备注" min-width="180" />
        <el-table-column prop="isActive" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'info'" size="small">{{ row.isActive ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="editItem(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteItem(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无起降场" /></template>
      </el-table>
      <div class="table-footer">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
          layout="total, prev, pager, next" background />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing.id ? '编辑起降场' : '新增起降场'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.airportName" placeholder="如 亦庄滨河公园起降场" /></el-form-item>
        <el-form-item label="编码"><el-input v-model="form.airportCode" placeholder="如 AP-YZ-01" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.airportType" style="width:100%">
            <el-option v-for="d in typeItems" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="坐标选点">
          <div style="width:100%">
            <MapPicker mode="point" v-model="airportPoint" :editable="true" height="260px" @update:model-value="onPointPicked" />
            <div style="margin-top:6px;color:var(--el-text-color-secondary);font-size:12px">在地图上点击选择起降场位置</div>
          </div>
        </el-form-item>
        <el-form-item label="经度"><el-input-number v-model="form.lon" :precision="5" :step="0.01" :controls="false" style="width:100%" /></el-form-item>
        <el-form-item label="纬度"><el-input-number v-model="form.lat" :precision="5" :step="0.01" :controls="false" style="width:100%" /></el-form-item>
        <el-form-item label="机位容量"><el-input-number v-model="form.capacity" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.isActive" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="precheck" :loading="checking">禁飞区预检</el-button>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="saveItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { airportApi, type UavAirport } from '@/api/airport'
import MapPicker from '@/components/MapPicker.vue'
import { computed } from 'vue'
import { usePaging } from '@/composables/usePaging'
import { useDict } from '@/composables/useDict'

const list = ref<UavAirport[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref<UavAirport>({})
const checking = ref(false)
const { page, size, total, paged } = usePaging(list)
const airportPoint = computed<number[][]>(() =>
  (form.value.lon != null && form.value.lat != null) ? [[form.value.lon, form.value.lat]] : [])
function onPointPicked(v: number[][]) {
  if (v?.length) { form.value.lon = v[0][0]; form.value.lat = v[0][1] }
}
const { items: typeItems, label: typeLabel } = useDict('airport_type')

const form = ref<UavAirport>({ airportName: '', airportCode: '', airportType: 'ALL', lon: 116.4, lat: 39.9, capacity: 4, isActive: true, remark: '' })

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try { list.value = ((await airportApi.list() as any).data) || [] }
  finally { loading.value = false }
}

function showCreate() {
  editing.value = {}
  form.value = { airportName: '', airportCode: '', airportType: 'ALL', lon: 116.4, lat: 39.9, capacity: 4, isActive: true, remark: '' }
  dialogVisible.value = true
}
function editItem(row: UavAirport) {
  editing.value = { ...row }
  form.value = { ...row }
  dialogVisible.value = true
}

async function precheck() {
  if (form.value.lon == null || form.value.lat == null) { ElMessage.warning('请先填写坐标'); return }
  checking.value = true
  try {
    const res = await airportApi.check(form.value.lon, form.value.lat)
    const v = (res as any).data || []
    v.length ? ElMessage.error('禁飞区预检未通过：' + v.join('；')) : ElMessage.success('预检通过：该位置不在禁飞区内')
  } finally { checking.value = false }
}

async function saveItem() {
  if (!form.value.airportName) { ElMessage.warning('请填写名称'); return }
  if (editing.value.id) { await airportApi.update(editing.value.id!, form.value); ElMessage.success('更新成功') }
  else { await airportApi.create(form.value); ElMessage.success('创建成功') }
  dialogVisible.value = false
  loadData()
}

async function deleteItem(row: UavAirport) {
  await ElMessageBox.confirm(`确定删除起降场「${row.airportName}」？`, '确认', { type: 'warning' })
  await airportApi.remove(row.id!)
  ElMessage.success('已删除')
  loadData()
}
</script>
