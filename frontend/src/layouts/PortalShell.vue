<script setup>
// Khung portal DÙNG CHUNG cho mọi vai trò (admin / nhân viên / giáo viên / trường):
// sidebar điều hướng + topbar (tìm kiếm, thông báo, user menu).
// Mỗi layout theo vai trò chỉ cần truyền `nav` (menu) riêng -> đảm bảo cùng phong cách.
// Cài đặt & Đăng xuất nằm trong dropdown avatar (kiểu GitHub) thay vì sidebar/topbar.
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import BrandLogo from '@/components/ui/BrandLogo.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { useLogout } from '@/composables/useLogout'
import { notificationApi } from '@/api/notifications'
import { roleHome } from '@/router/roleHome'

defineProps({
  // [{ title, items: [{ label, icon, to, badge }] }]
  nav: { type: Array, default: () => [] },
  // Route trang cài đặt của vai trò hiện tại (mỗi layout truyền path riêng).
  settingsTo: { type: String, default: '/settings' },
})

const router = useRouter()
const collapsed = ref(false) // thu gọn sidebar
const mobileOpen = ref(false) // mở sidebar trên màn nhỏ

// ===== Dropdown avatar =====
const menuOpen = ref(false)
const userMenuEl = ref(null)

// Đóng khi bấm ra ngoài / nhấn Escape. Listener gắn ở document nên phải
// tự gỡ khi component unmount để tránh leak.
function onDocClick(e) {
  if (menuOpen.value && userMenuEl.value && !userMenuEl.value.contains(e.target)) {
    menuOpen.value = false
  }
  if (notifOpen.value && notifWrap.value && !notifWrap.value.contains(e.target)) {
    notifOpen.value = false
  }
}
function onDocKeydown(e) {
  if (e.key === 'Escape') {
    menuOpen.value = false
    notifOpen.value = false
  }
}
onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onDocKeydown)
  if (auth.isLoggedIn) {
    loadNotifications()
    pollTimer = setInterval(refreshUnread, 60000) // cập nhật badge chưa đọc mỗi phút
  }
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onDocKeydown)
  if (pollTimer) clearInterval(pollTimer)
})

const auth = useAuthStore()
const ui = useUiStore() // nút mặt trời/mặt trăng trên topbar đảo theme sáng/tối
const onLogout = useLogout()
const roleLabels = {
  ADMIN: 'Quản trị viên',
  EMPLOYEE: 'Nhân viên',
  SCHOOL: 'Trường',
  TEACHER: 'Giáo viên',
  ACCOUNTANT: 'Kế toán',
  HR: 'Nhân sự',
  ACADEMIC: 'Đào tạo',
  SALES: 'Tuyển sinh',
}
const roleLabel = computed(() => roleLabels[auth.primaryRole] || 'Người dùng')

/* ══════════════════ TÌM KIẾM CHUNG ══════════════════ */
const searchQuery = ref('')
function onSearch() {
  const q = searchQuery.value.trim()
  router.push({ path: '/dashboard/teacher', query: q ? { q } : {} })
}

/* ══════════════════ THÔNG BÁO (chuông) ══════════════════ */
const notifOpen = ref(false)
const notifLoading = ref(false)
const notifications = ref([])
const unreadCount = ref(0)
const notifWrap = ref(null)
let pollTimer = null

// Điều hướng khi bấm thông báo — ưu tiên RefEntity, dự phòng theo Type.
const ENTITY_ROUTES = {
  Assignment: '/assignments',
  Schedule: '/schedule',
  Attendance: '/attendance',
  Payroll: '/payroll',
}
const TYPE_ROUTES = {
  ASSIGNMENT: '/assignments',
  SCHEDULE: '/schedule',
  ATTENDANCE: '/attendance',
  PAYROLL: '/payroll',
}
const TYPE_ICONS = {
  ASSIGNMENT: 'assignment',
  SCHEDULE: 'schedule',
  ATTENDANCE: 'attendance',
  PAYROLL: 'payroll',
  SYSTEM: 'settings',
}
const notifIcon = (n) => TYPE_ICONS[n.type] || 'bell'

async function loadNotifications() {
  notifLoading.value = true
  try {
    const { data } = await notificationApi.list()
    notifications.value = data.items || []
    unreadCount.value = data.unreadCount || 0
  } catch {
    // Không chặn UI nếu API lỗi/tạm thời.
  } finally {
    notifLoading.value = false
  }
}

async function refreshUnread() {
  try {
    const { data } = await notificationApi.unreadCount()
    unreadCount.value = data.unreadCount || 0
  } catch {
    /* im lặng */
  }
}

function toggleNotif() {
  notifOpen.value = !notifOpen.value
  if (notifOpen.value) loadNotifications()
}

async function openNotification(n) {
  if (!n.read) {
    try {
      await notificationApi.markRead(n.id)
      n.read = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch {
      /* im lặng */
    }
  }
  const to = ENTITY_ROUTES[n.refEntity] || TYPE_ROUTES[n.type]
  notifOpen.value = false
  if (to) router.push(to)
}

async function markAllRead() {
  try {
    const { data } = await notificationApi.markAllRead()
    notifications.value = data.items || []
    unreadCount.value = data.unreadCount || 0
  } catch {
    /* im lặng */
  }
}

function timeAgo(iso) {
  if (!iso) return ''
  const diff = Math.max(0, Date.now() - new Date(iso).getTime())
  const m = Math.floor(diff / 60000)
  if (m < 1) return 'Vừa xong'
  if (m < 60) return `${m} phút trước`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} giờ trước`
  const d = Math.floor(h / 24)
  return `${d} ngày trước`
}

/* ══════════════════ AVATAR & CHUYỂN TÀI KHOẢN (dropdown) ══════════════════ */
// Avatar = 2 chữ cái đầu của tên hiển thị (không dùng ảnh từ dịch vụ ngoài:
// tránh gửi request kèm IP người dùng cho bên thứ 3 + demo offline không vỡ ảnh).
function initialsOf(u) {
  const name = (u?.fullName || u?.username || '?').trim()
  const parts = name.split(/\s+/)
  const chars = parts.length >= 2 ? parts[0][0] + parts[parts.length - 1][0] : name.slice(0, 2)
  return chars.toUpperCase()
}
const initials = computed(() => initialsOf(auth.user))

function labelOf(u) {
  return roleLabels[u?.roles?.[0]] || 'Người dùng'
}

// Đổi sang tài khoản khác: điều hướng CỨNG (reload) về "nhà" của vai trò đó để mọi
// trang nạp lại dữ liệu theo tài khoản mới, không dính state cũ của tài khoản trước.
function switchTo(acc) {
  menuOpen.value = false
  const username = acc.user?.username
  if (!username || username === auth.user?.username) return
  const target = router.resolve(roleHome(acc.user?.roles ?? [])).fullPath
  auth.switchAccount(username)
  window.location.assign(target)
}
</script>

<template>
  <div class="admin" :class="{ 'is-collapsed': collapsed, 'is-mobile-open': mobileOpen }">
    <!-- Sidebar -->
    <aside class="sidebar">
      <div class="sidebar__brand">
        <BrandLogo :size="34" :show-text="false" />
        <span class="sidebar__name">KDC EduOps AI</span>
      </div>

      <nav class="sidebar__nav">
        <div v-for="group in nav" :key="group.title" class="navgroup">
          <p class="navgroup__title">{{ group.title }}</p>
          <!-- to: '#' = trang chưa làm -> không gán active-class, kẻo vue-router
               coi '#' là trang hiện tại và thắp sáng CẢ LOẠT mục menu -->
          <RouterLink
            v-for="item in group.items"
            :key="item.label"
            :to="item.to"
            class="navlink"
            :active-class="item.to === '#' ? '' : 'is-active'"
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

        <form class="topbar__search" @submit.prevent="onSearch">
          <SvgIcon name="search" :size="18" />
          <input v-model="searchQuery" type="text" placeholder="Tìm kiếm giáo viên…" />
        </form>

        <div class="topbar__actions">
          <!-- Chuyển nền sáng/tối -->
          <button
            class="iconbtn"
            :title="ui.isDark ? 'Chuyển nền sáng' : 'Chuyển nền tối'"
            :aria-label="ui.isDark ? 'Chuyển nền sáng' : 'Chuyển nền tối'"
            @click="ui.toggleTheme()"
          >
            <SvgIcon :name="ui.isDark ? 'sun' : 'moon'" :size="20" />
          </button>

          <!-- Chuông thông báo (dữ liệu thật từ API) -->
          <div ref="notifWrap" class="notif">
            <button
              class="iconbtn"
              :class="{ 'is-active': notifOpen }"
              title="Thông báo"
              @click="toggleNotif"
            >
              <SvgIcon name="bell" :size="20" />
              <span v-if="unreadCount > 0" class="notif__badge">{{
                unreadCount > 9 ? '9+' : unreadCount
              }}</span>
            </button>

            <transition name="notif-pop">
              <div v-if="notifOpen" class="notif__panel">
                <div class="notif__head">
                  <strong>Thông báo</strong>
                  <button
                    v-if="unreadCount > 0"
                    class="notif__markall"
                    @click="markAllRead"
                  >
                    <SvgIcon name="check-all" :size="14" /> Đánh dấu đã đọc
                  </button>
                </div>

                <div v-if="notifLoading" class="notif__state">Đang tải…</div>
                <ul v-else-if="notifications.length" class="notif__list">
                  <li
                    v-for="n in notifications"
                    :key="n.id"
                    class="notif__item"
                    :class="{ 'is-unread': !n.read }"
                    @click="openNotification(n)"
                  >
                    <span class="notif__icon"><SvgIcon :name="notifIcon(n)" :size="16" /></span>
                    <div class="notif__text">
                      <strong>{{ n.title }}</strong>
                      <small v-if="n.content">{{ n.content }}</small>
                      <span class="notif__time">{{ timeAgo(n.createdAt) }}</span>
                    </div>
                    <span v-if="!n.read" class="notif__unread-dot" />
                  </li>
                </ul>
                <div v-else class="notif__state notif__state--empty">
                  <SvgIcon name="bell" :size="24" />
                  <p>Chưa có thông báo nào.</p>
                </div>
              </div>
            </transition>
          </div>

          <!-- Menu tài khoản (avatar dropdown: cài đặt, chuyển tài khoản, đăng xuất) -->
          <div ref="userMenuEl" class="usermenu" :class="{ 'is-open': menuOpen }">
            <button
              class="topbar__user"
              aria-haspopup="menu"
              :aria-expanded="menuOpen"
              @click="menuOpen = !menuOpen"
            >
              <div class="topbar__avatar">{{ initials }}</div>
              <div class="topbar__user-info">
                <strong>{{ auth.user?.fullName || 'Người dùng' }}</strong>
                <small>{{ roleLabel }}</small>
              </div>
              <span class="topbar__chevron"><SvgIcon name="chevron" :size="16" /></span>
            </button>

            <Transition name="menu">
              <div v-if="menuOpen" class="usermenu__panel" role="menu">
                <div class="usermenu__head">
                  <strong>{{ auth.user?.fullName || 'Người dùng' }}</strong>
                  <small>@{{ auth.user?.username }} · {{ roleLabel }}</small>
                </div>
                <div class="usermenu__divider" />
                <RouterLink
                  :to="settingsTo"
                  class="usermenu__item"
                  role="menuitem"
                  @click="menuOpen = false"
                >
                  <SvgIcon name="settings" :size="17" />
                  Cài đặt tài khoản
                </RouterLink>

                <!-- Chuyển nhanh giữa các tài khoản đã đăng nhập (đỡ logout/login) -->
                <div class="usermenu__divider" />
                <p class="usermenu__label">Chuyển tài khoản</p>
                <button
                  v-for="acc in auth.accounts"
                  :key="acc.user?.username"
                  class="usermenu__acct"
                  role="menuitem"
                  @click="switchTo(acc)"
                >
                  <span class="usermenu__acct-avatar">{{ initialsOf(acc.user) }}</span>
                  <span class="usermenu__acct-info">
                    <strong>{{ acc.user?.fullName || acc.user?.username }}</strong>
                    <small>@{{ acc.user?.username }} · {{ labelOf(acc.user) }}</small>
                  </span>
                  <span
                    v-if="acc.user?.username === auth.user?.username"
                    class="usermenu__acct-check"
                  >
                    <SvgIcon name="check" :size="15" />
                  </span>
                </button>
                <RouterLink
                  :to="{ name: 'login', query: { add: '1' } }"
                  class="usermenu__item"
                  role="menuitem"
                  @click="menuOpen = false"
                >
                  <SvgIcon name="plus" :size="17" />
                  Thêm tài khoản
                </RouterLink>

                <div class="usermenu__divider" />
                <button class="usermenu__item is-danger" role="menuitem" @click="onLogout">
                  <SvgIcon name="logout" :size="17" />
                  Đăng xuất
                </button>
              </div>
            </Transition>
          </div>
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
  background: linear-gradient(180deg, var(--a-sidebar) 0%, var(--a-sidebar-2) 100%);
  color: #9fb2cf;
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
  gap: 0.6rem;
  padding: 1.15rem 1.2rem;
  font-weight: 800;
  font-size: 1.05rem;
  color: #fff;
  letter-spacing: 0.3px;
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
  color: #5f7693;
  white-space: nowrap;
}
.navlink {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.6rem 0.7rem;
  border-radius: 9px;
  color: #9fb2cf;
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
  transform: translateX(3px);
}
.navlink:hover .navlink__icon {
  transform: scale(1.12);
}
.navlink.is-active {
  background: var(--grad-primary);
  color: #fff;
  box-shadow: 0 6px 16px rgba(249, 115, 22, 0.4);
}
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
  background: var(--c-surface);
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
  border: 2px solid var(--c-surface);
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
.topbar__search:focus-within {
  border-color: var(--c-primary);
  background: var(--c-surface);
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.12);
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
.iconbtn.is-active {
  background: var(--a-bg);
  color: var(--c-primary);
}

/* ===== Chuông thông báo ===== */
.notif {
  position: relative;
}
.notif__badge {
  position: absolute;
  top: 3px;
  right: 3px;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  display: grid;
  place-items: center;
  font-size: 0.66rem;
  font-weight: 700;
  color: #fff;
  background: var(--c-primary);
  border: 2px solid var(--c-surface);
  border-radius: 20px;
  line-height: 1;
}
.notif__panel {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  width: 360px;
  max-width: calc(100vw - 2rem);
  background: var(--c-surface);
  border: 1px solid var(--a-border);
  border-radius: 14px;
  box-shadow: var(--a-shadow-lg);
  overflow: hidden;
  z-index: 50;
}
.notif__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.85rem 1rem;
  border-bottom: 1px solid var(--a-border);
}
.notif__head strong {
  font-size: 0.95rem;
  color: var(--a-text);
}
.notif__markall {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--c-primary);
  padding: 0.2rem 0.3rem;
  border-radius: 6px;
  transition: background var(--t-fast);
}
.notif__markall:hover {
  background: rgba(249, 115, 22, 0.1);
}
.notif__list {
  list-style: none;
  margin: 0;
  padding: 0.3rem;
  max-height: 380px;
  overflow-y: auto;
}
.notif__item {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  padding: 0.7rem 0.65rem;
  border-radius: 10px;
  cursor: pointer;
  position: relative;
  transition: background var(--t-fast);
}
.notif__item:hover {
  background: var(--a-bg);
}
.notif__item.is-unread {
  background: rgba(249, 115, 22, 0.07);
}
.notif__item.is-unread:hover {
  background: rgba(249, 115, 22, 0.16);
}
.notif__icon {
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  background: rgba(249, 115, 22, 0.12);
  color: var(--c-primary);
}
.notif__text {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
}
.notif__text strong {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--a-text);
}
.notif__text small {
  font-size: 0.78rem;
  color: var(--a-text-muted);
  line-height: 1.35;
}
.notif__time {
  font-size: 0.72rem;
  color: var(--a-text-muted);
  margin-top: 0.1rem;
}
.notif__unread-dot {
  position: absolute;
  top: 0.85rem;
  right: 0.65rem;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--c-primary);
}
.notif__state {
  padding: 1.4rem 1rem;
  text-align: center;
  color: var(--a-text-muted);
  font-size: 0.85rem;
}
.notif__state--empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.4rem;
  padding: 2rem 1rem;
  color: var(--a-text-muted);
}
.notif__state--empty p {
  margin: 0;
}
.notif-pop-enter-active,
.notif-pop-leave-active {
  transition:
    opacity var(--t-fast),
    transform var(--t-fast);
}
.notif-pop-enter-from,
.notif-pop-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.98);
}
.usermenu {
  position: relative;
}
.topbar__user {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.3rem 0.5rem 0.3rem 0.4rem;
  border: none;
  background: transparent;
  font: inherit;
  text-align: left;
  border-radius: 12px;
  cursor: pointer;
  color: var(--a-text-muted);
  transition: background var(--t-fast);
}
.topbar__user:hover,
.is-open .topbar__user {
  background: var(--a-bg);
}
.topbar__chevron {
  display: grid;
  place-items: center;
  transition: transform var(--t-fast);
}
.is-open .topbar__chevron {
  transform: rotate(180deg);
}
.usermenu__panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  min-width: 264px;
  padding: 0.4rem;
  background: var(--c-surface);
  border: 1px solid var(--a-border);
  border-radius: 14px;
  box-shadow: var(--a-shadow-lg);
  z-index: 30;
}
.usermenu__label {
  margin: 0.35rem 0 0.15rem;
  padding: 0 0.75rem;
  font-size: 0.66rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.7px;
  color: var(--a-text-muted);
}
.usermenu__acct {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 100%;
  padding: 0.45rem 0.75rem;
  border: none;
  background: transparent;
  font: inherit;
  text-align: left;
  border-radius: 9px;
  cursor: pointer;
  transition: background var(--t-fast);
}
.usermenu__acct:hover {
  background: var(--a-bg);
}
.usermenu__acct-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--grad-primary);
  color: #fff;
  font-size: 0.66rem;
  font-weight: 700;
  letter-spacing: 0.4px;
  flex: 0 0 auto;
}
.usermenu__acct-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}
.usermenu__acct-info strong {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--a-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.usermenu__acct-info small {
  font-size: 0.73rem;
  color: var(--a-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.usermenu__acct-check {
  display: grid;
  place-items: center;
  color: var(--c-primary);
  flex: 0 0 auto;
}
.usermenu__head {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  padding: 0.5rem 0.75rem;
  line-height: 1.3;
}
.usermenu__head strong {
  font-size: 0.9rem;
  color: var(--a-text);
}
.usermenu__head small {
  font-size: 0.75rem;
  color: var(--a-text-muted);
}
.usermenu__divider {
  height: 1px;
  margin: 0.3rem 0.4rem;
  background: var(--a-border);
}
.usermenu__item {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 100%;
  padding: 0.55rem 0.75rem;
  border: none;
  background: transparent;
  font: inherit;
  font-size: 0.88rem;
  font-weight: 500;
  color: var(--a-text);
  text-decoration: none;
  text-align: left;
  border-radius: 9px;
  cursor: pointer;
  transition:
    background var(--t-fast),
    color var(--t-fast);
}
.usermenu__item:hover {
  background: var(--a-bg);
  color: var(--c-primary);
}
.usermenu__item.is-danger {
  color: var(--c-danger);
}
.usermenu__item.is-danger:hover {
  background: rgba(239, 68, 68, 0.08);
  color: var(--c-danger);
}
.menu-enter-active,
.menu-leave-active {
  transition:
    opacity var(--t-fast),
    transform var(--t-fast);
}
.menu-enter-from,
.menu-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
.topbar__avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--grad-primary);
  color: #fff;
  font-size: 0.82rem;
  font-weight: 700;
  letter-spacing: 0.5px;
  flex: 0 0 auto;
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
