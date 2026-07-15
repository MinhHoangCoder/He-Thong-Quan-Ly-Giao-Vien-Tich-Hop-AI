<script setup>
// Layout cho NHÂN VIÊN PHÒNG BAN (HR / ACCOUNTANT / ACADEMIC / SALES).
// Dùng chung PortalShell; điểm khác biệt: menu sidebar TỰ LỌC theo `perms` trong token
// (xem router/staffModules.js) → mỗi phòng ban chỉ thấy đúng module của mình.
import { computed } from 'vue'
import PortalShell from '@/layouts/PortalShell.vue'
import { useAuthStore } from '@/stores/auth'
import { buildStaffNav } from '@/router/staffModules'

const auth = useAuthStore()
const nav = computed(() => buildStaffNav(auth.user?.perms ?? []))
</script>

<template>
  <PortalShell :nav="nav" settings-to="/staff/settings" profile-to="/staff/profile">
    <slot />
  </PortalShell>
</template>
