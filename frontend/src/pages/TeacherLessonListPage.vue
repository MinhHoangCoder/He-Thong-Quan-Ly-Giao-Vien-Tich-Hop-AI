<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { lessonApi } from '@/api/lessons'
import { subjectCategoryApi } from '@/api/subjectCategories'

const route = useRoute()
const router = useRouter()

/* =========================
   State
========================= */

const PAGE_SIZE = 10

const loading = ref(false)
const error = ref('')

const lessons = ref([])
const categories = ref([])
const gradeLevels = ref([])

const page = ref(0)
const totalPages = ref(0)
const totalItems = ref(0)

const pageInput = ref('')

/* =========================
   Filter
========================= */

// Teacher chỉ xem bài giảng đã xuất bản -> không có filter Trạng thái
// (khác trang Admin, nơi có đủ 3 trạng thái Bản nháp/Đã đăng/Lưu kho).
const filter = reactive({
  keyword: '',
})

/* =========================
   Constant
========================= */

const STATUS_MAP = {
  PUBLISHED: {
    label: 'Đã đăng',
    cls: 'badge--pub',
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

async function load() {
  loading.value = true
  error.value = ''

  try {
    const params = {
      page: page.value,
      size: PAGE_SIZE,
      status: 'PUBLISHED',
    }

    if (filter.category) params.category = filter.category
    if (filter.gradeLevel) params.gradeLevel = filter.gradeLevel
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
  filter.keyword = ''

  page.value = 0

  loadLessons()
}

function viewLesson(id) {
  router.push({
    name: 'teacher-lesson-view',
    params: {
      id,
    },
  })
}

/* =========================
   Mounted
========================= */

onMounted(async () => {
  // Mở từ "Lịch dạy của tôi" (bấm 1 buổi) → prefill ô tìm kiếm theo tên môn để lọc nhanh.
  if (route.query.keyword) filter.keyword = String(route.query.keyword)
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
        <p class="page__sub">Danh sách các bài giảng đã được xuất bản</p>
      </div>
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
        <span>Khối</span>

        <select v-model="filter.gradeLevel" @change="applyFilter">
          <option value="">Tất cả</option>

          <option v-for="g in gradeLevels" :key="g" :value="g">
            {{ g }}
          </option>
        </select>
      </label>

      <label class="field field--wide">
        <span>Tìm kiếm</span>

        <input
          v-model="filter.keyword"
          placeholder="Nhập tiêu đề bài giảng..."
          @keyup.enter="applyFilter"
        />
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

    <!-- ================= TABLE ================= -->

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Tiêu đề</th>

            <th>Danh mục</th>

            <th>Môn học</th>

            <th>Khối</th>

            <th>Trạng thái</th>

            <th width="90">Xem</th>
          </tr>
        </thead>

        <tbody>
          <tr v-if="loading">
            <td colspan="6" class="empty">Đang tải...</td>
          </tr>

          <tr v-else-if="lessons.length === 0">
            <td colspan="6" class="empty">Không có dữ liệu</td>
          </tr>

          <tr v-for="lesson in lessons" :key="lesson.id">
            <td class="col-title">
              <div class="title-text">
                {{ lesson.title }}
              </div>

              <div v-if="lesson.description" class="desc-text">
                {{ lesson.description }}
              </div>
            </td>

            <td>
              <span class="cat-badge">
                {{ lesson.category || '-' }}
              </span>
            </td>

            <td>
              {{ lesson.subjectName || '-' }}
            </td>

            <td>
              {{ lesson.gradeLevel || '-' }}
            </td>

            <td>
              <span class="badge" :class="STATUS_MAP[lesson.status]?.cls">
                {{ STATUS_MAP[lesson.status]?.label }}
              </span>
            </td>

            <td class="col-actions">
              <button class="act-btn" title="Xem bài giảng" @click="viewLesson(lesson.id)">
                👁
              </button>
            </td>
          </tr>
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
  text-align: center;
}

.act-btn {
  border: none;
  background: transparent;
  cursor: pointer;

  padding: 6px;

  border-radius: 6px;

  font-size: 17px;

  transition: 0.2s;
}

.act-btn:hover {
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
