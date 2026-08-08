<script setup>
/**
 * Trang Chấm công: xem/ghi chấm công theo khoảng ngày. Có thể sinh hàng loạt từ lịch
 * dạy đã duyệt, rồi chỉnh từng dòng (giờ vào/ra, trạng thái). Số giờ dạy tự tính —
 * là đầu vào cho Bảng lương.
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { attendanceApi } from '@/api/attendance'
import { assignmentApi } from '@/api/assignments'
import Pagination from '@/components/ui/Pagination.vue'
import DateField from '@/components/ui/DateField.vue'

const STATUSES = [
  { code: 'PRESENT', label: 'Có mặt', cls: 'badge-green' },
  { code: 'LATE', label: 'Đi muộn', cls: 'badge-amber' },
  { code: 'LEAVE', label: 'Nghỉ phép', cls: 'badge-gray' },
  { code: 'ABSENT', label: 'Vắng', cls: 'badge-red' },
]
const statusMeta = (code) =>
  STATUSES.find((s) => s.code === code) ?? { label: code, cls: 'badge-gray' }

/** Nguồn ghi nhận: GV tự bấm Check in (SELF) hay nhân viên ghi/sinh hộ — kế toán cần phân biệt. */
const METHOD_LABELS = {
  SELF: 'GV tự chấm',
  EMPLOYEE: 'Nhân viên',
  SCHOOL: 'Trường',
  DEVICE: 'Thiết bị',
  SYSTEM: 'Hệ thống',
}
const methodLabel = (m) => METHOD_LABELS[m] || '—'

// Format ngày theo GIỜ ĐỊA PHƯƠNG (yyyy-MM-dd). KHÔNG dùng toISOString() vì nó quy về
// UTC → ở múi giờ VN (UTC+7) mốc 00:00 bị lùi 1 ngày, làm khoảng lọc mặc định lệch:
// lọt ngày cuối tháng trước và thiếu ngày cuối tháng này.
const isoLocal = (d) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const today = new Date()
const firstOfMonth = isoLocal(new Date(today.getFullYear(), today.getMonth(), 1))
const lastOfMonth = isoLocal(new Date(today.getFullYear(), today.getMonth() + 1, 0))

const filter = reactive({ from: firstOfMonth, to: lastOfMonth, teacherId: '' })
const teachers = ref([])
const rows = ref([])
const loading = ref(false)
const info = ref('')

/* ── Phân trang phía client ── */
const PAGE_SIZE = 10
const page = ref(0)
const totalPages = computed(() => Math.ceil(rows.value.length / PAGE_SIZE))
const pagedRows = computed(() => {
  const start = page.value * PAGE_SIZE
  return rows.value.slice(start, start + PAGE_SIZE)
})

const editModal = reactive({ open: false, saving: false, error: '', id: null, form: {} })

async function loadTeachers() {
  try {
    const { data } = await assignmentApi.options()
    teachers.value = data.teachers
  } catch {
    teachers.value = []
  }
}

async function load() {
  loading.value = true
  info.value = ''
  page.value = 0
  try {
    const { data } = await attendanceApi.list({
      teacherId: filter.teacherId || undefined,
      from: filter.from,
      to: filter.to,
    })
    rows.value = data
  } catch (e) {
    rows.value = []
    info.value = e.response?.data?.message ?? 'Không tải được dữ liệu'
  } finally {
    loading.value = false
  }
  loadAttention()
}

/* ── Cần xử lý + nhật ký thay đổi ── */
const attention = ref([])
const attnOpen = ref(false)

async function loadAttention() {
  try {
    const { data } = await attendanceApi.attention({ from: filter.from, to: filter.to })
    attention.value = data
  } catch {
    attention.value = []
  }
}

/** Vì sao dòng này lọt vào "Cần xử lý" — nói thẳng lý do thay vì bắt kế toán tự đoán. */
function attentionReason(r) {
  if (r.checkInMethod === 'SYSTEM') return 'Hệ thống ghi Vắng — hết buổi không có check-in'
  if (r.autoCheckOut) return 'Hệ thống chốt hộ giờ ra — giáo viên quên check-out'
  return 'Đã vào lớp nhưng chưa có giờ ra'
}

const logRow = ref(null)
const logItems = ref([])
const logLoading = ref(false)

async function openLogs(r) {
  logRow.value = r
  logItems.value = []
  logLoading.value = true
  try {
    const { data } = await attendanceApi.logs(r.id)
    logItems.value = data
  } catch {
    logItems.value = []
  } finally {
    logLoading.value = false
  }
}
const hhmm = (t) => (t ? String(t).slice(0, 5) : '—')
const fmtAt = (iso) => (iso ? new Date(iso).toLocaleString('vi-VN') : '')

onMounted(() => {
  loadTeachers()
  load()
})

async function generate() {
  info.value = ''
  try {
    const { data } = await attendanceApi.generate(filter.from, filter.to)
    info.value = data.message ?? `Đã sinh ${data.created} dòng`
    load()
  } catch (e) {
    info.value = e.response?.data?.message ?? 'Sinh chấm công thất bại'
  }
}

function openEdit(r) {
  editModal.open = true
  editModal.saving = false
  editModal.error = ''
  editModal.id = r.id
  editModal.form = {
    teacherId: r.teacherId,
    scheduleId: r.scheduleId,
    workDate: r.workDate,
    checkIn: r.checkIn ? r.checkIn.slice(0, 5) : '',
    checkOut: r.checkOut ? r.checkOut.slice(0, 5) : '',
    status: r.status,
    note: r.note ?? '',
  }
}

async function saveEdit() {
  editModal.saving = true
  editModal.error = ''
  try {
    await attendanceApi.update(editModal.id, {
      teacherId: editModal.form.teacherId,
      scheduleId: editModal.form.scheduleId,
      workDate: editModal.form.workDate,
      checkIn: editModal.form.checkIn || null,
      checkOut: editModal.form.checkOut || null,
      status: editModal.form.status,
      note: editModal.form.note || null,
    })
    editModal.open = false
    load()
  } catch (e) {
    editModal.error = e.response?.data?.message ?? 'Lưu thất bại'
  } finally {
    editModal.saving = false
  }
}

const totalHours = computed(() =>
  rows.value.reduce((sum, r) => sum + Number(r.hours || 0), 0).toFixed(2),
)
const presentCount = computed(
  () => rows.value.filter((r) => r.status === 'PRESENT' || r.status === 'LATE').length,
)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="title">Chấm công</h2>
      </div>
      <button class="btn btn-primary" @click="generate">⟳ Sinh từ lịch dạy</button>
    </div>

    <!-- Thẻ tổng quan -->
    <div class="stats">
      <div class="stat card">
        <span class="stat-label">Tổng dòng</span>
        <span class="stat-value">{{ rows.length }}</span>
      </div>
      <div class="stat card">
        <span class="stat-label">Buổi có công</span>
        <span class="stat-value">{{ presentCount }}</span>
      </div>
      <div class="stat card">
        <span class="stat-label">Tổng giờ dạy</span>
        <span class="stat-value">{{ totalHours }}h</span>
      </div>
    </div>

    <!-- Bộ lọc -->
    <div class="toolbar">
      <label>Từ</label>
      <DateField v-model="filter.from" class="df--inline" />
      <label>Đến</label>
      <DateField v-model="filter.to" class="df--inline" />
      <label>Giáo viên</label>
      <select v-model="filter.teacherId">
        <option value="">Tất cả</option>
        <option v-for="t in teachers" :key="t.id" :value="t.id">{{ t.name }}</option>
      </select>
      <button class="btn btn-outline btn-sm" @click="load">Lọc</button>
      <span v-if="info" class="info-text">{{ info }}</span>
    </div>

    <!-- Cần xử lý: gom sẵn dòng bất thường thay vì bắt kế toán dò cả bảng bằng mắt -->
    <section v-if="attention.length" class="attn card">
      <button class="attn__head" @click="attnOpen = !attnOpen">
        <strong>Cần xử lý</strong>
        <span class="attn__count">{{ attention.length }}</span>
        <span class="attn__toggle">{{ attnOpen ? 'Thu gọn' : 'Xem' }}</span>
      </button>
      <ul v-if="attnOpen" class="attn__list">
        <li v-for="r in attention" :key="r.id">
          <span class="mono">{{ r.workDate }}</span>
          <span class="font-medium">{{ r.teacherName }}</span>
          <span class="attn__why">{{ attentionReason(r) }}</span>
          <button class="attn__link" @click="openLogs(r)">Nhật ký</button>
        </li>
      </ul>
    </section>

    <div class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>Ngày</th>
            <th>Giáo viên</th>
            <th>Vào</th>
            <th>Ra</th>
            <th>Giờ</th>
            <th>Trạng thái</th>
            <th>Nguồn</th>
            <th>Ghi chú</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="9" class="text-center text-muted">Đang tải…</td>
          </tr>
          <tr v-else-if="!rows.length">
            <td colspan="9" class="text-center text-muted">
              Chưa có dữ liệu — bấm “Sinh từ lịch dạy” để tạo từ các buổi đã duyệt.
            </td>
          </tr>
          <tr v-for="r in pagedRows" :key="r.id">
            <td class="mono">{{ r.workDate }}</td>
            <td class="font-medium">{{ r.teacherName }}</td>
            <td class="mono">{{ r.checkIn ? r.checkIn.slice(0, 5) : '—' }}</td>
            <td class="mono">{{ r.checkOut ? r.checkOut.slice(0, 5) : '—' }}</td>
            <td class="mono">{{ Number(r.hours).toFixed(2) }}</td>
            <td>
              <span class="badge" :class="statusMeta(r.status).cls">{{
                statusMeta(r.status).label
              }}</span>
            </td>
            <td>
              <span
                class="badge"
                :class="r.checkInMethod === 'SELF' ? 'badge-green' : 'badge-gray'"
              >
                {{ methodLabel(r.checkInMethod) }}
              </span>
            </td>
            <td class="text-muted small">{{ r.note ?? '—' }}</td>
            <td class="actions">
              <button class="btn btn-sm btn-outline" @click="openEdit(r)">Sửa</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <Pagination v-model="page" :total-pages="totalPages" />

    <!-- Modal sửa -->
    <div v-if="editModal.open" class="modal-overlay" @click.self="editModal.open = false">
      <div class="modal-box">
        <h3>Sửa chấm công — {{ editModal.form.workDate }}</h3>
        <div class="grid2">
          <div class="form-group">
            <label>Giờ vào</label>
            <input type="time" v-model="editModal.form.checkIn" />
          </div>
          <div class="form-group">
            <label>Giờ ra</label>
            <input type="time" v-model="editModal.form.checkOut" />
          </div>
        </div>
        <div class="form-group">
          <label>Trạng thái</label>
          <select v-model="editModal.form.status">
            <option v-for="s in STATUSES" :key="s.code" :value="s.code">{{ s.label }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>Ghi chú</label>
          <textarea v-model="editModal.form.note" rows="2" />
        </div>
        <p v-if="editModal.error" class="error-msg">{{ editModal.error }}</p>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="editModal.open = false">Hủy</button>
          <button class="btn btn-primary" :disabled="editModal.saving" @click="saveEdit">
            {{ editModal.saving ? 'Đang lưu…' : 'Lưu' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Nhật ký thay đổi: chấm công ra tiền lương nên phải truy được ai sửa, sửa gì -->
    <div v-if="logRow" class="modal-overlay" @click.self="logRow = null">
      <div class="modal-box modal-lg">
        <h3>Nhật ký chấm công — {{ logRow.teacherName }} · {{ logRow.workDate }}</h3>
        <p v-if="logLoading" class="text-muted small">Đang tải…</p>
        <p v-else-if="!logItems.length" class="text-muted small">Chưa có thay đổi nào.</p>
        <ul v-else class="log">
          <li v-for="l in logItems" :key="l.id">
            <div class="log__top">
              <strong>{{ l.actionLabel }}</strong>
              <span class="text-muted small">{{ l.changedByName }} · {{ fmtAt(l.changedAt) }}</span>
            </div>
            <div class="log__diff">
              <span v-if="l.oldStatus !== l.newStatus">
                Trạng thái: {{ l.oldStatus ?? '—' }} → <b>{{ l.newStatus ?? '—' }}</b>
              </span>
              <span v-if="hhmm(l.oldCheckIn) !== hhmm(l.newCheckIn)">
                Vào: {{ hhmm(l.oldCheckIn) }} → <b>{{ hhmm(l.newCheckIn) }}</b>
              </span>
              <span v-if="hhmm(l.oldCheckOut) !== hhmm(l.newCheckOut)">
                Ra: {{ hhmm(l.oldCheckOut) }} → <b>{{ hhmm(l.newCheckOut) }}</b>
              </span>
            </div>
          </li>
        </ul>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="logRow = null">Đóng</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ── Cần xử lý ── */
.attn {
  margin-bottom: 1rem;
  padding: 0.6rem 0.9rem;
  border-left: 3px solid var(--c-amber);
}
.attn__head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  border: 0;
  background: none;
  color: var(--c-text);
  font-size: 0.9rem;
  cursor: pointer;
}
.attn__count {
  min-width: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--c-amber);
  color: #fff;
  font-size: 0.75rem;
  font-weight: 700;
}
.attn__toggle {
  margin-left: auto;
  color: var(--c-primary);
  font-size: 0.8rem;
}
.attn__list {
  list-style: none;
  margin: 0.6rem 0 0;
  padding: 0;
  display: grid;
  gap: 0.35rem;
}
.attn__list li {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
  font-size: 0.83rem;
}
.attn__why {
  color: var(--c-text-muted);
}
.attn__link {
  margin-left: auto;
  border: 0;
  background: none;
  color: var(--c-primary);
  font-size: 0.8rem;
  cursor: pointer;
}

/* ── Nhật ký ── */
.log {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.5rem;
  max-height: 50vh;
  overflow-y: auto;
}
.log li {
  padding: 0.5rem 0.7rem;
  border: 1px solid var(--c-border);
  border-radius: 8px;
}
.log__top {
  display: flex;
  justify-content: space-between;
  gap: 0.6rem;
  flex-wrap: wrap;
  font-size: 0.86rem;
}
.log__diff {
  display: flex;
  flex-wrap: wrap;
  gap: 0.2rem 0.9rem;
  margin-top: 0.25rem;
  font-size: 0.8rem;
  color: var(--c-text-muted);
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin-bottom: 1.2rem;
}
.stat {
  padding: 1rem 1.2rem;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}
.stat-label {
  font-size: 0.8rem;
  color: var(--c-text-muted);
  font-weight: 600;
}
.stat-value {
  font-size: 1.5rem;
  font-weight: 800;
  color: var(--c-text);
}
.info-text {
  font-size: 0.82rem;
  color: var(--c-accent);
  margin-left: auto;
}
@media (max-width: 640px) {
  .stats {
    grid-template-columns: 1fr;
  }
}
</style>
