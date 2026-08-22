import http from './http'

/**
 * API module Chấm công (Attendance).
 * Base: /api/v1/attendance
 */
export const attendanceApi = {
  /**
   * Bảng chấm công theo khoảng ngày, CÓ PHÂN TRANG (trả về Page: content/totalPages/…).
   * keyword tìm theo tên giáo viên; status lọc PRESENT/LATE/LEAVE/ABSENT.
   */
  list({ teacherId, from, to, status, keyword, page = 0, size = 10 } = {}) {
    return http.get('/attendance', {
      params: {
        teacherId: teacherId || undefined,
        from,
        to,
        status: status || undefined,
        keyword: keyword || undefined,
        page,
        size,
      },
    })
  },

  /** Ba thẻ tổng quan tính trên CẢ kỳ — bảng đã phân trang nên không cộng dồn ở client được. */
  summary({ teacherId, from, to, status, keyword } = {}) {
    return http.get('/attendance/summary', {
      params: {
        teacherId: teacherId || undefined,
        from,
        to,
        status: status || undefined,
        keyword: keyword || undefined,
      },
    })
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

  /**
   * Tab "Hôm nay" của admin — MỌI buổi dạy trong ngày kèm trạng thái chấm.
   * Dựng từ lịch dạy nên thấy được cả buổi chưa ai chấm (bảng chấm công không có dòng nào).
   */
  today(date) {
    return http.get('/attendance/today', { params: { date: date || undefined } })
  },

  /** Các dòng cần kế toán soát lại (hệ thống chốt hộ / ghi Vắng / còn treo). */
  attention(params) {
    return http.get('/attendance/attention', { params })
  },

  /** Nhật ký thay đổi của một dòng chấm công. */
  logs(id) {
    return http.get(`/attendance/${id}/logs`)
  },

  checkinToday() {
    return http.get('/attendance/checkin/today')
  },

  /** GV check-in một buổi dạy hôm nay — body { scheduleId }; giờ vào do server ghi. */
  checkIn(body) {
    return http.post('/attendance/checkin', body)
  },

  /** GV check-out buổi đã check-in — body { scheduleId }; giờ ra do server ghi. */
  checkOut(body) {
    return http.post('/attendance/checkout', body)
  },

  create(body) {
    return http.post('/attendance', body)
  },

  /** Sửa một dòng — body BẮT BUỘC có adjustReason (lý do can thiệp tay). */
  update(id, body) {
    return http.put(`/attendance/${id}`, body)
  },
}

/**
 * Yêu cầu bổ sung chấm công — đường duy nhất để buổi đã lỡ được ghi công.
 * Base: /api/v1/attendance/amend-requests
 */
export const attendanceAmendApi = {
  /** GV gửi: { scheduleId, reason, proposedCheckIn, proposedCheckOut }. */
  create(body) {
    return http.post('/attendance/amend-requests', body)
  },

  /** Yêu cầu của CHÍNH GV đang đăng nhập (backend tự lấy từ token). */
  mine() {
    return http.get('/attendance/amend-requests/mine')
  },

  /** Hộp yêu cầu của admin — status bỏ trống là lấy tất. */
  list(status) {
    return http.get('/attendance/amend-requests', { params: { status: status || undefined } })
  },

  /** Duyệt: body { status?, reviewNote? } — status bỏ trống thì backend suy từ giờ GV khai. */
  approve(id, body) {
    return http.post(`/attendance/amend-requests/${id}/approve`, body ?? {})
  },

  /** Từ chối: body { reviewNote } — lý do BẮT BUỘC. */
  reject(id, body) {
    return http.post(`/attendance/amend-requests/${id}/reject`, body)
  },
}
