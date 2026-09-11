<template>
  <div class="main-layout">
    <aside class="sidebar">
      <div class="logo" @click="$router.push('/dashboard')">🛩️ UAV-AMS</div>
      <el-menu :default-active="currentRoute" router background-color="transparent" text-color="#8899aa" active-text-color="#409EFF" style="border-right:none">
        <el-menu-item index="/dashboard"><el-icon><Odometer /></el-icon><span>仪表盘</span></el-menu-item>
        <el-menu-item index="/flight-monitor"><el-icon><Monitor /></el-icon><span>飞行监控</span></el-menu-item>
        <el-menu-item index="/flight-plan"><el-icon><Document /></el-icon><span>飞行计划</span></el-menu-item>
        <el-menu-item index="/airspace"><el-icon><MapLocation /></el-icon><span>空域管理</span></el-menu-item>
        <el-menu-item index="/registry"><el-icon><Files /></el-icon><span>实名登记</span></el-menu-item>
        <el-menu-item index="/pilot"><el-icon><UserFilled /></el-icon><span>飞手管理</span></el-menu-item>
        <el-menu-item index="/alarm"><el-icon><Bell /></el-icon><span>告警中心</span></el-menu-item>
        <el-menu-item index="/violation"><el-icon><WarningFilled /></el-icon><span>违规处置</span></el-menu-item>
        <el-menu-item index="/system" v-if="userStore.roleCode==='ADMIN'"><el-icon><Setting /></el-icon><span>系统管理</span></el-menu-item>
        <el-menu-item index="/military" v-if="userStore.roleCode==='MILITARY'||userStore.roleCode==='ADMIN'"><el-icon><Medal /></el-icon><span>军事调度</span></el-menu-item>
      </el-menu>
      <div class="user-info"><span>{{ userStore.realName||userStore.username }}</span><el-button text type="danger" size="small" @click="handleLogout">退出</el-button></div>
    </aside>
    <main class="main-content"><router-view /></main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
const route = useRoute(); const router = useRouter(); const userStore = useUserStore()
const currentRoute = computed(() => route.path)
function handleLogout() { userStore.logout(); router.push('/login') }
</script>

<style scoped>
.main-layout { display:flex; height:100vh; background:#0f1923; }
.sidebar { width:220px; display:flex; flex-direction:column; background:rgba(255,255,255,.03); border-right:1px solid rgba(255,255,255,.06); }
.logo { color:#fff; font-size:18px; font-weight:bold; padding:20px; cursor:pointer; text-align:center; }
.sidebar .el-menu { flex:1; overflow-y:auto; }
.user-info { padding:16px; color:#889; font-size:13px; display:flex; justify-content:space-between; align-items:center; border-top:1px solid rgba(255,255,255,.06); }
.main-content { flex:1; overflow:hidden; position:relative; }
</style>
