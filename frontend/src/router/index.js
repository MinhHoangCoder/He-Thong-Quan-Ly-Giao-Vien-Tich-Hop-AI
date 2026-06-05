import { createRouter, createWebHistory } from 'vue-router'

import HomePage from '@/pages/HomePage.vue'

const routes = [
  {
    path: '/',
    name: 'home',
    component: HomePage,
    meta: { layout: 'default', public: true },
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
