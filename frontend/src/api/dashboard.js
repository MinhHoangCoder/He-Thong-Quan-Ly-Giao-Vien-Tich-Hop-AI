import http from './http'

/**
 * API module Bảng điều khiển (admin).
 * Base: /api/v1/dashboard
 */
export const dashboardApi = {
  /** Số liệu tổng hợp; months = số tháng vẽ biểu đồ (mặc định 8). */
  summary(months = 8) {
    return http.get('/dashboard', { params: { months } })
  },
}
