// Routes CÔNG KHAI (không cần đăng nhập): trang chủ, đăng nhập, đặt lại mật khẩu.
// Quy ước nhóm: thêm route mới vào CUỐI mảng để hạn chế merge conflict.
import HomePage from '@/pages/HomePage.vue'

export const publicRoutes = [
  {
    path: '/',
    name: 'home',
    component: HomePage,
    meta: { layout: 'default', public: true },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/pages/LoginPage.vue'),
    meta: { layout: 'blank', public: true },
  },
  {
    path: '/reset-password',
    name: 'reset-password',
    component: () => import('@/pages/ResetPasswordPage.vue'),
    meta: { layout: 'blank', public: true },
  },
]
