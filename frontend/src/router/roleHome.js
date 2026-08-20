// Map vai trò -> "trang chủ" tương ứng sau khi đăng nhập.
//
// Từ Flyway V33 hệ thống chỉ còn HAI tác nhân: ADMIN và TEACHER. Các nhánh cũ cho portal
// nhân viên phòng ban (ACCOUNTANT / HR / ACADEMIC / SALES) và EMPLOYEE đã bỏ cùng lúc với
// role của chúng — giữ lại thì đó là nhánh không bao giờ chạy tới.
//
// Vẫn giữ ưu tiên ADMIN trước TEACHER: một tài khoản có thể mang cả hai vai, và người vừa
// quản trị vừa dạy thì khu quản trị mới là nơi họ cần vào.
export function roleHome(roles = []) {
  if (roles.includes('ADMIN')) {
    return { name: 'dashboard' }
  }
  if (roles.includes('TEACHER')) {
    return { name: 'teacher-home' }
  }
  return { name: 'home' }
}
