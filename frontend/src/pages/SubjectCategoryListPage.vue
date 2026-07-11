<!-- src/pages/SubjectCategoryListPage.vue -->
<script setup>
/**
 * Trang "Nhóm môn học": CRUD SubjectCategory + môn học (Subject) lồng theo từng nhóm
 * (mở rộng 1 dòng nhóm -> xem/thêm/sửa/xóa môn thuộc nhóm đó). Validate ở cả 2 cấp
 * (nhóm + môn) trước khi gọi API, mirror đúng rule phía backend.
 *
 * readOnly=true (dùng cho portal GIÁO VIÊN): ẩn mọi nút thêm/sửa/xóa, chỉ xem.
 */
import { ref, reactive, onMounted } from 'vue'
import { subjectCategoryApi } from '@/api/subjectCategories'
import { subjectApi } from '@/api/subjects'

/* ── State: danh sách nhóm môn ── */
const loading = ref(false)
const items = ref([])
const total = ref(0)
const keyword = ref('')
const page = ref(0)
const pageSize = 10

/** Toàn bộ nhóm ACTIVE (không phân trang) — dùng cho dropdown "Nhóm môn" trong modal môn học. */
const allCategories = ref([])

const modal = reactive({
  open: false,
  mode: 'create', // 'create' | 'edit'
  id: null,
  form: { code: '', name: '', description: '', status: 'ACTIVE' },
  errors: {},
  error: '',
  saving: false,
})

const deleteTarget = ref(null)

/* ── State: mở rộng dòng + môn học lồng bên trong ── */
const expandedId = ref(null)
const subjectsLoading = ref(false)
const subjectsByCategory = ref([])

const subjectModal = reactive({
  open: false,
  mode: 'create',
  id: null,
  categoryName: '', // tên nhóm môn hiện tại — chỉ hiển thị (read-only) khi tạo mới
  form: { code: '', name: '', categoryId: null, description: '', status: 'ACTIVE' },
  errors: {},
  error: '',
  saving: false,
})

const deleteSubjectTarget = ref(null)

/* ── Load nhóm môn ── */
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
    // Nhóm đang mở rộng bị mất khỏi trang hiện tại (đổi trang/tìm kiếm) -> thu gọn lại
    if (expandedId.value && !items.value.some((i) => i.id === expandedId.value)) {
      expandedId.value = null
    }
  } catch {
    // handle error
  } finally {
    loading.value = false
  }
}

async function loadAllCategories() {
  try {
    const { data } = await subjectCategoryApi.listActive()
    allCategories.value = data
  } catch {
    // dropdown lỗi không block trang
  }
}

onMounted(() => {
  load()
  loadAllCategories()
})

function onSearch() {
  page.value = 0
  load()
}
function onPage(p) {
  page.value = p
  load()
}

/* ── Mở rộng / thu gọn + load môn học của nhóm ── */
async function toggleExpand(item) {
  if (expandedId.value === item.id) {
    expandedId.value = null
    return
  }
  expandedId.value = item.id
  await reloadSubjects(item.id)
}

async function reloadSubjects(categoryId) {
  subjectsLoading.value = true
  subjectsByCategory.value = []
  try {
    const { data } = await subjectApi.list(categoryId)
    subjectsByCategory.value = data
  } catch {
    // giữ danh sách rỗng, không chặn UI
  } finally {
    subjectsLoading.value = false
  }
}

/* ── Validate: nhóm môn (mirror SubjectCategoryRequest phía backend) ── */
const CATEGORY_CODE_RE = /^[A-Z0-9_]{2,50}$/
function validateCategoryForm(form) {
  const errors = {}
  const code = form.code.trim()
  const name = form.name.trim()
  if (!code) errors.code = 'Mã không được để trống'
  else if (!CATEGORY_CODE_RE.test(code)) errors.code = 'Chỉ chữ hoa, số, dấu _ (2-50 ký tự)'
  if (!name) errors.name = 'Tên nhóm môn không được để trống'
  else if (name.length > 100) errors.name = 'Tên tối đa 100 ký tự'
  if (form.description && form.description.length > 500)
    errors.description = 'Mô tả tối đa 500 ký tự'
  return errors
}

/* ── Modal tạo/sửa nhóm môn ── */
function openCreate() {
  Object.assign(modal, {
    open: true,
    mode: 'create',
    id: null,
    form: { code: '', name: '', description: '', status: 'ACTIVE' },
    errors: {},
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
    errors: {},
    error: '',
    saving: false,
  })
}

function clearFieldError(field) {
  if (modal.errors[field]) delete modal.errors[field]
}

async function saveModal() {
  modal.errors = validateCategoryForm(modal.form)
  if (Object.keys(modal.errors).length) return

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
    loadAllCategories()
  } catch (e) {
    modal.error = e.response?.data?.message ?? 'Lỗi không xác định'
  } finally {
    modal.saving = false
  }
}

/* ── Xóa nhóm môn ── */
async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await subjectCategoryApi.remove(deleteTarget.value.id)
    deleteTarget.value = null
    load()
    loadAllCategories()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Xóa thất bại')
  }
}

/* ── Validate: môn học (mirror SubjectRequest phía backend) ── */
const SUBJECT_CODE_RE = /^[A-Z0-9_]{2,20}$/
function validateSubjectForm(form) {
  const errors = {}
  const code = form.code.trim()
  const name = form.name.trim()
  if (!code) errors.code = 'Mã môn không được để trống'
  else if (!SUBJECT_CODE_RE.test(code)) errors.code = 'Chỉ chữ hoa, số, dấu _ (2-20 ký tự)'
  if (!name) errors.name = 'Tên môn học không được để trống'
  else if (name.length > 150) errors.name = 'Tên tối đa 150 ký tự'
  if (!form.categoryId) errors.categoryId = 'Vui lòng chọn nhóm môn'
  if (form.description && form.description.length > 500)
    errors.description = 'Mô tả tối đa 500 ký tự'
  return errors
}

/* ── Modal tạo/sửa môn học ── */
function openCreateSubject(category) {
  Object.assign(subjectModal, {
    open: true,
    mode: 'create',
    id: null,
    categoryName: category.name,
    form: { code: '', name: '', categoryId: category.id, description: '', status: 'ACTIVE' },
    errors: {},
    error: '',
    saving: false,
  })
}

function openEditSubject(subject) {
  Object.assign(subjectModal, {
    open: true,
    mode: 'edit',
    id: subject.id,
    form: {
      code: subject.code,
      name: subject.name,
      categoryId: subject.categoryId,
      description: subject.description ?? '',
      status: subject.status,
    },
    errors: {},
    error: '',
    saving: false,
  })
}

function clearSubjectFieldError(field) {
  if (subjectModal.errors[field]) delete subjectModal.errors[field]
}

async function saveSubjectModal() {
  subjectModal.errors = validateSubjectForm(subjectModal.form)
  if (Object.keys(subjectModal.errors).length) return

  subjectModal.saving = true
  subjectModal.error = ''
  const body = { ...subjectModal.form, categoryId: Number(subjectModal.form.categoryId) }
  try {
    if (subjectModal.mode === 'create') {
      await subjectApi.create(body)
    } else {
      await subjectApi.update(subjectModal.id, body)
    }
    subjectModal.open = false
    // Môn có thể đã đổi sang nhóm khác lúc sửa -> luôn refresh đúng nhóm đang mở
    if (expandedId.value) await reloadSubjects(expandedId.value)
    load() // cập nhật lại "Số môn" trên dòng nhóm
  } catch (e) {
    subjectModal.error = e.response?.data?.message ?? 'Lỗi không xác định'
  } finally {
    subjectModal.saving = false
  }
}

async function confirmDeleteSubject() {
  if (!deleteSubjectTarget.value) return
  try {
    await subjectApi.remove(deleteSubjectTarget.value.id)
    deleteSubjectTarget.value = null
    if (expandedId.value) await reloadSubjects(expandedId.value)
    load()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Xóa thất bại')
    deleteSubjectTarget.value = null
  }
}

/* ── Helpers ── */
const totalPages = () => Math.ceil(total.value / pageSize)
</script>

<template>
  <div class="sc-page">
    <!-- Header -->
    <div class="sc-header">
      <div>
        <h2 class="sc-title">Nhóm môn học</h2>
        <p v-if="readOnly" class="sc-readonly-note">
          Chế độ xem — bấm vào 1 dòng để xem các môn học trong nhóm.
        </p>
      </div>
      <button v-if="!readOnly" class="btn btn-primary" @click="openCreate">+ Thêm nhóm môn</button>
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
            <th class="expand-col"></th>
            <th>Mã (Code)</th>
            <th>Tên nhóm môn</th>
            <th>Mô tả</th>
            <th>Số môn</th>
            <th>Trạng thái</th>
            <th v-if="!readOnly"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td :colspan="readOnly ? 6 : 7" class="text-center text-muted">Đang tải...</td>
          </tr>
          <tr v-else-if="!items.length">
            <td :colspan="readOnly ? 6 : 7" class="text-center text-muted">Chưa có dữ liệu</td>
          </tr>
          <template v-for="item in items" :key="item.id">
            <tr class="row-clickable" @click="toggleExpand(item)">
              <td class="expand-cell">
                <span class="chevron" :class="{ open: expandedId === item.id }">›</span>
              </td>
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
              <td v-if="!readOnly" class="actions" @click.stop>
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

            <!-- Dòng mở rộng: môn học thuộc nhóm -->
            <tr v-if="expandedId === item.id" class="row-expanded">
              <td :colspan="readOnly ? 6 : 7">
                <div class="subj-panel">
                  <div class="subj-panel__head">
                    <h4>Môn học trong nhóm "{{ item.name }}"</h4>
                    <button
                      v-if="!readOnly"
                      class="btn btn-sm btn-primary"
                      @click="openCreateSubject(item)"
                    >
                      + Thêm môn học
                    </button>
                  </div>

                  <div v-if="subjectsLoading" class="text-muted small subj-loading">
                    Đang tải môn học...
                  </div>

                  <table v-else-if="subjectsByCategory.length" class="subj-table">
                    <thead>
                      <tr>
                        <th>Mã</th>
                        <th>Tên môn</th>
                        <th>Mô tả</th>
                        <th>Trạng thái</th>
                        <th v-if="!readOnly"></th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="s in subjectsByCategory" :key="s.id">
                        <td>
                          <code>{{ s.code }}</code>
                        </td>
                        <td>{{ s.name }}</td>
                        <td class="text-muted small">{{ s.description ?? '—' }}</td>
                        <td>
                          <span
                            :class="['badge', s.status === 'ACTIVE' ? 'badge-green' : 'badge-gray']"
                          >
                            {{ s.status === 'ACTIVE' ? 'Hoạt động' : 'Tắt' }}
                          </span>
                        </td>
                        <td v-if="!readOnly" class="actions">
                          <button class="btn btn-sm btn-outline" @click="openEditSubject(s)">
                            Sửa
                          </button>
                          <button
                            class="btn btn-sm btn-danger"
                            :disabled="s.lessonCount > 0"
                            :title="s.lessonCount > 0 ? 'Có bài giảng đang dùng môn này' : 'Xóa'"
                            @click="deleteSubjectTarget = s"
                          >
                            Xóa
                          </button>
                        </td>
                      </tr>
                    </tbody>
                  </table>

                  <p v-else class="text-muted small subj-empty">
                    Nhóm này chưa có môn học nào{{
                      readOnly ? '.' : ' — bấm "+ Thêm môn học" để tạo.'
                    }}
                  </p>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <!-- Pagination -->
    <div class="sc-pagination" v-if="totalPages() > 1">
      <button :disabled="page === 0" @click="onPage(page - 1)">‹</button>
      <span>Trang {{ page + 1 }} / {{ totalPages() }}</span>
      <button :disabled="page >= totalPages() - 1" @click="onPage(page + 1)">›</button>
    </div>

    <!-- Modal tạo/sửa nhóm môn -->
    <div v-if="modal.open" class="modal-overlay" @click.self="modal.open = false">
      <div class="modal-box">
        <h3>{{ modal.mode === 'create' ? 'Thêm nhóm môn' : 'Sửa nhóm môn' }}</h3>

        <div class="form-group">
          <label>Mã (Code) *</label>
          <input
            v-model="modal.form.code"
            placeholder="VD: TIN_HOC"
            :disabled="modal.mode === 'edit'"
            :class="{ 'input-error': modal.errors.code }"
            @input="clearFieldError('code')"
          />
          <small v-if="modal.errors.code" class="field-error">{{ modal.errors.code }}</small>
          <small v-else>Chỉ chữ hoa, số, dấu _. Không đổi sau khi tạo.</small>
        </div>
        <div class="form-group">
          <label>Tên nhóm môn *</label>
          <input
            v-model="modal.form.name"
            placeholder="VD: Tin học"
            :class="{ 'input-error': modal.errors.name }"
            @input="clearFieldError('name')"
          />
          <small v-if="modal.errors.name" class="field-error">{{ modal.errors.name }}</small>
        </div>
        <div class="form-group">
          <label>Mô tả</label>
          <textarea
            v-model="modal.form.description"
            rows="2"
            :class="{ 'input-error': modal.errors.description }"
            @input="clearFieldError('description')"
          />
          <small v-if="modal.errors.description" class="field-error">{{
            modal.errors.description
          }}</small>
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

    <!-- Confirm xóa nhóm môn -->
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

    <!-- Modal tạo/sửa môn học -->
    <div v-if="subjectModal.open" class="modal-overlay" @click.self="subjectModal.open = false">
      <div class="modal-box">
        <h3>{{ subjectModal.mode === 'create' ? 'Thêm môn học' : 'Sửa môn học' }}</h3>

        <div class="form-group">
          <label>Mã môn *</label>
          <input
            v-model="subjectModal.form.code"
            placeholder="VD: TIENGANH01"
            :class="{ 'input-error': subjectModal.errors.code }"
            @input="clearSubjectFieldError('code')"
          />
          <small v-if="subjectModal.errors.code" class="field-error">{{
            subjectModal.errors.code
          }}</small>
          <small v-else>Chỉ chữ hoa, số, dấu _ (2-20 ký tự).</small>
        </div>
        <div class="form-group">
          <label>Tên môn học *</label>
          <input
            v-model="subjectModal.form.name"
            placeholder="VD: Giao tiếp Tiếng Anh"
            :class="{ 'input-error': subjectModal.errors.name }"
            @input="clearSubjectFieldError('name')"
          />
          <small v-if="subjectModal.errors.name" class="field-error">{{
            subjectModal.errors.name
          }}</small>
        </div>
        <div v-if="subjectModal.mode === 'create'" class="form-group">
          <label>Nhóm môn</label>
          <input :value="subjectModal.categoryName" disabled />
        </div>
        <div v-else class="form-group">
          <label>Nhóm môn *</label>
          <select
            v-model="subjectModal.form.categoryId"
            :class="{ 'input-error': subjectModal.errors.categoryId }"
            @change="clearSubjectFieldError('categoryId')"
          >
            <option :value="null">-- Chọn nhóm môn --</option>
            <option v-for="c in allCategories" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
          <small v-if="subjectModal.errors.categoryId" class="field-error">{{
            subjectModal.errors.categoryId
          }}</small>
        </div>
        <div class="form-group">
          <label>Mô tả</label>
          <textarea
            v-model="subjectModal.form.description"
            rows="2"
            :class="{ 'input-error': subjectModal.errors.description }"
            @input="clearSubjectFieldError('description')"
          />
          <small v-if="subjectModal.errors.description" class="field-error">{{
            subjectModal.errors.description
          }}</small>
        </div>
        <div class="form-group">
          <label>Trạng thái</label>
          <select v-model="subjectModal.form.status">
            <option value="ACTIVE">Hoạt động</option>
            <option value="DISABLED">Tắt</option>
          </select>
        </div>

        <p v-if="subjectModal.error" class="error-msg">{{ subjectModal.error }}</p>

        <div class="modal-actions">
          <button class="btn btn-outline" @click="subjectModal.open = false">Hủy</button>
          <button class="btn btn-primary" :disabled="subjectModal.saving" @click="saveSubjectModal">
            {{ subjectModal.saving ? 'Đang lưu...' : 'Lưu' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Confirm xóa môn học -->
    <div v-if="deleteSubjectTarget" class="modal-overlay" @click.self="deleteSubjectTarget = null">
      <div class="modal-box modal-sm">
        <h3>Xác nhận xóa</h3>
        <p>
          Bạn có chắc muốn xóa môn học <strong>{{ deleteSubjectTarget.name }}</strong
          >?
        </p>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="deleteSubjectTarget = null">Hủy</button>
          <button class="btn btn-danger" @click="confirmDeleteSubject">Xóa</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sc-page {
  padding: 1.5rem;
  max-width: 980px;
}
.sc-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1rem;
  gap: 12px;
}
.sc-title {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0;
}
.sc-readonly-note {
  margin: 4px 0 0;
  font-size: 0.8rem;
  color: var(--c-text-muted);
}
.sc-toolbar {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}
.input-search {
  flex: 1;
  padding: 0.45rem 0.75rem;
  border: 1px solid var(--c-input-border);
  border-radius: 6px;
  font-size: 0.9rem;
}
.sc-table-wrap {
  overflow-x: auto;
  border: 1px solid var(--c-border);
  border-radius: 8px;
}
.sc-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}
.sc-table th {
  background: var(--c-surface-2);
  padding: 0.6rem 1rem;
  text-align: left;
  font-weight: 600;
  border-bottom: 1px solid var(--c-border);
}
.expand-col {
  width: 28px;
}
.sc-table td {
  padding: 0.6rem 1rem;
  border-bottom: 1px solid #f3f4f6;
  vertical-align: middle;
}
.sc-table tbody tr:last-child td {
  border-bottom: none;
}
.row-clickable {
  cursor: pointer;
}
.row-clickable:hover td {
  background: #fafafa;
}
.expand-cell {
  width: 28px;
}
.chevron {
  display: inline-block;
  color: var(--c-text-muted);
  font-size: 1rem;
  transition: transform 0.15s ease;
}
.chevron.open {
  transform: rotate(90deg);
  color: #f97316;
}
.row-expanded:hover td {
  background: var(--c-surface-2);
}
.row-expanded td {
  background: var(--c-surface-2);
  padding: 0;
  border-bottom: 1px solid var(--c-border);
}
.subj-panel {
  padding: 14px 20px 16px 46px;
}
.subj-panel__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.subj-panel__head h4 {
  margin: 0;
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--c-text);
}
.subj-loading,
.subj-empty {
  padding: 6px 0 4px;
}
.subj-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 8px;
  overflow: hidden;
}
.subj-table th {
  background: #f3f4f6;
  padding: 0.45rem 0.75rem;
  text-align: left;
  font-weight: 600;
  color: var(--c-text);
  border-bottom: 1px solid var(--c-border);
}
.subj-table td {
  padding: 0.45rem 0.75rem;
  border-bottom: 1px solid #f3f4f6;
  vertical-align: middle;
}
.subj-table tbody tr:last-child td {
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
  color: var(--c-text-muted);
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
  background: var(--c-surface);
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
  color: var(--c-text);
}
.form-group input,
.form-group select,
.form-group textarea {
  width: 100%;
  padding: 0.4rem 0.7rem;
  border: 1px solid var(--c-input-border);
  border-radius: 6px;
  font-size: 0.9rem;
  box-sizing: border-box;
}
.form-group small {
  color: var(--c-text-muted);
  font-size: 0.78rem;
}
.input-error {
  border-color: #dc2626 !important;
}
.field-error {
  color: #dc2626 !important;
  font-size: 0.78rem;
  display: block;
  margin-top: 2px;
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
  background: var(--c-surface);
  border: 1px solid var(--c-input-border);
  color: var(--c-text);
}
.btn-outline:hover {
  background: var(--c-surface-2);
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
  color: var(--c-text-muted);
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
