<!-- src/pages/SchoolClassListPage.vue -->
<script setup>
/**
 * Trang "Lớp học" (khu admin): CRUD SchoolClass.
 * Giao diện đồng bộ với Nhóm môn học / Kho bài giảng (filter-bar + table + modal).
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { classApi } from '@/api/classes'

const loading = ref(false)
const items = ref([])
const total = ref(0)
const keyword = ref('')
const filterSchoolId = ref('')
const filterStatus = ref('')
const page = ref(0)
const pageSize = 10
const pageInput = ref('')

const schools = ref([])

const modal = reactive({
  open: false,
  mode: 'create', // 'create' | 'edit'
  id: null,
  form: {
    schoolId: '',
    name: '',
    gradeLevel: '',
    schoolYear: '',
    status: 'ACTIVE',
  },
  errors: {},
  error: '',
  saving: false,
})

const deleteTarget = ref(null)

/* =========================
   Pagination
========================= */

const totalPages = computed(() => Math.ceil(total.value / pageSize) || 1)

const visiblePages = computed(() => {
  const totalP = totalPages.value
  const current = page.value + 1
  let start = Math.max(1, current - 2)
  let end = Math.min(totalP, current + 2)
  if (end - start < 4) {
    if (start === 1) end = Math.min(5, totalP)
    else if (end === totalP) start = Math.max(1, totalP - 4)
  }
  const arr = []
  for (let i = start; i <= end; i++) arr.push(i)
  return arr
})

function goPage(index) {
  if (index < 0 || index >= totalPages.value) return
  page.value = index
  load()
}

function jumpPage() {
  const p = Number(pageInput.value)
  if (isNaN(p) || p < 1 || p > totalPages.value) return
  goPage(p - 1)
}

/* ── Load ── */
async function load() {
  loading.value = true
  try {
    const res = await classApi.list({
      keyword: keyword.value || undefined,
      schoolId: filterSchoolId.value || undefined,
      status: filterStatus.value || undefined,
      page: page.value,
      size: pageSize,
    })
    items.value = res.data.content
    total.value = res.data.totalElements
  } catch {
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadSchools() {
  try {
    const { data } = await classApi.schoolOptions()
    schools.value = data
  } catch {
    schools.value = []
  }
}

onMounted(() => {
  load()
  loadSchools()
})

function onSearch() {
  page.value = 0
  load()
}

function clearSearch() {
  keyword.value = ''
  filterSchoolId.value = ''
  filterStatus.value = ''
  page.value = 0
  load()
}

/* ── Validate (mirror SchoolClassRequest) ── */
const YEAR_RE = /^\d{4}-\d{4}$/
function validateForm(form) {
  const errors = {}
  if (!form.schoolId) errors.schoolId = 'Vui lòng chọn trường'
  const name = (form.name || '').trim()
  if (!name) errors.name = 'Tên lớp không được để trống'
  else if (name.length > 100) errors.name = 'Tên lớp tối đa 100 ký tự'
  if (form.gradeLevel && form.gradeLevel.length > 50) errors.gradeLevel = 'Khối tối đa 50 ký tự'
  const year = (form.schoolYear || '').trim()
  if (!year) errors.schoolYear = 'Năm học không được để trống'
  else if (!YEAR_RE.test(year)) errors.schoolYear = 'Năm học dạng YYYY-YYYY (vd: 2025-2026)'
  return errors
}

function openCreate() {
  Object.assign(modal, {
    open: true,
    mode: 'create',
    id: null,
    form: {
      schoolId: filterSchoolId.value || '',
      name: '',
      gradeLevel: '',
      schoolYear: defaultSchoolYear(),
      status: 'ACTIVE',
    },
    errors: {},
    error: '',
    saving: false,
  })
}

function openEdit(item) {
  Object.assign(modal, {
    open: true,
    mode: 'edit',
    id: item.id,
    form: {
      schoolId: item.schoolId,
      name: item.name,
      gradeLevel: item.gradeLevel ?? '',
      schoolYear: item.schoolYear,
      status: item.status,
    },
    errors: {},
    error: '',
    saving: false,
  })
}

function defaultSchoolYear() {
  const y = new Date().getFullYear()
  // Năm học VN thường bắt đầu tháng 9
  const start = new Date().getMonth() >= 8 ? y : y - 1
  return `${start}-${start + 1}`
}

function clearFieldError(field) {
  if (modal.errors[field]) delete modal.errors[field]
}

async function saveModal() {
  modal.errors = validateForm(modal.form)
  if (Object.keys(modal.errors).length) return

  modal.saving = true
  modal.error = ''
  const body = {
    schoolId: Number(modal.form.schoolId),
    name: modal.form.name.trim(),
    gradeLevel: modal.form.gradeLevel?.trim() || null,
    schoolYear: modal.form.schoolYear.trim(),
    status: modal.form.status,
  }
  try {
    if (modal.mode === 'create') {
      await classApi.create(body)
    } else {
      await classApi.update(modal.id, body)
    }
    modal.open = false
    load()
  } catch (e) {
    modal.error = e.response?.data?.message ?? 'Lỗi không xác định'
  } finally {
    modal.saving = false
  }
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await classApi.remove(deleteTarget.value.id)
    deleteTarget.value = null
    load()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Xóa thất bại')
    deleteTarget.value = null
  }
}

function statusLabel(s) {
  return s === 'ACTIVE' ? 'Hoạt động' : 'Ngừng'
}
</script>

<template>
  <div class="page">
    <!-- ================= HEADER ================= -->
    <div class="page__head">
      <div>
        <h1 class="page__title">Lớp học</h1>
        <p class="page__sub">Quản lý lớp học theo trường khách hàng (tên, khối, năm học, trạng thái)</p>
      </div>
      <button class="btn" @click="openCreate">+ Thêm lớp học</button>
    </div>

    <!-- ================= FILTER ================= -->
    <div class="filter-bar">
      <label class="field field--wide">
        <span>Tìm kiếm</span>
        <input
          v-model="keyword"
          placeholder="Tên lớp, khối, năm học..."
          @keyup.enter="onSearch"
        />
      </label>

      <label class="field">
        <span>Trường</span>
        <select v-model="filterSchoolId">
          <option value="">Tất cả</option>
          <option v-for="s in schools" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
      </label>

      <label class="field">
        <span>Trạng thái</span>
        <select v-model="filterStatus">
          <option value="">Tất cả</option>
          <option value="ACTIVE">Hoạt động</option>
          <option value="INACTIVE">Ngừng</option>
        </select>
      </label>

      <div class="filter-actions">
        <button class="btn" @click="onSearch">Lọc</button>
        <button class="btn btn--ghost" @click="clearSearch">Xóa lọc</button>
      </div>
    </div>

    <p v-if="!loading" class="total">
      Tổng cộng
      <strong>{{ total }}</strong>
      lớp học
    </p>

    <!-- ================= TABLE ================= -->
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Tên lớp</th>
            <th>Trường</th>
            <th>Khối</th>
            <th>Năm học</th>
            <th>Trạng thái</th>
            <th width="120">Thao tác</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="6" class="empty">Đang tải...</td>
          </tr>
          <tr v-else-if="items.length === 0">
            <td colspan="6" class="empty">Không có dữ liệu</td>
          </tr>
          <tr v-for="item in items" :key="item.id">
            <td class="col-title">
              <div class="title-text">{{ item.name }}</div>
            </td>
            <td>{{ item.schoolName || '—' }}</td>
            <td>{{ item.gradeLevel || '—' }}</td>
            <td>
              <code class="code-text">{{ item.schoolYear }}</code>
            </td>
            <td>
              <span
                class="badge"
                :class="item.status === 'ACTIVE' ? 'badge--pub' : 'badge--draft'"
              >
                {{ statusLabel(item.status) }}
              </span>
            </td>
            <td class="col-actions">
              <button class="act-btn" title="Sửa" @click="openEdit(item)">Sửa</button>
              <button class="act-btn act-btn--del" title="Xóa" @click="deleteTarget = item">
                Xóa
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- ================= PAGINATION ================= -->
    <div v-if="total > pageSize" class="pagination">
      <button class="pg-btn" :disabled="page === 0" @click="goPage(0)">«</button>
      <button class="pg-btn" :disabled="page === 0" @click="goPage(page - 1)">‹</button>
      <button
        v-for="p in visiblePages"
        :key="p"
        class="pg-btn"
        :class="{ 'pg-btn--active': page === p - 1 }"
        @click="goPage(p - 1)"
      >
        {{ p }}
      </button>
      <button class="pg-btn" :disabled="page >= totalPages - 1" @click="goPage(page + 1)">›</button>
      <button
        class="pg-btn"
        :disabled="page >= totalPages - 1"
        @click="goPage(totalPages - 1)"
      >
        »
      </button>
      <input
        v-model="pageInput"
        class="page-input"
        type="number"
        min="1"
        :max="totalPages"
        placeholder="Trang"
      />
      <button class="pg-btn" @click="jumpPage">Đi</button>
    </div>

    <!-- ================= MODAL: tạo/sửa ================= -->
    <div v-if="modal.open" class="overlay" @click.self="modal.open = false">
      <div class="modal">
        <h3>{{ modal.mode === 'create' ? 'Thêm lớp học' : 'Sửa lớp học' }}</h3>

        <div class="form-group">
          <label>Trường *</label>
          <select
            v-model="modal.form.schoolId"
            :class="{ 'input-error': modal.errors.schoolId }"
            @change="clearFieldError('schoolId')"
          >
            <option value="">-- Chọn trường --</option>
            <option v-for="s in schools" :key="s.id" :value="s.id">{{ s.name }}</option>
          </select>
          <small v-if="modal.errors.schoolId" class="field-error">{{ modal.errors.schoolId }}</small>
        </div>

        <div class="form-group">
          <label>Tên lớp *</label>
          <input
            v-model="modal.form.name"
            placeholder="VD: 10A1"
            :class="{ 'input-error': modal.errors.name }"
            @input="clearFieldError('name')"
          />
          <small v-if="modal.errors.name" class="field-error">{{ modal.errors.name }}</small>
        </div>

        <div class="form-group">
          <label>Khối</label>
          <input
            v-model="modal.form.gradeLevel"
            placeholder="VD: Khối 10"
            :class="{ 'input-error': modal.errors.gradeLevel }"
            @input="clearFieldError('gradeLevel')"
          />
          <small v-if="modal.errors.gradeLevel" class="field-error">{{
            modal.errors.gradeLevel
          }}</small>
        </div>

        <div class="form-group">
          <label>Năm học *</label>
          <input
            v-model="modal.form.schoolYear"
            placeholder="VD: 2025-2026"
            :class="{ 'input-error': modal.errors.schoolYear }"
            @input="clearFieldError('schoolYear')"
          />
          <small v-if="modal.errors.schoolYear" class="field-error">{{
            modal.errors.schoolYear
          }}</small>
          <small v-else>Định dạng YYYY-YYYY (vd: 2025-2026)</small>
        </div>

        <div class="form-group">
          <label>Trạng thái</label>
          <select v-model="modal.form.status">
            <option value="ACTIVE">Hoạt động</option>
            <option value="INACTIVE">Ngừng</option>
          </select>
        </div>

        <p v-if="modal.error" class="msg msg--error">{{ modal.error }}</p>

        <div class="modal__actions">
          <button class="btn btn--ghost" @click="modal.open = false">Hủy</button>
          <button class="btn" :disabled="modal.saving" @click="saveModal">
            {{ modal.saving ? 'Đang lưu...' : 'Lưu' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ================= MODAL: xác nhận xóa ================= -->
    <div v-if="deleteTarget" class="overlay" @click.self="deleteTarget = null">
      <div class="modal">
        <h3>Xác nhận xóa</h3>
        <p>
          Bạn có chắc muốn xóa lớp
          <strong>{{ deleteTarget.name }}</strong>
          ({{ deleteTarget.schoolYear }})?
        </p>
        <div class="modal__actions">
          <button class="btn btn--ghost" @click="deleteTarget = null">Hủy</button>
          <button class="btn btn--danger" @click="confirmDelete">Xóa</button>
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

.page__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 22px;
}

.page__title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  color: var(--c-text);
}

.page__sub {
  margin-top: 6px;
  color: var(--c-text-muted);
  font-size: 14px;
}

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
  border-color: #f97316;
}

.filter-actions {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.btn {
  border: none;
  cursor: pointer;
  border-radius: 8px;
  padding: 10px 18px;
  font-weight: 600;
  background: #f97316;
  color: white;
  transition: 0.2s;
}

.btn:hover {
  transform: translateY(-1px);
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.btn--ghost {
  background: var(--c-surface-2);
  color: var(--c-text);
}

.btn--danger {
  background: #ef4444;
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
}

td {
  padding: 14px;
  border-top: 1px solid var(--c-border);
  vertical-align: middle;
}

tbody tr:hover {
  background: var(--c-surface-2);
}

.empty {
  text-align: center;
  color: var(--c-text-muted);
  padding: 35px;
}

.title-text {
  font-weight: 600;
}

.code-text {
  background: var(--c-surface-2);
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-family: monospace;
}

.badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.badge--draft {
  background: var(--c-surface-2);
  color: var(--c-text);
}

.badge--pub {
  background: rgba(34, 197, 94, 0.15);
  color: #15803d;
}

:root[data-theme='dark'] .badge--pub {
  color: #4ade80;
}

.col-actions {
  white-space: nowrap;
}

.act-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  margin-right: 4px;
  transition: 0.2s;
  color: var(--c-text);
}

.act-btn:hover {
  background: var(--c-surface-2);
}

.act-btn--del:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.12);
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 24px;
  flex-wrap: wrap;
}

.pg-btn {
  min-width: 38px;
  height: 38px;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  background: var(--c-surface);
  cursor: pointer;
  transition: 0.2s;
  color: var(--c-text);
}

.pg-btn:hover:not(:disabled) {
  background: #f97316;
  color: white;
}

.pg-btn--active {
  background: #f97316;
  color: white;
}

.pg-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.page-input {
  width: 70px;
  height: 38px;
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  text-align: center;
  background: var(--c-surface);
  color: var(--c-text);
}

.overlay {
  position: fixed;
  inset: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  background: rgba(0, 0, 0, 0.45);
  z-index: 999;
  padding: 20px;
}

.modal {
  width: 440px;
  max-width: 100%;
  max-height: 90vh;
  overflow-y: auto;
  background: var(--c-surface);
  border-radius: 16px;
  padding: 28px;
}

.modal h3 {
  margin-top: 0;
  color: var(--c-text);
}

.modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
}

.form-group {
  margin-bottom: 14px;
}

.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
  color: var(--c-text);
}

.form-group input,
.form-group select {
  width: 100%;
  height: 40px;
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  padding: 0 12px;
  font-size: 14px;
  box-sizing: border-box;
  font-family: inherit;
  background: var(--c-surface);
  color: var(--c-text);
}

.form-group input:focus,
.form-group select:focus {
  outline: none;
  border-color: #f97316;
}

.form-group small {
  display: block;
  margin-top: 4px;
  color: var(--c-text-muted);
  font-size: 12px;
}

.input-error {
  border-color: #dc2626 !important;
}

.field-error {
  color: #dc2626 !important;
}

.msg {
  padding: 12px;
  margin-top: 10px;
  margin-bottom: 0;
  border-radius: 8px;
}

.msg--error {
  background: rgba(239, 68, 68, 0.1);
  color: #b91c1c;
}

:root[data-theme='dark'] .msg--error {
  color: #f87171;
}

.total {
  margin-bottom: 14px;
  color: var(--c-text-muted);
}

@media (max-width: 900px) {
  .page__head {
    flex-direction: column;
    align-items: flex-start;
    gap: 15px;
  }

  .filter-bar {
    flex-direction: column;
  }

  .field {
    width: 100%;
  }

  .filter-actions {
    width: 100%;
  }

  .btn {
    width: 100%;
  }
}
</style>
