<script setup>
// Trang chủ PORTAL PHÒNG BAN: chào theo phòng ban + lưới "module của bạn" lọc theo quyền.
// Các thẻ hiện là placeholder (feature do thành viên khác build); ở đây chỉ dựng khung +
// chứng minh cơ chế RBAC: thấy đúng những gì mình được cấp quyền.
import { computed } from 'vue'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import { useAuthStore } from '@/stores/auth'
import { accessibleModules, DEPARTMENT_LABELS } from '@/router/staffModules'

const auth = useAuthStore()
const deptLabel = computed(() => DEPARTMENT_LABELS[auth.primaryRole] || 'Nhân viên')
const modules = computed(() => accessibleModules(auth.user?.perms ?? []))
</script>

<template>
  <section class="staff-home">
    <header class="hero">
      <div>
        <p class="hero__hello">Xin chào,</p>
        <h1 class="hero__name">{{ auth.user?.fullName || 'Nhân viên' }}</h1>
        <span class="hero__badge">Phòng {{ deptLabel }}</span>
      </div>
    </header>

    <h2 class="section-title">Chức năng của bạn</h2>

    <div v-if="modules.length" class="grid">
      <article v-for="m in modules" :key="m.perm" class="card">
        <span class="card__icon"><SvgIcon :name="m.icon" :size="22" /></span>
        <div class="card__body">
          <h3 class="card__title">{{ m.label }}</h3>
          <p class="card__desc">{{ m.desc }}</p>
        </div>
        <span class="card__soon">Sắp ra mắt</span>
      </article>
    </div>

    <p v-else class="empty">Tài khoản chưa được cấp quyền cho module nào. Liên hệ quản trị viên.</p>
  </section>
</template>

<style scoped>
.staff-home {
  max-width: 1080px;
  margin: 0 auto;
}
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.6rem 1.8rem;
  border-radius: 16px;
  color: #fff;
  background: var(--grad-hero, linear-gradient(120deg, #1e3a8a, #2563eb));
  box-shadow: 0 14px 34px rgba(15, 40, 80, 0.18);
}
.hero__hello {
  margin: 0;
  opacity: 0.85;
  font-size: 0.95rem;
}
.hero__name {
  margin: 0.15rem 0 0.5rem;
  font-size: 1.8rem;
  font-weight: 800;
}
.hero__badge {
  display: inline-block;
  padding: 0.25rem 0.8rem;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.3);
  font-size: 0.85rem;
  font-weight: 600;
}
.section-title {
  margin: 1.8rem 0 1rem;
  font-size: 1.1rem;
  color: var(--c-text, #1e293b);
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 1rem;
}
.card {
  position: relative;
  display: flex;
  gap: 0.85rem;
  padding: 1.1rem;
  background: var(--c-surface);
  border: 1px solid var(--c-border, #e2e8f0);
  border-radius: 14px;
  box-shadow: 0 6px 18px rgba(15, 40, 80, 0.06);
  transition:
    transform 0.15s,
    box-shadow 0.15s;
}
.card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 26px rgba(15, 40, 80, 0.12);
}
.card__icon {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  color: var(--c-primary, #f97316);
  background: rgba(249, 115, 22, 0.1);
}
.card__title {
  margin: 0 0 0.25rem;
  font-size: 1rem;
  color: var(--c-text, #1e293b);
}
.card__desc {
  margin: 0;
  font-size: 0.85rem;
  color: var(--c-text-muted, #64748b);
  line-height: 1.45;
}
.card__soon {
  position: absolute;
  top: 0.7rem;
  right: 0.7rem;
  font-size: 0.65rem;
  font-weight: 700;
  color: #92600a;
  background: #fff3cd;
  padding: 0.1rem 0.45rem;
  border-radius: 999px;
}
.empty {
  padding: 2rem;
  text-align: center;
  color: var(--c-text-muted, #64748b);
  background: var(--c-surface);
  border: 1px dashed var(--c-border, #e2e8f0);
  border-radius: 14px;
}
</style>
