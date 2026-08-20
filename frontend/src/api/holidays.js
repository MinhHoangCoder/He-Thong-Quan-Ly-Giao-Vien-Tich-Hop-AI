import http from './http'

/**
 * API module Lịch nghỉ (Holiday) — ngày lễ & kỳ nghỉ KHÔNG sinh buổi dạy.
 * Base: /api/v1/holidays
 */
export const holidayApi = {
  /** Danh sách có phân trang. params: keyword, kind, from, to, schoolId, page, size */
  list(params = {}) {
    return http.get('/holidays', { params })
  },

  detail(id) {
    return http.get(`/holidays/${id}`)
  },

  /**
   * Số buổi dạy ĐÃ SINH đang rơi vào kỳ nghỉ này.
   * Generator chỉ bỏ ngày nghỉ lúc sinh buổi, nên kỳ nghỉ khai báo SAU đó không tự dọn
   * lịch cũ — màn hình phải hỏi rồi mới hủy.
   */
  impact(id) {
    return http.get(`/holidays/${id}/impact`)
  },

  create(body) {
    return http.post('/holidays', body)
  },

  update(id, body) {
    return http.put(`/holidays/${id}`, body)
  },

  /** Hủy các buổi CHƯA diễn ra rơi vào kỳ nghỉ (buổi đã qua giữ nguyên). */
  cancelSessions(id) {
    return http.post(`/holidays/${id}/cancel-sessions`)
  },

  remove(id) {
    return http.delete(`/holidays/${id}`)
  },
}
