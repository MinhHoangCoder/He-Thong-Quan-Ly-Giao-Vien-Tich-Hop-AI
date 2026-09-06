import http from './http'

/**
 * API module ĐƠN XIN NGHỈ MỘT BUỔI DẠY (bảng V39).
 * Base: /api/v1/leave-requests
 *
 * Phần của giáo viên không cần quyền quản trị — backend tự ép phạm vi về hồ sơ giáo viên
 * của người đang đăng nhập.
 */
export const leaveRequestApi = {
  /** Các BUỔI dạy sắp tới của chính mình — nguồn cho ô chọn khi gửi đơn. */
  mySessions() {
    return http.get('/leave-requests/my-sessions')
  },

  /** Đơn của tôi (mọi trạng thái), mới nhất trước. */
  mine() {
    return http.get('/leave-requests/mine')
  },

  /** Gửi đơn xin nghỉ buổi ngày leaveDate của một phân công. */
  create({ assignmentId, leaveDate, reason }) {
    return http.post('/leave-requests', { assignmentId, leaveDate, reason })
  },

  /** (Admin) Hàng đợi đơn đang chờ duyệt. */
  pending() {
    return http.get('/leave-requests/pending')
  },

  /** (Admin) Một đơn theo id — mở thẳng hộp thoại duyệt từ dòng thông báo. */
  detail(id) {
    return http.get(`/leave-requests/${id}`)
  },

  /** (Admin) Duyệt đơn → buổi hôm đó chuyển "Nghỉ có phép", phân công giữ nguyên. */
  approve(id, note) {
    return http.post(`/leave-requests/${id}/approve`, { note: note || null })
  },

  /** (Admin) Từ chối đơn — bắt buộc nêu lý do. */
  reject(id, note) {
    return http.post(`/leave-requests/${id}/reject`, { note })
  },
}
