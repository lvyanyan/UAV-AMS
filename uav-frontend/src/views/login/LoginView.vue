<template>
  <div class="login-container">
    <div class="login-card">
      <h2><el-icon style="vertical-align:-3px;color:var(--el-color-primary)"><Promotion /></el-icon> UAV-AMS</h2>
      <p class="subtitle">无人机数字孪生监管平台</p>
      <el-form ref="formRef" :model="form" :rules="rules" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码"
            @keyup.enter="handleLogin" show-password />
        </el-form-item>
        <el-form-item v-if="showMfa" prop="mfaCode">
          <el-input v-model="form.mfaCode" placeholder="二次验证码 (开发: 123456)" maxlength="6" />
        </el-form-item>
        <el-checkbox v-model="form.militaryLogin" style="margin-bottom:16px;color:#fff">
          军民协调员登录 (需二次验证)
        </el-checkbox>
        <el-button type="primary" :loading="loading" @click="handleLogin" style="width:100%">
          {{ loading ? '登录中...' : '登 录' }}
        </el-button>
      </el-form>
      <div class="hint">默认账户: admin / admin123 | military01 / military123</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const form = ref({ username: 'admin', password: 'admin123', mfaCode: '', militaryLogin: false })
const showMfa = computed(() => form.value.militaryLogin)
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  loading.value = true
  try {
    await userStore.doLogin({
      username: form.value.username,
      password: form.value.password,
      mfaCode: form.value.militaryLogin ? (form.value.mfaCode || '123456') : undefined,
      militaryLogin: form.value.militaryLogin
    })
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e: any) { /* handled by interceptor */ }
  finally { loading.value = false }
}
</script>

<style scoped>
.login-container { width:100vw; height:100vh; background:linear-gradient(135deg,#0f1923,#1a2a3a,#0d1b2a); display:flex; align-items:center; justify-content:center; }
.login-card { width:420px; padding:40px; background:rgba(255,255,255,.06); border-radius:16px; border:1px solid rgba(255,255,255,.1); backdrop-filter:blur(10px); }
.login-card h2 { color:#fff; text-align:center; margin-bottom:4px; font-size:28px; }
.subtitle { color:#889; text-align:center; margin-bottom:32px; font-size:14px; }
.hint { color:#556; text-align:center; margin-top:20px; font-size:12px; }
</style>
