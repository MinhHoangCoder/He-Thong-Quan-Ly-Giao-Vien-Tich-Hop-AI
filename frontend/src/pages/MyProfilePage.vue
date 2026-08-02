<script setup>
// HỒ SƠ CỦA TÔI — nơi XEM toàn bộ hồ sơ và SỬA phần liên hệ (email + SĐT) tại chỗ.
// Email/SĐT là 2 trường DUY NHẤT backend cho tự sửa (PUT /me/profile); các trường
// định danh/công việc do phòng Nhân sự quản — hiển thị chỉ đọc kèm ghi chú.
// Cài đặt giờ chỉ còn: mật khẩu, thiết bị đăng nhập, giao diện (nút góc dẫn sang).
// Dùng chung cho mọi vai trò: GET /me/profile trả actorType + khối chi tiết
// tương ứng, template bật/tắt từng nhóm thông tin theo đó.
import { computed, onMounted, reactive } from 'vue'
import { useRoute } from 'vue-router'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import { settingsApi } from '@/api/settings'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { roleLabel } from '@/utils/labels'

const route = useRoute()
const auth = useAuthStore()

const state = reactive({ loading: true, error: '', data: null })

async function load() {
  state.loading = true
  state.error = ''
  try {
    const { data } = await settingsApi.getProfile()
    state.data = data
  } catch (e) {
    state.error = e?.response?.data?.message || 'Không tải được hồ sơ. Thử lại nhé.'
  } finally {
    state.loading = false
  }
}
onMounted(load)

// Trang Cài đặt của đúng khu vực hiện tại: /teacher/profile -> /teacher/settings,
// /profile (admin) -> /settings ... — suy từ path nên không phải khai báo từng layout.
const settingsPath = computed(() => route.path.replace(/\/profile$/, '/settings') || '/settings')

/* ── Sửa liên hệ tại chỗ (xem-trước-sửa-sau, chuyển từ trang Cài đặt sang) ── */
const edit = reactive({
  editing: false,
  form: { email: '', phone: '' },
  errors: {},
  saving: false,
  success: '',
  error: '',
})

// Mirror rule backend (UpdateProfileRequest): email hợp lệ; SĐT rỗng hoặc SĐT
// Việt Nam (0/+84 + 9-10 chữ số) — cùng quy ước với module Giáo viên.
const PHONE_RE = /^$|^(\+84|0)\d{9,10}$/
const FIELD_RULES = {
  email: () => {
    const email = edit.form.email.trim()
    if (!email) return 'Email không được để trống'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return 'Email không hợp lệ'
    return ''
  },
  phone: () =>
    PHONE_RE.test(edit.form.phone.trim())
      ? ''
      : 'SĐT phải bắt đầu bằng 0 hoặc +84, gồm 10–11 chữ số',
}

// Báo lỗi khi RỜI Ô; khi đang gõ chỉ validate lại nếu ô đó ĐANG lỗi
// (lỗi biến mất đúng lúc sửa xong, không cằn nhằn giữa chừng).
function checkField(field) {
  const msg = FIELD_RULES[field]()
  if (msg) edit.errors[field] = msg
  else delete edit.errors[field]
}

// Vào chế độ sửa: nạp form từ dữ liệu hiện tại để Hủy luôn quay về đúng bản gốc
function startEdit() {
  edit.form.email = p.value?.email ?? ''
  edit.form.phone = p.value?.phone ?? ''
  edit.errors = {}
  edit.success = ''
  edit.error = ''
  edit.editing = true
}

function cancelEdit() {
  edit.editing = false
  edit.errors = {}
  edit.error = ''
}

async function saveContact() {
  const errors = {}
  for (const field of Object.keys(FIELD_RULES)) {
    const msg = FIELD_RULES[field]()
    if (msg) errors[field] = msg
  }
  edit.errors = errors
  if (Object.keys(errors).length) return
  edit.saving = true
  edit.success = ''
  edit.error = ''
  try {
    const { data } = await settingsApi.updateProfile({
      email: edit.form.email.trim(),
      phone: edit.form.phone.trim(),
    })
    state.data = data
    edit.editing = false // lưu xong quay về chế độ xem
    edit.success = 'Đã lưu thông tin liên hệ'
    // Đồng bộ lại user trong store (topbar/menu đọc email từ đây)
    const me = await authApi.me()
    auth.setSession({ user: me.data })
  } catch (e) {
    edit.error = e?.response?.data?.message ?? 'Lưu thất bại'
  } finally {
    edit.saving = false
  }
}

const p = computed(() => state.data)
const isTeacher = computed(() => p.value?.actorType === 'TEACHER')
const isEmployee = computed(() => p.value?.actorType === 'EMPLOYEE')

// GV/NV có card Công việc -> chia 2 cột; admin/trường chỉ có 1 card -> 1 cột.
// (Khối "Quyền của tôi" đã bỏ 2026-07-17 — trùng vai trò với rail bên Cài đặt,
// nhóm thống nhất hồ sơ chỉ hiển thị thông tin con người, không hiển thị phân quyền.)
const hasWorkCol = computed(() => isTeacher.value || isEmployee.value)

const initials = computed(() => {
  const name = p.value?.fullName || p.value?.username || '?'
  const words = name.trim().split(/\s+/)
  return (
    (words[0]?.[0] ?? '') + (words.length > 1 ? words[words.length - 1][0] : '')
  ).toUpperCase()
})

/* ── Các hàm "dịch" giá trị thô sang chữ người đọc ── */
// Trang này dùng CHUNG cho giáo viên lẫn nhân viên trung tâm, mà hai bên có HAI bộ
// giá trị khác nhau ở cùng một trường `employmentType`:
//   - Giáo viên (bảng Teacher, đổi ở V20): CO_HUU | THINH_GIANG
//   - Nhân viên trung tâm (bảng Employee, V10): FULL_TIME | PART_TIME
// Thiếu vế nào thì tài khoản bên đó sẽ thấy chữ thô kiểu "FULL_TIME" trên hồ sơ.
const EMPLOYMENT_LABELS = {
  CO_HUU: 'Cơ hữu',
  THINH_GIANG: 'Thỉnh giảng',
  FULL_TIME: 'Toàn thời gian',
  PART_TIME: 'Bán thời gian',
}
const STATUS_LABELS = {
  ACTIVE: 'Đang hoạt động',
  INACTIVE: 'Ngừng hoạt động',
  RETIRED: 'Đã nghỉ',
  SUSPENDED: 'Tạm ngưng',
}
const employmentLabel = (v) => EMPLOYMENT_LABELS[v] || v
const statusLabel = (v) => STATUS_LABELS[v] || v
const genderLabel = (g) => (g === true ? 'Nam' : g === false ? 'Nữ' : null)
const fmtDate = (d) => (d ? new Intl.DateTimeFormat('vi-VN').format(new Date(d)) : null)
</script>

<template>
  <div class="page-head">
    <h1 class="page-head__title">Hồ sơ của tôi</h1>
    <RouterLink :to="settingsPath" class="btn-ghost">
      <SvgIcon name="settings" :size="16" /> Cài đặt tài khoản
    </RouterLink>
  </div>

  <p v-if="state.loading" class="state-note">Đang tải hồ sơ...</p>
  <p v-else-if="state.error" class="state-note state-note--err">
    {{ state.error }}
    <button class="btn-ghost" @click="load">Thử lại</button>
  </p>

  <template v-else-if="p">
    <!-- Thẻ định danh -->
    <section class="card id-card">
      <span class="id-card__avatar">{{ initials }}</span>
      <div class="id-card__info">
        <h2 class="id-card__name">{{ p.fullName }}</h2>
        <p class="id-card__meta">@{{ p.username }} · {{ p.email }}</p>
        <div class="id-card__chips">
          <span v-for="r in p.roles" :key="r" class="chip chip--role">{{ roleLabel(r) }}</span>
          <span v-if="p.position" class="chip">{{ p.position }}</span>
          <span v-if="p.profileStatus" class="chip chip--ok">{{
            statusLabel(p.profileStatus)
          }}</span>
        </div>
      </div>
    </section>

    <!-- GV/NV: 2 cột (cá nhân | công việc); admin/trường chỉ có 1 card -> 1 cột -->
    <div :class="hasWorkCol ? 'grid-2' : ''">
      <div class="col">
        <!-- Thông tin cá nhân: các trường HR quản chỉ đọc; email + SĐT sửa tại chỗ -->
        <section class="card">
          <div class="card__titlebar">
            <h3 class="card__title"><SvgIcon name="teacher" :size="17" /> Thông tin cá nhân</h3>
            <button
              v-if="!edit.editing"
              class="editbtn"
              title="Sửa email / số điện thoại"
              aria-label="Sửa email / số điện thoại"
              @click="startEdit"
            >
              <SvgIcon name="pencil" :size="16" />
            </button>
          </div>
          <dl class="info-grid">
            <div v-if="isTeacher" class="info-item">
              <dt>Số CCCD</dt>
              <dd>{{ p.teacher?.idCardNo || 'Chưa cập nhật' }}</dd>
            </div>
            <div v-if="isTeacher" class="info-item">
              <dt>Ngày sinh</dt>
              <dd>{{ fmtDate(p.teacher?.dateOfBirth) || 'Chưa cập nhật' }}</dd>
            </div>
            <div v-if="isTeacher" class="info-item">
              <dt>Giới tính</dt>
              <dd>{{ genderLabel(p.teacher?.gender) || 'Chưa cập nhật' }}</dd>
            </div>
            <!-- Chế độ XEM: chữ tĩnh. SĐT ẩn với tài khoản hệ thống (actorType NONE):
                 backend chỉ lưu phone trên hồ sơ Teacher/Employee/School — admin
                 không có hồ sơ nào nên có nhập cũng bị bỏ qua lặng lẽ. -->
            <template v-if="!edit.editing">
              <div v-if="p.actorType !== 'NONE'" class="info-item">
                <dt>Số điện thoại</dt>
                <dd>{{ p.phone || 'Chưa cập nhật' }}</dd>
              </div>
              <div class="info-item">
                <dt>Email</dt>
                <dd>{{ p.email }}</dd>
              </div>
            </template>
            <!-- Chế độ SỬA: 2 ô input thế đúng chỗ 2 dòng xem -->
            <template v-else>
              <div v-if="p.actorType !== 'NONE'" class="info-item">
                <dt>Số điện thoại</dt>
                <dd>
                  <input
                    v-model="edit.form.phone"
                    placeholder="VD: 0901234567"
                    :class="{ 'input-error': edit.errors.phone }"
                    @blur="checkField('phone')"
                    @input="edit.errors.phone && checkField('phone')"
                  />
                  <small v-if="edit.errors.phone" class="field-error">{{
                    edit.errors.phone
                  }}</small>
                </dd>
              </div>
              <div class="info-item">
                <dt>Email <span class="req">*</span></dt>
                <dd>
                  <input
                    v-model="edit.form.email"
                    type="email"
                    placeholder="ban@vidu.vn"
                    :class="{ 'input-error': edit.errors.email }"
                    @blur="checkField('email')"
                    @input="edit.errors.email && checkField('email')"
                  />
                  <small v-if="edit.errors.email" class="field-error">{{
                    edit.errors.email
                  }}</small>
                </dd>
              </div>
            </template>
            <div v-if="isTeacher" class="info-item info-item--wide">
              <dt>Địa chỉ</dt>
              <dd>{{ p.teacher?.address || 'Chưa cập nhật' }}</dd>
            </div>
          </dl>

          <p v-if="edit.success" class="msg msg--ok">{{ edit.success }}</p>
          <p v-if="edit.error" class="msg msg--err">{{ edit.error }}</p>

          <footer v-if="edit.editing" class="card__foot">
            <button class="btn-save" :disabled="edit.saving" @click="saveContact">
              {{ edit.saving ? 'Đang lưu...' : 'Lưu thay đổi' }}
            </button>
            <button class="btn-ghost" :disabled="edit.saving" @click="cancelEdit">Hủy</button>
          </footer>

          <p v-if="p.actorType !== 'NONE'" class="card__note">
            Chỉ email và số điện thoại tự sửa được. Họ tên, CCCD và các thông tin còn lại do phòng
            Nhân sự quản lý — cần điều chỉnh hãy liên hệ trung tâm.
          </p>
          <p v-else class="card__note">
            Tài khoản hệ thống chỉ tự sửa được email (dùng nhận liên kết đặt lại mật khẩu).
          </p>
        </section>
      </div>

      <!-- Cột phải: thông tin công việc (chỉ GV/NV có) — thế chỗ khối "Quyền của
           tôi" cũ; quyền vẫn xem được ở rail trang Cài đặt (đọc từ JWT) -->
      <div v-if="hasWorkCol" class="col">
        <!-- Công việc -->
        <section class="card">
          <h3 class="card__title"><SvgIcon name="assignment" :size="17" /> Công việc</h3>
          <dl class="info-grid">
            <div class="info-item">
              <dt>Chi nhánh</dt>
              <dd>{{ p.branchName || 'Chưa cập nhật' }}</dd>
            </div>
            <div v-if="isEmployee" class="info-item">
              <dt>Chức vụ</dt>
              <dd>{{ p.position || 'Chưa cập nhật' }}</dd>
            </div>
            <div class="info-item">
              <dt>Loại hình làm việc</dt>
              <dd>{{ employmentLabel(p.employmentType) || 'Chưa cập nhật' }}</dd>
            </div>
            <div v-if="isTeacher" class="info-item">
              <dt>Ngày vào làm</dt>
              <dd>{{ fmtDate(p.teacher?.hireDate) || 'Chưa cập nhật' }}</dd>
            </div>
            <div class="info-item">
              <dt>Trạng thái</dt>
              <dd>{{ statusLabel(p.profileStatus) || '—' }}</dd>
            </div>
            <!-- Ghi chú tự do, có thể dài & nhiều dòng nên chiếm trọn hàng (info-item--wide)
                 và giữ nguyên xuống dòng người dùng đã gõ (white-space: pre-line). -->
            <div v-if="isTeacher" class="info-item info-item--wide">
              <dt>Kinh nghiệm giảng dạy</dt>
              <dd class="info-text">
                {{ p.teacher?.teachingExperience || 'Chưa cập nhật' }}
              </dd>
            </div>
          </dl>
        </section>

        <!-- Bằng cấp & chứng chỉ (GV) -->
        <section v-if="isTeacher" class="card">
          <h3 class="card__title">
            <SvgIcon name="evaluation" :size="17" /> Bằng cấp &amp; chứng chỉ
          </h3>
          <ul v-if="p.teacher?.certificates?.length" class="cert-list">
            <li v-for="(c, i) in p.teacher.certificates" :key="i" class="cert">
              <span class="cert__icon"><SvgIcon name="check" :size="15" /></span>
              <div class="cert__body">
                <strong>{{ c.name }}</strong>
                <small>
                  <template v-if="c.issuer">{{ c.issuer }}</template>
                  <template v-if="c.issueDate"> · cấp {{ fmtDate(c.issueDate) }}</template>
                  <template v-if="c.expiryDate"> · hết hạn {{ fmtDate(c.expiryDate) }}</template>
                </small>
              </div>
            </li>
          </ul>
          <p v-else class="empty-note">Hồ sơ chưa ghi nhận bằng cấp/chứng chỉ nào.</p>
        </section>
      </div>
    </div>
  </template>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
  margin-bottom: 1.4rem;
}
.page-head__title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--a-text);
}
.btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  border: 1px solid var(--a-border);
  background: var(--c-surface);
  color: var(--a-text);
  cursor: pointer;
  padding: 0.55rem 0.95rem;
  border-radius: 10px;
  font-weight: 600;
  font-size: 0.86rem;
  text-decoration: none;
  transition:
    border-color var(--t-fast),
    color var(--t-fast),
    background var(--t-fast);
}
.btn-ghost:hover {
  border-color: var(--c-primary);
  color: var(--c-primary);
  background: rgba(249, 115, 22, 0.08);
}
.state-note {
  color: var(--a-text-muted);
  padding: 1.5rem 0;
}
.state-note--err {
  color: var(--c-danger);
  display: flex;
  align-items: center;
  gap: 0.8rem;
}

.card {
  background: var(--c-surface);
  border: 1px solid var(--a-border);
  border-radius: 16px;
  padding: 1.3rem;
  box-shadow: var(--a-shadow);
  margin-bottom: 1.1rem;
}
.card__title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0 0 1rem;
  font-size: 1.02rem;
  font-weight: 700;
  color: var(--a-text);
}
.card__title svg {
  color: var(--c-primary);
}
.card__note {
  margin: 0.9rem 0 0;
  padding-top: 0.8rem;
  border-top: 1px dashed var(--c-border);
  font-size: 0.8rem;
  color: var(--a-text-muted);
}
.card__titlebar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
}
.card__foot {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.9rem;
  padding-top: 0.9rem;
  border-top: 1px solid var(--c-border-soft);
}

/* Nút icon bút — mở chế độ sửa email/SĐT (đồng bộ pattern trang Cài đặt cũ) */
.editbtn {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  border: 1px solid var(--c-input-border);
  border-radius: 9px;
  background: var(--c-surface);
  color: var(--a-text-muted);
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

/* Ô nhập trong chế độ sửa — thế đúng chỗ dòng chữ tĩnh */
.info-item input {
  width: 100%;
  padding: 0.45rem 0.7rem;
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  font: inherit;
  font-size: 0.9rem;
  color: var(--a-text);
  background: var(--c-surface);
  transition:
    border-color var(--t-fast),
    box-shadow var(--t-fast);
}
.info-item input:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.14);
}
.input-error {
  border-color: var(--c-danger) !important;
}
.field-error {
  display: block;
  margin-top: 3px;
  font-size: 0.78rem;
  font-weight: 400;
  color: var(--c-danger);
}
.req {
  color: var(--c-danger);
}

.btn-save {
  border: none;
  border-radius: 10px;
  padding: 0.55rem 1.05rem;
  font: inherit;
  font-size: 0.86rem;
  font-weight: 600;
  color: #fff;
  background: var(--c-primary);
  cursor: pointer;
  box-shadow: 0 6px 14px rgba(249, 115, 22, 0.28);
  transition:
    background var(--t-fast),
    transform var(--t-fast),
    box-shadow var(--t-fast);
}
.btn-save:hover:not(:disabled) {
  background: var(--c-primary-dark);
  transform: translateY(-1px);
}
.btn-save:disabled,
.btn-ghost:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.msg {
  margin: 0.7rem 0 0;
  font-size: 0.85rem;
}
.msg--ok {
  color: #16a34a;
}
:root[data-theme='dark'] .msg--ok {
  color: #4ade80;
}
.msg--err {
  color: var(--c-danger);
}

/* Thẻ định danh */
.id-card {
  display: flex;
  align-items: center;
  gap: 1.1rem;
}
.id-card__avatar {
  flex: 0 0 auto;
  width: 72px;
  height: 72px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--grad-primary);
  color: #fff;
  font-size: 1.5rem;
  font-weight: 800;
  letter-spacing: 1px;
}
.id-card__name {
  margin: 0;
  font-size: 1.3rem;
  color: var(--a-text);
}
.id-card__meta {
  margin: 0.15rem 0 0.6rem;
  font-size: 0.88rem;
  color: var(--a-text-muted);
}
.id-card__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}
.chip {
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--c-text);
  background: var(--c-surface-2);
  border: 1px solid var(--c-border);
  border-radius: 9999px;
  padding: 0.15rem 0.6rem;
}
.chip--role {
  color: var(--c-accent-dark);
  background: rgba(37, 99, 235, 0.09);
  border-color: rgba(37, 99, 235, 0.22);
}
:root[data-theme='dark'] .chip--role {
  color: #93c5fd;
}
.chip--ok {
  color: #15803d;
  background: rgba(34, 197, 94, 0.14);
  border-color: rgba(34, 197, 94, 0.3);
}
:root[data-theme='dark'] .chip--ok {
  color: #4ade80;
}

.grid-2 {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 1.1rem;
  align-items: start;
}

/* Lưới nhãn–giá trị */
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.9rem 1.2rem;
  margin: 0;
}
.info-item--wide {
  grid-column: 1 / -1;
}
.info-item dt {
  font-size: 0.76rem;
  color: var(--a-text-muted);
  margin-bottom: 0.15rem;
}
.info-item dd {
  margin: 0;
  font-size: 0.93rem;
  font-weight: 600;
  color: var(--a-text);
  overflow-wrap: anywhere;
}
/* Ghi chú dài (kinh nghiệm giảng dạy): giữ lại xuống dòng người dùng đã gõ và bỏ
   chữ đậm — 500 ký tự mà in đậm hết thì nặng mắt so với các ô thông tin ngắn. */
.info-text {
  white-space: pre-line;
  font-weight: 500;
  line-height: 1.5;
}

/* Bằng cấp & chứng chỉ */
.cert-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.cert {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  padding: 0.6rem 0;
  border-bottom: 1px solid var(--c-border-soft);
}
.cert:last-child {
  border-bottom: none;
}
.cert__icon {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: rgba(34, 197, 94, 0.14);
  color: #15803d;
  margin-top: 1px;
}
:root[data-theme='dark'] .cert__icon {
  color: #4ade80;
}
.cert__body {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.cert__body strong {
  font-size: 0.92rem;
  color: var(--a-text);
}
.cert__body small {
  font-size: 0.78rem;
  color: var(--a-text-muted);
}

.empty-note {
  margin: 0;
  color: var(--a-text-muted);
  font-size: 0.88rem;
}

@media (max-width: 1000px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 560px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
