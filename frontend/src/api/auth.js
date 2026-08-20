import http from './http'

// Nhóm API Authentication. baseURL '/api/v1' đã gắn sẵn trong http.js.
export const authApi = {
  login(payload) {
    // payload: { username, password }
    return http.post('/auth/login', payload)
  },
  refresh(refreshToken) {
    return http.post('/auth/refresh', { refreshToken })
  },
  logout(refreshToken) {
    return http.post('/auth/logout', { refreshToken })
  },
  // Tạo tài khoản giáo viên (chỉ ADMIN — từ V31 trường không còn là người dùng).
  register(payload) {
    // payload: { role, username, email, fullName, password, phone, branchId }
    return http.post('/auth/register', payload)
  },
  forgotPassword(email) {
    return http.post('/auth/forgot-password', { email })
  },
  resetPassword(payload) {
    // payload: { token, newPassword }
    return http.post('/auth/reset-password', payload)
  },
  me() {
    return http.get('/auth/me')
  },
}
