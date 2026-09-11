<template>
  <div class="page-container">
    <PageHeader title="审计日志" :subtitle="`关键操作留痕（只读，最近 200 条）· 共 ${ total } 条`" />
    <el-card shadow="never" class="table-card">
      <el-table :data="paged" border stripe v-loading="loading">
        <el-table-column prop="username" label="操作用户" width="130" />
        <el-table-column prop="action" label="操作" width="180" />
        <el-table-column prop="target" label="操作对象" min-width="200" />
        <el-table-column prop="ip" label="IP" width="150" />
        <el-table-column prop="createTime" label="时间" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.createTime) }}</template>
        </el-table-column>
        <template #empty><el-empty description="暂无审计日志" /></template>
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
