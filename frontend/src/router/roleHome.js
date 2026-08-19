// Map vai trò -> "trang chủ" tương ứng sau khi đăng nhập.
// Một người có thể có nhiều vai trò: ưu tiên ADMIN (khu quản trị) trước.
export function roleHome(roles = []) {
  if (roles.includes('ADMIN')) {
    return { name: 'dashboard' }
  }
  // Nhân viên phòng ban nội bộ → portal nhân viên (menu tự lọc theo quyền).
  // Đặt TRƯỚC EMPLOYEE: tài khoản test 'employee' đã gộp 4 role phòng ban (xem Flyway V5)
  // nên cũng vào staff portal để thấy đủ module của các phòng.
  if (['ACCOUNTANT', 'HR', 'ACADEMIC', 'SALES'].some((r) => roles.includes(r))) {
    return { name: 'staff-home' }
  }
  // EMPLOYEE "trơn" (không kèm role phòng ban) → khu quản trị.
  if (roles.includes('EMPLOYEE')) {
    return { name: 'dashboard' }
  }
  if (roles.includes('TEACHER')) {
    return { name: 'teacher-home' }
  }
  return { name: 'home' }
}
