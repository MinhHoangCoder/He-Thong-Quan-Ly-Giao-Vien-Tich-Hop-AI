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
    // Tạo tài khoản GV/Trường — chỉ ADMIN & EMPLOYEE.
    path: '/users/new',
    name: 'user-create',
    component: () => import('@/pages/RegisterUserPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
]
