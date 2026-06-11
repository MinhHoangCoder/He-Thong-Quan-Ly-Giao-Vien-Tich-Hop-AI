// Routes PORTAL TRƯỜNG (vai trò SCHOOL) — layout 'school'.
// Trường chỉ XEM lịch/báo cáo (không gửi yêu cầu) — route mới thêm vào CUỐI mảng.
export const schoolRoutes = [
  {
    path: '/school',
    name: 'school-home',
    component: () => import('@/pages/SchoolDashboardPage.vue'),
    meta: { layout: 'school', roles: ['SCHOOL'] },
  },
]
