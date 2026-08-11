<script setup>
/**
 * Trang Phân công giảng dạy: danh sách phân công + tạo mới. Khi tạo, backend tự trải các
 * tiết thành buổi dạy (Schedule) — nguồn cho Chấm công & Bảng lương.
 *
 * <p>Mỗi TIẾT mang LỚP riêng: một phân công (GV + trường + môn) gồm nhiều dòng
 * "Thứ + Tiết + Lớp", nên sáng Thứ 2 có thể tiết 1 dạy 1A1, tiết 2 dạy 1A2, tiết 3 dạy
 * 1A3. Muốn dạy trường khác thì tạo phân công khác — khung tiết thuộc về từng trường.
 *
 * <p>Tiết nào GV đã bận sẽ bị KHÓA sẵn, so theo GIỜ THẬT nên bắt được cả trùng chéo
 * trường (tiết 1 trường A 07:00–07:35 đè tiết 1 trường B 07:00–07:45).
 */
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { assignmentApi } from '@/api/assignments'
import { tietLabel, tietShort } from '@/utils/period'
import Pagination from '@/components/ui/Pagination.vue'
import DateField from '@/components/ui/DateField.vue'

const DAYS = [
  { code: 'MON', label: 'Thứ 2' },
  { code: 'TUE', label: 'Thứ 3' },
  { code: 'WED', label: 'Thứ 4' },
  { code: 'THU', label: 'Thứ 5' },
  { code: 'FRI', label: 'Thứ 6' },
  { code: 'SAT', label: 'Thứ 7' },
  { code: 'SUN', label: 'Chủ nhật' },
]
const STATUS_LABEL = {
  PENDING: 'Chờ xác nhận',
  ACTIVE: 'Đang dạy',
  REJECTED: 'Bị từ chối',
  EXPIRED: 'Hết hạn',
  COMPLETED: 'Hoàn thành',
  CANCELLED: 'Đã hủy',
}
/* Tab lọc theo trạng thái — thứ tự theo mức độ cần xử lý, việc gấp nhất đứng trước. */
const STATUS_TABS = [
  { code: '', label: 'Tất cả' },
  { code: 'PENDING', label: 'Chờ xác nhận' },
  { code: 'EXPIRED', label: 'Hết hạn' },
  { code: 'REJECTED', label: 'Bị từ chối' },
  { code: 'ACTIVE', label: 'Đang dạy' },
  { code: 'CANCELLED', label: 'Đã hủy' },
]
/* Còn dưới ngần này tiếng là "sắp hết hạn" → tô vàng, nhắc admin xử lý trước khi phiếu chết. */
const DEADLINE_WARN_HOURS = 12

// Ngày hôm nay theo GIỜ ĐỊA PHƯƠNG (yyyy-MM-dd). Tránh toISOString() vì nó quy về UTC →
// ở múi giờ VN (UTC+7) lúc rạng sáng (00:00–07:00) sẽ trả nhầm về ngày hôm qua.
const isoToday = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const loading = ref(false)
const items = ref([])
const trashItems = ref([])
const statusCounts = ref({})

/* Tab trạng thái đang xem ('' = tất cả) + các phiếu đang tích chọn để thao tác hàng loạt. */
const statusFilter = ref('')
const selectedIds = ref([])

/* Chế độ xem: 'list' = danh sách | 'trash' = thùng rác. */
const view = ref('list')
const inTrash = computed(() => view.value === 'trash')

/* ── Tìm kiếm (chỉ ở danh sách đang hoạt động) — lọc phía server ──
   "Lọc ngay khi gõ" nhưng debounce 300ms để không gọi API dồn dập theo từng phím. */
const search = ref('')
let searchTimer = null
function onSearchInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(load, 300)
}
// Nút "Lọc" / Enter: tìm ngay (bỏ hàng đợi debounce đang chờ).
function applySearch() {
  clearTimeout(searchTimer)
  load()
}
// Nút "Xóa lọc": trả ô tìm về rỗng rồi tải lại toàn bộ danh sách.
function clearSearch() {
  if (!search.value) return
  search.value = ''
  clearTimeout(searchTimer)
  load()
}

/* ── Phân trang phía client (áp cho danh sách đang xem) ── */
const PAGE_SIZE = 10
const page = ref(0)
const currentList = computed(() => (inTrash.value ? trashItems.value : items.value))
const totalPages = computed(() => Math.ceil(currentList.value.length / PAGE_SIZE))
const pagedItems = computed(() => {
  const start = page.value * PAGE_SIZE
  return currentList.value.slice(start, start + PAGE_SIZE)
})

const options = reactive({ teachers: [], subjects: [], schools: [] })
const scoped = reactive({ classes: [], periods: [] })

// Giờ bận của GV đang chọn trên MỌI trường (mỗi dòng đã kèm khung giờ thật của tiết) —
// nguồn để khóa các tiết đè giờ. Khớp luật 409 ở backend.
const teacherBusy = ref([])

const modal = reactive({
  open: false,
  saving: false,
  error: '',
  /** null = tạo mới | id = đang SỬA phiếu đó (trường & môn khóa cứng, không đổi được). */
  editId: null,
  form: {
    teacherId: '',
    subjectId: '',
    schoolId: '',
    startDate: isoToday(),
    endDate: '',
    slots: [], // { dayOfWeek, periodId, classId }
  },
  slotDraft: { dayOfWeek: 'MON', periodId: '', classId: '' },
})

const cancelTarget = ref(null) // Hủy → đưa vào thùng rác
const purgeTarget = ref(null) // xóa vĩnh viễn khỏi thùng rác

async function load() {
  loading.value = true
  page.value = 0
  selectedIds.value = []
  try {
    const { data } = await assignmentApi.list({
      keyword: search.value,
      status: statusFilter.value,
    })
    items.value = data
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
  loadCounts()
}

/** Số phiếu từng trạng thái cho badge trên tab — tải kèm mỗi lần làm mới danh sách. */
async function loadCounts() {
  try {
    const { data } = await assignmentApi.statusCounts()
    statusCounts.value = data || {}
  } catch {
    statusCounts.value = {}
  }
}

function selectTab(code) {
  if (statusFilter.value === code) return
  statusFilter.value = code
  load()
}

async function loadTrash() {
  loading.value = true
  page.value = 0
  try {
    const { data } = await assignmentApi.trash()
    trashItems.value = data
  } catch {
    trashItems.value = []
  } finally {
    loading.value = false
  }
}

function showTrash() {
  view.value = 'trash'
  loadTrash()
}

function showList() {
  view.value = 'list'
  load()
}

onMounted(load)
onBeforeUnmount(() => clearTimeout(searchTimer))

async function openCreate() {
  Object.assign(modal.form, {
    teacherId: '',
    subjectId: '',
    schoolId: '',
    startDate: isoToday(),
    endDate: '',
    slots: [],
  })
  modal.slotDraft = { dayOfWeek: 'MON', periodId: '', classId: '' }
  modal.editId = null
  modal.error = ''
  travelWarn.value = '' // cảnh báo của phiếu trước không được dính sang phiếu mới
  modal.open = true
  scoped.classes = []
  scoped.periods = []
  teacherBusy.value = []
  await loadFormOptions()
}

/**
 * Sửa một phiếu CHƯA được xác nhận. Trường và môn khóa cứng (khung tiết + lớp gắn với trường,
 * đổi trường là phiếu khác hẳn); giáo viên thì ĐỔI được — phiếu bị từ chối cần xếp cho người
 * khác, mà giáo viên cũ vẫn để chọn lại phòng khi họ bấm nhầm nút Từ chối.
 */
async function openEdit(a) {
  Object.assign(modal.form, {
    teacherId: a.teacherId,
    subjectId: a.subjectId,
    schoolId: a.schoolId,
    startDate: a.startDate,
    endDate: a.endDate ?? '',
    slots: (a.slots ?? []).map((s) => ({
      dayOfWeek: s.dayOfWeek,
      periodId: Number(s.periodId),
      classId: Number(s.classId),
    })),
  })
  modal.slotDraft = { dayOfWeek: 'MON', periodId: '', classId: '' }
  modal.editId = a.id
  modal.error = ''
  travelWarn.value = '' // cảnh báo của phiếu trước không được dính sang phiếu mới
  modal.open = true
  await loadFormOptions()
  try {
    const { data } = await assignmentApi.schoolOptions(a.schoolId)
    scoped.classes = data.classes
    scoped.periods = data.periods
    modal.slotDraft.classId = scoped.classes.length ? scoped.classes[0].id : ''
  } catch {
    /* giữ trống */
  }
  await onTeacherChange()
  pickFirstFreePeriod()
}

async function loadFormOptions() {
  try {
    const { data } = await assignmentApi.options()
    options.teachers = data.teachers
    options.subjects = data.subjects
    options.schools = data.schools
  } catch (e) {
    modal.error = 'Không tải được dữ liệu form: ' + (e.response?.data?.message ?? e.message)
  }
}

async function onSchoolChange() {
  // Đổi trường thì cả lớp lẫn khung tiết đều khác → bỏ hết các dòng đã thêm.
  modal.form.slots = []
  modal.slotDraft.classId = ''
  scoped.classes = []
  scoped.periods = []
  if (!modal.form.schoolId) return
  try {
    const { data } = await assignmentApi.schoolOptions(modal.form.schoolId)
    scoped.classes = data.classes
    scoped.periods = data.periods
    modal.slotDraft.classId = scoped.classes.length ? scoped.classes[0].id : ''
    pickFirstFreePeriod()
  } catch {
    /* giữ trống */
  }
}

/* ── Khóa tiết GV đã bận ──
   Nạp bảng "giờ bận" của GV (mọi trường, kèm khung giờ thật) khi đổi Giáo viên, rồi
   disable những tiết ĐÈ GIỜ với nó. Phải so theo giờ chứ không theo periodId: mỗi trường
   một bộ khung tiết riêng nên "tiết 1" ở hai trường là hai id khác nhau mà giờ vẫn trùng. */
async function onTeacherChange() {
  teacherBusy.value = []
  const tid = modal.form.teacherId
  if (!tid) return
  try {
    // Không lọc ngày ở server: lấy trọn rồi tự lọc theo giai đoạn đang nhập, để đổi
    // ngày bắt đầu/kết thúc không phải gọi lại API.
    const { data } = await assignmentApi.teacherBusy({ teacherId: Number(tid) })
    teacherBusy.value = data || []
  } catch {
    teacherBusy.value = []
  }
}

// Hai giai đoạn [aStart,aEnd] và [bStart,bEnd] có chồng nhau không (chuỗi ISO yyyy-MM-dd
// so sánh trực tiếp được; end rỗng/null = vô thời hạn).
function rangesOverlap(aStart, aEnd, bStart, bEnd) {
  if (aEnd && bStart && aEnd < bStart) return false
  if (bEnd && aStart && bEnd < aStart) return false
  return true
}

// Hai khoảng giờ trong ngày có giao nhau không ("07:00:00" so chuỗi được vì cùng định dạng).
function timesOverlap(aStart, aEnd, bStart, bEnd) {
  if (!aStart || !aEnd || !bStart || !bEnd) return false
  return aStart < bEnd && bStart < aEnd
}

// { 'MON': [ô lịch bận…] } — chỉ giữ phân công có giai đoạn chồng với giai đoạn đang nhập.
const busyByDay = computed(() => {
  const map = {}
  const ns = modal.form.startDate
  const ne = modal.form.endDate
  for (const b of teacherBusy.value) {
    // Khi SỬA, bỏ qua chính phiếu đang sửa — không thì mọi tiết của nó đều báo "bận" vì
    // chạm vào chính nó, sửa xong không thêm lại được tiết cũ.
    if (modal.editId && b.assignmentId === modal.editId) continue
    if (ns && !rangesOverlap(ns, ne, b.startDate, b.endDate)) continue
    ;(map[b.dayOfWeek] ??= []).push(b)
  }
  return map
})

/**
 * Vì sao một tiết không chọn được cho một Thứ.
 * @returns null nếu chọn được, ngược lại { kind: 'busy'|'added', label }
 */
function periodConflict(period, day) {
  const busy = (busyByDay.value[day] ?? []).find((b) =>
    timesOverlap(period.startTime, period.endTime, b.startTime, b.endTime),
  )
  if (busy) {
    const at = [busy.schoolName, busy.className].filter(Boolean).join(' · ')
    return { kind: 'busy', label: at }
  }
  // Đã thêm ở ngay form này: so cả periodId lẫn giờ (2 tiết khác id vẫn có thể đè giờ).
  const added = modal.form.slots.find((s) => {
    if (s.dayOfWeek !== day) return false
    if (Number(s.periodId) === Number(period.id)) return true
    const p = scoped.periods.find((x) => Number(x.id) === Number(s.periodId))
    return p && timesOverlap(period.startTime, period.endTime, p.startTime, p.endTime)
  })
  if (added) {
    const c = scoped.classes.find((x) => Number(x.id) === Number(added.classId))
    return { kind: 'added', label: c ? c.name : '' }
  }
  return null
}

function periodTakenSuffix(period, day) {
  const c = periodConflict(period, day)
  if (!c) return ''
  return c.kind === 'busy'
    ? ` — bận: ${c.label || 'trường khác'}`
    : ` — đã thêm${c.label ? ' (' + c.label + ')' : ''}`
}

// Xung đột của tiết đang chọn ở ô nháp (để disable nút Thêm + báo đúng lý do).
const draftConflict = computed(() => {
  const p = scoped.periods.find((x) => Number(x.id) === Number(modal.slotDraft.periodId))
  return p ? periodConflict(p, modal.slotDraft.dayOfWeek) : null
})

// Nhảy ô tiết về tiết TRỐNG đầu tiên cho Thứ đang chọn (tránh dừng ở tiết vừa thêm/đã bận).
function pickFirstFreePeriod() {
  const day = modal.slotDraft.dayOfWeek
  const free = scoped.periods.find((p) => !periodConflict(p, day))
  modal.slotDraft.periodId = free ? free.id : ''
}

// Sau khi thêm một tiết, nhảy sang LỚP kế tiếp: xếp liên tiếp 1A1/1A2/1A3 cho tiết 1/2/3
// là ca dùng phổ biến nhất, đỡ phải chọn lại lớp mỗi dòng. Hết danh sách thì giữ nguyên.
function pickNextClass() {
  const i = scoped.classes.findIndex((c) => Number(c.id) === Number(modal.slotDraft.classId))
  if (i >= 0 && i + 1 < scoped.classes.length) modal.slotDraft.classId = scoped.classes[i + 1].id
}

function addSlot() {
  const { dayOfWeek, periodId, classId } = modal.slotDraft
  if (!periodId || !classId) return
  const period = scoped.periods.find((x) => Number(x.id) === Number(periodId))
  // Chặn tiết GV đã bận / đã thêm (khớp luật 409 backend).
  if (period && periodConflict(period, dayOfWeek)) return
  modal.form.slots.push({ dayOfWeek, periodId: Number(periodId), classId: Number(classId) })
  // Đổi lịch rồi thì cảnh báo cũ không còn đúng nữa — bỏ đi, để lần lưu sau tính lại.
  travelWarn.value = ''
  pickFirstFreePeriod() // tự chuyển sang tiết trống kế tiếp
  pickNextClass()
}

function removeSlot(i) {
  modal.form.slots.splice(i, 1)
  travelWarn.value = ''
}

function slotLabel(s) {
  const d = DAYS.find((x) => x.code === s.dayOfWeek)?.label ?? s.dayOfWeek
  const p = scoped.periods.find((x) => x.id === s.periodId)
  const c = scoped.classes.find((x) => Number(x.id) === Number(s.classId))
  const tiet = p
    ? tietLabel(p.periodNumber, p.sessionType, p.indexInSession)
    : 'Tiết #' + s.periodId
  return `${d} · ${tiet} · ${c ? c.name : 'Lớp #' + s.classId}`
}

const canSubmit = computed(
  () =>
    modal.form.teacherId &&
    modal.form.subjectId &&
    modal.form.schoolId &&
    modal.form.startDate &&
    modal.form.slots.length > 0,
)

/* ── Cảnh báo di chuyển giữa hai trường ──
 * Backend trả 409 kèm code TRAVEL_GAP khi hai buổi khác trường cách nhau dưới 30 phút.
 * Đây là CẢNH BÁO chứ không phải lỗi: mở hộp xác nhận, người xếp lịch chọn vẫn lưu thì
 * gửi lại đúng request đó kèm acceptTravelWarning, và backend ghi vết vào ghi chú phiếu.
 */
const travelWarn = ref('')

/** Gửi phiếu lên server. `accept` = đã bấm "Vẫn lưu" ở hộp cảnh báo. */
async function sendAssignment(accept) {
  if (modal.editId) {
    // Sửa xong phiếu quay về Chờ xác nhận và giáo viên nhận lại lời mời từ đầu.
    return assignmentApi.update(modal.editId, {
      teacherId: Number(modal.form.teacherId),
      startDate: modal.form.startDate,
      endDate: modal.form.endDate || null,
      slots: modal.form.slots,
      acceptTravelWarning: accept,
    })
  }
  return assignmentApi.create({
    teacherId: Number(modal.form.teacherId),
    subjectId: Number(modal.form.subjectId),
    schoolId: Number(modal.form.schoolId),
    classId: null, // lớp nằm ở từng tiết (slots[].classId), không còn ở cấp phân công
    startDate: modal.form.startDate,
    endDate: modal.form.endDate || null,
    slots: modal.form.slots,
    acceptTravelWarning: accept,
  })
}

async function submit(acceptTravelWarning = false) {
  if (!canSubmit.value) {
    modal.error = 'Vui lòng điền đủ GV, môn, trường, ngày bắt đầu và ít nhất 1 tiết (kèm lớp).'
    return
  }
  modal.saving = true
  modal.error = ''
  try {
    await sendAssignment(acceptTravelWarning)
    travelWarn.value = ''
    modal.open = false
    load()
  } catch (e) {
    const data = e.response?.data
    if (data?.code === 'TRAVEL_GAP') {
      // Không phải lỗi — đổi cụm nút thành "Quay lại sửa / Vẫn lưu" ngay trong form.
      travelWarn.value = data.message
    } else {
      modal.error =
        data?.message ?? (modal.editId ? 'Lưu thay đổi thất bại' : 'Tạo phân công thất bại')
    }
  } finally {
    modal.saving = false
  }
}

/* ── Hạn xác nhận: đếm ngược + mức cảnh báo ── */

/**
 * Trạng thái hạn của một phiếu chờ: 'over' (quá hạn) | 'warn' (sắp hết) | 'ok' | null.
 * Chỉ phiếu đang chờ mới có hạn — phiếu đã quyết rồi thì hạn không còn nghĩa gì.
 */
function deadlineLevel(a) {
  if (a.status !== 'PENDING' || !a.confirmDeadline) return null
  const left = new Date(a.confirmDeadline).getTime() - Date.now()
  if (left <= 0) return 'over'
  return left <= DEADLINE_WARN_HOURS * 3600_000 ? 'warn' : 'ok'
}

/** "còn 5 giờ" / "còn 40 phút" / "quá hạn" — admin cần biết còn bao lâu, không cần ngày giờ đầy đủ. */
function deadlineText(a) {
  if (!a.confirmDeadline) return '—'
  const left = new Date(a.confirmDeadline).getTime() - Date.now()
  if (left <= 0) return 'quá hạn'
  const hours = Math.floor(left / 3600_000)
  if (hours >= 24) return `còn ${Math.floor(hours / 24)} ngày`
  if (hours >= 1) return `còn ${hours} giờ`
  return `còn ${Math.max(1, Math.round(left / 60_000))} phút`
}

/** Hạn đầy đủ để hiện trong tooltip (dd/MM/yyyy HH:mm). */
function deadlineFull(a) {
  if (!a.confirmDeadline) return ''
  const d = new Date(a.confirmDeadline)
  const p = (n) => String(n).padStart(2, '0')
  return `Hạn xác nhận: ${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/* ── Tích chọn + thao tác hàng loạt ── */

// Chỉ phiếu CHƯA có hiệu lực mới thao tác hàng loạt được (nhắc/ép duyệt/hủy).
const actionableItems = computed(() =>
  pagedItems.value.filter((a) => ['PENDING', 'EXPIRED', 'REJECTED'].includes(a.status)),
)
const allSelected = computed(
  () =>
    actionableItems.value.length > 0 && selectedIds.value.length === actionableItems.value.length,
)
function toggleAll() {
  selectedIds.value = allSelected.value ? [] : actionableItems.value.map((a) => a.id)
}
function isSelectable(a) {
  return ['PENDING', 'EXPIRED', 'REJECTED'].includes(a.status)
}

const bulkBusy = ref(false)
async function runBulk(action, confirmText) {
  if (!selectedIds.value.length || bulkBusy.value) return
  if (confirmText && !window.confirm(confirmText)) return
  bulkBusy.value = true
  try {
    const { data } = await assignmentApi.bulk(action, [...selectedIds.value])
    if (data.errors?.length) {
      alert(`Xong ${data.succeeded} phiếu.\nKhông làm được:\n` + data.errors.join('\n'))
    }
    load()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Thao tác thất bại')
  } finally {
    bulkBusy.value = false
  }
}

/* ── Thao tác trên từng dòng ── */

async function remindOne(a) {
  try {
    await assignmentApi.remind(a.id)
    alert(`Đã gửi lại lời mời cho ${a.teacherName}.`)
    load()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Gửi nhắc thất bại')
  }
}

const approveTarget = ref(null) // phiếu đang chờ admin ép duyệt
const approveNote = ref('')
async function confirmForceApprove() {
  if (!approveTarget.value) return
  try {
    await assignmentApi.forceApprove(approveTarget.value.id, approveNote.value)
    approveTarget.value = null
    approveNote.value = ''
    load()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Ép duyệt thất bại')
    approveTarget.value = null
  }
}

/* Hủy phân công = đưa thẳng vào thùng rác (một thao tác). */
async function confirmCancel() {
  if (!cancelTarget.value) return
  try {
    await assignmentApi.remove(cancelTarget.value.id)
    cancelTarget.value = null
    load()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Hủy thất bại')
    cancelTarget.value = null
  }
}

/* Khôi phục từ thùng rác → đưa lại Đang chạy (có thể bị chặn nếu trùng lịch). */
async function restoreItem(a) {
  try {
    await assignmentApi.restore(a.id)
    loadTrash()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Khôi phục thất bại')
  }
}

/* Xóa vĩnh viễn khỏi hệ thống (không thể hoàn tác). */
async function confirmPurge() {
  if (!purgeTarget.value) return
  try {
    await assignmentApi.purge(purgeTarget.value.id)
    purgeTarget.value = null
    loadTrash()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Xóa vĩnh viễn thất bại')
    purgeTarget.value = null
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="title">
          {{ inTrash ? 'Thùng rác — Phân công đã xóa' : 'Phân công giảng dạy' }}
        </h2>
      </div>
      <div class="head-actions">
        <template v-if="view === 'list'">
          <button class="btn btn-outline" @click="showTrash">Thùng rác</button>
          <button class="btn btn-primary" @click="openCreate">+ Tạo phân công</button>
        </template>
        <button v-else class="btn btn-outline" @click="showList">← Quay lại danh sách</button>
      </div>
    </div>

    <!-- Tìm kiếm phân công theo GV/trường/lớp/môn (chỉ ở danh sách đang hoạt động) -->
    <div v-if="view === 'list'" class="filter-bar">
      <label class="field field--wide">
        <span>Tìm kiếm</span>
        <input
          v-model="search"
          type="search"
          placeholder="Nhập tên giáo viên, trường, lớp, môn…"
          aria-label="Tìm phân công theo giáo viên, trường, lớp, môn"
          @input="onSearchInput"
          @keyup.enter="applySearch"
        />
      </label>
      <div class="filter-actions">
        <button class="btn btn-primary" @click="applySearch">Lọc</button>
        <button class="btn btn-outline" @click="clearSearch">Xóa lọc</button>
      </div>
    </div>

    <!-- Tab trạng thái: việc cần xử lý (Chờ xác nhận / Hết hạn / Bị từ chối) đứng trước -->
    <div v-if="view === 'list'" class="status-tabs">
      <button
        v-for="t in STATUS_TABS"
        :key="t.code"
        class="status-tab"
        :class="{ 'status-tab--on': statusFilter === t.code }"
        @click="selectTab(t.code)"
      >
        {{ t.label }}
        <span v-if="t.code && statusCounts[t.code]" class="status-tab__n">{{
          statusCounts[t.code]
        }}</span>
      </button>
    </div>

    <!-- Thanh thao tác hàng loạt: đầu kỳ phân công cả chục GV, bấm từng dòng quá chậm -->
    <div v-if="view === 'list' && selectedIds.length" class="bulk-bar">
      <span class="bulk-bar__count">Đã chọn {{ selectedIds.length }} phiếu</span>
      <button class="btn btn-sm btn-outline" :disabled="bulkBusy" @click="runBulk('remind')">
        Nhắc giáo viên
      </button>
      <button
        class="btn btn-sm btn-outline"
        :disabled="bulkBusy"
        @click="
          runBulk(
            'force-approve',
            `Duyệt thay giáo viên ${selectedIds.length} phiếu? Lịch sẽ có hiệu lực ngay dù giáo viên chưa xác nhận.`,
          )
        "
      >
        Ép duyệt
      </button>
      <button
        class="btn btn-sm btn-danger"
        :disabled="bulkBusy"
        @click="runBulk('cancel', `Hủy ${selectedIds.length} phiếu và đưa vào thùng rác?`)"
      >
        Hủy phiếu
      </button>
      <button class="btn btn-sm btn-outline" @click="selectedIds = []">Bỏ chọn</button>
    </div>

    <div class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th v-if="!inTrash" class="col-check">
              <input
                type="checkbox"
                :checked="allSelected"
                :disabled="!actionableItems.length"
                aria-label="Chọn tất cả phiếu thao tác được"
                @change="toggleAll"
              />
            </th>
            <th>Giáo viên</th>
            <th>Trường</th>
            <th>Lớp</th>
            <th>Môn</th>
            <th>Giai đoạn</th>
            <th>Tiết / tuần</th>
            <th>Trạng thái</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="9" class="text-center text-muted">Đang tải…</td>
          </tr>
          <tr v-else-if="!currentList.length">
            <td colspan="9" class="text-center text-muted">
              {{
                inTrash
                  ? 'Thùng rác trống'
                  : search.trim()
                    ? 'Không tìm thấy phân công phù hợp'
                    : 'Chưa có phân công nào'
              }}
            </td>
          </tr>
          <tr v-for="a in pagedItems" :key="a.id">
            <td v-if="!inTrash" class="col-check">
              <input
                v-if="isSelectable(a)"
                v-model="selectedIds"
                type="checkbox"
                :value="a.id"
                :aria-label="`Chọn phiếu của ${a.teacherName}`"
              />
            </td>
            <td class="font-medium">{{ a.teacherName }}</td>
            <td>{{ a.schoolName }}</td>
            <td>{{ a.className ?? '—' }}</td>
            <td>{{ a.subjectName }}</td>
            <td class="text-muted small">
              {{ a.startDate }} → {{ a.endDate ?? 'không giới hạn' }}
            </td>
            <td>
              <span v-for="s in a.slots" :key="s.id" class="chip"
                >{{ s.dayOfWeekLabel }} ·
                {{ tietShort(s.periodNumber, s.sessionType, s.indexInSession)
                }}<template v-if="s.className"> · {{ s.className }}</template></span
              >
              <span v-if="!a.slots?.length" class="text-muted">—</span>
            </td>
            <td>
              <span
                class="badge"
                :class="{
                  'badge-green': a.status === 'ACTIVE',
                  'badge-gray': a.status === 'CANCELLED',
                  'badge-blue': a.status === 'COMPLETED',
                  'badge-amber': a.status === 'PENDING',
                  'badge-red': a.status === 'REJECTED' || a.status === 'EXPIRED',
                }"
                >{{ STATUS_LABEL[a.status] ?? a.status }}</span
              >
              <!-- Đếm ngược hạn: vàng khi sắp hết, đỏ khi đã quá — admin nhìn là biết phiếu nào gấp -->
              <div
                v-if="deadlineLevel(a)"
                class="deadline"
                :class="`deadline--${deadlineLevel(a)}`"
                :title="deadlineFull(a)"
              >
                {{ deadlineText(a) }}
              </div>
              <div v-if="a.status === 'REJECTED' && a.rejectionReason" class="reject-why">
                “{{ a.rejectionReason }}”
              </div>
              <div v-else-if="a.confirmSource === 'ADMIN'" class="reject-why">
                Duyệt thay bởi trung tâm
              </div>
            </td>
            <td class="actions">
              <template v-if="!inTrash">
                <!-- Phiếu chưa có hiệu lực: nhắc lại / duyệt thay / sửa rồi gửi lại -->
                <button
                  v-if="a.status === 'PENDING'"
                  class="btn btn-sm btn-outline"
                  title="Gửi lại lời mời cho giáo viên"
                  @click="remindOne(a)"
                >
                  Nhắc
                </button>
                <button
                  v-if="a.status === 'PENDING' || a.status === 'EXPIRED'"
                  class="btn btn-sm btn-outline"
                  title="Duyệt thay giáo viên — lịch có hiệu lực ngay"
                  @click="((approveTarget = a), (approveNote = ''))"
                >
                  Ép duyệt
                </button>
                <button
                  v-if="['PENDING', 'EXPIRED', 'REJECTED'].includes(a.status)"
                  class="btn btn-sm btn-outline"
                  title="Sửa rồi gửi lại lời mời (đổi được giáo viên)"
                  @click="openEdit(a)"
                >
                  Sửa
                </button>
                <button
                  v-if="a.status !== 'COMPLETED'"
                  class="btn btn-sm btn-danger"
                  @click="cancelTarget = a"
                >
                  Hủy
                </button>
              </template>
              <template v-else>
                <button class="btn btn-sm btn-outline" @click="restoreItem(a)">Khôi phục</button>
                <button class="btn btn-sm btn-danger" @click="purgeTarget = a">
                  Xóa vĩnh viễn
                </button>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <Pagination v-model="page" :total-pages="totalPages" />

    <!-- Modal tạo phân công -->
    <div v-if="modal.open" class="modal-overlay" @click.self="modal.open = false">
      <div class="modal-box modal-lg">
        <h3>{{ modal.editId ? `Sửa phân công #${modal.editId}` : 'Tạo phân công' }}</h3>
        <p v-if="modal.editId" class="modal-note">
          Lưu xong phiếu quay về <strong>Chờ xác nhận</strong> và giáo viên nhận lại lời mời, hạn
          trả lời tính lại từ đầu. Trường và môn không đổi được — muốn đổi thì hủy phiếu và tạo
          phiếu mới.
        </p>

        <div class="grid2">
          <div class="form-group">
            <label>Giáo viên *</label>
            <select v-model="modal.form.teacherId" @change="onTeacherChange">
              <option value="">-- Chọn giáo viên --</option>
              <option v-for="t in options.teachers" :key="t.id" :value="t.id">{{ t.name }}</option>
            </select>
          </div>
          <div class="form-group">
            <label>Môn học *</label>
            <select v-model="modal.form.subjectId" :disabled="!!modal.editId">
              <option value="">-- Chọn môn --</option>
              <option v-for="s in options.subjects" :key="s.id" :value="s.id">{{ s.name }}</option>
            </select>
          </div>
          <div class="form-group">
            <label>Trường *</label>
            <select
              v-model="modal.form.schoolId"
              :disabled="!!modal.editId"
              @change="onSchoolChange"
            >
              <option value="">-- Chọn trường --</option>
              <option v-for="s in options.schools" :key="s.id" :value="s.id">{{ s.name }}</option>
            </select>
          </div>
          <div class="form-group">
            <label>Ngày bắt đầu *</label>
            <DateField v-model="modal.form.startDate" />
          </div>
          <div class="form-group">
            <label>Ngày kết thúc</label>
            <DateField v-model="modal.form.endDate" />
            <small>Bỏ trống = sinh lịch 8 tuần từ ngày bắt đầu.</small>
          </div>
        </div>

        <!-- Slots: mỗi dòng là Thứ + Tiết + LỚP (một tiết một lớp) -->
        <div class="slots-block">
          <label class="slots-label">Các tiết dạy trong tuần * — mỗi tiết chọn lớp riêng</label>
          <div class="slot-add">
            <select v-model="modal.slotDraft.dayOfWeek" @change="pickFirstFreePeriod">
              <option v-for="d in DAYS" :key="d.code" :value="d.code">{{ d.label }}</option>
            </select>
            <select v-model="modal.slotDraft.periodId" :disabled="!scoped.periods.length">
              <option value="">
                {{ scoped.periods.length ? '-- Chọn tiết --' : 'Chọn trường trước' }}
              </option>
              <option
                v-for="p in scoped.periods"
                :key="p.id"
                :value="p.id"
                :disabled="!!periodConflict(p, modal.slotDraft.dayOfWeek)"
              >
                {{ tietLabel(p.periodNumber, p.sessionType, p.indexInSession)
                }}{{ periodTakenSuffix(p, modal.slotDraft.dayOfWeek) }}
              </option>
            </select>
            <select v-model="modal.slotDraft.classId" :disabled="!scoped.classes.length">
              <option value="">
                {{ scoped.classes.length ? '-- Chọn lớp --' : 'Chọn trường trước' }}
              </option>
              <option v-for="c in scoped.classes" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
            <button
              class="btn btn-outline btn-sm"
              type="button"
              :disabled="!modal.slotDraft.periodId || !modal.slotDraft.classId || !!draftConflict"
              @click="addSlot"
            >
              + Thêm tiết
            </button>
          </div>
          <p v-if="draftConflict?.kind === 'busy'" class="slot-hint slot-hint--warn">
            Giáo viên đã bận khung giờ này{{
              draftConflict.label ? ` (${draftConflict.label})` : ''
            }}
            — vui lòng chọn tiết khác.
          </p>
          <p v-else-if="draftConflict?.kind === 'added'" class="slot-hint slot-hint--muted">
            Khung giờ này đã có trong danh sách bên dưới.
          </p>
          <div class="chips">
            <span v-for="(s, i) in modal.form.slots" :key="i" class="chip chip-lg">
              {{ slotLabel(s) }}
              <button class="chip-x" type="button" @click="removeSlot(i)">×</button>
            </span>
            <span v-if="!modal.form.slots.length" class="text-muted small"
              >Chưa thêm tiết nào.</span
            >
          </div>
        </div>

        <p v-if="modal.error" class="error-msg">{{ modal.error }}</p>

        <!-- Cảnh báo di chuyển — nằm NGAY TRONG form, không mở hộp thứ hai: người xếp lịch
             vẫn thấy phiếu mình đang sửa để quyết, thay vì bị che mất. -->
        <div v-if="travelWarn" class="travel" role="alert">
          <span class="travel__icon" aria-hidden="true">!</span>
          <div>
            <strong class="travel__title">Cảnh báo di chuyển giữa hai trường</strong>
            <p class="travel__msg">{{ travelWarn }}</p>
          </div>
        </div>

        <div class="modal-actions">
          <template v-if="travelWarn">
            <button class="btn btn-outline" @click="travelWarn = ''">Quay lại sửa</button>
            <button class="btn btn-warn" :disabled="modal.saving" @click="submit(true)">
              {{ modal.saving ? 'Đang lưu…' : 'Vẫn lưu' }}
            </button>
          </template>
          <template v-else>
            <button class="btn btn-outline" @click="modal.open = false">Hủy</button>
            <button
              class="btn btn-primary"
              :disabled="modal.saving || !canSubmit"
              @click="submit()"
            >
              {{
                modal.saving
                  ? 'Đang lưu…'
                  : modal.editId
                    ? 'Lưu & gửi lại lời mời'
                    : 'Tạo phân công'
              }}
            </button>
          </template>
        </div>
      </div>
    </div>

    <!-- Ép duyệt thay giáo viên -->
    <div v-if="approveTarget" class="modal-overlay" @click.self="approveTarget = null">
      <div class="modal-box modal-sm">
        <h3>Duyệt thay giáo viên</h3>
        <p>
          Duyệt phân công của <strong>{{ approveTarget.teacherName }}</strong> tại
          {{ approveTarget.schoolName }} mà <strong>không cần giáo viên xác nhận</strong>? Lịch sẽ
          có hiệu lực ngay và được tính công, tính lương.
        </p>
        <div class="form-group">
          <label>Ghi chú (không bắt buộc)</label>
          <input
            v-model="approveNote"
            type="text"
            placeholder="vd: đã trao đổi trực tiếp với giáo viên"
          />
        </div>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="approveTarget = null">Không</button>
          <button class="btn btn-primary" @click="confirmForceApprove">Ép duyệt</button>
        </div>
      </div>
    </div>

    <!-- Confirm hủy → đưa vào thùng rác -->
    <div v-if="cancelTarget" class="modal-overlay" @click.self="cancelTarget = null">
      <div class="modal-box modal-sm">
        <h3>Xác nhận hủy</h3>
        <p>
          Hủy phân công của <strong>{{ cancelTarget.teacherName }}</strong> tại
          {{ cancelTarget.schoolName }} và đưa vào <strong>thùng rác</strong>? Các buổi chưa diễn ra
          sẽ bị hủy theo. Bạn có thể khôi phục lại từ thùng rác.
        </p>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="cancelTarget = null">Không</button>
          <button class="btn btn-danger" @click="confirmCancel">Hủy phân công</button>
        </div>
      </div>
    </div>

    <!-- Confirm xóa vĩnh viễn -->
    <div v-if="purgeTarget" class="modal-overlay" @click.self="purgeTarget = null">
      <div class="modal-box modal-sm">
        <h3>Xóa vĩnh viễn</h3>
        <p>
          Xóa <strong>vĩnh viễn</strong> phân công của
          <strong>{{ purgeTarget.teacherName }}</strong> tại {{ purgeTarget.schoolName }} khỏi hệ
          thống? Hành động này <strong>không thể hoàn tác</strong> và sẽ xóa cả các buổi dạy liên
          quan.
        </p>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="purgeTarget = null">Không</button>
          <button class="btn btn-danger" @click="confirmPurge">Xóa vĩnh viễn</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ── Cảnh báo di chuyển ──
   Tông VÀNG chứ không đỏ: đây là nhắc nhở có thể bỏ qua, không phải lỗi chặn.
   Nằm ngay trong form (không phải modal riêng) nên phải tự tách khỏi nội dung
   phía trên bằng nền + viền, thay vì dựa vào khung hộp. */
.travel {
  display: flex;
  gap: 0.7rem;
  align-items: flex-start;
  margin: 0.2rem 0 0;
  padding: 0.85rem 1rem;
  border: 1px solid rgba(245, 158, 11, 0.45);
  border-radius: 12px;
  background: rgba(245, 158, 11, 0.1);
}
/* Dấu chấm than tròn — mỏ neo thị giác để mắt bắt ngay đây là cảnh báo. */
.travel__icon {
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--c-amber);
  color: #fff;
  font-size: 0.85rem;
  font-weight: 700;
  line-height: 22px;
  text-align: center;
}
.travel__title {
  display: block;
  margin-bottom: 0.15rem;
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--c-text);
}
.travel__msg {
  margin: 0;
  font-size: 0.86rem;
  line-height: 1.55;
  color: var(--c-text-muted);
}
/* Nút "Vẫn lưu": tông cảnh báo, KHÔNG dùng primary cam — để người bấm thấy rõ
   mình đang chấp nhận rủi ro chứ không phải làm một thao tác bình thường. */
.btn-warn {
  background: var(--c-amber);
  color: #fff;
  border: 0;
}
.btn-warn:hover:not(:disabled) {
  background: #d97706;
}

.head-actions {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}
/* Ô tìm kiếm phân công — theo mẫu filter-bar (khung bo viền + nhãn + nút Lọc/Xóa lọc) */
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 18px;
  margin-bottom: 18px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 14px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 220px;
}
.field--wide {
  flex: 1;
}
.field span {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
}
.field input {
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--c-input-border, var(--c-border));
  border-radius: 8px;
  background: var(--c-surface);
  color: var(--c-text);
  font-size: 14px;
}
.field input:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.14);
}
.filter-actions {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}
/* Tiêu đề cột không bị ngắt dòng (vd "TRẠNG THÁI" bị xuống 2 dòng khi cột hẹp) */
.table th {
  white-space: nowrap;
}
/* Cột trạng thái: badge luôn gọn 1 dòng (không bị ngắt "Đang / chạy") */
.badge {
  white-space: nowrap;
}
/* Cột hành động: dùng lại ô bảng bình thường (không flex) để nút luôn nằm cùng 1 dòng
   và căn giữa theo chiều dọc, thẳng hàng với badge trạng thái kể cả ở dòng cao nhiều chip. */
.actions {
  display: table-cell;
  vertical-align: middle;
  white-space: nowrap;
}
.actions .btn + .btn {
  margin-left: 0.4rem;
}
.slots-block {
  margin-top: 0.5rem;
  border-top: 1px dashed var(--c-border);
  padding-top: 0.9rem;
}
.slots-label {
  display: block;
  font-size: 0.85rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
  color: var(--c-text);
}
.slot-add {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-bottom: 0.6rem;
}
.slot-add select {
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--c-input-border);
  border-radius: 6px;
  font-size: 0.88rem;
  background: var(--c-surface);
  color: var(--c-text);
}
.slot-add select option:disabled {
  color: var(--c-text-muted);
}
.slot-hint {
  margin: 0 0 0.6rem;
  font-size: 0.78rem;
}
.slot-hint--warn {
  color: #dc2626;
}
.slot-hint--muted {
  color: var(--c-text-muted);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(99, 102, 241, 0.12);
  color: #3730a3;
  border-radius: 9999px;
  padding: 0.12rem 0.55rem;
  font-size: 0.74rem;
  font-weight: 600;
  margin: 0 2px 2px 0;
}
.chip-lg {
  font-size: 0.8rem;
  padding: 0.2rem 0.7rem;
}
.chip-x {
  border: none;
  background: transparent;
  color: #6366f1;
  cursor: pointer;
  font-size: 0.95rem;
  line-height: 1;
}
.badge-blue {
  background: rgba(37, 99, 235, 0.12);
  color: #1e40af;
}
.badge-red {
  background: rgba(220, 38, 38, 0.12);
  color: #b91c1c;
}
.badge-amber {
  background: rgba(217, 119, 6, 0.14);
  color: #b45309;
}
/* Tab lọc theo trạng thái */
.status-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 14px;
}
.status-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: 1px solid var(--c-border);
  border-radius: 9999px;
  background: var(--c-surface);
  color: var(--c-text);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.status-tab--on {
  border-color: var(--c-primary);
  background: rgba(249, 115, 22, 0.12);
  color: var(--c-primary);
}
.status-tab__n {
  min-width: 18px;
  padding: 0 5px;
  border-radius: 9999px;
  background: var(--c-border);
  font-size: 11px;
  line-height: 17px;
  text-align: center;
}
.status-tab--on .status-tab__n {
  background: var(--c-primary);
  color: #fff;
}
/* Thanh thao tác hàng loạt */
.bulk-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 12px;
  border: 1px solid var(--c-primary);
  border-radius: 10px;
  background: rgba(249, 115, 22, 0.08);
}
.bulk-bar__count {
  font-size: 13px;
  font-weight: 700;
  margin-right: 4px;
}
.col-check {
  width: 34px;
  text-align: center;
}
/* Đếm ngược hạn xác nhận dưới badge trạng thái */
.deadline {
  margin-top: 3px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
.deadline--ok {
  color: var(--c-text-muted);
}
.deadline--warn {
  color: #b45309;
}
.deadline--over {
  color: #b91c1c;
}
.reject-why {
  margin-top: 3px;
  font-size: 11px;
  font-style: italic;
  color: var(--c-text-muted);
  max-width: 190px;
}
.modal-note {
  margin: -4px 0 14px;
  padding: 9px 12px;
  border-radius: 8px;
  background: rgba(249, 115, 22, 0.08);
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--c-text);
}
/* Chữ đậm chìm trên nền tối → dùng tông sáng hơn */
:root[data-theme='dark'] .chip {
  color: #a5b4fc;
}
:root[data-theme='dark'] .badge-blue {
  color: #93c5fd;
}
:root[data-theme='dark'] .badge-red {
  color: #fca5a5;
}
</style>
