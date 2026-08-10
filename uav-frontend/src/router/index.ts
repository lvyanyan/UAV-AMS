import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/views/main/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/main/Dashboard.vue'), meta: { title: '仪表盘' } },
      { path: 'flight-monitor', name: 'FlightMonitor', component: () => import('@/views/main/FlightMonitor.vue'), meta: { title: '飞行监控' } },
      { path: 'flight-plan', name: 'FlightPlan', component: () => import('@/views/main/FlightPlan.vue'), meta: { title: '飞行计划' } },
      { path: 'airspace', name: 'Airspace', component: () => import('@/views/main/Airspace.vue'), meta: { title: '空域管理' } },
      { path: 'registry', name: 'Registry', component: () => import('@/views/main/Registry.vue'), meta: { title: '实名登记' } },
      { path: 'pilot', name: 'Pilot', component: () => import('@/views/main/Pilot.vue'), meta: { title: '驾驶员管理' } },
      { path: 'alarm', name: 'Alarm', component: () => import('@/views/main/AlarmCenter.vue'), meta: { title: '告警中心' } },
      { path: 'violation', name: 'Violation', component: () => import('@/views/main/Violation.vue'), meta: { title: '违规处置' } },
      { path: 'system', name: 'System', component: () => import('@/views/main/System.vue'), meta: { title: '系统管理' } },
      { path: 'military', name: 'Military', component: () => import('@/views/main/Military.vue'), meta: { title: '军事调度', role: 'MILITARY' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Auth guard
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  if (to.path === '/login') {
    next()
  } else if (!token) {
    next('/login')
  } else {
    next()
  }
})

export default router
