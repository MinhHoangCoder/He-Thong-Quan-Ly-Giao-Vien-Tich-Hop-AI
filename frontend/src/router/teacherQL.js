import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/dashboard/teachers',
    name: 'teacher-list',
    component: () => import('@/pages/TeacherListPage.vue'),
    meta: { layout: 'admin', roles: ['ADMIN', 'EMPLOYEE'] },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})


export default router