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
