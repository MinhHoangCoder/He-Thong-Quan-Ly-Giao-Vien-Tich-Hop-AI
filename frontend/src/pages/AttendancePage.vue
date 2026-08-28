<script setup>
/**
 * Trang Chấm công của admin/kế toán — BA TAB:
 *
 *   Bảng chấm công · xem theo khoảng ngày, sửa từng dòng (bắt buộc ghi lý do)
 *   Hôm nay        · mọi buổi dạy trong ngày + buổi nào đã chấm, buổi nào chưa
 *   Yêu cầu bổ sung· giáo viên lỡ buổi xin ghi công, duyệt hoặc từ chối tại đây
 *
 * KHÔNG còn nút "Sinh từ lịch dạy": công chỉ sinh ra từ việc giáo viên tự chấm, sinh
 * hàng loạt là tạo công cho buổi chưa diễn ra.
 *
 * Tab "Hôm nay" đọc từ LỊCH DẠY chứ không từ bảng chấm công — buổi chưa ai chấm thì
 * chưa có dòng nào, nhìn bảng chấm công sẽ không thấy nó tồn tại.
 */
import { ref, reactive, onMounted, watch } from 'vue'
import { attendanceApi, attendanceAmendApi } from '@/api/attendance'
import { assignmentApi } from '@/api/assignments'
import Pagination from '@/components/ui/Pagination.vue'
import DateField from '@/components/ui/DateField.vue'
import { tietLabel } from '@/utils/period'
import SearchSelect from '@/components/ui/SearchSelect.vue'
import { useToast } from '@/composables/useToast'
import { taiFile, loiTaiFile } from '@/utils/download'

const { showToast } = useToast()

/**
 * Xuất theo ĐÚNG bộ lọc đang dùng, lấy trọn khoảng ngày chứ không lấy trang đang xem.
 *
 * Màn này phân trang ở server 10 dòng một trang; dựng file từ đó ra được đúng 10 dòng — một
 * cái file trông có vẻ đúng mà thiếu gần hết dữ liệu. Server vì thế tự lấy trọn khoảng, và
 * chặn lại nếu khoảng quá rộng thay vì im lặng cắt bớt.
 */
const dangXuat = ref(false)

async function xuatExcel() {
  dangXuat.value = true
  try {
    await taiFile(
      '/attendance/export',
      {
        teacherId: filter.teacherId || undefined,
        from: filter.from,
        to: filter.to,
        status: filter.status || undefined,
        keyword: filter.keyword.trim() || undefined,
      },
      `cham-cong_${filter.from}_${filter.to}.xlsx`,
    )
  } catch (e) {
    showToast(await loiTaiFile(e, 'Không xuất được bảng chấm công'), 'error')
  } finally {
    dangXuat.value = false
  }
}

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

const filter = reactive({
  from: firstOfMonth,
  to: lastOfMonth,
  teacherId: '',
  status: '',
  keyword: '',
})
const teachers = ref([])
const rows = ref([])
const loading = ref(false)
const info = ref('')

/* ── Phân trang phía SERVER ──
   Trước đây tải hết một tháng rồi mới cắt 10 dòng ở trình duyệt: 101 giáo viên × một tháng
   là hơn nghìn dòng JSON mỗi lần mở trang, để hiện đúng 10 dòng đầu. Ô tìm và bộ lọc trạng
   thái vì thế cũng phải chạy ở server — lọc ở client trên dữ liệu đã cắt trang thì chỉ tìm
   được trong trang đang xem. */
const PAGE_SIZE = 10
const page = ref(0)
const totalPages = ref(1)
const totalRows = ref(0)

const editModal = reactive({ open: false, saving: false, error: '', id: null, form: {} })

async function loadTeachers() {
  try {
    const { data } = await assignmentApi.options()
    teachers.value = data.teachers
  } catch {
    teachers.value = []
  }
}

/** Đổi bộ lọc là đổi hẳn tập dữ liệu → về trang 1, nếu không sẽ rơi vào trang trống. */
function applyFilter() {
  page.value = 0
  load()
}

async function load() {
  loading.value = true
  info.value = ''
  try {
    const { data } = await attendanceApi.list({
      teacherId: filter.teacherId || undefined,
      from: filter.from,
      to: filter.to,
      status: filter.status || undefined,
      keyword: filter.keyword.trim() || undefined,
      page: page.value,
      size: PAGE_SIZE,
    })
    rows.value = data.content ?? []
    totalPages.value = Math.max(1, data.totalPages ?? 1)
    totalRows.value = data.totalElements ?? 0
  } catch (e) {
    rows.value = []
    totalPages.value = 1
    totalRows.value = 0
    info.value = e.response?.data?.message ?? 'Không tải được dữ liệu'
  } finally {
    loading.value = false
  }
  loadAttention()
  loadSummary()
}

// Bấm số trang → nạp lại từ server (khác bản cũ: cắt sẵn trong mảng đã tải).
watch(page, load)

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
/** Giờ từ chuỗi LocalDateTime "2026-08-09T07:00:00" — cắt tại chỗ, KHÔNG qua new Date()
 *  vì backend gửi giờ tường Việt Nam không kèm múi giờ, parse ra là lệch. */
const hhmmOf = (dt) => (dt ? String(dt).slice(11, 16) : '—')
const fmtAt = (iso) => (iso ? new Date(iso).toLocaleString('vi-VN') : '')

onMounted(() => {
  loadTeachers()
  load()
  // Đếm yêu cầu chờ duyệt ngay từ đầu: nhãn tab phải báo có việc kể cả khi admin
  // không bao giờ bấm sang tab đó.
  refreshPendingCount()
})

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
    // Cố tình KHÔNG nạp lại lý do cũ: mỗi lần sửa là một lần giải trình mới.
    adjustReason: '',
  }
}

async function saveEdit() {
  // Chặn ngay ở client cho phản hồi tức thì; backend vẫn tự kiểm lại (@NotBlank).
  if (!editModal.form.adjustReason.trim()) {
    editModal.error = 'Vui lòng nhập lý do điều chỉnh'
    return
  }
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
      adjustReason: editModal.form.adjustReason.trim(),
    })
    editModal.open = false
    load()
  } catch (e) {
    editModal.error = e.response?.data?.message ?? 'Lưu thất bại'
  } finally {
    editModal.saving = false
  }
}

/* ══════════ Tab "Hôm nay" ══════════ */

const TAB_STATES = {
  NOT_YET: { label: 'Chưa tới giờ', cls: 'badge-gray' },
  OPEN: { label: 'Đang mở', cls: 'badge-amber' },
  CHECKED_IN: { label: 'Đang dạy', cls: 'badge-amber' },
  DONE: { label: 'Đã chấm', cls: 'badge-green' },
  MISSED: { label: 'Chưa ai chấm', cls: 'badge-red' },
  ABSENT: { label: 'Vắng / nghỉ', cls: 'badge-red' },
}
const stateMeta = (s) => TAB_STATES[s] ?? { label: s, cls: 'badge-gray' }

const todayDate = ref(isoLocal(today))
const todayData = ref(null)
const todayLoading = ref(false)

async function loadToday() {
  todayLoading.value = true
  try {
    const { data } = await attendanceApi.today(todayDate.value)
    todayData.value = data
  } catch {
    todayData.value = null
  } finally {
    todayLoading.value = false
  }
}

/* ══════════ Tab "Yêu cầu bổ sung" ══════════ */

const AMEND_STATUSES = {
  PENDING: { label: 'Chờ duyệt', cls: 'badge-amber' },
  APPROVED: { label: 'Đã duyệt', cls: 'badge-green' },
  REJECTED: { label: 'Từ chối', cls: 'badge-red' },
}
const amendMeta = (s) => AMEND_STATUSES[s] ?? { label: s, cls: 'badge-gray' }

const amendFilter = ref('PENDING')
const amendRows = ref([])
const amendLoading = ref(false)
const amendBusyId = ref(null)
const amendInfo = ref('')

/** Số yêu cầu đang chờ — hiện lên nhãn tab để admin biết có việc mà không phải mở ra xem. */
const pendingCount = ref(0)

async function loadAmend() {
  amendLoading.value = true
  amendInfo.value = ''
  try {
    const { data } = await attendanceAmendApi.list(amendFilter.value)
    amendRows.value = data
  } catch (e) {
    amendRows.value = []
    amendInfo.value = e.response?.data?.message ?? 'Không tải được danh sách yêu cầu'
  } finally {
    amendLoading.value = false
  }
  refreshPendingCount()
}

async function refreshPendingCount() {
  try {
    const { data } = await attendanceAmendApi.list('PENDING')
    pendingCount.value = data.length
  } catch {
    pendingCount.value = 0
  }
}

/**
 * Duyệt một yêu cầu bổ sung.
 *
 * @param status bỏ trống = ghi CÔNG (backend tự suy Có mặt/Đi muộn theo giờ giáo viên khai);
 *   'LEAVE' = ghi NGHỈ PHÉP.
 *
 * Vì sao cần nhánh Nghỉ phép: job nền ghi VẮNG cho mọi buổi hết giờ mà không ai check-in,
 * nên giáo viên nằm viện bị đánh y hệt người bỏ dạy — cả hai đều mất tiền buổi đó. Đây là
 * đường để kế toán phân biệt hai trường hợp, và luồng xin/duyệt đã có sẵn nên không cần
 * dựng thêm module đơn xin nghỉ riêng.
 */
async function approveAmend(r, status) {
  if (amendBusyId.value) return
  amendBusyId.value = r.id
  try {
    await attendanceAmendApi.approve(r.id, status ? { status } : {})
    showToast(
      status === 'LEAVE'
        ? `Đã ghi Nghỉ phép cho ${r.teacherName}`
        : `Đã duyệt yêu cầu của ${r.teacherName}`,
    )
    await loadAmend()
    load()
  } catch (e) {
    showToast(e.response?.data?.message ?? 'Duyệt thất bại', 'error')
  } finally {
    amendBusyId.value = null
  }
}

const rejectModal = reactive({ open: false, saving: false, error: '', row: null, note: '' })

function openReject(r) {
  rejectModal.open = true
  rejectModal.saving = false
  rejectModal.error = ''
  rejectModal.row = r
  rejectModal.note = ''
}

async function confirmReject() {
  if (!rejectModal.note.trim()) {
    rejectModal.error = 'Vui lòng nhập lý do từ chối'
    return
  }
  rejectModal.saving = true
  rejectModal.error = ''
  try {
    await attendanceAmendApi.reject(rejectModal.row.id, { reviewNote: rejectModal.note.trim() })
    rejectModal.open = false
    amendInfo.value = `Đã từ chối yêu cầu của ${rejectModal.row.teacherName}`
    loadAmend()
  } catch (e) {
    rejectModal.error = e.response?.data?.message ?? 'Từ chối thất bại'
  } finally {
    rejectModal.saving = false
  }
}

/* ══════════ Điều hướng tab ══════════ */

const tab = ref('table')

function switchTab(name) {
  tab.value = name
  if (name === 'today' && !todayData.value) loadToday()
  if (name === 'amend' && !amendRows.value.length) loadAmend()
}

/* Ba thẻ tổng quan lấy từ server: bảng nay phân trang nên cộng dồn rows.value chỉ ra
   tổng của 10 dòng đang hiện — một con số vô nghĩa nằm cạnh nhãn "Tổng giờ dạy". */
const summary = ref({ totalRows: 0, presentRows: 0, totalHours: 0 })

async function loadSummary() {
  try {
    const { data } = await attendanceApi.summary({
      teacherId: filter.teacherId || undefined,
      from: filter.from,
      to: filter.to,
      status: filter.status || undefined,
      keyword: filter.keyword.trim() || undefined,
    })
    summary.value = data
  } catch {
    summary.value = { totalRows: 0, presentRows: 0, totalHours: 0 }
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="title">Chấm công</h2>
      </div>
    </div>

    <nav class="tabs">
      <button class="tab" :class="{ 'is-on': tab === 'table' }" @click="switchTab('table')">
        Bảng chấm công
      </button>
      <button class="tab" :class="{ 'is-on': tab === 'today' }" @click="switchTab('today')">
        Hôm nay
      </button>
      <button class="tab" :class="{ 'is-on': tab === 'amend' }" @click="switchTab('amend')">
        Yêu cầu bổ sung
        <span v-if="pendingCount" class="tab__badge">{{ pendingCount }}</span>
      </button>
    </nav>

    <template v-if="tab === 'table'">
      <!-- Thẻ tổng quan -->
      <div class="stats">
        <div class="stat card">
          <span class="stat-label">Tổng dòng</span>
          <span class="stat-value">{{ summary.totalRows }}</span>
        </div>
        <div class="stat card">
          <span class="stat-label">Buổi có công</span>
          <span class="stat-value">{{ summary.presentRows }}</span>
        </div>
        <div class="stat card">
          <span class="stat-label">Tổng giờ dạy</span>
          <span class="stat-value">{{ Number(summary.totalHours).toFixed(2) }}h</span>
        </div>
      </div>

      <!-- Bộ lọc -->
      <div class="toolbar">
        <label>Từ</label>
        <DateField v-model="filter.from" class="df--inline" />
        <label>Đến</label>
        <DateField v-model="filter.to" class="df--inline" />
        <label>Giáo viên</label>
        <SearchSelect
          v-model="filter.teacherId"
          :options="teachers"
          class="ss"
          placeholder="Tất cả"
          search-placeholder="Gõ tên giáo viên…"
          clearable
          @change="applyFilter"
        />
        <label>Trạng thái</label>
        <select v-model="filter.status" @change="applyFilter">
          <option value="">Tất cả</option>
          <option v-for="s in STATUSES" :key="s.code" :value="s.code">{{ s.label }}</option>
        </select>
      </div>

      <div class="toolbar">
        <input
          v-model="filter.keyword"
          class="search-input"
          type="search"
          placeholder="Tìm theo tên giáo viên…"
          @keyup.enter="applyFilter"
        />
        <button class="btn btn-outline btn-sm" @click="applyFilter">Lọc</button>
        <button class="btn btn-outline btn-sm" :disabled="dangXuat" @click="xuatExcel">
          {{ dangXuat ? 'Đang xuất…' : 'Xuất Excel' }}
        </button>
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
              <th>Buổi dạy</th>
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
              <td colspan="10" class="text-center text-muted">Đang tải…</td>
            </tr>
            <tr v-else-if="!rows.length">
              <td colspan="10" class="text-center text-muted">
                Không có dòng nào khớp bộ lọc — dòng chấm công chỉ xuất hiện khi giáo viên tự
                check-in.
              </td>
            </tr>
            <tr v-for="r in rows" :key="r.id">
              <td class="mono">{{ r.workDate }}</td>
              <td class="font-medium">{{ r.teacherName }}</td>
              <!-- Cột này trước đây KHÔNG có: kế toán nhìn "Nguyễn Văn A – 07:00 – Có mặt"
                   mà không biết buổi đó dạy ở đâu, trong khi chính giáo viên lại thấy đủ. -->
              <td class="text-muted small">
                <template v-if="r.schoolName">
                  {{ r.schoolName }}<template v-if="r.className"> · Lớp {{ r.className }}</template
                  ><template v-if="r.subjectName"> · {{ r.subjectName }}</template>
                  <div v-if="r.periodNumber" class="sess-period">
                    {{ tietLabel(r.periodNumber, r.sessionType, r.indexInSession) }}
                  </div>
                </template>
                <template v-else>—</template>
              </td>
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
    </template>

    <!-- Tab Hôm nay: đi từ LỊCH DẠY nên thấy được cả buổi chưa ai chấm -->
    <template v-else-if="tab === 'today'">
      <div class="toolbar">
        <label>Ngày</label>
        <DateField v-model="todayDate" class="df--inline" />
        <button class="btn btn-outline btn-sm" @click="loadToday">Xem</button>
      </div>

      <div v-if="todayData" class="stats">
        <div class="stat card">
          <span class="stat-label">Buổi trong ngày</span>
          <span class="stat-value">{{ todayData.summary.total }}</span>
        </div>
        <div class="stat card">
          <span class="stat-label">Đã chấm</span>
          <span class="stat-value">{{ todayData.summary.marked }}</span>
        </div>
        <div class="stat card">
          <span class="stat-label">Đi muộn</span>
          <span class="stat-value">{{ todayData.summary.late }}</span>
        </div>
        <div class="stat card">
          <span class="stat-label">Chưa ai chấm</span>
          <span class="stat-value">{{ todayData.summary.missing }}</span>
        </div>
      </div>

      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>Giờ</th>
              <th>Giáo viên</th>
              <th>Trường · Lớp · Môn</th>
              <th>Vào</th>
              <th>Ra</th>
              <th>Trạng thái</th>
              <th>Nguồn</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="todayLoading">
              <td colspan="7" class="text-center text-muted">Đang tải…</td>
            </tr>
            <tr v-else-if="!todayData || !todayData.sessions.length">
              <td colspan="7" class="text-center text-muted">Ngày này không có buổi dạy nào.</td>
            </tr>
            <tr v-for="s in todayData?.sessions ?? []" :key="s.scheduleId">
              <td class="mono">{{ hhmmOf(s.startTime) }}–{{ hhmmOf(s.endTime) }}</td>
              <td class="font-medium">{{ s.teacherName }}</td>
              <td class="text-muted small">
                {{ s.schoolName ?? '—'
                }}<template v-if="s.className"> · Lớp {{ s.className }}</template
                ><template v-if="s.subjectName"> · {{ s.subjectName }}</template>
              </td>
              <td class="mono">{{ hhmm(s.checkIn) }}</td>
              <td class="mono">{{ hhmm(s.checkOut) }}</td>
              <td>
                <span class="badge" :class="stateMeta(s.state).cls">{{
                  stateMeta(s.state).label
                }}</span>
              </td>
              <td class="text-muted small">{{ methodLabel(s.checkInMethod) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <!-- Tab Yêu cầu bổ sung -->
    <template v-else>
      <div class="toolbar">
        <label>Trạng thái</label>
        <select v-model="amendFilter" @change="loadAmend">
          <option value="PENDING">Chờ duyệt</option>
          <option value="APPROVED">Đã duyệt</option>
          <option value="REJECTED">Từ chối</option>
          <option value="">Tất cả</option>
        </select>
        <button class="btn btn-outline btn-sm" @click="loadAmend">Làm mới</button>
        <span v-if="amendInfo" class="info-text">{{ amendInfo }}</span>
      </div>

      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>Ngày dạy</th>
              <th>Giáo viên</th>
              <th>Buổi</th>
              <th>Giờ đề xuất</th>
              <th>Lý do</th>
              <th>Trạng thái</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="amendLoading">
              <td colspan="7" class="text-center text-muted">Đang tải…</td>
            </tr>
            <tr v-else-if="!amendRows.length">
              <td colspan="7" class="text-center text-muted">Không có yêu cầu nào.</td>
            </tr>
            <tr v-for="r in amendRows" :key="r.id">
              <td class="mono">{{ r.workDate ?? '—' }}</td>
              <td class="font-medium">{{ r.teacherName }}</td>
              <td class="text-muted small">
                {{ hhmmOf(r.sessionStart) }}–{{ hhmmOf(r.sessionEnd) }}
                <template v-if="r.schoolName"> · {{ r.schoolName }}</template>
                <template v-if="r.className"> · Lớp {{ r.className }}</template>
              </td>
              <td class="mono">{{ hhmm(r.proposedCheckIn) }}–{{ hhmm(r.proposedCheckOut) }}</td>
              <td class="text-muted small">
                {{ r.reason }}
                <div v-if="r.reviewNote" class="amend__note">Ghi chú: {{ r.reviewNote }}</div>
              </td>
              <td>
                <span class="badge" :class="amendMeta(r.status).cls">{{
                  amendMeta(r.status).label
                }}</span>
              </td>
              <td class="actions">
                <template v-if="r.status === 'PENDING'">
                  <button
                    class="btn btn-sm btn-primary"
                    :disabled="amendBusyId === r.id"
                    @click="approveAmend(r)"
                  >
                    Ghi công
                  </button>
                  <!-- Buổi giáo viên báo ốm / có phép: ghi Nghỉ phép thay vì Có mặt.
                       Nghỉ phép KHÔNG được tính tiết nên không phát sinh tiền, chỉ làm
                       sạch hồ sơ chuyên cần. -->
                  <button
                    class="btn btn-sm btn-outline"
                    :disabled="amendBusyId === r.id"
                    title="Buổi không diễn ra nhưng giáo viên có phép — không tính tiền"
                    @click="approveAmend(r, 'LEAVE')"
                  >
                    Nghỉ phép
                  </button>
                  <button
                    class="btn btn-sm btn-outline"
                    :disabled="amendBusyId === r.id"
                    @click="openReject(r)"
                  >
                    Từ chối
                  </button>
                </template>
                <span v-else class="text-muted small">{{ r.reviewedByName ?? '—' }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

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
        <div class="form-group">
          <label>Lý do điều chỉnh <span class="req">*</span></label>
          <textarea
            v-model="editModal.form.adjustReason"
            rows="2"
            placeholder="Vì sao phải sửa dòng này? Nội dung được lưu vào nhật ký."
          />
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

    <!-- Từ chối yêu cầu bổ sung: bắt buộc nói vì sao, giáo viên còn biết đường gửi lại -->
    <div v-if="rejectModal.open" class="modal-overlay" @click.self="rejectModal.open = false">
      <div class="modal-box">
        <h3>Từ chối yêu cầu — {{ rejectModal.row?.teacherName }}</h3>
        <p class="text-muted small">
          Buổi ngày {{ rejectModal.row?.workDate }}, giáo viên khai
          {{ hhmm(rejectModal.row?.proposedCheckIn) }}–{{ hhmm(rejectModal.row?.proposedCheckOut) }}
        </p>
        <div class="form-group">
          <label>Lý do từ chối <span class="req">*</span></label>
          <textarea
            v-model="rejectModal.note"
            rows="3"
            placeholder="Giáo viên sẽ nhận được nội dung này trong thông báo."
          />
        </div>
        <p v-if="rejectModal.error" class="error-msg">{{ rejectModal.error }}</p>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="rejectModal.open = false">Hủy</button>
          <button class="btn btn-primary" :disabled="rejectModal.saving" @click="confirmReject">
            {{ rejectModal.saving ? 'Đang gửi…' : 'Từ chối' }}
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
/* ── Tab ── */
.tabs {
  display: flex;
  gap: 0.35rem;
  border-bottom: 1px solid var(--c-border);
  margin-bottom: 1rem;
}
.tab {
  position: relative;
  padding: 0.55rem 0.95rem;
  border: 0;
  border-bottom: 2px solid transparent;
  background: none;
  color: var(--c-text-muted);
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
}
.tab.is-on {
  color: var(--c-primary);
  border-bottom-color: var(--c-primary);
}
.tab__badge {
  display: inline-block;
  min-width: 1.15rem;
  margin-left: 0.35rem;
  padding: 0.05rem 0.35rem;
  border-radius: 999px;
  background: var(--c-danger);
  color: #fff;
  font-size: 0.72rem;
  line-height: 1.4;
}
.req {
  color: var(--c-danger);
}
.amend__note {
  margin-top: 0.2rem;
  font-style: italic;
}

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

/* Nhãn tiết nằm dưới tên trường/lớp/môn trong cột "Buổi dạy". */
.sess-period {
  margin-top: 0.1rem;
  font-variant-numeric: tabular-nums;
}
</style>
