<script setup>
// Layout quản trị: sidebar điều hướng + topbar. Nội dung trang render qua <slot />.
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import { useAuthStore } from '@/stores/auth'
import { useLogout } from '@/composables/useLogout'

const collapsed = ref(false) // thu gọn sidebar
const mobileOpen = ref(false) // mở sidebar trên màn nhỏ

const auth = useAuthStore()
const onLogout = useLogout()
const roleLabels = {
  ADMIN: 'Quản trị viên',
  EMPLOYEE: 'Nhân viên',
  SCHOOL: 'Trường',
  TEACHER: 'Giáo viên',
}
const roleLabel = computed(() => roleLabels[auth.primaryRole] || 'Người dùng')

// Nhóm menu theo nghiệp vụ TSDMS. (to: '#' = chưa làm trang, vẫn hiển thị.)
const nav = [
  {
    title: 'Tổng quan',
    items: [{ label: 'Dashboard', icon: 'dashboard', to: '/dashboard' }],
  },
  {
    title: 'Nhân sự',
    items: [
      { label: 'Tạo tài khoản', icon: 'plus', to: '/users/new' },
      { label: 'Giáo viên', icon: 'teacher', to: '#', badge: '128' },
      { label: 'Môn học', icon: 'subject', to: '#' },
      { label: 'Đánh giá', icon: 'evaluation', to: '#' },
    ],
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
      { label: 'Phân công', icon: 'assignment', to: '#' },
      { label: 'Lịch dạy', icon: 'schedule', to: '#' },
      { label: 'Chấm công', icon: 'clock', to: '#' },
      { label: 'Bảng lương', icon: 'payroll', to: '#' },
    ],
  },
  {
    title: 'Hệ thống',
    items: [
      { label: 'Trợ lý AI', icon: 'ai', to: '#' },
      { label: 'Cài đặt', icon: 'settings', to: '#' },
    ],
  },
]
</script>

<template>
  <div class="admin" :class="{ 'is-collapsed': collapsed, 'is-mobile-open': mobileOpen }">
    <!-- Sidebar -->
    <aside class="sidebar">
      <div class="sidebar__brand">
        <span class="sidebar__logo"><SvgIcon name="ai" :size="22" /></span>
        <span class="sidebar__name">TSDMS</span>
      </div>

      <nav class="sidebar__nav">
        <div v-for="group in nav" :key="group.title" class="navgroup">
          <p class="navgroup__title">{{ group.title }}</p>
          <RouterLink
            v-for="item in group.items"
            :key="item.label"
            :to="item.to"
            class="navlink"
            active-class="is-active"
          >
            <span class="navlink__icon"><SvgIcon :name="item.icon" :size="19" /></span>
            <span class="navlink__label">{{ item.label }}</span>
            <span v-if="item.badge" class="navlink__badge">{{ item.badge }}</span>
          </RouterLink>
        </div>
      </nav>
    </aside>

    <!-- Lớp phủ khi mở sidebar trên mobile -->
    <div class="backdrop" @click="mobileOpen = false" />

    <!-- Khu vực phải -->
    <div class="main">
      <header class="topbar">
        <button class="iconbtn topbar__menu" @click="collapsed = !collapsed">
          <SvgIcon name="menu" :size="20" />
        </button>
        <button class="iconbtn topbar__menu--mobile" @click="mobileOpen = !mobileOpen">
          <SvgIcon name="menu" :size="20" />
        </button>

        <div class="topbar__search">
          <SvgIcon name="search" :size="18" />
          <input type="text" placeholder="Tìm giáo viên, trường, lịch dạy…" />
        </div>

        <div class="topbar__actions">
          <button class="iconbtn has-dot"><SvgIcon name="mail" :size="20" /></button>
          <button class="iconbtn has-dot"><SvgIcon name="bell" :size="20" /></button>
          <div class="topbar__user">
            <img src="https://i.pravatar.cc/64?img=12" alt="avatar" />
            <div class="topbar__user-info">
              <strong>{{ auth.user?.fullName || 'Người dùng' }}</strong>
              <small>{{ roleLabel }}</small>
            </div>
          </div>
          <button class="iconbtn" title="Đăng xuất" @click="onLogout">
            <SvgIcon name="logout" :size="20" />
          </button>
        </div>
      </header>

      <main class="content">
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin {
  --sidebar-w: 256px;
  display: flex;
  min-height: 100vh;
  background: var(--a-bg);
}

/* ===== Sidebar ===== */
.sidebar {
  width: var(--sidebar-w);
  flex: 0 0 var(--sidebar-w);
  /* nền dốc nhẹ cho có chiều sâu thay vì 1 màu phẳng */
  background: linear-gradient(180deg, var(--a-sidebar) 0%, var(--a-sidebar-2) 100%);
  color: #b8ccc7;
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
  transition:
    width var(--t),
    flex-basis var(--t);
}
.sidebar__brand {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 1.15rem 1.3rem;
  font-weight: 800;
  font-size: 1.25rem;
  color: #fff;
  letter-spacing: 0.5px;
}
.sidebar__logo {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--grad-primary);
  color: #fff;
  box-shadow: 0 4px 12px rgba(13, 148, 136, 0.45);
}
.sidebar__nav {
  padding: 0.5rem 0.75rem 1.5rem;
}
.navgroup {
  margin-top: 1rem;
}
.navgroup__title {
  margin: 0 0 0.4rem;
  padding: 0 0.6rem;
  font-size: 0.68rem;
  text-transform: uppercase;
  letter-spacing: 0.8px;
  color: #5f7d77;
  white-space: nowrap;
}
.navlink {
  position: relative; /* để vẽ thanh chỉ báo bên trái khi active */
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.6rem 0.7rem;
  border-radius: 9px;
  color: #b8ccc7;
  text-decoration: none;
  font-size: 0.9rem;
  font-weight: 500;
  margin-bottom: 2px;
  transition:
    background var(--t-fast),
    color var(--t-fast),
    transform var(--t-fast);
}
.navlink:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  transform: translateX(3px); /* nhích nhẹ sang phải khi rê chuột */
}
/* icon to nhẹ khi rê chuột */
.navlink:hover .navlink__icon {
  transform: scale(1.12);
}
.navlink.is-active {
  background: var(--grad-primary);
  color: #fff;
  box-shadow: 0 6px 16px rgba(13, 148, 136, 0.4);
}
/* thanh sáng nhỏ bên trái link đang chọn */
.navlink.is-active::before {
  content: '';
  position: absolute;
  left: -0.75rem;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 22px;
  border-radius: 0 4px 4px 0;
  background: var(--c-accent);
}
.navlink__icon {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  transition: transform var(--t-fast);
}
.navlink__label {
  flex: 1;
  white-space: nowrap;
}
.navlink__badge {
  font-size: 0.7rem;
  font-weight: 700;
  background: var(--c-amber);
  color: #3a2400;
  padding: 0.05rem 0.4rem;
  border-radius: 20px;
}

/* Thu gọn: chỉ còn icon */
.is-collapsed .sidebar {
  width: 76px;
  flex-basis: 76px;
}
.is-collapsed .sidebar__name,
.is-collapsed .navgroup__title,
.is-collapsed .navlink__label,
.is-collapsed .navlink__badge {
  display: none;
}
.is-collapsed .navlink {
  justify-content: center;
}

/* ===== Main ===== */
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 1rem;
  height: 66px;
  padding: 0 1.4rem;
  background: #fff;
  border-bottom: 1px solid var(--a-border);
}
.iconbtn {
  position: relative;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--a-text-muted);
  cursor: pointer;
  transition:
    background var(--t-fast),
    color var(--t-fast),
    transform var(--t-fast);
}
.iconbtn:hover {
  background: var(--a-bg);
  color: var(--c-primary);
  transform: translateY(-1px);
}
.iconbtn.has-dot::after {
  content: '';
  position: absolute;
  top: 9px;
  right: 9px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--c-amber);
  border: 2px solid #fff;
}
.topbar__menu--mobile {
  display: none;
}
.topbar__search {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex: 1;
  max-width: 420px;
  padding: 0 0.85rem;
  height: 42px;
  background: var(--a-bg);
  border: 1px solid var(--a-border);
  border-radius: 10px;
  color: var(--a-text-muted);
  transition:
    border-color var(--t),
    box-shadow var(--t),
    background var(--t);
}
/* sáng viền teal khi đang gõ tìm kiếm */
.topbar__search:focus-within {
  border-color: var(--c-primary);
  background: #fff;
  box-shadow: 0 0 0 3px rgba(13, 148, 136, 0.12);
  color: var(--c-primary);
}
.topbar__search input {
  border: none;
  background: transparent;
  outline: none;
  width: 100%;
  font-size: 0.9rem;
  color: var(--a-text);
}
.topbar__actions {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-left: auto;
}
.topbar__user {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.3rem 0.5rem 0.3rem 0.4rem;
  border-radius: 12px;
  cursor: pointer;
  color: var(--a-text-muted);
  transition: background var(--t-fast);
}
.topbar__user:hover {
  background: var(--a-bg);
}
.topbar__user img {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  object-fit: cover;
}
.topbar__user-info {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}
.topbar__user-info strong {
  font-size: 0.86rem;
  color: var(--a-text);
}
.topbar__user-info small {
  font-size: 0.72rem;
}
.content {
  padding: 1.6rem;
  flex: 1;
}

/* ===== Backdrop (mobile) ===== */
.backdrop {
  display: none;
}

/* ===== Responsive ===== */
@media (max-width: 900px) {
  .sidebar {
    position: fixed;
    z-index: 40;
    transform: translateX(-100%);
    transition: transform 0.22s;
  }
  .is-mobile-open .sidebar {
    transform: translateX(0);
  }
  .topbar__menu {
    display: none;
  }
  .topbar__menu--mobile {
    display: grid;
  }
  .is-mobile-open .backdrop {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(15, 23, 42, 0.45);
    z-index: 30;
  }
  .topbar__user-info {
    display: none;
  }
}
@media (max-width: 560px) {
  .topbar__search {
    display: none;
  }
}
</style>
