<template>
  <div class="main-layout">
    <aside class="sidebar">
      <div class="logo" @click="$router.push('/dashboard')"><el-icon class="logo-icon"><Promotion /></el-icon> UAV-AMS</div>
      <el-menu :default-active="currentRoute" router background-color="transparent" text-color="#8fa2ba" active-text-color="#66b1ff" style="border-right:none" class="side-menu">
        <el-menu-item index="/dashboard"><el-icon><Odometer /></el-icon><span>仪表盘</span></el-menu-item>
        <el-menu-item index="/flight-monitor"><el-icon><Monitor /></el-icon><span>飞行监控</span></el-menu-item>
        <el-menu-item index="/flight-plan"><el-icon><Document /></el-icon><span>飞行计划</span></el-menu-item>
        <el-menu-item index="/airspace"><el-icon><MapLocation /></el-icon><span>空域管理</span></el-menu-item>
        <el-menu-item index="/air-route"><el-icon><Guide /></el-icon><span>航路管理</span></el-menu-item>
        <el-menu-item index="/airport"><el-icon><LocationInformation /></el-icon><span>起降场管理</span></el-menu-item>
        <el-menu-item index="/registry"><el-icon><Files /></el-icon><span>实名登记</span></el-menu-item>
        <el-menu-item index="/pilot"><el-icon><UserFilled /></el-icon><span>飞手管理</span></el-menu-item>
        <el-menu-item index="/alarm"><el-icon><Bell /></el-icon><span>告警中心</span></el-menu-item>
        <el-menu-item index="/violation"><el-icon><WarningFilled /></el-icon><span>违规处置</span></el-menu-item>
        <el-sub-menu v-if="userStore.roleCode==='ADMIN'" index="/system">
          <template #title><el-icon><Setting /></el-icon><span>系统管理</span></template>
          <el-menu-item index="/system/users"><el-icon><UserFilled /></el-icon>用户管理</el-menu-item>
          <el-menu-item index="/system/roles"><el-icon><Avatar /></el-icon>角色管理</el-menu-item>
          <el-menu-item index="/system/audit"><el-icon><Memo /></el-icon>审计日志</el-menu-item>
          <el-menu-item index="/system/dict"><el-icon><Collection /></el-icon>字典管理</el-menu-item>
        </el-sub-menu>
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
.main-layout { display:flex; height:100vh; background:var(--el-bg-color-page); }
.sidebar { width:220px; display:flex; flex-direction:column; background:rgba(255,255,255,.02); border-right:1px solid var(--el-border-color-lighter); }
.logo { color:var(--el-text-color-primary); font-size:18px; font-weight:bold; padding:20px; cursor:pointer; text-align:center; letter-spacing:1px; display:flex; align-items:center; justify-content:center; gap:8px; }
.logo-icon { color:var(--el-color-primary); font-size:22px; filter: drop-shadow(0 0 6px rgba(64,158,255,.5)); }
.sidebar .el-menu { flex:1; overflow-y:auto; border-right:none; }
.side-menu :deep(.el-menu-item),
.side-menu :deep(.el-sub-menu__title) { margin:2px 10px; border-radius:8px; height:46px; }
.side-menu :deep(.el-menu-item:hover),
.side-menu :deep(.el-sub-menu__title:hover) { background:rgba(64,158,255,.08); }
.side-menu :deep(.el-menu-item.is-active) { background:rgba(64,158,255,.14); }
.side-menu :deep(.el-menu .el-menu-item) { min-width:0; }
.user-info { padding:16px; color:var(--el-text-color-secondary); font-size:13px; display:flex; justify-content:space-between; align-items:center; border-top:1px solid var(--el-border-color-lighter); }
.main-content { flex:1; overflow:hidden; position:relative; }
</style>
