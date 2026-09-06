<!-- src/pages/HolidayListPage.vue -->
<script setup>
/**
 * Trang "Lịch nghỉ" — quản lý ngày lễ & kỳ nghỉ mà hệ thống KHÔNG sinh buổi dạy.
 *
 * VÌ SAO TRANG NÀY QUAN TRỌNG HƠN VẺ NGOÀI CỦA NÓ
 * Generator lịch dạy hỏi bảng Holiday mỗi lần trải ô thời khóa biểu (Flyway V29). Thiếu một
 * ngày nghỉ ở đây thì lịch đẻ ra buổi dạy vào ngày trường đóng cửa, job khép sổ chấm công
 * ghi VẮNG cho giáo viên, và tiền bị trừ khỏi bảng lương — không ai bấm nút nào sai cả.
 *
 * HAI THỨ TRANG NÀY CỐ Ý LÀM RÕ
 * 1. Nhãn "Cần rà soát" — các ngày suy từ âm lịch (Tết, Giỗ Tổ) và ngày nghỉ bù 2/9 do
 *    Chính phủ chốt riêng từng năm được seed kèm dấu cảnh báo. Không đẩy lên màn hình thì
 *    chẳng ai nhớ đi đối chiếu, và cả năm học sinh lịch theo một ngày đoán mò.
 * 2. Nút "Hủy N buổi dạy" — khai báo kỳ nghỉ MỚI không tự dọn lịch đã sinh trước đó. Trang
 *    đếm sẵn số buổi bị ảnh hưởng và để người dùng tự bấm, thay vì hủy ngầm sau lưng họ:
 *    một kỳ nghỉ gõ nhầm năm mà tự hủy sẽ quét sạch lịch trước khi ai kịp nhìn.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { holidayApi } from '@/api/holidays'
import { schoolApi } from '@/api/schools'
import DateField from '@/components/ui/DateField.vue'
import Pagination from '@/components/ui/Pagination.vue'
import { PAGE_SIZE } from '@/utils/pagination'
import { isoToday } from '@/utils/format'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import FilterBar from '@/components/ui/FilterBar.vue'
import { useToast } from '@/composables/useToast'

const route = useRoute()
const { showToast } = useToast()

/** 'active' = đang dùng · 'trash' = đã xóa. Hai danh sách dùng chung bảng bên dưới. */
const tab = ref('active')

/** badge: lớp màu lấy từ page-common.css, khỏi tự pha màu riêng cho trang này. */
const KINDS = [
  { value: 'NATIONAL', label: 'Lễ theo luật', badge: 'badge-red' },
  { value: 'BREAK', label: 'Kỳ nghỉ của học sinh', badge: 'badge-blue' },
  { value: 'CENTER', label: 'Trung tâm nghỉ riêng', badge: 'badge-amber' },
]
const kindMeta = (v) => KINDS.find((k) => k.value === v) ?? { label: v, badge: 'badge-gray' }

const loading = ref(false)
const error = ref('')
const items = ref([])
const total = ref(0)
const page = ref(0)
const pageSize = PAGE_SIZE

const schools = ref([])

const filter = reactive({ keyword: '', kind: '', schoolId: '', from: '', to: '' })

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

/**
 * Kỳ nghỉ đang ở giai đoạn nào so với hôm nay: 'past' | 'now' | 'next'.
 *
 * Gọi isoToday() mỗi lần thay vì tính sẵn một hằng số lúc load: trang này hay được mở suốt
 * ngày, hằng số sẽ đứng yên qua nửa đêm.
 */
function phase(h) {
  const today = isoToday()
  if (h.toDate < today) return 'past'
  return h.fromDate <= today ? 'now' : 'next'
}

function fmt(iso) {
  if (!iso) return '—'
  const [y, m, d] = iso.split('-')
  return `${d}/${m}/${y}`
}

/** "12/02/2026" cho nghỉ 1 ngày, "12/02/2026 → 22/02/2026" cho kỳ dài. */
function fmtRange(h) {
  return h.fromDate === h.toDate ? fmt(h.fromDate) : `${fmt(h.fromDate)} → ${fmt(h.toDate)}`
}

/**
 * Danh sách PHẲNG để bảng chỉ có một <tbody>: xen dòng mốc năm giữa các kỳ nghỉ.
 *
 * Server trả về đã sắp fromDate giảm dần nên các kỳ nghỉ cùng năm nằm liền nhau — chỉ cần
 * cắt khúc, không phải sắp lại. Thùng rác sắp theo NGÀY XÓA nên bỏ qua mốc năm: chèn vào đó
 * sẽ ra "2026 · 2025 · 2026" lộn xộn.
 */
const rows = computed(() => {
  const out = []
  let year = null
  for (const h of items.value) {
    if (tab.value === 'active') {
      const y = h.fromDate.slice(0, 4)
      if (y !== year) {
        year = y
        out.push({ type: 'year', key: `y${y}`, year: y })
      }
    }
    out.push({ type: 'row', key: `h${h.id}`, h })
  }
  return out
})

/** Id các kỳ nghỉ đang hiện mà còn việc phải xử lý — quyết định có vẽ nút "Buổi dạy" ở dòng đó. */
const issues = ref(new Set())
/**
 * Hỏi không được thì HIỆN HẾT nút, đừng ẩn hết. Ẩn nhầm là cắt mất đường vào chỗ duy nhất
 * dọn được buổi dạy vướng kỳ nghỉ; hiện thừa thì cùng lắm bấm vào và nhận "không vướng gì".
 */
const issuesFailed = ref(false)

/**
 * Nạp số việc cho các dòng đang hiện — gọi RỜI, không chờ, sau khi bảng đã vẽ.
 *
 * Câu đếm bên server quét cả bảng buổi dạy nên mất khoảng nửa giây (nguội thì lâu hơn). Chờ
 * nó rồi mới vẽ bảng là đánh đổi sai: người dùng vào đây để ĐỌC lịch nghỉ, còn nút kia chỉ
 * cần cho vài dòng hiếm hoi.
 */
async function loadIssues() {
  issues.value = new Set()
  issuesFailed.value = false
  // Thùng rác không có nút "Buổi dạy" nên khỏi hỏi.
  if (tab.value !== 'active' || items.value.length === 0) return
  const ids = items.value.map((h) => h.id)
  try {
    const res = await holidayApi.withIssues(ids)
    // Danh sách có thể đã đổi trong lúc chờ (đổi trang, đổi bộ lọc) — kết quả cũ về sau thì bỏ.
    if (items.value.some((h, i) => h.id !== ids[i])) return
    issues.value = new Set(res.data ?? [])
  } catch {
    issuesFailed.value = true
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = { page: page.value, size: pageSize }
    if (filter.keyword.trim()) params.keyword = filter.keyword.trim()
    // Thùng rác chỉ lọc theo từ khóa: lọc loại/phạm vi/ngày ở đây chỉ làm người dùng
    // tưởng kỳ nghỉ mình vừa xóa nhầm đã mất hẳn.
    if (tab.value === 'active') {
      if (filter.kind) params.kind = filter.kind
      if (filter.schoolId) params.schoolId = filter.schoolId
      if (filter.from) params.from = filter.from
      if (filter.to) params.to = filter.to
    }
    const res =
      tab.value === 'trash' ? await holidayApi.trash(params) : await holidayApi.list(params)
    items.value = res.data?.content ?? []
    total.value = res.data?.totalElements ?? 0
    loadIssues()
  } catch (e) {
    error.value = e.response?.data?.message || 'Không tải được lịch nghỉ.'
  } finally {
    loading.value = false
  }
}

function switchTab(t) {
  if (tab.value === t) return
  tab.value = t
  page.value = 0
  load()
}

function applyFilter() {
  page.value = 0
  load()
}

function clearFilter() {
  filter.keyword = ''
  filter.kind = ''
  filter.schoolId = ''
  filter.from = ''
  filter.to = ''
  applyFilter()
}

function goPage(p) {
  if (p < 0 || p >= totalPages.value) return
  page.value = p
  load()
}

/* ───────────────────────── Thêm / sửa ───────────────────────── */

const modal = reactive({
  open: false,
  mode: 'create',
  id: null,
  /** Nghỉ 1 ngày: form chỉ hiện một ô ngày, toDate tự bám theo fromDate. */
  oneDay: true,
  form: { fromDate: '', toDate: '', name: '', kind: 'NATIONAL', schoolId: '', note: '' },
  errors: {},
  error: '',
  saving: false,
})

function openCreate() {
  modal.open = true
  modal.mode = 'create'
  modal.id = null
  modal.oneDay = true
  modal.form = { fromDate: '', toDate: '', name: '', kind: 'NATIONAL', schoolId: '', note: '' }
  modal.errors = {}
  modal.error = ''
}

function openEdit(h) {
  modal.open = true
  modal.mode = 'edit'
  modal.id = h.id
  modal.oneDay = h.fromDate === h.toDate
  modal.form = {
    fromDate: h.fromDate,
    toDate: h.toDate,
    name: h.name,
    kind: h.kind || 'NATIONAL',
    schoolId: h.schoolId ?? '',
    note: h.note ?? '',
  }
  modal.errors = {}
  modal.error = ''
}

function validate() {
  const e = {}
  if (!modal.form.fromDate) e.fromDate = 'Vui lòng chọn ngày'
  if (!modal.oneDay && !modal.form.toDate) e.toDate = 'Vui lòng chọn ngày kết thúc'
  if (!modal.oneDay && modal.form.toDate && modal.form.toDate < modal.form.fromDate) {
    e.toDate = 'Ngày kết thúc phải từ ngày bắt đầu trở đi'
  }
  if (!modal.form.name.trim()) e.name = 'Tên kỳ nghỉ không được để trống'
  modal.errors = e
  return Object.keys(e).length === 0
}

async function save() {
  if (!validate()) return
  modal.saving = true
  modal.error = ''
  try {
    const body = {
      fromDate: modal.form.fromDate,
      toDate: modal.oneDay ? modal.form.fromDate : modal.form.toDate,
      name: modal.form.name.trim(),
      kind: modal.form.kind,
      schoolId: modal.form.schoolId === '' ? null : Number(modal.form.schoolId),
      note: modal.form.note.trim() || null,
    }
    if (modal.mode === 'create') {
      const res = await holidayApi.create(body)
      modal.open = false
      await load()
      // Kỳ nghỉ vừa khai báo có thể trùm lên lịch đã sinh trước đó → hỏi ngay.
      await checkImpact(res.data)
    } else {
      await holidayApi.update(modal.id, body)
      modal.open = false
      await load()
    }
  } catch (e) {
    modal.error = e.response?.data?.message || 'Không lưu được. Thử lại.'
  } finally {
    modal.saving = false
  }
}

/* ──────────── Buổi dạy đã sinh đang rơi vào kỳ nghỉ ──────────── */

/**
 * Một hộp thoại, HAI việc — cố ý gộp: chúng là hai nửa của cùng một sự cố.
 *   · Buổi CHƯA diễn ra  → hủy đi là xong (impact).
 *   · Buổi ĐÃ diễn ra    → hủy không cứu được, dòng VẮNG mà job nền ghi vẫn nằm trong hồ sơ
 *                          chuyên cần của giáo viên và phải chuyển sang Nghỉ phép (absence).
 * Tách thành hai nút riêng thì ai cũng bấm nút đầu rồi tưởng đã xong.
 */
const impact = reactive({
  open: false,
  holiday: null,
  data: null,
  loading: false,
  working: false,
  done: '',
})

const absence = reactive({
  rows: [],
  /** attendanceId đang được tick — mặc định tick hết, bỏ tick dòng giáo viên thật sự có dạy. */
  picked: new Set(),
  lockedCount: 0,
  lockedPeriods: [],
  reason: '',
  working: false,
  done: '',
})

const pickedCount = computed(() => absence.picked.size)
const allPicked = computed(
  () => absence.rows.length > 0 && absence.picked.size === absence.rows.length,
)

function togglePick(id) {
  if (absence.picked.has(id)) absence.picked.delete(id)
  else absence.picked.add(id)
}

function toggleAll() {
  if (allPicked.value) absence.picked.clear()
  else absence.rows.forEach((r) => absence.picked.add(r.attendanceId))
}

async function checkImpact(h) {
  impact.open = true
  impact.holiday = h
  impact.data = null
  impact.done = ''
  impact.loading = true
  absence.rows = []
  absence.picked = new Set()
  absence.lockedCount = 0
  absence.lockedPeriods = []
  absence.done = ''
  // Lý do điền sẵn theo tên kỳ nghỉ: hệ thống bắt mọi can thiệp tay phải có lý do, mà bắt gõ
  // tay 50 lần thì chỉ nhận về "sua loi". Vẫn cho sửa nếu đợt này có bối cảnh riêng.
  absence.reason = `${h.name} — buổi không diễn ra, lịch sinh trước khi khai báo kỳ nghỉ`
  try {
    const [imp, abs] = await Promise.all([holidayApi.impact(h.id), holidayApi.absences(h.id)])
    impact.data = imp.data
    absence.rows = abs.data?.rows ?? []
    absence.lockedCount = abs.data?.lockedCount ?? 0
    absence.lockedPeriods = abs.data?.lockedPeriods ?? []
    absence.rows.forEach((r) => absence.picked.add(r.attendanceId))
    // Không có gì vướng thì đừng bắt người dùng đóng một hộp thoại rỗng — nhưng phải NÓI RA.
    // Đóng im lặng làm nút "Buổi dạy" trông y hệt một nút hỏng, và người dùng bỏ qua luôn
    // kỳ nghỉ đó thay vì biết rằng hệ thống đã kiểm tra và không có gì phải sửa.
    const nothing =
      !imp.data?.sessionCount &&
      !imp.data?.pastSessionCount &&
      !absence.rows.length &&
      !absence.lockedCount
    if (nothing) {
      impact.open = false
      showToast(`"${h.name}" không vướng buổi dạy nào — không phải xử lý gì.`)
    }
  } catch (e) {
    // Mất quyền hoặc backend lỗi mà nuốt lặng còn tệ hơn: người dùng tưởng đã kiểm tra xong
    // và yên tâm rằng lịch sạch, trong khi buổi dạy vẫn nằm nguyên trong kỳ nghỉ.
    impact.open = false
    showToast(e.response?.data?.message || 'Không kiểm tra được buổi dạy của kỳ nghỉ này.', 'error')
  } finally {
    impact.loading = false
  }
}

/**
 * Đóng hộp thoại, và nạp lại danh sách nếu vừa có thao tác.
 *
 * Nút "Buổi dạy" vẽ theo issueCount của lần tải trước. Dọn xong mà không tải lại thì nút vẫn
 * nằm đó, người dùng bấm lại và chỉ nhận về "không vướng buổi nào" — trông như thao tác vừa
 * rồi không ăn.
 */
function closeImpact() {
  const worked = !!impact.done || !!absence.done
  impact.open = false
  if (worked) load()
}

async function doCancelSessions() {
  impact.working = true
  try {
    const res = await holidayApi.cancelSessions(impact.holiday.id)
    impact.done = `Đã hủy ${res.data?.cancelled ?? 0} buổi dạy.`
    impact.data = { ...impact.data, sessionCount: 0 }
  } catch (e) {
    impact.done = e.response?.data?.message || 'Không hủy được. Thử lại.'
  } finally {
    impact.working = false
  }
}

/** Chuyển các dòng Vắng đã tick sang Nghỉ phép — KHÔNG đụng tới tiền, chỉ sạch hồ sơ. */
async function doFixAbsences() {
  if (!absence.picked.size) return
  absence.working = true
  try {
    const res = await holidayApi.fixAbsences(impact.holiday.id, {
      attendanceIds: [...absence.picked],
      reason: absence.reason.trim(),
    })
    const fixed = res.data?.fixed ?? 0
    absence.done = `Đã chuyển ${fixed} dòng sang Nghỉ phép và báo cho giáo viên liên quan.`
    absence.rows = absence.rows.filter((r) => !absence.picked.has(r.attendanceId))
    absence.picked = new Set()
  } catch (e) {
    absence.done = e.response?.data?.message || 'Không sửa được. Thử lại.'
  } finally {
    absence.working = false
  }
}

/* ───────────────────────── Xóa ───────────────────────── */

const deleteTarget = ref(null)
const deleting = ref(false)
/** Hậu quả kỳ nghỉ này đã ghi vào dữ liệu — nạp khi mở hộp thoại, null = chưa có/không lấy được. */
const deleteImpact = ref(null)

async function askDelete(h) {
  deleteTarget.value = h
  deleteImpact.value = null
  try {
    const res = await holidayApi.deleteImpact(h.id)
    deleteImpact.value = res.data
  } catch {
    // Không lấy được số liệu thì vẫn cho xóa — chỉ mất phần cảnh báo, không chặn việc chính.
  }
}

/** Những gì xóa kỳ nghỉ KHÔNG hoàn lại — hiện thành danh sách trong hộp thoại. */
const deleteBlockers = computed(() => {
  const d = deleteImpact.value
  if (!d) return []
  const out = []
  if (d.cancelledSessions)
    out.push({
      label: `${d.cancelledSessions} buổi dạy đã hủy trong khoảng ngày này — KHÔNG sống lại`,
    })
  if (d.leaveAttendances)
    out.push({ label: `${d.leaveAttendances} dòng chấm công đang là Nghỉ phép — giữ nguyên` })
  if (d.futureSessions)
    out.push({ label: `${d.futureSessions} buổi chưa diễn ra sẽ chạy lại bình thường` })
  return out
})

async function confirmDelete() {
  deleting.value = true
  try {
    const name = deleteTarget.value.name
    await holidayApi.remove(deleteTarget.value.id)
    deleteTarget.value = null
    showToast(`Đã chuyển "${name}" vào thùng rác`)
    await load()
  } catch (e) {
    showToast(e.response?.data?.message || 'Không xóa được.', 'error')
  } finally {
    deleting.value = false
  }
}

/* ───────────────────────── Khôi phục ───────────────────────── */

const restoreTarget = ref(null)
const restoring = ref(false)

async function confirmRestore() {
  restoring.value = true
  try {
    const name = restoreTarget.value.name
    await holidayApi.restore(restoreTarget.value.id)
    restoreTarget.value = null
    showToast(`Đã khôi phục "${name}"`)
    await load()
  } catch (e) {
    showToast(e.response?.data?.message || 'Không khôi phục được.', 'error')
  } finally {
    restoring.value = false
  }
}

onMounted(async () => {
  await load()
  try {
    const res = await schoolApi.list({ page: 0, size: 200 })
    schools.value = res.data?.content ?? []
  } catch {
    schools.value = []
  }
  // Cảnh báo bên Bảng lương trỏ sang đây kèm ?focus=<id>: mở thẳng hộp thoại xử lý của đúng
  // kỳ nghỉ đó. Đẩy người dùng tới danh sách rồi để họ tự dò lại là làm hỏng nửa sau của
  // chuỗi "phát hiện → sửa".
  const focusId = Number(route.query.focus)
  if (focusId) {
    try {
      const res = await holidayApi.detail(focusId)
      await checkImpact(res.data)
    } catch {
      /* kỳ nghỉ đã bị xóa — im lặng, danh sách vẫn dùng được bình thường */
    }
  }
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="title">Lịch nghỉ</h2>
      </div>
      <button v-if="tab === 'active'" class="btn btn-primary" @click="openCreate">
        + Thêm kỳ nghỉ
      </button>
    </div>

    <div class="tabs">
      <button :class="{ on: tab === 'active' }" @click="switchTab('active')">Đang dùng</button>
      <button :class="{ on: tab === 'trash' }" @click="switchTab('trash')">Thùng rác</button>
    </div>

    <FilterBar
      v-model="filter.keyword"
      placeholder="Tên kỳ nghỉ…"
      aria-label="Tìm kỳ nghỉ theo tên"
      @apply="applyFilter"
      @clear="clearFilter"
    >
      <template v-if="tab === 'active'">
        <label class="field">
          <span>Loại</span>
          <select v-model="filter.kind" @change="applyFilter">
            <option value="">Tất cả</option>
            <option v-for="k in KINDS" :key="k.value" :value="k.value">{{ k.label }}</option>
          </select>
        </label>

        <label class="field">
          <span>Phạm vi</span>
          <select v-model="filter.schoolId" @change="applyFilter">
            <option value="">Toàn hệ thống</option>
            <option v-for="s in schools" :key="s.id" :value="s.id">{{ s.name }}</option>
          </select>
        </label>

        <label class="field">
          <span>Từ ngày</span>
          <DateField v-model="filter.from" @update:model-value="applyFilter" />
        </label>

        <label class="field">
          <span>Đến ngày</span>
          <DateField v-model="filter.to" @update:model-value="applyFilter" />
        </label>
      </template>
    </FilterBar>

    <p v-if="error" class="error-msg">{{ error }}</p>

    <p v-if="!loading" class="total small text-muted">
      Tổng cộng <strong>{{ total }}</strong> kỳ nghỉ
    </p>

    <p v-if="loading" class="card empty">Đang tải...</p>
    <p v-else-if="items.length === 0" class="card empty">Chưa có kỳ nghỉ nào</p>

    <div v-else class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th width="180">Thời gian</th>
            <th width="70">Số ngày</th>
            <th>Tên kỳ nghỉ</th>
            <th width="160">Loại</th>
            <th>Phạm vi</th>
            <th width="150">Thao tác</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="r in rows" :key="r.key">
            <!-- Mốc năm: bảng dài thì đọc tới giữa không phải tự nhẩm "cái này của năm nào". -->
            <tr v-if="r.type === 'year'" class="year-row">
              <td colspan="6">Năm {{ r.year }}</td>
            </tr>

            <tr v-else :class="{ 'row--past': phase(r.h) === 'past' }">
              <td class="col-range">
                <div class="mono">{{ fmtRange(r.h) }}</div>
                <span v-if="tab === 'active' && phase(r.h) === 'now'" class="badge badge-amber">
                  Đang nghỉ
                </span>
                <span
                  v-else-if="tab === 'active' && phase(r.h) === 'past'"
                  class="badge badge-gray"
                >
                  Đã qua
                </span>
              </td>
              <td>{{ r.h.dayCount }}</td>
              <td>
                <div class="font-medium">{{ r.h.name }}</div>
                <div v-if="r.h.needsReview" class="warn-tag" :title="r.h.note">
                  Cần rà soát — ngày suy từ âm lịch hoặc do Chính phủ chốt từng năm
                </div>
                <div v-else-if="r.h.note" class="desc-text">{{ r.h.note }}</div>
              </td>
              <td>
                <span class="badge" :class="kindMeta(r.h.kind).badge">
                  {{ kindMeta(r.h.kind).label }}
                </span>
              </td>
              <td>
                <span v-if="r.h.schoolId" class="small font-medium">
                  {{ r.h.schoolName || '(trường đã xóa)' }}
                </span>
                <span v-else class="small text-muted">Toàn hệ thống</span>
              </td>
              <td class="col-actions">
                <template v-if="tab === 'active'">
                  <!-- Không vướng buổi nào thì không vẽ nút: bấm vào chỉ nhận về một câu
                       "không có gì" — thà bỏ hẳn để mắt dừng đúng ở dòng cần xử lý. -->
                  <button
                    v-if="issuesFailed || issues.has(r.h.id)"
                    class="link"
                    @click="checkImpact(r.h)"
                  >
                    Buổi dạy
                  </button>
                  <button class="link" @click="openEdit(r.h)">Sửa</button>
                  <button class="link link--danger" @click="askDelete(r.h)">Xóa</button>
                </template>
                <button v-else class="link" @click="restoreTarget = r.h">Khôi phục</button>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <Pagination :model-value="page" :total-pages="totalPages" @update:model-value="goPage" />

    <!-- ============ Modal thêm/sửa ============ -->
    <div v-if="modal.open" class="modal-overlay" @click.self="modal.open = false">
      <div class="modal-box">
        <h3>{{ modal.mode === 'create' ? 'Thêm kỳ nghỉ' : 'Sửa kỳ nghỉ' }}</h3>

        <label class="check">
          <input v-model="modal.oneDay" type="checkbox" />
          <span>Nghỉ đúng một ngày</span>
        </label>

        <div class="grid2">
          <div class="form-group">
            <label>{{ modal.oneDay ? 'Ngày nghỉ *' : 'Từ ngày *' }}</label>
            <DateField v-model="modal.form.fromDate" :invalid="!!modal.errors.fromDate" />
            <small v-if="modal.errors.fromDate" class="err">{{ modal.errors.fromDate }}</small>
          </div>

          <div v-if="!modal.oneDay" class="form-group">
            <label>Đến ngày *</label>
            <DateField
              v-model="modal.form.toDate"
              :min="modal.form.fromDate"
              :invalid="!!modal.errors.toDate"
            />
            <small v-if="modal.errors.toDate" class="err">{{ modal.errors.toDate }}</small>
          </div>
        </div>

        <div class="form-group">
          <label>Tên kỳ nghỉ *</label>
          <input v-model="modal.form.name" placeholder="VD: Nghỉ Tết Nguyên đán" />
          <small v-if="modal.errors.name" class="err">{{ modal.errors.name }}</small>
        </div>

        <div class="grid2">
          <div class="form-group">
            <label>Loại</label>
            <select v-model="modal.form.kind">
              <option v-for="k in KINDS" :key="k.value" :value="k.value">{{ k.label }}</option>
            </select>
          </div>

          <div class="form-group">
            <label>Phạm vi</label>
            <select v-model="modal.form.schoolId">
              <option value="">Toàn hệ thống</option>
              <option v-for="s in schools" :key="s.id" :value="s.id">Chỉ {{ s.name }}</option>
            </select>
          </div>
        </div>
        <p class="hint">
          Để "Toàn hệ thống" cho ngày lễ và nghỉ hè. Chọn một trường khi chỉ trường đó nghỉ — ví dụ
          trường sửa chữa, tổ chức sự kiện riêng.
        </p>

        <div class="form-group">
          <label>Ghi chú</label>
          <input v-model="modal.form.note" placeholder="Nguồn thông báo, lý do nghỉ..." />
        </div>

        <p v-if="modal.error" class="error-msg">{{ modal.error }}</p>

        <div class="modal-actions">
          <button class="btn btn-outline" @click="modal.open = false">Hủy</button>
          <button class="btn btn-primary" :disabled="modal.saving" @click="save">
            {{ modal.saving ? 'Đang lưu...' : 'Lưu' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ============ Modal: buổi dạy vướng kỳ nghỉ ============ -->
    <div v-if="impact.open" class="modal-overlay" @click.self="closeImpact">
      <div class="modal-box modal-lg">
        <h3 class="title-tight">Buổi dạy rơi vào kỳ nghỉ</h3>
        <p class="modal-sub">
          {{ impact.holiday?.name }} · {{ impact.holiday && fmtRange(impact.holiday) }}
        </p>

        <p v-if="impact.loading" class="text-muted">Đang kiểm tra...</p>

        <template v-else>
          <!-- ─── Phần 1: buổi CHƯA diễn ra — hủy là xong ─── -->
          <h4 class="sec-title">1 · Buổi chưa diễn ra</h4>

          <template v-if="impact.data">
            <p v-if="impact.data.sessionCount > 0" class="note note--warn">
              Có <strong>{{ impact.data.sessionCount }}</strong> buổi dạy chưa diễn ra của
              <strong>{{ impact.data.teacherCount }}</strong> giáo viên đang nằm trong kỳ nghỉ này
              (từ {{ fmt(impact.data.firstDate) }} đến {{ fmt(impact.data.lastDate) }}).
              <br />
              Chúng được sinh ra TRƯỚC khi kỳ nghỉ được khai báo nên vẫn còn trong lịch. Không hủy
              thì hệ thống sẽ tự ghi vắng cho giáo viên vào ngày trường đóng cửa.
            </p>
            <p v-else class="note note--ok">
              Không còn buổi dạy nào chưa diễn ra vướng kỳ nghỉ này.
            </p>

            <p v-if="impact.data.pastSessionCount > 0" class="small text-muted">
              Ngoài ra có {{ impact.data.pastSessionCount }} buổi ĐÃ diễn ra trong khoảng này — giữ
              nguyên, không hủy: chúng có thể đã gắn chấm công và đã vào bảng lương.
            </p>

            <p v-if="impact.done" class="note">{{ impact.done }}</p>

            <div v-if="impact.data.sessionCount > 0" class="sec-actions">
              <button class="btn btn-danger" :disabled="impact.working" @click="doCancelSessions">
                {{ impact.working ? 'Đang hủy...' : `Hủy ${impact.data.sessionCount} buổi dạy` }}
              </button>
            </div>
          </template>

          <!-- ─── Phần 2: buổi ĐÃ diễn ra — hủy không cứu được, phải sửa chấm công ─── -->
          <h4 class="sec-title">2 · Dòng chấm công Vắng của buổi đã qua</h4>

          <p v-if="!absence.rows.length && !absence.lockedCount" class="note note--ok">
            Không có dòng Vắng nào do hệ thống ghi nhầm trong kỳ nghỉ này.
          </p>

          <template v-if="absence.rows.length">
            <p class="note note--warn">
              <strong>{{ absence.rows.length }}</strong> dòng chấm công đang ghi
              <strong>Vắng</strong> cho buổi rơi vào kỳ nghỉ. Hủy buổi KHÔNG xóa được các dòng này —
              chúng nằm trong hồ sơ chuyên cần của giáo viên, và job nền đã nhắn cho họ là đã vắng
              buổi đó.
              <br />
              Chuyển sang <strong>Nghỉ phép</strong> không làm đổi tiền lương (cả hai đều không tính
              tiết) — nó chỉ trả lại hồ sơ đúng sự thật: hôm đó trường không hoạt động.
            </p>

            <div class="abs-wrap">
              <table class="table abs-table">
                <thead>
                  <tr>
                    <th width="36">
                      <input type="checkbox" :checked="allPicked" @change="toggleAll" />
                    </th>
                    <th width="110">Ngày</th>
                    <th>Giáo viên</th>
                    <th>Ghi chú của hệ thống</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="r in absence.rows"
                    :key="r.attendanceId"
                    :class="{ 'row--off': !absence.picked.has(r.attendanceId) }"
                  >
                    <td>
                      <input
                        type="checkbox"
                        :checked="absence.picked.has(r.attendanceId)"
                        @change="togglePick(r.attendanceId)"
                      />
                    </td>
                    <td class="mono">{{ fmt(r.workDate) }}</td>
                    <td>{{ r.teacherName }}</td>
                    <td class="desc-text">{{ r.note || '—' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div class="form-group">
              <label>Lý do điều chỉnh *</label>
              <input v-model="absence.reason" placeholder="Vì sao sửa các dòng này..." />
              <small>Ghi vào từng dòng chấm công và lưu vĩnh viễn trong nhật ký thay đổi.</small>
            </div>
          </template>

          <p v-if="absence.lockedCount > 0" class="small text-muted">
            Còn <strong>{{ absence.lockedCount }}</strong> dòng thuộc kỳ lương ĐÃ CHỐT ({{
              absence.lockedPeriods.join(', ')
            }}) nên chưa sửa được. Vào Bảng lương mở lại kỳ đó rồi quay lại đây.
          </p>

          <p v-if="absence.done" class="note">{{ absence.done }}</p>
        </template>

        <div class="modal-actions">
          <button class="btn btn-outline" @click="closeImpact">Đóng</button>
          <button
            v-if="absence.rows.length"
            class="btn btn-primary"
            :disabled="absence.working || !pickedCount || !absence.reason.trim()"
            @click="doFixAbsences"
          >
            {{ absence.working ? 'Đang sửa...' : `Chuyển ${pickedCount} dòng sang Nghỉ phép` }}
          </button>
        </div>
      </div>
    </div>

    <!-- Xác nhận xóa: kể đủ hậu quả ĐÃ ghi rồi mới cho bấm -->
    <ConfirmDialog
      v-if="deleteTarget"
      title="Chuyển kỳ nghỉ vào thùng rác?"
      :name="`${deleteTarget.name} (${fmtRange(deleteTarget)})`"
      :blockers="deleteBlockers"
      :busy="deleting"
      confirm-text="Xóa"
      danger
      @confirm="confirmDelete"
      @cancel="deleteTarget = null"
    >
      Lịch dạy sinh ra sau đó sẽ lại có buổi vào những ngày này. Khôi phục lại được ở tab Thùng rác,
      nhưng những gì kỳ nghỉ đã ghi vào dữ liệu thì không tự hoàn lại:
    </ConfirmDialog>

    <ConfirmDialog
      v-if="restoreTarget"
      title="Khôi phục kỳ nghỉ?"
      :name="`${restoreTarget.name} (${fmtRange(restoreTarget)})`"
      :busy="restoring"
      confirm-text="Khôi phục"
      @confirm="confirmRestore"
      @cancel="restoreTarget = null"
    >
      Từ giờ lịch dạy sẽ không sinh buổi vào những ngày này nữa. Buổi ĐÃ sinh trước đó vẫn còn — bấm
      "Buổi dạy" ở danh sách chính để dọn.
    </ConfirmDialog>
  </div>
</template>

<style scoped>
/* Khung trang, nút, bảng, badge, form và modal đều lấy từ assets/page-common.css — ở đây
   chỉ khai những gì RIÊNG của Lịch nghỉ. */

/* ── Tab Đang dùng / Thùng rác ── */
.tabs {
  display: inline-flex;
  margin-bottom: 1rem;
  border: 1px solid var(--c-border);
  border-radius: 10px;
  overflow: hidden;
  background: var(--c-surface);
}
.tabs button {
  border: none;
  background: transparent;
  padding: 0.45rem 1.1rem;
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--c-text-muted);
  cursor: pointer;
}
.tabs button.on {
  background: var(--grad-primary);
  color: #fff;
}

.total {
  margin: 0 0 0.6rem;
}
.empty {
  padding: 2.2rem;
  text-align: center;
  color: var(--c-text-muted);
}

/* ── Bảng ── */
/* Nhãn cột, cột ngày, cụm thao tác và badge đều không được xuống dòng: viên thuốc gãy
   đôi trông như lỗi hiển thị, còn ngày tháng bẻ dòng thì khó đọc. */
.table th,
.col-range,
.col-actions,
.badge {
  white-space: nowrap;
}
/* Mốc năm là dòng phân cách chứ không phải dữ liệu: nền phẳng và không sáng lên khi rê
   chuột (luật hover của .table trong page-common). */
.year-row td {
  padding: 0.4rem 1rem;
  background: var(--c-bg);
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.4px;
  text-transform: uppercase;
  color: var(--c-text-muted);
}
.table tbody .year-row:hover td {
  background: var(--c-bg);
}

.col-range .badge {
  margin-top: 0.25rem;
}
/* Kỳ nghỉ đã qua vẫn phải giữ lại (là căn cứ của lịch cũ) nhưng không cần bắt mắt. Làm
   nhạt CHỮ chứ không hạ opacity cả dòng: nhãn "Đã qua" phải còn đọc được. */
.row--past {
  color: var(--c-text-muted);
}
.desc-text {
  margin-top: 0.2rem;
  font-size: 0.78rem;
  color: var(--c-text-muted);
}
.warn-tag {
  margin-top: 0.2rem;
  font-size: 0.78rem;
  font-weight: 600;
  color: #b45309;
}
:root[data-theme='dark'] .warn-tag {
  color: var(--c-amber);
}

.link {
  border: none;
  background: none;
  cursor: pointer;
  padding: 0 0.35rem;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--c-accent);
}
.link:hover {
  text-decoration: underline;
}
.link--danger {
  color: var(--c-danger);
}

/* ── Hộp thoại ── */
.check {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.9rem;
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
}
.err {
  color: var(--c-danger);
  font-size: 0.76rem;
}
.hint {
  margin: -0.3rem 0 0.9rem;
  font-size: 0.76rem;
  color: var(--c-text-muted);
}
/* Tên kỳ nghỉ đi liền dưới tiêu đề, không để khoảng trống 1rem của h3 chen vào giữa. */
.title-tight {
  margin-bottom: 0.25rem;
}
.modal-sub {
  margin: 0 0 1rem;
  font-size: 0.85rem;
  color: var(--c-text-muted);
}

/* ── Hai phần của hộp thoại "Buổi dạy" ── */
.sec-title {
  margin: 1.2rem 0 0.6rem;
  padding-top: 0.9rem;
  border-top: 1px solid var(--c-border);
  font-size: 0.95rem;
  font-weight: 700;
}
.sec-title:first-of-type {
  margin-top: 0;
  padding-top: 0;
  border-top: 0;
}
.note {
  margin: 0 0 0.7rem;
  padding: 0.7rem 0.9rem;
  border-radius: 10px;
  background: var(--c-surface-2);
  font-size: 0.86rem;
  line-height: 1.55;
}
.note--warn {
  background: rgba(239, 68, 68, 0.1);
}
.note--ok {
  background: rgba(34, 197, 94, 0.12);
}
.sec-actions {
  display: flex;
  justify-content: flex-end;
}
.abs-wrap {
  max-height: 260px;
  overflow-y: auto;
  margin-bottom: 0.9rem;
  border: 1px solid var(--c-border);
  border-radius: 10px;
}
.abs-table {
  font-size: 0.82rem;
}
.abs-table th,
.abs-table td {
  padding: 0.45rem 0.6rem;
}
/* Cuộn danh sách dài vẫn phải biết cột nào là cột nào. */
.abs-table th {
  position: sticky;
  top: 0;
  z-index: 1;
}
/* Dòng bị bỏ tick — làm mờ để thấy ngay cái gì sẽ KHÔNG bị sửa. */
.row--off {
  opacity: 0.45;
}

@media (max-width: 700px) {
  .grid2 {
    grid-template-columns: 1fr;
  }
  .page-head {
    flex-direction: column;
  }
}
</style>
