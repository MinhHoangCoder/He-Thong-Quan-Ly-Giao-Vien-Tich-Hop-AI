import http from './http'

/**
 * API module Trường khách hàng (School).
 * Base: /api/v1/schools
 */
export const schoolApi = {
  /** Danh sách có phân trang + tìm kiếm + lọc theo chi nhánh/trạng thái. */
  list(params = {}) {
    return http.get('/schools', { params })
  },

  detail(id) {
    return http.get(`/schools/${id}`)
  },

  create(body) {
    return http.post('/schools', body)
  },

  update(id, body) {
    return http.put(`/schools/${id}`, body)
  },

  remove(id) {
    return http.delete(`/schools/${id}`)
  },
}
