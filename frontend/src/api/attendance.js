import http from './http'

/**
 * API module Chấm công (Attendance).
 * Base: /api/v1/attendance
 */
export const attendanceApi = {
  /** Bảng chấm công theo khoảng ngày (mặc định tháng hiện tại) + lọc GV. */
  list({ teacherId, from, to } = {}) {
    return http.get('/attendance', { params: { teacherId: teacherId || undefined, from, to } })
  },

  /**
   * "Bảng chấm công của tôi" — chấm công của CHÍNH giáo viên đang đăng nhập trong [from, to].
   * KHÔNG truyền teacherId: backend tự lấy từ token. Lọc tùy chọn theo trạng thái.
   */
  mine({ from, to, status } = {}) {
    return http.get('/attendance/mine', {
      params: { from, to, status: status || undefined },
    })
  },

  /** Sinh chấm công hàng loạt từ lịch dạy đã duyệt trong khoảng ngày. */
  generate(from, to) {
    return http.post('/attendance/generate', null, { params: { from, to } })
  },

  create(body) {
    return http.post('/attendance', body)
  },

  update(id, body) {
    return http.put(`/attendance/${id}`, body)
  },
}
