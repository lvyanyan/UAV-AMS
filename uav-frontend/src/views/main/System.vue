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
      </el-tabs>

      <!-- 用户管理 -->
      <el-table v-if="tab==='user'" :data="userPage.paged.value" border stripe v-loading="loading">
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="realName" label="姓名" width="140" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="phone" label="电话" width="140" />
        <el-table-column prop="roleName" label="角色" width="130" />
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
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { systemApi, type SysUser, type SysRole } from '@/api/system'
import { usePaging } from '@/composables/usePaging'

const tab = ref('user')
const users = ref<SysUser[]>([])
const roles = ref<SysRole[]>([])
const logs = ref<any[]>([])
const loading = ref(false)
const userPage = usePaging(users, 10)
const rolePage = usePaging(roles, 10)
const logPage = usePaging(logs, 10)

onMounted(() => { loadUsers(); loadRoles(); loadLogs() })

async function loadAll(fn: () => Promise<any>): Promise<any[]> {
  try { return (await fn() as any).data || [] } catch { return [] }
}

async function loadUsers() { loading.value = true; users.value = await loadAll(systemApi.listUsers); loading.value = false }
async function loadRoles() { roles.value = await loadAll(systemApi.listRoles) }
async function loadLogs() { logs.value = await loadAll(systemApi.listAuditLogs) }

function fmtTime(t?: string) { return t ? String(t).replace('T', ' ').slice(0, 19) : '--' }
</script>
