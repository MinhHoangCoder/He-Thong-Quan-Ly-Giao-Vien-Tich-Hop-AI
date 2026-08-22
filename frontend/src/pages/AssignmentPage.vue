<script setup>
/**
 * Trang PHÂN CÔNG GIẢNG DẠY — danh sách + các thao tác duyệt/hủy/thùng rác.
 *
 * Việc TẠO và SỬA nằm ở trang riêng ({@link AssignmentFormPage}) chứ không còn trong modal:
 * bước xếp tiết là lưới thời khóa biểu cho từng trường, modal không đủ chỗ.
 *
 * Một phiếu nay trải được NHIỀU TRƯỜNG (V27) nên cột "Trường" và "Lớp" đều là tập hợp —
 * quá hai cái thì rút gọn "TH Dư Hàng +2" để dòng không tràn.
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { assignmentApi } from '@/api/assignments'
import { tietShort } from '@/utils/period'
import Pagination from '@/components/ui/Pagination.vue'

const router = useRouter()

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

const loading = ref(false)
const items = ref([])
const trashItems = ref([])
const statusCounts = ref({})

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
function applySearch() {
  clearTimeout(searchTimer)
  load()
}
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

const cancelTarget = ref(null) // Hủy → đưa vào thùng rác

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

/* ── Điều hướng sang trang tạo/sửa ── */
function openCreate() {
  router.push({ name: 'assignment-new' })
}
function openEdit(a) {
  router.push({ name: 'assignment-edit', params: { id: a.id } })
}

/* ── Nhãn hiển thị ── */

/** "2026-08-17" → "17/08/2026" (rỗng/null → '—'). */
function fmtDate(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso ?? '')
  return m ? `${m[3]}/${m[2]}/${m[1]}` : '—'
}

/**
 * Danh sách trường/lớp của phiếu.
 *
 * <p>Hiện ĐỦ, không rút gọn "+N" nữa: rút gọn thì người xem thấy "1A1, 1A2 +2" mà không có
 * cách nào biết hai lớp còn lại là lớp nào — đúng thứ cột này sinh ra để trả lời.
 */
function nameList(joined) {
  return joined || '—'
}

/** Phiếu có trải nhiều trường không — để hiện tên trường trên từng chip tiết. */
function isMultiSchool(a) {
  return new Set((a.slots ?? []).map((s) => s.schoolId).filter(Boolean)).size > 1
}

/* ── Hạn xác nhận: đếm ngược + mức cảnh báo ── */

function deadlineLevel(a) {
  if (a.status !== 'PENDING' || !a.confirmDeadline) return null
  const left = new Date(a.confirmDeadline).getTime() - Date.now()
  if (left <= 0) return 'over'
  return left <= DEADLINE_WARN_HOURS * 3600_000 ? 'warn' : 'ok'
}

function deadlineText(a) {
  if (!a.confirmDeadline) return '—'
  const left = new Date(a.confirmDeadline).getTime() - Date.now()
  if (left <= 0) return 'quá hạn'
  const hours = Math.floor(left / 3600_000)
  if (hours >= 24) return `còn ${Math.floor(hours / 24)} ngày`
  if (hours >= 1) return `còn ${hours} giờ`
  return `còn ${Math.max(1, Math.round(left / 60_000))} phút`
}

function deadlineFull(a) {
  if (!a.confirmDeadline) return ''
  const d = new Date(a.confirmDeadline)
  const p = (n) => String(n).padStart(2, '0')
  return `Hạn xác nhận: ${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/* ── Tích chọn + thao tác hàng loạt ── */

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

/* Khôi phục từ thùng rác (có thể bị chặn nếu trùng lịch / trùng lớp). */
async function restoreItem(a) {
  try {
    await assignmentApi.restore(a.id)
    loadTrash()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Khôi phục thất bại')
  }
}
</script>

<template>
  <div class="page apg">
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
            <th>Lịch trong tuần</th>
            <th>Trạng thái</th>
            <th class="col-actions">Hành động</th>
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
            <td>{{ nameList(a.schoolName) }}</td>
            <td>{{ nameList(a.className) }}</td>
            <td>{{ a.subjectName }}</td>
            <td class="text-muted small mono">
              {{ fmtDate(a.startDate) }} → {{ fmtDate(a.endDate) }}
            </td>
            <td>
              <span v-for="s in a.slots" :key="s.id" class="chip"
                >{{ s.dayOfWeekLabel }} ·
                {{ tietShort(s.periodNumber, s.sessionType, s.indexInSession)
                }}<template v-if="s.className"> · {{ s.className }}</template
                ><template v-if="isMultiSchool(a) && s.schoolName">
                  <span class="chip__school">{{ s.schoolName }}</span> </template
              ></span>
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
            </td>
            <td class="col-actions">
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
                  title="Sửa rồi gửi lại lời mời (đổi được giáo viên và trường)"
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
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <Pagination v-model="page" :total-pages="totalPages" />

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
  </div>
</template>

<style scoped>
/* Trang này là BẢNG NHIỀU CỘT, không phải trang đọc chữ: trần 1200px của .page dùng chung
   khiến màn rộng bỏ trống hẳn một mảng bên phải trong khi cột "Lịch trong tuần" lại phải
   xuống dòng liên tục. Cho bảng dùng hết bề ngang thật của khung nội dung. */
.apg {
  max-width: none;
}
.head-actions {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}
/* Ô tìm kiếm phân công — khung bo viền + nhãn + nút Lọc/Xóa lọc */
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
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--c-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}
.field input {
  padding: 0.5rem 0.7rem;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  font-size: 0.9rem;
  background: var(--c-surface);
  color: var(--c-text);
}
.field input:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.12);
}
.filter-actions {
  display: flex;
  gap: 0.5rem;
  align-items: flex-end;
}

/* ── Tab trạng thái ── */
.status-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-bottom: 0.9rem;
}
.status-tab {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.4rem 0.85rem;
  border: 1px solid var(--c-border);
  border-radius: 9999px;
  background: var(--c-surface);
  color: var(--c-text-muted);
  font-size: 0.84rem;
  font-weight: 600;
  cursor: pointer;
  transition: 0.15s;
}
.status-tab:hover {
  border-color: var(--c-primary);
  color: var(--c-primary);
}
.status-tab--on {
  border-color: var(--c-primary);
  background: var(--c-bg);
  color: var(--c-primary);
}
.status-tab__n {
  min-width: 20px;
  padding: 0 0.3rem;
  border-radius: 9999px;
  background: var(--c-surface-2);
  font-size: 0.72rem;
  text-align: center;
}
.status-tab--on .status-tab__n {
  background: var(--grad-primary);
  color: #fff;
}

/* ── Thanh thao tác hàng loạt ── */
.bulk-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 0.9rem;
  margin-bottom: 0.8rem;
  border: 1px solid var(--c-primary);
  border-radius: 12px;
  background: var(--c-bg);
}
.bulk-bar__count {
  margin-right: auto;
  font-size: 0.85rem;
  font-weight: 700;
}

/* ── Ô trong bảng ── */
/* Tiêu đề cột viết hoa nên rất dễ gãy làm hai dòng ("TRẠNG / THÁI", "GIÁO / VIÊN"), làm hàng
   tiêu đề cao gấp đôi và nhìn như lỗi. Tiêu đề luôn ngắn nên giữ nguyên dòng là an toàn. */
.table th {
  white-space: nowrap;
}
/* Nhãn trạng thái cũng vậy: "Đang dạy" bị bẻ thành "Đang / dạy" trông như hỏng. */
.badge {
  white-space: nowrap;
}
.col-check {
  width: 36px;
}
/* Cột Hành động.
   KHÔNG dùng class .actions dùng chung ở đây: nó đặt display:flex, mà biến chính thẻ <td>
   thành flex container thì ô mất vai trò table-cell — trình duyệt không tính được bề rộng
   cột nữa, nút trôi lên đỉnh ô và tràn sang cột Trạng thái. Giữ <td> là table-cell, cho
   các nút nằm ngang bằng inline-block + nowrap.
   width:1% là thủ thuật bảng quen thuộc: ép cột co sát nội dung, nhường chỗ cho cột Lịch. */
.col-actions {
  width: 1%;
  white-space: nowrap;
  text-align: right;
  vertical-align: middle;
}
.col-actions .btn {
  /* Nút viền (.btn-outline có border 1px) và nút đặc (.btn-danger không border) cao lệch nhau
     2px, nên căn theo baseline HAY theo tâm đều còn so le 1px — muốn hàng nút phẳng thì phải
     ép CÙNG CHIỀU CAO. box-sizing:border-box để viền không cộng thêm vào con số này.
     Chỉ áp trong cột này, không đụng .btn dùng chung của toàn app. */
  display: inline-flex;
  align-items: center;
  box-sizing: border-box;
  height: 30px;
  vertical-align: middle;
}
.col-actions .btn + .btn {
  margin-left: 0.35rem;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  margin: 0 0.25rem 0.25rem 0;
  padding: 0.16rem 0.5rem;
  border: 1px solid var(--c-border);
  border-radius: 9999px;
  background: var(--c-surface);
  font-size: 0.74rem;
  white-space: nowrap;
}
.chip__school {
  color: var(--c-text-muted);
  font-size: 0.68rem;
}
.deadline {
  margin-top: 0.2rem;
  font-size: 0.72rem;
  font-weight: 700;
}
.deadline--ok {
  color: var(--c-text-muted);
}
.deadline--warn {
  color: #b45309;
}
.deadline--over {
  color: var(--c-danger);
}
:root[data-theme='dark'] .deadline--warn {
  color: #fbbf24;
}
.reject-why {
  margin-top: 0.2rem;
  max-width: 190px;
  font-size: 0.72rem;
  font-style: italic;
  color: var(--c-text-muted);
}
.badge-blue {
  background: rgba(37, 99, 235, 0.12);
  color: #1d4ed8;
}
:root[data-theme='dark'] .badge-blue {
  color: #93c5fd;
}
</style>
