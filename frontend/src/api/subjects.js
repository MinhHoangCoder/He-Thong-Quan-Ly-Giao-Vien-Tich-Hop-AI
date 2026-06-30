import http from './http'

/**
 * API module Môn học (Subject).
 * Base: /api/v1/subjects
 * Môn học luôn thuộc 1 nhóm (categoryId) — quản lý lồng trong trang "Nhóm môn học".
 */
export const subjectApi = {
  /** Danh sách môn — lọc theo categoryId nếu truyền vào. */
  list(categoryId) {
    return http.get('/subjects', { params: categoryId ? { categoryId } : {} })
  },

  detail(id) {
    return http.get(`/subjects/${id}`)
  },

  create(body) {
    return http.post('/subjects', body)
  },

  update(id, body) {
    return http.put(`/subjects/${id}`, body)
  },

  remove(id) {
    return http.delete(`/subjects/${id}`)
  },
}
