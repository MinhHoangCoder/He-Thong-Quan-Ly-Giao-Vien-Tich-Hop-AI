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
]
