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

// ============================================================================
// LÀM MỚI PHIÊN — gom MỌI nơi (interceptor 401 + App.vue lúc F5) về đúng 1 promise.
//
// Vì sao bắt buộc: backend XOAY refresh token (dùng 1 lần) + có reuse-detection —
// token cũ bị gửi lần thứ 2 sẽ coi là bị đánh cắp và THU HỒI TOÀN BỘ phiên.
// Trước đây App.vue và interceptor gọi /refresh SONG SONG với cùng token cũ khi F5
// -> cuộc gọi thứ 2 dính reuse-detection -> user bị văng đăng nhập toàn bộ thiết bị.
// ============================================================================
let refreshingPromise = null

/** Xin cặp token mới từ refresh token hiện có (dedupe: nhiều nơi gọi = 1 request). */
export function refreshSession() {
  const auth = useAuthStore()
  if (!auth.refreshToken) {
    return Promise.reject(new Error('Không có refresh token'))
  }
  if (!refreshingPromise) {
    // Gọi bằng axios "trần" (không qua interceptor) để tránh vòng lặp 401 -> refresh.
    refreshingPromise = axios
      .post('/api/v1/auth/refresh', { refreshToken: auth.refreshToken })
      .then(({ data }) => {
        auth.setSession({
          accessToken: data.accessToken,
          refreshToken: data.refreshToken,
          user: data.user,
        })
        return data
      })
      .finally(() => {
        refreshingPromise = null
      })
  }
  return refreshingPromise
}

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
      const data = await refreshSession()
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
