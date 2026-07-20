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
      { label: 'Giáo viên', icon: 'teacher', to: '/dashboard/teacher' },
      { label: 'Đánh giá', icon: 'evaluation', to: '/admin/evaluations' },
    ],
  },
  {
    title: 'Môn học',
    items: [
      { label: 'Bài giảng', icon: 'subject', to: '/admin/lessons' },
      { label: 'Nhóm môn học', icon: 'subject', to: '/admin/subject-categories' },
    ],
  },
  {
    title: 'Trường học',
    items: [
      { label: 'Trường', icon: 'school', to: '/admin/schools' },
      { label: 'Lớp học', icon: 'class', to: '/admin/classes' },
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
  // TẠM ẨN CẢ NHÓM "Hệ thống" (2026-07-20): hai mục bên trong đều đang ẩn cho đợt
  // bảo vệ nên để lại nhóm rỗng sẽ trơ mỗi tiêu đề "HỆ THỐNG" trên sidebar.
  //  - Trợ lý AI: quy mô đồ án chưa làm AI thật (trang + backend vẫn còn trong code).
  //  - Ma trận quyền: demo chỉ dùng admin + teacher (trang /settings/roles vẫn chạy).
  // Mở lại: bỏ comment cả khối dưới đây (và dòng mục tương ứng).
  // {
  //   title: 'Hệ thống',
  //   items: [
  //     { label: 'Trợ lý AI', icon: 'ai', to: '/ai-assistant' },
  //     { label: 'Ma trận quyền', icon: 'eye', to: '/settings/roles' },
  //   ],
  // },
]
// "Cài đặt" không nằm trong sidebar: vào qua dropdown avatar trên topbar (PortalShell).
</script>

<template>
  <PortalShell :nav="nav" settings-to="/settings" profile-to="/profile">
    <slot />
  </PortalShell>
</template>