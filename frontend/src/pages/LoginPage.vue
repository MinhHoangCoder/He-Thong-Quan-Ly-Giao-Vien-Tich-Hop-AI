<script setup>
// Trang đăng nhập dùng CHUNG cho cả 4 actor (RBAC: 1 login, phân quyền sau đăng nhập).
// Có thêm chế độ "Quên mật khẩu" (gửi email đặt lại).
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const mode = ref('login') // 'login' | 'forgot'

// --- Đăng nhập ---
const username = ref('')
const password = ref('')
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
    const redirect = route.query.redirect || '/dashboard'
    router.push(redirect)
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
const demoAccounts = ['admin', 'employee', 'school', 'teacher']
</script>

<template>
  <div class="login-card">
    <h1 class="login-title">TSDMS</h1>
    <p class="login-sub">Hệ thống quản lý &amp; điều phối giáo viên</p>

    <!-- CHẾ ĐỘ ĐĂNG NHẬP -->
    <form v-if="mode === 'login'" @submit.prevent="onLogin" class="form">
      <label class="field">
        <span>Tên đăng nhập</span>
        <input v-model="username" type="text" autocomplete="username" required />
      </label>
      <label class="field">
        <span>Mật khẩu</span>
        <input v-model="password" type="password" autocomplete="current-password" required />
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
</template>

<style scoped>
.login-card {
  width: 100%;
  max-width: 380px;
  padding: 32px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 10px 40px rgba(13, 148, 136, 0.12);
  border: 1px solid rgba(13, 148, 136, 0.1);
}
.login-title {
  margin: 0;
  font-size: 28px;
  font-weight: 800;
  text-align: center;
  color: var(--c-primary, #0d9488);
}
.login-sub {
  margin: 4px 0 20px;
  text-align: center;
  color: #64748b;
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
  color: #334155;
}
.field input {
  padding: 10px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  font-size: 15px;
  outline: none;
  transition: border-color 0.15s;
}
.field input:focus {
  border-color: var(--c-primary, #0d9488);
}
.btn {
  margin-top: 4px;
  padding: 11px;
  border: none;
  border-radius: 10px;
  background: var(--c-primary, #0d9488);
  color: #fff;
  font-weight: 600;
  font-size: 15px;
  cursor: pointer;
  transition: filter 0.15s;
}
.btn:hover:not(:disabled) {
  filter: brightness(1.05);
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.link {
  background: none;
  border: none;
  color: var(--c-primary, #0d9488);
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
  border-top: 1px dashed #e2e8f0;
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
  border: 1px solid rgba(13, 148, 136, 0.3);
  background: rgba(13, 148, 136, 0.06);
  color: var(--c-primary, #0d9488);
  border-radius: 999px;
  font-size: 13px;
  cursor: pointer;
}
.chip:hover {
  background: rgba(13, 148, 136, 0.14);
}
code {
  background: #f1f5f9;
  padding: 1px 5px;
  border-radius: 4px;
}
</style>
