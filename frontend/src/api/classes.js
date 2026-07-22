import http from './http'

/**
 * API module Lớp học (SchoolClass).
 * Base: /api/v1/classes
 */
export const classApi = {
  /** Dropdown trường cho form. */
  schoolOptions() {
    return http.get('/classes/school-options')
  },

  /** Các khối đang tồn tại (lọc danh sách). */
  gradeLevels() {
    return http.get('/classes/grade-levels')
  },

  /** Dropdown lớp ACTIVE theo trường. */
  bySchool(schoolId) {
    return http.get(`/classes/by-school/${schoolId}`)
  },

  /** Danh sách phân trang + lọc. */
  list(params = {}) {
    return http.get('/classes', { params })
  },

  detail(id) {
    return http.get(`/classes/${id}`)
  },

  create(body) {
    return http.post('/classes', body)
  },

  update(id, body) {
    return http.put(`/classes/${id}`, body)
  },

  remove(id) {
    return http.delete(`/classes/${id}`)
  },
}
