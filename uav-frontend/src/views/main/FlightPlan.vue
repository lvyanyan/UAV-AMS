<template>
  <div class="page-container">
    <PageHeader :title="$t('plan.title')" :subtitle="$t('plan.subtitle', { total })">
      <template #actions>
        <el-select v-model="statusFilter" :placeholder="$t('common.statusFilter')" clearable style="width:160px" @change="loadPlans">
          <el-option v-for="s in STATUS_OPTIONS" :key="s" :label="$t('dict.plan_status.' + s)" :value="s" />
        </el-select>
        <el-button type="primary" @click="showCreateDialog">{{ $t('plan.create') }}</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="table-card">
      <el-table :data="paged" border stripe v-loading="loading" style="width:100%">
        <el-table-column prop="planCode" :label="$t('plan.code')" width="140" />
        <el-table-column prop="pilotName" :label="$t('plan.pilot')" width="100">
          <template #default="{ row }">{{ pilotNames[row.pilotId] || row.pilotName || '--' }}</template>
        </el-table-column>
        <el-table-column prop="droneSn" :label="$t('plan.sn')" width="130" />
        <el-table-column prop="departure" :label="$t('plan.departure')" min-width="120" />
        <el-table-column prop="destination" :label="$t('plan.destination')" min-width="120" />
        <el-table-column prop="plannedStart" :label="$t('plan.start')" width="150">
          <template #default="{ row }">{{ fmtTime(row.plannedStart) }}</template>
        </el-table-column>
        <el-table-column prop="plannedEnd" :label="$t('plan.end')" width="150">
          <template #default="{ row }">{{ fmtTime(row.plannedEnd) }}</template>
        </el-table-column>
        <el-table-column prop="altCeilingM" :label="$t('plan.altCeiling')" width="110" />
        <el-table-column prop="planStatus" :label="$t('common.status')" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.planStatus)" :style="statusTagStyle(row.planStatus)">{{ statusLabel(row.planStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.operation')" width="340" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showDetail(row)">{{ $t('common.detail') }}</el-button>
            <el-button v-if="row.planStatus==='DRAFT'" size="small" type="success" @click="submitPlan(row)">{{ $t('common.submit') }}</el-button>
            <el-button v-if="pendingLevel(row.planStatus)" size="small" type="warning" @click="approvePlan(row)">{{ $t('plan.approve') }}</el-button>
            <el-button v-if="pendingLevel(row.planStatus)" size="small" type="danger" @click="rejectPlan(row)">{{ $t('plan.reject') }}</el-button>
            <el-button v-if="row.planStatus==='APPROVED'" size="small" type="primary" @click="releasePlan(row)">{{ $t('plan.release') }}</el-button>
            <el-button v-if="row.planStatus==='RELEASED'" size="small" type="primary" @click="takeoffPlan(row)">{{ $t('plan.takeoff') }}</el-button>
            <el-button v-if="row.planStatus==='RELEASED'" size="small" type="danger" @click="abortPlan(row)">{{ $t('plan.cancelPlan') }}</el-button>
            <el-button v-if="row.planStatus==='IN_FLIGHT'" size="small" type="warning" @click="rtlPlan(row)">{{ $t('plan.rtl') }}</el-button>
            <el-button v-if="row.planStatus==='IN_FLIGHT'" size="small" type="danger" @click="abortPlan(row)">{{ $t('plan.abort') }}</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty :description="$t('plan.empty')" /></template>
      </el-table>
      <TablePagination v-model:page="page" v-model:size="size" :total="total" />
    </el-card>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingPlan.id ? $t('plan.edit') : $t('plan.create')" width="600px">
      <el-form :model="form" :label-width="labelWidth">
        <el-form-item :label="$t('plan.code')"><el-input v-model="form.planCode" :placeholder="$t('plan.autoGen')" disabled /></el-form-item>
        <el-form-item :label="$t('plan.sn')"><el-input v-model="form.droneSn" :placeholder="$t('plan.snPlaceholder')" /></el-form-item>
        <el-form-item :label="$t('plan.pilot')">
          <el-select v-model="form.pilotId" style="width:100%" :placeholder="$t('plan.selectPilot')">
            <el-option v-for="pl in pilots" :key="pl.id" :label="pl.pilotName" :value="pl.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('plan.route')">
          <el-select v-model="form.routeId" style="width:100%" :placeholder="$t('plan.selectRoute')" clearable>
            <el-option v-for="r in routes" :key="r.id" :label="`${r.routeName} (${r.routeCode})`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('plan.departure')">
          <el-select v-model="form.departure" style="width:100%" :placeholder="$t('plan.selectDeparture')" filterable allow-create>
            <el-option v-for="a in airports" :key="a.id" :label="`${a.airportName} (${typeLabel(a.airportType)})`" :value="a.airportName" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('plan.destination')">
          <el-select v-model="form.destination" style="width:100%" :placeholder="$t('plan.selectLanding')" filterable allow-create>
            <el-option v-for="a in airports" :key="a.id" :label="`${a.airportName} (${typeLabel(a.airportType)})`" :value="a.airportName" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('plan.start')"><el-date-picker v-model="form.plannedStart" type="datetime" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('plan.end')"><el-date-picker v-model="form.plannedEnd" type="datetime" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('plan.altCeiling')"><el-input-number v-model="form.altCeilingM" :min="0" :max="5000" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('plan.purpose')"><el-input v-model="form.flightPurpose" type="textarea" :rows="2" /></el-form-item>
        <el-form-item :label="$t('plan.situationMap')">
          <div style="width:100%">
            <MapPicker
              mode="route"
              v-model="planRoutePoints"
              :editable="false"
              :zones="zones"
              :track="planRoutePoints"
              :marks="planAirportMarks"
              height="320px"
            />
            <div style="margin-top:6px;color:var(--el-text-color-secondary);font-size:12px">
              {{ $t('plan.mapLegend') }}
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="savePlan">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 审批对话框 -->
    <el-dialog v-model="approveDialog" :title="$t('plan.approveComment')" width="400px">
      <el-input v-model="approveComment" type="textarea" :rows="3" :placeholder="$t('plan.commentPlaceholder')" />
      <template #footer>
        <el-button @click="approveDialog=false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="doApprove(true)">{{ $t('plan.pass') }}</el-button>
        <el-button type="danger" @click="doApprove(false)">{{ $t('plan.reject') }}</el-button>
      </template>
    </el-dialog>

    <!-- 放行检查清单（未通过时逐项展示失败原因） -->
    <el-dialog v-model="checksDialog" :title="$t('plan.checkListTitle')" width="600px">
      <el-alert v-if="checksList.length" :title="$t('plan.checkFailedSummary', { failed: checksList.filter(c=>!c.pass).length, total: checksList.length })" type="error" show-icon :closable="false" style="margin-bottom:12px" />
      <el-table :data="checksList" border size="small">
        <el-table-column prop="name" :label="$t('plan.checkItem')" min-width="140" />
        <el-table-column :label="$t('plan.result')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.pass ? 'success' : 'danger'" size="small">{{ row.pass ? $t('plan.passed') : $t('plan.failed') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('plan.remark')" min-width="240">
          <template #default="{ row }">
            <span :style="row.pass ? '' : 'color:var(--el-color-danger);font-weight:600'">{{ row.reason || '--' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="checksDialog=false">{{ $t('common.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { flightPlanApi, type FlightPlan, type ReleaseCheckItem } from '@/api/flight-plan'
import { pilotApi } from '@/api/pilot'
import { usePaging } from '@/composables/usePaging'
import { useDict } from '@/composables/useDict'
import { fmtDateTime as fmtTime } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import TablePagination from '@/components/TablePagination.vue'
import { routeApi, type UavRoute } from '@/api/route'
import MapPicker from '@/components/MapPicker.vue'
import { airspaceApi } from '@/api/airspace'
import { airportApi, type UavAirport } from '@/api/airport'
import { locale } from '@/locales'

const { t } = useI18n()
// 表单标签宽度：中英文标签长度不同，英文适当放宽
const labelWidth = computed(() => (locale.value === 'en' ? '130px' : '100px'))

const plans = ref<FlightPlan[]>([])
const loading = ref(false)
const statusFilter = ref('')
const dialogVisible = ref(false)
const approveDialog = ref(false)
const editingPlan = ref<FlightPlan>({})
const currentPlan = ref<FlightPlan>({})
const approveComment = ref('')
const { page, size, total, paged } = usePaging(plans)
const { label: statusLabel } = useDict('plan_status')

// 状态筛选可选项（含 RELEASED/IN_FLIGHT/EXPIRED/CANCELLED，label 走 dict.plan_status.* 词条）
const STATUS_OPTIONS = ['DRAFT', 'PENDING_LEVEL1', 'PENDING_LEVEL2', 'PENDING_LEVEL3', 'APPROVED', 'RELEASED', 'IN_FLIGHT', 'COMPLETED', 'EXPIRED', 'CANCELLED', 'REJECTED']

const form = ref<FlightPlan>({
  pilotId: undefined, droneSn: '', departure: '', destination: '', altCeilingM: 120, flightPurpose: '',
  plannedStart: '', plannedEnd: '', planCode: ''
})

const pilots = ref<any[]>([])
const routes = ref<UavRoute[]>([])
const zones = ref<Array<{ name?: string; geoJson?: string; kind: string }>>([])
const planRoutePoints = computed<number[][]>(() => {
  const r = routes.value.find(x => x.id === form.value.routeId)
  try { return JSON.parse(r?.waypoints || '[]') } catch { return [] }
})
const planAirportMarks = computed<Array<{ lon: number; lat: number; label: string }>>(() => {
  const marks: Array<{ lon: number; lat: number; label: string }> = []
  for (const name of [form.value.departure, form.value.destination]) {
    if (!name) continue
    const a = airports.value.find(x => x.airportName === name)
    if (a?.lon != null && a?.lat != null) marks.push({ lon: a.lon, lat: a.lat, label: a.airportName || '' })
  }
  return marks
})
const airports = ref<UavAirport[]>([])
const { label: typeLabel } = useDict('airport_type')
const pilotNames = ref<Record<number, string>>({})

onMounted(async () => {
  loadPlans()
  try {
    const res = await pilotApi.list()
    const m: Record<number, string> = {}
    for (const p of ((res as any).data || [])) m[p.id] = p.pilotName
    pilotNames.value = m
    pilots.value = (res as any).data || []
    routes.value = ((await routeApi.list(true) as any).data) || []
    airports.value = ((await airportApi.list(true) as any).data) || []
    const zs = ((await airspaceApi.list() as any).data) || []
    zones.value = zs
      .filter((z: any) => z.geoJson)
      .map((z: any) => ({ name: z.airspaceName, geoJson: z.geoJson, kind: ['NO_FLY', 'TEMP_NO_FLY'].includes(z.airspaceType) ? 'no_fly' : 'other' }))
  } catch (e) {}
})

async function loadPlans() {
  loading.value = true
  try {
    const res = statusFilter.value
      ? await flightPlanApi.listByStatus(statusFilter.value)
      : await flightPlanApi.list()
    plans.value = (res as any).data || []
  } finally { loading.value = false }
}

function showCreateDialog() {
  editingPlan.value = {}
  form.value = { pilotId: undefined, droneSn: '', departure: '', destination: '', altCeilingM: 120, flightPurpose: '', plannedStart: '', plannedEnd: '', planCode: '' }
  dialogVisible.value = true
}

function showDetail(row: FlightPlan) {
  editingPlan.value = { ...row }
  form.value = { ...row }
  dialogVisible.value = true
}

async function savePlan() {
  if (editingPlan.value.id) {
    await flightPlanApi.update(editingPlan.value.id!, form.value)
    ElMessage.success(t('common.updateSuccess'))
  } else {
    await flightPlanApi.create(form.value)
    ElMessage.success(t('common.createSuccess'))
  }
  dialogVisible.value = false
  loadPlans()
}

async function submitPlan(row: FlightPlan) {
  await flightPlanApi.submit(row.id!)
  ElMessage.success(t('plan.submitted'))
  loadPlans()
}

function approvePlan(row: FlightPlan) {
  currentPlan.value = row
  approveComment.value = ''
  approveDialog.value = true
}

function rejectPlan(row: FlightPlan) {
  currentPlan.value = row
  approveComment.value = ''
  approveDialog.value = true
}

async function doApprove(pass: boolean) {
  const id = currentPlan.value.id!
  if (pass) {
    await flightPlanApi.approve(id, 1, approveComment.value)
    ElMessage.success(t('plan.approveOk'))
  } else {
    if (!approveComment.value) { ElMessage.warning(t('plan.rejectNeedReason')); return }
    await flightPlanApi.reject(id, 1, approveComment.value)
    ElMessage.success(t('plan.rejectedOk'))
  }
  approveDialog.value = false
  loadPlans()
}

function pendingLevel(s: string) { return s && s.startsWith('PENDING_LEVEL') }
function statusTag(s: string): any {
  const map: Record<string,string> = {
    DRAFT:'info', PENDING_LEVEL1:'warning', PENDING_LEVEL2:'warning', PENDING_LEVEL3:'warning',
    APPROVED:'success', REJECTED:'danger', MILITARY_CANCELLED:'danger',
    RELEASED:'success', IN_FLIGHT:'primary', EXPIRED:'info', CANCELLED:'info',
  }
  return map[s] || 'info'
}
/** RELEASED 用自定义青色（el-tag 内置色板无青色） */
function statusTagStyle(s: string) {
  if (s === 'RELEASED') return { backgroundColor: '#13c2c2', borderColor: '#13c2c2', color: '#fff' }
  if (s === 'EXPIRED') return { filter: 'grayscale(1)', opacity: 0.85 }
  return {}
}

// ===== 放行 → 起飞 → 返航/中止 闭环操作 =====
const checksDialog = ref(false)
const checksList = ref<ReleaseCheckItem[]>([])

/** 放行：成功提示并刷新；失败（400）弹检查清单逐项展示原因 */
async function releasePlan(row: FlightPlan) {
  try {
    const res = await flightPlanApi.release(row.id!)
    const checks = (res as any)?.data?.checks || []
    ElMessage.success(t('plan.releaseOk', { n: checks.length }))
    loadPlans()
  } catch (e: any) {
    // 拦截器已弹错误提示，这里负责渲染检查清单
    const body = e?.response?.data
    const checks: ReleaseCheckItem[] = body?.data?.checks || body?.checks || []
    if (checks.length) {
      checksList.value = checks
      checksDialog.value = true
    }
  }
}

async function takeoffPlan(row: FlightPlan) {
  try {
    await flightPlanApi.takeoff(row.id!)
    ElMessage.success(t('plan.takeoffOk'))
    loadPlans()
  } catch (e) {}
}

async function rtlPlan(row: FlightPlan) {
  try {
    await flightPlanApi.rtl(row.id!)
    ElMessage.success(t('plan.rtlOk'))
  } catch (e) {}
}

async function abortPlan(row: FlightPlan) {
  try {
    await ElMessageBox.confirm(
      row.planStatus === 'IN_FLIGHT' ? t('plan.abortInFlightConfirm') : t('plan.abortConfirm'),
      t('plan.abortTitle'),
      { type: 'warning', confirmButtonText: t('plan.confirmAbort'), cancelButtonText: t('plan.giveUp') }
    )
  } catch { return }
  try {
    await flightPlanApi.abort(row.id!)
    ElMessage.success(t('plan.abortedOk'))
    loadPlans()
  } catch (e) {}
}
</script>
