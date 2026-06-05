import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

// Instance axios dùng chung. baseURL '/api/v1' -> vite proxy chuyển sang backend.
const http = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
})

// Tự đính kèm Authorization: Bearer <token> vào mọi request (nếu đã đăng nhập).
http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  return config
})

// Xử lý lỗi chung. TODO: khi có refresh token, bắt 401 ở đây để tự làm mới.
http.interceptors.response.use(
  (response) => response,
  (error) => {
    // if (error.response?.status === 401) { ... refresh ... }
    return Promise.reject(error)
  },
)

export default http
