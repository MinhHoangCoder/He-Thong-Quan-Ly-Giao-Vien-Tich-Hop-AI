<script setup>
/**
 * Trang "Bảng chấm công" của GIÁO VIÊN.
 *
 * <p>Dữ liệu sinh ra từ chính việc giáo viên bấm Check in/out ở panel phía trên — bảng
 * bên dưới là bản ghi lại, KHÔNG sửa được.
 *
 * <p>Buổi đã lỡ (cửa sổ chấm đã đóng) thì bấm "Xin bổ sung" để gửi yêu cầu cho admin
 * duyệt; đó là đường duy nhất để buổi đó được ghi công.
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { attendanceApi, attendanceAmendApi } from '@/api/attendance'
import { tietLabel } from '@/utils/period'
import Pagination from '@/components/ui/Pagination.vue'
import CheckinPanel from '@/components/CheckinPanel.vue'
import DateField from '@/components/ui/DateField.vue'

const STATUSES = [
  { code: 'PRESENT', label: 'Có mặt', cls: 'badge-green' },
  { code: 'LATE', label: 'Đi muộn', cls: 'badge-amber' },
  { code: 'LEAVE', label: 'Nghỉ phép', cls: 'badge-gray' },
  { code: 'ABSENT', label: 'Vắng', cls: 'badge-red' },
]
const statusMeta = (code) =>
  STATUSES.find((s) => s.code === code) ?? { label: code, cls: 'badge-gray' }

/** Nguồn ghi nhận: buổi GV tự bấm Check in (SELF) khác buổi hệ thống/kế toán ghi hộ. */
const METHOD_LABELS = {
  SELF: 'Tự chấm',
  EMPLOYEE: 'Admin ghi',
  SCHOOL: 'Trường',
  DEVICE: 'Thiết bị',
  SYSTEM: 'Hệ thống',
}
const methodLabel = (m) => METHOD_LABELS[m] || '—'

// Format ngày theo GIỜ ĐỊA PHƯƠNG (yyyy-MM-dd). KHÔNG dùng toISOString() vì nó quy về
// UTC → ở múi giờ VN (UTC+7) mốc 00:00 bị lùi 1 ngày, làm khoảng lọc mặc định lệch:
// lọt ngày cuối tháng trước và thiếu ngày cuối tháng này. (Trang Lịch dạy đã dùng cách này.)
const isoLocal = (d) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const today = new Date()
const firstOfMonth = isoLocal(new Date(today.getFullYear(), today.getMonth(), 1))
const lastOfMonth = isoLocal(new Date(today.getFullYear(), today.getMonth() + 1, 0))

const filter = reactive({ from: firstOfMonth, to: lastOfMonth, status: '' })
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

/* ── Nhãn thứ trong tuần (CN, Th 2..Th 7) từ ngày làm việc ── */
const hhmm = (t) => (t ? String(t).slice(0, 5) : '—')

const WEEKDAYS = ['CN', 'Th 2', 'Th 3', 'Th 4', 'Th 5', 'Th 6', 'Th 7']
function weekdayLabel(iso) {
  if (!iso) return ''
  const d = new Date(iso + 'T00:00:00')
  return WEEKDAYS[d.getDay()] ?? ''
}

async function load() {
  loading.value = true
  info.value = ''
  page.value = 0
  try {
    const { data } = await attendanceApi.mine({
      from: filter.from,
      to: filter.to,
      status: filter.status || undefined,
    })
    rows.value = data
  } catch (e) {
    rows.value = []
    info.value = e.response?.data?.message ?? 'Không tải được dữ liệu'
  } finally {
    loading.value = false
  }
}

/* ══════════ Xin bổ sung chấm công ══════════ */

const AMEND_STATUSES = {
  PENDING: { label: 'Chờ duyệt', cls: 'badge-amber' },
  APPROVED: { label: 'Đã duyệt', cls: 'badge-green' },
  REJECTED: { label: 'Từ chối', cls: 'badge-red' },
}
const amendMeta = (s) => AMEND_STATUSES[s] ?? { label: s, cls: 'badge-gray' }

const myAmends = ref([])
const amendModal = reactive({
  open: false,
  saving: false,
  error: '',
  session: null,
  reason: '',
  checkIn: '',
  checkOut: '',
})

async function loadMyAmends() {
  try {
    const { data } = await attendanceAmendApi.mine()
    myAmends.value = data
  } catch {
    myAmends.value = []
  }
}

/** Mở form từ một buổi đã lỡ — điền sẵn giờ tiết để GV chỉ phải sửa nếu khác thực tế. */
function openAmend(session) {
  amendModal.open = true
  amendModal.saving = false
  amendModal.error = ''
  amendModal.session = session
  amendModal.reason = ''
  amendModal.checkIn = String(session.startTime).slice(11, 16)
  amendModal.checkOut = String(session.endTime).slice(11, 16)
}

/* ── Xin bổ sung cho buổi các NGÀY TRƯỚC ──
 * Panel phía trên chỉ liệt kê buổi hôm nay, nên buổi lỡ hôm kia không có lối vào nào —
 * dù API vẫn cho xin trong 7 ngày. Nút ở đây là lối vào đó.
 */
const AMEND_WINDOW_DAYS = 7

/** Dòng này còn xin bổ sung được không: đang Vắng, có buổi dạy, và chưa quá hạn. */
function canAmend(r) {
  if (r.status !== 'ABSENT' || !r.scheduleId || !r.sessionStart) return false
  if (myAmends.value.some((a) => a.scheduleId === r.scheduleId && a.status === 'PENDING'))
    return false
  const days = (new Date(today.toDateString()) - new Date(r.workDate + 'T00:00:00')) / 86400000
  return days >= 0 && days <= AMEND_WINDOW_DAYS
}

/** Dòng chấm công dùng chung form với buổi hôm nay — nắn về đúng hình dạng session. */
function openAmendFromRow(r) {
  openAmend({
    scheduleId: r.scheduleId,
    startTime: r.sessionStart,
    endTime: r.sessionEnd,
    schoolName: r.schoolName,
    className: r.className,
    subjectName: r.subjectName,
  })
}

async function submitAmend() {
  if (!amendModal.reason.trim()) {
    amendModal.error = 'Vui lòng nhập lý do'
    return
  }
  amendModal.saving = true
  amendModal.error = ''
  try {
    await attendanceAmendApi.create({
      scheduleId: amendModal.session.scheduleId,
      reason: amendModal.reason.trim(),
      proposedCheckIn: amendModal.checkIn,
      proposedCheckOut: amendModal.checkOut,
    })
    amendModal.open = false
    info.value = 'Đã gửi yêu cầu bổ sung — chờ admin duyệt.'
    loadMyAmends()
  } catch (e) {
    amendModal.error = e.response?.data?.message ?? 'Gửi yêu cầu thất bại'
  } finally {
    amendModal.saving = false
  }
}

onMounted(() => {
  load()
  loadMyAmends()
})

/* ── Thẻ thống kê (tính trên toàn bộ kết quả đang lọc) ── */
const totalRows = computed(() => rows.value.length)
const workedCount = computed(
  () => rows.value.filter((r) => r.status === 'PRESENT' || r.status === 'LATE').length,
)
const lateCount = computed(() => rows.value.filter((r) => r.status === 'LATE').length)
const absentCount = computed(() => rows.value.filter((r) => r.status === 'ABSENT').length)
const totalHours = computed(() =>
  rows.value.reduce((sum, r) => sum + Number(r.hours || 0), 0).toFixed(2),
)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="title">Bảng chấm công</h2>
      </div>
    </div>

    <!-- Thẻ tổng quan -->
    <div class="stats">
      <div class="stat card">
        <span class="stat-label">Tổng buổi</span>
        <span class="stat-value">{{ totalRows }}</span>
      </div>
      <div class="stat card">
        <span class="stat-label">Buổi có công</span>
        <span class="stat-value">{{ workedCount }}</span>
      </div>
      <div class="stat card">
        <span class="stat-label">Đi muộn</span>
        <span class="stat-value">{{ lateCount }}</span>
      </div>
      <div class="stat card">
        <span class="stat-label">Vắng</span>
        <span class="stat-value">{{ absentCount }}</span>
      </div>
      <div class="stat card">
        <span class="stat-label">Tổng giờ dạy</span>
        <span class="stat-value">{{ totalHours }}h</span>
      </div>
    </div>

    <!-- Chấm công hôm nay: bấm ngay tại trang Chấm công, không phải quay về Trang chủ -->
    <CheckinPanel @changed="load" @amend="openAmend" />

    <!-- Yêu cầu bổ sung đã gửi: GV phải theo dõi được admin đã xử lý tới đâu -->
    <section v-if="myAmends.length" class="card amend">
      <h3 class="amend__title">Yêu cầu bổ sung của tôi</h3>
      <ul class="amend__list">
        <li v-for="a in myAmends" :key="a.id">
          <span class="mono">{{ a.workDate ?? '—' }}</span>
          <span class="mono">{{ hhmm(a.proposedCheckIn) }}–{{ hhmm(a.proposedCheckOut) }}</span>
          <span class="amend__reason">{{ a.reason }}</span>
          <span class="badge" :class="amendMeta(a.status).cls">{{
            amendMeta(a.status).label
          }}</span>
          <span v-if="a.reviewNote" class="amend__note">{{ a.reviewNote }}</span>
        </li>
      </ul>
    </section>

    <!-- Bộ lọc -->
    <div class="toolbar">
      <label>Từ</label>
      <DateField v-model="filter.from" class="df--inline" />
      <label>Đến</label>
      <DateField v-model="filter.to" class="df--inline" />
      <label>Trạng thái</label>
      <select v-model="filter.status">
        <option value="">Tất cả</option>
        <option v-for="s in STATUSES" :key="s.code" :value="s.code">{{ s.label }}</option>
      </select>
      <button class="btn btn-outline btn-sm" @click="load">Lọc</button>
      <span v-if="info" class="info-text">{{ info }}</span>
    </div>

    <div class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>Ngày</th>
            <th>Thứ</th>
            <th>Buổi · Tiết</th>
            <th>Trường</th>
            <th>Lớp</th>
            <th>Môn</th>
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
            <td colspan="13" class="text-center text-muted">Đang tải…</td>
          </tr>
          <tr v-else-if="!rows.length">
            <td colspan="13" class="text-center text-muted">
              Chưa có dữ liệu chấm công cho kỳ này.
            </td>
          </tr>
          <tr v-for="r in pagedRows" :key="r.id">
            <td class="mono">{{ r.workDate }}</td>
            <td>{{ weekdayLabel(r.workDate) }}</td>
            <td>{{ tietLabel(r.periodNumber, r.sessionType, r.indexInSession) || '—' }}</td>
            <td>{{ r.schoolName ?? '—' }}</td>
            <td>{{ r.className ? 'Lớp ' + r.className : '—' }}</td>
            <td class="font-medium">{{ r.subjectName ?? '—' }}</td>
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
            <td>
              <button
                v-if="canAmend(r)"
                class="btn btn-sm btn-outline"
                @click="openAmendFromRow(r)"
              >
                Xin bổ sung
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <Pagination v-model="page" :total-pages="totalPages" />

    <!-- Form xin bổ sung: giờ điền sẵn theo khung tiết, GV sửa lại nếu dạy khác giờ -->
    <div v-if="amendModal.open" class="modal-overlay" @click.self="amendModal.open = false">
      <div class="modal-box">
        <h3>Xin bổ sung chấm công</h3>
        <p class="text-muted small">
          {{ amendModal.session?.schoolName }}
          <template v-if="amendModal.session?.className">
            · Lớp {{ amendModal.session.className }}</template
          >
          <template v-if="amendModal.session?.subjectName">
            · {{ amendModal.session.subjectName }}</template
          >
        </p>
        <div class="grid2">
          <div class="form-group">
            <label>Giờ vào</label>
            <input v-model="amendModal.checkIn" type="time" />
          </div>
          <div class="form-group">
            <label>Giờ ra</label>
            <input v-model="amendModal.checkOut" type="time" />
          </div>
        </div>
        <div class="form-group">
          <label>Lý do <span class="req">*</span></label>
          <textarea
            v-model="amendModal.reason"
            rows="3"
            placeholder="Vì sao bạn không bấm được đúng lúc? Admin đọc nội dung này để duyệt."
          />
        </div>
        <p v-if="amendModal.error" class="error-msg">{{ amendModal.error }}</p>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="amendModal.open = false">Hủy</button>
          <button class="btn btn-primary" :disabled="amendModal.saving" @click="submitAmend">
            {{ amendModal.saving ? 'Đang gửi…' : 'Gửi yêu cầu' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ── Yêu cầu bổ sung của tôi ── */
.amend {
  padding: 0.9rem 1.1rem;
  margin-bottom: 1rem;
}
.amend__title {
  margin: 0 0 0.6rem;
  font-size: 0.95rem;
  font-weight: 700;
}
.amend__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.4rem;
}
.amend__list li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
  font-size: 0.84rem;
}
.amend__reason {
  flex: 1;
  min-width: 10rem;
  color: var(--c-text-muted);
}
.amend__note {
  width: 100%;
  color: var(--c-text-muted);
  font-style: italic;
  font-size: 0.8rem;
}
.req {
  color: var(--c-danger);
}

.stats {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
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
@media (max-width: 960px) {
  .stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 640px) {
  .stats {
    grid-template-columns: 1fr;
  }
}
</style>
