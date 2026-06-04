import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 开发环境下将 /api 代理到后端服务
      '/api': {
        target: 'http://localhost:8088',
        changeOrigin: true
      }
    }
  }
})
