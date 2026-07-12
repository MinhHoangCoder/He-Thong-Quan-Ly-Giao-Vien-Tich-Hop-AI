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
}
