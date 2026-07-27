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

  /** Xóa mềm 1 lớp. */
  remove(id) {
    return http.delete(`/classes/${id}`)
  },

  /** Xóa mềm nhiều lớp. */
  removeMany(ids) {
    return http.post('/classes/batch-delete', ids)
  },

  /** Thùng rác. */
  trash() {
    return http.get('/classes/trash')
  },

  /** Khôi phục từ thùng rác. */
  restore(id) {
    return http.post(`/classes/trash/${id}/restore`)
  },

  /** Khôi phục nhiều. */
  restoreMany(ids) {
    return Promise.all(ids.map((id) => http.post(`/classes/trash/${id}/restore`)))
  },

  /** Xóa vĩnh viễn (chỉ khi đang ở thùng rác). */
  purge(id) {
    return http.delete(`/classes/trash/${id}`)
  },

  /** Xóa vĩnh viễn nhiều. */
  purgeMany(ids) {
    return Promise.all(ids.map((id) => http.delete(`/classes/trash/${id}`)))
  },
}
