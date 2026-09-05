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
  /** Danh sách phân trang phía server — trả về Page (content / totalElements / totalPages). */
  list({ teacherId, keyword, status, page, size } = {}) {
    return http.get('/assignments', {
      params: {
        teacherId: teacherId || undefined,
        keyword: keyword || undefined,
        status: status || undefined,
        page: page ?? 0,
        size: size ?? undefined,
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

  /** Thao tác hàng loạt: action = 'remind' | 'force-approve'. Hủy hàng loạt xem bulkCancel. */
  bulk(action, ids, note) {
    return http.post(`/assignments/bulk/${action}`, { ids, note: note || null })
  },

  /** Hủy hàng loạt — cùng ngày hiệu lực, cùng lý do (lý do bắt buộc). */
  bulkCancel(ids, { effectiveDate, reason }) {
    return http.post('/assignments/bulk/cancel', {
      ids,
      effectiveDate: effectiveDate || null,
      reason,
    })
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

  /**
   * Các ô lịch ĐÃ BỊ CHIẾM của một trường — lưới xếp tiết dùng để tô xám sẵn ô "lớp này đã
   * có giáo viên khác dạy". Chiều ngược của teacherBusy: cái kia hỏi giáo viên có rảnh không,
   * cái này hỏi lớp có trống không. Khớp luật 409 checkClass ở backend.
   * @param {Object} params - { schoolId, startDate, endDate }
   */
  classBusy({ schoolId, startDate, endDate }) {
    return http.get('/assignments/class-busy', {
      params: { schoolId, startDate: startDate || undefined, endDate: endDate || undefined },
    })
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

  /**
   * HỦY phân công kể từ một ngày (bỏ trống = từ hôm nay), bắt buộc kèm lý do.
   * Khác remove(): phiếu vẫn nằm trong danh sách để tra cứu và bỏ hủy được.
   */
  cancel(id, { effectiveDate, reason }) {
    return http.post(`/assignments/${id}/cancel`, {
      effectiveDate: effectiveDate || null,
      reason,
    })
  },

  /** Bỏ hủy: đưa phiếu đã hủy / đã kết thúc sớm về lại như trước khi hủy. */
  reactivate(id) {
    return http.post(`/assignments/${id}/reactivate`)
  },

  /** Khôi phục phân công từ thùng rác. */
  restore(id) {
    return http.post(`/assignments/${id}/restore`)
  },
}
