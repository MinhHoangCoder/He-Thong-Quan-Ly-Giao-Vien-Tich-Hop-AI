// Routes PORTAL NHÂN VIÊN PHÒNG BAN (HR / ACCOUNTANT / ACADEMIC / SALES) — layout 'staff'.
// Menu & nội dung tự lọc theo PERMISSION trong token (xem router/staffModules.js).
// Route feature của từng phòng ban thêm vào CUỐI mảng (giữ quy ước chống conflict).
export const staffRoutes = [
  {
    path: '/staff',
    name: 'staff-home',
    component: () => import('@/pages/StaffHomePage.vue'),
    meta: { layout: 'staff', roles: ['ACCOUNTANT', 'HR', 'ACADEMIC', 'SALES'] },
  },
  // ── Kho bài giảng (LESSON) ────────────────────────────────────────
  {
    path: '/staff/lessons',
    name: 'lesson-list',
    component: () => import('@/pages/LessonListPage.vue'),
    meta: { layout: 'staff', roles: ['ACCOUNTANT', 'HR', 'ACADEMIC', 'SALES'] },
  },
  {
    path: '/staff/lessons/new',
    name: 'lesson-new',
    component: () => import('@/pages/LessonFormPage.vue'),
    meta: { layout: 'staff', roles: ['ACCOUNTANT', 'HR', 'ACADEMIC', 'SALES'] },
  },
  {
    path: '/staff/lessons/:id/edit',
    name: 'lesson-edit',
    component: () => import('@/pages/LessonFormPage.vue'),
    meta: { layout: 'staff', roles: ['ACCOUNTANT', 'HR', 'ACADEMIC', 'SALES'] },
  },
]
