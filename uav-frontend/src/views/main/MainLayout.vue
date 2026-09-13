<template>
  <div class="main-layout">
    <aside class="sidebar">
      <div class="logo" @click="$router.push('/dashboard')">
        <span class="logo-badge"><el-icon class="logo-icon"><Promotion /></el-icon></span>
        <span class="logo-text">UAV-AMS</span>
      </div>
      <el-menu :default-active="currentRoute" router background-color="transparent" text-color="#8fa2ba" active-text-color="#7cc7ff" style="border-right:none" class="side-menu">
        <el-menu-item v-for="m in visibleMenus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon><span>{{ t(m.title) }}</span>
        </el-menu-item>
        <el-sub-menu v-if="hasPerm('system:menu')" index="/system">
          <template #title><el-icon><Setting /></el-icon><span>{{ t('menu.system') }}</span></template>
          <el-menu-item v-for="c in visibleSystemChildren" :key="c.path" :index="c.path">
            <el-icon><component :is="c.icon" /></el-icon>{{ t(c.title) }}
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
      <div class="sidebar-foot">{{ t('app.slogan') }}</div>
    </aside>

    <div class="right-area">
      <header class="topbar">
        <div class="tb-title">
          <span class="tb-crumb">{{ currentTitle }}</span>
        </div>
        <div class="tb-right">
          <span class="tb-clock">{{ clock }}</span>
          <span v-if="linkActive" class="tb-link" :class="{ on: wsConnected }">
            <i class="dot" />{{ wsConnected ? t('app.liveLink') : t('app.linkDown') }}
          </span>
          <el-dropdown @command="cmd => setLocale(cmd as Locale)">
            <span class="tb-lang">
              {{ locale === 'en' ? 'EN' : '中文' }}
              <el-icon class="tb-caret"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="zh-CN" :disabled="locale === 'zh-CN'">中文</el-dropdown-item>
                <el-dropdown-item command="en" :disabled="locale === 'en'">English</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-dropdown @command="cmd => cmd === 'logout' && handleLogout()">
            <span class="tb-user">
              <el-icon><UserFilled /></el-icon>
              {{ userStore.realName || userStore.username }}
              <el-icon class="tb-caret"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">{{ t('app.logout') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <main class="main-content"><router-view /></main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { wsClient } from '@/api/websocket'
import { setLocale, locale, type Locale } from '@/locales'
import { ArrowDown, UserFilled } from '@element-plus/icons-vue'

// 菜单配置：perm 为 RBAC 菜单权限码（登录响应下发），无权限自动隐藏；title 为 i18n key
interface MenuItem { path: string; title: string; icon: string; perm: string }

const MENUS: MenuItem[] = [
  { path: '/dashboard',      title: 'menu.dashboard', icon: 'Odometer',            perm: 'dashboard:menu' },
  { path: '/flight-monitor', title: 'menu.monitor',   icon: 'Monitor',             perm: 'monitor:menu' },
  { path: '/message-replay', title: 'menu.replay',    icon: 'VideoPlay',           perm: 'replay:menu' },
  { path: '/flight-plan',    title: 'menu.plan',      icon: 'Document',            perm: 'flightplan:menu' },
  { path: '/airspace',       title: 'menu.airspace',  icon: 'MapLocation',         perm: 'airspace:menu' },
  { path: '/air-route',      title: 'menu.airroute',  icon: 'Guide',               perm: 'airroute:menu' },
  { path: '/airport',        title: 'menu.airport',   icon: 'LocationInformation', perm: 'airport:menu' },
  { path: '/registry',       title: 'menu.registry',  icon: 'Files',               perm: 'registry:menu' },
  { path: '/pilot',          title: 'menu.pilot',     icon: 'UserFilled',          perm: 'pilot:menu' },
  { path: '/alarm',          title: 'menu.alarm',     icon: 'Bell',                perm: 'alarm:menu' },
  { path: '/violation',      title: 'menu.violation', icon: 'WarningFilled',       perm: 'violation:menu' },
  { path: '/military',       title: 'menu.military',  icon: 'Medal',               perm: 'military:menu' },
]

const SYSTEM_CHILDREN: MenuItem[] = [
  { path: '/system/users', title: 'menu.users', icon: 'UserFilled',  perm: 'system:user:menu' },
  { path: '/system/roles', title: 'menu.roles', icon: 'Avatar',      perm: 'system:role:menu' },
  { path: '/system/audit', title: 'menu.audit', icon: 'Memo',        perm: 'system:audit:menu' },
  { path: '/system/dict',  title: 'menu.dict',  icon: 'Collection',  perm: 'system:dict:menu' },
]

const { t } = useI18n()
const route = useRoute(); const router = useRouter(); const userStore = useUserStore()
const hasPerm = (perm: string) => userStore.hasPerm(perm)

const visibleMenus = computed(() => MENUS.filter(m => hasPerm(m.perm)))
const visibleSystemChildren = computed(() => SYSTEM_CHILDREN.filter(c => hasPerm(c.perm)))
const currentRoute = computed(() => route.path)
const currentTitle = computed(() => {
  const all = [...MENUS, ...SYSTEM_CHILDREN]
  const hit = all.find(m => m.path === route.path)
  if (hit) return t(hit.title)
  if (route.path.startsWith('/system')) return t('menu.system')
  return route.meta.title ? t(String(route.meta.title)) : t('app.title')
})

// 顶栏实时时钟 + WS 链路状态（时钟跟随 locale）
const clock = ref('')
// linkActive：本会话建立过 WS 连接才显示链路指示，避免"未启用"被误报成"链路中断"
const wsConnected = ref(wsClient.connected)
const linkActive = ref(wsClient.connected || wsClient.everConnected)
let clockTimer: any = null
let offWsStatus: (() => void) | null = null

function tickClock() {
  const d = new Date()
  if (locale.value === 'en') {
    clock.value = d.toLocaleString('en-US', { hour12: false, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
  } else {
    const p = (n: number) => String(n).padStart(2, '0')
    clock.value = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
  }
}

onMounted(() => {
  tickClock()
  clockTimer = setInterval(tickClock, 1000)
  offWsStatus = wsClient.onStatus(ok => {
    wsConnected.value = ok
    if (ok) linkActive.value = true
  })
})
onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer)
  if (offWsStatus) offWsStatus()
})

function handleLogout() { userStore.logout(); router.push('/login') }
</script>

<style scoped>
.main-layout { display:flex; height:100vh; background:var(--el-bg-color-page); }

/* ---- 侧边栏 ---- */
.sidebar {
  width:220px; display:flex; flex-direction:column;
  background: linear-gradient(180deg, rgba(16, 27, 48, 0.9), rgba(8, 14, 26, 0.95));
  border-right:1px solid rgba(47, 129, 247, 0.18);
  backdrop-filter: blur(10px);
}
.logo { padding:20px 16px 18px; cursor:pointer; display:flex; align-items:center; justify-content:center; gap:10px; }
.logo-badge {
  width:34px; height:34px; border-radius:10px; display:flex; align-items:center; justify-content:center;
  background: linear-gradient(135deg, #2f81f7, #22d3ee);
  box-shadow: 0 0 16px rgba(34, 211, 238, 0.45);
}
.logo-icon { color:#fff; font-size:19px; }
.logo-text {
  color:var(--el-text-color-primary); font-size:19px; font-weight:700; letter-spacing:2px;
  background: linear-gradient(100deg, #eaf3ff 10%, #7cc7ff 60%, #22d3ee 100%);
  -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
}
.sidebar .el-menu { flex:1; overflow-y:auto; border-right:none; }
.side-menu :deep(.el-menu-item),
.side-menu :deep(.el-sub-menu__title) { margin:2px 10px; border-radius:8px; height:44px; position:relative; transition: background .2s ease, color .2s ease; }
.side-menu :deep(.el-menu-item:hover),
.side-menu :deep(.el-sub-menu__title:hover) { background:rgba(47, 129, 247, 0.10); color:#cfe4ff; }
.side-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(47, 129, 247, 0.22), rgba(34, 211, 238, 0.06));
  color:#7cc7ff;
  box-shadow: inset 3px 0 0 #22d3ee, 0 0 12px rgba(34, 211, 238, 0.12);
}
.side-menu :deep(.el-menu .el-menu-item) { min-width:0; }
.sidebar-foot {
  padding:14px; text-align:center; font-size:11px; letter-spacing:3px;
  color:#46587a; border-top:1px solid rgba(47, 129, 247, 0.14);
}

/* ---- 右侧：顶栏 + 内容 ---- */
.right-area { flex:1; display:flex; flex-direction:column; min-width:0; }
.topbar {
  height:52px; flex-shrink:0; display:flex; align-items:center; justify-content:space-between;
  padding:0 20px;
  background: linear-gradient(90deg, rgba(16, 27, 48, 0.85), rgba(11, 19, 34, 0.6));
  border-bottom:1px solid rgba(47, 129, 247, 0.18);
  backdrop-filter: blur(10px);
}
.tb-crumb {
  font-size:15px; font-weight:600; letter-spacing:1.5px;
  background: linear-gradient(100deg, #eaf3ff 30%, #7cc7ff 90%);
  -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
}
.tb-crumb::before {
  content:''; display:inline-block; width:6px; height:6px; border-radius:50%;
  background:#22d3ee; box-shadow:0 0 8px #22d3ee; margin-right:10px; vertical-align:2px;
}
.tb-right { display:flex; align-items:center; gap:18px; }
.tb-clock { color:#8fa1bc; font-size:13px; letter-spacing:0.5px; font-variant-numeric: tabular-nums; }
.tb-link { display:inline-flex; align-items:center; gap:6px; font-size:12px; color:#5b6d86; }
.tb-link .dot { width:7px; height:7px; border-radius:50%; background:#5b6d86; }
.tb-link.on { color:#34d399; }
.tb-link.on .dot { background:#34d399; box-shadow:0 0 8px rgba(52, 211, 153, 0.8); animation: linkPulse 2s ease-in-out infinite; }
@keyframes linkPulse { 0%, 100% { opacity:1; } 50% { opacity:0.45; } }
.tb-user, .tb-lang {
  display:inline-flex; align-items:center; gap:6px; cursor:pointer; font-size:13px;
  color:#c8d5e6; padding:6px 10px; border-radius:8px; border:1px solid rgba(47, 129, 247, 0.22);
  background: rgba(47, 129, 247, 0.08); transition: border-color .2s, box-shadow .2s;
}
.tb-user:hover, .tb-lang:hover { border-color: rgba(34, 211, 238, 0.5); box-shadow: 0 0 10px rgba(34, 211, 238, 0.15); }
.tb-lang { font-size:12px; padding:5px 9px; }
.tb-caret { font-size:12px; color:#8fa1bc; }

.main-content { flex:1; overflow:hidden; position:relative; }
</style>
