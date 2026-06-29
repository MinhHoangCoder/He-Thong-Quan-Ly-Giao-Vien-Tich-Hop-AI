import http from './http'

/**
 * API module Nhóm môn (SubjectCategory).
 * Base: /api/v1/subject-categories
 */
export const subjectCategoryApi = {
  /** Dropdown: chỉ ACTIVE, không phân trang. */
  listActive() {
    return http.get('/subject-categories/active')
  },

  /** Danh sách đầy đủ có phân trang + tìm kiếm. */
  list(params = {}) {
    return http.get('/subject-categories', { params })
  },

  detail(id) {
    return http.get(`/subject-categories/${id}`)
  },

  create(body) {
    return http.post('/subject-categories', body)
  },

  update(id, body) {
    return http.put(`/subject-categories/${id}`, body)
  },

  remove(id) {
    return http.delete(`/subject-categories/${id}`)
  },
}
a
