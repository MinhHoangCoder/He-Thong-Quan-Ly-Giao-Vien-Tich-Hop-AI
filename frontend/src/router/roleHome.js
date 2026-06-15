// Map vai trò -> "trang chủ" tương ứng sau khi đăng nhập.
// Một người có thể có nhiều vai trò: ưu tiên ADMIN/EMPLOYEE (khu quản trị) trước.
export function roleHome(roles = []) {
  if (roles.includes('ADMIN') || roles.includes('EMPLOYEE')) {
    return { name: 'dashboard' }
  }
  // Nhân viên phòng ban nội bộ → portal nhân viên (menu tự lọc theo quyền).
  if (['ACCOUNTANT', 'HR', 'ACADEMIC', 'SALES'].some((r) => roles.includes(r))) {
    return { name: 'staff-home' }
  }
  if (roles.includes('SCHOOL')) {
    return { name: 'school-home' }
  }
  if (roles.includes('TEACHER')) {
    return { name: 'teacher-home' }
  }
  return { name: 'home' }
}
