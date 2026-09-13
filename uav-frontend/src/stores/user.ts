import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, type LoginParams, type LoginResult } from '@/api/auth'

const PERMS_KEY = 'permissions'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const username = ref('')
  const realName = ref('')
  const roleCode = ref('')
  const isMilitary = ref(false)
  // 当前角色权限码列表（域:资源:操作），登录响应带出并落 localStorage 以支持刷新恢复
  const permissions = ref<string[]>(readPerms())

  function readPerms(): string[] {
    try {
      const raw = localStorage.getItem(PERMS_KEY)
      const arr = raw ? JSON.parse(raw) : []
      return Array.isArray(arr) ? arr : []
    } catch {
      return []
    }
  }

  async function doLogin(params: LoginParams) {
    // axios 拦截器返回 R 信封，实际载荷在 data 中（兼容直接返回载荷的情况）
    const env: any = await loginApi(params)
    const res: LoginResult = (env && env.data) ? env.data : env
    token.value = res.token
    username.value = res.username
    realName.value = res.realName
    roleCode.value = res.roleCode
    isMilitary.value = res.militaryLogin
    permissions.value = (res as any).permissions || []
    localStorage.setItem('token', res.token)
    localStorage.setItem(PERMS_KEY, JSON.stringify(permissions.value))
  }

  /** 是否持有某权限码；未传码视为放行（用于 v-permission 与路由守卫） */
  function hasPerm(code?: string): boolean {
    if (!code) return true
    return permissions.value.includes(code)
  }

  function logout() {
    token.value = ''
    username.value = ''
    permissions.value = []
    localStorage.removeItem('token')
    localStorage.removeItem(PERMS_KEY)
  }

  return { token, username, realName, roleCode, isMilitary, permissions, doLogin, logout, hasPerm }
})
