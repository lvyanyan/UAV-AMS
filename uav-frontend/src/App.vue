<template>
  <el-config-provider :locale="epLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import enEp from 'element-plus/dist/locale/en.mjs'
import { i18n } from '@/locales'

// Element Plus 组件文案跟随 i18n locale
const epLocale = computed(() => (i18n.global.locale.value === 'en' ? enEp : zhCn))
</script>

<style>
html, body, #app {
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 0;
  overflow: hidden;
  font-family: 'HarmonyOS Sans SC', 'PingFang SC', 'Microsoft YaHei', 'Segoe UI', system-ui, sans-serif;
  font-variant-numeric: tabular-nums;
  -webkit-font-smoothing: antialiased;
}
</style>
