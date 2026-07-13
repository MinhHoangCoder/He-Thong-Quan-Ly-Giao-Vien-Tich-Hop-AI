import http from './http'

/**
 * API module Đánh giá giáo viên (TeacherEvaluation).
 * Scope theo role do backend enforce (TEACHER chỉ thấy của mình, SCHOOL chỉ trường mình).
 */
export const evaluationApi = {
  periodPresets() {
    return http.get('/evaluations/period-presets')
  },

  /** { presets: string[], suggested: string } */
  periodMeta() {
    return http.get('/evaluations/period-meta')
  },

  /**
   * @param {{ teacherId: number, periodNote: string, excludeId?: number }} params
   */
  duplicateCheck(params) {
    return http.get('/evaluations/duplicate-check', { params })
  },

  /**
   * Dropdown GV thông minh.
   * @param {{ periodNote?: string, keyword?: string }} params
   */
  teachers(params = {}) {
    return http.get('/evaluations/teachers', { params })
  },

  /**
   * GV chưa đánh giá trong kỳ.
   * @param {{ periodNote?: string, keyword?: string }} params
   */
  unevaluatedTeachers(params = {}) {
    return http.get('/evaluations/teachers/unevaluated', { params })
  },

  stats(params = {}) {
    return http.get('/evaluations/stats', { params })
  },

  teacherSummary(teacherId) {
    return http.get(`/evaluations/teachers/${teacherId}/summary`)
  },

  list(params = {}) {
    return http.get('/evaluations', { params })
  },

  detail(id) {
    return http.get(`/evaluations/${id}`)
  },

  create(body) {
    return http.post('/evaluations', body)
  },

  update(id, body) {
    return http.put(`/evaluations/${id}`, body)
  },

  remove(id) {
    return http.delete(`/evaluations/${id}`)
  },
}
