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
      // Đặt ngay dưới Lịch dạy vì đây là thứ quyết định lịch dạy có buổi hay không —
      // ai thấy buổi dạy "biến mất" khỏi thời khóa biểu sẽ tìm câu trả lời ở ngay đây.
      { label: 'Lịch nghỉ', icon: 'clock', to: '/admin/holidays' },
      { label: 'Chấm công', icon: 'clock', to: '/attendance' },
      { label: 'Bảng lương', icon: 'payroll', to: '/payroll' },
    ],
  },
  // Nhóm "Hệ thống" TẠM ẨN cho đợt bảo vệ (demo chỉ dùng admin + teacher).
  // Trang /settings/roles vẫn hoạt động — mở lại bằng cách bỏ comment khối dưới.
  // {
  //   title: 'Hệ thống',
  //   items: [{ label: 'Ma trận quyền', icon: 'eye', to: '/settings/roles' }],
  // },
]
// "Cài đặt" không nằm trong sidebar: vào qua dropdown avatar trên topbar (PortalShell).
</script>

<template>
  <PortalShell :nav="nav" settings-to="/settings" profile-to="/profile">
    <slot />
  </PortalShell>
</template>
