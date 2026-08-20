import http from './http'

/**
 * API module Bảng lương (Payroll).
 * Base: /api/v1/payroll
 */
export const payrollApi = {
  /** Bảng lương một kỳ (year/month). */
  list(year, month) {
    return http.get('/payroll', { params: { year, month } })
  },

  /**
   * "Phiếu lương của tôi" — phiếu của CHÍNH giáo viên đang đăng nhập trong năm (mới nhất trước).
   * KHÔNG truyền teacherId: backend tự lấy từ token, chỉ trả phiếu đã chốt/đã trả.
   * month = undefined → cả năm; month cụ thể → đúng 1 phiếu tháng đó.
   */
  mine({ year, month } = {}) {
    return http.get('/payroll/mine', { params: { year, month: month || undefined } })
  },

  /** Sinh/tính lại lương từ chấm công theo tiết (đơn giá tự tra theo cấp lớp). */
  generate(year, month) {
    return http.post('/payroll/generate', null, { params: { year, month } })
  },

  update(id, body) {
    return http.put(`/payroll/${id}`, body)
  },

  finalize(id) {
    return http.post(`/payroll/${id}/finalize`)
  },

  /**
   * Cảnh báo trước khi chốt: kỳ này còn dòng Vắng nào rơi vào ngày nghỉ.
   * Chốt lương khóa luôn chấm công của kỳ — chốt khi còn Vắng giả là khóa lỗi vào trong.
   */
  holidayIssues(year, month) {
    return http.get('/payroll/holiday-issues', { params: { year, month } })
  },

  /** Lịch sử chốt / mở lại của một phiếu lương (mới nhất trước). */
  logs(id) {
    return http.get(`/payroll/${id}/logs`)
  },

  /** Mở lại một phiếu đã chốt về nháp. Cần quyền PAYROLL_REOPEN. body: { reason } */
  reopen(id, reason) {
    return http.post(`/payroll/${id}/reopen`, { reason })
  },

  /** Mở lại MỌI phiếu đã chốt của một kỳ. Cần quyền PAYROLL_REOPEN. */
  reopenPeriod(year, month, reason) {
    return http.post('/payroll/reopen-period', { reason }, { params: { year, month } })
  },
}
