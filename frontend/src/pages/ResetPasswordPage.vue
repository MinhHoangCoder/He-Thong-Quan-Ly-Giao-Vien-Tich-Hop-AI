<script setup>
// Trang đặt lại mật khẩu. Người dùng tới đây từ link trong email:
//   http://localhost:5173/reset-password?token=XXXX
// Lấy token trên URL -> gửi kèm mật khẩu mới cho backend.
import { ref, computed } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { authApi } from '@/api/auth'
import { isStrongPassword, PASSWORD_HINT } from '@/utils/password'
import SvgIcon from '@/components/ui/SvgIcon.vue'

const route = useRoute()
const token = route.query.token || ''

const newPassword = ref('')
const confirm = ref('')
const showPassword = ref(false)
const loading = ref(false)
const error = ref('')
const done = ref(false)

const canSubmit = computed(
  () => isStrongPassword(newPassword.value) && newPassword.value === confirm.value,
)

async function onSubmit() {
  error.value = ''
  if (!isStrongPassword(newPassword.value)) {
    error.value = `Mật khẩu chưa đủ mạnh: ${PASSWORD_HINT}.`
    return
  }
  if (newPassword.value !== confirm.value) {
    error.value = 'Mật khẩu nhập lại không khớp.'
    return
  }
  loading.value = true
  try {
    await authApi.resetPassword({ token, newPassword: newPassword.value })
    done.value = true
  } catch (e) {
    error.value = e.response?.data?.message || 'Đặt lại mật khẩu thất bại. Token có thể đã hết hạn.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="card">
    <h1 class="title">Đặt lại mật khẩu</h1>

    <!-- Thành công -->
    <template v-if="done">
      <p class="msg msg--ok">Đặt lại mật khẩu thành công! Hãy đăng nhập bằng mật khẩu mới.</p>
      <RouterLink class="btn" :to="{ name: 'login' }">Đến trang đăng nhập</RouterLink>
    </template>

    <!-- Thiếu token -->
    <template v-else-if="!token">
      <p class="msg msg--error">
        Liên kết không hợp lệ hoặc thiếu token. Hãy yêu cầu lại ở mục “Quên mật khẩu”.
      </p>
      <RouterLink class="link" :to="{ name: 'login' }">← Về đăng nhập</RouterLink>
    </template>

    <!-- Form đặt lại -->
    <form v-else class="form" @submit.prevent="onSubmit">
      <p class="sub">Nhập mật khẩu mới cho tài khoản của bạn ({{ PASSWORD_HINT }}).</p>

      <label class="field">
        <span>Mật khẩu mới</span>
        <div class="pwd">
          <input
            v-model="newPassword"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="new-password"
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

      <label class="field">
        <span>Nhập lại mật khẩu</span>
        <input
          v-model="confirm"
          :type="showPassword ? 'text' : 'password'"
          autocomplete="new-password"
          required
        />
      </label>

      <p v-if="error" class="msg msg--error">{{ error }}</p>

      <button class="btn" type="submit" :disabled="loading || !canSubmit">
        {{ loading ? 'Đang lưu…' : 'Đặt lại mật khẩu' }}
      </button>
      <RouterLink class="link" :to="{ name: 'login' }">← Về đăng nhập</RouterLink>
    </form>
  </div>
</template>

<style scoped>
.card {
  width: 100%;
  max-width: 380px;
  padding: 32px;
  border-radius: 16px;
  background: var(--c-surface);
  box-shadow: 0 18px 50px rgba(15, 40, 80, 0.12);
  border: 1px solid var(--c-border);
}
.title {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 800;
  text-align: center;
  color: var(--c-primary, #f97316);
}
.sub {
  margin: 0 0 16px;
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
  border: 1px solid var(--c-input-border);
  border-radius: 10px;
  font-size: 15px;
  outline: none;
  transition: border-color 0.15s;
}
.field input:focus {
  border-color: var(--c-primary, #f97316);
}
.pwd {
  position: relative;
}
.pwd input {
  width: 100%;
  padding-right: 42px;
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
}
.pwd__toggle:hover {
  color: var(--c-primary, #f97316);
  background: rgba(249, 115, 22, 0.08);
}
.btn {
  margin-top: 4px;
  padding: 11px;
  border: none;
  border-radius: 10px;
  background: var(--c-primary, #f97316);
  color: #fff;
  font-weight: 600;
  font-size: 15px;
  cursor: pointer;
  text-align: center;
  text-decoration: none;
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
  display: inline-block;
  margin-top: 4px;
  color: var(--c-primary, #f97316);
  font-size: 13px;
  text-align: center;
  text-decoration: none;
}
.msg {
  margin: 0;
  font-size: 13px;
  border-radius: 8px;
  padding: 8px 10px;
}
.msg--error {
  background: rgba(239, 68, 68, 0.1);
  color: #b91c1c;
}
.msg--ok {
  background: rgba(34, 197, 94, 0.12);
  color: #047857;
  margin-bottom: 14px;
}
:root[data-theme='dark'] .msg--error {
  color: #f87171;
}
:root[data-theme='dark'] .msg--ok {
  color: #4ade80;
}
</style>
