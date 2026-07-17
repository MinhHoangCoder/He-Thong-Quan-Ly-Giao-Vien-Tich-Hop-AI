import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // 5173 bận thì báo lỗi và DỪNG, không âm thầm né sang cổng khác:
    // backend chỉ cho phép CORS origin http://localhost:5173 (SecurityConfig),
    // chạy nhầm cổng khác là mọi API dính 403 "Invalid CORS request".
    strictPort: true,
    proxy: {
      // Mọi request /api/... được chuyển sang backend Spring Boot (cổng 8080)
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
