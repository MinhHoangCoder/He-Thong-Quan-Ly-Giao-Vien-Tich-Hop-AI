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

  /** Số lớp / giáo viên / học sinh / khung tiết / hợp đồng dịch vụ của một trường. */
  summary(id) {
    return http.get(`/schools/${id}/summary`)
  },

  create(body) {
    return http.post('/schools', body)
  },

  update(id, body) {
    return http.put(`/schools/${id}`, body)
  },

  /** Xóa mềm — trường rơi vào thùng rác, còn khôi phục được. */
  remove(id) {
    return http.delete(`/schools/${id}`)
  },

  /** Thùng rác: trường đã xóa mềm (không phân trang). */
  trash() {
    return http.get('/schools/trash')
  },

  restore(id) {
    return http.post(`/schools/${id}/restore`)
  },
}
