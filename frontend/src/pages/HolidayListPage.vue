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
import { holidayApi } from '@/api/holidays'
import { schoolApi } from '@/api/schools'
import DateField from '@/components/ui/DateField.vue'

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
const pageSize = 20

const schools = ref([])

const filter = reactive({ keyword: '', kind: '', schoolId: '', from: '', to: '' })

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

/** Kỳ nghỉ đã trôi qua hoàn toàn — làm mờ đi để mắt bám vào phần sắp tới. */
const todayIso = new Date().toISOString().slice(0, 10)
const isPast = (h) => h.toDate < todayIso

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
    if (filter.kind) params.kind = filter.kind
    if (filter.schoolId) params.schoolId = filter.schoolId
    if (filter.from) params.from = filter.from
    if (filter.to) params.to = filter.to
    const res = await holidayApi.list(params)
    items.value = res.data?.content ?? []
    total.value = res.data?.totalElements ?? 0
  } catch (e) {
    error.value = e.response?.data?.message || 'Không tải được lịch nghỉ.'
  } finally {
    loading.value = false
  }
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

const impact = reactive({ open: false, holiday: null, data: null, loading: false, working: false, done: '' })

async function checkImpact(h) {
  impact.open = true
  impact.holiday = h
  impact.data = null
  impact.done = ''
  impact.loading = true
  try {
    const res = await holidayApi.impact(h.id)
    impact.data = res.data
    // Không có buổi nào vướng thì không bắt người dùng đóng một hộp thoại vô nghĩa.
    if (!res.data?.sessionCount && !res.data?.pastSessionCount) impact.open = false
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

/* ───────────────────────── Xóa ───────────────────────── */

const deleteTarget = ref(null)
const deleting = ref(false)

async function confirmDelete() {
  deleting.value = true
  try {
    await holidayApi.remove(deleteTarget.value.id)
    deleteTarget.value = null
    await load()
  } catch (e) {
    error.value = e.response?.data?.message || 'Không xóa được.'
  } finally {
    deleting.value = false
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
})
</script>

<template>
  <div class="page">
    <div class="page__head">
      <div>
        <h1 class="page__title">Lịch nghỉ</h1>
        <p class="page__sub">
          Ngày lễ và kỳ nghỉ khai ở đây thì hệ thống KHÔNG sinh buổi dạy vào những ngày đó.
        </p>
      </div>
      <button class="btn" @click="openCreate">+ Thêm kỳ nghỉ</button>
    </div>

    <div class="filter-bar">
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
          <option value="">Toàn hệ thống + mọi trường</option>
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

      <label class="field field--wide">
        <span>Tìm kiếm</span>
        <input v-model="filter.keyword" placeholder="Tên kỳ nghỉ..." @keyup.enter="applyFilter" />
      </label>

      <div class="filter-actions">
        <button class="btn" @click="applyFilter">Lọc</button>
        <button class="btn btn--ghost" @click="clearFilter">Xóa lọc</button>
      </div>
    </div>

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
              <button class="link" @click="checkImpact(h)">Buổi dạy</button>
              <button class="link" @click="openEdit(h)">Sửa</button>
              <button class="link link--danger" @click="deleteTarget = h">Xóa</button>
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
      <div class="modal-box">
        <h2 class="modal-title">Buổi dạy rơi vào kỳ nghỉ</h2>
        <p class="modal-sub">{{ impact.holiday?.name }} · {{ impact.holiday && fmtRange(impact.holiday) }}</p>

        <p v-if="impact.loading">Đang kiểm tra...</p>

        <template v-else-if="impact.data">
          <p v-if="impact.data.sessionCount > 0" class="impact-warn">
            Có <strong>{{ impact.data.sessionCount }}</strong> buổi dạy chưa diễn ra của
            <strong>{{ impact.data.teacherCount }}</strong> giáo viên đang nằm trong kỳ nghỉ này
            (từ {{ fmt(impact.data.firstDate) }} đến {{ fmt(impact.data.lastDate) }}).
            <br />
            Chúng được sinh ra TRƯỚC khi kỳ nghỉ được khai báo nên vẫn còn trong lịch. Không hủy
            thì hệ thống sẽ tự ghi vắng cho giáo viên vào ngày trường đóng cửa, và trừ vào lương.
          </p>
          <p v-else class="impact-ok">Không còn buổi dạy nào chưa diễn ra vướng kỳ nghỉ này.</p>

          <p v-if="impact.data.pastSessionCount > 0" class="impact-note">
            Ngoài ra có {{ impact.data.pastSessionCount }} buổi ĐÃ diễn ra trong khoảng này —
            giữ nguyên, không hủy: chúng có thể đã gắn chấm công và đã vào bảng lương.
          </p>

          <p v-if="impact.done" class="msg">{{ impact.done }}</p>
        </template>

        <div class="modal-actions">
          <button class="btn btn--ghost" @click="impact.open = false">Đóng</button>
          <button
            v-if="impact.data?.sessionCount > 0"
            class="btn btn--danger"
            :disabled="impact.working"
            @click="doCancelSessions"
          >
            {{ impact.working ? 'Đang hủy...' : `Hủy ${impact.data.sessionCount} buổi dạy` }}
          </button>
        </div>
      </div>
    </div>

    <!-- ============ Modal xác nhận xóa ============ -->
    <div v-if="deleteTarget" class="modal" @click.self="deleteTarget = null">
      <div class="modal-box modal-box--sm">
        <h2 class="modal-title">Xóa kỳ nghỉ?</h2>
        <p>
          Xóa <strong>{{ deleteTarget.name }}</strong> ({{ fmtRange(deleteTarget) }}).
          Lịch dạy sinh ra sau đó sẽ lại có buổi vào những ngày này.
        </p>
        <p class="impact-note">
          Các buổi đã hủy theo kỳ nghỉ này KHÔNG tự sống lại — nếu cần thì xếp lại qua Phân công.
        </p>
        <div class="modal-actions">
          <button class="btn btn--ghost" @click="deleteTarget = null">Hủy</button>
          <button class="btn btn--danger" :disabled="deleting" @click="confirmDelete">
            {{ deleting ? 'Đang xóa...' : 'Xóa' }}
          </button>
        </div>
      </div>
    </div>
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
.page__sub {
  margin: 6px 0 0;
  color: var(--c-text-muted);
  font-size: 14px;
  max-width: 62ch;
}

/* ================= Filter ================= */
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 18px;
  margin-bottom: 18px;
  background: var(--c-surface);
  border-radius: 14px;
  border: 1px solid var(--c-border);
}
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 170px;
}
.field--wide {
  flex: 1;
}
.field span {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
}
.field input,
.field select {
  height: 40px;
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  padding: 0 12px;
  font-size: 14px;
  background: var(--c-surface);
  color: var(--c-text);
}
.field input:focus,
.field select:focus {
  outline: none;
  border-color: var(--c-primary);
}
.filter-actions {
  display: flex;
  align-items: flex-end;
  gap: 10px;
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

@media (max-width: 700px) {
  .grid2 {
    grid-template-columns: 1fr;
  }
  .page__head {
    flex-direction: column;
  }
}
</style>
