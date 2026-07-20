<script setup>
// HỒ SƠ CỦA TÔI — trang CHỈ XEM, tách khỏi Cài đặt (Cài đặt = nơi SỬA liên hệ,
// mật khẩu, phiên đăng nhập; nút "Chỉnh sửa" ở góc dẫn sang đó).
// Dùng chung cho mọi vai trò: GET /me/profile trả actorType + khối chi tiết
// tương ứng, template bật/tắt từng nhóm thông tin theo đó.
import { computed, onMounted, reactive } from 'vue'
import { useRoute } from 'vue-router'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import { settingsApi } from '@/api/settings'
import { useAuthStore } from '@/stores/auth'
import { permGroups, roleLabel } from '@/utils/labels'

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

const p = computed(() => state.data)
const isTeacher = computed(() => p.value?.actorType === 'TEACHER')
const isEmployee = computed(() => p.value?.actorType === 'EMPLOYEE')

// Quyền trong token nhóm theo module — người thường đọc được thay vì mã SCHEDULE_VIEW.
const permRows = computed(() => permGroups(auth.user?.perms ?? []))
const isAdmin = computed(() => (p.value?.roles ?? []).includes('ADMIN'))

const initials = computed(() => {
  const name = p.value?.fullName || p.value?.username || '?'
  const words = name.trim().split(/\s+/)
  return (
    (words[0]?.[0] ?? '') + (words.length > 1 ? words[words.length - 1][0] : '')
  ).toUpperCase()
})

/* ── Các hàm "dịch" giá trị thô sang chữ người đọc ── */
const EMPLOYMENT_LABELS = {
  FULL_TIME: 'Toàn thời gian',
  PART_TIME: 'Bán thời gian',
  CONTRACT: 'Hợp đồng',
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
      <SvgIcon name="pencil" :size="16" /> Chỉnh sửa liên hệ &amp; bảo mật
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

    <div class="grid-2">
      <div class="col">
        <!-- Thông tin cá nhân -->
        <section class="card">
          <h3 class="card__title"><SvgIcon name="teacher" :size="17" /> Thông tin cá nhân</h3>
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
            <div class="info-item">
              <dt>Số điện thoại</dt>
              <dd>{{ p.phone || 'Chưa cập nhật' }}</dd>
            </div>
            <div class="info-item">
              <dt>Email</dt>
              <dd>{{ p.email }}</dd>
            </div>
            <div v-if="isTeacher" class="info-item info-item--wide">
              <dt>Địa chỉ</dt>
              <dd>{{ p.teacher?.address || 'Chưa cập nhật' }}</dd>
            </div>
          </dl>
          <p class="card__note">
            Họ tên và CCCD do phòng Nhân sự quản lý — cần điều chỉnh hãy liên hệ trung tâm.
          </p>
        </section>

        <!-- Công việc -->
        <section v-if="isTeacher || isEmployee" class="card">
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

      <!-- Quyền của tôi: dịch mã quyền trong token sang tiếng Việt, gom theo phân hệ -->
      <div class="col">
        <section class="card">
          <h3 class="card__title"><SvgIcon name="shield" :size="17" /> Quyền của tôi</h3>
          <p v-if="isAdmin" class="perm-note">
            Bạn là <strong>Quản trị viên</strong> — có toàn quyền trên mọi phân hệ của hệ thống.
          </p>
          <template v-else-if="permRows.length">
            <ul class="perm-rows">
              <li v-for="row in permRows" :key="row.module" class="perm-row">
                <span class="perm-row__module">{{ row.module }}</span>
                <span class="perm-row__actions">
                  <span v-for="a in row.actions" :key="a" class="perm-chip">{{ a }}</span>
                </span>
              </li>
            </ul>
            <p v-if="isTeacher" class="perm-note">
              Các quyền "Xem" của giáo viên chỉ áp dụng với dữ liệu của <strong>chính bạn</strong>
              (lịch dạy, chấm công, đánh giá của mình).
            </p>
          </template>
          <p v-else class="empty-note">Tài khoản chưa được cấp quyền chi tiết nào.</p>
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

/* Quyền của tôi */
.perm-rows {
  list-style: none;
  margin: 0;
  padding: 0;
}
.perm-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  padding: 0.55rem 0;
  border-bottom: 1px solid var(--c-border-soft);
}
.perm-row:last-child {
  border-bottom: none;
}
.perm-row__module {
  font-size: 0.88rem;
  color: var(--a-text);
}
.perm-row__actions {
  display: flex;
  gap: 0.3rem;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.perm-chip {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--c-accent-dark);
  background: rgba(37, 99, 235, 0.09);
  border: 1px solid rgba(37, 99, 235, 0.22);
  border-radius: 9999px;
  padding: 0.08rem 0.5rem;
  white-space: nowrap;
}
:root[data-theme='dark'] .perm-chip {
  color: #93c5fd;
}
.perm-note {
  margin: 0.9rem 0 0;
  font-size: 0.82rem;
  color: var(--a-text-muted);
  line-height: 1.5;
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
