import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import cesium from 'vite-plugin-cesium'

// 复用主项目 uav-frontend 的 Cesium 工程模式：vite-plugin-cesium 自动注入
// CESIUM_BASE_URL、拷贝静态资源（Workers/Assets/Widgets）。
export default defineConfig({
  base: './',
  plugins: [vue(), cesium()],
  server: {
    port: 5173,
    proxy: {
      // 本地 dev：/resize（HTTP）与 /stress（WS）都代理到 Go 服务端；线上由 Caddy 同源反代
      '/resize': { target: 'http://localhost:8099', changeOrigin: true },
      '/stress': { target: 'http://localhost:8099', ws: true, changeOrigin: true }
    }
  },
  optimizeDeps: { include: ['cesium'] },
  build: {
    chunkSizeWarningLimit: 4000
    // 注：vite-plugin-cesium 把 cesium 标记为 external，不能放进 manualChunks
  }
})
