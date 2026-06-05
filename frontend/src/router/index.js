import { createRouter, createWebHistory } from 'vue-router'

import HomePage from '@/pages/HomePage.vue'

const routes = [
  {
    path: '/',
    name: 'home',
    component: HomePage,
    meta: { layout: 'default', public: true },
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    // () => import(...) = LAZY LOAD: file trang chỉ tải về trình duyệt KHI vào
    // /dashboard, không tải sẵn lúc mở app → app nhẹ hơn. (HomePage import thẳng
    // ở đầu file vì là trang chủ, cần ngay.)
    component: () => import('@/pages/DashboardPage.vue'),
    // meta = dữ liệu phụ tự gắn cho route. 'layout' là QUY ƯỚC RIÊNG của dự án:
    // App.vue đọc nó để chọn khung (xem App.vue). public:true = chưa cần đăng nhập.
    meta: { layout: 'admin', public: true },
  },
  // Ví dụ trang dùng layout trống (cho login sau này):
  // {
  //   path: '/login',
  //   name: 'login',
  //   component: () => import('@/pages/LoginPage.vue'),
  //   meta: { layout: 'blank', public: true },
  // },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// Route guard: kiểm tra đăng nhập/role.
// Hiện tại mọi route đang public; khi làm phần login thì bật phần kiểm tra bên dưới.
router.beforeEach((to) => {
  // const auth = useAuthStore()
  // if (!to.meta.public && !auth.isLoggedIn) {
  //   return { name: 'login' }
  // }
  return true
})

export default router
