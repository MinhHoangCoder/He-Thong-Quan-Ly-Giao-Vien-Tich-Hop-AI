import http from './http'

/**
 * API module Thông báo (chuông trên topbar).
 * Base: /api/v1/notifications
 */
export const notificationApi = {
  /** Danh sách thông báo + số chưa đọc. */
  list() {
    return http.get('/notifications')
  },

  /** Chỉ số chưa đọc (poll nhẹ cho badge). */
  unreadCount() {
    return http.get('/notifications/unread-count')
  },

  /** Đánh dấu 1 thông báo đã đọc. */
  markRead(id) {
    return http.post(`/notifications/${id}/read`)
  },

  /** Đánh dấu tất cả đã đọc. */
  markAllRead() {
    return http.post('/notifications/read-all')
  },

  /** Bảng lịch dạy chi tiết của lời mời trong thông báo (thứ + tiết + lớp). */
  assignmentDetail(id) {
    return http.get(`/notifications/${id}/assignment`)
  },

  /** Giáo viên XÁC NHẬN nhận lịch dạy được phân công. */
  confirm(id) {
    return http.post(`/notifications/${id}/confirm`)
  },

  /** Giáo viên TỪ CHỐI (Hủy) lịch dạy — kèm lý do. */
  cancel(id, reason) {
    return http.post(`/notifications/${id}/cancel`, { reason })
  },
}
