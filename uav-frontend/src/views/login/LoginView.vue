<template>
  <div class="login-container">
    <!-- 背景装饰：网格 / 光晕 / 扫描线 -->
    <div class="bg-grid" />
    <div class="bg-glow bg-glow-1" />
    <div class="bg-glow bg-glow-2" />
    <div class="bg-scanline" />

    <div class="login-card">
      <div class="brand">
        <div class="brand-icon">
          <el-icon :size="26"><Promotion /></el-icon>
        </div>
        <h2>UAV-AMS</h2>
        <p class="subtitle">{{ $t('app.subtitle') }}</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" :placeholder="$t('login.username')" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" :placeholder="$t('login.password')"
            :prefix-icon="Lock" @keyup.enter="handleLogin" show-password />
        </el-form-item>
        <el-form-item v-if="showMfa" prop="mfaCode">
          <el-input v-model="form.mfaCode" :placeholder="$t('login.mfaCode')" maxlength="6" />
        </el-form-item>
        <el-checkbox v-model="form.militaryLogin" class="military-check">
          {{ $t('login.militaryCheck') }}
        </el-checkbox>
        <el-button type="primary" :loading="loading" @click="handleLogin" class="login-btn">
          {{ loading ? $t('login.logging') : $t('login.submit') }}
        </el-button>
      </el-form>
      <div class="hint">{{ $t('login.hint') }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const { t } = useI18n()
const userStore = useUserStore()
const loading = ref(false)

const form = ref({ username: 'admin', password: 'admin123', mfaCode: '', militaryLogin: false })
const showMfa = computed(() => form.value.militaryLogin)
const rules = computed(() => ({
  username: [{ required: true, message: t('login.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.passwordRequired'), trigger: 'blur' }]
}))

async function handleLogin() {
  loading.value = true
  try {
    await userStore.doLogin({
      username: form.value.username,
      password: form.value.password,
      mfaCode: form.value.militaryLogin ? (form.value.mfaCode || '123456') : undefined,
      militaryLogin: form.value.militaryLogin
    })
    ElMessage.success(t('login.success'))
    router.push('/dashboard')
  } catch (e: any) { /* handled by interceptor */ }
  finally { loading.value = false }
}
</script>

<style scoped>
.login-container {
  width: 100vw; height: 100vh; position: relative; overflow: hidden;
  display: flex; align-items: center; justify-content: center;
  /* AI 生成的低空城市夜景做底，叠加深色遮罩保证表单可读 */
  background:
    linear-gradient(rgba(6, 11, 20, 0.62), rgba(6, 11, 20, 0.8)),
    url('@/assets/login-bg.jpg') center/cover no-repeat,
    #060b14;
}

/* 透视网格地面 */
.bg-grid {
  position: absolute; inset: -60% -20% 0;
  background:
    linear-gradient(rgba(56, 189, 248, 0.10) 1px, transparent 1px),
    linear-gradient(90deg, rgba(56, 189, 248, 0.10) 1px, transparent 1px);
  background-size: 48px 48px;
  transform: perspective(700px) rotateX(58deg) translateY(10%);
  transform-origin: center top;
  mask-image: linear-gradient(to bottom, transparent 30%, rgba(0,0,0,.8) 75%, #000 96%);
  -webkit-mask-image: linear-gradient(to bottom, transparent 30%, rgba(0,0,0,.8) 75%, #000 96%);
  animation: gridMove 14s linear infinite;
  pointer-events: none;
}
@keyframes gridMove { from { background-position: 0 0; } to { background-position: 0 48px; } }

/* 呼吸光晕 */
.bg-glow { position: absolute; border-radius: 50%; filter: blur(90px); pointer-events: none; }
.bg-glow-1 { width: 420px; height: 420px; left: 12%; top: 8%; background: rgba(47, 129, 247, 0.14); animation: breathe 7s ease-in-out infinite; }
.bg-glow-2 { width: 360px; height: 360px; right: 10%; bottom: 12%; background: rgba(34, 211, 238, 0.10); animation: breathe 9s ease-in-out infinite 1.5s; }
@keyframes breathe { 0%, 100% { opacity: .55; transform: scale(1); } 50% { opacity: 1; transform: scale(1.12); } }

/* 雷达扫描线 */
.bg-scanline {
  position: absolute; left: 0; right: 0; height: 140px; pointer-events: none;
  background: linear-gradient(to bottom, transparent, rgba(34, 211, 238, 0.05), transparent);
  animation: scan 9s linear infinite;
}
@keyframes scan { from { top: -140px; } to { top: 100vh; } }

.login-card {
  position: relative; z-index: 1;
  width: 420px; padding: 44px 40px 32px;
  background: linear-gradient(180deg, rgba(18, 30, 52, 0.82), rgba(9, 16, 29, 0.88));
  border-radius: 18px;
  border: 1px solid rgba(47, 129, 247, 0.30);
  border-top: 1px solid rgba(34, 211, 238, 0.55);
  backdrop-filter: blur(16px);
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.6), 0 0 26px rgba(47, 129, 247, 0.18);
}
/* 卡片四角光标 */
.login-card::before, .login-card::after {
  content: ''; position: absolute; width: 18px; height: 18px; pointer-events: none;
}
.login-card::before {
  top: -1px; left: -1px;
  border-top: 2px solid var(--uav-cyan, #22d3ee); border-left: 2px solid var(--uav-cyan, #22d3ee);
  border-top-left-radius: 6px;
}
.login-card::after {
  bottom: -1px; right: -1px;
  border-bottom: 2px solid var(--uav-cyan, #22d3ee); border-right: 2px solid var(--uav-cyan, #22d3ee);
  border-bottom-right-radius: 6px;
}

.brand { text-align: center; margin-bottom: 30px; }
.brand-icon {
  width: 58px; height: 58px; margin: 0 auto 14px; border-radius: 16px;
  display: flex; align-items: center; justify-content: center; color: #fff;
  background: linear-gradient(135deg, #2f81f7, #22d3ee);
  box-shadow: 0 0 24px rgba(34, 211, 238, 0.5);
  animation: iconFloat 4s ease-in-out infinite;
}
@keyframes iconFloat { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-5px); } }
.brand h2 {
  margin: 0; font-size: 30px; letter-spacing: 4px; font-weight: 700;
  background: linear-gradient(100deg, #eaf3ff 20%, #7cc7ff 55%, #22d3ee 90%);
  -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
}
.subtitle { color: #8fa1bc; text-align: center; margin: 8px 0 0; font-size: 14px; letter-spacing: 2px; }

.military-check { margin-bottom: 16px; color: #c8d5e6; }
.login-btn { width: 100%; letter-spacing: 6px; font-size: 15px; }

.hint { color: #5b6d86; text-align: center; margin-top: 22px; font-size: 12px; letter-spacing: 0.3px; }
</style>
