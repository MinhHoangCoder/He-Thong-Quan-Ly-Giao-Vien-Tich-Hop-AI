<script setup>
// Form tạo tài khoản cho GIÁO VIÊN / TRƯỜNG. Chỉ ADMIN & EMPLOYEE vào được
// (route có meta.roles, và backend cũng chặn ở SecurityConfig).
import { ref, computed, onMounted } from 'vue'
import { authApi } from '@/api/auth'
import { branchApi } from '@/api/branches'
import { isStrongPassword, PASSWORD_HINT } from '@/utils/password'

const form = ref({
  role: 'TEACHER',
  username: '',
  email: '',
  fullName: '',
  password: '',
  phone: '',
  branchId: '',
})

const branches = ref([])
const loading = ref(false)
const error = ref('')
const success = ref('')

// Nhãn đổi theo vai trò: GV thì "Họ tên", trường thì "Tên trường".
const fullNameLabel = computed(() =>
  form.value.role === 'SCHOOL' ? 'Tên trường' : 'Họ tên giáo viên',
)

onMounted(async () => {
  try {
    const { data } = await branchApi.list()
    branches.value = data
    if (data.length) form.value.branchId = data[0].id
  } catch {
    error.value = 'Không tải được danh sách chi nhánh.'
  }
})

async function onSubmit() {
  error.value = ''
  success.value = ''
  if (!isStrongPassword(form.value.password)) {
    error.value = `Mật khẩu chưa đủ mạnh: ${PASSWORD_HINT}.`
    return
  }
  loading.value = true
  try {
    const { data } = await authApi.register({
      ...form.value,
      branchId: Number(form.value.branchId),
    })
    success.value = `Đã tạo tài khoản "${data.username}" (vai trò ${data.roles.join(', ')}).`
    // Giữ lại role + branch để tạo tiếp nhanh; xóa các field còn lại.
    form.value.username = ''
    form.value.email = ''
    form.value.fullName = ''
    form.value.password = ''
    form.value.phone = ''
  } catch (e) {
    error.value = e.response?.data?.message || 'Tạo tài khoản thất bại.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <h1 class="page__title">Tạo tài khoản</h1>
    <p class="page__sub">Admin / Nhân viên tạo tài khoản đăng nhập cho Giáo viên hoặc Trường.</p>

    <form class="card" @submit.prevent="onSubmit">
      <div class="grid">
        <label class="field">
          <span>Vai trò</span>
          <select v-model="form.role">
            <option value="TEACHER">Giáo viên</option>
            <option value="SCHOOL">Trường</option>
          </select>
        </label>

        <label class="field">
          <span>Chi nhánh</span>
          <select v-model="form.branchId" required>
            <option v-for="b in branches" :key="b.id" :value="b.id">{{ b.name }}</option>
          </select>
        </label>

        <label class="field">
          <span>Tên đăng nhập</span>
          <input v-model="form.username" type="text" autocomplete="off" required />
        </label>

        <label class="field">
          <span>Email</span>
          <input v-model="form.email" type="email" required />
        </label>

        <label class="field field--full">
          <span>{{ fullNameLabel }}</span>
          <input v-model="form.fullName" type="text" required />
        </label>

        <label class="field">
          <span>Mật khẩu ({{ PASSWORD_HINT }})</span>
          <input v-model="form.password" type="text" autocomplete="off" required />
        </label>

        <label class="field">
          <span>Số điện thoại (tùy chọn)</span>
          <input v-model="form.phone" type="text" />
        </label>
      </div>

      <p v-if="error" class="msg msg--error">{{ error }}</p>
      <p v-if="success" class="msg msg--ok">{{ success }}</p>

      <button class="btn" type="submit" :disabled="loading">
        {{ loading ? 'Đang tạo…' : 'Tạo tài khoản' }}
      </button>
    </form>
  </div>
</template>

<style scoped>
.page {
  max-width: 760px;
}
.page__title {
  margin: 0 0 4px;
  font-size: 22px;
  color: var(--a-text, #0f172a);
}
.page__sub {
  margin: 0 0 18px;
  color: var(--a-text-muted, #64748b);
  font-size: 14px;
}
.card {
  background: #fff;
  border: 1px solid var(--a-border, #e2e8f0);
  border-radius: 14px;
  padding: 22px;
}
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 14px;
  color: #334155;
}
.field--full {
  grid-column: 1 / -1;
}
.field input,
.field select {
  padding: 10px 12px;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  font-size: 15px;
  outline: none;
  background: #fff;
  transition: border-color 0.15s;
}
.field input:focus,
.field select:focus {
  border-color: var(--c-primary, #f97316);
}
.btn {
  margin-top: 18px;
  padding: 11px 22px;
  border: none;
  border-radius: 10px;
  background: var(--c-primary, #f97316);
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
.msg {
  margin: 16px 0 0;
  font-size: 13px;
  border-radius: 8px;
  padding: 10px 12px;
}
.msg--error {
  background: #fef2f2;
  color: #b91c1c;
}
.msg--ok {
  background: #ecfdf5;
  color: #047857;
}
@media (max-width: 560px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
