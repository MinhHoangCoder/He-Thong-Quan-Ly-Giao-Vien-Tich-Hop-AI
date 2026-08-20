<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { schoolApi } from '@/api/schools'
import { branchApi } from '@/api/branches'
import DateField from '@/components/ui/DateField.vue'
import Pagination from '@/components/ui/Pagination.vue'

/* ── State: danh sách trường ── */
const loading = ref(false)
const items = ref([])
const total = ref(0)
const keyword = ref('')
const branchFilter = ref('')
const statusFilter = ref('')
const page = ref(0)
const pageSize = 5

/** Danh sách chi nhánh — dùng cho dropdown lọc + form thêm/sửa. */
const branches = ref([])

const emptyForm = () => ({
  branchId: '',
  name: '',
  address: '',
  phone: '',
  email: '',
  contactPerson: '',
  contractStartDate: '',
  contractEndDate: '',
  status: 'ACTIVE',
})

const modal = reactive({
  open: false,
  mode: 'create', // 'create' | 'edit'
  id: null,
  form: emptyForm(),
  errors: {},
  error: '',
  saving: false,
})

const deleteTarget = ref(null)

//  Pagination — dùng component dùng chung Pagination

const totalPages = computed(() => Math.ceil(total.value / pageSize))

function goPage(index) {
  if (index < 0 || index >= totalPages.value) return
  page.value = index
  load()
}

/* ── Load danh sách trường ── */
async function load() {
  loading.value = true
  try {
    const res = await schoolApi.list({
      keyword: keyword.value || undefined,
      branchId: branchFilter.value || undefined,
      status: statusFilter.value || undefined,
      page: page.value,
      size: pageSize,
    })
    items.value = res.data.content
    total.value = res.data.totalElements
  } catch {
    // handle error
  } finally {
    loading.value = false
  }
}

async function loadBranches() {
  try {
    const { data } = await branchApi.list()
    branches.value = data
  } catch {
    // dropdown lỗi không block trang
  }
}

onMounted(() => {
  load()
  loadBranches()
})

function onSearch() {
  page.value = 0
  load()
}

// Lọc theo chi nhánh/trạng thái -> áp dụng ngay khi đổi lựa chọn, không cần bấm nút.
watch([branchFilter, statusFilter], onSearch)

// Ô tìm kiếm -> debounce 350ms để không gọi API dồn dập theo từng ký tự gõ.
let searchDebounce = null
watch(keyword, () => {
  clearTimeout(searchDebounce)
  searchDebounce = setTimeout(onSearch, 350)
})
// Bấm Enter -> tìm ngay, không cần chờ debounce.
function searchNow() {
  clearTimeout(searchDebounce)
  onSearch()
}

function clearSearch() {
  clearTimeout(searchDebounce) // hủy debounce đang chờ (nếu có) để khỏi gọi API lần nữa sau đó
  keyword.value = ''
  branchFilter.value = ''
  statusFilter.value = ''
  page.value = 0
  load()
}

function branchName(branchId) {
  return branches.value.find((b) => b.id === branchId)?.name ?? '-'
}

/* ── Validate (mirror SchoolRequest phía backend) ── */
const PHONE_RE = /^\d{10}$/ // chỉ số, không dấu +, không khoảng trắng
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const NAME_RE = /^[A-Za-zÀ-ỹĐđ]+(\s[A-Za-zÀ-ỹĐđ]+)*$/ // chỉ chữ cái + 1 khoảng trắng giữa các từ, không số/ký tự đặc biệt, không thừa dấu cách

function validateForm(form) {
  const errors = {}
  const name = form.name.trim()
  const address = form.address.trim()
  const phone = form.phone.trim()
  const contactPerson = form.contactPerson.trim()

  if (!form.branchId) errors.branchId = 'Vui lòng chọn chi nhánh phụ trách'

  if (!name) errors.name = 'Tên trường không được để trống'
  else if (name.length > 200) errors.name = 'Tên tối đa 200 ký tự'
  else if (!NAME_RE.test(name))
    errors.name =
      'Tên trường chỉ được chứa chữ cái và khoảng trắng, không chứa số hoặc ký tự đặc biệt'

  if (!address) errors.address = 'Địa chỉ không được để trống'
  else if (address.length > 255) errors.address = 'Địa chỉ tối đa 255 ký tự'

  if (!phone) errors.phone = 'Số điện thoại không được để trống'
  else if (!PHONE_RE.test(phone)) errors.phone = 'Số điện thoại chỉ được nhập số (10 chữ số)'

  if (!form.email) errors.email = 'Email không được để trống'
  else if (!EMAIL_RE.test(form.email)) errors.email = 'Email không hợp lệ'

  if (!contactPerson) errors.contactPerson = 'Người liên hệ không được để trống'
  else if (contactPerson.length > 150) errors.contactPerson = 'Tên người liên hệ tối đa 150 ký tự'
  else if (!NAME_RE.test(contactPerson))
    errors.contactPerson =
      'Người liên hệ chỉ được chứa chữ cái và khoảng trắng, không chứa số hoặc ký tự đặc biệt'

  if (!form.contractStartDate)
    errors.contractStartDate = 'Ngày bắt đầu hợp đồng không được để trống'

  if (!form.contractEndDate) errors.contractEndDate = 'Ngày hết hạn hợp đồng không được để trống'
  else if (form.contractStartDate && form.contractEndDate < form.contractStartDate) {
    errors.contractEndDate = 'Ngày kết thúc phải sau ngày bắt đầu'
  }

  return errors
}

/** Chặn nhập ký tự không phải số ở ô SĐT ngay khi gõ. */
function onPhoneInput(e) {
  modal.form.phone = e.target.value.replace(/\D/g, '')
  clearFieldError('phone')
}

/* ── Modal tạo/sửa ── */
function openCreate() {
  Object.assign(modal, {
    open: true,
    mode: 'create',
    id: null,
    form: emptyForm(),
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
      branchId: item.branchId,
      name: item.name,
      address: item.address ?? '',
      phone: item.phone ?? '',
      email: item.email ?? '',
      contactPerson: item.contactPerson ?? '',
      contractStartDate: item.contractStartDate ?? '',
      contractEndDate: item.contractEndDate ?? '',
      status: item.status,
    },
    errors: {},
    error: '',
    saving: false,
  })
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
    ...modal.form,
    branchId: Number(modal.form.branchId),
    name: modal.form.name.trim(),
    address: modal.form.address.trim() || null,
    phone: modal.form.phone.trim() || null,
    email: modal.form.email || null,
    contactPerson: modal.form.contactPerson.trim() || null,
    contractStartDate: modal.form.contractStartDate || null,
    contractEndDate: modal.form.contractEndDate || null,
  }

  try {
    if (modal.mode === 'create') {
      await schoolApi.create(body)
    } else {
      await schoolApi.update(modal.id, body)
    }
    modal.open = false
    load()
  } catch (e) {
    modal.error = e.response?.data?.message ?? 'Lỗi không xác định'
  } finally {
    modal.saving = false
  }
}

/* ── Xóa trường ── */
async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await schoolApi.remove(deleteTarget.value.id)
    deleteTarget.value = null
    load()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Xóa thất bại')
    deleteTarget.value = null
  }
}

const STATUS_LABEL = { ACTIVE: 'Hoạt động', INACTIVE: 'Ngừng hoạt động', EXPIRED: 'Hết hạn' }
</script>

<template>
  <div class="page">
    <!-- ================= HEADER ================= -->
    <div class="page__head">
      <div>
        <h1 class="page__title">Quản lý trường</h1>
        <p class="page__sub">Danh sách trường khách hàng đang hợp tác</p>
      </div>

      <button class="btn" @click="openCreate">+ Thêm trường</button>
    </div>

    <!-- ================= FILTER (gọn, đồng bộ trang Giáo viên) ================= -->
    <div class="filters">
      <div class="filter-total">
        <span class="filter-total__num">{{ total }}</span>
        <span class="filter-total__label">Tổng trường</span>
      </div>

      <div class="filter-search">
        <svg
          viewBox="0 0 24 24"
          width="16"
          height="16"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <circle cx="11" cy="11" r="8" />
          <line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>
        <input
          v-model="keyword"
          type="text"
          placeholder="Tên trường, địa chỉ, người liên hệ, SĐT..."
          @keyup.enter="searchNow"
        />
      </div>

      <select v-model="branchFilter" class="filter-select">
        <option value="">Tất cả chi nhánh</option>
        <option v-for="b in branches" :key="b.id" :value="b.id">{{ b.name }}</option>
      </select>

      <select v-model="statusFilter" class="filter-select">
        <option value="">Tất cả trạng thái</option>
        <option value="ACTIVE">Hoạt động</option>
        <option value="INACTIVE">Ngừng hoạt động</option>
        <option value="EXPIRED">Hết hạn</option>
      </select>

      <button class="btn-filter btn-filter--ghost" @click="clearSearch">Xóa lọc</button>
    </div>

    <!-- ================= TABLE ================= -->
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Tên trường</th>
            <th>Liên hệ</th>
            <th>Chi nhánh</th>
            <th>Hợp đồng</th>
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

          <tr v-for="item in items" :key="item.id" class="row-clickable" @click="openEdit(item)">
            <td class="col-title">
              <div class="title-text">{{ item.name }}</div>
              <div v-if="item.address" class="desc-text">{{ item.address }}</div>
            </td>

            <td>
              <div v-if="item.contactPerson" class="title-text">{{ item.contactPerson }}</div>
              <div v-if="item.phone" class="desc-text">SĐT: {{ item.phone }}</div>
              <div v-if="item.email" class="desc-text">{{ item.email }}</div>
              <span v-if="!item.contactPerson && !item.phone && !item.email">-</span>
            </td>

            <td>
              <span class="cat-badge">{{ item.branchName ?? branchName(item.branchId) }}</span>
            </td>

            <td>
              <span v-if="item.contractStartDate || item.contractEndDate" class="desc-text">
                {{ item.contractStartDate ?? '?' }} → {{ item.contractEndDate ?? '?' }}
              </span>
              <span v-else>-</span>
            </td>

            <td>
              <span class="badge" :class="item.status === 'ACTIVE' ? 'badge--pub' : 'badge--draft'">
                {{ STATUS_LABEL[item.status] ?? item.status }}
              </span>
            </td>

            <td class="col-actions" @click.stop>
              <button class="act-btn act-btn--edit" title="Sửa" @click="openEdit(item)">Sửa</button>
              <button class="act-btn act-btn--del" title="Xóa" @click="deleteTarget = item">
                Xóa
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- ================= PAGINATION (component dùng chung) ================= -->
    <Pagination
      v-if="totalPages > 1"
      :model-value="page"
      :total-pages="totalPages"
      @update:model-value="goPage"
    />

    <!-- ================= MODAL: tạo/sửa trường ================= -->
    <div v-if="modal.open" class="overlay" @click.self="modal.open = false">
      <div class="modal">
        <h3>{{ modal.mode === 'create' ? 'Thêm trường' : 'Sửa trường' }}</h3>

        <div class="form-group">
          <label>Chi nhánh phụ trách *</label>
          <select
            v-model="modal.form.branchId"
            :class="{ 'input-error': modal.errors.branchId }"
            @change="clearFieldError('branchId')"
          >
            <option value="">-- Chọn chi nhánh --</option>
            <option v-for="b in branches" :key="b.id" :value="b.id">{{ b.name }}</option>
          </select>
          <small v-if="modal.errors.branchId" class="field-error">{{
            modal.errors.branchId
          }}</small>
        </div>

        <div class="form-group">
          <label>Tên trường *</label>
          <input
            v-model="modal.form.name"
            placeholder="VD: Trường Tiểu học Ban Mai"
            :class="{ 'input-error': modal.errors.name }"
            @input="clearFieldError('name')"
          />
          <small v-if="modal.errors.name" class="field-error">{{ modal.errors.name }}</small>
        </div>

        <div class="form-group">
          <label>Địa chỉ *</label>
          <input
            v-model="modal.form.address"
            placeholder="VD: Lê Chân, Hải Phòng"
            :class="{ 'input-error': modal.errors.address }"
            @input="clearFieldError('address')"
          />
          <small v-if="modal.errors.address" class="field-error">{{ modal.errors.address }}</small>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label>Số điện thoại *</label>
            <input
              :value="modal.form.phone"
              placeholder="10 số"
              inputmode="numeric"
              :class="{ 'input-error': modal.errors.phone }"
              @input="onPhoneInput"
            />
            <small v-if="modal.errors.phone" class="field-error">{{ modal.errors.phone }}</small>
          </div>

          <div class="form-group">
            <label>Email *</label>
            <input
              v-model="modal.form.email"
              placeholder="school@example.com"
              :class="{ 'input-error': modal.errors.email }"
              @input="clearFieldError('email')"
            />
            <small v-if="modal.errors.email" class="field-error">{{ modal.errors.email }}</small>
          </div>
        </div>

        <div class="form-group">
          <label>Người liên hệ *</label>
          <input
            v-model="modal.form.contactPerson"
            placeholder="VD: Cô Nguyễn Thu Hằng"
            :class="{ 'input-error': modal.errors.contactPerson }"
            @input="clearFieldError('contactPerson')"
          />
          <small v-if="modal.errors.contactPerson" class="field-error">{{
            modal.errors.contactPerson
          }}</small>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label>Ngày bắt đầu hợp đồng *</label>
            <DateField
              v-model="modal.form.contractStartDate"
              :invalid="!!modal.errors.contractStartDate"
              @update:modelValue="clearFieldError('contractStartDate')"
            />
            <small v-if="modal.errors.contractStartDate" class="field-error">{{
              modal.errors.contractStartDate
            }}</small>
          </div>

          <div class="form-group">
            <label>Ngày hết hạn hợp đồng *</label>
            <DateField
              v-model="modal.form.contractEndDate"
              :invalid="!!modal.errors.contractEndDate"
              @update:modelValue="clearFieldError('contractEndDate')"
            />
            <small v-if="modal.errors.contractEndDate" class="field-error">{{
              modal.errors.contractEndDate
            }}</small>
          </div>
        </div>

        <div class="form-group">
          <label>Trạng thái</label>
          <select v-model="modal.form.status">
            <option value="ACTIVE">Hoạt động</option>
            <option value="INACTIVE">Ngừng hoạt động</option>
            <option value="EXPIRED">Hết hạn</option>
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
          Bạn có chắc muốn xóa trường <strong>{{ deleteTarget.name }}</strong
          >?
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

/* ================= Header ================= */
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

/* ================= Filter (gọn, giống trang Giáo viên) ================= */
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.65rem;
  padding: 1rem 1.1rem;
  margin-bottom: 18px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 12px;
}

.filter-total {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.1rem;
  padding: 0.5rem 1.1rem;
  background: #f97316;
  border-radius: 10px;
  color: #fff;
  flex-shrink: 0;
}

.filter-total__num {
  font-size: 1.15rem;
  font-weight: 800;
  line-height: 1;
}

.filter-total__label {
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 0.4px;
  text-transform: uppercase;
  opacity: 0.92;
}

.filter-search {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex: 1;
  min-width: 200px;
  height: 38px;
  padding: 0 0.75rem;
  background: var(--c-surface-2);
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  color: var(--c-text-muted);
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.filter-search:focus-within {
  border-color: #f97316;
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.12);
}

.filter-search input {
  border: none;
  background: transparent;
  outline: none;
  width: 100%;
  font-size: 0.875rem;
  color: var(--c-text);
}

.filter-select {
  height: 38px;
  padding: 0 0.75rem;
  background: var(--c-surface-2);
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  font-size: 0.85rem;
  color: var(--c-text);
  cursor: pointer;
  outline: none;
  transition: border-color 0.15s;
}

.filter-select:focus {
  border-color: #f97316;
}

.btn-filter {
  height: 38px;
  padding: 0 1.1rem;
  border: none;
  border-radius: 8px;
  background: #f97316;
  color: #fff;
  font-size: 0.85rem;
  font-weight: 700;
  cursor: pointer;
  transition:
    filter 0.15s,
    transform 0.15s;
}

.btn-filter:hover {
  filter: brightness(1.06);
  transform: translateY(-1px);
}

.btn-filter--ghost {
  background: var(--c-surface-2);
  color: var(--c-text);
}

/* ================= Buttons ================= */
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

/* ================= Table ================= */
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

.desc-text {
  margin-top: 2px;
  color: var(--c-text-muted);
  font-size: 12px;
}

.cat-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.12);
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
}

:root[data-theme='dark'] .cat-badge {
  color: #93c5fd;
}

/* ================= Status ================= */
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

/* ================= Action ================= */
.col-actions {
  white-space: nowrap;
}

.act-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  transition: 0.2s;
}

.act-btn:hover {
  background: var(--c-surface-2);
}
.act-btn--edit {
  color: #16a34a;
}
.act-btn--del {
  color: #dc2626;
}

.act-btn--edit:hover,
.act-btn--edit:focus-visible {
  background: #16a34a;
  color: #fff;
}
.act-btn--del:hover,
.act-btn--del:focus-visible {
  background: #dc2626;
  color: #fff;
}

/* ================= Modal ================= */
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
  width: 480px;
  max-width: 100%;
  max-height: 90vh;
  overflow-y: auto;
  background: var(--c-surface);
  border-radius: 16px;
  padding: 28px;
}

.modal h3 {
  margin-top: 0;
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

.form-row {
  display: flex;
  gap: 12px;
}

.form-row .form-group {
  flex: 1;
  min-width: 0;
}

.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
  color: var(--c-text);
}

.form-group input,
.form-group select,
.form-group textarea {
  width: 100%;
  height: 40px;
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  padding: 0 12px;
  font-size: 14px;
  box-sizing: border-box;
  font-family: inherit;
}

.form-group input:focus,
.form-group select:focus,
.form-group textarea:focus {
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

/* ================= Message ================= */
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

/* ================= Responsive ================= */
@media (max-width: 640px) {
  .page__head {
    flex-direction: column;
    align-items: flex-start;
    gap: 15px;
  }

  .page__head .btn {
    width: 100%;
  }

  .filters {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-total {
    flex-direction: row;
    justify-content: center;
    gap: 0.4rem;
  }

  .filter-search,
  .filter-select {
    width: 100%;
  }

  .btn-filter {
    width: 100%;
  }

  .form-row {
    flex-direction: column;
    gap: 0;
  }
}
</style>
