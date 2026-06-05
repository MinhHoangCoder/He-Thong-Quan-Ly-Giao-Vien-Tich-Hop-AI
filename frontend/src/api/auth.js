import http from './http'

// Mỗi resource một file. Đây là ví dụ cho nhóm Authentication (§9.2).
export const authApi = {
  login(payload) {
    // payload: { username, password }
    return http.post('/auth/login', payload)
  },
  refresh(refreshToken) {
    return http.post('/auth/refresh', { refreshToken })
  },
  logout() {
    return http.post('/auth/logout')
  },
}
