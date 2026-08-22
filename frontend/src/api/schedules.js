import http from './http'

/**
 * API module Lịch dạy (Schedule) — read-only.
 * Base: /api/v1/schedules
 */
export const scheduleApi = {
  /**
   * Buổi dạy trong [from, to] (yyyy-MM-dd), lọc GV/trường/lớp/trạng thái tùy chọn.
   * Bỏ trống status = chỉ buổi ĐÃ DUYỆT.
   */
  list({ from, to, teacherId, schoolId, classId, status } = {}) {
    return http.get('/schedules', {
      params: {
        from,
        to,
        teacherId: teacherId || undefined,
        schoolId: schoolId || undefined,
        classId: classId || undefined,
        status: status || undefined,
      },
    })
  },

  /** Ngày nghỉ chạm vào [from, to] — để lịch tô màu, phân biệt "nghỉ lễ" với "quên xếp lịch". */
  holidays({ from, to, schoolId } = {}) {
    return http.get('/schedules/holidays', {
      params: { from, to, schoolId: schoolId || undefined },
    })
  },

  /** GV + trường cho bộ lọc. */
  filters() {
    return http.get('/schedules/filters')
  },

  /** Lớp theo trường. */
  classes(schoolId) {
    return http.get(`/schedules/filters/${schoolId}`)
  },

  /**
   * "Lịch dạy của tôi" — buổi ĐÃ DUYỆT của chính giáo viên đang đăng nhập trong [from, to].
   * KHÔNG truyền teacherId: backend tự lấy từ token, lọc theo trường/lớp tùy chọn.
   */
  mine({ from, to, schoolId, classId } = {}) {
    return http.get('/schedules/mine', {
      params: {
        from,
        to,
        schoolId: schoolId || undefined,
        classId: classId || undefined,
      },
    })
  },

  /** Trường + lớp mà chính giáo viên đang dạy (cho bộ lọc trang "Lịch dạy của tôi"). */
  myFilters() {
    return http.get('/schedules/mine/filters')
  },
}
