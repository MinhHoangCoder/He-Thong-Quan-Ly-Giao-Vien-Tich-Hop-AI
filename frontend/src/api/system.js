import http from './http'

/**
 * API khu vực HỆ THỐNG — nhật ký thao tác và rà soát dữ liệu mồ côi.
 * Cả hai đều chỉ ADMIN xem được.
 */
export const auditApi = {
  /** Nhật ký, phân trang phía server. */
  list(params = {}) {
    return http.get('/audit-logs', { params })
  },

  /** Loại thao tác + bảng đã từng ghi — đổ vào ô lọc, không hard-code ở frontend. */
  filterOptions() {
    return http.get('/audit-logs/filter-options')
  },
}

export const orphanApi = {
  /** Quét lại NGAY và ghi một ảnh chụp vào nhật ký rà soát. */
  scan() {
    return http.get('/orphan-scan')
  },
}
