<script setup>
/**
 * Trang Phân công giảng dạy: danh sách phân công + tạo mới (chọn GV/môn/trường/lớp,
 * khoảng thời gian và các tiết Thứ+Tiết). Khi tạo, backend tự trải các tiết thành
 * buổi dạy (Schedule) — nguồn cho Chấm công & Bảng lương.
 */
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { assignmentApi } from '@/api/assignments'
import { tietLabel, tietShort } from '@/utils/period'
import Pagination from '@/components/ui/Pagination.vue'

const DAYS = [
  { code: 'MON', label: 'Thứ 2' },
  { code: 'TUE', label: 'Thứ 3' },
  { code: 'WED', label: 'Thứ 4' },
  { code: 'THU', label: 'Thứ 5' },
  { code: 'FRI', label: 'Thứ 6' },
  { code: 'SAT', label: 'Thứ 7' },
  { code: 'SUN', label: 'Chủ nhật' },
]
const STATUS_LABEL = { ACTIVE: 'Đang chạy', COMPLETED: 'Hoàn thành', CANCELLED: 'Đã hủy' }

// Ngày hôm nay theo GIỜ ĐỊA PHƯƠNG (yyyy-MM-dd). Tránh toISOString() vì nó quy về UTC →
// ở múi giờ VN (UTC+7) lúc rạng sáng (00:00–07:00) sẽ trả nhầm về ngày hôm qua.
const isoToday = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const loading = ref(false)
const items = ref([])
const trashItems = ref([])

/* Chế độ xem: 'list' = danh sách phân công | 'trash' = thùng rác (đã xóa mềm). */
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

// Các phân công ACTIVE của GV đang chọn — dùng để KHÓA tiết mà GV đã có lịch dạy
// (khớp luật chống trùng 409 ở backend: cùng Thứ + đúng periodId + giai đoạn chồng ngày).
const teacherBusy = ref([])

const modal = reactive({
  open: false,
  saving: false,
  error: '',
  form: {
    teacherId: '',
    subjectId: '',
    schoolId: '',
    classId: '',
    startDate: isoToday(),
    endDate: '',
    slots: [],
  },
  slotDraft: { dayOfWeek: 'MON', periodId: '' },
})

const cancelTarget = ref(null) // Hủy → đưa vào thùng rác
const purgeTarget = ref(null) // xóa vĩnh viễn khỏi thùng rác

async function load() {
  loading.value = true
  page.value = 0
  try {
    const { data } = await assignmentApi.list({ keyword: search.value })
    items.value = data
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
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
    classId: '',
    startDate: isoToday(),
    endDate: '',
    slots: [],
  })
  modal.slotDraft = { dayOfWeek: 'MON', periodId: '' }
  modal.error = ''
  modal.open = true
  scoped.classes = []
  scoped.periods = []
  teacherBusy.value = []
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
  modal.form.classId = ''
  modal.form.slots = []
  scoped.classes = []
  scoped.periods = []
  if (!modal.form.schoolId) return
  try {
    const { data } = await assignmentApi.schoolOptions(modal.form.schoolId)
    scoped.classes = data.classes
    scoped.periods = data.periods
    pickFirstFreePeriod()
  } catch {
    /* giữ trống */
  }
}

/* ── Khóa tiết GV đã có lịch ──
   Nạp phân công ACTIVE của GV khi đổi Giáo viên; suy ra tiết bận theo Thứ (lọc theo
   giai đoạn chồng ngày với phân công đang tạo) rồi disable đúng option tiết đó. */
async function onTeacherChange() {
  teacherBusy.value = []
  const tid = modal.form.teacherId
  if (!tid) return
  try {
    const { data } = await assignmentApi.list({ teacherId: Number(tid) })
    teacherBusy.value = (data || []).filter((a) => a.status === 'ACTIVE')
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

// { 'MON': Set(periodId), ... } — tiết GV đã bận, chỉ tính phân công chồng ngày với form.
const busyByDay = computed(() => {
  const map = {}
  const ns = modal.form.startDate
  const ne = modal.form.endDate
  for (const a of teacherBusy.value) {
    if (a.status !== 'ACTIVE') continue
    if (ns && !rangesOverlap(ns, ne, a.startDate, a.endDate)) continue
    for (const s of a.slots || []) {
      ;(map[s.dayOfWeek] ??= new Set()).add(Number(s.periodId))
    }
  }
  return map
})

// Lý do 1 tiết bị khóa với 1 Thứ: 'busy' = GV đã có lịch | 'added' = đã thêm trong form | ''.
function periodTakenReason(period, day) {
  const pid = Number(period.id)
  if (busyByDay.value[day]?.has(pid)) return 'busy'
  if (modal.form.slots.some((s) => s.dayOfWeek === day && Number(s.periodId) === pid))
    return 'added'
  return ''
}
function periodTakenSuffix(period, day) {
  const r = periodTakenReason(period, day)
  return r === 'busy' ? ' — đã có lịch' : r === 'added' ? ' — đã thêm' : ''
}

// Lý do khóa của tiết đang chọn ở ô nháp: 'busy' | 'added' | '' (để disable nút + báo đúng chữ).
const draftReason = computed(() => {
  const p = scoped.periods.find((x) => Number(x.id) === Number(modal.slotDraft.periodId))
  return p ? periodTakenReason(p, modal.slotDraft.dayOfWeek) : ''
})

// Nhảy ô tiết về tiết TRỐNG đầu tiên cho Thứ đang chọn (tránh dừng ở tiết vừa thêm/đã bận).
function pickFirstFreePeriod() {
  const day = modal.slotDraft.dayOfWeek
  const free = scoped.periods.find((p) => !periodTakenReason(p, day))
  modal.slotDraft.periodId = free ? free.id : ''
}

function addSlot() {
  const { dayOfWeek, periodId } = modal.slotDraft
  if (!periodId) return
  const period = scoped.periods.find((x) => Number(x.id) === Number(periodId))
  // Chặn tiết GV đã có lịch / đã thêm (khớp luật 409 backend).
  if (period && periodTakenReason(period, dayOfWeek)) return
  modal.form.slots.push({ dayOfWeek, periodId: Number(periodId) })
  pickFirstFreePeriod() // tự chuyển sang tiết trống kế tiếp
}

function removeSlot(i) {
  modal.form.slots.splice(i, 1)
}

function slotLabel(s) {
  const d = DAYS.find((x) => x.code === s.dayOfWeek)?.label ?? s.dayOfWeek
  const p = scoped.periods.find((x) => x.id === s.periodId)
  return `${d} · ${p ? tietLabel(p.periodNumber, p.sessionType) : 'Tiết #' + s.periodId}`
}

const canSubmit = computed(
  () =>
    modal.form.teacherId &&
    modal.form.subjectId &&
    modal.form.schoolId &&
    modal.form.classId &&
    modal.form.startDate &&
    modal.form.slots.length > 0,
)

async function submit() {
  if (!canSubmit.value) {
    modal.error = 'Vui lòng điền đủ GV, môn, trường, lớp, ngày bắt đầu và ít nhất 1 tiết.'
    return
  }
  modal.saving = true
  modal.error = ''
  try {
    await assignmentApi.create({
      teacherId: Number(modal.form.teacherId),
      subjectId: Number(modal.form.subjectId),
      schoolId: Number(modal.form.schoolId),
      classId: Number(modal.form.classId),
      startDate: modal.form.startDate,
      endDate: modal.form.endDate || null,
      slots: modal.form.slots,
    })
    modal.open = false
    load()
  } catch (e) {
    modal.error = e.response?.data?.message ?? 'Tạo phân công thất bại'
  } finally {
    modal.saving = false
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
        <template v-if="!inTrash">
          <button class="btn btn-outline" @click="showTrash">Thùng rác</button>
          <button class="btn btn-primary" @click="openCreate">+ Tạo phân công</button>
        </template>
        <button v-else class="btn btn-outline" @click="showList">← Quay lại danh sách</button>
      </div>
    </div>

    <!-- Tìm kiếm phân công theo GV/trường/lớp/môn (chỉ ở danh sách đang hoạt động) -->
    <div v-if="!inTrash" class="filter-bar">
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

    <div class="table-wrap">
      <table class="table">
        <thead>
          <tr>
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
            <td colspan="8" class="text-center text-muted">Đang tải…</td>
          </tr>
          <tr v-else-if="!currentList.length">
            <td colspan="8" class="text-center text-muted">
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
            <td class="font-medium">{{ a.teacherName }}</td>
            <td>{{ a.schoolName }}</td>
            <td>{{ a.className ?? '—' }}</td>
            <td>{{ a.subjectName }}</td>
            <td class="text-muted small">
              {{ a.startDate }} → {{ a.endDate ?? 'không giới hạn' }}
            </td>
            <td>
              <span v-for="s in a.slots" :key="s.id" class="chip"
                >{{ s.dayOfWeekLabel }} · {{ tietShort(s.periodNumber, s.sessionType) }}</span
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
                }"
                >{{ STATUS_LABEL[a.status] ?? a.status }}</span
              >
            </td>
            <td class="actions">
              <template v-if="!inTrash">
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
        <h3>Tạo phân công</h3>

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
            <select v-model="modal.form.subjectId">
              <option value="">-- Chọn môn --</option>
              <option v-for="s in options.subjects" :key="s.id" :value="s.id">{{ s.name }}</option>
            </select>
          </div>
          <div class="form-group">
            <label>Trường *</label>
            <select v-model="modal.form.schoolId" @change="onSchoolChange">
              <option value="">-- Chọn trường --</option>
              <option v-for="s in options.schools" :key="s.id" :value="s.id">{{ s.name }}</option>
            </select>
          </div>
          <div class="form-group">
            <label>Lớp *</label>
            <select v-model="modal.form.classId" :disabled="!scoped.classes.length">
              <option value="">
                {{ scoped.classes.length ? '-- Chọn lớp --' : 'Chọn trường trước' }}
              </option>
              <option v-for="c in scoped.classes" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </div>
          <div class="form-group">
            <label>Ngày bắt đầu *</label>
            <input type="date" v-model="modal.form.startDate" />
          </div>
          <div class="form-group">
            <label>Ngày kết thúc</label>
            <input type="date" v-model="modal.form.endDate" />
            <small>Bỏ trống = sinh lịch 8 tuần từ ngày bắt đầu.</small>
          </div>
        </div>

        <!-- Slots -->
        <div class="slots-block">
          <label class="slots-label">Các tiết dạy trong tuần *</label>
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
                :disabled="!!periodTakenReason(p, modal.slotDraft.dayOfWeek)"
              >
                {{ tietLabel(p.periodNumber, p.sessionType)
                }}{{ periodTakenSuffix(p, modal.slotDraft.dayOfWeek) }}
              </option>
            </select>
            <button
              class="btn btn-outline btn-sm"
              type="button"
              :disabled="!modal.slotDraft.periodId || !!draftReason"
              @click="addSlot"
            >
              + Thêm tiết
            </button>
          </div>
          <p v-if="draftReason === 'busy'" class="slot-hint slot-hint--warn">
            Giáo viên đã có lịch dạy tiết này — vui lòng chọn tiết khác.
          </p>
          <p v-else-if="draftReason === 'added'" class="slot-hint slot-hint--muted">
            Tiết này đã có trong danh sách bên dưới.
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

        <div class="modal-actions">
          <button class="btn btn-outline" @click="modal.open = false">Hủy</button>
          <button class="btn btn-primary" :disabled="modal.saving || !canSubmit" @click="submit">
            {{ modal.saving ? 'Đang lưu…' : 'Tạo phân công' }}
          </button>
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
/* Chữ đậm chìm trên nền tối → dùng tông sáng hơn */
:root[data-theme='dark'] .chip {
  color: #a5b4fc;
}
:root[data-theme='dark'] .badge-blue {
  color: #93c5fd;
}
</style>
