import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import cesium from 'vite-plugin-cesium'
import path from 'path'

export default defineConfig({
  plugins: [
    vue(),
    cesium()
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    },
    extensions: ['.mjs', '.js', '.ts', '.vue', '.json']
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:18080',
        changeOrigin: true
      },
      '/ws': {
        target: 'ws://localhost:8090',
        ws: true
      }
    }
  },
  optimizeDeps: {
    include: ['cesium', 'dayjs', 'element-plus/es']
  },
  build: {
    chunkSizeWarningLimit: 4000,
    rollupOptions: {
      output: {
        // cesium 由 vite-plugin-cesium 外部化注入，不能进 manualChunks
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia'],
          element: ['element-plus']
        }
      }
    }
  }
})
