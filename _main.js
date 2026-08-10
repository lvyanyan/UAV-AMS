import { createApp } from 'vue'
import App from './App.vue'
import './style.css'

// Mock API 服务（只拦截 /api/drone/* 的 fetch 请求，不碰 XHR）
import './utils/apiService.js'

const app = createApp(App)
app.mount('#app')
