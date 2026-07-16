import http from './http'

/**
 * API module Phân công giảng dạy (Assignment).
 * Base: /api/v1/assignments
 */
export const assignmentApi = {
  /** Danh sách phân công — lọc theo teacherId nếu truyền. */
  list(teacherId) {
    return http.get('/assignments', { params: teacherId ? { teacherId } : {} })
  },

  detail(id) {
    return http.get(`/assignments/${id}`)
  },

  /** Options cấp 1 cho form: GV / môn / trường. */
  options() {
    return http.get('/assignments/options')
  },

  /** Options cấp 2 theo trường đã chọn: lớp + khung tiết. */
  schoolOptions(schoolId) {
    return http.get(`/assignments/options/${schoolId}`)
  },

  create(body) {
    return http.post('/assignments', body)
  },

  /** Hủy phân công + đưa vào thùng rác (một thao tác). */
  remove(id) {
    return http.delete(`/assignments/${id}`)
  },

  /** Danh sách phân công trong thùng rác (đã xóa mềm). */
  trash() {
    return http.get('/assignments/trash')
  },

  /** Khôi phục phân công từ thùng rác. */
  restore(id) {
    return http.post(`/assignments/${id}/restore`)
  },

  /** Xóa vĩnh viễn phân công khỏi hệ thống. */
  purge(id) {
    return http.delete(`/assignments/${id}/permanent`)
  },
}
