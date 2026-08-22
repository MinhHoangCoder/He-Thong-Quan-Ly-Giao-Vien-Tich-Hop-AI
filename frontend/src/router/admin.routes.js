// Routes khu QUẢN TRỊ (chỉ ADMIN — Flyway V33 bỏ role nhân viên) — layout 'admin'.
// Ai làm feature thuộc khu quản trị (phân công, lịch dạy, chấm công, lương...)
// thì thêm route vào CUỐI mảng này, không đụng file khu vực khác.
export const adminRoutes = [
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('@/pages/DashboardPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN'] },
  },
  {
    path: '/dashboard/teacher',
    name: 'teacher-list',
    component: () => import('@/pages/TeacherListPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN'] },
  },
  {
    path: '/assignments',
    name: 'assignments',
    component: () => import('@/pages/AssignmentPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN'] },
  },
  {
    path: '/schedule',
    name: 'schedule',
    component: () => import('@/pages/SchedulePage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN'] },
  },
  {
    path: '/attendance',
    name: 'attendance',
    component: () => import('@/pages/AttendancePage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN'] },
  },
  {
    path: '/payroll',
    name: 'payroll',
    component: () => import('@/pages/PayrollPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN'] },
  },
  {
    path: '/admin/lessons',
    name: 'admin-lesson-list',
    component: () => import('@/pages/LessonListPage.vue'),
    meta: {
      layout: 'admin',
      roles: ['ADMIN'],
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
      roles: ['ADMIN'],
    },
  },
  {
    // FIX (2026-07-10): tương tự route phía trên — trả lại đúng roles của khu ADMIN.
    path: '/admin/lessons/:id/edit',
    name: 'admin-lesson-edit',
    component: () => import('@/pages/LessonFormPage.vue'),
    meta: {
      layout: 'admin',
      roles: ['ADMIN'],
    },
  },
  // ── Nhóm môn (SubjectCategory) — quản lý danh mục ───────────────
  {
    path: '/admin/subject-categories',
    name: 'admin-subject-category-list',
    component: () => import('@/pages/SubjectCategoryListPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN'] },
  },
  // ── Trường khách hàng (School) — quản lý trường ─────────────────
  {
    path: '/admin/schools',
    name: 'admin-school-list',
    component: () => import('@/pages/SchoolListPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN'] },
  },

  // ── Đánh giá giáo viên ──────────────────────────────────────────
  {
    path: '/admin/evaluations',
    name: 'admin-evaluations',
    component: () => import('@/pages/EvaluationPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN'], evaluationPortal: 'admin' },
  },
  // ── Khu CÀI ĐẶT ──────────────────────────────────────────────────
  {
    // Cài đặt CÁ NHÂN của người đang đăng nhập (hồ sơ / mật khẩu / thiết bị).
    // Cùng component với /staff/settings, /teacher/settings... — chỉ khác layout bọc ngoài.
    path: '/settings',
    name: 'admin-settings',
    component: () => import('@/pages/SettingsPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN'] },
  },
  // ── Hồ sơ của tôi (chỉ xem — tách riêng khỏi Cài đặt) ─────────────
  {
    path: '/profile',
    name: 'admin-profile',
    component: () => import('@/pages/MyProfilePage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN'] },
  },
  // ── Lớp học (SchoolClass) ────────────────────────────────────────
  {
    path: '/admin/classes',
    name: 'admin-class-list',
    component: () => import('@/pages/SchoolClassListPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN'] },
  },
  // ── Lịch nghỉ (Holiday) — ngày không sinh buổi dạy ────
  {
    path: '/admin/holidays',
    name: 'admin-holiday-list',
    component: () => import('@/pages/HolidayListPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN'] },
  },
  // ── Hệ thống: nhật ký thao tác + rà soát dữ liệu mồ côi ──────────
  // Chỉ ADMIN: đây là câu hỏi về sức khỏe dữ liệu, không phải nghiệp vụ hằng ngày.
  {
    path: '/admin/system',
    name: 'admin-system',
    component: () => import('@/pages/SystemLogPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN'] },
  },
  // ── Tạo / sửa phân công (wizard 3 bước, trang riêng) ─────────────
  // Tách khỏi modal cũ vì bước xếp tiết là lưới thời khóa biểu 10 tiết × 7 thứ cho TỪNG
  // trường — modal không đủ chỗ. Sửa dùng CHUNG trang này để hai luồng không lệch nhau.
  {
    path: '/assignments/new',
    name: 'assignment-new',
    component: () => import('@/pages/AssignmentFormPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN'] },
  },
  {
    path: '/assignments/:id/edit',
    name: 'assignment-edit',
    component: () => import('@/pages/AssignmentFormPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN'] },
  },
]
