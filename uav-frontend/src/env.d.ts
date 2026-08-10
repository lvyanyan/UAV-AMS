/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

// Cesium 静态资源路径（vite-plugin-cesium 自动注入）
interface Window {
  CESIUM_BASE_URL: string
}
