import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { i18n } from '@/locales'

// meta.permission = 访问该路由所需的菜单权限码（域:资源:操作），由 RBAC 登录响应下发
// meta.title = i18n key（渲染处用 t() 翻译）
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: 'menu.login' }
  },
  {
    path: '/',
    component: () => import('@/views/main/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/main/Dashboard.vue'), meta: { title: 'menu.dashboard', permission: 'dashboard:menu' } },
      { path: 'flight-monitor', name: 'FlightMonitor', component: () => import('@/views/main/FlightMonitor.vue'), meta: { title: 'menu.monitor', permission: 'monitor:menu' } },
      { path: 'message-replay', name: 'MessageReplay', component: () => import('@/views/main/MessageReplay.vue'), meta: { title: 'menu.replay', permission: 'replay:menu' } },
      { path: 'flight-plan', name: 'FlightPlan', component: () => import('@/views/main/FlightPlan.vue'), meta: { title: 'menu.plan', permission: 'flightplan:menu' } },
      { path: 'airspace', name: 'Airspace', component: () => import('@/views/main/Airspace.vue'), meta: { title: 'menu.airspace', permission: 'airspace:menu' } },
      { path: 'air-route', name: 'AirRoute', component: () => import('@/views/main/AirRoute.vue'), meta: { title: 'menu.airroute', permission: 'airroute:menu' } },
      { path: 'airport', name: 'Airport', component: () => import('@/views/main/Airport.vue'), meta: { title: 'menu.airport', permission: 'airport:menu' } },
      { path: 'registry', name: 'Registry', component: () => import('@/views/main/Registry.vue'), meta: { title: 'menu.registry', permission: 'registry:menu' } },
      { path: 'pilot', name: 'Pilot', component: () => import('@/views/main/Pilot.vue'), meta: { title: 'menu.pilot', permission: 'pilot:menu' } },
      { path: 'alarm', name: 'Alarm', component: () => import('@/views/main/AlarmCenter.vue'), meta: { title: 'menu.alarm', permission: 'alarm:menu' } },
      { path: 'violation', name: 'Violation', component: () => import('@/views/main/Violation.vue'), meta: { title: 'menu.violation', permission: 'violation:menu' } },
      { path: 'system', redirect: '/system/users', meta: { title: 'menu.system', permission: 'system:menu' } },
      { path: 'system/users', name: 'SystemUsers', component: () => import('@/views/main/SystemUsers.vue'), meta: { title: 'menu.users', permission: 'system:user:menu' } },
      { path: 'system/roles', name: 'SystemRoles', component: () => import('@/views/main/SystemRoles.vue'), meta: { title: 'menu.roles', permission: 'system:role:menu' } },
      { path: 'system/audit', name: 'SystemAudit', component: () => import('@/views/main/SystemAudit.vue'), meta: { title: 'menu.audit', permission: 'system:audit:menu' } },
      { path: 'system/dict', name: 'SystemDict', component: () => import('@/views/main/SystemDict.vue'), meta: { title: 'menu.dict', permission: 'system:dict:menu' } },
      { path: 'military', name: 'Military', component: () => import('@/views/main/Military.vue'), meta: { title: 'menu.military', permission: 'military:menu' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Auth + RBAC guard
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  if (to.path === '/login') {
    next()
    return
  }
  if (!token) {
    next('/login')
    return
  }
  // 菜单权限校验：无对应权限码时提示并回落到工作台（dashboard 自身不再拦截，避免循环重定向）
  const perm = to.meta.permission as string | undefined
  if (perm) {
    const userStore = useUserStore()
    if (!userStore.hasPerm(perm)) {
      ElMessage.warning(i18n.global.t('common.noPermission'))
      if (to.path === '/dashboard') {
        next()
      } else {
        next('/dashboard')
      }
      return
    }
  }
  next()
})

export default router
