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

  /**
   * Các dòng chấm công VẮNG mà hệ thống tự ghi cho buổi ĐÃ QUA trong kỳ nghỉ.
   * Hủy buổi chỉ cứu được buổi CHƯA diễn ra — buổi đã qua thì job nền đã ghi Vắng mất rồi,
   * và dòng vắng đó không biến mất khi buổi bị hủy.
   */
  absences(id) {
    return http.get(`/holidays/${id}/absences`)
  },

  /** Chuyển các dòng Vắng đã chọn sang Nghỉ phép. body: { attendanceIds, reason } */
  fixAbsences(id, body) {
    return http.post(`/holidays/${id}/fix-absences`, body)
  },

  remove(id) {
    return http.delete(`/holidays/${id}`)
  },

  /** Kỳ nghỉ đã xóa (thùng rác). params: keyword, page, size */
  trash(params = {}) {
    return http.get('/holidays/trash', { params })
  },

  /** Đưa kỳ nghỉ từ thùng rác về danh sách chính. */
  restore(id) {
    return http.post(`/holidays/${id}/restore`)
  },

  /**
   * Kỳ nghỉ này đã để lại những gì — hỏi TRƯỚC khi xóa.
   * Khác impact() ở chỗ: impact() đếm buổi SẼ phải hủy, còn cái này đếm hậu quả ĐÃ ghi
   * (buổi đã hủy, dòng chấm công đã chuyển sang Nghỉ phép) — những thứ xóa kỳ nghỉ không
   * hoàn lại được.
   */
  deleteImpact(id) {
    return http.get(`/holidays/${id}/delete-impact`)
  },
}
