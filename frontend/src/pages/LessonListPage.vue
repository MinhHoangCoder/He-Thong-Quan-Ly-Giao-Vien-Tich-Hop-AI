<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { lessonApi } from '@/api/lessons'
import { subjectCategoryApi } from '@/api/subjectCategories'

const router = useRouter()
const route = useRoute()

// FIX (2026-07-10): trang này được DÙNG CHUNG cho 2 khu vực (2 bộ route khác
// tên/khác meta.roles):
//   - Khu ADMIN:  /admin/lessons        (name: 'admin-lesson-list', roles ADMIN/EMPLOYEE)
//   - Khu STAFF:  /staff/lessons        (name: 'lesson-list', roles ACCOUNTANT/HR/ACADEMIC/SALES)
// Trước đây nút "Sửa"/"+ Thêm bài giảng" LUÔN push cứng sang tên route của khu
// STAFF ('lesson-edit' / 'lesson-new'). Khi ADMIN đang ở /admin/lessons bấm
// "Sửa", router điều hướng sang /staff/lessons/:id/edit — route này không cho
// phép role ADMIN/EMPLOYEE vào -> route guard tự động đá về dashboard.
// -> Phải chọn ĐÚNG tên route theo khu vực đang đứng, dựa vào tiền tố route
// hiện tại ('admin-lesson-list' vs 'lesson-list').
const isAdminArea = computed(() => route.name === 'admin-lesson-list')
const editRouteName = computed(() => (isAdminArea.value ? 'admin-lesson-edit' : 'lesson-edit'))
const newRouteName = computed(() => (isAdminArea.value ? 'admin-lesson-new' : 'lesson-new'))

/* =========================
   State
========================= */

// Kho bài giảng hiển thị dạng nhóm Khối > Danh mục > Bài giảng nên không dùng
// phân trang nhỏ (sẽ làm vỡ nhóm giữa 2 trang) — lấy 1 lượt với size đủ lớn.
const PAGE_SIZE = 1000

const loading = ref(false)
const error = ref('')

const lessons = ref([])
const categories = ref([])
const gradeLevels = ref([])

const page = ref(0)
const totalPages = ref(0)
const totalItems = ref(0)

const pageInput = ref('')

const deleteId = ref(null)

/* =========================
   Filter
========================= */

const filter = reactive({
  category: '',
  gradeLevel: '',
  status: '',
  keyword: '',
})

/* =========================
   Constant
========================= */

const STATUS_MAP = {
  DRAFT: {
    label: 'Bản nháp',
    cls: 'badge--draft',
  },
  PUBLISHED: {
    label: 'Đã đăng',
    cls: 'badge--pub',
  },
  ARCHIVED: {
    label: 'Lưu kho',
    cls: 'badge--arch',
  },
}

/* =========================
   Pagination
========================= */

const visiblePages = computed(() => {
  const total = totalPages.value
  const current = page.value + 1

  let start = Math.max(1, current - 2)
  let end = Math.min(total, current + 2)

  if (end - start < 4) {
    if (start === 1) {
      end = Math.min(5, total)
    } else if (end === total) {
      start = Math.max(1, total - 4)
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
  loadLessons()
}

function jumpPage() {
  const p = Number(pageInput.value)

  if (isNaN(p)) return
  if (p < 1) return
  if (p > totalPages.value) return

  goPage(p - 1)
}

/* =========================
   API
========================= */

async function loadMeta() {
  try {
    const [categoryRes, gradeRes] = await Promise.all([
      subjectCategoryApi.listActive(),
      lessonApi.gradeLevels(),
    ])

    categories.value = categoryRes.data
    gradeLevels.value = gradeRes.data
  } catch (e) {
    console.error(e)
  }
}

async function loadLessons() {
  loading.value = true
  error.value = ''

  try {
    const params = {
      page: page.value,
      size: PAGE_SIZE,
    }

    if (filter.category) params.category = filter.category
    if (filter.gradeLevel) params.gradeLevel = filter.gradeLevel
    if (filter.status) params.status = filter.status
    if (filter.keyword) params.keyword = filter.keyword

    const { data } = await lessonApi.list(params)

    lessons.value = data.content || []
    totalPages.value = data.totalPages || 0
    totalItems.value = data.totalElements || 0
  } catch (err) {
    console.error(err)
    error.value = 'Không tải được danh sách bài giảng.'
  } finally {
    loading.value = false
  }
}

/* =========================
   Actions
========================= */

function applyFilter() {
  page.value = 0
  loadLessons()
}

function clearFilter() {
  filter.category = ''
  filter.gradeLevel = ''
  filter.status = ''
  filter.keyword = ''

  page.value = 0

  loadLessons()
}

function confirmDelete(id) {
  deleteId.value = id
}

/* =========================
   Nhóm: Khối > Danh mục > Bài giảng
========================= */

/**
 * Kho bài giảng hiển thị theo cấu trúc Khối lớp -> Danh mục -> Bài giảng thay
 * vì bảng phẳng, để dễ định vị bài giảng theo đúng khối/nhóm môn.
 * Thứ tự Khối theo `gradeLevels` (Lớp 1..9), Danh mục theo `categories`
 * (alphabet, load từ SubjectCategory ACTIVE); phần không xác định được xếp
 * cuối trong mục "Chưa phân loại".
 */
const groupedLessons = computed(() => {
  const gradeOrder = gradeLevels.value
  const categoryOrder = categories.value.map((c) => c.name)

  const gradeMap = new Map()
  for (const lesson of lessons.value) {
    const gradeKey = lesson.gradeLevel || 'Chưa phân loại'
    if (!gradeMap.has(gradeKey)) gradeMap.set(gradeKey, new Map())
    const catMap = gradeMap.get(gradeKey)
    const catKey = lesson.category || 'Chưa phân loại'
    if (!catMap.has(catKey)) catMap.set(catKey, [])
    catMap.get(catKey).push(lesson)
  }

  const byKnownOrder = (order) => (a, b) => {
    const ia = order.indexOf(a)
    const ib = order.indexOf(b)
    if (ia === -1 && ib === -1) return a.localeCompare(b)
    if (ia === -1) return 1
    if (ib === -1) return -1
    return ia - ib
  }

  return [...gradeMap.keys()].sort(byKnownOrder(gradeOrder)).map((gradeKey) => {
    const catMap = gradeMap.get(gradeKey)
    const catKeys = [...catMap.keys()].sort(byKnownOrder(categoryOrder))
    return {
      grade: gradeKey,
      total: catKeys.reduce((sum, k) => sum + catMap.get(k).length, 0),
      categories: catKeys.map((catKey) => ({
        category: catKey,
        items: catMap.get(catKey),
      })),
    }
  })
})

async function doDelete() {
  if (!deleteId.value) return

  try {
    await lessonApi.remove(deleteId.value)

    deleteId.value = null

    loadLessons()
  } catch (e) {
    console.error(e)

    error.value = 'Xóa thất bại.'

    deleteId.value = null
  }
}

/* =========================
   Mounted
========================= */

onMounted(async () => {
  await loadMeta()
  await loadLessons()
})
</script>

<template>
  <div class="page">
    <!-- Header -->
    <div class="page__head">
      <div>
        <h1 class="page__title">Kho bài giảng</h1>
        <p class="page__sub">Quản lý bài giảng theo danh mục và khối lớp</p>
      </div>

      <button class="btn" @click="router.push({ name: newRouteName })">+ Thêm bài giảng</button>
    </div>

    <!-- ================= FILTER ================= -->

    <div class="filter-bar">
      <label class="field">
        <span>Danh mục</span>

        <select v-model="filter.category" @change="applyFilter">
          <option value="">Tất cả</option>

          <option v-for="item in categories" :key="item.id" :value="item.name">
            {{ item.name }}
          </option>
        </select>
      </label>

      <label class="field">
        <span>Khối lớp</span>

        <select v-model="filter.gradeLevel" @change="applyFilter">
          <option value="">Tất cả</option>

          <option v-for="g in gradeLevels" :key="g" :value="g">
            {{ g }}
          </option>
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
        <span>Tìm kiếm</span>

        <input v-model="filter.keyword" placeholder="Nhập tiêu đề..." @keyup.enter="applyFilter" />
      </label>

      <div class="filter-actions">
        <button class="btn" @click="applyFilter">Lọc</button>

        <button class="btn btn--ghost" @click="clearFilter">Xóa lọc</button>
      </div>
    </div>

    <!-- ================= INFO ================= -->

    <p v-if="error" class="msg msg--error">
      {{ error }}
    </p>

    <p v-if="!loading" class="total">
      Tổng cộng
      <strong>{{ totalItems }}</strong>
      bài giảng
    </p>

    <!-- ================= KHO BÀI GIẢNG: Khối > Danh mục > Bài giảng ================= -->

    <p v-if="loading" class="empty-state">Đang tải...</p>

    <p v-else-if="lessons.length === 0" class="empty-state">Không có dữ liệu</p>

    <div v-else class="lesson-tree">
      <details v-for="grp in groupedLessons" :key="grp.grade" class="grade-group" open>
        <summary class="grade-group__head">
          <span class="grade-group__title">Khối {{ grp.grade }}</span>
          <span class="grade-group__count">{{ grp.total }} bài</span>
        </summary>

        <details
          v-for="catGrp in grp.categories"
          :key="grp.grade + '-' + catGrp.category"
          class="cat-group"
          open
        >
          <summary class="cat-group__head">
            <span class="cat-badge">{{ catGrp.category }}</span>
            <span class="cat-group__count">{{ catGrp.items.length }} bài</span>
          </summary>

          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Tiêu đề</th>
                  <th>Môn học</th>
                  <th>Trạng thái</th>
                  <th width="120">Thao tác</th>
                </tr>
              </thead>

              <tbody>
                <tr v-for="lesson in catGrp.items" :key="lesson.id">
                  <td class="col-title">
                    <div class="title-text">
                      {{ lesson.title }}
                    </div>

                    <div v-if="lesson.description" class="desc-text">
                      {{ lesson.description }}
                    </div>
                  </td>

                  <td>
                    {{ lesson.subjectName || '-' }}
                  </td>

                  <td>
                    <span class="badge" :class="STATUS_MAP[lesson.status]?.cls">
                      {{ STATUS_MAP[lesson.status]?.label }}
                    </span>
                  </td>

                  <td class="col-actions">
                    <button
                      class="act-btn"
                      title="Sửa"
                      @click="
                        router.push({
                          name: editRouteName,
                          params: { id: lesson.id },
                        })
                      "
                    >
                      Sửa
                    </button>

                    <button
                      class="act-btn act-btn--del"
                      title="Xóa"
                      @click="confirmDelete(lesson.id)"
                    >
                      Xóa
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </details>
      </details>
    </div>

    <!-- ================= PAGINATION ================= -->

    <div v-if="totalPages > 1" class="pagination">
      <button class="pg-btn" :disabled="page === 0" @click="goPage(0)">«</button>

      <button class="pg-btn" :disabled="page === 0" @click="goPage(page - 1)">‹</button>

      <button
        v-for="p in visiblePages"
        :key="p"
        class="pg-btn"
        :class="{
          'pg-btn--active': page === p - 1,
        }"
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

    <!-- ================= DELETE MODAL ================= -->

    <div v-if="deleteId" class="overlay" @click.self="deleteId = null">
      <div class="modal">
        <h3>Xác nhận xóa</h3>

        <p>Bạn có chắc muốn xóa bài giảng này?</p>

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

.field input,
.field select {
  height: 40px;
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  padding: 0 12px;
  font-size: 14px;
}

.field input:focus,
.field select:focus {
  outline: none;
  border-color: #f97316;
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

.btn--ghost {
  background: var(--c-surface-2);
  color: var(--c-text);
}

.btn--danger {
  background: #ef4444;
}

.filter-actions {
  display: flex;
  align-items: flex-end;
  gap: 10px;
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
}

tbody tr:hover {
  background: var(--c-surface-2);
}

.empty {
  text-align: center;
  color: var(--c-text-muted);
  padding: 35px;
}

.empty-state {
  text-align: center;
  color: var(--c-text-muted);
  padding: 35px;
  background: var(--c-surface);
  border-radius: 14px;
  border: 1px solid var(--c-border);
}

/* ================= Kho bài giảng: nhóm Khối > Danh mục ================= */

.lesson-tree {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.grade-group {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 14px;
  padding: 4px 16px 16px;
}

.grade-group__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 4px;
  cursor: pointer;
  list-style: none;
  font-size: 16px;
  font-weight: 700;
  color: var(--c-text);
}

.grade-group__head::-webkit-details-marker {
  display: none;
}

.grade-group__count {
  font-size: 12px;
  font-weight: 600;
  color: var(--c-text-muted);
}

.cat-group {
  margin: 10px 0 0;
  padding-left: 8px;
  border-left: 3px solid var(--c-surface-2);
}

.cat-group__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 4px;
  cursor: pointer;
  list-style: none;
}

.cat-group__head::-webkit-details-marker {
  display: none;
}

.cat-group__count {
  font-size: 12px;
  font-weight: 600;
  color: var(--c-text-muted);
}

.cat-group .table-wrap {
  margin-top: 6px;
}

/* ================= Lesson ================= */

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

/* ================= Category ================= */

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

.badge--arch {
  background: rgba(245, 158, 11, 0.16);
  color: #a16207;
}

:root[data-theme='dark'] .badge--pub {
  color: #4ade80;
}
:root[data-theme='dark'] .badge--arch {
  color: #fbbf24;
}

/* ================= Action ================= */

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
}

.act-btn:hover {
  background: var(--c-surface-2);
}

.act-btn--del:hover {
  background: rgba(239, 68, 68, 0.12);
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
}

.modal {
  width: 420px;

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

/* ================= Message ================= */

.msg {
  padding: 12px;
  margin-bottom: 16px;

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
}
</style>
