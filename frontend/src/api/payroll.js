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
