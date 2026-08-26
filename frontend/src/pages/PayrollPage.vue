<script setup>
/**
 * Trang Bảng lương: tính theo kỳ (tháng/năm) = lương cứng + SỐ TIẾT × ĐƠN GIÁ + phụ cấp
 * + thưởng − khấu trừ. "Tính lương" tổng hợp số tiết từ Chấm công.
 *
 * VÒNG ĐỜI MỘT PHIẾU: Nháp → Đã chốt → Đã trả. Mỗi bước đi một chiều và có rào riêng:
 * 1. CHỐT khóa luôn chấm công của kỳ. Nếu kỳ còn dòng VẮNG ghi nhầm cho buổi rơi vào ngày lễ
 *    (buổi sinh trước khi khai kỳ nghỉ — V29) thì chốt là khóa luôn lỗi vào trong, nên bấm
 *    Chốt lúc đó sẽ hỏi lại một lần.
 * 2. MỞ LẠI (V32) gỡ được bước 1 — cần quyền PAYROLL_REOPEN, bắt buộc nêu lý do.
 * 3. ĐÃ TRẢ (V38) là điểm không quay lại: tiền đã ra khỏi quỹ. Cần quyền PAYROLL_PAY.
 *    Trước V38 trạng thái này không có đường nào đặt được, nên "đã chốt" và "đã trả" là một.
 *
 * Nút "Bảng đơn giá" mở barem tiết dạy (V38) — trước đó hai mức giá là hằng số trong code.
 */
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { payrollApi, payRateApi } from '@/api/payroll'
import { useAuthStore } from '@/stores/auth'
import Pagination from '@/components/ui/Pagination.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import { useToast } from '@/composables/useToast'
import { taiFile, loiTaiFile } from '@/utils/download'
import { isoToday } from '@/utils/format'

const router = useRouter()
const auth = useAuthStore()
const { showToast } = useToast()

/** Nút chỉ hiện với người thật sự bấm được — ADMIN đi tắt như mọi quyền khác. */
const hasPerm = (code) =>
  auth.roles.includes('ADMIN') || (auth.user?.perms ?? []).includes(code)
const canReopen = computed(() => hasPerm('PAYROLL_REOPEN'))
const canPay = computed(() => hasPerm('PAYROLL_PAY'))
const canManageRate = computed(() => hasPerm('PAYRATE_MANAGE'))

const now = new Date()
const filter = reactive({
  year: now.getFullYear(),
  month: now.getMonth() + 1,
})
const years = Array.from({ length: 6 }, (_, i) => now.getFullYear() - i)
const months = Array.from({ length: 12 }, (_, i) => i + 1)

const STATUS = {
  DRAFT: { label: 'Nháp', cls: 'badge-gray' },
  FINALIZED: { label: 'Đã chốt', cls: 'badge-green' },
  PAID: { label: 'Đã trả', cls: 'badge-amber' },
}

const rows = ref([])
const loading = ref(false)
const info = ref('')
/** Ô tìm theo tên giáo viên — lọc tại chỗ, xem ghi chú ở filteredRows. */
const keyword = ref('')

/* ── Tìm + phân trang ──
   Khác Chấm công (phân trang ở server): một kỳ lương chỉ có tối đa vài chục dòng — mỗi giáo
   viên một dòng — nên tải trọn kỳ về là chuyện bình thường, và dòng "Tổng thực nhận" ở chân
   bảng phải cộng CẢ KỲ chứ không phải trang đang xem. Tải trọn rồi lọc tại chỗ vừa đúng số
   vừa gõ tới đâu lọc tới đó. */
const norm = (s) =>
  String(s ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()

const filteredRows = computed(() => {
  const q = norm(keyword.value.trim())
  return q ? rows.value.filter((r) => norm(r.teacherName).includes(q)) : rows.value
})

const PAGE_SIZE = 10
const page = ref(0)
const totalPages = computed(() => Math.ceil(filteredRows.value.length / PAGE_SIZE))
const pagedRows = computed(() => {
  const start = page.value * PAGE_SIZE
  return filteredRows.value.slice(start, start + PAGE_SIZE)
})
// Gõ tìm làm danh sách ngắn lại → trang 5 có thể không còn tồn tại.
watch(filteredRows, () => {
  if (page.value >= totalPages.value) page.value = 0
})

const editModal = reactive({ open: false, saving: false, error: '', row: null, form: {} })

const vnd = (n) =>
  n == null ? '—' : new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(n) + ' ₫'

/** Dòng Vắng rơi vào ngày nghỉ trong kỳ đang xem — dùng cho hộp xác nhận lúc bấm Chốt. */
const issues = ref(null)
const hasIssues = computed(() => (issues.value?.absenceCount ?? 0) > 0)

/**
 * Xuất CẢ KỲ ra Excel, không phải trang đang xem.
 *
 * Bảng lương phân trang 10 dòng ở client trên tập đã tải; nhưng file phải đủ 150 giáo viên,
 * nên server tự lấy trọn kỳ và dựng file. Xuất từ những gì trình duyệt đang giữ là ra một cái
 * file trông đúng mà thiếu dữ liệu — và không có gì báo cho người dùng biết.
 */
const dangXuat = ref(false)

async function xuatExcel() {
  dangXuat.value = true
  try {
    await taiFile(
      '/payroll/export',
      { year: filter.year, month: filter.month },
      `bang-luong_${filter.month}-${filter.year}.xlsx`,
    )
  } catch (e) {
    showToast(await loiTaiFile(e, 'Không xuất được bảng lương'), 'error')
  } finally {
    dangXuat.value = false
  }
}

async function loadIssues() {
  try {
    const { data } = await payrollApi.holidayIssues(filter.year, filter.month)
    issues.value = data
  } catch {
    // Cảnh báo hỏng thì im lặng, không chặn việc chính: người dùng vẫn phải xem được bảng lương.
    issues.value = null
  }
}

async function load() {
  loading.value = true
  info.value = ''
  page.value = 0
  try {
    const { data } = await payrollApi.list(filter.year, filter.month)
    rows.value = data
  } catch (e) {
    rows.value = []
    info.value = e.response?.data?.message ?? 'Không tải được bảng lương'
  } finally {
    loading.value = false
  }
  loadIssues()
}

onMounted(load)

async function generate() {
  info.value = ''
  try {
    const { data } = await payrollApi.generate(filter.year, filter.month)
    rows.value = data
    page.value = 0
    info.value = `Đã tính lương cho ${data.length} giáo viên`
  } catch (e) {
    info.value = e.response?.data?.message ?? 'Tính lương thất bại'
  }
}

function openEdit(r) {
  editModal.open = true
  editModal.saving = false
  editModal.error = ''
  editModal.row = r
  editModal.form = {
    baseSalary: Number(r.baseSalary ?? 0),
    ratePerHour: Number(r.ratePerHour ?? 0),
    allowance: Number(r.allowance ?? 0),
    bonus: Number(r.bonus ?? 0),
    deduction: Number(r.deduction ?? 0),
  }
}

const previewNet = computed(() => {
  const f = editModal.form
  const hours = Number(editModal.row?.taughtHours ?? 0)
  return (
    Number(f.baseSalary || 0) +
    hours * Number(f.ratePerHour || 0) +
    Number(f.allowance || 0) +
    Number(f.bonus || 0) -
    Number(f.deduction || 0)
  )
})

async function saveEdit() {
  editModal.saving = true
  editModal.error = ''
  try {
    await payrollApi.update(editModal.row.id, editModal.form)
    editModal.open = false
    load()
  } catch (e) {
    editModal.error = e.response?.data?.message ?? 'Lưu thất bại'
  } finally {
    editModal.saving = false
  }
}

/* ──────────── Chốt lương: hỏi lại nếu kỳ còn Vắng rơi vào ngày nghỉ ──────────── */

const confirmModal = reactive({ open: false, row: null, working: false })

/**
 * Kỳ sạch thì chốt thẳng, không thêm một cú bấm vô nghĩa. Kỳ còn lỗi mới hỏi lại — cảnh báo
 * hiện ra ở mọi lần bấm sẽ bị bấm qua theo phản xạ trong đúng một tuần.
 */
function finalize(r) {
  if (!hasIssues.value) {
    doFinalize(r)
    return
  }
  confirmModal.open = true
  confirmModal.row = r
  confirmModal.working = false
}

async function doFinalize(r) {
  confirmModal.working = true
  try {
    await payrollApi.finalize(r.id)
    confirmModal.open = false
    showToast(`Đã chốt phiếu lương của ${r.teacherName}`)
    load()
  } catch (e) {
    showToast(e.response?.data?.message ?? 'Chốt lương thất bại', 'error')
  } finally {
    confirmModal.working = false
  }
}

/* ──────────── Xác nhận đã trả lương (V38) ──────────── */

/**
 * row = null → đánh dấu CẢ THÁNG. Kế toán chi lương theo đợt chứ không theo từng người, nên
 * bắt bấm năm chục lần là bắt làm sai quy trình thật.
 */
const payTarget = ref(null) // { row } | { row: null } cho cả tháng; null = đóng
const paying = ref(false)

const finalizedCount = computed(() => rows.value.filter((r) => r.status === 'FINALIZED').length)
const finalizedTotal = computed(() =>
  rows.value
    .filter((r) => r.status === 'FINALIZED')
    .reduce((s, r) => s + Number(r.netAmount || 0), 0),
)

async function doPay() {
  paying.value = true
  try {
    const row = payTarget.value.row
    if (row) {
      await payrollApi.pay(row.id)
      showToast(`Đã đánh dấu đã trả lương cho ${row.teacherName}`)
    } else {
      const { data } = await payrollApi.payPeriod(filter.year, filter.month)
      showToast(`Đã đánh dấu đã trả ${data?.paid ?? 0} phiếu lương kỳ ${filter.month}/${filter.year}`)
    }
    payTarget.value = null
    load()
  } catch (e) {
    showToast(e.response?.data?.message ?? 'Đánh dấu thất bại', 'error')
  } finally {
    paying.value = false
  }
}

/* ──────────── Nhật ký phiếu lương ──────────── */

/**
 * Endpoint /logs và bảng PayrollChangeLog đã có từ V32 nhưng frontend chưa gọi bao giờ —
 * lịch sử chốt/mở lại/trả lương nằm trong DB mà không có đường nào nhìn thấy.
 */
const logModal = reactive({ open: false, row: null, items: [], loading: false })

const LOG_ACTIONS = {
  FINALIZE: 'Chốt lương',
  REOPEN: 'Mở lại',
  PAY: 'Đã trả',
}

async function openLogs(r) {
  logModal.open = true
  logModal.row = r
  logModal.items = []
  logModal.loading = true
  try {
    const { data } = await payrollApi.logs(r.id)
    logModal.items = data
  } catch (e) {
    showToast(e.response?.data?.message ?? 'Không tải được nhật ký', 'error')
    logModal.open = false
  } finally {
    logModal.loading = false
  }
}

const fmtAt = (iso) => (iso ? new Date(iso).toLocaleString('vi-VN') : '—')

/* ──────────── Bảng đơn giá tiết dạy (V38) ──────────── */

/**
 * Trước V38 hai mức giá là hằng số trong PayrollService: tăng giá phải sửa code và deploy.
 * Bảng này để kế toán tự khai, và quan trọng hơn — để người xem bảng lương đối chiếu được
 * con số trên phiếu với barem mà không phải hỏi ai.
 */
const rateModal = reactive({ open: false, items: [], loading: false, error: '', saving: false })
const rateForm = reactive({ gradeFrom: 1, gradeTo: 5, amount: null, effectiveFrom: '', note: '' })

async function openRates() {
  rateModal.open = true
  rateModal.error = ''
  rateModal.loading = true
  try {
    const { data } = await payRateApi.list()
    rateModal.items = data
  } catch (e) {
    rateModal.error = e.response?.data?.message ?? 'Không tải được bảng đơn giá'
  } finally {
    rateModal.loading = false
  }
}

/**
 * Một mức giá CHƯA tới ngày áp dụng thì còn xóa được — đó là mức vừa gõ nhầm.
 *
 * Mức ĐÃ có hiệu lực thì không: nó là căn cứ của những phiếu lương đã trả, xóa đi thì tính
 * lại kỳ cũ ra số khác và không ai giải thích được chênh lệch. Backend chặn lần nữa, ở đây chỉ
 * là không bày ra cái nút mà bấm vào chắc chắn báo lỗi.
 */
const coTheXoaMuc = (r) => canManageRate.value && r.effectiveFrom > isoToday()

async function removeRate(r) {
  rateModal.error = ''
  try {
    await payRateApi.remove(r.id)
    showToast(`Đã xóa mức giá khối ${r.gradeFrom}–${r.gradeTo} áp dụng từ ${r.effectiveFrom}.`)
    await openRates()
  } catch (e) {
    rateModal.error = e.response?.data?.message ?? 'Không xóa được mức giá'
  }
}

async function saveRate() {
  if (!rateForm.amount || !rateForm.effectiveFrom) {
    rateModal.error = 'Vui lòng nhập đơn giá và ngày bắt đầu áp dụng.'
    return
  }
  rateModal.saving = true
  rateModal.error = ''
  try {
    await payRateApi.create({ ...rateForm })
    rateForm.amount = null
    rateForm.note = ''
    showToast('Đã thêm mức giá mới. Mức cũ cùng khối đã được đóng lại.')
    await openRates()
  } catch (e) {
    rateModal.error = e.response?.data?.message ?? 'Không lưu được mức giá'
  } finally {
    rateModal.saving = false
  }
}

/** Từ cảnh báo đi thẳng sang kỳ nghỉ gây lỗi — chỉ ra vấn đề mà không chỉ đường sửa là vô ích. */
function goFixHoliday(holidayId) {
  confirmModal.open = false
  router.push({ path: '/admin/holidays', query: { focus: holidayId } })
}

/* ──────────── Mở lại kỳ lương đã chốt (V32) ──────────── */

const reopenModal = reactive({ open: false, row: null, reason: '', working: false, error: '' })

/** row = null → mở lại CẢ THÁNG (lỗi lịch nghỉ hiếm khi chỉ dính một người). */
function openReopen(row) {
  reopenModal.open = true
  reopenModal.row = row
  reopenModal.reason = ''
  reopenModal.error = ''
  reopenModal.working = false
}

async function doReopen() {
  if (!reopenModal.reason.trim()) return
  reopenModal.working = true
  reopenModal.error = ''
  try {
    if (reopenModal.row) {
      await payrollApi.reopen(reopenModal.row.id, reopenModal.reason.trim())
      info.value = `Đã mở lại phiếu lương của ${reopenModal.row.teacherName}.`
    } else {
      const { data } = await payrollApi.reopenPeriod(filter.year, filter.month, reopenModal.reason.trim())
      info.value = `Đã mở lại ${data?.reopened ?? 0} phiếu lương của kỳ ${filter.month}/${filter.year}.`
    }
    reopenModal.open = false
    load()
  } catch (e) {
    reopenModal.error = e.response?.data?.message ?? 'Mở lại thất bại'
  } finally {
    reopenModal.working = false
  }
}

const totalNet = computed(() => rows.value.reduce((s, r) => s + Number(r.netAmount || 0), 0))
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="title">Bảng lương</h2>
      </div>
    </div>

    <div class="toolbar">
      <label>Tháng</label>
      <select v-model.number="filter.month">
        <option v-for="m in months" :key="m" :value="m">Tháng {{ m }}</option>
      </select>
      <label>Năm</label>
      <select v-model.number="filter.year">
        <option v-for="y in years" :key="y" :value="y">{{ y }}</option>
      </select>
      <button class="btn btn-outline btn-sm" @click="load">Xem</button>
      <span class="divider" />
      <button class="btn btn-primary btn-sm" @click="generate">Tính lương từ chấm công</button>
      <button class="btn btn-outline btn-sm" @click="openRates">Bảng đơn giá</button>
      <button class="btn btn-outline btn-sm" :disabled="dangXuat" @click="xuatExcel">
        {{ dangXuat ? 'Đang xuất…' : 'Xuất Excel' }}
      </button>
      <!-- Còn đúng một phiếu đã chốt thì dùng nút trên dòng của nó, không cần thao tác cả tháng. -->
      <button
        v-if="canPay && finalizedCount > 1"
        class="btn btn-outline btn-sm"
        @click="payTarget = { row: null }"
      >
        Đã trả cả tháng
      </button>
      <button
        v-if="canReopen && finalizedCount > 1"
        class="btn btn-outline btn-sm"
        @click="openReopen(null)"
      >
        Mở lại cả tháng
      </button>
      <span v-if="info" class="info-text">{{ info }}</span>
    </div>

    <div class="toolbar">
      <input
        v-model="keyword"
        class="search-input"
        type="search"
        placeholder="Tìm theo tên giáo viên…"
      />
      <span class="spacer" />
      <span class="count-info">{{ filteredRows.length }} / {{ rows.length }} giáo viên</span>
    </div>

    <div class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>Giáo viên</th>
            <th class="num">Số tiết</th>
            <th class="num" title="Đi muộn vẫn được trả đủ tiết — số này để kế toán tự quyết khấu trừ">
              Đi muộn
            </th>
            <th class="num">Đơn giá/tiết</th>
            <th class="num">Lương cứng</th>
            <th class="num">Phụ cấp</th>
            <th class="num">Thưởng</th>
            <th class="num">Khấu trừ</th>
            <th class="num">Thực nhận</th>
            <th>Trạng thái</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="11" class="text-center text-muted">Đang tải…</td>
          </tr>
          <tr v-else-if="!rows.length">
            <td colspan="11" class="text-center text-muted">
              Chưa có dữ liệu — bấm “Tính lương từ chấm công”.
            </td>
          </tr>
          <tr v-else-if="!filteredRows.length">
            <td colspan="11" class="text-center text-muted">Không có giáo viên nào khớp từ khóa.</td>
          </tr>
          <tr v-for="r in pagedRows" :key="r.id">
            <td class="font-medium">{{ r.teacherName }}</td>
            <td class="num mono">{{ Math.round(Number(r.taughtHours ?? 0)) }}</td>
            <td class="num mono" :class="{ 'late-warn': r.lateCount > 0 }">
              {{ r.lateCount || '—' }}
            </td>
            <td class="num mono">{{ vnd(r.ratePerHour) }}</td>
            <td class="num mono">{{ vnd(r.baseSalary) }}</td>
            <td class="num mono">{{ vnd(r.allowance) }}</td>
            <td class="num mono">{{ vnd(r.bonus) }}</td>
            <td class="num mono">{{ vnd(r.deduction) }}</td>
            <td class="num mono net">{{ vnd(r.netAmount) }}</td>
            <td>
              <span class="badge" :class="(STATUS[r.status] || {}).cls">{{
                (STATUS[r.status] || {}).label ?? r.status
              }}</span>
            </td>
            <td class="actions">
              <button
                v-if="r.status === 'DRAFT'"
                class="btn btn-sm btn-outline"
                @click="openEdit(r)"
              >
                Sửa
              </button>
              <button
                v-if="r.status === 'DRAFT'"
                class="btn btn-sm btn-primary"
                @click="finalize(r)"
              >
                Chốt
              </button>
              <button
                v-if="canPay && r.status === 'FINALIZED'"
                class="btn btn-sm btn-primary"
                @click="payTarget = { row: r }"
              >
                Đã trả
              </button>
              <button
                v-if="canReopen && r.status === 'FINALIZED'"
                class="btn btn-sm btn-outline"
                @click="openReopen(r)"
              >
                Mở lại
              </button>
              <button class="btn btn-sm btn-outline" @click="openLogs(r)">Lịch sử</button>
            </td>
          </tr>
        </tbody>
        <!-- Tổng của CẢ KỲ, không phải của trang đang xem — đó là con số kế toán cần. -->
        <tfoot v-if="rows.length">
          <tr>
            <td colspan="8" class="text-right font-medium">Tổng thực nhận</td>
            <td class="num mono net">{{ vnd(totalNet) }}</td>
            <td colspan="2"></td>
          </tr>
        </tfoot>
      </table>
    </div>

    <Pagination v-model="page" :total-pages="totalPages" />

    <!-- Modal sửa -->
    <div v-if="editModal.open" class="modal-overlay" @click.self="editModal.open = false">
      <div class="modal-box">
        <h3>Điều chỉnh lương — {{ editModal.row.teacherName }}</h3>
        <p class="small text-muted">
          Số tiết: <strong>{{ Math.round(Number(editModal.row.taughtHours ?? 0)) }} tiết</strong>
        </p>
        <div class="grid2">
          <div class="form-group">
            <label>Đơn giá/tiết (₫)</label>
            <input type="number" v-model.number="editModal.form.ratePerHour" />
          </div>
          <div class="form-group">
            <label>Lương cứng (₫)</label>
            <input type="number" v-model.number="editModal.form.baseSalary" />
          </div>
          <div class="form-group">
            <label>Phụ cấp (₫)</label>
            <input type="number" v-model.number="editModal.form.allowance" />
          </div>
          <div class="form-group">
            <label>Thưởng (₫)</label>
            <input type="number" v-model.number="editModal.form.bonus" />
          </div>
          <div class="form-group">
            <label>Khấu trừ (₫)</label>
            <input type="number" v-model.number="editModal.form.deduction" />
          </div>
        </div>
        <div class="preview">
          Thực nhận (tạm tính): <strong>{{ vnd(previewNet) }}</strong>
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

    <!-- Modal xác nhận chốt khi kỳ còn Vắng rơi vào ngày nghỉ -->
    <div v-if="confirmModal.open" class="modal-overlay" @click.self="confirmModal.open = false">
      <div class="modal-box">
        <h3>Chốt lương khi kỳ còn lỗi ngày nghỉ?</h3>
        <p class="warn-block">
          Kỳ {{ filter.month }}/{{ filter.year }} còn
          <strong>{{ issues?.absenceCount }}</strong> dòng chấm công ghi Vắng cho buổi rơi vào
          ngày nghỉ ({{ issues?.teacherCount }} giáo viên).
          <br /><br />
          Chốt xong sẽ <strong>khóa chấm công của cả kỳ này</strong>. Các dòng Vắng đó nằm lại
          trong hồ sơ chuyên cần của giáo viên, và chỉ sửa được sau khi mở lại bảng lương.
        </p>
        <p class="small text-muted">
          Nếu bạn biết rõ đây là vắng thật (giáo viên có buổi dạy bù hôm đó mà bỏ) thì cứ chốt.
        </p>
        <div class="chip-row">
          <button
            v-for="h in issues?.holidays ?? []"
            :key="h.holidayId"
            class="link-chip"
            @click="goFixHoliday(h.holidayId)"
          >
            Đi sửa: {{ h.name }} ({{ h.absenceCount }}) →
          </button>
        </div>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="confirmModal.open = false">Để tôi xử lý trước</button>
          <button
            class="btn btn-primary"
            :disabled="confirmModal.working"
            @click="doFinalize(confirmModal.row)"
          >
            {{ confirmModal.working ? 'Đang chốt…' : 'Vẫn chốt' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Modal mở lại bảng lương đã chốt -->
    <div v-if="reopenModal.open" class="modal-overlay" @click.self="reopenModal.open = false">
      <div class="modal-box">
        <h3>
          {{
            reopenModal.row
              ? `Mở lại phiếu lương — ${reopenModal.row.teacherName}`
              : `Mở lại cả kỳ ${filter.month}/${filter.year}`
          }}
        </h3>
        <p class="warn-block">
          Phiếu sẽ quay về trạng thái <strong>Nháp</strong>, chấm công của kỳ mở khóa trở lại và
          giáo viên tạm thời KHÔNG xem được phiếu lương (họ sẽ nhận thông báo giải thích).
          <span v-if="!reopenModal.row">
            <br /><br />
            Áp dụng cho <strong>{{ finalizedCount }}</strong> phiếu đang ở trạng thái đã chốt.
          </span>
        </p>
        <div class="form-group">
          <label>Lý do mở lại *</label>
          <input
            v-model="reopenModal.reason"
            placeholder="VD: sửa dòng Vắng ghi nhầm vào ngày lễ 2/9"
          />
          <small class="text-muted">Lưu vĩnh viễn trong nhật ký phiếu lương.</small>
        </div>
        <p v-if="reopenModal.error" class="error-msg">{{ reopenModal.error }}</p>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="reopenModal.open = false">Hủy</button>
          <button
            class="btn btn-primary"
            :disabled="reopenModal.working || !reopenModal.reason.trim()"
            @click="doReopen"
          >
            {{ reopenModal.working ? 'Đang mở…' : 'Mở lại' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Xác nhận đã trả lương — một chiều, không mở lại được nên phải hỏi -->
    <ConfirmDialog
      v-if="payTarget"
      title="Xác nhận đã trả lương?"
      :name="
        payTarget.row
          ? payTarget.row.teacherName
          : `Cả kỳ ${filter.month}/${filter.year} — ${finalizedCount} phiếu`
      "
      :busy="paying"
      confirm-text="Đã trả"
      @confirm="doPay"
      @cancel="payTarget = null"
    >
      Số tiền:
      <strong>{{ vnd(payTarget.row ? payTarget.row.netAmount : finalizedTotal) }}</strong
      >.
      <br /><br />
      Đánh dấu đã trả là <strong>một chiều</strong>: phiếu đã trả KHÔNG mở lại được nữa, vì
      tiền đã ra khỏi quỹ thì sửa số trên hệ thống chỉ làm lệch sổ sách. Chênh lệch (nếu có)
      điều chỉnh vào kỳ lương kế tiếp.
    </ConfirmDialog>

    <!-- Nhật ký phiếu lương: chốt / mở lại / đã trả -->
    <div v-if="logModal.open" class="modal-overlay" @click.self="logModal.open = false">
      <div class="modal-box modal-lg">
        <h3>Nhật ký phiếu lương — {{ logModal.row?.teacherName }}</h3>
        <p v-if="logModal.loading" class="text-muted small">Đang tải…</p>
        <p v-else-if="!logModal.items.length" class="text-muted small">
          Phiếu này chưa từng được chốt hay mở lại.
        </p>
        <table v-else class="table">
          <thead>
            <tr>
              <th>Thời điểm</th>
              <th>Việc</th>
              <th>Trạng thái</th>
              <th class="num">Thực nhận</th>
              <th>Người thực hiện</th>
              <th>Lý do</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="l in logModal.items" :key="l.id">
              <td class="mono small">{{ fmtAt(l.changedAt) }}</td>
              <td class="font-medium">{{ LOG_ACTIONS[l.action] ?? l.action }}</td>
              <td class="small text-muted">{{ l.statusBefore }} → {{ l.statusAfter }}</td>
              <td class="num mono">{{ vnd(l.netAmountAfter ?? l.netAmountBefore) }}</td>
              <td class="small">{{ l.changedByName ?? '—' }}</td>
              <td class="small text-muted">{{ l.reason ?? '—' }}</td>
            </tr>
          </tbody>
        </table>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="logModal.open = false">Đóng</button>
        </div>
      </div>
    </div>

    <!-- Bảng đơn giá tiết dạy -->
    <div v-if="rateModal.open" class="modal-overlay" @click.self="rateModal.open = false">
      <div class="modal-box modal-lg">
        <h3>Đơn giá tiết dạy</h3>
        <p class="text-muted small">
          Đơn giá tra theo <strong>ngày dạy</strong> của từng buổi, không theo hôm nay — nhờ vậy
          tính lại một kỳ cũ vẫn ra đúng số đã trả. Tăng giá thì thêm mức mới, mức cũ tự được
          đóng lại ở ngày liền trước. Giáo viên có đơn giá riêng trong hợp đồng thì hợp đồng
          thắng bảng này.
        </p>

        <p v-if="rateModal.error" class="msg msg--error">{{ rateModal.error }}</p>
        <p v-if="rateModal.loading" class="text-muted small">Đang tải…</p>
        <table v-else class="table">
          <thead>
            <tr>
              <th>Khối</th>
              <th class="num">Đơn giá/tiết</th>
              <th>Áp dụng từ</th>
              <th>Đến</th>
              <th>Ghi chú</th>
              <th v-if="canManageRate" width="70"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rateModal.items" :key="r.id" :class="{ closed: !!r.effectiveTo }">
              <td>Khối {{ r.gradeFrom }}–{{ r.gradeTo }}</td>
              <td class="num mono">{{ vnd(r.amount) }}</td>
              <td class="mono">{{ r.effectiveFrom }}</td>
              <td class="mono">{{ r.effectiveTo ?? 'còn hiệu lực' }}</td>
              <td class="small text-muted">{{ r.note ?? '—' }}</td>
              <td v-if="canManageRate">
                <button
                  v-if="coTheXoaMuc(r)"
                  class="link link--danger"
                  title="Mức này chưa tới ngày áp dụng nên còn xóa được"
                  @click="removeRate(r)"
                >
                  Xóa
                </button>
                <span v-else class="small text-muted" title="Mức đã áp dụng là căn cứ của phiếu lương đã tính">
                  đã áp dụng
                </span>
              </td>
            </tr>
          </tbody>
        </table>

        <template v-if="canManageRate">
          <h4 class="rate-form-title">Thêm mức giá mới</h4>
          <div class="rate-form">
            <label>
              Khối từ
              <input v-model.number="rateForm.gradeFrom" type="number" min="1" max="9" />
            </label>
            <label>
              đến
              <input v-model.number="rateForm.gradeTo" type="number" min="1" max="9" />
            </label>
            <label>
              Đơn giá (₫)
              <input v-model.number="rateForm.amount" type="number" min="1" />
            </label>
            <label>
              Áp dụng từ
              <input v-model="rateForm.effectiveFrom" type="date" />
            </label>
            <label class="rate-note">
              Ghi chú
              <input v-model="rateForm.note" placeholder="VD: tăng giá theo quyết định 12/2026" />
            </label>
            <button class="btn btn-primary btn-sm" :disabled="rateModal.saving" @click="saveRate">
              {{ rateModal.saving ? 'Đang lưu…' : 'Thêm' }}
            </button>
          </div>
        </template>

        <p v-if="rateModal.error" class="error-msg">{{ rateModal.error }}</p>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="rateModal.open = false">Đóng</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Nút chữ trong bảng đơn giá + dòng báo lỗi của hộp thoại. */
.link {
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  color: var(--c-primary);
  font-size: 0.84rem;
}
.link--danger {
  color: var(--c-danger, #ef4444);
}
.msg--error {
  color: var(--c-danger, #ef4444);
  font-size: 0.86rem;
  margin: 8px 0;
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.link-chip {
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  border-radius: 999px;
  padding: 5px 12px;
  font-size: 13px;
  cursor: pointer;
}
.link-chip:hover {
  border-color: var(--c-primary);
  color: var(--c-primary-dark);
}
.warn-block {
  margin: 0;
  padding: 12px 14px;
  border-radius: 10px;
  background: rgba(239, 68, 68, 0.1);
  font-size: 14px;
  line-height: 1.55;
}

.num {
  text-align: right;
}
.text-right {
  text-align: right;
}
.net {
  font-weight: 800;
  color: var(--c-primary-dark);
}
.divider {
  width: 1px;
  height: 22px;
  background: var(--c-border);
  margin: 0 0.3rem;
}
.info-text {
  font-size: 0.82rem;
  color: var(--c-accent);
  margin-left: auto;
}
.preview {
  margin-top: 0.4rem;
  padding: 0.6rem 0.8rem;
  background: rgba(249, 115, 22, 0.09);
  border: 1px solid rgba(249, 115, 22, 0.3);
  border-radius: 8px;
  font-size: 0.9rem;
  color: #9a3412;
}
:root[data-theme='dark'] .preview {
  color: #fdba74;
}
tfoot td {
  padding: 0.7rem 1rem;
  border-top: 2px solid var(--c-border);
}

/* Số buổi đi muộn — chỉ nhấn khi khác 0, để mắt không bị kéo về cả cột dấu gạch. */
.late-warn {
  color: var(--c-danger);
  font-weight: 700;
}

/* ── Modal bảng đơn giá ── */
.closed td {
  opacity: 0.55;
}
.rate-form-title {
  margin: 1.2rem 0 0.6rem;
  font-size: 0.95rem;
  font-weight: 700;
}
.rate-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  align-items: flex-end;
}
.rate-form label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.8rem;
  color: var(--c-text-muted);
}
.rate-form input {
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  font-size: 0.88rem;
  background: var(--c-surface);
  color: var(--c-text);
  width: 130px;
}
.rate-note {
  flex: 1;
}
.rate-note input {
  width: 100%;
}
</style>
