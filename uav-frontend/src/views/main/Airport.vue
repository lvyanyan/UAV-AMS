<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">{{ $t('airport.title') }}</h2>
        <div class="page-subtitle">{{ $t('airport.subtitle', { total }) }}</div>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="showCreate">{{ $t('airport.create') }}</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="paged" border stripe v-loading="loading">
        <el-table-column prop="airportName" :label="$t('common.name')" min-width="180" />
        <el-table-column prop="airportCode" :label="$t('common.code')" width="130" />
        <el-table-column prop="airportType" :label="$t('common.type')" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ typeLabel(row.airportType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lon" :label="$t('map.lon')" width="110" />
        <el-table-column prop="lat" :label="$t('map.lat')" width="110" />
        <el-table-column prop="capacity" :label="$t('airport.capacity')" width="90" />
        <el-table-column prop="remark" :label="$t('airport.remark')" min-width="180" />
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
        <template #empty><el-empty :description="$t('airport.empty')" /></template>
      </el-table>
      <div class="table-footer">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
          layout="total, prev, pager, next" background />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing.id ? $t('airport.edit') : $t('airport.create')" width="520px">
      <el-form :model="form" :label-width="labelWidth">
        <el-form-item :label="$t('common.name')"><el-input v-model="form.airportName" :placeholder="$t('airport.namePh')" /></el-form-item>
        <el-form-item :label="$t('common.code')"><el-input v-model="form.airportCode" :placeholder="$t('airport.codePh')" /></el-form-item>
        <el-form-item :label="$t('common.type')">
          <el-select v-model="form.airportType" style="width:100%">
            <el-option v-for="d in typeItems" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('airport.pickPoint')">
          <div style="width:100%">
            <MapPicker mode="point" v-model="airportPoint" :editable="true" height="260px" @update:model-value="onPointPicked" />
            <div style="margin-top:6px;color:var(--el-text-color-secondary);font-size:12px">{{ $t('map.pointHint') }}</div>
          </div>
        </el-form-item>
        <el-form-item :label="$t('map.lon')"><el-input-number v-model="form.lon" :precision="5" :step="0.01" :controls="false" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('map.lat')"><el-input-number v-model="form.lat" :precision="5" :step="0.01" :controls="false" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('airport.capacity')"><el-input-number v-model="form.capacity" :min="0" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('airport.remark')"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
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
import { airportApi, type UavAirport } from '@/api/airport'
import MapPicker from '@/components/MapPicker.vue'
import { usePaging } from '@/composables/usePaging'
import { useDict } from '@/composables/useDict'
import { locale } from '@/locales'

const { t } = useI18n()
// 表单标签宽度：中英文标签长度不同，英文适当放宽
const labelWidth = computed(() => (locale.value === 'en' ? '140px' : '90px'))

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
  if (form.value.lon == null || form.value.lat == null) { ElMessage.warning(t('airport.needCoord')); return }
  checking.value = true
  try {
    const res = await airportApi.check(form.value.lon, form.value.lat)
    const v = (res as any).data || []
    v.length ? ElMessage.error(t('map.precheckFail', { zones: v.join('；') })) : ElMessage.success(t('map.precheckOkPoint'))
  } finally { checking.value = false }
}

async function saveItem() {
  if (!form.value.airportName) { ElMessage.warning(t('airport.needName')); return }
  if (editing.value.id) { await airportApi.update(editing.value.id!, form.value); ElMessage.success(t('common.updateSuccess')) }
  else { await airportApi.create(form.value); ElMessage.success(t('common.createSuccess')) }
  dialogVisible.value = false
  loadData()
}

async function deleteItem(row: UavAirport) {
  await ElMessageBox.confirm(t('airport.deleteConfirm', { name: row.airportName }), t('common.confirmTitle'), { type: 'warning' })
  await airportApi.remove(row.id!)
  ElMessage.success(t('common.deleteSuccess'))
  loadData()
}
</script>
