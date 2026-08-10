<template>
  <div class="page-container">
    <el-tabs v-model="tab">
      <el-tab-pane label="用户管理" name="user" />
      <el-tab-pane label="角色管理" name="role" />
      <el-tab-pane label="审计日志" name="audit" />
    </el-tabs>

    <!-- 用户管理 -->
    <div v-if="tab==='user'">
      <div style="margin-bottom:12px"><el-button type="primary" @click="showUserDialog">新增用户</el-button></div>
      <el-table :data="users" border stripe>
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="email" label="邮箱" width="180" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="roleName" label="角色" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status==='ACTIVE'?'success':'danger'">{{ row.status==='ACTIVE'?'正常':'禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button size="small" @click="editUser(row)">编辑</el-button>
            <el-button v-if="row.status==='ACTIVE'" size="small" type="danger" @click="toggleUser(row,false)">禁用</el-button>
            <el-button v-else size="small" type="success" @click="toggleUser(row,true)">启用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 角色管理 -->
    <div v-if="tab==='role'">
      <div style="margin-bottom:12px"><el-button type="primary" @click="showRoleDialog">新增角色</el-button></div>
      <el-table :data="roles" border stripe>
        <el-table-column prop="roleCode" label="角色编码" width="160" />
        <el-table-column prop="roleName" label="角色名称" width="180" />
        <el-table-column prop="description" label="描述" min-width="200" />
      </el-table>
    </div>

    <!-- 审计日志 -->
    <div v-if="tab==='audit'">
      <el-table :data="logs" border stripe>
        <el-table-column prop="username" label="操作用户" width="120" />
        <el-table-column prop="action" label="操作" width="180" />
        <el-table-column prop="target" label="操作对象" min-width="200" />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column prop="createTime" label="时间" width="160" />
      </el-table>
    </div>

    <!-- User Dialog -->
    <el-dialog v-model="userDialog" :title="editingUser.id?'编辑用户':'新增用户'" width="450px">
      <el-form :model="userForm" label-width="80px">
        <el-form-item label="用户名"><el-input v-model="userForm.username" /></el-form-item>
        <el-form-item v-if="!editingUser.id" label="密码"><el-input v-model="userForm.password" type="password" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="userForm.realName" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="userForm.email" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="userForm.phone" /></el-form-item>
        <el-form-item label="角色ID"><el-input-number v-model="userForm.roleId" :min="1" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialog=false">取消</el-button>
        <el-button type="primary" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>

    <!-- Role Dialog -->
    <el-dialog v-model="roleDialog" title="新增角色" width="400px">
      <el-form :model="roleForm" label-width="80px">
        <el-form-item label="角色编码"><el-input v-model="roleForm.roleCode" /></el-form-item>
        <el-form-item label="角色名称"><el-input v-model="roleForm.roleName" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="roleForm.description" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialog=false">取消</el-button>
        <el-button type="primary" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { systemApi, type SysUser, type SysRole } from '@/api/system'

const tab = ref('user')
const users = ref<SysUser[]>([])
const roles = ref<SysRole[]>([])
const logs = ref<any[]>([])
const userDialog = ref(false)
const roleDialog = ref(false)
const editingUser = ref<SysUser>({})
const userForm = ref<SysUser & { password?: string }>({ username:'', password:'', realName:'', email:'', phone:'', roleId:1 })
const roleForm = ref<SysRole>({ roleCode:'', roleName:'', description:'' })

onMounted(() => { loadUsers(); loadRoles(); loadLogs() })

async function loadUsers() {
  try { const res = await systemApi.listUsers(); users.value = (res as any).data || [] }
  catch { users.value = [{ id:1,username:'admin',realName:'管理员',email:'admin@uav.cn',phone:'13800000000',roleName:'超级管理员',status:'ACTIVE' }] }
}
async function loadRoles() {
  try { const res = await systemApi.listRoles(); roles.value = (res as any).data || [] }
  catch { roles.value = [
    { id:1,roleCode:'SUPER_ADMIN',roleName:'超级管理员',description:'全部权限' },
    { id:2,roleCode:'OPS',roleName:'操作员',description:'日常操作' },
    { id:3,roleCode:'REGULATOR',roleName:'监管员',description:'审批+监管' },
    { id:4,roleCode:'MILITARY',roleName:'军民协调员',description:'军事调度权限' },
  ]}
}
async function loadLogs() {
  try { const res = await systemApi.listAuditLogs(); logs.value = (res as any).data || [] }
  catch { logs.value = [] }
}

function showUserDialog() { editingUser.value = {}; userForm.value = { username:'', password:'', realName:'', email:'', phone:'', roleId:1 }; userDialog.value = true }
function editUser(row: SysUser) { editingUser.value = { ...row }; Object.assign(userForm.value, row); userDialog.value = true }
async function saveUser() {
  if (editingUser.value.id) { await systemApi.updateUser(editingUser.value.id!, userForm.value as SysUser); ElMessage.success('更新成功') }
  else { await systemApi.createUser(userForm.value as SysUser); ElMessage.success('创建成功') }
  userDialog.value = false; loadUsers()
}
async function toggleUser(row: SysUser, enable: boolean) {
  if (enable) { await systemApi.enableUser(row.id!) } else { await systemApi.disableUser(row.id!) }
  ElMessage.success(enable ? '已启用' : '已禁用'); loadUsers()
}

function showRoleDialog() { roleForm.value = { roleCode:'', roleName:'', description:'' }; roleDialog.value = true }
async function saveRole() { await systemApi.createRole(roleForm.value); ElMessage.success('创建成功'); roleDialog.value = false; loadRoles() }
</script>

<style scoped>
.page-container { padding: 20px; }
</style>
