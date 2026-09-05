import http from './http'

/**
 * API module ĐƠN XIN NGHỈ DẠY (V39).
 * Base: /api/v1/leave-requests
 *
 * Phần của giáo viên không cần quyền quản trị — backend tự ép phạm vi về hồ sơ giáo viên
 * của người đang đăng nhập.
 */
export const leaveRequestApi = {
  /** Các phân công ĐANG DẠY của chính mình — nguồn cho ô chọn khi gửi đơn. */
  myAssignments() {
    return http.get('/leave-requests/my-assignments')
  },

  /** Đơn của tôi (mọi trạng thái), mới nhất trước. */
  mine() {
    return http.get('/leave-requests/mine')
  },

  /** Gửi đơn xin nghỉ một phân công kể từ ngày effectiveDate. */
  create({ assignmentId, effectiveDate, reason }) {
    return http.post('/leave-requests', { assignmentId, effectiveDate, reason })
  },

  /** (Admin) Duyệt đơn → phân công bị hủy từ ngày trong đơn. */
  approve(id, note) {
    return http.post(`/leave-requests/${id}/approve`, { note: note || null })
  },

  /** (Admin) Từ chối đơn — bắt buộc nêu lý do. */
  reject(id, note) {
    return http.post(`/leave-requests/${id}/reject`, { note })
  },
}
