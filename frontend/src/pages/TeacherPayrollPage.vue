<script setup>
/**
 * Trang "Phiếu lương" của GIÁO VIÊN (tự phục vụ, READ-ONLY).
 * Chỉ xem phiếu lương của CHÍNH mình, và CHỈ phiếu đã chốt/đã trả (backend lọc cứng).
 * Danh sách theo năm → bấm 1 tháng mở phiếu chi tiết + In/Xuất PDF. Không sửa, không tính lương.
 */
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { payrollApi } from '@/api/payroll'
import SvgIcon from '@/components/ui/SvgIcon.vue'

const now = new Date()
const filter = reactive({ year: now.getFullYear(), month: '' })
const years = Array.from({ length: 6 }, (_, i) => now.getFullYear() - i)
const months = Array.from({ length: 12 }, (_, i) => i + 1)

const STATUS = {
  DRAFT: { label: 'Nháp', cls: 'badge-gray' },
  FINALIZED: { label: 'Đã chốt', cls: 'badge-green' },
  PAID: { label: 'Đã trả', cls: 'badge-amber' },
}
const statusMeta = (code) => STATUS[code] ?? { label: code, cls: 'badge-gray' }

const rows = ref([])
const loading = ref(false)
const info = ref('')

const vnd = (n) =>
  n == null ? '—' : new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(n) + ' ₫'
const periodLabel = (r) => `Tháng ${r.periodMonth}/${r.periodYear}`

async function load() {
  loading.value = true
  info.value = ''
  try {
    const { data } = await payrollApi.mine({
      year: filter.year,
      month: filter.month || undefined,
    })
    rows.value = data
  } catch (e) {
    rows.value = []
    info.value = e.response?.data?.message ?? 'Không tải được phiếu lương'
  } finally {
    loading.value = false
  }
}

onMounted(load)

/* ── Phiếu chi tiết ── */
const detail = reactive({ open: false, row: null })
function openDetail(r) {
  detail.row = r
  detail.open = true
}
function closeDetail() {
  detail.open = false
  detail.row = null
}

/** Thành tiền tiết dạy = số tiết × đơn giá (minh bạch trong phiếu; netAmount do DB tính). */
const teachingPay = computed(() => {
  const r = detail.row
  if (!r) return 0
  return Number(r.taughtHours ?? 0) * Number(r.ratePerHour ?? 0)
})

/* ── In / Xuất PDF: gắn cờ body để CSS @media print chỉ chừa phần phiếu ── */
function printPayslip() {
  document.body.classList.add('tpay-printing')
  const cleanup = () => document.body.classList.remove('tpay-printing')
  window.addEventListener('afterprint', cleanup, { once: true })
  window.print()
  setTimeout(cleanup, 1500)
}

onBeforeUnmount(() => document.body.classList.remove('tpay-printing'))
</script>

<template>
  <div class="page tpay">
    <div class="page-head tpay__noprint">
      <div>
        <h2 class="title">Phiếu lương</h2>
      </div>
    </div>

    <!-- Bộ lọc -->
    <div class="toolbar tpay__noprint">
      <label>Năm</label>
      <select v-model.number="filter.year">
        <option v-for="y in years" :key="y" :value="y">{{ y }}</option>
      </select>
      <label>Tháng</label>
      <select v-model="filter.month">
        <option value="">Tất cả</option>
        <option v-for="m in months" :key="m" :value="m">Tháng {{ m }}</option>
      </select>
      <button class="btn btn-outline btn-sm" @click="load">Xem</button>
      <span v-if="info" class="info-text">{{ info }}</span>
    </div>

    <!-- Danh sách phiếu theo năm -->
    <div class="table-wrap tpay__noprint">
      <table class="table">
        <thead>
          <tr>
            <th>Kỳ</th>
            <th class="num">Số tiết</th>
            <th class="num">Thực nhận</th>
            <th>Trạng thái</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="5" class="text-center text-muted">Đang tải…</td>
          </tr>
          <tr v-else-if="!rows.length">
            <td colspan="5" class="text-center text-muted">
              Chưa có phiếu lương đã chốt cho kỳ này.
            </td>
          </tr>
          <tr v-for="r in rows" :key="r.id" class="rowlink" @click="openDetail(r)">
            <td class="font-medium">{{ periodLabel(r) }}</td>
            <td class="num mono">{{ Math.round(Number(r.taughtHours ?? 0)) }}</td>
            <td class="num mono net">{{ vnd(r.netAmount) }}</td>
            <td>
              <span class="badge" :class="statusMeta(r.status).cls">{{
                statusMeta(r.status).label
              }}</span>
            </td>
            <td class="actions">
              <button class="btn btn-sm btn-outline" @click.stop="openDetail(r)">Xem phiếu</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Modal phiếu chi tiết (nội dung này in ra PDF) -->
    <div v-if="detail.open" class="modal-overlay" @click.self="closeDetail">
      <div class="modal-box payslip">
        <div class="payslip__head">
          <div>
            <h3 class="payslip__title">PHIẾU LƯƠNG</h3>
            <p class="payslip__period">{{ periodLabel(detail.row) }}</p>
          </div>
          <span class="badge" :class="statusMeta(detail.row.status).cls">{{
            statusMeta(detail.row.status).label
          }}</span>
        </div>

        <div class="payslip__meta">
          <span class="k">Giáo viên</span>
          <span class="v">{{ detail.row.teacherName }}</span>
        </div>

        <table class="payslip__table">
          <tbody>
            <tr>
              <td>Lương cứng</td>
              <td class="num mono">{{ vnd(detail.row.baseSalary) }}</td>
            </tr>
            <tr>
              <td>
                Tiết dạy
                <small class="muted"
                  >({{ Math.round(Number(detail.row.taughtHours ?? 0)) }} tiết ×
                  {{ vnd(detail.row.ratePerHour) }})</small
                >
              </td>
              <td class="num mono">{{ vnd(teachingPay) }}</td>
            </tr>
            <tr>
              <td>Phụ cấp</td>
              <td class="num mono">{{ vnd(detail.row.allowance) }}</td>
            </tr>
            <tr>
              <td>Thưởng</td>
              <td class="num mono">{{ vnd(detail.row.bonus) }}</td>
            </tr>
            <tr>
              <td>Khấu trừ</td>
              <td class="num mono">− {{ vnd(detail.row.deduction) }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr class="net-row">
              <td>THỰC NHẬN</td>
              <td class="num mono net">{{ vnd(detail.row.netAmount) }}</td>
            </tr>
          </tfoot>
        </table>

        <div class="modal-actions tpay__noprint">
          <button class="btn btn-outline" @click="closeDetail">Đóng</button>
          <button class="btn btn-primary btn-print" @click="printPayslip">
            <SvgIcon name="payroll" :size="16" /> In / Xuất PDF
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.num {
  text-align: right;
}
.net {
  font-weight: 800;
  color: var(--c-primary-dark);
}
.info-text {
  font-size: 0.82rem;
  color: var(--c-accent);
  margin-left: auto;
}
.rowlink {
  cursor: pointer;
}
.rowlink:hover {
  background: var(--c-surface-2, rgba(0, 0, 0, 0.03));
}

/* ── Phiếu chi tiết ── */
.payslip {
  max-width: 460px;
}
.payslip__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding-bottom: 0.8rem;
  border-bottom: 2px solid var(--c-border);
}
.payslip__title {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 800;
  letter-spacing: 0.03em;
}
.payslip__period {
  margin: 0.15rem 0 0;
  color: var(--c-text-muted);
  font-size: 0.9rem;
}
.payslip__meta {
  display: flex;
  justify-content: space-between;
  padding: 0.8rem 0;
  border-bottom: 1px dashed var(--c-border);
}
.payslip__meta .k {
  color: var(--c-text-muted);
}
.payslip__meta .v {
  font-weight: 600;
}
.payslip__table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 0.4rem;
}
.payslip__table td {
  padding: 0.55rem 0;
  border-bottom: 1px solid var(--c-border);
}
.payslip__table .muted {
  color: var(--c-text-muted);
  font-weight: 400;
}
.payslip__table tfoot .net-row td {
  padding-top: 0.7rem;
  border-top: 2px solid var(--c-border);
  border-bottom: none;
  font-weight: 800;
  font-size: 1.05rem;
}
.btn-print {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}
</style>

<!-- CSS in ấn KHÔNG scoped: cần với tới khung app (sidebar/topbar) nằm ngoài component.
     Chỉ áp khi body có cờ .tpay-printing (bật lúc bấm In) → không ảnh hưởng trang khác. -->
<style>
@media print {
  body.tpay-printing .sidebar,
  body.tpay-printing .topbar,
  body.tpay-printing .backdrop,
  body.tpay-printing .tpay__noprint {
    display: none !important;
  }
  body.tpay-printing .content {
    padding: 0 !important;
  }
  /* Modal → in như một trang giấy thường (bỏ nền phủ, canh trên). */
  body.tpay-printing .modal-overlay {
    position: static !important;
    display: block !important;
    background: none !important;
    padding: 0 !important;
  }
  body.tpay-printing .modal-box {
    box-shadow: none !important;
    border: none !important;
    max-width: 100% !important;
    width: 100% !important;
  }
}
</style>
