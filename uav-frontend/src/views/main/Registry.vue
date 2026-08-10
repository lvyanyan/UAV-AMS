<template>
  <div class="page-container">
    <div class="page-header">
      <h2>实名登记管理</h2>
      <el-tabs v-model="tab" style="flex:1;margin-left:20px">
        <el-tab-pane label="所有人登记" name="owner" />
        <el-tab-pane label="无人机登记" name="drone" />
      </el-tabs>
    </div>

    <!-- 所有人登记 -->
    <div v-if="tab==='owner'">
      <div style="margin-bottom:12px">
        <el-button type="primary" @click="showOwnerDialog">新增所有人</el-button>
      </div>
      <el-table :data="owners" border stripe>
        <el-table-column prop="ownerName" label="姓名/单位" width="140" />
        <el-table-column prop="idCardNo" label="证件号" width="180" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="organization" label="所属单位" min-width="150" />
        <el-table-column prop="registerStatus" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.registerStatus==='APPROVED'?'success':row.registerStatus==='REJECTED'?'danger':'warning'">
              {{ row.registerStatus==='APPROVED'?'已通过':row.registerStatus==='REJECTED'?'已拒绝':'待审核' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button v-if="row.registerStatus==='PENDING'" size="small" type="success" @click="approveOwner(row)">通过</el-button>
            <el-button v-if="row.registerStatus==='PENDING'" size="small" type="danger" @click="rejectOwner(row)">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 无人机登记 -->
    <div v-if="tab==='drone'">
      <div style="margin-bottom:12px">
        <el-button type="primary" @click="showDroneDialog">新增无人机</el-button>
      </div>
      <el-table :data="drones" border stripe>
        <el-table-column prop="droneSn" label="SN" width="150" />
        <el-table-column prop="droneModel" label="型号" width="120" />
        <el-table-column prop="droneType" label="类型" width="100" />
        <el-table-column prop="droneWeight" label="重量(kg)" width="100" />
        <el-table-column prop="registerNo" label="登记号" width="180" />
        <el-table-column prop="registerStatus" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.registerStatus==='APPROVED'?'success':row.registerStatus==='REJECTED'?'danger':'warning'">
              {{ row.registerStatus==='APPROVED'?'已通过':row.registerStatus==='REJECTED'?'已拒绝':'待审核' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button v-if="row.registerStatus==='PENDING'" size="small" type="success" @click="approveDrone(row)">通过</el-button>
            <el-button v-if="row.registerStatus==='PENDING'" size="small" type="danger" @click="rejectDrone(row)">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Owner Dialog -->
    <el-dialog v-model="ownerDialog" title="新增所有人" width="450px">
      <el-form :model="ownerForm" label-width="80px">
        <el-form-item label="姓名/单位"><el-input v-model="ownerForm.ownerName" /></el-form-item>
        <el-form-item label="证件号"><el-input v-model="ownerForm.idCardNo" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="ownerForm.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="ownerForm.email" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="ownerForm.organization" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ownerDialog=false">取消</el-button>
        <el-button type="primary" @click="saveOwner">保存</el-button>
      </template>
    </el-dialog>

    <!-- Drone Dialog -->
    <el-dialog v-model="droneDialog" title="新增无人机" width="450px">
      <el-form :model="droneForm" label-width="80px">
        <el-form-item label="SN"><el-input v-model="droneForm.droneSn" /></el-form-item>
        <el-form-item label="型号"><el-input v-model="droneForm.droneModel" /></el-form-item>
        <el-form-item label="类型"><el-input v-model="droneForm.droneType" /></el-form-item>
        <el-form-item label="重量(kg)"><el-input-number v-model="droneForm.droneWeight" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="所有人ID"><el-input-number v-model="droneForm.ownerId" :min="1" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="droneDialog=false">取消</el-button>
        <el-button type="primary" @click="saveDrone">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { registryApi, type UavOwner, type UavRegistration } from '@/api/registry'

const tab = ref('owner')
const owners = ref<UavOwner[]>([])
const drones = ref<UavRegistration[]>([])
const ownerDialog = ref(false)
const droneDialog = ref(false)
const ownerForm = ref<UavOwner>({ ownerName:'', idCardNo:'', phone:'', email:'', organization:'' })
const droneForm = ref<UavRegistration>({ ownerId:1, droneSn:'', droneModel:'', droneType:'', droneWeight:0 })

onMounted(() => { loadOwners(); loadDrones() })

async function loadOwners() { const res = await registryApi.listOwners(); owners.value = (res as any).data || [] }
async function loadDrones() { const res = await registryApi.listDrones(); drones.value = (res as any).data || [] }

function showOwnerDialog() { ownerForm.value = { ownerName:'', idCardNo:'', phone:'', email:'', organization:'' }; ownerDialog.value = true }
async function saveOwner() { await registryApi.registerOwner(ownerForm.value); ElMessage.success('登记成功'); ownerDialog.value = false; loadOwners() }
async function approveOwner(row: UavOwner) { await registryApi.approveOwner(row.id!); ElMessage.success('已通过'); loadOwners() }
async function rejectOwner(row: UavOwner) { await registryApi.rejectOwner(row.id!); ElMessage.success('已拒绝'); loadOwners() }

function showDroneDialog() { droneForm.value = { ownerId:1, droneSn:'', droneModel:'', droneType:'', droneWeight:0 }; droneDialog.value = true }
async function saveDrone() { await registryApi.registerDrone(droneForm.value); ElMessage.success('登记成功'); droneDialog.value = false; loadDrones() }
async function approveDrone(row: UavRegistration) { await registryApi.approveDrone(row.id!); ElMessage.success('已通过'); loadDrones() }
async function rejectDrone(row: UavRegistration) { await registryApi.rejectDrone(row.id!); ElMessage.success('已拒绝'); loadDrones() }
</script>

<style scoped>
.page-container { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
</style>
