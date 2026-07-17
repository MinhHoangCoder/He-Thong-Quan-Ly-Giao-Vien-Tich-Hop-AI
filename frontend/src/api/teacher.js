import http from './http'

// API quản lý Giáo viên — ánh xạ tới TeacherController /api/v1/teacher
export const teacherApi = {
  /** Lấy toàn bộ GV active (FE tự lọc). */
  list() {
    return http.get('/teacher')
  },

  /** Xem Chi tiết 1 GV + chứng chỉ + hợp đồng). */
  get(id) {
    return http.get(`/teacher/${id}`)
  },

  /** Create GV. */
  create(data) {
    return http.post('/teacher', data)
  },

  /** Update GV. */
  update(id, data) {
        return http.put(`/teacher/${id}`, {
      ...data,
      employmentType: data.employmentType || null,
    })
  },

  /** Xóa GV (chỉ ADMIN). */
  delete(id) {
    return http.delete(`/teacher/${id}`)
  },
  deleteTrue(id) {
    return http.delete(`/teacher/trash/${id}`)
  },

  /** Xóa mềm nhiều GV cùng lúc — gọi tuần tự từng id. */
  deleteMany(ids) {
    return Promise.all(ids.map((id) => http.delete(`/teacher/${id}`)))
  },

  /** Danh sách GV đã xóa (history). */
  trash() {
    return http.get('/teacher/trash')
  },

  /** Khôi phục GV*/
  restore(id) {
    return http.post(`/teacher/trash/${id}/restore`)
  },

 addCertificate(teacherId, data) {
    return http.post(`/teacher/${teacherId}/certificates`, data)
  },

  /** Xóa mềm 1 chứng chỉ/bằng cấp của GV. */
  deleteCertificate(teacherId, certId) {
    return http.delete(`/teacher/${teacherId}/certificates/${certId}`)
  },

}
