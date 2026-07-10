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
    path: '/dashboard/teacher',
    name: 'teacher-list',
    component: () => import('@/pages/TeacherListPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
  {
    path: '/assignments',
    name: 'assignments',
    component: () => import('@/pages/AssignmentPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
  {
    path: '/schedule',
    name: 'schedule',
    component: () => import('@/pages/SchedulePage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
  {
    path: '/attendance',
    name: 'attendance',
    component: () => import('@/pages/AttendancePage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
  {
    path: '/payroll',
    name: 'payroll',
    component: () => import('@/pages/PayrollPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
  {
    path: '/ai-assistant',
    name: 'ai-assistant',
    component: () => import('@/pages/AiAssistantPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
  {
    path: '/admin/lessons',
    name: 'admin-lesson-list',
    component: () => import('@/pages/LessonListPage.vue'),
    meta: {
      layout: 'admin',
      roles: ['ADMIN', 'EMPLOYEE'],
    },
  },
  {
    path: '/admin/lessons/new',
    name: 'admin-lesson-new',
    component: () => import('@/pages/LessonFormPage.vue'),
    meta: {
      layout: 'admin',
      roles: ['ACCOUNTANT', 'HR', 'ACADEMIC', 'SALES'],
    },
  },
  {
    path: '/admin/lessons/:id/edit',
    name: 'admin-lesson-edit',
    component: () => import('@/pages/LessonFormPage.vue'),
    meta: {
      layout: 'admin',
      roles: ['ACCOUNTANT', 'HR', 'ACADEMIC', 'SALES'],
    },
  },
  {
    path: '/staff/settings',
    name: 'staff-settings',
    component: () => import('@/pages/SettingsPage.vue'),
    meta: {
      layout: 'admin',
      roles: ['ACCOUNTANT', 'HR', 'ACADEMIC', 'SALES'],
    },
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
