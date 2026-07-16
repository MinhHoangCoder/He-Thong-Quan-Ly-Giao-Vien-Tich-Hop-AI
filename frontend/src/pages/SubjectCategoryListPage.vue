<!-- src/pages/SubjectCategoryListPage.vue -->
<script setup>
/**
 * Trang "Nhóm môn học": CRUD SubjectCategory + môn học (Subject) lồng theo từng nhóm
 * (mở rộng 1 dòng nhóm -> xem/thêm/sửa/xóa môn thuộc nhóm đó). Validate ở cả 2 cấp
 * (nhóm + môn) trước khi gọi API, mirror đúng rule phía backend.
 *
 * FIX (2026-07-16): đồng bộ giao diện với "Kho bài giảng" (LessonListPage.vue) —
 * cùng bố cục page__head/filter-bar/table-wrap/pagination, cùng token màu & badge,
 * cùng kiểu nút hành động icon (✏️/🗑️) và modal xác nhận xóa. Chức năng CRUD
 * nhóm môn + môn học lồng bên trong giữ NGUYÊN không đổi.
 *
 * readOnly=true (dùng cho portal GIÁO VIÊN): ẩn mọi nút thêm/sửa/xóa, chỉ xem.
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { subjectCategoryApi } from '@/api/subjectCategories'
import { subjectApi } from '@/api/subjects'

/* ── State: danh sách nhóm môn ── */
const loading = ref(false)
const items = ref([])
const total = ref(0)
const keyword = ref('')
const page = ref(0)
const pageSize = 10
const pageInput = ref('')

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

/* =========================
   Pagination (đồng bộ với LessonListPage.vue)
========================= */

const totalPages = computed(() => Math.ceil(total.value / pageSize))

const visiblePages = computed(() => {
  const totalP = totalPages.value
  const current = page.value + 1

  let start = Math.max(1, current - 2)
  let end = Math.min(totalP, current + 2)

  if (end - start < 4) {
    if (start === 1) {
      end = Math.min(5, totalP)
    } else if (end === totalP) {
      start = Math.max(1, totalP - 4)
    }
  }

  const arr = []

  for (let i = start; i <= end; i++) {
    arr.push(i)
  }

  return arr
})

function goPage(index) {
  if (index < 0 || index >= totalPages.value) return

  page.value = index
  load()
}

function jumpPage() {
  const p = Number(pageInput.value)

  if (isNaN(p)) return
  if (p < 1) return
  if (p > totalPages.value) return

  goPage(p - 1)
}

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

function clearSearch() {
  keyword.value = ''
  page.value = 0
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
</script>

<template>
  <div class="page">
    <!-- ================= HEADER ================= -->
    <div class="page__head">
      <div>
        <h1 class="page__title">Nhóm môn học</h1>
        <p class="page__sub">
          {{
            readOnly
              ? 'Chế độ xem — bấm vào 1 dòng để xem các môn học trong nhóm.'
              : 'Quản lý nhóm môn và các môn học trực thuộc'
          }}
        </p>
      </div>

      <button v-if="!readOnly" class="btn" @click="openCreate">+ Thêm nhóm môn</button>
    </div>

    <!-- ================= FILTER ================= -->

    <div class="filter-bar">
      <label class="field field--wide">
        <span>Tìm kiếm</span>

        <input v-model="keyword" placeholder="Nhập tên, mã nhóm môn..." @keyup.enter="onSearch" />
      </label>

      <div class="filter-actions">
        <button class="btn" @click="onSearch">Lọc</button>

        <button class="btn btn--ghost" @click="clearSearch">Xóa lọc</button>
      </div>
    </div>

    <!-- ================= INFO ================= -->

    <p v-if="!loading" class="total">
      Tổng cộng
      <strong>{{ total }}</strong>
      nhóm môn
    </p>

    <!-- ================= TABLE ================= -->

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th width="28"></th>
            <th>Mã (Code)</th>
            <th>Tên nhóm môn</th>
            <th>Mô tả</th>
            <th>Số môn</th>
            <th>Trạng thái</th>
            <th v-if="!readOnly" width="120">Thao tác</th>
          </tr>
        </thead>

        <tbody>
          <tr v-if="loading">
            <td :colspan="readOnly ? 6 : 7" class="empty">Đang tải...</td>
          </tr>

          <tr v-else-if="items.length === 0">
            <td :colspan="readOnly ? 6 : 7" class="empty">Không có dữ liệu</td>
          </tr>

          <template v-for="item in items" :key="item.id">
            <tr class="row-clickable" @click="toggleExpand(item)">
              <td class="expand-cell">
                <span class="chevron" :class="{ open: expandedId === item.id }">›</span>
              </td>

              <td>
                <code class="code-text">{{ item.code }}</code>
              </td>

              <td class="col-title">
                <div class="title-text">{{ item.name }}</div>
              </td>

              <td>
                <div v-if="item.description" class="desc-text">{{ item.description }}</div>
                <span v-else>-</span>
              </td>

              <td>
                <span class="cat-badge">{{ item.subjectCount }}</span>
              </td>

              <td>
                <span
                  class="badge"
                  :class="item.status === 'ACTIVE' ? 'badge--pub' : 'badge--draft'"
                >
                  {{ item.status === 'ACTIVE' ? 'Hoạt động' : 'Tắt' }}
                </span>
              </td>

              <td v-if="!readOnly" class="col-actions" @click.stop>
                <button class="act-btn" title="Sửa" @click="openEdit(item)">✏️</button>

                <button
                  class="act-btn act-btn--del"
                  title="Xóa"
                  :disabled="item.subjectCount > 0"
                  @click="deleteTarget = item"
                >
                  🗑️
                </button>
              </td>
            </tr>

            <!-- Dòng mở rộng: môn học thuộc nhóm -->
            <tr v-if="expandedId === item.id" class="row-expanded">
              <td :colspan="readOnly ? 6 : 7">
                <div class="subj-panel">
                  <div class="subj-panel__head">
                    <h4>Môn học trong nhóm "{{ item.name }}"</h4>
                    <button v-if="!readOnly" class="btn btn--sm" @click="openCreateSubject(item)">
                      + Thêm môn học
                    </button>
                  </div>

                  <p v-if="subjectsLoading" class="empty empty--inline">Đang tải môn học...</p>

                  <table v-else-if="subjectsByCategory.length" class="subj-table">
                    <thead>
                      <tr>
                        <th>Mã</th>
                        <th>Tên môn</th>
                        <th>Mô tả</th>
                        <th>Trạng thái</th>
                        <th v-if="!readOnly" width="90">Thao tác</th>
                      </tr>
                    </thead>

                    <tbody>
                      <tr v-for="s in subjectsByCategory" :key="s.id">
                        <td>
                          <code class="code-text">{{ s.code }}</code>
                        </td>

                        <td class="title-text">{{ s.name }}</td>

                        <td>
                          <span v-if="s.description" class="desc-text">{{ s.description }}</span>
                          <span v-else>-</span>
                        </td>

                        <td>
                          <span
                            class="badge"
                            :class="s.status === 'ACTIVE' ? 'badge--pub' : 'badge--draft'"
                          >
                            {{ s.status === 'ACTIVE' ? 'Hoạt động' : 'Tắt' }}
                          </span>
                        </td>

                        <td v-if="!readOnly" class="col-actions">
                          <button class="act-btn" title="Sửa" @click="openEditSubject(s)">
                            ✏️
                          </button>

                          <button
                            class="act-btn act-btn--del"
                            title="Xóa"
                            :disabled="s.lessonCount > 0"
                            @click="deleteSubjectTarget = s"
                          >
                            🗑️
                          </button>
                        </td>
                      </tr>
                    </tbody>
                  </table>

                  <p v-else class="empty empty--inline">
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

    <!-- ================= PAGINATION ================= -->

    <div v-if="totalPages > 1" class="pagination">
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

      <button class="pg-btn" :disabled="page === totalPages - 1" @click="goPage(page + 1)">
        ›
      </button>

      <button class="pg-btn" :disabled="page === totalPages - 1" @click="goPage(totalPages - 1)">
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

    <!-- ================= MODAL: tạo/sửa nhóm môn ================= -->
    <div v-if="modal.open" class="overlay" @click.self="modal.open = false">
      <div class="modal">
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

        <p v-if="modal.error" class="msg msg--error">{{ modal.error }}</p>

        <div class="modal__actions">
          <button class="btn btn--ghost" @click="modal.open = false">Hủy</button>
          <button class="btn" :disabled="modal.saving" @click="saveModal">
            {{ modal.saving ? 'Đang lưu...' : 'Lưu' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ================= MODAL: xác nhận xóa nhóm môn ================= -->
    <div v-if="deleteTarget" class="overlay" @click.self="deleteTarget = null">
      <div class="modal">
        <h3>Xác nhận xóa</h3>

        <p>
          Bạn có chắc muốn xóa nhóm môn <strong>{{ deleteTarget.name }}</strong
          >?
        </p>

        <div class="modal__actions">
          <button class="btn btn--ghost" @click="deleteTarget = null">Hủy</button>
          <button class="btn btn--danger" @click="confirmDelete">Xóa</button>
        </div>
      </div>
    </div>

    <!-- ================= MODAL: tạo/sửa môn học ================= -->
    <div v-if="subjectModal.open" class="overlay" @click.self="subjectModal.open = false">
      <div class="modal">
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

        <p v-if="subjectModal.error" class="msg msg--error">{{ subjectModal.error }}</p>

        <div class="modal__actions">
          <button class="btn btn--ghost" @click="subjectModal.open = false">Hủy</button>
          <button class="btn" :disabled="subjectModal.saving" @click="saveSubjectModal">
            {{ subjectModal.saving ? 'Đang lưu...' : 'Lưu' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ================= MODAL: xác nhận xóa môn học ================= -->
    <div v-if="deleteSubjectTarget" class="overlay" @click.self="deleteSubjectTarget = null">
      <div class="modal">
        <h3>Xác nhận xóa</h3>

        <p>
          Bạn có chắc muốn xóa môn học <strong>{{ deleteSubjectTarget.name }}</strong
          >?
        </p>

        <div class="modal__actions">
          <button class="btn btn--ghost" @click="deleteSubjectTarget = null">Hủy</button>
          <button class="btn btn--danger" @click="confirmDeleteSubject">Xóa</button>
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

.field input {
  height: 40px;
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  padding: 0 12px;
  font-size: 14px;
}

.field input:focus {
  outline: none;
  border-color: #f97316;
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

.btn--sm {
  padding: 6px 12px;
  font-size: 13px;
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

.empty--inline {
  padding: 10px 0;
  text-align: left;
}

/* ================= Expand chevron ================= */

.expand-cell {
  width: 28px;
}

.chevron {
  display: inline-block;
  color: var(--c-text-muted);
  font-size: 16px;
  transition: transform 0.15s ease;
}

.chevron.open {
  transform: rotate(90deg);
  color: #f97316;
}

.row-clickable {
  cursor: pointer;
}

.row-expanded:hover td {
  background: var(--c-surface-2);
}

.row-expanded td {
  background: var(--c-surface-2);
  padding: 0;
  border-top: 1px solid var(--c-border);
}

/* ================= Group / Subject ================= */

.title-text {
  font-weight: 600;
}

.desc-text {
  margin-top: 4px;

  color: var(--c-text-muted);

  font-size: 12px;

  overflow: hidden;

  text-overflow: ellipsis;

  white-space: nowrap;

  max-width: 260px;
}

.code-text {
  background: var(--c-surface-2);
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-family: monospace;
}

/* ================= Category (số môn) ================= */

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

/* Nền rgba mờ hòa được cả theme sáng/tối; chữ chỉnh riêng cho nền tối bên dưới */
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

  padding: 6px;

  border-radius: 6px;

  font-size: 16px;

  transition: 0.2s;
}

.act-btn:hover {
  background: var(--c-surface-2);
}

.act-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.act-btn--del:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.12);
}

/* ================= Subject panel (dòng mở rộng) ================= */

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
  font-size: 14px;
  font-weight: 700;
  color: var(--c-text);
}

.subj-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 10px;
  overflow: hidden;
}

.subj-table th {
  background: var(--c-surface-2);
  padding: 10px 12px;
  text-align: left;
  font-weight: 700;
  color: var(--c-text);
  border-bottom: 1px solid var(--c-border);
}

.subj-table td {
  padding: 10px 12px;
  border-top: 1px solid var(--c-border);
  vertical-align: middle;
}

.subj-table tbody tr:hover {
  background: var(--c-surface-2);
}

/* ================= Pagination ================= */

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

.form-group textarea {
  height: auto;
  padding: 8px 12px;
  resize: vertical;
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

.total {
  margin-bottom: 14px;
  color: var(--c-text-muted);
}

/* ================= Responsive ================= */

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

  .subj-panel {
    padding: 14px;
  }
}
</style>
