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
import { PAGE_SIZE } from '@/utils/pagination'
import { isoToday } from '@/utils/format'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import FilterBar from '@/components/ui/FilterBar.vue'
import { useToast } from '@/composables/useToast'

const route = useRoute()
const { showToast } = useToast()

/** 'active' = đang dùng · 'trash' = đã xóa. Hai danh sách dùng chung bảng bên dưới. */
const tab = ref('active')

const KINDS = [
  { value: 'NATIONAL', label: 'Lễ theo luật' },
  { value: 'BREAK', label: 'Kỳ nghỉ của học sinh' },
  { value: 'CENTER', label: 'Trung tâm nghỉ riêng' },
]
const kindLabel = (v) => KINDS.find((k) => k.value === v)?.label ?? v

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
 * Kỳ nghỉ đã trôi qua hoàn toàn — làm mờ đi để mắt bám vào phần sắp tới.
 *
 * Gọi isoToday() mỗi lần thay vì tính sẵn một hằng số lúc load: trang này hay được mở suốt
 * ngày, hằng số sẽ đứng yên qua nửa đêm.
 */
const isPast = (h) => h.toDate < isoToday()

function fmt(iso) {
  if (!iso) return '—'
  const [y, m, d] = iso.split('-')
  return `${d}/${m}/${y}`
}

/** "12/02/2026" cho nghỉ 1 ngày, "12/02/2026 → 22/02/2026" cho kỳ dài. */
function fmtRange(h) {
  return h.fromDate === h.toDate ? fmt(h.fromDate) : `${fmt(h.fromDate)} → ${fmt(h.toDate)}`
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
    const res = tab.value === 'trash' ? await holidayApi.trash(params) : await holidayApi.list(params)
    items.value = res.data?.content ?? []
    total.value = res.data?.totalElements ?? 0
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
const impact = reactive({ open: false, holiday: null, data: null, loading: false, working: false, done: '' })

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
const allPicked = computed(() => absence.rows.length > 0 && absence.picked.size === absence.rows.length)

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
    // Không có gì vướng thì đừng bắt người dùng đóng một hộp thoại rỗng.
    const nothing =
      !imp.data?.sessionCount && !imp.data?.pastSessionCount && !absence.rows.length && !absence.lockedCount
    if (nothing) impact.open = false
  } catch {
    impact.open = false
  } finally {
    impact.loading = false
  }
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
  if (d.cancelledSessions) out.push({ label: `${d.cancelledSessions} buổi dạy đã hủy trong khoảng ngày này — KHÔNG sống lại` })
  if (d.leaveAttendances) out.push({ label: `${d.leaveAttendances} dòng chấm công đang là Nghỉ phép — giữ nguyên` })
  if (d.futureSessions) out.push({ label: `${d.futureSessions} buổi chưa diễn ra sẽ chạy lại bình thường` })
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
    <div class="page__head">
      <div>
        <h1 class="page__title">Lịch nghỉ</h1>
      </div>
      <button v-if="tab === 'active'" class="btn" @click="openCreate">+ Thêm kỳ nghỉ</button>
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

    <p v-if="error" class="msg msg--error">{{ error }}</p>

    <p v-if="!loading" class="total">
      Tổng cộng <strong>{{ total }}</strong> kỳ nghỉ
    </p>

    <p v-if="loading" class="empty-state">Đang tải...</p>
    <p v-else-if="items.length === 0" class="empty-state">Chưa có kỳ nghỉ nào</p>

    <div v-else class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Thời gian</th>
            <th width="70">Số ngày</th>
            <th>Tên kỳ nghỉ</th>
            <th width="150">Loại</th>
            <th>Phạm vi</th>
            <th width="150">Thao tác</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="h in items" :key="h.id" :class="{ 'row--past': isPast(h) }">
            <td class="col-range">{{ fmtRange(h) }}</td>
            <td>{{ h.dayCount }}</td>
            <td>
              <div class="name-text">{{ h.name }}</div>
              <div v-if="h.needsReview" class="warn-tag" :title="h.note">
                Cần rà soát — ngày suy từ âm lịch hoặc do Chính phủ chốt từng năm
              </div>
              <div v-else-if="h.note" class="desc-text">{{ h.note }}</div>
            </td>
            <td>
              <span class="badge" :class="'badge--' + h.kind.toLowerCase()">{{ kindLabel(h.kind) }}</span>
            </td>
            <td>
              <span v-if="h.schoolId" class="scope scope--school">{{ h.schoolName || '(trường đã xóa)' }}</span>
              <span v-else class="scope">Toàn hệ thống</span>
            </td>
            <td class="col-actions">
              <template v-if="tab === 'active'">
                <button class="link" @click="checkImpact(h)">Buổi dạy</button>
                <button class="link" @click="openEdit(h)">Sửa</button>
                <button class="link link--danger" @click="askDelete(h)">Xóa</button>
              </template>
              <button v-else class="link" @click="restoreTarget = h">Khôi phục</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="totalPages > 1" class="pagination">
      <button class="btn btn--ghost" :disabled="page === 0" @click="goPage(page - 1)">Trước</button>
      <span>Trang {{ page + 1 }} / {{ totalPages }}</span>
      <button class="btn btn--ghost" :disabled="page + 1 >= totalPages" @click="goPage(page + 1)">
        Sau
      </button>
    </div>

    <!-- ============ Modal thêm/sửa ============ -->
    <div v-if="modal.open" class="modal" @click.self="modal.open = false">
      <div class="modal-box">
        <h2 class="modal-title">{{ modal.mode === 'create' ? 'Thêm kỳ nghỉ' : 'Sửa kỳ nghỉ' }}</h2>

        <label class="check">
          <input v-model="modal.oneDay" type="checkbox" />
          <span>Nghỉ đúng một ngày</span>
        </label>

        <div class="grid2">
          <label class="field">
            <span>{{ modal.oneDay ? 'Ngày nghỉ *' : 'Từ ngày *' }}</span>
            <DateField v-model="modal.form.fromDate" :invalid="!!modal.errors.fromDate" />
            <small v-if="modal.errors.fromDate" class="err">{{ modal.errors.fromDate }}</small>
          </label>

          <label v-if="!modal.oneDay" class="field">
            <span>Đến ngày *</span>
            <DateField
              v-model="modal.form.toDate"
              :min="modal.form.fromDate"
              :invalid="!!modal.errors.toDate"
            />
            <small v-if="modal.errors.toDate" class="err">{{ modal.errors.toDate }}</small>
          </label>
        </div>

        <label class="field">
          <span>Tên kỳ nghỉ *</span>
          <input v-model="modal.form.name" placeholder="VD: Nghỉ Tết Nguyên đán" />
          <small v-if="modal.errors.name" class="err">{{ modal.errors.name }}</small>
        </label>

        <div class="grid2">
          <label class="field">
            <span>Loại</span>
            <select v-model="modal.form.kind">
              <option v-for="k in KINDS" :key="k.value" :value="k.value">{{ k.label }}</option>
            </select>
          </label>

          <label class="field">
            <span>Phạm vi</span>
            <select v-model="modal.form.schoolId">
              <option value="">Toàn hệ thống</option>
              <option v-for="s in schools" :key="s.id" :value="s.id">Chỉ {{ s.name }}</option>
            </select>
          </label>
        </div>
        <p class="hint">
          Để "Toàn hệ thống" cho ngày lễ và nghỉ hè. Chọn một trường khi chỉ trường đó nghỉ —
          ví dụ trường sửa chữa, tổ chức sự kiện riêng.
        </p>

        <label class="field">
          <span>Ghi chú</span>
          <input v-model="modal.form.note" placeholder="Nguồn thông báo, lý do nghỉ..." />
        </label>

        <p v-if="modal.error" class="msg msg--error">{{ modal.error }}</p>

        <div class="modal-actions">
          <button class="btn btn--ghost" @click="modal.open = false">Hủy</button>
          <button class="btn" :disabled="modal.saving" @click="save">
            {{ modal.saving ? 'Đang lưu...' : 'Lưu' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ============ Modal: buổi dạy vướng kỳ nghỉ ============ -->
    <div v-if="impact.open" class="modal" @click.self="impact.open = false">
      <div class="modal-box modal-box--lg">
        <h2 class="modal-title">Buổi dạy rơi vào kỳ nghỉ</h2>
        <p class="modal-sub">{{ impact.holiday?.name }} · {{ impact.holiday && fmtRange(impact.holiday) }}</p>

        <p v-if="impact.loading">Đang kiểm tra...</p>

        <template v-else>
          <!-- ─── Phần 1: buổi CHƯA diễn ra — hủy là xong ─── -->
          <h3 class="sec-title">1 · Buổi chưa diễn ra</h3>

          <template v-if="impact.data">
            <p v-if="impact.data.sessionCount > 0" class="impact-warn">
              Có <strong>{{ impact.data.sessionCount }}</strong> buổi dạy chưa diễn ra của
              <strong>{{ impact.data.teacherCount }}</strong> giáo viên đang nằm trong kỳ nghỉ này
              (từ {{ fmt(impact.data.firstDate) }} đến {{ fmt(impact.data.lastDate) }}).
              <br />
              Chúng được sinh ra TRƯỚC khi kỳ nghỉ được khai báo nên vẫn còn trong lịch. Không hủy
              thì hệ thống sẽ tự ghi vắng cho giáo viên vào ngày trường đóng cửa.
            </p>
            <p v-else class="impact-ok">Không còn buổi dạy nào chưa diễn ra vướng kỳ nghỉ này.</p>

            <p v-if="impact.data.pastSessionCount > 0" class="impact-note">
              Ngoài ra có {{ impact.data.pastSessionCount }} buổi ĐÃ diễn ra trong khoảng này —
              giữ nguyên, không hủy: chúng có thể đã gắn chấm công và đã vào bảng lương.
            </p>

            <p v-if="impact.done" class="msg">{{ impact.done }}</p>

            <div v-if="impact.data.sessionCount > 0" class="sec-actions">
              <button class="btn btn--danger" :disabled="impact.working" @click="doCancelSessions">
                {{ impact.working ? 'Đang hủy...' : `Hủy ${impact.data.sessionCount} buổi dạy` }}
              </button>
            </div>
          </template>

          <!-- ─── Phần 2: buổi ĐÃ diễn ra — hủy không cứu được, phải sửa chấm công ─── -->
          <h3 class="sec-title">2 · Dòng chấm công Vắng của buổi đã qua</h3>

          <p v-if="!absence.rows.length && !absence.lockedCount" class="impact-ok">
            Không có dòng Vắng nào do hệ thống ghi nhầm trong kỳ nghỉ này.
          </p>

          <template v-if="absence.rows.length">
            <p class="impact-warn">
              <strong>{{ absence.rows.length }}</strong> dòng chấm công đang ghi
              <strong>Vắng</strong> cho buổi rơi vào kỳ nghỉ. Hủy buổi KHÔNG xóa được các dòng
              này — chúng nằm trong hồ sơ chuyên cần của giáo viên, và job nền đã nhắn cho họ là
              đã vắng buổi đó.
              <br />
              Chuyển sang <strong>Nghỉ phép</strong> không làm đổi tiền lương (cả hai đều không
              tính tiết) — nó chỉ trả lại hồ sơ đúng sự thật: hôm đó trường không hoạt động.
            </p>

            <div class="abs-wrap">
              <table class="abs-table">
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
                    <td>{{ fmt(r.workDate) }}</td>
                    <td>{{ r.teacherName }}</td>
                    <td class="desc-text">{{ r.note || '—' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <label class="field">
              <span>Lý do điều chỉnh *</span>
              <input v-model="absence.reason" placeholder="Vì sao sửa các dòng này..." />
              <small class="hint-inline">
                Ghi vào từng dòng chấm công và lưu vĩnh viễn trong nhật ký thay đổi.
              </small>
            </label>
          </template>

          <p v-if="absence.lockedCount > 0" class="impact-note">
            Còn <strong>{{ absence.lockedCount }}</strong> dòng thuộc kỳ lương ĐÃ CHỐT
            ({{ absence.lockedPeriods.join(', ') }}) nên chưa sửa được. Vào Bảng lương mở lại kỳ
            đó rồi quay lại đây.
          </p>

          <p v-if="absence.done" class="msg">{{ absence.done }}</p>
        </template>

        <div class="modal-actions">
          <button class="btn btn--ghost" @click="impact.open = false">Đóng</button>
          <button
            v-if="absence.rows.length"
            class="btn"
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
      Lịch dạy sinh ra sau đó sẽ lại có buổi vào những ngày này. Khôi phục lại được ở tab
      Thùng rác, nhưng những gì kỳ nghỉ đã ghi vào dữ liệu thì không tự hoàn lại:
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
      Từ giờ lịch dạy sẽ không sinh buổi vào những ngày này nữa. Buổi ĐÃ sinh trước đó vẫn
      còn — bấm "Buổi dạy" ở danh sách chính để dọn.
    </ConfirmDialog>
  </div>
</template>

<style scoped>
.page {
  max-width: 1280px;
  margin: auto;
}

/* ================= Header ================= */
.page__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 22px;
}
.page__title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  color: var(--c-text);
}
.page__desc {
  margin: 6px 0 0;
  max-width: 640px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--c-text-muted);
}

/* ================= Tabs ================= */
.tabs {
  display: inline-flex;
  margin-bottom: 16px;
  border: 1px solid var(--c-border);
  border-radius: 10px;
  overflow: hidden;
  background: var(--c-surface);
}
.tabs button {
  border: none;
  background: transparent;
  padding: 8px 18px;
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text-muted);
  cursor: pointer;
}
.tabs button.on {
  background: var(--grad-primary);
  color: #fff;
}

/* ================= Buttons ================= */
.btn {
  border: none;
  cursor: pointer;
  border-radius: 8px;
  padding: 10px 18px;
  font-weight: 600;
  background: var(--c-primary);
  color: #fff;
  transition: 0.2s;
}
.btn:hover:not(:disabled) {
  transform: translateY(-1px);
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn--ghost {
  background: var(--c-surface-2);
  color: var(--c-text);
}
.btn--danger {
  background: var(--c-danger);
  color: #fff;
}

.link {
  border: none;
  background: none;
  cursor: pointer;
  padding: 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-accent);
}
.link:hover {
  text-decoration: underline;
}
.link--danger {
  color: var(--c-danger);
}

/* ================= Table ================= */
.total {
  color: var(--c-text-muted);
  font-size: 14px;
  margin: 0 0 10px;
}
.table-wrap {
  overflow-x: auto;
  background: var(--c-surface);
  border-radius: 14px;
  border: 1px solid var(--c-border);
}
table {
  width: 100%;
  border-collapse: collapse;
}
thead {
  background: var(--c-surface-2);
}
th {
  padding: 14px;
  text-align: left;
  font-size: 13px;
  font-weight: 700;
  color: var(--c-text);
  white-space: nowrap;
}
td {
  padding: 14px;
  border-top: 1px solid var(--c-border);
  font-size: 14px;
  vertical-align: top;
}
tbody tr:hover {
  background: var(--c-surface-2);
}
/* Kỳ nghỉ đã qua: vẫn phải giữ lại (là căn cứ của lịch cũ) nhưng không cần bắt mắt. */
.row--past {
  opacity: 0.55;
}
.col-range {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.col-actions {
  white-space: nowrap;
}
.name-text {
  font-weight: 600;
}
.desc-text {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--c-text-muted);
}
.warn-tag {
  margin-top: 4px;
  font-size: 12.5px;
  font-weight: 600;
  color: #b45309;
}
:root[data-theme='dark'] .warn-tag {
  color: var(--c-amber);
}

.badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: var(--c-surface-2);
  color: var(--c-text-muted);
  white-space: nowrap;
}
.badge--national {
  background: rgba(239, 68, 68, 0.12);
  color: #b91c1c;
}
.badge--break {
  background: rgba(37, 99, 235, 0.12);
  color: #1d4ed8;
}
.badge--center {
  background: rgba(249, 115, 22, 0.14);
  color: #c2410c;
}

.scope {
  font-size: 13px;
  color: var(--c-text-muted);
}
.scope--school {
  color: var(--c-text);
  font-weight: 600;
}

.empty-state {
  text-align: center;
  color: var(--c-text-muted);
  padding: 35px;
  background: var(--c-surface);
  border-radius: 14px;
  border: 1px solid var(--c-border);
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 16px;
  color: var(--c-text-muted);
  font-size: 14px;
}

.msg {
  padding: 10px 14px;
  border-radius: 8px;
  background: var(--c-surface-2);
  font-size: 14px;
}
.msg--error {
  background: rgba(239, 68, 68, 0.1);
  color: #b91c1c;
}

/* ================= Modal ================= */
.modal {
  position: fixed;
  inset: 0;
  background: rgba(8, 20, 38, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  z-index: 60;
}
.modal-box {
  width: 100%;
  max-width: 620px;
  max-height: 90vh;
  overflow-y: auto;
  background: var(--c-surface);
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.modal-box--sm {
  max-width: 460px;
}
/* Hộp thoại "Buổi dạy" chứa cả bảng chấm công nên cần rộng hơn hộp thoại nhập liệu. */
.modal-box--lg {
  max-width: 860px;
}
.modal-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}
.modal-sub {
  margin: -8px 0 0;
  color: var(--c-text-muted);
  font-size: 14px;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}
.grid2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.check {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.hint {
  margin: -6px 0 0;
  font-size: 12.5px;
  color: var(--c-text-muted);
}
.err {
  color: var(--c-danger);
  font-size: 12.5px;
}

.impact-warn {
  margin: 0;
  padding: 12px 14px;
  border-radius: 10px;
  background: rgba(239, 68, 68, 0.1);
  font-size: 14px;
  line-height: 1.55;
}
.impact-ok {
  margin: 0;
  padding: 12px 14px;
  border-radius: 10px;
  background: rgba(34, 197, 94, 0.12);
  font-size: 14px;
}
.impact-note {
  margin: 0;
  font-size: 13px;
  color: var(--c-text-muted);
}

/* ===== Hai phần của hộp thoại "Buổi dạy" ===== */
.sec-title {
  margin: 6px 0 0;
  padding-top: 12px;
  border-top: 1px solid var(--c-border);
  font-size: 15px;
  font-weight: 700;
}
.sec-title:first-of-type {
  border-top: 0;
  padding-top: 0;
}
.sec-actions {
  display: flex;
  justify-content: flex-end;
}
.abs-wrap {
  max-height: 260px;
  overflow-y: auto;
  border: 1px solid var(--c-border);
  border-radius: 10px;
}
.abs-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13.5px;
}
.abs-table th,
.abs-table td {
  padding: 8px 10px;
  text-align: left;
  border-bottom: 1px solid var(--c-border);
}
.abs-table th {
  position: sticky;
  top: 0;
  background: var(--c-surface);
  font-weight: 600;
}
.abs-table tr:last-child td {
  border-bottom: 0;
}
/* Dòng bị bỏ tick — làm mờ để thấy ngay cái gì sẽ KHÔNG bị sửa. */
.row--off {
  opacity: 0.45;
}
.hint-inline {
  font-size: 12.5px;
  color: var(--c-text-muted);
}

@media (max-width: 700px) {
  .grid2 {
    grid-template-columns: 1fr;
  }
  .page__head {
    flex-direction: column;
  }
}
</style>
