import { createRouter, createWebHistory } from 'vue-router'

import HomePage from '@/pages/HomePage.vue'
import { useAuthStore } from '@/stores/auth'
import { roleHome } from '@/router/roleHome'

const routes = [
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
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('@/pages/DashboardPage.vue'),
    // Khu quản trị: chỉ ADMIN & EMPLOYEE.
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
  {
    path: '/school',
    name: 'school-home',
    component: () => import('@/pages/SchoolPage.vue'),
    meta: { layout: 'blank', roles: ['SCHOOL'] },
  },
  {
    path: '/teacher',
    name: 'teacher-home',
    component: () => import('@/pages/TeacherPage.vue'),
    meta: { layout: 'blank', roles: ['TEACHER'] },
  },
  {
    path: '/reset-password',
    name: 'reset-password',
    component: () => import('@/pages/ResetPasswordPage.vue'),
    meta: { layout: 'blank', public: true },
  },
  {
    // Tạo tài khoản GV/Trường — chỉ ADMIN & EMPLOYEE.
    path: '/users/new',
    name: 'user-create',
    component: () => import('@/pages/RegisterUserPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// Route guard: 3 lớp kiểm tra.
router.beforeEach((to) => {
  const auth = useAuthStore()

  // 1) Trang cần đăng nhập mà chưa đăng nhập -> đẩy về /login (nhớ trang muốn vào).
  if (!to.meta.public && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  // 2) Đã đăng nhập mà vào /login -> về thẳng "nhà" theo vai trò.
  if (to.name === 'login' && auth.isLoggedIn) {
    return roleHome(auth.roles)
  }
  // 3) Vào trang không thuộc vai trò của mình -> chuyển về "nhà" của vai trò.
  if (to.meta.roles && auth.isLoggedIn && !to.meta.roles.some((r) => auth.roles.includes(r))) {
    return roleHome(auth.roles)
  }
  return true
})

export default router
