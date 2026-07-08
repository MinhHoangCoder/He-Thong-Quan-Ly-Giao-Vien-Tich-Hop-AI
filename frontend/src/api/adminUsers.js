import http from './http'

// Nhóm API CÀI ĐẶT HỆ THỐNG (/api/v1/admin/**) — quản lý tài khoản + phân quyền.
// Backend chặn theo permission: USER_VIEW/USER_MANAGE (tài khoản), ROLE_MANAGE (gán role);
// ADMIN đi tắt. FE chỉ ẩn/hiện menu — chốt chặn thật nằm ở @PreAuthorize.
//
// ⚠ PHÂN CÔNG: trang UI "quản lý tài khoản" (danh sách/khóa-mở/gán vai trò) thuộc về
// THÀNH VIÊN làm module tài khoản — backend đã sẵn sàng, gọi thẳng các hàm dưới đây
// (list/setStatus/setRoles). Hiện FE base chỉ dùng roles() cho trang Ma trận quyền.
// Luật backend cần biết khi dựng UI: không tự khóa/tự đổi role chính mình (400);
// chỉ ADMIN đụng được tài khoản ADMIN khác (403); roles không được rỗng (400);
// khóa hoặc đổi role sẽ tự đăng xuất user đó khỏi mọi thiết bị.
export const adminUsersApi = {
  list(params) {
    // params: { keyword, page, size }
    return http.get('/admin/users', { params })
  },
  setStatus(id, status) {
    // status: 'ACTIVE' | 'LOCKED'
    return http.patch(`/admin/users/${id}/status`, { status })
  },
  setRoles(id, roles) {
    // roles: ['HR', 'ACADEMIC', ...] — THAY trọn bộ danh sách role của tài khoản
    return http.put(`/admin/users/${id}/roles`, { roles })
  },
  roles() {
    // Danh sách role + quyền của từng role (dựng ma trận phân quyền, chỉ đọc)
    return http.get('/admin/roles')
  },
}
