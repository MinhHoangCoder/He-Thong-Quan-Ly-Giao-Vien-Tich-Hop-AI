<script setup>
/**
 * Trang Bảng lương: tính lương theo kỳ (tháng/năm) = GIỜ DẠY × ĐƠN GIÁ + phụ cấp
 * + thưởng − khấu trừ. "Tính lương" tổng hợp giờ dạy từ Chấm công; kế toán có thể
 * chỉnh từng dòng rồi chốt (FINALIZED).
 *
 * CHỐT LƯƠNG LÀ HÀNH ĐỘNG MỘT CHIỀU — hai thứ trang này thêm vào vì lẽ đó:
 * 1. Cảnh báo ngày nghỉ. Chốt xong là chấm công của kỳ bị khóa. Nếu kỳ còn dòng VẮNG mà hệ
 *    thống ghi nhầm cho buổi rơi vào ngày lễ (buổi sinh trước khi khai kỳ nghỉ — Flyway V29),
 *    chốt chính là khóa luôn lỗi vào trong. Banner báo trước, và hỏi lại lần hai lúc bấm.
 * 2. Nút "Mở lại". Trước V32 khóa đó là vĩnh viễn: một lỗi hoàn toàn nhìn thấy trở thành
 *    không thể sửa. Nay mở lại được, nhưng phải có quyền PAYROLL_REOPEN, phải nêu lý do, và
 *    mọi lần mở đều nằm lại trong nhật ký phiếu lương.
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { payrollApi } from '@/api/payroll'
import { useAuthStore } from '@/stores/auth'
import Pagination from '@/components/ui/Pagination.vue'

const router = useRouter()
const auth = useAuthStore()

/** Nút mở lại chỉ hiện với người thật sự bấm được — ADMIN đi tắt như mọi quyền khác. */
const canReopen = computed(
  () => auth.roles.includes('ADMIN') || (auth.user?.perms ?? []).includes('PAYROLL_REOPEN'),
)

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

/* ── Phân trang phía client ── */
const PAGE_SIZE = 10
const page = ref(0)
const totalPages = computed(() => Math.ceil(rows.value.length / PAGE_SIZE))
const pagedRows = computed(() => {
  const start = page.value * PAGE_SIZE
  return rows.value.slice(start, start + PAGE_SIZE)
})

const editModal = reactive({ open: false, saving: false, error: '', row: null, form: {} })

const vnd = (n) =>
  n == null ? '—' : new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(n) + ' ₫'

/**
 * Dòng Vắng rơi vào ngày nghỉ trong kỳ đang xem — nguồn của cả banner lẫn hộp xác nhận.
 * Một nguồn duy nhất để hai chỗ không bao giờ nói hai con số khác nhau.
 */
const issues = ref(null)
const hasIssues = computed(() => (issues.value?.absenceCount ?? 0) > 0)

async function loadIssues() {
  try {
    const { data } = await payrollApi.holidayIssues(filter.year, filter.month)
    issues.value = data
  } catch {
    issues.value = null // cảnh báo hỏng thì im lặng, không chặn việc chính
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
    load()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Chốt lương thất bại')
  } finally {
    confirmModal.working = false
  }
}

/** Từ cảnh báo đi thẳng sang kỳ nghỉ gây lỗi — chỉ ra vấn đề mà không chỉ đường sửa là vô ích. */
function goFixHoliday(holidayId) {
  confirmModal.open = false
  router.push({ path: '/admin/holidays', query: { focus: holidayId } })
}

/* ──────────── Mở lại kỳ lương đã chốt (V32) ──────────── */

const reopenModal = reactive({ open: false, row: null, reason: '', working: false, error: '' })

const finalizedCount = computed(() => rows.value.filter((r) => r.status === 'FINALIZED').length)

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
      <button
        v-if="canReopen && finalizedCount > 0"
        class="btn btn-outline btn-sm"
        @click="openReopen(null)"
      >
        Mở lại cả tháng ({{ finalizedCount }})
      </button>
      <span v-if="info" class="info-text">{{ info }}</span>
    </div>

    <!-- Cảnh báo NGÀY NGHỈ: hiện ngay khi chọn tháng, trước cả khi người dùng định chốt. -->
    <div v-if="hasIssues" class="alert-holiday">
      <div class="alert-holiday__body">
        <strong
          >Kỳ này còn {{ issues.absenceCount }} dòng chấm công Vắng rơi vào ngày nghỉ</strong
        >
        ({{ issues.teacherCount }} giáo viên).
        Đó là buổi dạy sinh ra trước khi kỳ nghỉ được khai báo — hôm đó trường đóng cửa nhưng
        hệ thống vẫn ghi giáo viên vắng mặt.
        <br />
        <span class="alert-holiday__warn">
          Chốt lương sẽ KHÓA chấm công của kỳ này. Xử lý trước khi chốt.
        </span>
        <div class="alert-holiday__links">
          <button
            v-for="h in issues.holidays"
            :key="h.holidayId"
            class="link-chip"
            @click="goFixHoliday(h.holidayId)"
          >
            {{ h.name }} · {{ h.absenceCount }} dòng →
          </button>
        </div>
      </div>
    </div>

    <div class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>Giáo viên</th>
            <th class="num">Số tiết</th>
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
            <td colspan="10" class="text-center text-muted">Đang tải…</td>
          </tr>
          <tr v-else-if="!rows.length">
            <td colspan="10" class="text-center text-muted">
              Chưa có dữ liệu — bấm “Tính lương từ chấm công”.
            </td>
          </tr>
          <tr v-for="r in pagedRows" :key="r.id">
            <td class="font-medium">{{ r.teacherName }}</td>
            <td class="num mono">{{ Math.round(Number(r.taughtHours ?? 0)) }}</td>
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
                v-if="canReopen && r.status === 'FINALIZED'"
                class="btn btn-sm btn-outline"
                @click="openReopen(r)"
              >
                Mở lại
              </button>
            </td>
          </tr>
        </tbody>
        <tfoot v-if="rows.length">
          <tr>
            <td colspan="7" class="text-right font-medium">Tổng thực nhận</td>
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
  </div>
</template>

<style scoped>
/* ===== Cảnh báo ngày nghỉ ===== */
.alert-holiday {
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  margin-bottom: 14px;
  border-radius: 12px;
  border-left: 4px solid #f59e0b;
  background: rgba(245, 158, 11, 0.12);
  font-size: 14px;
  line-height: 1.55;
}
.alert-holiday__warn {
  font-weight: 700;
}
.alert-holiday__links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
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
</style>
