<template>
  <div class="page-container">
    <PageHeader :title="$t('system.audit.title')" :subtitle="$t('system.audit.subtitle', { total })" />
    <el-card shadow="never" class="table-card">
      <el-table :data="paged" border stripe v-loading="loading">
        <el-table-column prop="username" :label="$t('system.audit.user')" width="130" />
        <el-table-column prop="action" :label="$t('system.audit.action')" width="180" />
        <el-table-column prop="target" :label="$t('system.audit.target')" min-width="200" />
        <el-table-column prop="ip" label="IP" width="150" />
        <el-table-column prop="createTime" :label="$t('common.time')" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.createTime) }}</template>
        </el-table-column>
        <template #empty><el-empty :description="$t('system.audit.empty')" /></template>
      </el-table>
      <TablePagination v-model:page="page" v-model:size="size" :total="total" layout="total, prev, pager, next" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { systemApi } from '@/api/system'
import { usePaging } from '@/composables/usePaging'
import { fmtDateTime } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import TablePagination from '@/components/TablePagination.vue'

const logs = ref<any[]>([])
const loading = ref(false)
const { page, size, total, paged } = usePaging(logs, 20)

onMounted(async () => {
  loading.value = true
  try { logs.value = ((await systemApi.listAuditLogs() as any).data) || [] }
  finally { loading.value = false }
})
</script>
