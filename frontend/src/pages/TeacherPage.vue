<script setup>
// Trang đích tạm cho vai trò GIÁO VIÊN. Portal đầy đủ (lịch dạy, chấm công...) làm ở PR sau.
import { useAuthStore } from '@/stores/auth'
import { useLogout } from '@/composables/useLogout'
import SvgIcon from '@/components/ui/SvgIcon.vue'

const auth = useAuthStore()
const logout = useLogout()
</script>

<template>
  <div class="portal">
    <span class="portal__logo"><SvgIcon name="teacher" :size="26" /></span>
    <h1>Xin chào, {{ auth.user?.fullName }}</h1>
    <p class="portal__role">Khu vực dành cho <strong>Giáo viên</strong></p>
    <p class="portal__todo">Trang dành cho giáo viên đang được phát triển.</p>
    <button class="portal__logout" @click="logout">
      <SvgIcon name="logout" :size="18" /> Đăng xuất
    </button>
  </div>
</template>

<style scoped>
.portal {
  width: 100%;
  max-width: 420px;
  padding: 36px;
  text-align: center;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(13, 148, 136, 0.12);
  border: 1px solid rgba(13, 148, 136, 0.1);
}
.portal__logo {
  display: inline-grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: var(--grad-primary, linear-gradient(135deg, #0d9488, #16a34a));
  color: #fff;
  margin-bottom: 12px;
}
.portal h1 {
  margin: 0 0 6px;
  font-size: 22px;
  color: #0f172a;
}
.portal__role {
  margin: 0 0 4px;
  color: #475569;
}
.portal__todo {
  margin: 0 0 22px;
  color: #94a3b8;
  font-size: 14px;
}
.portal__logout {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border: none;
  border-radius: 10px;
  background: var(--c-primary, #0d9488);
  color: #fff;
  font-weight: 600;
  cursor: pointer;
}
.portal__logout:hover {
  filter: brightness(1.05);
}
</style>
