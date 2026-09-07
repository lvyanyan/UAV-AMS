import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, type LoginParams, type LoginResult } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const username = ref('')
  const realName = ref('')
  const roleCode = ref('')
  const isMilitary = ref(false)

  async function doLogin(params: LoginParams) {
    // axios 拦截器返回 R 信封，实际载荷在 data 中（兼容直接返回载荷的情况）
    const env: any = await loginApi(params)
    const res: LoginResult = (env && env.data) ? env.data : env
    token.value = res.token
    username.value = res.username
    realName.value = res.realName
    roleCode.value = res.roleCode
    isMilitary.value = res.militaryLogin
    localStorage.setItem('token', res.token)
  }

  function logout() {
    token.value = ''
    username.value = ''
    localStorage.removeItem('token')
  }

  return { token, username, realName, roleCode, isMilitary, doLogin, logout }
})
