<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">{{ $t('route.title') }}</h2>
        <div class="page-subtitle">{{ $t('route.subtitle', { total }) }}</div>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="showCreate">{{ $t('route.create') }}</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="paged" border stripe v-loading="loading">
        <el-table-column prop="routeName" :label="$t('route.name')" min-width="170" />
        <el-table-column prop="routeCode" :label="$t('common.code')" width="150" />
        <el-table-column :label="$t('route.waypointCount')" width="90">
          <template #default="{ row }">{{ waypointCount(row.waypoints) }}</template>
        </el-table-column>
        <el-table-column prop="corridorWidthM" :label="$t('route.corridorWidth')" width="120" />
        <el-table-column prop="direction" :label="$t('route.direction')" width="90">
          <template #default="{ row }">
            <el-tag size="small">{{ directionLabel(row.direction) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="$t('common.desc')" min-width="200" />
        <el-table-column prop="isActive" :label="$t('common.status')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'info'" size="small">{{ row.isActive ? $t('common.enabled') : $t('common.disabled') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.operation')" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="editItem(row)">{{ $t('common.edit') }}</el-button>
            <el-button size="small" type="danger" @click="deleteItem(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty :description="$t('route.empty')" /></template>
      </el-table>
      <div class="table-footer">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
          layout="total, prev, pager, next" background />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing.id ? $t('route.edit') : $t('route.create')" width="620px">
      <el-form :model="form" :label-width="labelWidth">
        <el-form-item :label="$t('route.name')"><el-input v-model="form.routeName" :placeholder="$t('route.namePh')" /></el-form-item>
        <el-form-item :label="$t('common.code')"><el-input v-model="form.routeCode" :placeholder="$t('route.codePh')" /></el-form-item>
        <el-form-item :label="$t('route.direction')">
          <el-select v-model="form.direction" style="width:100%">
            <el-option v-for="d in directionItems" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('route.corridorWidth')"><el-input-number v-model="form.corridorWidthM" :min="50" :max="2000" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('route.routeMap')">
          <div style="width:100%">
            <MapPicker ref="mapRef" mode="route" v-model="waypoints" :editable="true" height="300px" />
            <div style="margin-top:6px;display:flex;gap:8px;align-items:center">
              <el-button size="small" @click="mapRef?.undo()">{{ $t('map.undoLast') }}</el-button>
              <el-button size="small" @click="mapRef?.clear()">{{ $t('map.clear') }}</el-button>
              <span style="color:var(--el-text-color-secondary);font-size:12px">{{ $t('map.routeHint') }}</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item :label="$t('route.waypointList')">
          <div class="wp-editor">
            <div v-for="(wp, i) in waypoints" :key="i" class="wp-row">
              <span class="wp-idx">{{ i + 1 }}</span>
              <el-input-number v-model="wp[0]" :precision="5" :step="0.01" :controls="false" :placeholder="$t('map.lon')" style="width:150px" />
              <el-input-number v-model="wp[1]" :precision="5" :step="0.01" :controls="false" :placeholder="$t('map.lat')" style="width:150px" />
              <el-button size="small" text type="danger" @click="waypoints.splice(i, 1)">{{ $t('common.delete') }}</el-button>
            </div>
            <el-button size="small" @click="waypoints.push([116.4, 39.9])">{{ $t('route.addWaypoint') }}</el-button>
            <span class="wp-hint">{{ $t('route.wpHint') }}</span>
          </div>
        </el-form-item>
        <el-form-item :label="$t('common.desc')"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item :label="$t('common.status')"><el-switch v-model="form.isActive" :active-text="$t('common.enabled')" :inactive-text="$t('common.disabled')" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="precheck" :loading="checking">{{ $t('map.precheck') }}</el-button>
        <el-button @click="dialogVisible=false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveItem">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { routeApi, type UavRoute } from '@/api/route'
import MapPicker from '@/components/MapPicker.vue'
import { ref as vueRef } from 'vue'
import { usePaging } from '@/composables/usePaging'
import { useDict } from '@/composables/useDict'
import { locale } from '@/locales'

const { t } = useI18n()
// 表单标签宽度：中英文标签长度不同，英文适当放宽
const labelWidth = computed(() => (locale.value === 'en' ? '150px' : '100px'))

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
    v.length ? ElMessage.error(t('map.precheckFail', { zones: v.join('；') })) : ElMessage.success(t('map.precheckOkRoute'))
  } finally { checking.value = false }
}

async function saveItem() {
  if (!form.value.routeName) { ElMessage.warning(t('route.needName')); return }
  if (waypoints.value.length < 2) { ElMessage.warning(t('route.needWaypoints')); return }
  form.value.waypoints = JSON.stringify(waypoints.value)
  if (editing.value.id) { await routeApi.update(editing.value.id!, form.value); ElMessage.success(t('common.updateSuccess')) }
  else { await routeApi.create(form.value); ElMessage.success(t('common.createSuccess')) }
  dialogVisible.value = false
  loadData()
}

async function deleteItem(row: UavRoute) {
  await ElMessageBox.confirm(t('route.deleteConfirm', { name: row.routeName }), t('common.confirmTitle'), { type: 'warning' })
  await routeApi.remove(row.id!)
  ElMessage.success(t('common.deleteSuccess'))
  loadData()
}
</script>

<style scoped>
.wp-editor { width: 100%; }
.wp-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.wp-idx { width: 22px; color: var(--el-text-color-secondary); font-size: 12px; }
.wp-hint { margin-left: 10px; color: var(--el-text-color-secondary); font-size: 12px; }
</style>
