<template>
  <div class="page-container">
    <PageHeader :title="$t('airspace.title')" :subtitle="$t('airspace.subtitle', { total })">
      <template #actions>
        <el-button type="primary" @click="showCreate">{{ $t('airspace.create') }}</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="table-card">
      <el-table :data="paged" border stripe v-loading="loading">
        <el-table-column prop="airspaceName" :label="$t('common.name')" min-width="150" />
        <el-table-column prop="airspaceCode" :label="$t('common.code')" width="150" />
        <el-table-column prop="airspaceType" :label="$t('common.type')" width="110">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.airspaceType)">{{ typeLabel(row.airspaceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="altFloorM" :label="$t('airspace.floorAlt')" width="90" />
        <el-table-column prop="altCeilingM" :label="$t('airspace.ceilingAlt')" width="90" />
        <el-table-column prop="startTime" :label="$t('airspace.start')" width="150">
          <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column prop="endTime" :label="$t('airspace.end')" width="150">
          <template #default="{ row }">{{ fmtTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column prop="isActive" :label="$t('common.status')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'info'">{{ row.isActive ? $t('airspace.active') : $t('common.disabled') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.operation')" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="editItem(row)">{{ $t('common.edit') }}</el-button>
            <el-button size="small" type="danger" @click="deleteItem(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty :description="$t('airspace.empty')" /></template>
      </el-table>
      <TablePagination v-model:page="page" v-model:size="size" :total="total" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing.id ? $t('airspace.edit') : $t('airspace.create')" width="550px">
      <el-form :model="form" :label-width="labelWidth">
        <el-form-item :label="$t('common.name')"><el-input v-model="form.airspaceName" /></el-form-item>
        <el-form-item :label="$t('common.type')">
          <el-select v-model="form.airspaceType" style="width:100%">
            <el-option v-for="d in airspaceTypeItems" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('airspace.floorAlt')"><el-input-number v-model="form.altFloorM" :min="0" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('airspace.ceilingAlt')"><el-input-number v-model="form.altCeilingM" :min="0" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('airspace.area')">
          <div style="width:100%">
            <MapPicker
              ref="mapRef"
              mode="polygon"
              v-model="polygon"
              :editable="true"
              height="300px"
            />
            <div style="margin-top:6px;display:flex;gap:8px;align-items:center">
              <el-button size="small" @click="mapRef?.undo()">{{ $t('map.undoLast') }}</el-button>
              <el-button size="small" @click="mapRef?.clear()">{{ $t('map.clear') }}</el-button>
              <span style="color:var(--el-text-color-secondary);font-size:12px">
                {{ $t('map.addVerticesHint') }}
              </span>
            </div>
          </div>
        </el-form-item>
        <el-form-item :label="$t('airspace.start')"><el-date-picker v-model="form.startTime" type="datetime" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('airspace.end')"><el-date-picker v-model="form.endTime" type="datetime" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
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
import { airspaceApi, type Airspace } from '@/api/airspace'
import { usePaging } from '@/composables/usePaging'
import { useDict } from '@/composables/useDict'
import MapPicker from '@/components/MapPicker.vue'
import { ref as vueRef } from 'vue'
import { fmtDateTime as fmtTime } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import TablePagination from '@/components/TablePagination.vue'
import { locale } from '@/locales'

const { t } = useI18n()
// 表单标签宽度：中英文标签长度不同，英文适当放宽
const labelWidth = computed(() => (locale.value === 'en' ? '150px' : '110px'))

const list = ref<Airspace[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref<Airspace>({})
const { page, size, total, paged } = usePaging(list)
const mapRef = vueRef(null)
const polygon = vueRef([])

function geoJsonToPolygon(geoJson?: string): number[][] {
  try {
    const ring = JSON.parse(geoJson || '[]')?.coordinates?.[0] || []
    const pts = ring.slice(0, -1) // 去掉闭合点
    return pts.map((p: any) => [+p[0], +p[1]])
  } catch { return [] }
}
function polygonToGeoJson(pts: number[][]): string {
  if (pts.length < 3) return ''
  const ring = [...pts, pts[0]]
  return JSON.stringify({ type: 'Polygon', coordinates: [ring] })
}

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

function showCreate() {
  editing.value = {}
  form.value = { airspaceName:'', airspaceType:'DEMO', altFloorM:0, altCeilingM:120, geoJson:'', startTime:'', endTime:'' }
  polygon.value = []
  dialogVisible.value = true
}
function editItem(row: Airspace) {
  editing.value = { ...row }
  form.value = { ...row }
  polygon.value = geoJsonToPolygon(row.geoJson)
  dialogVisible.value = true
}

async function saveItem() {
  form.value.geoJson = polygonToGeoJson(polygon.value)
  if (polygon.value.length >= 3 && !form.value.geoJson) { ElMessage.warning(t('airspace.needVertices')); return }
  if (editing.value.id) { await airspaceApi.update(editing.value.id!, form.value); ElMessage.success(t('common.updateSuccess')) }
  else { await airspaceApi.create(form.value); ElMessage.success(t('common.createSuccess')) }
  dialogVisible.value = false
  loadData()
}

async function deleteItem(row: Airspace) {
  await ElMessageBox.confirm(t('airspace.deleteConfirm'), t('common.confirmTitle'), { type: 'warning' })
  await airspaceApi.delete(row.id!)
  ElMessage.success(t('common.deleteSuccess'))
  loadData()
}

function typeTag(t: string): any {
  const map: Record<string,string> = { CONTROL:'danger', NO_FLY:'danger', OPERATION:'warning', CORRIDOR:'success', DEMO:'primary', CONTROLLED:'warning', SUITABLE:'success', TEMP_NO_FLY:'danger' }
  return map[t] || 'info'
}
const { items: airspaceTypeItems, label: typeLabel } = useDict('airspace_type')
</script>
