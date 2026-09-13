<template>
  <div class="page-container">
    <PageHeader :title="$t('system.users.title')" :subtitle="$t('system.users.subtitle', { total })">
      <template #actions>
        <el-button v-permission="'system:user:create'" type="primary" @click="openCreate">{{ $t('system.users.create') }}</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="table-card">
      <!-- 搜索栏：用户名/姓名关键字 + 角色过滤 -->
      <div class="toolbar">
        <el-input v-model="query.keyword" :placeholder="$t('system.users.keywordPh')" clearable style="width:220px" @keyup.enter="reload" @clear="reload" />
        <el-select v-model="query.roleCode" :placeholder="$t('system.users.allRoles')" clearable style="width:160px" @change="reload">
          <el-option v-for="r in roles" :key="r.roleCode" :label="r.roleName" :value="r.roleCode" />
        </el-select>
        <el-button type="primary" plain @click="reload">{{ $t('common.search') }}</el-button>
        <el-button @click="resetQuery">{{ $t('common.reset') }}</el-button>
      </div>

      <el-table :data="records" border stripe v-loading="loading">
        <el-table-column prop="username" :label="$t('system.users.username')" width="130" />
        <el-table-column prop="realName" :label="$t('system.users.realName')" width="130" />
        <el-table-column prop="email" :label="$t('common.email')" min-width="170" show-overflow-tooltip />
        <el-table-column prop="phone" :label="$t('common.phone')" width="130" />
        <el-table-column prop="roleName" :label="$t('common.role')" width="130">
          <template #default="{ row }">
            <el-tag size="small">{{ row.roleName || row.roleCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.status')" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              :disabled="!userStore.hasPerm('system:user:enable')"
              @change="(v: any) => onToggleEnabled(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="$t('common.createTime')" width="160" />
        <el-table-column :label="$t('common.operation')" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:user:update'" link type="primary" size="small" @click="openEdit(row)">{{ $t('common.edit') }}</el-button>
            <el-button v-permission="'system:user:reset-pwd'" link type="primary" size="small" @click="onResetPwd(row)">{{ $t('system.users.resetPwd') }}</el-button>
            <el-button v-permission="'system:user:delete'" link type="danger" size="small" @click="onDelete(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty :description="$t('system.users.empty')" /></template>
      </el-table>
      <TablePagination v-model:page="query.page" v-model:size="query.size" :total="total" />
    </el-card>

    <!-- 新增/编辑用户 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? $t('system.users.title') : $t('system.users.create')" width="480px" destroy-on-close>
      <el-form :model="form" :label-width="labelWidth">
        <el-form-item :label="$t('system.users.username')" required>
          <el-input v-model="form.username" :disabled="!!form.id" :placeholder="$t('system.users.accountPh')" />
        </el-form-item>
        <el-form-item :label="$t('system.users.realName')">
          <el-input v-model="form.realName" :placeholder="$t('system.users.realNamePh')" />
        </el-form-item>
        <el-form-item v-if="!form.id" :label="$t('system.users.password')" required>
          <el-input v-model="form.password" type="password" show-password :placeholder="$t('system.users.min6')" />
        </el-form-item>
        <el-form-item :label="$t('common.email')">
          <el-input v-model="form.email" placeholder="user@example.com" />
        </el-form-item>
        <el-form-item :label="$t('common.phone')">
          <el-input v-model="form.phone" :placeholder="$t('system.users.phonePh')" />
        </el-form-item>
        <el-form-item :label="$t('common.role')" required>
          <el-select v-model="form.roleCode" :placeholder="$t('system.users.selectRole')" style="width:100%">
            <el-option v-for="r in roles" :key="r.roleCode" :label="`${r.roleName}（${r.roleCode}）`" :value="r.roleCode" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { systemApi, type SysUser, type SysRole } from '@/api/system'
import { useUserStore } from '@/stores/user'
import PageHeader from '@/components/PageHeader.vue'
import TablePagination from '@/components/TablePagination.vue'
import { locale } from '@/locales'

const { t } = useI18n()
// 表单标签宽度：中英文标签长度不同，英文适当放宽
const labelWidth = computed(() => (locale.value === 'en' ? '120px' : '80px'))

const userStore = useUserStore()

const records = ref<SysUser[]>([])
const roles = ref<SysRole[]>([])
const loading = ref(false)
const saving = ref(false)
const total = ref(0)

const query = reactive({ page: 1, size: 20, keyword: '', roleCode: '' })
const dialogVisible = ref(false)
const form = reactive<Partial<SysUser>>({})

async function load() {
  loading.value = true
  try {
    const res: any = await systemApi.listUsers({
      page: query.page, size: query.size,
      keyword: query.keyword || undefined,
      roleCode: query.roleCode || undefined,
    })
    records.value = res?.data?.records || []
    total.value = res?.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  const res: any = await systemApi.listRoles({ page: 1, size: 100 })
  roles.value = res?.data?.records || []
}

function reload() {
  // 翻页/页容量由下方 watch 统一触发加载；此处仅在页码不变时主动刷新
  if (query.page === 1) load()
  else query.page = 1
}

// 服务端分页：页码/页容量变化即重新拉取
watch(() => [query.page, query.size], () => load())

function resetQuery() {
  query.keyword = ''
  query.roleCode = ''
  reload()
}

function openCreate() {
  Object.assign(form, { id: undefined, username: '', realName: '', password: '', email: '', phone: '', roleCode: '' })
  dialogVisible.value = true
}

function openEdit(row: SysUser) {
  Object.assign(form, {
    id: row.id, username: row.username, realName: row.realName,
    password: undefined, email: row.email, phone: row.phone, roleCode: row.roleCode,
  })
  dialogVisible.value = true
}

async function save() {
  if (!form.username || (!form.id && !form.password) || !form.roleCode) {
    ElMessage.warning(t('system.users.fillRequired'))
    return
  }
  saving.value = true
  try {
    if (form.id) {
      const res: any = await systemApi.updateUser(form.id, {
        realName: form.realName, email: form.email, phone: form.phone, roleCode: form.roleCode,
      })
      if (res?.code !== 200) return
      ElMessage.success(t('system.users.updated'))
    } else {
      const res: any = await systemApi.createUser({
        username: form.username, password: form.password, realName: form.realName,
        email: form.email, phone: form.phone, roleCode: form.roleCode,
      })
      if (res?.code !== 200) return
      ElMessage.success(t('system.users.created'))
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onToggleEnabled(row: SysUser, enabled: boolean) {
  const res: any = await systemApi.setUserEnabled(row.id!, enabled)
  if (res?.code !== 200) return
  row.enabled = enabled
  ElMessage.success(enabled ? t('common.enabledOk') : t('common.disabledOk'))
}

async function onResetPwd(row: SysUser) {
  const { value } = await ElMessageBox.prompt(t('system.users.resetPrompt', { name: row.username }), t('system.users.resetPwd'), {
    inputPlaceholder: t('system.users.min6'),
    inputPattern: /^.{6,}$/,
    inputErrorMessage: t('system.users.pwdMinLen'),
    type: 'warning',
  })
  const res: any = await systemApi.resetPassword(row.id!, value)
  if (res?.code !== 200) return
  ElMessage.success(t('system.users.pwdReset'))
}

async function onDelete(row: SysUser) {
  const ok = await ElMessageBox.confirm(t('system.users.deleteConfirm', { name: row.username }), t('common.deleteConfirmTitle'), {
    type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel'),
  }).catch(() => false)
  if (!ok) return
  const res: any = await systemApi.deleteUser(row.id!)
  if (res?.code !== 200) return
  ElMessage.success(t('system.users.deleted'))
  load()
}

onMounted(() => {
  load()
  loadRoles()
})
</script>

<style scoped>
.toolbar { display:flex; gap:10px; margin-bottom:14px; align-items:center; }
</style>
