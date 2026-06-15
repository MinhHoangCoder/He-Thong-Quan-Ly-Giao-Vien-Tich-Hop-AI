import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { roleHome } from '@/router/roleHome'
// Routes tách theo KHU VỰC để mỗi người làm feature chỉ sửa file của khu mình
// (hạn chế merge conflict). File này chỉ GHÉP lại + giữ route guard — ít khi phải sửa.
import { publicRoutes } from '@/router/public.routes'
import { adminRoutes } from '@/router/admin.routes'
import { schoolRoutes } from '@/router/school.routes'
import { teacherRoutes } from '@/router/teacher.routes'
import { staffRoutes } from '@/router/staff.routes'

const routes = [...publicRoutes, ...adminRoutes, ...schoolRoutes, ...teacherRoutes, ...staffRoutes]

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
