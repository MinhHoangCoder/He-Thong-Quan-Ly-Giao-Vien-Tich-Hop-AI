<script setup>
/**
 * Trang Bảng lương: tính lương theo kỳ (tháng/năm) = GIỜ DẠY × ĐƠN GIÁ + phụ cấp
 * + thưởng − khấu trừ. "Tính lương" tổng hợp giờ dạy từ Chấm công; kế toán có thể
 * chỉnh từng dòng rồi chốt (FINALIZED).
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { payrollApi } from '@/api/payroll'
import Pagination from '@/components/ui/Pagination.vue'

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

async function finalize(r) {
  try {
    await payrollApi.finalize(r.id)
    load()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Chốt lương thất bại')
  }
}

const totalNet = computed(() =>
  rows.value.reduce((s, r) => s + Number(r.netAmount || 0), 0),
)
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
      <span v-if="info" class="info-text">{{ info }}</span>
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
  </div>
</template>

<style scoped>
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
