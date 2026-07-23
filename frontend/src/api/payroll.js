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
}
