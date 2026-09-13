<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">{{ $t('dictPage.title') }}</h2>
        <div class="page-subtitle">{{ $t('dictPage.subtitle') }}</div>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="showCreate">{{ $t('dictPage.create') }}</el-button>
        <el-button @click="reload">{{ $t('common.refresh') }}</el-button>
      </div>
    </div>

    <div class="dict-layout">
      <!-- 左：业务分组 → 字典类型 -->
      <el-card shadow="never" class="dict-side">
        <el-collapse v-model="activeGroups">
          <el-collapse-item v-for="g in groups" :key="g.name" :name="g.name">
            <template #title><b class="group-name">{{ g.name }}</b></template>
            <div
              v-for="t in g.types" :key="t.dictType"
              class="dict-type-item" :class="{ active: currentType === t.dictType }"
              @click="currentType = t.dictType">
              {{ t.dictName }}
              <span class="dict-type-code">{{ t.dictType }}</span>
            </div>
          </el-collapse-item>
        </el-collapse>
      </el-card>

      <!-- 右：所选类型的字典项 -->
      <el-card shadow="never" class="dict-main table-card">
        <template #header>
          <b>{{ currentTypeName }}</b>
          <span class="dict-type-code" style="margin-left:8px">{{ currentType }}</span>
        </template>
        <el-table :data="paged" border stripe v-loading="loading">
          <el-table-column prop="value" :label="$t('dictPage.value')" width="200" />
          <el-table-column prop="label" :label="$t('dictPage.label')" min-width="180" />
          <el-table-column prop="sort" :label="$t('dictPage.sort')" width="90" />
          <el-table-column :label="$t('common.operation')" width="150">
            <template #default="{ row }">
              <el-button size="small" @click="editItem(row)">{{ $t('common.edit') }}</el-button>
              <el-button size="small" type="danger" @click="removeItem(row)">{{ $t('common.delete') }}</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty :description="$t('dictPage.empty')" /></template>
        </el-table>
        <div class="table-footer">
          <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
            layout="total, prev, pager, next" background />
        </div>
      </el-card>
    </div>

    <el-dialog v-model="dialogVisible" :title="editing.id ? $t('dictPage.edit') : $t('dictPage.create')" width="440px">
      <el-form :model="form" :label-width="labelWidth">
        <el-form-item :label="$t('dictPage.type')">
          <el-select v-if="!editing.id" v-model="form.dictType" style="width:100%" filterable allow-create>
            <el-option v-for="t in meta" :key="t.dictType" :label="`${t.businessGroup} / ${t.dictName} (${t.dictType})`" :value="t.dictType" />
          </el-select>
          <el-input v-else v-model="form.dictType" disabled />
        </el-form-item>
        <el-form-item :label="$t('dictPage.value')">
          <el-input v-model="form.dictValue" :disabled="!!editing.id" :placeholder="$t('dictPage.valuePh')" />
        </el-form-item>
        <el-form-item :label="$t('dictPage.label')"><el-input v-model="form.dictLabel" :placeholder="$t('dictPage.labelPh')" /></el-form-item>
        <el-form-item :label="$t('dictPage.sort')"><el-input-number v-model="form.sortOrder" :min="0" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="save">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dictApi, type DictItem } from '@/api/dict'
import { usePaging } from '@/composables/usePaging'
import { dictCache, loadDicts } from '@/composables/useDict'
import { locale } from '@/locales'

const { t } = useI18n()
// 表单标签宽度：中英文标签长度不同，英文适当放宽
const labelWidth = computed(() => (locale.value === 'en' ? '110px' : '80px'))

interface DictTypeMeta { dictType: string; dictName: string; businessGroup: string; sort?: number }

const meta = ref<DictTypeMeta[]>([])
const currentType = ref('')
const entries = ref<DictItem[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref<DictItem>({})
const form = ref({ dictType: '', dictValue: '', dictLabel: '', sortOrder: 0 })
const { page, size, total, paged } = usePaging(entries, 20)

// 按业务分组（组序按 meta sort，组内按名称）
const groups = computed(() => {
  const byGroup = new Map<string, DictTypeMeta[]>()
  for (const t of meta.value) {
    if (!byGroup.has(t.businessGroup)) byGroup.set(t.businessGroup, [])
    byGroup.get(t.businessGroup)!.push(t)
  }
  return Array.from(byGroup.entries())
    .sort((a, b) => (a[1][0]?.sort ?? 99) - (b[1][0]?.sort ?? 99))
    .map(([name, types]) => ({ name, types }))
})
const activeGroups = ref<string[]>([])

const currentTypeName = computed(() => meta.value.find(t => t.dictType === currentType.value)?.dictName || '')

// 当前类型的数据项：直接读全局缓存（写回后全站即时生效）
const dictRows = computed<DictItem[]>(() => (dictCache().value[currentType.value] || []) as DictItem[])
watch(dictRows, (rows) => { entries.value = [...rows] }, { immediate: true, deep: true })

async function reload() {
  await loadDicts()
  const res = await dictApi.all()
  if (res?.data) dictCache().value = res.data
  const m = await dictApi.meta()
  meta.value = (m?.data || []) as DictTypeMeta[]
  activeGroups.value = groups.value.map(g => g.name)
  if (!meta.value.some(t => t.dictType === currentType.value)) {
    currentType.value = meta.value[0]?.dictType || ''
  }
}

onMounted(reload)

function showCreate() {
  editing.value = {}
  form.value = { dictType: currentType.value, dictValue: '', dictLabel: '', sortOrder: entries.value.length + 1 }
  dialogVisible.value = true
}
function editItem(row: DictItem) {
  editing.value = { ...row }
  form.value = { dictType: currentType.value, dictValue: row.value, dictLabel: row.label, sortOrder: row.sort || 0 }
  dialogVisible.value = true
}
async function save() {
  if (editing.value.id) {
    await dictApi.update(editingDictId(), { dictLabel: form.value.dictLabel, sortOrder: form.value.sortOrder })
  } else {
    await dictApi.create({ dictType: form.value.dictType, dictValue: form.value.dictValue, dictLabel: form.value.dictLabel, sortOrder: form.value.sortOrder })
  }
  ElMessage.success(t('common.saveSuccess'))
  dialogVisible.value = false
  reload()
}
function editingDictId() {
  const hit = dictRows.value.find(r => r.value === editing.value.value)
  return hit?.id ?? 0
}
async function removeItem(row: DictItem) {
  await ElMessageBox.confirm(t('dictPage.deleteConfirm', { value: row.value, label: row.label }), t('common.confirmTitle'), { type: 'warning' })
  if (row.id) await dictApi.remove(row.id)
  ElMessage.success(t('common.deleteSuccess'))
  reload()
}
</script>

<style scoped>
.dict-layout { display: flex; gap: 14px; align-items: flex-start; }
.dict-side { width: 260px; flex-shrink: 0; }
.dict-main { flex: 1; }
.group-name { color: var(--el-text-color-primary); }
.dict-type-item { padding: 7px 10px; border-radius: 6px; cursor: pointer; color: var(--el-text-color-regular); font-size: 13px; display:flex; justify-content: space-between; align-items:center; gap: 8px; }
.dict-type-item:hover { background: var(--el-fill-color-light); }
.dict-type-item.active { background: rgba(64,158,255,.14); color: var(--el-color-primary); }
.dict-type-code { color: var(--el-text-color-secondary); font-size: 11px; font-family: monospace; }
</style>
