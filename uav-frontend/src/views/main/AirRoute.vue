<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">航路管理</h2>
        <div class="page-subtitle">低空走廊 / 巡检航路划设 · 共 {{ total }} 条航路</div>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="showCreate">新增航路</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="paged" border stripe v-loading="loading">
        <el-table-column prop="routeName" label="航路名称" min-width="170" />
        <el-table-column prop="routeCode" label="编码" width="150" />
        <el-table-column label="航点数" width="90">
          <template #default="{ row }">{{ waypointCount(row.waypoints) }}</template>
        </el-table-column>
        <el-table-column prop="corridorWidthM" label="走廊宽度(m)" width="120" />
        <el-table-column prop="direction" label="方向" width="90">
          <template #default="{ row }">
            <el-tag size="small">{{ directionLabel(row.direction) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" />
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
        <template #empty><el-empty description="暂无航路" /></template>
      </el-table>
      <div class="table-footer">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
          layout="total, prev, pager, next" background />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing.id ? '编辑航路' : '新增航路'" width="620px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="航路名称"><el-input v-model="form.routeName" placeholder="如 通州-亦庄低空走廊" /></el-form-item>
        <el-form-item label="编码"><el-input v-model="form.routeCode" placeholder="如 RT-TZ-YZ-01" /></el-form-item>
        <el-form-item label="方向">
          <el-select v-model="form.direction" style="width:100%">
            <el-option v-for="d in directionItems" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="走廊宽度(m)"><el-input-number v-model="form.corridorWidthM" :min="50" :max="2000" style="width:100%" /></el-form-item>
        <el-form-item label="航路地图">
          <div style="width:100%">
            <MapPicker ref="mapRef" mode="route" v-model="waypoints" :editable="true" height="300px" />
            <div style="margin-top:6px;display:flex;gap:8px;align-items:center">
              <el-button size="small" @click="mapRef?.undo()">撤销上一点</el-button>
              <el-button size="small" @click="mapRef?.clear()">清空</el-button>
              <span style="color:var(--el-text-color-secondary);font-size:12px">点击地图按序添加航点</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="航点列表">
          <div class="wp-editor">
            <div v-for="(wp, i) in waypoints" :key="i" class="wp-row">
              <span class="wp-idx">{{ i + 1 }}</span>
              <el-input-number v-model="wp[0]" :precision="5" :step="0.01" :controls="false" placeholder="经度" style="width:150px" />
              <el-input-number v-model="wp[1]" :precision="5" :step="0.01" :controls="false" placeholder="纬度" style="width:150px" />
              <el-button size="small" text type="danger" @click="waypoints.splice(i, 1)">删除</el-button>
            </div>
            <el-button size="small" @click="waypoints.push([116.4, 39.9])">添加航点</el-button>
            <span class="wp-hint">按序构成航线，保存前可做禁飞区预检</span>
          </div>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
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
import { routeApi, type UavRoute } from '@/api/route'
import MapPicker from '@/components/MapPicker.vue'
import { ref as vueRef } from 'vue'
import { usePaging } from '@/composables/usePaging'
import { useDict } from '@/composables/useDict'

const list = ref<UavRoute[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref<UavRoute>({})
const checking = ref(false)
const waypoints = ref<number[][]>([])
const mapRef = vueRef(null)
const { page, size, total, paged } = usePaging(list)
const { items: directionItems, label: directionLabel } = useDict('route_direction')

const form = ref<UavRoute>({ routeName: '', routeCode: '', direction: 'ONE_WAY', corridorWidthM: 200, isActive: true, description: '', waypoints: '[]' })

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try { list.value = ((await routeApi.list() as any).data) || [] }
  finally { loading.value = false }
}

function parseWp(json?: string): number[][] {
  try { return JSON.parse(json || '[]') } catch { return [] }
}
function waypointCount(json?: string) { return parseWp(json).length }

function showCreate() {
  editing.value = {}
  form.value = { routeName: '', routeCode: '', direction: 'ONE_WAY', corridorWidthM: 200, isActive: true, description: '', waypoints: '[]' }
  waypoints.value = [[116.4, 39.9]]
  dialogVisible.value = true
}
function editItem(row: UavRoute) {
  editing.value = { ...row }
  form.value = { ...row }
  waypoints.value = parseWp(row.waypoints)
  dialogVisible.value = true
}

async function precheck() {
  checking.value = true
  try {
    const res = await routeApi.check(waypoints.value)
    const v = (res as any).data || []
    v.length ? ElMessage.error('禁飞区预检未通过：' + v.join('；')) : ElMessage.success('预检通过：航路未穿越禁飞区')
  } finally { checking.value = false }
}

async function saveItem() {
  if (!form.value.routeName) { ElMessage.warning('请填写航路名称'); return }
  if (waypoints.value.length < 2) { ElMessage.warning('航路至少需要 2 个航点'); return }
  form.value.waypoints = JSON.stringify(waypoints.value)
  if (editing.value.id) { await routeApi.update(editing.value.id!, form.value); ElMessage.success('更新成功') }
  else { await routeApi.create(form.value); ElMessage.success('创建成功') }
  dialogVisible.value = false
  loadData()
}

async function deleteItem(row: UavRoute) {
  await ElMessageBox.confirm(`确定删除航路「${row.routeName}」？`, '确认', { type: 'warning' })
  await routeApi.remove(row.id!)
  ElMessage.success('已删除')
  loadData()
}
</script>

<style scoped>
.wp-editor { width: 100%; }
.wp-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.wp-idx { width: 22px; color: var(--el-text-color-secondary); font-size: 12px; }
.wp-hint { margin-left: 10px; color: var(--el-text-color-secondary); font-size: 12px; }
</style>
