import http from './http'

/**
 * API module Phân công giảng dạy (Assignment).
 * Base: /api/v1/assignments
 */
export const assignmentApi = {
  /**
   * Danh sách phân công (còn hoạt động).
   * @param {Object} [params] - { teacherId, keyword }.
   *   keyword: tìm không phân biệt hoa/thường & dấu theo tên GV/trường/lớp/môn.
   */
  list({ teacherId, keyword, status } = {}) {
    return http.get('/assignments', {
      params: {
        teacherId: teacherId || undefined,
        keyword: keyword || undefined,
        status: status || undefined,
      },
    })
  },

  /** Số phiếu theo từng trạng thái — badge trên các tab. */
  statusCounts() {
    return http.get('/assignments/status-counts')
  },

  /** Sửa phiếu chưa xác nhận rồi gửi lại lời mời (đổi được cả giáo viên). */
  update(id, body) {
    return http.put(`/assignments/${id}`, body)
  },

  /** Admin duyệt thay giáo viên (phiếu đang chờ hoặc đã hết hạn). */
  forceApprove(id, note) {
    return http.post(`/assignments/${id}/force-approve`, { ids: [id], note: note || null })
  },

  /** Gửi lại lời mời cho phiếu đang chờ. */
  remind(id) {
    return http.post(`/assignments/${id}/remind`)
  },

  /** Thao tác hàng loạt: action = 'remind' | 'force-approve' | 'cancel'. */
  bulk(action, ids, note) {
    return http.post(`/assignments/bulk/${action}`, { ids, note: note || null })
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

  /**
   * Giờ bận của một GV trên MỌI trường trong giai đoạn đang xếp — form dùng để khóa các
   * tiết đè giờ. Phải hỏi backend vì mỗi trường có bộ khung tiết riêng, form không thể
   * tự suy ra giờ của tiết ở trường khác.
   * @param {Object} params - { teacherId, startDate, endDate }
   */
  teacherBusy({ teacherId, startDate, endDate }) {
    return http.get('/assignments/teacher-busy', {
      params: { teacherId, startDate: startDate || undefined, endDate: endDate || undefined },
    })
  },

  /** Các cặp ô lịch đang trùng giờ của cùng một GV (gồm cả trùng chéo trường). */
  conflicts() {
    return http.get('/assignments/conflicts')
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
