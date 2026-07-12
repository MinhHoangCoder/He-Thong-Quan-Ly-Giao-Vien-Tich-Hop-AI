import http from './http'

/**
 * API module Đánh giá giáo viên (TeacherEvaluation).
 * Scope theo role do backend enforce (TEACHER chỉ thấy của mình, SCHOOL chỉ trường mình).
 */
export const evaluationApi = {
  /** Gợi ý kỳ đánh giá (dropdown). */
  periodPresets() {
    return http.get('/evaluations/period-presets')
  },

  /** Dropdown GV khi tạo/sửa (cần EVALUATION_MANAGE). */
  teachers() {
    return http.get('/evaluations/teachers')
  },

  /**
   * KPI: totalCount, averageScore, highScoreCount, teacherCountEvaluated, score1..score5
   * @param {{ teacherId?: number, schoolId?: number, source?: 'CENTER'|'SCHOOL' }} params
   */
  stats(params = {}) {
    return http.get('/evaluations/stats', { params })
  },

  /** Tổng hợp theo 1 GV. */
  teacherSummary(teacherId) {
    return http.get(`/evaluations/teachers/${teacherId}/summary`)
  },

  /**
   * Danh sách phân trang.
   * @param {{ teacherId?, schoolId?, score?, periodNote?, source?, keyword?, page?, size? }} params
   */
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
