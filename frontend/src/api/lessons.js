import http from './http'

/**
 * API module Bài giảng.
 * Category: 1 trong 4 giá trị cố định "Tin học" | "Tiếng Anh" | "STEM - AI" | "Kĩ năng sống".
 * FileType: 'pdf' = file PDF upload, 'canva' = link Canva.
 */
export const lessonApi = {
  /* ── Metadata ──────────────────────────────────────── */
  subjects() {
    return http.get('/lessons/subjects')
  },
  gradeLevels() {
    return http.get('/lessons/grade-levels')
  },
  categories() {
    return http.get('/lessons/categories')
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

  /**
   * Tải nội dung file đính kèm (KHÔNG dùng cho fileType 'canva' — canva chỉ
   * cần window.open() thẳng fileUrl vì đã là link Canva ngoài, không cần đi
   * qua API). Dùng responseType 'blob' để FE tự tạo <a download> ép trình
   * duyệt tải file về máy (xem TeacherLessonViewPage.vue / dev-notes).
   */
  downloadFile(lessonId, fileId) {
    return http.get(`/lessons/${lessonId}/files/${fileId}/download`, { responseType: 'blob' })
  },
}
