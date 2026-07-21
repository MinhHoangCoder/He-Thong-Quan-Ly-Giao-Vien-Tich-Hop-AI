<script setup>
// Layout cho GIÁO VIÊN (tự phục vụ): xem lịch dạy, phiếu lương, hồ sơ.
// (to: '#' = trang chưa làm, vẫn hiển thị để thấy cấu trúc.)
import PortalShell from '@/layouts/PortalShell.vue'

const nav = [
  {
    title: 'Tổng quan',
    items: [{ label: 'Dashboard', icon: 'dashboard', to: '/teacher' }],
  },
  {
    title: 'Công việc',
    items: [
      { label: 'Lịch dạy của tôi', icon: 'schedule', to: '/teacher/schedule' },
      { label: 'Bảng chấm công', icon: 'attendance', to: '/teacher/attendance' },
    ],
  },
  {
    title: 'Kho bài giảng',
    items: [{ label: 'Bài giảng', icon: 'subject', to: '/teacher/lessons' }],
  },
  {
    title: 'Cá nhân',
    items: [
      { label: 'Phiếu lương', icon: 'payroll', to: '#' },
      { label: 'Hồ sơ của tôi', icon: 'teacher', to: '/teacher/profile' },
      { label: 'Đánh giá', icon: 'evaluation', to: '/teacher/evaluations' },
    ],
  },
  // Thông báo KHÔNG để ở sidebar: đã có chuông thông báo trên topbar (PortalShell).
]
// "Cài đặt" không nằm trong sidebar: vào qua dropdown avatar trên topbar (PortalShell).
</script>

<template>
  <PortalShell :nav="nav" settings-to="/teacher/settings" profile-to="/teacher/profile">
    <slot />
  </PortalShell>
</template>
