import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import '@/styles/theme.css'
import App from './App.vue'
import router from './router'
import { permission } from './directives/permission'
import i18n from './locales'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(i18n)
// Element Plus 组件文案 locale 由 App.vue 的 <el-config-provider> 跟随 i18n 切换
app.use(ElementPlus)
// 按钮级权限指令：v-permission="'system:user:create'"
app.directive('permission', permission)
for (const [name, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, comp)
}
// 全站深色主题（与大屏外壳同色系）
document.documentElement.classList.add('dark')
app.mount('#app')
