<template>
  <div class="page-container">
    <PageHeader title="角色管理" :subtitle="`平台角色与权限域（只读）· 共 ${ total } 个角色`" />
    <el-card shadow="never" class="table-card">
      <el-table :data="paged" border stripe v-loading="loading">
        <el-table-column prop="roleCode" label="角色编码" width="180">
          <template #default="{ row }">{{ roleLabel(row.roleCode) }}</template>
        </el-table-column>
        <el-table-column prop="roleName" label="角色名称" width="200" />
        <el-table-column prop="description" label="描述" min-width="220" />
        <template #empty><el-empty description="暂无角色数据" /></template>
      </el-table>
      <TablePagination v-model:page="page" v-model:size="size" :total="total" layout="total, prev, pager, next" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { systemApi, type SysRole } from '@/api/system'
import { usePaging } from '@/composables/usePaging'
import { useDict } from '@/composables/useDict'
import PageHeader from '@/components/PageHeader.vue'
import TablePagination from '@/components/TablePagination.vue'

const roles = ref<SysRole[]>([])
const loading = ref(false)
const { page, size, total, paged } = usePaging(roles, 20)
const { label: roleLabel } = useDict('user_role')

onMounted(async () => {
  loading.value = true
  try { roles.value = ((await systemApi.listRoles() as any).data) || [] }
  finally { loading.value = false }
})
</script>
