<script setup>
// Layout cho ADMIN & NHÂN VIÊN: dùng khung chung PortalShell, chỉ truyền menu riêng.
import PortalShell from '@/layouts/PortalShell.vue'

const nav = [
  {
    title: 'Tổng quan',
    items: [{ label: 'Dashboard', icon: 'dashboard', to: '/dashboard' }],
  },
  {
    title: 'Nhân sự',
    items: [
      { label: 'Giáo viên', icon: 'teacher', to: '/dashboard/teacher', badge: '128' },
      { label: 'Môn học', icon: 'subject', to: '#' },
      { label: 'Đánh giá', icon: 'evaluation', to: '#' },
    ],
  },
  {
    title: 'Kho bài giảng',
    items: [{ label: 'Bài giảng', icon: 'subject', to: '/admin/lessons' }],
  },
  {
    title: 'Khách hàng',
    items: [
      { label: 'Trường', icon: 'school', to: '#' },
      { label: 'Lớp & Học sinh', icon: 'attendance', to: '#' },
    ],
  },
  {
    title: 'Điều phối',
    items: [
      { label: 'Phân công', icon: 'assignment', to: '/assignments' },
      { label: 'Lịch dạy', icon: 'schedule', to: '/schedule' },
      { label: 'Chấm công', icon: 'clock', to: '/attendance' },
      { label: 'Bảng lương', icon: 'payroll', to: '/payroll' },
    ],
  },
  {
    title: 'Hệ thống',
    items: [
      { label: 'Trợ lý AI', icon: 'ai', to: '/ai-assistant' },
      { label: 'Ma trận quyền', icon: 'eye', to: '/settings/roles' },
    ],
  },
]
// "Cài đặt" không nằm trong sidebar: vào qua dropdown avatar trên topbar (PortalShell).
</script>

<template>
  <PortalShell :nav="nav" settings-to="/settings">
    <slot />
  </PortalShell>
</template>
