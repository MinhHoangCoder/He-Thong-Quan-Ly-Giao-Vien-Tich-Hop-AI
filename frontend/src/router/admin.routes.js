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
    path: '/admin/lessons',
    name: 'admin-lesson-list',
    component: () => import('@/pages/LessonListPage.vue'),
    meta: {
      layout: 'admin',
      roles: ['ADMIN', 'EMPLOYEE'],
    },
  },
  {
    // FIX (2026-07-10): route này bị copy-paste nhầm roles của khu STAFF
    // (['ACCOUNTANT','HR','ACADEMIC','SALES']) trong khi path/tên route lại
    // thuộc khu ADMIN. Vì route guard (router/index.js, bước 3) so khớp
    // to.meta.roles với role hiện tại rồi mới cho vào trang, ADMIN/EMPLOYEE
    // KHÔNG nằm trong danh sách cũ -> bấm "Sửa"/"Thêm bài giảng" ở /admin/lessons
    // luôn bị đá về roleHome() = trang dashboard. Phải khớp với 'admin-lesson-list'.
    path: '/admin/lessons/new',
    name: 'admin-lesson-new',
    component: () => import('@/pages/LessonFormPage.vue'),
    meta: {
      layout: 'admin',
      roles: ['ADMIN', 'EMPLOYEE'],
    },
  },
  {
    // FIX (2026-07-10): tương tự route phía trên — trả lại đúng roles ADMIN/EMPLOYEE.
    path: '/admin/lessons/:id/edit',
    name: 'admin-lesson-edit',
    component: () => import('@/pages/LessonFormPage.vue'),
    meta: {
      layout: 'admin',
      roles: ['ADMIN', 'EMPLOYEE'],
    },
  },
  {
    path: '/admin/lessons',
    name: 'admin-lesson-list',
    component: () => import('@/pages/LessonListPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
  {
    path: '/admin/lessons/new',
    name: 'admin-lesson-new',
    component: () => import('@/pages/LessonFormPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
  {
    path: '/admin/lessons/:id/edit',
    name: 'admin-lesson-edit',
    component: () => import('@/pages/LessonFormPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
  // ── Nhóm môn (SubjectCategory) — quản lý danh mục ───────────────
  {
    path: '/admin/subject-categories',
    name: 'admin-subject-category-list',
    component: () => import('@/pages/SubjectCategoryListPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
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
  // ── Trường khách hàng (School) — quản lý trường ─────────────────
  {
    path: '/admin/schools',
    name: 'admin-school-list',
    component: () => import('@/pages/SchoolListPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },

  // ── Đánh giá giáo viên ──────────────────────────────────────────
  {
    path: '/admin/evaluations',
    name: 'admin-evaluations',
    component: () => import('@/pages/EvaluationPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'], evaluationPortal: 'admin' },
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
  // ── Hồ sơ của tôi (chỉ xem — tách riêng khỏi Cài đặt) ─────────────
  {
    path: '/profile',
    name: 'admin-profile',
    component: () => import('@/pages/MyProfilePage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
  // ── Lớp học (SchoolClass) ────────────────────────────────────────
  {
    path: '/admin/classes',
    name: 'admin-class-list',
    component: () => import('@/pages/SchoolClassListPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
  // ── Lịch nghỉ (Holiday) — ngày không sinh buổi dạy ────
  {
    path: '/admin/holidays',
    name: 'admin-holiday-list',
    component: () => import('@/pages/HolidayListPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
  // ── Tạo / sửa phân công (wizard 3 bước, trang riêng) ─────────────
  // Tách khỏi modal cũ vì bước xếp tiết là lưới thời khóa biểu 10 tiết × 7 thứ cho TỪNG
  // trường — modal không đủ chỗ. Sửa dùng CHUNG trang này để hai luồng không lệch nhau.
  {
    path: '/assignments/new',
    name: 'assignment-new',
    component: () => import('@/pages/AssignmentFormPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
  {
    path: '/assignments/:id/edit',
    name: 'assignment-edit',
    component: () => import('@/pages/AssignmentFormPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
]
