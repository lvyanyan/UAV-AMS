<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">系统管理</h2>
        <div class="page-subtitle">用户 / 角色 / 审计日志（只读台账）· 用户 {{ users.length }} · 角色 {{ roles.length }} · 日志 {{ logs.length }}</div>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-tabs v-model="tab">
        <el-tab-pane label="用户管理" name="user" />
        <el-tab-pane label="角色管理" name="role" />
        <el-tab-pane label="审计日志" name="audit" />
        <el-tab-pane label="字典管理" name="dict" />
      </el-tabs>

      <!-- 用户管理 -->
      <el-table v-if="tab==='user'" :data="userPage.paged.value" border stripe v-loading="loading">
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="realName" label="姓名" width="140" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="phone" label="电话" width="140" />
        <el-table-column prop="roleName" label="角色" width="140">
          <template #default="{ row }">{{ roleLabel(row.roleName) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status==='ACTIVE'?'success':'danger'">{{ row.status==='ACTIVE'?'正常':'禁用' }}</el-tag>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无用户数据" /></template>
      </el-table>
      <div v-if="tab==='user'" class="table-footer">
        <el-pagination v-model:current-page="userPage.page.value" v-model:page-size="userPage.size.value" :total="userPage.total.value"
          layout="total, prev, pager, next" background />
      </div>

      <!-- 角色管理 -->
      <el-table v-if="tab==='role'" :data="rolePage.paged.value" border stripe v-loading="loading">
        <el-table-column prop="roleCode" label="角色编码" width="160" />
        <el-table-column prop="roleName" label="角色名称" width="180" />
        <el-table-column prop="description" label="描述" min-width="200" />
        <template #empty><el-empty description="暂无角色数据" /></template>
      </el-table>

      <!-- 审计日志 -->
      <el-table v-if="tab==='audit'" :data="logPage.paged.value" border stripe v-loading="loading">
        <el-table-column prop="username" label="操作用户" width="130" />
        <el-table-column prop="action" label="操作" width="180" />
        <el-table-column prop="target" label="操作对象" min-width="200" />
        <el-table-column prop="ip" label="IP" width="150" />
        <el-table-column prop="createTime" label="时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <template #empty><el-empty description="暂无审计日志" /></template>
      </el-table>
      <div v-if="tab==='audit'" class="table-footer">
        <el-pagination v-model:current-page="logPage.page.value" v-model:page-size="logPage.size.value" :total="logPage.total.value"
          layout="total, prev, pager, next" background />
      </div>
          <!-- 字典管理 -->
      <div v-if="tab==='dict'">
        <div class="header-actions" style="margin:4px 0 12px">
          <el-select v-model="dictTypeFilter" placeholder="全部类型" clearable style="width:200px">
            <el-option v-for="t in dictTypes" :key="t" :label="t" :value="t" />
          </el-select>
          <el-button type="primary" @click="showDictDialog">新增字典项</el-button>
          <el-button @click="reloadDicts">刷新</el-button>
        </div>
        <el-table :data="pagedDicts" border stripe>
          <el-table-column prop="dictType" label="字典类型" width="180" />
          <el-table-column prop="value" label="值" width="180" />
          <el-table-column prop="label" label="标签" min-width="160" />
          <el-table-column prop="sort" label="排序" width="90" />
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button size="small" @click="editDict(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="removeDict(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无字典项" /></template>
        </el-table>
        <div class="table-footer">
          <el-pagination v-model:current-page="dictPage.page.value" v-model:page-size="dictPage.size.value" :total="dictRows.length"
            layout="total, prev, pager, next" background />
        </div>
      </div>
    </el-card>

    <!-- 字典项对话框 -->
    <el-dialog v-model="dictDialog" :title="editingDict.id ? '编辑字典项' : '新增字典项'" width="440px">
      <el-form :model="dictForm" label-width="80px">
        <el-form-item label="字典类型">
          <el-select v-if="!editingDict.id" v-model="dictForm.dictType" style="width:100%" filterable allow-create>
            <el-option v-for="t in dictTypes" :key="t" :label="t" :value="t" />
          </el-select>
          <el-input v-else v-model="dictForm.dictType" disabled />
        </el-form-item>
        <el-form-item label="值">
          <el-input v-model="dictForm.dictValue" :disabled="!!editingDict.id" placeholder="如 CONTROL" />
        </el-form-item>
        <el-form-item label="标签"><el-input v-model="dictForm.dictLabel" placeholder="如 管制区" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="dictForm.sortOrder" :min="0" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictDialog=false">取消</el-button>
        <el-button type="primary" @click="saveDict">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { systemApi, type SysUser, type SysRole } from '@/api/system'
import { usePaging } from '@/composables/usePaging'
import { useDict, dictCache, loadDicts } from '@/composables/useDict'
import { dictApi, type DictItem } from '@/api/dict'
import { fmtDateTime as fmtTime } from '@/utils/format'

const tab = ref('user')
const users = ref<SysUser[]>([])
const roles = ref<SysRole[]>([])
const logs = ref<any[]>([])
const loading = ref(false)
const { label: roleLabel } = useDict('user_role')
const userPage = usePaging(users, 10)
const rolePage = usePaging(roles, 10)
const logPage = usePaging(logs, 10)

onMounted(() => { loadUsers(); loadRoles(); loadLogs(); loadDicts() })

// ── 字典管理 ──
const dictTypeFilter = ref('')
const dictDialog = ref(false)
const editingDict = ref<DictItem>({})
const dictForm = ref({ dictType: '', dictValue: '', dictLabel: '', sortOrder: 0 })
const dictEntries = ref<DictItem[]>([])
const dictPage = usePaging(dictEntries, 10)
const dictTypes = computed(() => Object.keys(dictCache().value).sort())
const dictRows = computed<DictItem[]>(() => {
  const src = Object.entries(dictCache().value)
  const rows: DictItem[] = []
  for (const [type, items] of src) for (const it of items) rows.push({ ...it, dictType: type })
  return dictTypeFilter.value ? rows.filter(r => r.dictType === dictTypeFilter.value) : rows
})
const pagedDicts = computed(() => {
  const start = (dictPage.page.value - 1) * dictPage.size.value
  return dictRows.value.slice(start, start + dictPage.size.value)
})
watch(dictRows, () => {
  const max = Math.max(1, Math.ceil(dictRows.value.length / dictPage.size.value))
  if (dictPage.page.value > max) dictPage.page.value = max
})

async function reloadDicts() { await loadDicts(); const res = await dictApi.all(); if (res?.data) dictCache().value = res.data }
function showDictDialog() {
  editingDict.value = {}
  dictForm.value = { dictType: dictTypeFilter.value || '', dictValue: '', dictLabel: '', sortOrder: 0 }
  dictDialog.value = true
}
function editDict(row: DictItem) {
  editingDict.value = { ...row }
  dictForm.value = { dictType: row.dictType || '', dictValue: row.value, dictLabel: row.label, sortOrder: row.sort || 0 }
  dictDialog.value = true
}
async function saveDict() {
  if (editingDict.value.id) {
    await dictApi.update(editingDict.value.id!, { dictLabel: dictForm.value.dictLabel, sortOrder: dictForm.value.sortOrder })
  } else {
    await dictApi.create(dictForm.value)
  }
  ElMessage.success('已保存')
  dictDialog.value = false
  reloadDicts()
}
async function removeDict(row: DictItem) {
  await ElMessageBox.confirm(`删除字典项 ${row.dictType}.${row.value}？`, '确认', { type: 'warning' })
  await dictApi.remove(row.id!)
  ElMessage.success('已删除')
  reloadDicts()
}

async function loadAll(fn: () => Promise<any>): Promise<any[]> {
  try { return (await fn() as any).data || [] } catch { return [] }
}

async function loadUsers() { loading.value = true; users.value = await loadAll(systemApi.listUsers); loading.value = false }
async function loadRoles() { roles.value = await loadAll(systemApi.listRoles) }
async function loadLogs() { logs.value = await loadAll(systemApi.listAuditLogs) }

</script>
