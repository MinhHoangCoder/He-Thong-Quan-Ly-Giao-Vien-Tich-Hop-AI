<script setup>
// Trang đăng nhập dùng CHUNG cho cả 4 actor (RBAC: 1 login, phân quyền sau đăng nhập).
// Có thêm chế độ "Quên mật khẩu" (gửi email đặt lại) và chế độ "Thêm tài khoản"
// (?add=1 — đăng nhập THÊM tài khoản cho account switcher, phiên đang có giữ nguyên).
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { roleHome } from '@/router/roleHome'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import BrandLogo from '@/components/ui/BrandLogo.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const mode = ref('login') // 'login' | 'forgot'

// Chế độ "Thêm tài khoản": vào từ dropdown avatar khi ĐÃ đăng nhập (guard cho qua
// nhờ ?add=1). Login thành công thì store upsert tài khoản mới, tài khoản cũ ở lại.
const isAddMode = computed(() => route.query.add === '1')

// --- Đăng nhập ---
const username = ref('')
const password = ref('')
const showPassword = ref(false) // bật/tắt hiện mật khẩu
const loading = ref(false)
const error = ref('')

async function onLogin() {
  error.value = ''
  loading.value = true
  try {
    const { data } = await authApi.login({
      username: username.value.trim(),
      password: password.value,
    })
    // data: { accessToken, refreshToken, tokenType, expiresIn, user }
    auth.setSession({
      accessToken: data.accessToken,
      refreshToken: data.refreshToken,
      user: data.user,
    })
    // Ưu tiên trang bị chặn trước đó (?redirect=), nếu không thì về "nhà" theo vai trò.
    const target = route.query.redirect || roleHome(data.user.roles)
    router.push(target)
  } catch (e) {
    error.value = e.response?.data?.message || 'Đăng nhập thất bại, vui lòng thử lại.'
  } finally {
    loading.value = false
  }
}

// --- Quên mật khẩu ---
const forgotEmail = ref('')
const forgotMsg = ref('')
const forgotLoading = ref(false)

async function onForgot() {
  forgotMsg.value = ''
  forgotLoading.value = true
  try {
    const { data } = await authApi.forgotPassword(forgotEmail.value.trim())
    forgotMsg.value = data.message || 'Đã gửi yêu cầu. Vui lòng kiểm tra email.'
  } catch (e) {
    forgotMsg.value = e.response?.data?.message || 'Có lỗi xảy ra, vui lòng thử lại.'
  } finally {
    forgotLoading.value = false
  }
}

// Bấm vào 1 tài khoản demo -> điền nhanh
function fillDemo(u) {
  username.value = u
  password.value = 'Tsdms@123'
}
// Chip đăng nhập nhanh — tất cả mật khẩu Tsdms@123.
// 'employee' CỐ TÌNH không liệt kê: nó là tài khoản TEST gộp 4 phòng ban (Flyway V5),
// để dành test nội bộ (gõ tay 'employee'), không bày ra màn đăng nhập demo cho gọn.
const demoAccounts = ['admin', 'school', 'teacher', 'ketoan', 'nhansu', 'daotao', 'tuyensinh']

// Tính năng hiển thị ở hero (giống các "hexchip" trên ảnh bìa)
const features = [
  { icon: 'assignment', label: 'Phân công & điều phối' },
  { icon: 'schedule', label: 'Lịch dạy trực quan' },
  { icon: 'ai', label: 'Trợ lý AI hỗ trợ' },
  { icon: 'evaluation', label: 'Báo cáo & đánh giá' },
]
</script>

<template>
  <div class="auth">
    <!-- ===== HERO (trái) ===== -->
    <aside class="auth__hero">
      <div class="auth__glow auth__glow--1" />
      <div class="auth__glow auth__glow--2" />
      <div class="auth__hero-inner fade-up">
        <BrandLogo :size="48" variant="light" class="auth__brand" />
        <h2 class="auth__headline">
          Vận hành & điều phối giáo viên
          <span class="auth__headline-accent">tích hợp AI</span>
        </h2>
        <p class="auth__tagline">
          Phân công thông minh · Lịch dạy · Chấm công · Báo cáo — tất cả trong một nền tảng.
        </p>
        <ul class="auth__features">
          <li v-for="f in features" :key="f.label">
            <span class="hexchip"><SvgIcon :name="f.icon" :size="20" /></span>
            {{ f.label }}
          </li>
        </ul>
      </div>
    </aside>

    <!-- ===== PANEL ĐĂNG NHẬP (phải) ===== -->
    <main class="auth__panel">
      <div class="login-card fade-up">
        <BrandLogo :size="44" :show-text="false" class="login-mark" />
        <h1 class="login-title">KDC EduOps AI</h1>
        <p class="login-sub">Hệ thống quản lý &amp; điều phối giáo viên</p>

        <!-- CHẾ ĐỘ ĐĂNG NHẬP -->
        <form v-if="mode === 'login'" @submit.prevent="onLogin" class="form">
          <p v-if="isAddMode" class="addnote">
            Đang <strong>thêm tài khoản</strong> — các tài khoản đã đăng nhập vẫn giữ nguyên.
            <button type="button" class="addnote__back" @click="router.back()">← Quay lại</button>
          </p>
          <label class="field">
            <span>Tên đăng nhập</span>
            <input v-model="username" type="text" autocomplete="username" required />
          </label>
          <label class="field">
            <span>Mật khẩu</span>
            <div class="pwd">
              <input
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                required
              />
              <button
                type="button"
                class="pwd__toggle"
                :title="showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'"
                :aria-label="showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'"
                @click="showPassword = !showPassword"
              >
                <SvgIcon :name="showPassword ? 'eye-off' : 'eye'" :size="18" />
              </button>
            </div>
          </label>

          <p v-if="error" class="msg msg--error">{{ error }}</p>

          <button class="btn" type="submit" :disabled="loading">
            {{ loading ? 'Đang đăng nhập…' : 'Đăng nhập' }}
          </button>

          <button type="button" class="link" @click="mode = 'forgot'">Quên mật khẩu?</button>

          <div class="demo">
            <p class="demo__title">Tài khoản demo (mật khẩu: <code>Tsdms@123</code>)</p>
            <div class="demo__chips">
              <button
                v-for="u in demoAccounts"
                :key="u"
                type="button"
                class="chip"
                @click="fillDemo(u)"
              >
                {{ u }}
              </button>
            </div>
          </div>
        </form>

        <!-- CHẾ ĐỘ QUÊN MẬT KHẨU -->
        <form v-else @submit.prevent="onForgot" class="form">
          <p class="login-sub">Nhập email tài khoản, hệ thống sẽ gửi liên kết đặt lại mật khẩu.</p>
          <label class="field">
            <span>Email</span>
            <input v-model="forgotEmail" type="email" required />
          </label>

          <p v-if="forgotMsg" class="msg msg--ok">{{ forgotMsg }}</p>

          <button class="btn" type="submit" :disabled="forgotLoading">
            {{ forgotLoading ? 'Đang gửi…' : 'Gửi liên kết đặt lại' }}
          </button>
          <button type="button" class="link" @click="mode = 'login'">← Quay lại đăng nhập</button>
        </form>
      </div>
    </main>
  </div>
</template>

<style scoped>
/* Banner chế độ "Thêm tài khoản" (account switcher) */
.addnote {
  margin: 0 0 0.4rem;
  padding: 0.55rem 0.8rem;
  font-size: 0.82rem;
  color: var(--c-accent-dark, #1d4ed8);
  background: #eff6ff;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  line-height: 1.45;
}
.addnote__back {
  border: none;
  background: transparent;
  padding: 0;
  margin-left: 0.35rem;
  font: inherit;
  font-weight: 600;
  color: inherit;
  text-decoration: underline;
  cursor: pointer;
}

.auth {
  display: flex;
  width: 100%;
  min-height: 100vh;
}

/* ===== HERO ===== */
.auth__hero {
  position: relative;
  flex: 1.05;
  overflow: hidden;
  display: flex;
  align-items: center;
  padding: 3rem;
  color: #fff;
  background: var(--grad-hero);
}
/* lưới mạch điện mờ tạo chiều sâu công nghệ (như ảnh bìa) */
.auth__hero::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.05) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: radial-gradient(circle at 30% 40%, #000 0%, transparent 75%);
}
.auth__glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  opacity: 0.55;
  pointer-events: none;
}
.auth__glow--1 {
  width: 320px;
  height: 320px;
  top: -80px;
  right: -60px;
  background: #f97316;
}
.auth__glow--2 {
  width: 280px;
  height: 280px;
  bottom: -90px;
  left: -40px;
  background: #2563eb;
}
.auth__hero-inner {
  position: relative;
  z-index: 1;
  max-width: 440px;
}
.auth__brand {
  margin-bottom: 1.75rem;
}
.auth__brand :deep(.brandlogo__text) {
  font-size: 1.35rem;
}
.auth__headline {
  margin: 0 0 0.85rem;
  font-size: 2.05rem;
  line-height: 1.2;
  font-weight: 800;
  letter-spacing: 0.2px;
}
.auth__headline-accent {
  display: inline-block;
  color: #ffb877;
}
.auth__tagline {
  margin: 0 0 2rem;
  font-size: 1rem;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.82);
}
.auth__features {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.85rem;
}
.auth__features li {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  font-weight: 600;
  font-size: 0.97rem;
}
.hexchip {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 12px;
  box-shadow: 0 0 18px rgba(37, 99, 235, 0.25);
}

/* ===== PANEL ===== */
.auth__panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem 1.25rem;
  background: var(--c-bg);
}
.login-card {
  width: 100%;
  max-width: 400px;
  padding: 32px;
  border-radius: 18px;
  background: var(--c-surface);
  box-shadow: 0 18px 50px rgba(15, 40, 80, 0.12);
  border: 1px solid var(--c-border);
}
.login-mark {
  display: block;
  margin: 0 auto 10px;
  width: fit-content;
}
.login-title {
  margin: 0;
  font-size: 26px;
  font-weight: 800;
  text-align: center;
  color: var(--c-text);
}
.login-sub {
  margin: 4px 0 20px;
  text-align: center;
  color: var(--c-text-muted);
  font-size: 14px;
}
.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 14px;
  color: var(--c-text);
}
.field input {
  padding: 10px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  font-size: 15px;
  outline: none;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}
.field input:focus {
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.14);
}
.btn {
  margin-top: 4px;
  padding: 11px;
  border: none;
  border-radius: 10px;
  background: var(--c-primary);
  color: #fff;
  font-weight: 600;
  font-size: 15px;
  cursor: pointer;
  box-shadow: 0 6px 16px rgba(249, 115, 22, 0.28);
  transition:
    filter 0.15s,
    transform var(--t-fast),
    box-shadow var(--t);
}
.btn:hover:not(:disabled) {
  filter: brightness(1.04);
  transform: translateY(-1px);
  box-shadow: 0 10px 22px rgba(249, 115, 22, 0.38);
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.pwd {
  position: relative;
}
.pwd input {
  width: 100%;
  padding-right: 42px; /* chừa chỗ cho nút con mắt */
}
.pwd__toggle {
  position: absolute;
  top: 50%;
  right: 6px;
  transform: translateY(-50%);
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: var(--c-text-muted);
  cursor: pointer;
  border-radius: 8px;
  transition:
    color 0.15s,
    background 0.15s;
}
.pwd__toggle:hover {
  color: var(--c-primary);
  background: rgba(249, 115, 22, 0.08);
}
.link {
  background: none;
  border: none;
  color: var(--c-primary);
  font-size: 13px;
  cursor: pointer;
  align-self: center;
}
.msg {
  margin: 0;
  font-size: 13px;
  border-radius: 8px;
  padding: 8px 10px;
}
.msg--error {
  background: #fef2f2;
  color: #b91c1c;
}
.msg--ok {
  background: #ecfdf5;
  color: #047857;
}
.demo {
  margin-top: 8px;
  padding-top: 14px;
  border-top: 1px dashed var(--c-border);
}
.demo__title {
  margin: 0 0 8px;
  font-size: 12px;
  color: #64748b;
}
.demo__chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.chip {
  padding: 4px 12px;
  border: 1px solid rgba(249, 115, 22, 0.35);
  background: rgba(249, 115, 22, 0.07);
  color: var(--c-primary-dark);
  border-radius: 999px;
  font-size: 13px;
  cursor: pointer;
  transition:
    background var(--t-fast),
    transform var(--t-fast);
}
.chip:hover {
  background: rgba(249, 115, 22, 0.16);
  transform: translateY(-1px);
}
code {
  background: var(--c-surface-2);
  padding: 1px 5px;
  border-radius: 4px;
}

/* ===== Responsive: ẩn hero, chỉ còn form trên màn nhỏ ===== */
@media (max-width: 880px) {
  .auth__hero {
    display: none;
  }
  .auth__panel {
    flex: 1;
  }
}
</style>
