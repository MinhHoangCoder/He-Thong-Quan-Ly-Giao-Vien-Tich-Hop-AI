// Routes PORTAL GIÁO VIÊN (vai trò TEACHER) — layout 'teacher'.
// Route mới (lịch của tôi, chấm công, hồ sơ...) thêm vào CUỐI mảng.
export const teacherRoutes = [
  {
    path: '/teacher',
    name: 'teacher-home',
    component: () => import('@/pages/TeacherDashboardPage.vue'),
    meta: { layout: 'teacher', roles: ['TEACHER'] },
  },
]
