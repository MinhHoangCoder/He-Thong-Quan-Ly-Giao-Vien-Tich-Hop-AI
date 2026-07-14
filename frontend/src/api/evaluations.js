import http from './http'

/**
 * API module Đánh giá giáo viên (TeacherEvaluation).
 */
export const evaluationApi = {
  periodPresets() {
    return http.get('/evaluations/period-presets')
  },

  periodMeta() {
    return http.get('/evaluations/period-meta')
  },

  /** { schools: [{id,name}], branches: [{id,name}], schoolScoped: boolean } */
  filterMeta() {
    return http.get('/evaluations/filter-meta')
  },

  duplicateCheck(params) {
    return http.get('/evaluations/duplicate-check', { params })
  },

  /**
   * Tìm GV phân trang.
   * @returns {{ content, page, size, totalElements, totalPages }}
   */
  teachers(params = {}) {
    return http.get('/evaluations/teachers', { params })
  },

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
