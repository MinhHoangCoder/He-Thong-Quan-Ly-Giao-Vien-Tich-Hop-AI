import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

// Instance axios dùng chung. baseURL '/api/v1' -> vite proxy chuyển sang backend :8080.
const http = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
})

// Tự đính kèm Authorization: Bearer <access token> vào mọi request (nếu có).
http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  return config
})

// Tránh nhiều request 401 cùng lúc gọi /refresh song song: gom về 1 promise.
let refreshingPromise = null

// Bắt 401 -> thử dùng refresh token xin access token mới rồi GỌI LẠI request gốc 1 lần.
http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const auth = useAuthStore()
    const original = error.config

    const canRetry = error.response?.status === 401 && !original?._retry && auth.refreshToken
    if (!canRetry) {
      return Promise.reject(error)
    }

    original._retry = true
    try {
      // Gọi /refresh bằng axios "trần" (không qua interceptor) để tránh vòng lặp.
      refreshingPromise =
        refreshingPromise || axios.post('/api/v1/auth/refresh', { refreshToken: auth.refreshToken })
      const { data } = await refreshingPromise
      refreshingPromise = null

      auth.setSession({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        user: data.user,
      })
      original.headers.Authorization = `Bearer ${data.accessToken}`
      return http(original) // phát lại request gốc
    } catch (e) {
      refreshingPromise = null
      // Chỉ gỡ tài khoản có refresh token chết; các tài khoản khác (nếu có) giữ nguyên.
      // Reload về /login: guard sẽ tự đưa về "nhà" của tài khoản kế tiếp, hết thì ở login.
      auth.dropActiveAccount()
      window.location.assign('/login')
      return Promise.reject(e)
    }
  },
)

export default http
