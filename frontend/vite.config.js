import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 前端 5174(避开智耕云枢前端占用的 5173),/api 代理到智问后端 8091
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://localhost:8091',
        changeOrigin: true
      }
    }
  }
})
