<!-- src/pages/SettingsPage.vue -->
<script setup>
/**
 * Trang CÀI ĐẶT CÁ NHÂN — dùng CHUNG cho mọi vai trò (admin/staff/teacher/school):
 * component này thuần nội dung, được bọc bởi layout của từng khu vực qua meta.layout
 * (route đăng ký riêng ở mỗi file *.routes.js theo quy ước chống conflict).
 *
 * Bố cục: HERO hồ sơ + LƯỚI 2 CỘT — cột trái là 4 tab nội dung, cột phải là "rail"
 * tiện ích luôn hiển thị (Quyền của tôi / Tài khoản trên máy này):
 *  - Hồ sơ     : 2 card tách bạch "của ai quản cái gì" — Thông tin cơ bản CHỈ ĐỌC
 *                (phòng Nhân sự quản) và Thông tin liên hệ theo pattern XEM-TRƯỚC-
 *                SỬA-SAU (bấm icon bút mới thành form; PUT /me/profile).
 *  - Mật khẩu  : đổi mật khẩu (POST /me/change-password) — backend thu hồi mọi phiên
 *                KHÁC, thiết bị này ở lại nhờ gửi kèm refreshToken. Có nút mắt 👁
 *                hiện/ẩn từng ô mật khẩu.
 *  - Thiết bị  : phiên đăng nhập đang sống, đăng xuất từng/mọi thiết bị khác.
 *  - Giao diện : theme Sáng/Tối/Theo hệ thống + cỡ chữ + giảm hiệu ứng — đọc/ghi
 *                stores/ui.js (áp data-* lên <html>, main.css lật token màu theo).
 *
 * Style bám design tokens ở assets/main.css: KHÔNG hard-code màu nền/chữ (điều kiện
 * để dark mode hoạt động) — mọi màu đi qua var(--c-surface/--c-text/...).
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import { settingsApi } from '@/api/settings'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { useLogout } from '@/composables/useLogout'
import { roleHome } from '@/router/roleHome'
import { formatDateTime } from '@/utils/format'
import { isStrongPassword, PASSWORD_HINT } from '@/utils/password'

const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()
const doLogout = useLogout()

const tab = ref('profile') // 'profile' | 'password' | 'sessions' | 'appearance'

/* ══════════ Hồ sơ ══════════ */
const profile = reactive({
  loading: true,
  data: null, // { username, email, fullName, phone, actorType, position, roles }
  editing: false, // xem-trước-sửa-sau: chỉ hiện form khi bấm icon bút
  form: { email: '', phone: '' },
  errors: {},
  saving: false,
  success: '',
  error: '',
})

// Nhãn tiếng Việt cho loại hồ sơ gắn với tài khoản
const ACTOR_LABELS = {
  TEACHER: 'Giáo viên',
  EMPLOYEE: 'Nhân viên trung tâm',
  SCHOOL: 'Trường khách hàng',
  NONE: 'Tài khoản hệ thống',
}

// Avatar chữ cái đầu (đồng bộ cách làm ở PortalShell — không dùng ảnh ngoài)
function initialsOf(name) {
  const n = (name || '?').trim()
  const parts = n.split(/\s+/)
  const chars = parts.length >= 2 ? parts[0][0] + parts[parts.length - 1][0] : n.slice(0, 2)
  return chars.toUpperCase()
}
const initials = computed(() => initialsOf(profile.data?.fullName || profile.data?.username))

async function loadProfile() {
  profile.loading = true
  try {
    const { data } = await settingsApi.getProfile()
    profile.data = data
    profile.form.email = data.email ?? ''
    profile.form.phone = data.phone ?? ''
  } catch (e) {
    profile.error = e.response?.data?.message ?? 'Không tải được hồ sơ'
  } finally {
    profile.loading = false
  }
}

// Mirror rule backend: email hợp lệ; SĐT rỗng hoặc SĐT Việt Nam (0/+84 + 9-10 chữ số)
// — cùng quy ước với module Giáo viên (TeacherListPage / TeacherResponse).
const PHONE_RE = /^$|^(\+84|0)\d{9,10}$/
const FIELD_RULES = {
  email: () => {
    const email = profile.form.email.trim()
    if (!email) return 'Email không được để trống'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return 'Email không hợp lệ'
    return ''
  },
  phone: () =>
    PHONE_RE.test(profile.form.phone.trim())
      ? ''
      : 'SĐT phải bắt đầu bằng 0 hoặc +84, gồm 10–11 chữ số',
}

// Validate 1 ô: gọi khi RỜI Ô (blur) để báo lỗi ngay không đợi bấm Lưu; khi đang gõ
// chỉ gọi lại nếu ô đó ĐANG lỗi (lỗi biến mất đúng lúc sửa xong, không cằn nhằn giữa chừng).
function checkField(field) {
  const msg = FIELD_RULES[field]()
  if (msg) profile.errors[field] = msg
  else delete profile.errors[field]
}

function validateProfile() {
  const errors = {}
  for (const field of Object.keys(FIELD_RULES)) {
    const msg = FIELD_RULES[field]()
    if (msg) errors[field] = msg
  }
  return errors
}

// Vào chế độ sửa: nạp lại form từ dữ liệu hiện tại để Hủy luôn quay về đúng bản gốc
function startEditProfile() {
  profile.form.email = profile.data?.email ?? ''
  profile.form.phone = profile.data?.phone ?? ''
  profile.errors = {}
  profile.success = ''
  profile.error = ''
  profile.editing = true
}

function cancelEditProfile() {
  profile.editing = false
  profile.errors = {}
  profile.error = ''
}

async function saveProfile() {
  profile.errors = validateProfile()
  if (Object.keys(profile.errors).length) return
  profile.saving = true
  profile.success = ''
  profile.error = ''
  try {
    const { data } = await settingsApi.updateProfile({
      email: profile.form.email.trim(),
      phone: profile.form.phone.trim(),
    })
    profile.data = data
    profile.editing = false // lưu xong quay về chế độ xem
    profile.success = 'Đã lưu thông tin liên hệ'
    // Đồng bộ lại user trong store (topbar/menu đọc email từ đây)
    const me = await authApi.me()
    auth.setSession({ user: me.data })
  } catch (e) {
    profile.error = e.response?.data?.message ?? 'Lưu thất bại'
  } finally {
    profile.saving = false
  }
}

/* ══════════ Sao chép nhanh (username/email) ══════════ */
const copied = ref('') // key của ô vừa chép — icon đổi thành dấu ✓ trong ~1.6s

async function copyText(key, text) {
  try {
    await navigator.clipboard.writeText(text)
    copied.value = key
    setTimeout(() => {
      if (copied.value === key) copied.value = ''
    }, 1600)
  } catch {
    /* clipboard bị chặn (http không phải localhost) -> bỏ qua, không vỡ trang */
  }
}

/* ══════════ Mật khẩu ══════════ */
const pwd = reactive({
  form: { current: '', next: '', confirm: '' },
  errors: {},
  saving: false,
  success: '',
  error: '',
})

// Hiện/ẩn từng ô mật khẩu (nút mắt). Tách 3 cờ vì người dùng thường chỉ cần soi
// lại 1 ô (vd: "mật khẩu mới" gõ đúng chưa) chứ không phải lộ cả 3 cùng lúc.
const showPw = reactive({ current: false, next: false, confirm: false })

// Checklist sống: tick xanh từng điều kiện khi người dùng gõ (mirror regex backend)
const pwChecks = computed(() => ({
  length: pwd.form.next.length >= 8 && pwd.form.next.length <= 72,
  cases: /[a-z]/.test(pwd.form.next) && /[A-Z]/.test(pwd.form.next),
  digit: /\d/.test(pwd.form.next),
}))

function validatePassword() {
  const errors = {}
  if (!pwd.form.current) errors.current = 'Nhập mật khẩu hiện tại'
  if (!isStrongPassword(pwd.form.next)) errors.next = PASSWORD_HINT
  else if (pwd.form.next === pwd.form.current)
    errors.next = 'Mật khẩu mới phải khác mật khẩu hiện tại'
  if (pwd.form.confirm !== pwd.form.next) errors.confirm = 'Nhập lại chưa khớp mật khẩu mới'
  return errors
}

// Báo "chưa khớp" ngay khi rời ô nhập lại (chỉ khi đã gõ gì đó — ô trống chưa cần cằn nhằn)
function checkConfirm() {
  if (pwd.form.confirm && pwd.form.confirm !== pwd.form.next)
    pwd.errors.confirm = 'Nhập lại chưa khớp mật khẩu mới'
  else delete pwd.errors.confirm
}

async function changePassword() {
  pwd.errors = validatePassword()
  if (Object.keys(pwd.errors).length) return
  pwd.saving = true
  pwd.success = ''
  pwd.error = ''
  try {
    const { data } = await settingsApi.changePassword({
      currentPassword: pwd.form.current,
      newPassword: pwd.form.next,
      // Gửi kèm phiên hiện tại để backend GIỮ thiết bị này, đăng xuất các thiết bị khác
      refreshToken: auth.refreshToken,
    })
    pwd.success = data.message ?? 'Đổi mật khẩu thành công'
    pwd.form = { current: '', next: '', confirm: '' }
  } catch (e) {
    pwd.error = e.response?.data?.message ?? 'Đổi mật khẩu thất bại'
  } finally {
    pwd.saving = false
  }
}

/* ══════════ Thiết bị đăng nhập ══════════ */
const sessions = reactive({
  loading: false,
  items: [], // [{ id, createdAt, expiresAt, current }]
  message: '',
  error: '',
})
const confirmRevoke = ref(null) // phiên chờ xác nhận đăng xuất

const otherCount = computed(() => sessions.items.filter((s) => !s.current).length)

// "Còn N ngày" cạnh hạn phiên — đỡ phải nhẩm từ ngày giờ tuyệt đối
function daysLeft(iso) {
  return Math.max(0, Math.ceil((new Date(iso) - Date.now()) / 86400000))
}

async function loadSessions() {
  sessions.loading = true
  sessions.error = ''
  try {
    const { data } = await settingsApi.listSessions(auth.refreshToken)
    sessions.items = data
  } catch (e) {
    sessions.error = e.response?.data?.message ?? 'Không tải được danh sách phiên'
  } finally {
    sessions.loading = false
  }
}

async function revokeConfirmed() {
  const target = confirmRevoke.value
  if (!target) return
  confirmRevoke.value = null
  try {
    await settingsApi.revokeSession(target.id)
    if (target.current) {
      // Tự thu hồi phiên của chính thiết bị này = đăng xuất tại chỗ
      await doLogout()
      return
    }
    sessions.message = 'Đã đăng xuất thiết bị'
    await loadSessions()
  } catch (e) {
    sessions.error = e.response?.data?.message ?? 'Thao tác thất bại'
  }
}

async function revokeOthers() {
  sessions.message = ''
  sessions.error = ''
  try {
    const { data } = await settingsApi.revokeOtherSessions(auth.refreshToken)
    sessions.message = data.message
    await loadSessions()
  } catch (e) {
    sessions.error = e.response?.data?.message ?? 'Thao tác thất bại'
  }
}

function openTab(name) {
  tab.value = name
  if (name === 'sessions' && !sessions.items.length) loadSessions()
}

/* ══════════ Giao diện (đọc/ghi stores/ui.js) ══════════ */
const THEME_OPTIONS = [
  { value: 'light', label: 'Sáng', icon: 'sun', desc: 'Nền trắng quen thuộc' },
  { value: 'dark', label: 'Tối', icon: 'moon', desc: 'Dịu mắt khi làm việc đêm' },
  { value: 'system', label: 'Theo hệ thống', icon: 'monitor', desc: 'Tự đổi theo Windows/macOS' },
]

/* ══════════ Rail tiện ích: quyền + tài khoản trên máy này ══════════ */
const perms = computed(() => auth.user?.perms ?? [])
const permsOpen = ref(false) // gập/mở danh sách mã quyền chi tiết

// Đổi tài khoản: điều hướng CỨNG về "nhà" của vai trò đó (đồng bộ cách làm ở
// PortalShell.switchTo) — reload để mọi trang nạp lại dữ liệu theo tài khoản mới.
function switchTo(acc) {
  const username = acc.user?.username
  if (!username || username === auth.user?.username) return
  const target = router.resolve(roleHome(acc.user?.roles ?? [])).fullPath
  auth.switchAccount(username)
  window.location.assign(target)
}

onMounted(loadProfile)
</script>

<template>
  <div class="st-page fade-up">
    <!-- ═════ HERO hồ sơ: avatar + định danh + vai trò ═════ -->
    <section class="hero">
      <div class="hero__band"></div>
      <div class="hero__body">
        <div class="hero__avatar">{{ initials }}</div>

        <div v-if="profile.loading" class="hero__identity">
          <div class="skeleton skeleton--title"></div>
          <div class="skeleton skeleton--line"></div>
        </div>
        <div v-else-if="profile.data" class="hero__identity">
          <h2 class="hero__name">{{ profile.data.fullName }}</h2>
          <p class="hero__meta">
            @{{ profile.data.username }}
            <span class="dot">·</span>
            {{ ACTOR_LABELS[profile.data.actorType] ?? profile.data.actorType }}
            <template v-if="profile.data.position">
              <span class="dot">·</span> {{ profile.data.position }}
            </template>
          </p>
          <div class="hero__badges">
            <span v-for="r in profile.data.roles" :key="r" class="chip chip--role">{{ r }}</span>
            <span v-if="!profile.data.roles.length" class="chip chip--warn">Chưa có vai trò</span>
          </div>
        </div>
        <p v-else class="msg msg-err">{{ profile.error }}</p>
      </div>
    </section>

    <!-- ═════ Tabs ═════ -->
    <nav class="st-tabs">
      <button :class="{ active: tab === 'profile' }" @click="openTab('profile')">Hồ sơ</button>
      <button :class="{ active: tab === 'password' }" @click="openTab('password')">
        Mật khẩu & bảo mật
      </button>
      <button :class="{ active: tab === 'sessions' }" @click="openTab('sessions')">
        Thiết bị đăng nhập
      </button>
      <button :class="{ active: tab === 'appearance' }" @click="openTab('appearance')">
        Giao diện
      </button>
    </nav>

    <!-- ═════ Lưới 2 cột: nội dung tab (trái) + rail tiện ích (phải) ═════ -->
    <div class="st-cols">
      <div class="st-main">
        <!-- ═════ Tab: Hồ sơ (2 card — ranh giới "ai quản cái gì" tự rõ) ═════ -->
        <template v-if="tab === 'profile'">
          <!-- Card 1: Thông tin cơ bản — CHỈ ĐỌC, phòng Nhân sự quản -->
          <section class="card">
            <header class="card__head card__head--row">
              <div>
                <h3>Thông tin cơ bản</h3>
                <p v-if="profile.data && profile.data.actorType !== 'NONE'">
                  Thông tin định danh gắn với hợp đồng &amp; bảng lương.
                </p>
              </div>
              <span
                v-if="profile.data && profile.data.actorType !== 'NONE'"
                class="chip chip--muted"
                title="Cần thay đổi? Liên hệ phòng Nhân sự"
              >
                Phòng Nhân sự quản lý
              </span>
            </header>

            <div v-if="profile.loading" class="text-muted">Đang tải...</div>
            <div v-else-if="profile.data" class="info-grid">
              <div class="info-item">
                <small>Họ và tên</small>
                <span>{{ profile.data.fullName }}</span>
              </div>
              <div class="info-item">
                <small>Tên đăng nhập</small>
                <span class="copyable">
                  @{{ profile.data.username }}
                  <button
                    class="minibtn"
                    :title="copied === 'username' ? 'Đã chép' : 'Sao chép tên đăng nhập'"
                    @click="copyText('username', profile.data.username)"
                  >
                    <SvgIcon :name="copied === 'username' ? 'check' : 'copy'" :size="13" />
                  </button>
                </span>
              </div>
              <div class="info-item">
                <small>Loại tài khoản</small>
                <span>{{ ACTOR_LABELS[profile.data.actorType] ?? profile.data.actorType }}</span>
              </div>
              <div v-if="profile.data.position" class="info-item">
                <small>Chức vụ</small>
                <span>{{ profile.data.position }}</span>
              </div>
            </div>
          </section>

          <!-- Card 2: Thông tin liên hệ — XEM trước, bấm icon bút mới SỬA -->
          <section class="card card--gap">
            <header class="card__head card__head--row">
              <div>
                <h3>Thông tin liên hệ</h3>
                <p>Email dùng để nhận liên kết đặt lại mật khẩu.</p>
              </div>
              <button
                v-if="profile.data && !profile.editing"
                class="editbtn"
                title="Sửa thông tin liên hệ"
                aria-label="Sửa thông tin liên hệ"
                @click="startEditProfile"
              >
                <SvgIcon name="pencil" :size="16" />
              </button>
            </header>

            <div v-if="profile.loading" class="text-muted">Đang tải...</div>
            <template v-else-if="profile.data">
              <!-- Chế độ XEM: chữ tĩnh, không sợ sửa nhầm -->
              <div v-if="!profile.editing" class="info-grid">
                <div class="info-item">
                  <small>Email</small>
                  <span class="copyable">
                    {{ profile.data.email }}
                    <button
                      class="minibtn"
                      :title="copied === 'email' ? 'Đã chép' : 'Sao chép email'"
                      @click="copyText('email', profile.data.email)"
                    >
                      <SvgIcon :name="copied === 'email' ? 'check' : 'copy'" :size="13" />
                    </button>
                  </span>
                </div>
                <div v-if="profile.data.actorType !== 'NONE'" class="info-item">
                  <small>Số điện thoại</small>
                  <span v-if="profile.data.phone">{{ profile.data.phone }}</span>
                  <span v-else class="info-empty">Chưa cập nhật</span>
                </div>
              </div>

              <!-- Chế độ SỬA: form + Lưu/Hủy -->
              <template v-else>
                <div class="form-grid">
                  <div class="form-group">
                    <label>Email <span class="req">*</span></label>
                    <input
                      v-model="profile.form.email"
                      type="email"
                      placeholder="ban@vidu.vn"
                      :class="{ 'input-error': profile.errors.email }"
                      @blur="checkField('email')"
                      @input="profile.errors.email && checkField('email')"
                    />
                    <small v-if="profile.errors.email" class="field-error">{{
                      profile.errors.email
                    }}</small>
                  </div>
                  <div v-if="profile.data.actorType !== 'NONE'" class="form-group">
                    <label>Số điện thoại</label>
                    <input
                      v-model="profile.form.phone"
                      placeholder="VD: 0901234567"
                      :class="{ 'input-error': profile.errors.phone }"
                      @blur="checkField('phone')"
                      @input="profile.errors.phone && checkField('phone')"
                    />
                    <small v-if="profile.errors.phone" class="field-error">{{
                      profile.errors.phone
                    }}</small>
                  </div>
                </div>
              </template>

              <p v-if="profile.success" class="msg msg-ok">{{ profile.success }}</p>
              <p v-if="profile.error" class="msg msg-err">{{ profile.error }}</p>

              <footer v-if="profile.editing" class="card__foot card__foot--row">
                <button class="btn btn--primary" :disabled="profile.saving" @click="saveProfile">
                  {{ profile.saving ? 'Đang lưu...' : 'Lưu thay đổi' }}
                </button>
                <button
                  class="btn btn--outline"
                  :disabled="profile.saving"
                  @click="cancelEditProfile"
                >
                  Hủy
                </button>
              </footer>
            </template>
          </section>
        </template>

        <!-- ═════ Tab: Mật khẩu & bảo mật ═════ -->
        <section v-if="tab === 'password'" class="card card--narrow">
          <header class="card__head">
            <h3>Đổi mật khẩu</h3>
            <p>Sau khi đổi, mọi thiết bị khác sẽ bị đăng xuất — thiết bị này vẫn giữ phiên.</p>
          </header>

          <div class="form-group">
            <label>Mật khẩu hiện tại <span class="req">*</span></label>
            <div class="pw-wrap">
              <input
                v-model="pwd.form.current"
                :type="showPw.current ? 'text' : 'password'"
                autocomplete="current-password"
                :class="{ 'input-error': pwd.errors.current }"
                @input="delete pwd.errors.current"
              />
              <button
                class="pw-eye"
                type="button"
                :title="showPw.current ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'"
                @click="showPw.current = !showPw.current"
              >
                <SvgIcon :name="showPw.current ? 'eye-off' : 'eye'" :size="17" />
              </button>
            </div>
            <small v-if="pwd.errors.current" class="field-error">{{ pwd.errors.current }}</small>
          </div>
          <div class="form-group">
            <label>Mật khẩu mới <span class="req">*</span></label>
            <div class="pw-wrap">
              <input
                v-model="pwd.form.next"
                :type="showPw.next ? 'text' : 'password'"
                autocomplete="new-password"
                :class="{ 'input-error': pwd.errors.next }"
                @input="delete pwd.errors.next"
              />
              <button
                class="pw-eye"
                type="button"
                :title="showPw.next ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'"
                @click="showPw.next = !showPw.next"
              >
                <SvgIcon :name="showPw.next ? 'eye-off' : 'eye'" :size="17" />
              </button>
            </div>
            <small v-if="pwd.errors.next" class="field-error">{{ pwd.errors.next }}</small>
            <!-- Checklist sống: tick từng điều kiện khi gõ -->
            <ul class="pw-checks">
              <li :class="{ ok: pwChecks.length }">8–72 ký tự</li>
              <li :class="{ ok: pwChecks.cases }">Có chữ hoa và chữ thường</li>
              <li :class="{ ok: pwChecks.digit }">Có chữ số</li>
            </ul>
          </div>
          <div class="form-group">
            <label>Nhập lại mật khẩu mới <span class="req">*</span></label>
            <div class="pw-wrap">
              <input
                v-model="pwd.form.confirm"
                :type="showPw.confirm ? 'text' : 'password'"
                autocomplete="new-password"
                :class="{ 'input-error': pwd.errors.confirm }"
                @blur="checkConfirm"
                @input="pwd.errors.confirm && checkConfirm()"
              />
              <button
                class="pw-eye"
                type="button"
                :title="showPw.confirm ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'"
                @click="showPw.confirm = !showPw.confirm"
              >
                <SvgIcon :name="showPw.confirm ? 'eye-off' : 'eye'" :size="17" />
              </button>
            </div>
            <small v-if="pwd.errors.confirm" class="field-error">{{ pwd.errors.confirm }}</small>
          </div>

          <p v-if="pwd.success" class="msg msg-ok">{{ pwd.success }}</p>
          <p v-if="pwd.error" class="msg msg-err">{{ pwd.error }}</p>

          <footer class="card__foot">
            <button class="btn btn--primary" :disabled="pwd.saving" @click="changePassword">
              {{ pwd.saving ? 'Đang đổi...' : 'Đổi mật khẩu' }}
            </button>
          </footer>
        </section>

        <!-- ═════ Tab: Thiết bị đăng nhập ═════ -->
        <section v-if="tab === 'sessions'" class="card">
          <header class="card__head card__head--row">
            <div>
              <h3>Thiết bị đăng nhập</h3>
              <p>
                Mỗi dòng là một lần đăng nhập còn hiệu lực. Thấy phiên lạ? Đăng xuất nó và đổi mật
                khẩu ngay.
              </p>
            </div>
            <button class="btn btn--outline" :disabled="!otherCount" @click="revokeOthers">
              Đăng xuất {{ otherCount }} thiết bị khác
            </button>
          </header>

          <p v-if="sessions.loading" class="text-muted">Đang tải...</p>
          <ul v-else class="sess-list">
            <li v-if="!sessions.items.length" class="text-muted sess-empty">Không có phiên nào</li>
            <li v-for="s in sessions.items" :key="s.id" class="sess-item">
              <span class="sess-item__icon" :class="{ current: s.current }">
                <SvgIcon name="clock" :size="18" />
              </span>
              <div class="sess-item__info">
                <strong>Đăng nhập lúc {{ formatDateTime(s.createdAt) }}</strong>
                <small>
                  Hết hạn {{ formatDateTime(s.expiresAt) }} · còn {{ daysLeft(s.expiresAt) }} ngày
                </small>
              </div>
              <span v-if="s.current" class="chip chip--ok">Thiết bị này</span>
              <button class="btn btn--danger-outline btn--sm" @click="confirmRevoke = s">
                Đăng xuất
              </button>
            </li>
          </ul>

          <p v-if="sessions.message" class="msg msg-ok">{{ sessions.message }}</p>
          <p v-if="sessions.error" class="msg msg-err">{{ sessions.error }}</p>
        </section>

        <!-- ═════ Tab: Giao diện ═════ -->
        <template v-if="tab === 'appearance'">
          <section class="card">
            <header class="card__head">
              <h3>Chủ đề màu</h3>
              <p>Lưu tự động trên thiết bị này — mỗi máy/trình duyệt nhớ lựa chọn riêng.</p>
            </header>

            <div class="theme-grid">
              <button
                v-for="opt in THEME_OPTIONS"
                :key="opt.value"
                class="theme-opt"
                :class="{ active: ui.theme === opt.value }"
                @click="ui.setTheme(opt.value)"
              >
                <!-- Mini preview: màu CỐ TÌNH hard-code vì nó ĐẠI DIỆN cho theme,
                     không được đổi theo theme đang bật -->
                <span class="theme-opt__preview" :class="`tp--${opt.value}`">
                  <span class="tp-side"></span>
                  <span class="tp-body">
                    <span class="tp-line"></span>
                    <span class="tp-line tp-line--short"></span>
                  </span>
                </span>
                <span class="theme-opt__label">
                  <SvgIcon :name="opt.icon" :size="15" /> {{ opt.label }}
                </span>
                <small class="theme-opt__desc">{{ opt.desc }}</small>
              </button>
            </div>
          </section>

          <section class="card card--gap">
            <header class="card__head">
              <h3>Hiển thị &amp; trợ năng</h3>
            </header>

            <div class="setting-row">
              <div class="setting-row__text">
                <strong>Cỡ chữ</strong>
                <small>Phóng to toàn bộ chữ trong ứng dụng.</small>
              </div>
              <div class="seg">
                <button
                  :class="{ active: ui.fontSize === 'normal' }"
                  @click="ui.setFontSize('normal')"
                >
                  Mặc định
                </button>
                <button
                  :class="{ active: ui.fontSize === 'large' }"
                  @click="ui.setFontSize('large')"
                >
                  Lớn
                </button>
              </div>
            </div>

            <div class="setting-row">
              <div class="setting-row__text">
                <strong>Hiệu ứng chuyển động</strong>
                <small>Tắt nếu hiệu ứng trượt/mờ làm bạn khó chịu hoặc chóng mặt.</small>
              </div>
              <div class="seg">
                <button :class="{ active: ui.motion }" @click="ui.setMotion(true)">Bật</button>
                <button :class="{ active: !ui.motion }" @click="ui.setMotion(false)">Giảm</button>
              </div>
            </div>
          </section>
        </template>
      </div>

      <!-- ═════ Rail tiện ích (luôn hiển thị, mọi tab) ═════ -->
      <aside class="st-rail">
        <!-- Quyền của tôi: đọc từ JWT trong store — không tốn API -->
        <section class="card rail-card">
          <header class="rail-head">
            <span class="rail-ico rail-ico--blue"><SvgIcon name="shield" :size="18" /></span>
            <div>
              <h4>Quyền của tôi</h4>
              <small>{{ auth.roles.length }} vai trò · {{ perms.length }} quyền chi tiết</small>
            </div>
          </header>
          <div class="rail-chips">
            <span v-for="r in auth.roles" :key="r" class="chip chip--role">{{ r }}</span>
          </div>
          <button v-if="perms.length" class="rail-toggle" @click="permsOpen = !permsOpen">
            {{ permsOpen ? 'Thu gọn' : `Xem ${perms.length} mã quyền` }}
            <span class="rail-toggle__chev" :class="{ open: permsOpen }">
              <SvgIcon name="chevron" :size="14" />
            </span>
          </button>
          <ul v-if="permsOpen" class="perm-list">
            <li v-for="p in perms" :key="p">{{ p }}</li>
          </ul>
        </section>

        <!-- Tài khoản đã đăng nhập trên máy này (multi-account của store auth) -->
        <section class="card rail-card">
          <header class="rail-head">
            <span class="rail-ico rail-ico--orange"><SvgIcon name="teacher" :size="18" /></span>
            <div>
              <h4>Tài khoản trên máy này</h4>
              <small>Bấm để chuyển nhanh, không cần đăng nhập lại</small>
            </div>
          </header>
          <button
            v-for="acc in auth.accounts"
            :key="acc.user?.username"
            class="acct"
            :class="{ current: acc.user?.username === auth.user?.username }"
            @click="switchTo(acc)"
          >
            <span class="acct__avatar">{{
              initialsOf(acc.user?.fullName || acc.user?.username)
            }}</span>
            <span class="acct__info">
              <strong>{{ acc.user?.fullName || acc.user?.username }}</strong>
              <small>@{{ acc.user?.username }}</small>
            </span>
            <span v-if="acc.user?.username === auth.user?.username" class="acct__check">
              <SvgIcon name="check" :size="15" />
            </span>
          </button>
          <RouterLink :to="{ name: 'login', query: { add: '1' } }" class="rail-link">
            <SvgIcon name="plus" :size="15" /> Thêm tài khoản
          </RouterLink>
        </section>
      </aside>
    </div>

    <!-- Confirm đăng xuất thiết bị -->
    <div v-if="confirmRevoke" class="modal-overlay" @click.self="confirmRevoke = null">
      <div class="modal-box">
        <h3>Xác nhận đăng xuất</h3>
        <p v-if="confirmRevoke.current">
          Đây là <strong>thiết bị bạn đang dùng</strong> — bạn sẽ bị đưa về trang đăng nhập.
        </p>
        <p v-else>Đăng xuất phiên đăng nhập lúc {{ formatDateTime(confirmRevoke.createdAt) }}?</p>
        <div class="modal-actions">
          <button class="btn btn--outline" @click="confirmRevoke = null">Hủy</button>
          <button class="btn btn--danger" @click="revokeConfirmed">Đăng xuất</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.st-page {
  padding: 1.5rem;
  max-width: 1180px;
}

/* Lưới 2 cột: nội dung (trái) + rail tiện ích (phải). minmax(0,1fr) để cột trái
   được phép co lại thay vì đẩy rail tràn màn hình khi nội dung rộng. */
.st-cols {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 1.25rem;
  align-items: start;
}
.st-rail {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
  position: sticky;
  top: 82px; /* dưới topbar (66px) một chút */
}

/* ══════════ HERO hồ sơ ══════════ */
.hero {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 14px;
  box-shadow: var(--a-shadow);
  overflow: hidden;
  margin-bottom: 1.25rem;
}
/* Dải nhận diện thương hiệu (xanh→cam) phía trên avatar */
.hero__band {
  height: 64px;
  background: var(--grad-hero);
}
.hero__body {
  display: flex;
  align-items: flex-start;
  gap: 1.1rem;
  padding: 0 1.5rem 1.25rem;
}
.hero__avatar {
  width: 76px;
  height: 76px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--grad-primary);
  color: #fff;
  font-size: 1.5rem;
  font-weight: 800;
  letter-spacing: 1px;
  border: 4px solid var(--c-surface);
  box-shadow: 0 6px 16px rgba(249, 115, 22, 0.35);
  margin-top: -32px; /* avatar "gối" lên dải màu */
  flex: 0 0 auto;
}
.hero__identity {
  padding-top: 0.65rem;
  min-width: 0;
}
.hero__name {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--c-text);
}
.hero__meta {
  margin: 2px 0 0;
  font-size: 0.85rem;
  color: var(--c-text-muted);
}
.dot {
  margin: 0 0.35rem;
  color: var(--c-border);
}
.hero__badges {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin-top: 0.5rem;
}

/* Skeleton khi đang tải hero */
.skeleton {
  background: linear-gradient(
    90deg,
    var(--c-surface-2) 25%,
    var(--c-border-soft) 50%,
    var(--c-surface-2) 75%
  );
  background-size: 200% 100%;
  animation: shimmer 1.2s infinite;
  border-radius: 6px;
}
.skeleton--title {
  width: 180px;
  height: 20px;
  margin-top: 0.8rem;
}
.skeleton--line {
  width: 260px;
  height: 13px;
  margin-top: 8px;
}
@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}

/* ══════════ Chip / badge ══════════ */
.chip {
  display: inline-block;
  padding: 0.16rem 0.6rem;
  border-radius: 9999px;
  font-size: 0.74rem;
  font-weight: 600;
}
.chip--role {
  background: rgba(37, 99, 235, 0.09);
  color: var(--c-accent-dark);
  border: 1px solid rgba(37, 99, 235, 0.22);
}
.chip--ok {
  background: rgba(34, 197, 94, 0.14);
  color: #15803d;
}
.chip--warn {
  background: rgba(245, 158, 11, 0.16);
  color: #92400e;
}
.chip--muted {
  background: var(--c-surface-2);
  color: var(--c-text-muted);
  border: 1px solid var(--c-border);
  flex: 0 0 auto;
  cursor: help;
}
/* Pastel chips chỉnh lại chữ cho đủ tương phản trên nền tối */
:root[data-theme='dark'] .chip--role {
  color: #93c5fd;
}
:root[data-theme='dark'] .chip--ok {
  color: #4ade80;
}
:root[data-theme='dark'] .chip--warn {
  color: #fbbf24;
}

/* ══════════ Tabs (gạch chân cam) ══════════ */
.st-tabs {
  display: flex;
  gap: 0.25rem;
  border-bottom: 1px solid var(--c-border);
  margin-bottom: 1.1rem;
  /* overflow-x:auto (cuộn ngang khi màn hẹp) kéo theo overflow-y tự thành auto
     (spec CSS không cho 1 trục visible khi trục kia cuộn). Trước đây nút tab có
     margin-bottom:-1px làm nội dung tràn dọc đúng 1px → Windows vẽ scrollbar dọc
     tí hon = 2 mũi tên ▲▼ vô nghĩa ở cuối thanh tab. Khóa trục dọc để khỏi tái phát. */
  overflow-x: auto;
  overflow-y: hidden;
}
.st-tabs button {
  border: none;
  background: transparent;
  padding: 0.55rem 0.9rem;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--c-text-muted);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  white-space: nowrap;
  transition:
    color var(--t-fast),
    border-color var(--t-fast);
}
.st-tabs button:hover {
  color: var(--c-primary);
}
.st-tabs button.active {
  color: var(--c-primary);
  border-bottom-color: var(--c-primary);
}

/* ══════════ Card ══════════ */
.card {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 14px;
  box-shadow: var(--a-shadow);
  padding: 1.35rem 1.5rem;
}
.card--narrow {
  max-width: 480px;
}
.card--gap {
  margin-top: 1.1rem;
}
.card__head {
  margin-bottom: 1rem;
}
.card__head--row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}
.card__head h3 {
  margin: 0;
  font-size: 1.02rem;
  font-weight: 700;
  color: var(--c-text);
}
.card__head p {
  margin: 4px 0 0;
  font-size: 0.83rem;
  color: var(--c-text-muted);
  max-width: 560px;
}
.card__foot {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--c-border-soft);
}
.card__foot--row {
  display: flex;
  gap: 0.5rem;
}

/* Nút icon bút — mở chế độ sửa của card liên hệ */
.editbtn {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  border: 1px solid var(--c-input-border);
  border-radius: 9px;
  background: var(--c-surface);
  color: var(--c-text-muted);
  cursor: pointer;
  transition:
    border-color var(--t-fast),
    color var(--t-fast),
    box-shadow var(--t-fast);
}
.editbtn:hover {
  border-color: var(--c-primary);
  color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.12);
}

/* Lưới thông tin CHỈ ĐỌC (chế độ xem) */
.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 0.9rem 1.25rem;
}
.info-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.info-item small {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  color: var(--c-text-muted);
}
.info-item span {
  font-size: 0.92rem;
  font-weight: 500;
  color: var(--c-text);
  overflow-wrap: anywhere;
}
.info-item .info-empty,
.info-empty {
  color: var(--c-text-muted);
  font-style: italic;
  font-weight: 400;
}

/* Nút sao chép tí hon cạnh giá trị — chỉ hiện rõ khi rê chuột vào */
.copyable {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}
.minibtn {
  display: inline-grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--c-text-muted);
  opacity: 0.55;
  cursor: pointer;
  transition:
    opacity var(--t-fast),
    color var(--t-fast),
    background var(--t-fast);
}
.copyable:hover .minibtn,
.minibtn:focus-visible {
  opacity: 1;
}
.minibtn:hover {
  background: var(--c-surface-2);
  color: var(--c-primary);
}

/* ══════════ Form ══════════ */
.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 0 1.25rem;
}
.form-group {
  margin-bottom: 0.9rem;
}
.form-group label {
  display: block;
  font-size: 0.85rem;
  font-weight: 600;
  margin-bottom: 0.3rem;
  color: var(--c-text);
}
.req {
  color: var(--c-danger);
}
.form-group input {
  width: 100%;
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  font-size: 0.9rem;
  color: var(--c-text);
  background: var(--c-surface);
  transition:
    border-color var(--t-fast),
    box-shadow var(--t-fast);
}
/* focus cam nhẹ — đồng bộ ô tìm kiếm topbar */
.form-group input:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.14);
}
.input-error {
  border-color: var(--c-danger) !important;
}
.field-error {
  color: var(--c-danger);
  font-size: 0.78rem;
  display: block;
  margin-top: 3px;
}

/* Ô mật khẩu có nút mắt: input chừa chỗ phải, nút đè lên trong cùng khung */
.pw-wrap {
  position: relative;
}
.pw-wrap input {
  padding-right: 2.4rem;
}
.pw-eye {
  position: absolute;
  top: 50%;
  right: 6px;
  transform: translateY(-50%);
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--c-text-muted);
  cursor: pointer;
  transition:
    color var(--t-fast),
    background var(--t-fast);
}
.pw-eye:hover {
  color: var(--c-primary);
  background: var(--c-surface-2);
}

/* Checklist mật khẩu */
.pw-checks {
  list-style: none;
  margin: 0.5rem 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.pw-checks li {
  font-size: 0.78rem;
  color: var(--c-text-muted);
  padding-left: 1.25rem;
  position: relative;
  transition: color var(--t-fast);
}
.pw-checks li::before {
  content: '○';
  position: absolute;
  left: 2px;
}
.pw-checks li.ok {
  color: var(--c-success);
}
.pw-checks li.ok::before {
  content: '✓';
  font-weight: 700;
}

/* ══════════ Danh sách thiết bị ══════════ */
.sess-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.sess-empty {
  padding: 0.75rem 0;
}
.sess-item {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  padding: 0.7rem 0.25rem;
  border-bottom: 1px solid var(--c-border-soft);
}
.sess-item:last-child {
  border-bottom: none;
}
.sess-item__icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: var(--c-surface-2);
  color: var(--c-text-muted);
  flex: 0 0 auto;
}
.sess-item__icon.current {
  background: rgba(249, 115, 22, 0.12);
  color: var(--c-primary);
}
.sess-item__info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.sess-item__info strong {
  font-size: 0.88rem;
  color: var(--c-text);
  font-weight: 600;
}
.sess-item__info small {
  font-size: 0.76rem;
  color: var(--c-text-muted);
}

/* ══════════ Tab Giao diện ══════════ */
.theme-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 0.9rem;
}
.theme-opt {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.45rem;
  padding: 0.8rem;
  border: 2px solid var(--c-border);
  border-radius: 12px;
  background: var(--c-surface);
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition:
    border-color var(--t-fast),
    box-shadow var(--t-fast),
    transform var(--t-fast);
}
.theme-opt:hover {
  transform: translateY(-2px);
  box-shadow: var(--a-shadow);
}
.theme-opt.active {
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.15);
}
.theme-opt__label {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--c-text);
}
.theme-opt__desc {
  font-size: 0.75rem;
  color: var(--c-text-muted);
}

/* Mini preview: hình thu nhỏ "sidebar + nội dung". Màu hard-code CÓ CHỦ ĐÍCH —
   ô Sáng phải luôn trông sáng, ô Tối luôn tối, bất kể theme đang bật. */
.theme-opt__preview {
  display: flex;
  width: 100%;
  height: 58px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--c-border);
}
.tp-side {
  width: 26%;
  background: #0b2a4a;
}
.tp-body {
  flex: 1;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.tp-line {
  height: 8px;
  border-radius: 4px;
  background: #d8e0ec;
}
.tp-line--short {
  width: 55%;
  background: var(--c-primary);
  opacity: 0.85;
}
.tp--light .tp-body {
  background: #f5f8fc;
}
.tp--dark .tp-side {
  background: #050d1a;
}
.tp--dark .tp-body {
  background: #0b1523;
}
.tp--dark .tp-line {
  background: #263752;
}
.tp--dark .tp-line--short {
  background: var(--c-primary);
}
/* "Theo hệ thống": nửa sáng nửa tối chéo góc */
.tp--system .tp-body {
  background: linear-gradient(115deg, #f5f8fc 50%, #0b1523 50%);
}
.tp--system .tp-line {
  background: linear-gradient(115deg, #d8e0ec 55%, #263752 55%);
}

/* Hàng cài đặt: chữ trái, nút chọn phải */
.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.75rem 0;
  border-bottom: 1px solid var(--c-border-soft);
}
.setting-row:last-child {
  border-bottom: none;
}
.setting-row__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.setting-row__text strong {
  font-size: 0.9rem;
  color: var(--c-text);
}
.setting-row__text small {
  font-size: 0.78rem;
  color: var(--c-text-muted);
}
/* Segmented control 2 lựa chọn */
.seg {
  display: inline-flex;
  padding: 3px;
  border: 1px solid var(--c-border);
  border-radius: 10px;
  background: var(--c-surface-2);
  flex: 0 0 auto;
}
.seg button {
  border: none;
  background: transparent;
  padding: 0.35rem 0.85rem;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--c-text-muted);
  border-radius: 8px;
  cursor: pointer;
  transition:
    background var(--t-fast),
    color var(--t-fast),
    box-shadow var(--t-fast);
}
.seg button.active {
  background: var(--c-surface);
  color: var(--c-primary);
  box-shadow: 0 1px 4px rgba(15, 40, 80, 0.15);
}

/* ══════════ Rail tiện ích ══════════ */
.rail-card {
  padding: 1.1rem 1.2rem;
}
.rail-head {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  margin-bottom: 0.75rem;
}
.rail-head h4 {
  margin: 0;
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--c-text);
}
.rail-head small {
  font-size: 0.75rem;
  color: var(--c-text-muted);
}
.rail-ico {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  flex: 0 0 auto;
}
.rail-ico--blue {
  background: rgba(37, 99, 235, 0.12);
  color: var(--c-accent);
}
.rail-ico--orange {
  background: rgba(249, 115, 22, 0.12);
  color: var(--c-primary);
}
.rail-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}
.rail-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-top: 0.7rem;
  border: none;
  background: transparent;
  padding: 0;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--c-accent);
  cursor: pointer;
}
.rail-toggle__chev {
  display: grid;
  place-items: center;
  transition: transform var(--t-fast);
}
.rail-toggle__chev.open {
  transform: rotate(180deg);
}
/* Danh sách mã quyền: chữ mono nhỏ, cuộn riêng để card không dài vô hạn */
.perm-list {
  list-style: none;
  margin: 0.6rem 0 0;
  padding: 0.5rem 0.65rem;
  max-height: 190px;
  overflow-y: auto;
  background: var(--c-surface-2);
  border-radius: 9px;
}
.perm-list li {
  font-family: Consolas, 'Courier New', monospace;
  font-size: 0.73rem;
  color: var(--c-text);
  padding: 2px 0;
  overflow-wrap: anywhere;
}

/* Tài khoản trên máy này */
.acct {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 100%;
  padding: 0.45rem 0.5rem;
  border: none;
  background: transparent;
  font: inherit;
  text-align: left;
  border-radius: 9px;
  cursor: pointer;
  transition: background var(--t-fast);
}
.acct:hover {
  background: var(--c-surface-2);
}
.acct.current {
  cursor: default;
}
.acct__avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--grad-primary);
  color: #fff;
  font-size: 0.64rem;
  font-weight: 700;
  letter-spacing: 0.4px;
  flex: 0 0 auto;
}
.acct__info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}
.acct__info strong {
  font-size: 0.83rem;
  font-weight: 600;
  color: var(--c-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.acct__info small {
  font-size: 0.72rem;
  color: var(--c-text-muted);
}
.acct__check {
  display: grid;
  place-items: center;
  color: var(--c-primary);
  flex: 0 0 auto;
}
.rail-link {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.4rem;
  padding: 0.3rem 0.5rem;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--c-accent);
  text-decoration: none;
  border-radius: 8px;
  transition: background var(--t-fast);
}
.rail-link:hover {
  background: var(--c-surface-2);
}

/* ══════════ Nút (theo tông CTA cam của site) ══════════ */
.btn {
  padding: 0.5rem 1.05rem;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  transition:
    background var(--t-fast),
    color var(--t-fast),
    border-color var(--t-fast),
    box-shadow var(--t-fast),
    transform var(--t-fast);
}
.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn:active:not(:disabled) {
  transform: translateY(1px);
}
.btn--primary {
  background: var(--c-primary);
  color: #fff;
  box-shadow: 0 6px 14px rgba(249, 115, 22, 0.28);
}
.btn--primary:hover:not(:disabled) {
  background: var(--c-primary-dark);
  transform: translateY(-1px);
  box-shadow: 0 9px 18px rgba(249, 115, 22, 0.36);
}
.btn--outline {
  background: var(--c-surface);
  border-color: var(--c-input-border);
  color: var(--c-text);
}
.btn--outline:hover:not(:disabled) {
  border-color: var(--c-primary);
  color: var(--c-primary);
}
.btn--danger {
  background: var(--c-danger);
  color: #fff;
}
.btn--danger:hover:not(:disabled) {
  background: #dc2626;
}
.btn--danger-outline {
  background: transparent;
  border-color: rgba(239, 68, 68, 0.45);
  color: var(--c-danger);
}
.btn--danger-outline:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.09);
  border-color: var(--c-danger);
}
.btn--sm {
  padding: 0.32rem 0.7rem;
  font-size: 0.8rem;
}

/* ══════════ Thông báo + modal ══════════ */
.msg {
  font-size: 0.85rem;
  margin: 0.6rem 0 0;
}
.msg-ok {
  color: #16a34a;
}
:root[data-theme='dark'] .msg-ok {
  color: #4ade80;
}
.msg-err {
  color: var(--c-danger);
}
.text-muted {
  color: var(--c-text-muted);
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(5, 10, 20, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}
.modal-box {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 14px;
  padding: 1.5rem;
  width: 100%;
  max-width: 400px;
  box-shadow: var(--a-shadow-lg);
}
.modal-box h3 {
  margin: 0 0 0.75rem;
  font-size: 1.02rem;
  font-weight: 700;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 1.1rem;
}

/* ══════════ Responsive ══════════ */
/* Dưới 1024px: rail tụt xuống dưới nội dung (1 cột) */
@media (max-width: 1024px) {
  .st-cols {
    grid-template-columns: 1fr;
  }
  .st-rail {
    position: static;
  }
}
@media (max-width: 640px) {
  .st-page {
    padding: 1rem;
  }
  .hero__body {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
  .hero__badges {
    justify-content: center;
  }
  .card__head--row {
    flex-direction: column;
  }
  .sess-item {
    flex-wrap: wrap;
  }
  .setting-row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
a
