// src/api/teacher.js
// Toàn bộ gọi API liên quan đến giáo viên tập trung ở đây.
import http from '@/api/http'

const BASE = '/teacher'

export const teacherApi = {
  /** Lấy danh sách tất cả giáo viên */
  getAll: () => http.get(BASE),

  /** Xem chi tiết 1 giáo viên theo id. */
  getById: (id) => http.get(`${BASE}/${id}`),

  /** Thêm giáo viên */
  create: (data) => http.post(BASE, data),

  /** Sửa thông tin */
  update: (id, data) => http.put(`${BASE}/${id}`, data),

  /** Xóa (IsDeleted = true) — chỉ ADMIN. */
  remove: (id) => http.delete(`${BASE}/${id}`),
}
