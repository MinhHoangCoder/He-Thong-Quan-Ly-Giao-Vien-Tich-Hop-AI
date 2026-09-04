import { fileURLToPath, URL } from 'node:url'
import { createLogger, defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const BACKEND = 'http://127.0.0.1:8080'

// Nuốt stack trace ECONNREFUSED của proxy: dev server đã tự in ra cả chục dòng
// "AggregateError [ECONNREFUSED] at internalConnectMultiple..." cho MỖI request /api khi backend
// chưa lên, che hết log thật. Handler proxy bên dưới in thay một dòng gọn nói rõ phải làm gì.
const logger = createLogger()
const logError = logger.error.bind(logger)
logger.error = (msg, opts) => {
  if (typeof msg === 'string' && msg.includes('http proxy error')) return
  logError(msg, opts)
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  customLogger: logger,
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
      // Mọi request /api/... được chuyển sang backend Spring Boot (cổng 8080).
      //
      // Đích ghi thẳng 127.0.0.1 chứ KHÔNG dùng "localhost": trên Windows localhost phân giải ra
      // cả ::1 lẫn 127.0.0.1, Node thử lần lượt rồi gộp thành AggregateError khó đọc — mà backend
      // bind IPv4 nên nhánh ::1 chỉ tổ chậm thêm.
      '/api': {
        target: BACKEND,
        changeOrigin: true,
        configure: (proxy) => {
          // Backend mất khoảng 15 giây để khởi động, còn Vite lên sau chưa đầy 1 giây → khoảng
          // giữa đó mọi request /api đều bị từ chối kết nối. Mặc định proxy trả về một socket
          // CHẾT (không mã HTTP), axios chỉ thấy "Network Error" nên màn hình báo sai bệnh.
          // Trả 503 kèm JSON đúng hình dạng lỗi của backend để interceptor ở src/api/http.js
          // hiện thẳng câu tiếng Việt, và app tự khỏi khi backend lên (badge chuông poll mỗi 30s).
          let dangHong = false
          proxy.on('error', (err, req, res) => {
            if (!dangHong) {
              dangHong = true
              logger.warn(
                `[api-proxy] Không gọi được backend ${BACKEND} (${err.code || err.message}).` +
                  ' Chạy backend: cd backend && ./mvnw.cmd -DskipTests spring-boot:run',
                { timestamp: true },
              )
            }
            if (!('req' in res)) return res.end() // nhánh websocket: chỉ có socket, không có res
            if (res.headersSent || res.writableEnded) return
            res.writeHead(503, { 'Content-Type': 'application/json; charset=utf-8' })
            res.end(
              JSON.stringify({
                message: 'Không kết nối được tới máy chủ. Kiểm tra backend đã chạy chưa.',
              }),
            )
          })
          proxy.on('proxyRes', () => {
            if (!dangHong) return
            dangHong = false
            logger.info('[api-proxy] Backend đã phản hồi trở lại.', { timestamp: true })
          })
        },
      },
    },
  },
})
