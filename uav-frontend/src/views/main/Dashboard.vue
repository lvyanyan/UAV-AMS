<template>
  <div class="dashboard">
    <!-- 顶部统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ stats.onlineDrones }}</div>
          <div class="stat-label">在线无人机</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value danger">{{ stats.activeAlarms }}</div>
          <div class="stat-label">活跃告警</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value warning">{{ stats.pendingPlans }}</div>
          <div class="stat-label">待审批计划</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ stats.totalDrones }}</div>
          <div class="stat-label">注册无人机</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ stats.activePilots }}</div>
          <div class="stat-label">活跃驾驶员</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ stats.airspaceCount }}</div>
          <div class="stat-label">空域配置</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷入口 -->
    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="16">
        <el-card header="📡 系统概览">
          <el-row :gutter="12">
            <el-col :span="12">
              <div class="info-item"><span class="label">网关状态</span><el-tag type="success" size="small">运行中 :8080</el-tag></div>
              <div class="info-item"><span class="label">告警引擎</span><el-tag type="success" size="small">运行中 :8082</el-tag></div>
              <div class="info-item"><span class="label">风险评估</span><el-tag type="success" size="small">运行中 :8083</el-tag></div>
              <div class="info-item"><span class="label">空域控制器</span><el-tag type="success" size="small">运行中 :8084</el-tag></div>
            </el-col>
            <el-col :span="12">
              <div class="info-item"><span class="label">实时桥接</span><el-tag type="success" size="small">运行中 :8090</el-tag></div>
              <div class="info-item"><span class="label">轨迹融合</span><el-tag type="success" size="small">运行中 :8089</el-tag></div>
              <div class="info-item"><span class="label">数据库 PG</span><el-tag :type="pgOnline?'success':'danger'" size="small">{{ pgOnline?'在线':'离线' }}</el-tag></div>
              <div class="info-item"><span class="label">Redis</span><el-tag :type="redisOnline?'success':'danger'" size="small">{{ redisOnline?'在线':'离线' }}</el-tag></div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card header="⚡ 快捷操作">
          <el-button type="primary" style="width:100%;margin-bottom:8px" @click="$router.push('/flight-monitor')">🛰️ 飞行监控大屏</el-button>
          <el-button type="success" style="width:100%;margin-bottom:8px" @click="$router.push('/flight-plan')">📋 飞行计划审批</el-button>
          <el-button type="warning" style="width:100%;margin-bottom:8px" @click="$router.push('/alarm')">🔔 告警中心</el-button>
          <el-button type="info" style="width:100%" @click="$router.push('/airspace')">🗺️ 空域配置</el-button>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const stats = ref({
  onlineDrones: 0, activeAlarms: 0, pendingPlans: 0,
  totalDrones: 1250, activePilots: 386, airspaceCount: 24
})

const pgOnline = ref(true)
const redisOnline = ref(true)

let timer: any = null

onMounted(() => {
  timer = setInterval(() => {
    stats.value.onlineDrones = Math.floor(Math.random() * 30) + 20
    stats.value.activeAlarms = Math.floor(Math.random() * 8)
    stats.value.pendingPlans = Math.floor(Math.random() * 10) + 2
  }, 2000)
})

onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.dashboard { padding: 20px; }
.stat-card { text-align: center; cursor: pointer; }
.stat-value { font-size: 36px; font-weight: 700; color: #303133; }
.stat-value.danger { color: #F56C6C; }
.stat-value.warning { color: #E6A23C; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.info-item { display: flex; justify-content: space-between; align-items: center; padding: 6px 0; border-bottom: 1px solid #f0f0f0; }
.info-item .label { color: #606266; }
</style>
