// Routes PORTAL GIÁO VIÊN (vai trò TEACHER) — layout 'teacher'.
// Route mới (lịch của tôi, chấm công, hồ sơ...) thêm vào CUỐI mảng.
export const teacherRoutes = [
  {
    path: '/teacher',
    name: 'teacher-home',
    component: () => import('@/pages/TeacherDashboardPage.vue'),
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
]
