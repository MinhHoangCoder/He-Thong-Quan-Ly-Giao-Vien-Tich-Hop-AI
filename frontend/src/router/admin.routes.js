// Routes khu QUẢN TRỊ (ADMIN & EMPLOYEE) — layout 'admin'.
// Ai làm feature thuộc khu quản trị (phân công, lịch dạy, chấm công, lương...)
// thì thêm route vào CUỐI mảng này, không đụng file khu vực khác.
export const adminRoutes = [
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('@/pages/DashboardPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
  {
    // Tạo tài khoản GV/Trường. Backend chặn theo QUYỀN: HR (TEACHER_MANAGE) tạo GV,
    // SALES (SCHOOL_MANAGE) tạo trường, ADMIN tạo cả hai (employee gộp 4 phòng nên cũng vào được).
    // meta.roles ở FE chỉ để HIỆN trang — chốt chặn thật nằm ở @PreAuthorize + RegistrationService.
    path: '/users/new',
    name: 'user-create',
    component: () => import('@/pages/RegisterUserPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE', 'HR', 'SALES'] },
  },
    {
    path: '/dashboard/teacher',
    name: 'teacher-list',
    component: () => import('@/pages/TeacherListPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
  // ── Khu CÀI ĐẶT ──────────────────────────────────────────────────
  {
    // Cài đặt CÁ NHÂN của người đang đăng nhập (hồ sơ / mật khẩu / thiết bị).
    // Cùng component với /staff/settings, /teacher/settings... — chỉ khác layout bọc ngoài.
    path: '/settings',
    name: 'admin-settings',
    component: () => import('@/pages/SettingsPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
  {
    // Ma trận Role × Permission (chỉ đọc).
    // LƯU Ý: trang "quản lý tài khoản" (list/khóa/gán role) do THÀNH VIÊN KHÁC dựng UI —
    // backend đã sẵn ở /api/v1/admin/users/** (xem api/adminUsers.js), FE không làm trùng.
    path: '/settings/roles',
    name: 'admin-roles',
    component: () => import('@/pages/RoleMatrixPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN'] },
  },
]
