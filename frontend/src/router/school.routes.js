// Routes PORTAL TRƯỜNG (vai trò SCHOOL) — layout 'school'.
// Trường chỉ XEM lịch/báo cáo (không gửi yêu cầu) — route mới thêm vào CUỐI mảng.
export const schoolRoutes = [
  {
    path: '/school',
    name: 'school-home',
    component: () => import('@/pages/SchoolDashboardPage.vue'),
    meta: { layout: 'school', roles: ['SCHOOL'] },
  },
  // ── Đánh giá giáo viên ──────────────────────────────────────────
  {
    path: '/school/evaluations',
    name: 'school-evaluations',
    component: () => import('@/pages/EvaluationPage.vue'),
    meta: { layout: 'school', roles: ['SCHOOL'], evaluationPortal: 'school' },
  },
  // ── Cài đặt cá nhân (hồ sơ / mật khẩu / thiết bị đăng nhập) ─────
  {
    path: '/school/settings',
    name: 'school-settings',
    component: () => import('@/pages/SettingsPage.vue'),
    meta: { layout: 'school', roles: ['SCHOOL'] },
  },
]
