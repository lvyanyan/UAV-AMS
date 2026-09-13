<template>
  <div class="page-container">
    <PageHeader :title="$t('system.roles.title')" :subtitle="$t('system.roles.subtitle', { total })">
      <template #actions>
        <el-button v-permission="'system:role:create'" type="primary" @click="openCreate">{{ $t('system.roles.create') }}</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" :placeholder="$t('system.roles.keywordPh')" clearable style="width:240px" @keyup.enter="reload" @clear="reload" />
        <el-button type="primary" plain @click="reload">{{ $t('common.search') }}</el-button>
        <el-button @click="resetQuery">{{ $t('common.reset') }}</el-button>
      </div>

      <el-table :data="records" border stripe v-loading="loading">
        <el-table-column prop="roleCode" :label="$t('system.roles.code')" width="150" />
        <el-table-column prop="roleName" :label="$t('system.roles.name')" width="160" />
        <el-table-column prop="description" :label="$t('common.desc')" min-width="220" show-overflow-tooltip />
        <el-table-column :label="$t('common.status')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === false ? 'danger' : 'success'" size="small">{{ row.enabled === false ? $t('common.disabled') : $t('common.enabled') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="$t('common.createTime')" width="160" />
        <el-table-column :label="$t('common.operation')" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:role:assign'" link type="primary" size="small" @click="openPerms(row)">{{ $t('system.roles.assignPerms') }}</el-button>
            <el-button v-permission="'system:role:update'" link type="primary" size="small" @click="openEdit(row)">{{ $t('common.edit') }}</el-button>
            <el-button v-permission="'system:role:delete'" link type="danger" size="small" @click="onDelete(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty :description="$t('system.roles.empty')" /></template>
      </el-table>
      <TablePagination v-model:page="query.page" v-model:size="query.size" :total="total" />
    </el-card>

    <!-- 新增/编辑角色 -->
    <el-dialog v-model="dialogVisible" :title="form.roleCode ? $t('system.roles.title') : $t('system.roles.create')" width="460px" destroy-on-close>
      <el-form :model="form" :label-width="labelWidth">
        <el-form-item :label="$t('system.roles.code')" required>
          <el-input v-model="form.roleCode" :disabled="!!isEdit" :placeholder="$t('system.roles.codePh')" />
        </el-form-item>
        <el-form-item :label="$t('system.roles.name')" required>
          <el-input v-model="form.roleName" :placeholder="$t('system.roles.namePh')" />
        </el-form-item>
        <el-form-item :label="$t('common.desc')">
          <el-input v-model="form.description" type="textarea" :rows="2" :placeholder="$t('system.roles.descPh')" />
        </el-form-item>
        <el-form-item :label="$t('common.enabled')">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限：权限树勾选，保存为该角色全量权限码 -->
    <el-dialog v-model="permDialogVisible" :title="$t('system.roles.permDialogTitle', { name: permRole?.roleName || '', code: permRole?.roleCode || '' })" width="520px" destroy-on-close>
      <el-tree
        ref="treeRef"
        :data="permTree"
        node-key="permCode"
        :props="{ label: 'permName', children: 'children' }"
        show-checkbox
        default-expand-all
        :default-checked-keys="checkedKeys"
      />
      <template #footer>
        <el-button @click="permDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="savingPerms" @click="savePerms">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed, onMounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { systemApi, type SysRole, type PermNode } from '@/api/system'
import PageHeader from '@/components/PageHeader.vue'
import TablePagination from '@/components/TablePagination.vue'
import { locale } from '@/locales'

const { t } = useI18n()
// 表单标签宽度：中英文标签长度不同，英文适当放宽
const labelWidth = computed(() => (locale.value === 'en' ? '130px' : '80px'))

const records = ref<SysRole[]>([])
const loading = ref(false)
const saving = ref(false)
const savingPerms = ref(false)
const total = ref(0)

const query = reactive({ page: 1, size: 20, keyword: '' })
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = reactive<Partial<SysRole>>({})

const permDialogVisible = ref(false)
const permRole = ref<SysRole | null>(null)
const permTree = ref<PermNode[]>([])
const checkedKeys = ref<string[]>([])
const treeRef = ref<any>(null)

async function load() {
  loading.value = true
  try {
    const res: any = await systemApi.listRoles({
      page: query.page, size: query.size, keyword: query.keyword || undefined,
    })
    records.value = res?.data?.records || []
    total.value = res?.data?.total || 0
  } finally {
    loading.value = false
  }
}

function reload() {
  if (query.page === 1) load()
  else query.page = 1
}

function resetQuery() {
  query.keyword = ''
  reload()
}

watch(() => [query.page, query.size], () => load())

function openCreate() {
  isEdit.value = false
  Object.assign(form, { roleCode: '', roleName: '', description: '', enabled: true })
  dialogVisible.value = true
}

function openEdit(row: SysRole) {
  isEdit.value = true
  Object.assign(form, { roleCode: row.roleCode, roleName: row.roleName, description: row.description, enabled: row.enabled !== false })
  dialogVisible.value = true
}

async function save() {
  if (!form.roleCode || !form.roleName) {
    ElMessage.warning(t('system.roles.fillRequired'))
    return
  }
  saving.value = true
  try {
    let res: any
    if (isEdit.value) {
      res = await systemApi.updateRole(form.roleCode!, { roleName: form.roleName, description: form.description, enabled: form.enabled })
    } else {
      res = await systemApi.createRole({ roleCode: form.roleCode, roleName: form.roleName, description: form.description, enabled: form.enabled })
    }
    if (res?.code !== 200) return
    ElMessage.success(isEdit.value ? t('system.roles.updated') : t('system.roles.created'))
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: SysRole) {
  const ok = await ElMessageBox.confirm(t('system.roles.deleteConfirm', { name: row.roleName, code: row.roleCode }), t('common.deleteConfirmTitle'), {
    type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel'),
  }).catch(() => false)
  if (!ok) return
  const res: any = await systemApi.deleteRole(row.roleCode)
  if (res?.code !== 200) return
  ElMessage.success(t('system.roles.deleted'))
  load()
}

/** 收集权限树中的叶子权限码（父节点勾选态由 el-tree 级联推导，回显只喂叶子避免全选） */
function collectLeafCodes(nodes: PermNode[], acc: Set<string> = new Set()): Set<string> {
  for (const n of nodes) {
    if (n.children && n.children.length) collectLeafCodes(n.children, acc)
    else acc.add(n.permCode)
  }
  return acc
}

async function openPerms(row: SysRole) {
  permRole.value = row
  const [treeRes, permsRes]: any[] = await Promise.all([
    systemApi.permissionTree(),
    systemApi.getRolePerms(row.roleCode),
  ])
  permTree.value = treeRes?.data || []
  const owned: string[] = permsRes?.data || []
  const leaves = collectLeafCodes(permTree.value)
  checkedKeys.value = owned.filter(c => leaves.has(c))
  permDialogVisible.value = true
  await nextTick()
  treeRef.value?.setCheckedKeys(checkedKeys.value)
}

async function savePerms() {
  if (!permRole.value) return
  const tree = treeRef.value
  // 半选的父节点（如 system:menu）也要保存，保证树回显与菜单过滤完整
  const keys: string[] = [...(tree?.getCheckedKeys() || []), ...(tree?.getHalfCheckedKeys() || [])]
  savingPerms.value = true
  try {
    const res: any = await systemApi.assignRolePerms(permRole.value.roleCode, keys)
    if (res?.code !== 200) return
    ElMessage.success(t('system.roles.permsSaved'))
    permDialogVisible.value = false
  } finally {
    savingPerms.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display:flex; gap:10px; margin-bottom:14px; align-items:center; }
</style>
