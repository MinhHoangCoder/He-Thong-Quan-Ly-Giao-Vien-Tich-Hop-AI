// Routes PORTAL GIÁO VIÊN (vai trò TEACHER) — layout 'teacher'.
// Route mới (lịch của tôi, chấm công, hồ sơ...) thêm vào CUỐI mảng.
export const teacherRoutes = [
  {
    path: '/teacher',
    name: 'teacher-home',
    component: () => import('@/pages/TeacherDashboardPage.vue'),
    meta: { layout: 'teacher', roles: ['TEACHER'] },
  },
  // Lịch dạy của tôi (thời khóa biểu tuần + lịch tháng, chỉ buổi đã duyệt)
  {
    path: '/teacher/schedule',
    name: 'teacher-schedule',
    component: () => import('@/pages/TeacherSchedulePage.vue'),
    meta: { layout: 'teacher', roles: ['TEACHER'] },
  },
  // Bảng chấm công của tôi (read-only, sinh từ lịch dạy đã duyệt)
  {
    path: '/teacher/attendance',
    name: 'teacher-attendance',
    component: () => import('@/pages/TeacherAttendancePage.vue'),
    meta: { layout: 'teacher', roles: ['TEACHER'] },
  },
  // Danh sách bài giảng
  {
    path: '/teacher/lessons',
    name: 'teacher-lessons',
    component: () => import('@/pages/TeacherLessonListPage.vue'),
    meta: {
      layout: 'teacher',
      roles: ['TEACHER'],
    },
  },

  // Xem chi tiết bài giảng
  {
    path: '/teacher/lessons/:id',
    name: 'teacher-lesson-view',
    component: () => import('@/pages/TeacherLessonViewPage.vue'),
    meta: {
      layout: 'teacher',
      roles: ['TEACHER'],
    },
    props: true,
  },
  // ── Đánh giá (chỉ xem về chính mình) ────────────────────────────
  {
    path: '/teacher/evaluations',
    name: 'teacher-evaluations',
    component: () => import('@/pages/EvaluationPage.vue'),
    meta: { layout: 'teacher', roles: ['TEACHER'], evaluationPortal: 'teacher' },
  },
  // ── Cài đặt cá nhân (liên hệ / mật khẩu / thiết bị đăng nhập) ─────
  {
    path: '/teacher/settings',
    name: 'teacher-settings',
    component: () => import('@/pages/SettingsPage.vue'),
    meta: { layout: 'teacher', roles: ['TEACHER'] },
  },
  // ── Hồ sơ của tôi (chỉ xem — tách riêng khỏi Cài đặt) ─────────────
  {
    path: '/teacher/profile',
    name: 'teacher-profile',
    component: () => import('@/pages/MyProfilePage.vue'),
    meta: { layout: 'teacher', roles: ['TEACHER'] },
  },
]
