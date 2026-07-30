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
  // ── Nhóm môn (SubjectCategory) — quản lý danh mục ───────────────
  {
    path: '/staff/subject-categories',
    name: 'subject-category-list',
    component: () => import('@/pages/SubjectCategoryListPage.vue'),
    meta: { layout: 'staff', roles: ['ACCOUNTANT', 'HR', 'ACADEMIC', 'SALES'] },
  },
  // ── Trường khách hàng (School) — quản lý trường ─────────────────
  {
    path: '/staff/schools',
    name: 'staff-school-list',
    component: () => import('@/pages/SchoolListPage.vue'),
    meta: { layout: 'staff', roles: ['ACCOUNTANT', 'HR', 'ACADEMIC', 'SALES'] },
  },
  // ── Đánh giá giáo viên ──────────────────────────────────────────
  // Chỉ ACADEMIC có quyền EVALUATION_* (V3) — mở cho cả 4 phòng ban thì
  // HR/Kế toán/Sales vào được trang nhưng mọi API trả 403, nhìn như trang trống.
  {
    path: '/staff/evaluations',
    name: 'staff-evaluations',
    component: () => import('@/pages/EvaluationPage.vue'),
    meta: {
      layout: 'staff',
      roles: ['ACADEMIC'],
      evaluationPortal: 'staff',
    },
  },
  // ── Cài đặt cá nhân (liên hệ / mật khẩu / thiết bị đăng nhập) ─────
  {
    path: '/staff/settings',
    name: 'staff-settings',
    component: () => import('@/pages/SettingsPage.vue'),
    meta: { layout: 'staff', roles: ['ACCOUNTANT', 'HR', 'ACADEMIC', 'SALES'] },
  },
  // ── Hồ sơ của tôi (chỉ xem — tách riêng khỏi Cài đặt) ─────────────
  {
    path: '/staff/profile',
    name: 'staff-profile',
    component: () => import('@/pages/MyProfilePage.vue'),
    meta: { layout: 'staff', roles: ['ACCOUNTANT', 'HR', 'ACADEMIC', 'SALES'] },
  },
]
