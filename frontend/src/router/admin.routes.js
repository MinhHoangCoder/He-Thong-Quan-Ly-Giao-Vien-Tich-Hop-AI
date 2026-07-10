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
    path: '/ai-assistant',
    name: 'ai-assistant',
    component: () => import('@/pages/AiAssistantPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
]
