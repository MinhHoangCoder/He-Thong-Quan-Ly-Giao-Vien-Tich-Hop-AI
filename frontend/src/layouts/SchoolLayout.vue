<script setup>
// Layout cho TRƯỜNG (chỉ XEM thống kê/báo cáo theo quy ước nghiệp vụ).
// (to: '#' = trang chưa làm, vẫn hiển thị để thấy cấu trúc.)
import PortalShell from '@/layouts/PortalShell.vue'

const nav = [
  {
    title: 'Tổng quan',
    items: [{ label: 'Dashboard', icon: 'dashboard', to: '/school' }],
  },
  {
    title: 'Theo dõi',
    items: [
      { label: 'Lịch dạy tại trường', icon: 'schedule', to: '#' },
      { label: 'Giáo viên', icon: 'teacher', to: '#' },
      { label: 'Lớp & Học sinh', icon: 'attendance', to: '#' },
    ],
  },
  {
    title: 'Báo cáo',
    items: [
      { label: 'Thống kê', icon: 'evaluation', to: '#' },
      { label: 'Phản hồi', icon: 'mail', to: '#' },
    ],
  },
  {
    title: 'Hệ thống',
    items: [{ label: 'Thông báo', icon: 'bell', to: '#' }],
  },
]
</script>

<template>
  <PortalShell :nav="nav">
    <slot />
  </PortalShell>
</template>
