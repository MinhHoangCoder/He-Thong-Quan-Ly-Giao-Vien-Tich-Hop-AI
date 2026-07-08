// Routes PORTAL GIÁO VIÊN (vai trò TEACHER) — layout 'teacher'.
// Route mới (lịch của tôi, chấm công, hồ sơ...) thêm vào CUỐI mảng.
export const teacherRoutes = [
  {
    path: '/teacher',
    name: 'teacher-home',
    component: () => import('@/pages/TeacherDashboardPage.vue'),
    meta: { layout: 'teacher', roles: ['TEACHER'] },
  },
  // ── Cài đặt cá nhân (hồ sơ / mật khẩu / thiết bị đăng nhập) ─────
  {
    path: '/teacher/settings',
    name: 'teacher-settings',
    component: () => import('@/pages/SettingsPage.vue'),
    meta: { layout: 'teacher', roles: ['TEACHER'] },
  },
]
