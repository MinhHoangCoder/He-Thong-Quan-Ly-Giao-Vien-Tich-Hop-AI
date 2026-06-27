<!-- src/pages/SubjectCategoryListPage.vue -->
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { subjectCategoryApi } from '@/api/subjectCategories'

/* ── State ── */
const loading = ref(false)
const items = ref([])
const total = ref(0)
const keyword = ref('')
const page = ref(0)
const pageSize = 10

const modal = reactive({
  open: false,
  mode: 'create', // 'create' | 'edit'
  id: null,
  form: { code: '', name: '', description: '', status: 'ACTIVE' },
  error: '',
  saving: false,
})

const deleteTarget = ref(null)

/* ── Load ── */
async function load() {
  loading.value = true
  try {
    const res = await subjectCategoryApi.list({
      keyword: keyword.value || undefined,
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

onMounted(load)

function onSearch() {
  page.value = 0
  load()
}
function onPage(p) {
  page.value = p
  load()
}

/* ── Modal tạo/sửa ── */
function openCreate() {
  Object.assign(modal, {
    open: true,
    mode: 'create',
    id: null,
    form: { code: '', name: '', description: '', status: 'ACTIVE' },
    error: '',
    saving: false,
  })
}

async function openEdit(item) {
  Object.assign(modal, {
    open: true,
    mode: 'edit',
    id: item.id,
    form: {
      code: item.code,
      name: item.name,
      description: item.description ?? '',
      status: item.status,
    },
    error: '',
    saving: false,
  })
}

async function saveModal() {
  modal.saving = true
  modal.error = ''
  try {
    if (modal.mode === 'create') {
      await subjectCategoryApi.create(modal.form)
    } else {
      await subjectCategoryApi.update(modal.id, modal.form)
    }
    modal.open = false
    load()
  } catch (e) {
    modal.error = e.response?.data?.message ?? 'Lỗi không xác định'
  } finally {
    modal.saving = false
  }
}

/* ── Xóa ── */
async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await subjectCategoryApi.remove(deleteTarget.value.id)
    deleteTarget.value = null
    load()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Xóa thất bại')
  }
}

/* ── Helpers ── */
const totalPages = () => Math.ceil(total.value / pageSize)
</script>

<template>
  <div class="sc-page">
    <!-- Header -->
    <div class="sc-header">
      <h2 class="sc-title">Nhóm môn học</h2>
      <button class="btn btn-primary" @click="openCreate">+ Thêm nhóm môn</button>
    </div>

    <!-- Search -->
    <div class="sc-toolbar">
      <input
        v-model="keyword"
        class="input-search"
        placeholder="Tìm theo tên, mã..."
        @keyup.enter="onSearch"
      />
      <button class="btn btn-outline" @click="onSearch">Tìm</button>
    </div>

    <!-- Table -->
    <div class="sc-table-wrap" :class="{ loading }">
      <table class="sc-table">
        <thead>
          <tr>
            <th>Mã (Code)</th>
            <th>Tên nhóm môn</th>
            <th>Mô tả</th>
            <th>Số môn</th>
            <th>Trạng thái</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="6" class="text-center text-muted">Đang tải...</td>
          </tr>
          <tr v-else-if="!items.length">
            <td colspan="6" class="text-center text-muted">Chưa có dữ liệu</td>
          </tr>
          <tr v-for="item in items" :key="item.id">
            <td>
              <code>{{ item.code }}</code>
            </td>
            <td class="font-medium">{{ item.name }}</td>
            <td class="text-muted small">{{ item.description ?? '—' }}</td>
            <td>{{ item.subjectCount }}</td>
            <td>
              <span :class="['badge', item.status === 'ACTIVE' ? 'badge-green' : 'badge-gray']">
                {{ item.status === 'ACTIVE' ? 'Hoạt động' : 'Tắt' }}
              </span>
            </td>
            <td class="actions">
              <button class="btn btn-sm btn-outline" @click="openEdit(item)">Sửa</button>
              <button
                class="btn btn-sm btn-danger"
                :disabled="item.subjectCount > 0"
                :title="item.subjectCount > 0 ? 'Có môn học đang dùng nhóm này' : 'Xóa'"
                @click="deleteTarget = item"
              >
                Xóa
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Pagination -->
    <div class="sc-pagination" v-if="totalPages() > 1">
      <button :disabled="page === 0" @click="onPage(page - 1)">‹</button>
      <span>Trang {{ page + 1 }} / {{ totalPages() }}</span>
      <button :disabled="page >= totalPages() - 1" @click="onPage(page + 1)">›</button>
    </div>

    <!-- Modal tạo/sửa -->
    <div v-if="modal.open" class="modal-overlay" @click.self="modal.open = false">
      <div class="modal-box">
        <h3>{{ modal.mode === 'create' ? 'Thêm nhóm môn' : 'Sửa nhóm môn' }}</h3>

        <div class="form-group">
          <label>Mã (Code) *</label>
          <input
            v-model="modal.form.code"
            placeholder="VD: TIN_HOC"
            :disabled="modal.mode === 'edit'"
          />
          <small>Chỉ chữ hoa, số, dấu _. Không đổi sau khi tạo.</small>
        </div>
        <div class="form-group">
          <label>Tên nhóm môn *</label>
          <input v-model="modal.form.name" placeholder="VD: Tin học" />
        </div>
        <div class="form-group">
          <label>Mô tả</label>
          <textarea v-model="modal.form.description" rows="2" />
        </div>
        <div class="form-group">
          <label>Trạng thái</label>
          <select v-model="modal.form.status">
            <option value="ACTIVE">Hoạt động</option>
            <option value="DISABLED">Tắt</option>
          </select>
        </div>

        <p v-if="modal.error" class="error-msg">{{ modal.error }}</p>

        <div class="modal-actions">
          <button class="btn btn-outline" @click="modal.open = false">Hủy</button>
          <button class="btn btn-primary" :disabled="modal.saving" @click="saveModal">
            {{ modal.saving ? 'Đang lưu...' : 'Lưu' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Confirm xóa -->
    <div v-if="deleteTarget" class="modal-overlay" @click.self="deleteTarget = null">
      <div class="modal-box modal-sm">
        <h3>Xác nhận xóa</h3>
        <p>
          Bạn có chắc muốn xóa nhóm môn <strong>{{ deleteTarget.name }}</strong
          >?
        </p>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="deleteTarget = null">Hủy</button>
          <button class="btn btn-danger" @click="confirmDelete">Xóa</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sc-page {
  padding: 1.5rem;
  max-width: 900px;
}
.sc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}
.sc-title {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0;
}
.sc-toolbar {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}
.input-search {
  flex: 1;
  padding: 0.45rem 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.9rem;
}
.sc-table-wrap {
  overflow-x: auto;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}
.sc-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}
.sc-table th {
  background: #f9fafb;
  padding: 0.6rem 1rem;
  text-align: left;
  font-weight: 600;
  border-bottom: 1px solid #e5e7eb;
}
.sc-table td {
  padding: 0.6rem 1rem;
  border-bottom: 1px solid #f3f4f6;
  vertical-align: middle;
}
.sc-table tr:last-child td {
  border-bottom: none;
}
.actions {
  display: flex;
  gap: 0.4rem;
}
.sc-pagination {
  margin-top: 1rem;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  justify-content: center;
}

.badge {
  display: inline-block;
  padding: 0.15rem 0.55rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
}
.badge-green {
  background: #dcfce7;
  color: #166534;
}
.badge-gray {
  background: #f3f4f6;
  color: #6b7280;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}
.modal-box {
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  width: 100%;
  max-width: 440px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}
.modal-sm {
  max-width: 360px;
}
.modal-box h3 {
  margin: 0 0 1rem;
  font-size: 1.05rem;
  font-weight: 600;
}
.form-group {
  margin-bottom: 0.9rem;
}
.form-group label {
  display: block;
  font-size: 0.85rem;
  font-weight: 500;
  margin-bottom: 0.25rem;
  color: #374151;
}
.form-group input,
.form-group select,
.form-group textarea {
  width: 100%;
  padding: 0.4rem 0.7rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.9rem;
  box-sizing: border-box;
}
.form-group small {
  color: #6b7280;
  font-size: 0.78rem;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 1rem;
}
.error-msg {
  color: #dc2626;
  font-size: 0.85rem;
  margin: 0.5rem 0 0;
}

.btn {
  padding: 0.45rem 1rem;
  border-radius: 6px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: 0.15s;
}
.btn-primary {
  background: #2563eb;
  color: white;
}
.btn-primary:hover {
  background: #1d4ed8;
}
.btn-outline {
  background: white;
  border: 1px solid #d1d5db;
  color: #374151;
}
.btn-outline:hover {
  background: #f9fafb;
}
.btn-danger {
  background: #dc2626;
  color: white;
}
.btn-danger:hover {
  background: #b91c1c;
}
.btn-danger:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.btn-sm {
  padding: 0.3rem 0.65rem;
  font-size: 0.8rem;
}

.text-center {
  text-align: center;
}
.text-muted {
  color: #9ca3af;
}
.font-medium {
  font-weight: 500;
}
.small {
  font-size: 0.83rem;
}
code {
  background: #f3f4f6;
  padding: 0.1rem 0.35rem;
  border-radius: 4px;
  font-size: 0.82rem;
  font-family: monospace;
}
</style>
