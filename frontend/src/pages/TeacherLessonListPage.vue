<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { lessonApi } from '@/api/lessons'

const router = useRouter()

const loading = ref(false)
const error = ref('')

const lessons = ref([])

const page = ref(0)
const totalPages = ref(0)
const totalItems = ref(0)

const PAGE_SIZE = 10

const pageInput = ref('')

const filter = reactive({
  keyword: '',
})

const STATUS_MAP = {
  PUBLISHED: {
    label: 'Đã đăng',
    cls: 'badge--pub',
  },
}

const visiblePages = computed(() => {
  const total = totalPages.value
  const current = page.value + 1

  let start = Math.max(1, current - 2)
  let end = Math.min(total, current + 2)

  if (end - start < 4) {
    if (start === 1) {
      end = Math.min(total, 5)
    } else if (end === total) {
      start = Math.max(1, total - 4)
    }
  }

  const pages = []

  for (let i = start; i <= end; i++) {
    pages.push(i)
  }

  return pages
})

async function load() {
  loading.value = true
  error.value = ''

  try {
    const { data } = await lessonApi.list({
      page: page.value,
      size: PAGE_SIZE,
      keyword: filter.keyword,
      status: 'PUBLISHED',
    })

    lessons.value = data.content
    totalPages.value = data.totalPages
    totalItems.value = data.totalElements
  } catch (e) {
    console.error(e)
    error.value = 'Không tải được danh sách bài giảng.'
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 0
  load()
}

function clearSearch() {
  filter.keyword = ''
  page.value = 0
  load()
}

function goPage(p) {
  if (p < 0) return
  if (p >= totalPages.value) return

  page.value = p
  load()
}

function jumpPage() {
  const p = Number(pageInput.value)

  if (isNaN(p)) return
  if (p < 1) return
  if (p > totalPages.value) return

  goPage(p - 1)
}

function viewLesson(id) {
  router.push({
    name: 'teacher-lesson-view',
    params: {
      id,
    },
  })
}

onMounted(() => {
  load()
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

    <!-- Filter -->
    <div class="filter-bar">
      <label class="field field--wide">
        <span>Tìm kiếm</span>

        <input
          v-model="filter.keyword"
          type="text"
          placeholder="Nhập tiêu đề bài giảng..."
          @keyup.enter="search"
        />
      </label>

      <div class="filter-actions">
        <button class="btn" @click="search">Tìm kiếm</button>

        <button class="btn btn--ghost" @click="clearSearch">Xóa</button>
      </div>
    </div>

    <!-- Message -->

    <p v-if="error" class="msg msg--error">
      {{ error }}
    </p>

    <p v-if="!loading" class="total">
      Có
      <strong>{{ totalItems }}</strong>
      bài giảng
    </p>

    <!-- Table -->

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Tiêu đề</th>
            <th>Danh mục</th>
            <th>Môn học</th>
            <th>Khối lớp</th>
            <th>Trạng thái</th>
            <th width="90">Xem</th>
          </tr>
        </thead>

        <tbody>
          <tr v-if="loading">
            <td colspan="6" class="empty">Đang tải...</td>
          </tr>

          <tr v-else-if="lessons.length === 0">
            <td colspan="6" class="empty">Không có bài giảng.</td>
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
                Xem
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Pagination -->

    <div v-if="totalPages > 1" class="pagination">
      <button class="pg-btn" :disabled="page === 0" @click="goPage(0)">Trang đầu</button>

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
        Trang cuối
      </button>

      <input
        v-model="pageInput"
        class="page-input"
        type="number"
        :min="1"
        :max="totalPages"
        placeholder="Trang"
      />

      <button class="pg-btn" @click="jumpPage">Đi</button>
    </div>
  </div>
</template>
<style scoped>
.page {
  max-width: 1200px;
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
  font-size: 24px;
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
  gap: 14px;
  padding: 18px;
  margin-bottom: 18px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 12px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field--wide {
  flex: 1;
}

.field span {
  font-size: 13px;
  color: var(--c-text);
  font-weight: 600;
}

.field input {
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
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

.btn {
  padding: 10px 18px;
  border: none;
  border-radius: 8px;
  background: #f97316;
  color: white;
  cursor: pointer;
  font-weight: 600;
}

.btn--ghost {
  background: var(--c-surface-2);
  color: var(--c-text);
}

.table-wrap {
  overflow-x: auto;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 12px;
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
  padding: 30px;
}

.title-text {
  font-weight: 600;
}

.desc-text {
  margin-top: 5px;
  color: var(--c-text-muted);
  font-size: 12px;
}

.cat-badge {
  display: inline-block;
  background: rgba(37, 99, 235, 0.12);
  color: #2563eb;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
}
:root[data-theme='dark'] .cat-badge {
  color: #93c5fd;
}

.badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.badge--pub {
  background: rgba(34, 197, 94, 0.15);
  color: #15803d;
}
:root[data-theme='dark'] .badge--pub {
  color: #4ade80;
}

.col-actions {
  text-align: center;
}

.act-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  padding: 6px 12px;
  border-radius: 6px;
}

.act-btn:hover {
  background: var(--c-surface-2);
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  margin-top: 22px;
  flex-wrap: wrap;
}

.pg-btn {
  height: 38px;
  min-width: 38px;
  padding: 0 12px;
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  border-radius: 8px;
  cursor: pointer;
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
  opacity: 0.5;
  cursor: not-allowed;
}

.page-input {
  width: 70px;
  height: 38px;
  text-align: center;
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
}

.msg {
  padding: 12px;
  border-radius: 8px;
  margin-bottom: 15px;
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
