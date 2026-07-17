import http from './http'

/**
 * API module Lịch dạy (Schedule) — read-only.
 * Base: /api/v1/schedules
 */
export const scheduleApi = {
  /** Buổi dạy đã duyệt trong [from, to] (yyyy-MM-dd), lọc GV/trường/lớp tùy chọn. */
  list({ from, to, teacherId, schoolId, classId } = {}) {
    return http.get('/schedules', {
      params: {
        from,
        to,
        teacherId: teacherId || undefined,
        schoolId: schoolId || undefined,
        classId: classId || undefined,
      },
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
