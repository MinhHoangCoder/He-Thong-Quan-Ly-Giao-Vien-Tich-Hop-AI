<script setup>
// Layout cho GIÁO VIÊN (tự phục vụ): xem lịch dạy, chấm công, phiếu lương, hồ sơ.
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
      { label: 'Lịch dạy của tôi', icon: 'schedule', to: '#' },
      { label: 'Chấm công', icon: 'clock', to: '#' },
      { label: 'Bảng công', icon: 'attendance', to: '#' },
    ],
  },
  {
    title: 'Cá nhân',
    items: [
      { label: 'Phiếu lương', icon: 'payroll', to: '#' },
      { label: 'Hồ sơ của tôi', icon: 'teacher', to: '#' },
      { label: 'Đánh giá', icon: 'evaluation', to: '#' },
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
