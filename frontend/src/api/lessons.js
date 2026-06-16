import http from './http'

/**
 * API module Bài giảng.
 * Category: 1 trong 4 giá trị cố định "Tin học" | "Tiếng Anh" | "STEM - AI" | "Kĩ năng sống".
 * FileType: 'pptx' = file PPT upload, 'canva' = link Canva.
 */
export const lessonApi = {
  /* ── Metadata ──────────────────────────────────────── */
  subjects() {
    return http.get('/lessons/subjects')
  },
  gradeLevels() {
    return http.get('/lessons/grade-levels')
  },

  /* ── CRUD ──────────────────────────────────────────── */
  /**
   * Danh sách bài giảng có phân trang + lọc.
   * @param {Object} params - { category, gradeLevel, status, keyword, page, size }
   */
  list(params = {}) {
    return http.get('/lessons', { params })
  },
  detail(id) {
    return http.get(`/lessons/${id}`)
  },
  create(body) {
    return http.post('/lessons', body)
  },
  update(id, body) {
    return http.put(`/lessons/${id}`, body)
  },
  remove(id) {
    return http.delete(`/lessons/${id}`)
  },

  /* ── File đính kèm ─────────────────────────────────── */
  uploadFiles(id, files) {
    const form = new FormData()
    files.forEach((f) => form.append('files', f))
    return http.post(`/lessons/${id}/files`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  addCanvaLink(id, body) {
    return http.post(`/lessons/${id}/canva`, body)
  },
  removeFile(lessonId, fileId) {
    return http.delete(`/lessons/${lessonId}/files/${fileId}`)
  },
}
