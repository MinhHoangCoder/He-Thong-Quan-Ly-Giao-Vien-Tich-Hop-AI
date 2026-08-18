import http from './http'

/**
 * API module Khung tiết học (Period).
 * Base: /api/v1/periods
 */
export const periodApi = {
  /**
   * Áp bộ khung tiết CHUẨN theo cấp học cho một trường chưa có khung nào.
   * Cấp học do backend suy từ khối lớp cao nhất của trường.
   * @param {number} schoolId
   */
  applyStandard(schoolId) {
    return http.post(`/periods/standard/${schoolId}`)
  },
}
