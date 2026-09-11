<template>
  <div class="page-container">
    <PageHeader title="用户管理" :subtitle="`平台账户台账（只读）· 共 ${ total } 个用户`" />
    <el-card shadow="never" class="table-card">
      <el-table :data="paged" border stripe v-loading="loading">
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
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">{{ fmtDateTime(row.createTime) }}</template>
        </el-table-column>
        <template #empty><el-empty description="暂无用户数据" /></template>
      </el-table>
      <TablePagination v-model:page="page" v-model:size="size" :total="total" layout="total, prev, pager, next" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { systemApi, type SysUser } from '@/api/system'
import { usePaging } from '@/composables/usePaging'
import { useDict } from '@/composables/useDict'
import { fmtDateTime } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import TablePagination from '@/components/TablePagination.vue'

const users = ref<SysUser[]>([])
const loading = ref(false)
const { page, size, total, paged } = usePaging(users, 20)
const { label: roleLabel } = useDict('user_role')

onMounted(async () => {
  loading.value = true
  try { users.value = ((await systemApi.listUsers() as any).data) || [] }
  finally { loading.value = false }
})
</script>
