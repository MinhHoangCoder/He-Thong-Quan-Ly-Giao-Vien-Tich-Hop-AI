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
}
