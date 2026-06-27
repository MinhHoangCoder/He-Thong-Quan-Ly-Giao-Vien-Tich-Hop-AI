<script setup>
/**
 * Trang danh sách bài giảng — ADMIN / EMPLOYEE.
 * Lọc: Category (Danh mục từ bảng SubjectCategory) | GradeLevel | Status | Từ khóa.
 * Danh mục lấy từ endpoint /subject-categories/active (chỉ các nhóm ACTIVE).
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { lessonApi } from '@/api/lessons'
import { subjectCategoryApi } from '@/api/subjectCategories'

const router = useRouter()

// Danh sách nhóm môn từ bảng SubjectCategory: [{ id, code, name, ... }]
const categories = ref([])
const gradeLevels = ref([])

const STATUS_MAP = {
  DRAFT: { label: 'Bản nháp', cls: 'badge--draft' },
  PUBLISHED: { label: 'Đã đăng', cls: 'badge--pub' },
  ARCHIVED: { label: 'Lưu kho', cls: 'badge--arch' },
}

const DIFFICULTY_MAP = {
  BASIC: 'Cơ bản',
  INTERMEDIATE: 'Trung cấp',
  ADVANCED: 'Nâng cao',
}

const filter = reactive({
  category: '',
  gradeLevel: '',
  status: '',
  keyword: '',
})

const page = ref(0)
const PAGE_SIZE = 10

const lessons = ref([])
const totalPages = ref(0)
const totalItems = ref(0)
const loading = ref(false)
const error = ref('')
const deleteId = ref(null)

async function loadMeta() {
  try {
    const [cats, grade] = await Promise.all([
      subjectCategoryApi.listActive(),
      lessonApi.gradeLevels(),
    ])
    categories.value = cats.data
    gradeLevels.value = grade.data
  } catch {
    /* dropdown lỗi không block trang */
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = {
      page: page.value,
      size: PAGE_SIZE,
      ...(filter.category && { category: filter.category }),
      ...(filter.gradeLevel && { gradeLevel: filter.gradeLevel }),
      ...(filter.status && { status: filter.status }),
      ...(filter.keyword && { keyword: filter.keyword }),
    }
    const { data } = await lessonApi.list(params)
    lessons.value = data.content
    totalPages.value = data.totalPages
    totalItems.value = data.totalElements
  } catch {
    error.value = 'Không tải được danh sách bài giảng.'
  } finally {
    loading.value = false
  }
}

function applyFilter() {
  page.value = 0
  load()
}

function clearFilter() {
  Object.assign(filter, { category: '', gradeLevel: '', status: '', keyword: '' })
  page.value = 0
  load()
}

function goPage(p) {
  if (p < 0 || p >= totalPages.value) return
  page.value = p
  load()
}

function confirmDelete(id) {
  deleteId.value = id
}

async function doDelete() {
  if (!deleteId.value) return
  try {
    await lessonApi.remove(deleteId.value)
    deleteId.value = null
    load()
  } catch {
    error.value = 'Xóa thất bại, thử lại sau.'
    deleteId.value = null
  }
}

onMounted(() => {
  loadMeta()
  load()
})
</script>

<template>
  <div class="page">
    <div class="page__head">
      <div>
        <h1 class="page__title">Kho bài giảng</h1>
        <p class="page__sub">Quản lý bài giảng theo danh mục và khối lớp.</p>
      </div>
      <button class="btn" @click="router.push({ name: 'lesson-new' })">+ Thêm bài giảng</button>
    </div>

    <!-- Bộ lọc -->
    <div class="filter-bar">
      <!-- Dropdown Danh mục (từ bảng SubjectCategory) -->
      <label class="field">
        <span>Danh mục</span>
        <select v-model="filter.category" @change="applyFilter">
          <option value="">Tất cả danh mục</option>
          <option v-for="cat in categories" :key="cat.id" :value="cat.name">{{ cat.name }}</option>
        </select>
      </label>

      <label class="field">
        <span>Khối lớp</span>
        <select v-model="filter.gradeLevel" @change="applyFilter">
          <option value="">Tất cả khối</option>
          <option v-for="g in gradeLevels" :key="g" :value="g">{{ g }}</option>
        </select>
      </label>

      <label class="field">
        <span>Trạng thái</span>
        <select v-model="filter.status" @change="applyFilter">
          <option value="">Tất cả</option>
          <option value="DRAFT">Bản nháp</option>
          <option value="PUBLISHED">Đã đăng</option>
          <option value="ARCHIVED">Lưu kho</option>
        </select>
      </label>

      <label class="field field--wide">
        <span>Tìm tiêu đề</span>
        <input
          v-model="filter.keyword"
          type="text"
          placeholder="Nhập từ khóa..."
          @keyup.enter="applyFilter"
        />
      </label>

      <div class="filter-actions">
        <button class="btn" @click="applyFilter">Lọc</button>
        <button class="btn btn--ghost" @click="clearFilter">Xóa lọc</button>
      </div>
    </div>

    <p v-if="error" class="msg msg--error">{{ error }}</p>
    <p v-if="!loading" class="total">
      Tìm thấy <strong>{{ totalItems }}</strong> bài giảng
    </p>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Tiêu đề</th>
            <th>Danh mục</th>
            <th>Môn học</th>
            <th>Khối</th>
            <th>Thời lượng</th>
            <th>Độ khó</th>
            <th>Trạng thái</th>
            <th>Thao tác</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="8" class="empty">Đang tải…</td>
          </tr>
          <tr v-else-if="lessons.length === 0">
            <td colspan="8" class="empty">Không có bài giảng nào.</td>
          </tr>
          <tr v-for="l in lessons" :key="l.id">
            <td class="col-title">
              <span class="title-text">{{ l.title }}</span>
              <span v-if="l.description" class="desc-text">{{ l.description }}</span>
            </td>
            <td>
              <span class="cat-badge">{{ l.category || '—' }}</span>
            </td>
            <td>{{ l.subjectName || '—' }}</td>
            <td>{{ l.gradeLevel || '—' }}</td>
            <td>{{ l.duration ? l.duration + ' phút' : '—' }}</td>
            <td>{{ DIFFICULTY_MAP[l.difficultyLevel] || '—' }}</td>
            <td>
              <span class="badge" :class="STATUS_MAP[l.status]?.cls">
                {{ STATUS_MAP[l.status]?.label || l.status }}
              </span>
            </td>
            <td class="col-actions">
              <button
                class="act-btn act-btn--edit"
                title="Sửa"
                @click="router.push({ name: 'lesson-edit', params: { id: l.id } })"
              >
                ✏️
              </button>
              <button class="act-btn act-btn--del" title="Xóa" @click="confirmDelete(l.id)">
                🗑️
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="totalPages > 1" class="pagination">
      <button class="pg-btn" :disabled="page === 0" @click="goPage(page - 1)">‹ Trước</button>
      <button
        v-for="p in totalPages"
        :key="p"
        class="pg-btn"
        :class="{ 'pg-btn--active': page === p - 1 }"
        @click="goPage(p - 1)"
      >
        {{ p }}
      </button>
      <button class="pg-btn" :disabled="page >= totalPages - 1" @click="goPage(page + 1)">
        Sau ›
      </button>
    </div>

    <div v-if="deleteId" class="overlay" @click.self="deleteId = null">
      <div class="modal">
        <h3>Xác nhận xóa</h3>
        <p>Bài giảng sẽ bị xóa mềm (ẩn khỏi danh sách). Bạn chắc chưa?</p>
        <div class="modal__actions">
          <button class="btn btn--ghost" @click="deleteId = null">Hủy</button>
          <button class="btn btn--danger" @click="doDelete">Xóa</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 1200px;
}
.page__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 18px;
  gap: 12px;
}
.page__title {
  margin: 0 0 4px;
  font-size: 22px;
  color: var(--a-text, #0f172a);
}
.page__sub {
  margin: 0;
  color: var(--a-text-muted, #64748b);
  font-size: 14px;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
  background: #fff;
  border: 1px solid var(--a-border, #e2e8f0);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 14px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 5px;
  font-size: 13px;
  color: #475569;
  min-width: 150px;
}
.field--wide {
  min-width: 220px;
  flex: 1;
}
.field select,
.field input {
  padding: 8px 10px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  background: #fff;
}
.field select:focus,
.field input:focus {
  border-color: var(--c-primary, #f97316);
}
.filter-actions {
  display: flex;
  gap: 8px;
  align-self: flex-end;
}

.total {
  font-size: 13px;
  color: #64748b;
  margin: 0 0 10px;
}

.table-wrap {
  overflow-x: auto;
  background: #fff;
  border: 1px solid var(--a-border, #e2e8f0);
  border-radius: 12px;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th {
  background: #f8fafc;
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  text-align: left;
  border-bottom: 1px solid #e2e8f0;
  white-space: nowrap;
}
td {
  padding: 10px 12px;
  font-size: 14px;
  color: #1e293b;
  border-bottom: 1px solid #f1f5f9;
  vertical-align: top;
}
tr:last-child td {
  border-bottom: none;
}
tr:hover td {
  background: #fafafa;
}
.empty {
  text-align: center;
  color: #94a3b8;
  padding: 32px !important;
}

.col-title {
  min-width: 200px;
  max-width: 260px;
}
.title-text {
  display: block;
  font-weight: 500;
}
.desc-text {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 240px;
}

.cat-badge {
  display: inline-block;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  border-radius: 6px;
  padding: 3px 8px;
  font-weight: 500;
}

.badge {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}
.badge--draft {
  background: #f1f5f9;
  color: #64748b;
}
.badge--pub {
  background: #dcfce7;
  color: #16a34a;
}
.badge--arch {
  background: #fef9c3;
  color: #a16207;
}

.col-actions {
  white-space: nowrap;
}
.act-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 16px;
  padding: 4px;
  border-radius: 6px;
  transition: background 0.15s;
}
.act-btn:hover {
  background: #f1f5f9;
}

.pagination {
  display: flex;
  gap: 6px;
  margin-top: 16px;
  flex-wrap: wrap;
}
.pg-btn {
  padding: 6px 11px;
  border: 1px solid #e2e8f0;
  border-radius: 7px;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}
.pg-btn:hover:not(:disabled) {
  border-color: var(--c-primary, #f97316);
  color: var(--c-primary, #f97316);
}
.pg-btn--active {
  background: var(--c-primary, #f97316);
  color: #fff;
  border-color: var(--c-primary, #f97316);
}
.pg-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.btn {
  padding: 9px 18px;
  border: none;
  border-radius: 10px;
  background: var(--c-primary, #f97316);
  color: #fff;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
  transition: filter 0.15s;
  white-space: nowrap;
}
.btn:hover {
  filter: brightness(1.05);
}
.btn--ghost {
  background: #f1f5f9;
  color: #475569;
}
.btn--ghost:hover {
  background: #e2e8f0;
  filter: none;
}
.btn--danger {
  background: #ef4444;
}

.msg {
  font-size: 13px;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 12px;
}
.msg--error {
  background: #fef2f2;
  color: #b91c1c;
}

.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
}
.modal {
  background: #fff;
  border-radius: 14px;
  padding: 28px;
  max-width: 400px;
  width: 90%;
}
.modal h3 {
  margin: 0 0 10px;
  font-size: 18px;
}
.modal p {
  color: #475569;
  font-size: 14px;
  margin: 0 0 20px;
}
.modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
