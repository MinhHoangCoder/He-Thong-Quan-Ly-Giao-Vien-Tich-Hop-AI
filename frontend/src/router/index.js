import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { roleHome } from '@/router/roleHome'
// Routes tách theo KHU VỰC để mỗi người làm feature chỉ sửa file của khu mình
// (hạn chế merge conflict). File này chỉ GHÉP lại + giữ route guard — ít khi phải sửa.
import { publicRoutes } from '@/router/public.routes'
import { adminRoutes } from '@/router/admin.routes'
import { teacherRoutes } from '@/router/teacher.routes'
import { staffRoutes } from '@/router/staff.routes'

// Route 404 CATCH-ALL: khớp MỌI URL không trùng route nào ở trên. BẮT BUỘC đặt CUỐI
// CÙNG — vue-router khớp theo thứ tự, để trước sẽ nuốt hết các route thật. public:true
// để guard không đẩy khách chưa đăng nhập về /login (404 hiện cho cả người lạ lẫn đã
// đăng nhập). Không đặt trong *.routes.js khu vực nào vì nó là route TOÀN CỤC.
const notFoundRoute = {
  path: '/:pathMatch(.*)*',
  name: 'not-found',
  component: () => import('@/pages/NotFoundPage.vue'),
  meta: { layout: 'blank', public: true },
}

const routes = [
  ...publicRoutes,
  ...adminRoutes,
  ...teacherRoutes,
  ...staffRoutes,
  notFoundRoute,
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
  //    NGOẠI LỆ ?add=1: chế độ "Thêm tài khoản" (account switcher) — cho phép vào
  //    trang login để đăng nhập THÊM tài khoản, các phiên đang có giữ nguyên.
  if (to.name === 'login' && auth.isLoggedIn && to.query.add !== '1') {
    return roleHome(auth.roles)
  }
  // 3) Vào trang không thuộc vai trò của mình -> chuyển về "nhà" của vai trò.
  if (to.meta.roles && auth.isLoggedIn && !to.meta.roles.some((r) => auth.roles.includes(r))) {
    return roleHome(auth.roles)
  }
  return true
})

export default router
