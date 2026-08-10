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
    const res: LoginResult = await loginApi(params)
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
