import http from './http'

// Nhóm API CÀI ĐẶT CÁ NHÂN (/api/v1/me/**) — thao tác trên chính tài khoản đang đăng nhập.
// Lưu ý: 2 API sessions dùng POST vì refresh token phải nằm trong BODY,
// tuyệt đối không đưa token lên URL (dính access log / lịch sử trình duyệt).
export const settingsApi = {
  getProfile() {
    return http.get('/me/profile')
  },
  updateProfile(payload) {
    // payload: { email, phone }
    return http.put('/me/profile', payload)
  },
  changePassword(payload) {
    // payload: { currentPassword, newPassword, refreshToken }
    // refreshToken = phiên hiện tại, backend GIỮ LẠI phiên này khi thu hồi các phiên khác.
    return http.post('/me/change-password', payload)
  },
  listSessions(refreshToken) {
    return http.post('/me/sessions', { refreshToken })
  },
  revokeSession(id) {
    return http.delete(`/me/sessions/${id}`)
  },
  revokeOtherSessions(refreshToken) {
    return http.post('/me/sessions/revoke-others', { refreshToken })
  },
}
