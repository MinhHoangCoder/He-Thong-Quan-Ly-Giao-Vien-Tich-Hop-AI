<template>
  <div class="lesson-management">
    <!-- ══════════════════════ HEADER ══════════════════════ -->
    <div class="page-header">
      <div class="header-left">
        <div class="header-icon">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
          >
            <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
            <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
            <line x1="8" y1="7" x2="16" y2="7" />
            <line x1="8" y1="11" x2="13" y2="11" />
          </svg>
        </div>
        <div>
          <h1 class="page-title">Quản lý Bài giảng</h1>
          <p class="page-subtitle">Tạo, chỉnh sửa và điều phối nội dung giảng dạy</p>
        </div>
      </div>
      <!-- Chỉ EMPLOYEE/ADMIN mới thấy nút Tạo bài giảng -->
      <button v-if="isEmployee" class="btn-primary" @click="openCreate">
        <span class="btn-icon">＋</span> Tạo bài giảng
      </button>
      <!-- TEACHER: banner nhắc nhở -->
      <div v-else class="role-badge role-badge--teacher">👁 Chế độ xem — Giáo viên</div>
    </div>

    <!-- STATS BAR — chỉ EMPLOYEE/ADMIN -->
    <div v-if="isEmployee" class="stats-row">
      <div
        class="stat-card"
        v-for="s in statsCards"
        :key="s.label"
        :class="['stat-card--' + s.color, { active: filter.status === s.status }]"
        @click="filterByStatus(s.status)"
      >
        <span class="stat-number">{{ stats[s.status] ?? 0 }}</span>
        <span class="stat-label">{{ s.label }}</span>
      </div>
      <div
        class="stat-card stat-card--total"
        :class="{ active: !filter.status }"
        @click="filterByStatus(null)"
      >
        <span class="stat-number">{{ totalCount }}</span>
        <span class="stat-label">Tổng bài giảng</span>
      </div>
    </div>

    <!-- ══════════════════════ FILTERS ══════════════════════ -->
    <div class="filter-bar">
      <div class="search-wrap">
        <span class="search-icon">🔍</span>
        <input
          v-model="filter.keyword"
          class="search-input"
          placeholder="Tìm theo tiêu đề, mô tả…"
          @input="debouncedSearch"
        />
        <button
          v-if="filter.keyword"
          class="clear-btn"
          @click="
            () => {
              filter.keyword = ''
              fetchLessons()
            }
          "
        >
          ✕
        </button>
      </div>

      <select v-model="filter.subjectId" class="filter-select" @change="fetchLessons">
        <option value="">Tất cả môn</option>
        <option v-for="s in subjects" :key="s.subjectId" :value="s.subjectId">{{ s.name }}</option>
      </select>

      <select v-model="filter.difficultyLevel" class="filter-select" @change="fetchLessons">
        <option value="">Tất cả mức độ</option>
        <option value="BASIC">Cơ bản</option>
        <option value="INTERMEDIATE">Trung cấp</option>
        <option value="ADVANCED">Nâng cao</option>
      </select>

      <select v-model="filter.sortBy" class="filter-select" @change="fetchLessons">
        <option value="createdAt">Mới nhất</option>
        <option value="title">Tên A-Z</option>
        <option value="updatedAt">Cập nhật gần đây</option>
      </select>

      <button class="btn-outline" @click="resetFilter">↺ Đặt lại</button>
    </div>

    <!-- ══════════════════════ TABLE ══════════════════════ -->
    <div class="table-container">
      <!-- Loading skeleton -->
      <div v-if="loading" class="skeleton-wrap">
        <div v-for="i in 5" :key="i" class="skeleton-row">
          <div class="skeleton-cell w20"></div>
          <div class="skeleton-cell w15"></div>
          <div class="skeleton-cell w10"></div>
          <div class="skeleton-cell w10"></div>
          <div class="skeleton-cell w8"></div>
          <div class="skeleton-cell w12"></div>
        </div>
      </div>

      <!-- Empty state -->
      <div v-else-if="lessons.length === 0" class="empty-state">
        <div class="empty-icon">📚</div>
        <p class="empty-title">Chưa có bài giảng nào</p>
        <p class="empty-sub">Nhấn "Tạo bài giảng" để bắt đầu soạn nội dung</p>
        <button class="btn-primary" @click="openCreate">Tạo bài giảng đầu tiên</button>
      </div>

      <!-- Table -->
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>Tiêu đề</th>
            <th>Môn học</th>
            <th>Khối</th>
            <th>Mức độ</th>
            <th>Thời lượng</th>
            <th>File</th>
            <th>Trạng thái</th>
            <th>Ngày tạo</th>
            <th v-if="isEmployee" class="th-action">Thao tác</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="lesson in lessons"
            :key="lesson.lessonId"
            class="data-row"
            @click="openDetail(lesson)"
            style="cursor: pointer"
          >
            <td class="td-title">
              <span class="lesson-title">{{ lesson.title }}</span>
              <span v-if="lesson.teacherName" class="lesson-teacher"
                >GV: {{ lesson.teacherName }}</span
              >
            </td>
            <td>
              <span class="subject-tag">{{ lesson.subjectName || '—' }}</span>
            </td>
            <td>{{ lesson.gradeLevel || '—' }}</td>
            <td>
              <span
                v-if="lesson.difficultyLevel"
                :class="['badge-diff', 'diff--' + lesson.difficultyLevel.toLowerCase()]"
              >
                {{ diffLabel(lesson.difficultyLevel) }}
              </span>
              <span v-else>—</span>
            </td>
            <td>{{ lesson.duration ? lesson.duration + ' phút' : '—' }}</td>
            <td>
              <span class="file-count" v-if="lesson.fileCount"> 📎 {{ lesson.fileCount }} </span>
              <span v-else class="text-muted">0</span>
            </td>
            <td @click.stop>
              <!-- EMPLOYEE/ADMIN: dropdown đổi trạng thái -->
              <select
                v-if="isEmployee"
                class="status-select"
                :class="'status--' + lesson.status.toLowerCase()"
                :value="lesson.status"
                @change="quickChangeStatus(lesson, $event.target.value)"
              >
                <option value="DRAFT">Bản nháp</option>
                <option value="PUBLISHED">Đã xuất bản</option>
                <option value="ARCHIVED">Lưu trữ</option>
              </select>
              <!-- TEACHER: chỉ hiển thị badge, không tương tác -->
              <span
                v-else
                :class="['status-badge', 'status-badge--' + lesson.status.toLowerCase()]"
              >
                {{ statusLabel(lesson.status) }}
              </span>
            </td>
            <td class="td-date">{{ formatDate(lesson.createdAt) }}</td>
            <td v-if="isEmployee" class="td-actions" @click.stop>
              <button class="icon-btn edit" @click="openEdit(lesson)" title="Chỉnh sửa">✏️</button>
              <button class="icon-btn delete" @click="confirmDelete(lesson)" title="Xóa">🗑️</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- ══════════════════════ PAGINATION ══════════════════════ -->
    <div v-if="totalPages > 1" class="pagination">
      <button :disabled="filter.page === 0" class="page-btn" @click="goPage(filter.page - 1)">
        ‹
      </button>
      <button
        v-for="p in pageNumbers"
        :key="p"
        :class="['page-btn', { active: p - 1 === filter.page }]"
        @click="goPage(p - 1)"
      >
        {{ p }}
      </button>
      <button
        :disabled="filter.page === totalPages - 1"
        class="page-btn"
        @click="goPage(filter.page + 1)"
      >
        ›
      </button>
      <span class="page-info"
        >Trang {{ filter.page + 1 }} / {{ totalPages }} · {{ totalCount }} bài giảng</span
      >
    </div>

    <!-- ══════════════════════ MODAL: CREATE / EDIT ══════════════════════ -->
    <Teleport to="body">
      <div v-if="showModal" class="modal-overlay" @mousedown.self="closeModal">
        <div class="modal" :class="{ 'modal--wide': activeTab === 'content' }">
          <!-- Modal header -->
          <div class="modal-header">
            <div class="modal-title-wrap">
              <span class="modal-icon">{{ editMode ? '✏️' : '＋' }}</span>
              <h2 class="modal-title">
                {{ editMode ? 'Chỉnh sửa bài giảng' : 'Tạo bài giảng mới' }}
              </h2>
            </div>
            <button class="modal-close" @click="closeModal">✕</button>
          </div>

          <!-- Tabs -->
          <div class="modal-tabs">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              :class="['tab-btn', { active: activeTab === tab.key }]"
              @click="activeTab = tab.key"
            >
              {{ tab.label }}
              <span v-if="tab.key === 'files' && form.files.length" class="tab-badge">{{
                form.files.length
              }}</span>
            </button>
          </div>

          <!-- Tab: Thông tin cơ bản -->
          <div v-show="activeTab === 'info'" class="modal-body">
            <div class="form-grid">
              <div class="form-group span-full">
                <label class="form-label required">Tiêu đề bài giảng</label>
                <input
                  v-model="form.title"
                  class="form-input"
                  :class="{ error: errors.title }"
                  placeholder="Nhập tiêu đề bài giảng…"
                  maxlength="300"
                />
                <span v-if="errors.title" class="form-error">{{ errors.title }}</span>
              </div>

              <div class="form-group">
                <label class="form-label required">Môn học</label>
                <select
                  v-model="form.subjectId"
                  class="form-input"
                  :class="{ error: errors.subjectId }"
                >
                  <option value="">-- Chọn môn học --</option>
                  <option v-for="s in subjects" :key="s.subjectId" :value="s.subjectId">
                    [{{ s.code }}] {{ s.name }}
                  </option>
                </select>
                <span v-if="errors.subjectId" class="form-error">{{ errors.subjectId }}</span>
              </div>

              <div class="form-group">
                <label class="form-label">Giáo viên phụ trách</label>
                <select v-model="form.teacherId" class="form-input">
                  <option value="">-- Không chỉ định --</option>
                  <option v-for="t in teachers" :key="t.teacherId" :value="t.teacherId">
                    {{ t.fullName }}
                  </option>
                </select>
              </div>

              <div class="form-group">
                <label class="form-label">Khối lớp</label>
                <input
                  v-model="form.gradeLevel"
                  class="form-input"
                  placeholder="Vd: Lớp 6, THPT…"
                  maxlength="50"
                />
              </div>

              <div class="form-group">
                <label class="form-label">Thời lượng (phút)</label>
                <input
                  v-model.number="form.duration"
                  type="number"
                  min="1"
                  class="form-input"
                  placeholder="45"
                />
              </div>

              <div class="form-group">
                <label class="form-label">Mức độ khó</label>
                <select v-model="form.difficultyLevel" class="form-input">
                  <option value="">-- Không xác định --</option>
                  <option value="BASIC">Cơ bản</option>
                  <option value="INTERMEDIATE">Trung cấp</option>
                  <option value="ADVANCED">Nâng cao</option>
                </select>
              </div>

              <div class="form-group">
                <label class="form-label">Trạng thái</label>
                <select v-model="form.status" class="form-input">
                  <option value="DRAFT">Bản nháp</option>
                  <option value="PUBLISHED">Xuất bản ngay</option>
                  <option value="ARCHIVED">Lưu trữ</option>
                </select>
              </div>

              <div class="form-group span-full">
                <label class="form-label">Mô tả tóm tắt</label>
                <textarea
                  v-model="form.description"
                  class="form-textarea"
                  rows="3"
                  placeholder="Mô tả ngắn gọn về bài giảng…"
                  maxlength="2000"
                ></textarea>
                <span class="char-count">{{ (form.description || '').length }}/2000</span>
              </div>
            </div>
          </div>

          <!-- Tab: Nội dung chi tiết -->
          <div v-show="activeTab === 'content'" class="modal-body">
            <div class="form-group">
              <label class="form-label">Nội dung bài giảng</label>
              <p class="form-hint">Hỗ trợ Markdown. Ví dụ: **đậm**, *nghiêng*, ## Tiêu đề</p>
              <textarea
                v-model="form.content"
                class="form-textarea content-editor"
                rows="18"
                placeholder="# Tiêu đề bài giảng&#10;&#10;## Mục tiêu&#10;- Học sinh hiểu...&#10;&#10;## Nội dung chính&#10;..."
              ></textarea>
            </div>
          </div>

          <!-- Tab: File đính kèm -->
          <div v-show="activeTab === 'files'" class="modal-body">
            <div class="file-add-form">
              <h4 class="file-section-title">Thêm file / liên kết</h4>
              <div class="form-grid">
                <div class="form-group">
                  <label class="form-label required">Tên file</label>
                  <input
                    v-model="newFile.fileName"
                    class="form-input"
                    placeholder="Slide bài 1.pptx"
                  />
                </div>
                <div class="form-group">
                  <label class="form-label required">URL / Đường dẫn</label>
                  <input
                    v-model="newFile.fileUrl"
                    class="form-input"
                    placeholder="https://… hoặc /uploads/…"
                  />
                </div>
                <div class="form-group">
                  <label class="form-label">Loại file</label>
                  <select v-model="newFile.fileType" class="form-input">
                    <option value="">-- Chọn loại --</option>
                    <option value="pdf">PDF</option>
                    <option value="pptx">PowerPoint</option>
                    <option value="mp4">Video (MP4)</option>
                    <option value="link">Liên kết web</option>
                    <option value="docx">Word</option>
                    <option value="other">Khác</option>
                  </select>
                </div>
                <div class="form-group">
                  <label class="form-label">Kích thước (KB)</label>
                  <input
                    v-model.number="newFile.fileSizeKb"
                    type="number"
                    class="form-input"
                    placeholder="1024"
                  />
                </div>
              </div>
              <button
                class="btn-outline"
                @click="addFileToForm"
                :disabled="!newFile.fileName || !newFile.fileUrl"
              >
                ＋ Thêm vào danh sách
              </button>
            </div>

            <div v-if="form.files.length > 0" class="file-list">
              <h4 class="file-section-title">Danh sách file đính kèm ({{ form.files.length }})</h4>
              <div v-for="(f, idx) in form.files" :key="idx" class="file-item">
                <div class="file-type-icon">{{ fileIcon(f.fileType) }}</div>
                <div class="file-info">
                  <span class="file-name">{{ f.fileName }}</span>
                  <span class="file-url">{{ f.fileUrl }}</span>
                  <div class="file-meta">
                    <span v-if="f.fileType" class="file-tag">{{ f.fileType }}</span>
                    <span v-if="f.fileSizeKb" class="file-size">{{
                      formatSize(f.fileSizeKb)
                    }}</span>
                  </div>
                </div>
                <button class="icon-btn delete" @click="removeFileFromForm(idx)">✕</button>
              </div>
            </div>
            <div v-else class="empty-files">
              <span>📂</span>
              <p>Chưa có file đính kèm nào</p>
            </div>
          </div>

          <!-- Modal footer -->
          <div class="modal-footer">
            <button class="btn-outline" @click="closeModal">Hủy</button>
            <button class="btn-primary" @click="save" :disabled="saving">
              <span v-if="saving" class="spinner"></span>
              {{ saving ? 'Đang lưu…' : editMode ? 'Cập nhật' : 'Tạo bài giảng' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ══════════════════════ MODAL: DETAIL VIEW ══════════════════════ -->
    <Teleport to="body">
      <div
        v-if="showDetail && currentLesson"
        class="modal-overlay"
        @mousedown.self="showDetail = false"
      >
        <div class="modal modal--wide">
          <div class="modal-header">
            <div class="modal-title-wrap">
              <span :class="['status-dot', 'dot--' + currentLesson.status.toLowerCase()]"></span>
              <h2 class="modal-title">{{ currentLesson.title }}</h2>
            </div>
            <div class="detail-actions">
              <button v-if="isEmployee" class="btn-outline btn-sm" @click="openEditFromDetail">
                ✏️ Chỉnh sửa
              </button>
              <button class="modal-close" @click="showDetail = false">✕</button>
            </div>
          </div>
          <!-- Banner chế độ xem cho TEACHER -->
          <div v-if="isTeacher" class="teacher-view-banner">
            📖 Bạn đang xem bài giảng được phân công giảng dạy. Liên hệ nhân viên trung tâm nếu cần
            hỗ trợ.
          </div>
          <div class="modal-body detail-body">
            <div class="detail-grid">
              <div class="detail-row">
                <span class="detail-key">Môn học</span>
                <span class="detail-val"
                  ><span class="subject-tag">{{ currentLesson.subjectName }}</span></span
                >
              </div>
              <div class="detail-row">
                <span class="detail-key">Giáo viên</span>
                <span class="detail-val">{{ currentLesson.teacherName || '—' }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-key">Khối lớp</span>
                <span class="detail-val">{{ currentLesson.gradeLevel || '—' }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-key">Thời lượng</span>
                <span class="detail-val">{{
                  currentLesson.duration ? currentLesson.duration + ' phút' : '—'
                }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-key">Mức độ</span>
                <span class="detail-val">
                  <span
                    v-if="currentLesson.difficultyLevel"
                    :class="['badge-diff', 'diff--' + currentLesson.difficultyLevel.toLowerCase()]"
                  >
                    {{ diffLabel(currentLesson.difficultyLevel) }}
                  </span>
                  <span v-else>—</span>
                </span>
              </div>
              <div class="detail-row">
                <span class="detail-key">Trạng thái</span>
                <span class="detail-val">
                  <span
                    :class="['status-badge', 'status-badge--' + currentLesson.status.toLowerCase()]"
                  >
                    {{ statusLabel(currentLesson.status) }}
                  </span>
                </span>
              </div>
            </div>

            <div v-if="currentLesson.description" class="detail-section">
              <h4 class="section-label">Mô tả</h4>
              <p class="detail-description">{{ currentLesson.description }}</p>
            </div>

            <div v-if="currentLesson.content" class="detail-section">
              <h4 class="section-label">Nội dung bài giảng</h4>
              <pre class="content-preview">{{ currentLesson.content }}</pre>
            </div>

            <div v-if="currentLesson.files && currentLesson.files.length" class="detail-section">
              <h4 class="section-label">File đính kèm ({{ currentLesson.files.length }})</h4>
              <div class="file-list">
                <div v-for="f in currentLesson.files" :key="f.lessonFileId" class="file-item">
                  <div class="file-type-icon">{{ fileIcon(f.fileType) }}</div>
                  <div class="file-info">
                    <a :href="f.fileUrl" target="_blank" class="file-link">{{ f.fileName }}</a>
                    <div class="file-meta">
                      <span v-if="f.fileType" class="file-tag">{{ f.fileType }}</span>
                      <span v-if="f.fileSizeKb" class="file-size">{{
                        formatSize(f.fileSizeKb)
                      }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ══════════════════════ MODAL: CONFIRM DELETE ══════════════════════ -->
    <Teleport to="body">
      <div
        v-if="showDeleteConfirm"
        class="modal-overlay"
        @mousedown.self="showDeleteConfirm = false"
      >
        <div class="modal modal--sm">
          <div class="modal-header">
            <h2 class="modal-title">⚠️ Xác nhận xóa</h2>
          </div>
          <div class="modal-body">
            <p class="confirm-text">
              Bạn có chắc muốn xóa bài giảng
              <strong>"{{ deleteTarget?.title }}"</strong>?
            </p>
            <p class="confirm-sub">Hành động này không thể hoàn tác.</p>
          </div>
          <div class="modal-footer">
            <button class="btn-outline" @click="showDeleteConfirm = false">Hủy</button>
            <button class="btn-danger" @click="doDelete" :disabled="saving">
              <span v-if="saving" class="spinner"></span>
              {{ saving ? 'Đang xóa…' : 'Xác nhận xóa' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ══════════════════════ TOAST ══════════════════════ -->
    <Teleport to="body">
      <div class="toast-stack">
        <div v-for="t in toasts" :key="t.id" :class="['toast', 'toast--' + t.type]">
          <span>{{ t.icon }}</span> {{ t.msg }}
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'

// ── API base ──────────────────────────────────────────────────────
const API_BASE = 'http://localhost:8080/api/v1'

// ── Auth store (thay bằng Pinia/Vuex store thực tế) ───────────────
// Lấy từ localStorage hoặc auth store sau khi đăng nhập thành công.
// currentUserRole: 'ADMIN' | 'EMPLOYEE' | 'TEACHER'
const currentUserRole = ref(localStorage.getItem('user_role') ?? 'EMPLOYEE')
const isEmployee = computed(() => ['ADMIN', 'EMPLOYEE'].includes(currentUserRole.value))
const isTeacher = computed(() => currentUserRole.value === 'TEACHER')

// ── State ─────────────────────────────────────────────────────────
const lessons = ref([])
const subjects = ref([])
const teachers = ref([])
const stats = ref({ DRAFT: 0, PUBLISHED: 0, ARCHIVED: 0 })
const loading = ref(false)
const saving = ref(false)
const totalCount = ref(0)
const totalPages = ref(1)

const showModal = ref(false)
const showDetail = ref(false)
const showDeleteConfirm = ref(false)
const editMode = ref(false)
const activeTab = ref('info')
const currentLesson = ref(null)
const deleteTarget = ref(null)

const toasts = ref([])

const tabs = [
  { key: 'info', label: 'Thông tin cơ bản' },
  { key: 'content', label: 'Nội dung' },
  { key: 'files', label: 'File đính kèm' },
]

const statsCards = [
  { label: 'Bản nháp', status: 'DRAFT', color: 'draft' },
  { label: 'Đã xuất bản', status: 'PUBLISHED', color: 'published' },
  { label: 'Lưu trữ', status: 'ARCHIVED', color: 'archived' },
]

const filter = reactive({
  keyword: '',
  subjectId: '',
  difficultyLevel: '',
  status: null,
  page: 0,
  size: 10,
  sortBy: 'createdAt',
  sortDir: 'desc',
})

const emptyForm = () => ({
  lessonId: null,
  title: '',
  subjectId: '',
  teacherId: '',
  branchId: 1,
  gradeLevel: '',
  duration: null,
  difficultyLevel: '',
  status: 'DRAFT',
  description: '',
  content: '',
  files: [],
})
const form = reactive(emptyForm())
const errors = reactive({})
const newFile = reactive({ fileName: '', fileUrl: '', fileType: '', fileSizeKb: null })

// ── Computed ──────────────────────────────────────────────────────
const pageNumbers = computed(() => {
  const pages = []
  const start = Math.max(1, filter.page)
  const end = Math.min(totalPages.value, filter.page + 3)
  for (let p = start; p <= end; p++) pages.push(p)
  return pages
})

// ── API calls ─────────────────────────────────────────────────────
async function api(path, options = {}) {
  const token = localStorage.getItem('access_token') ?? ''
  const res = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...options.headers,
    },
    ...options,
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || `Lỗi HTTP ${res.status}`)
  }
  if (res.status === 204) return null
  return res.json()
}

async function fetchLessons() {
  loading.value = true
  try {
    const params = new URLSearchParams()
    Object.entries(filter).forEach(([k, v]) => {
      if (v !== '' && v !== null && v !== undefined) params.append(k, v)
    })
    const data = await api(`/lessons?${params}`)
    lessons.value = data.content
    totalCount.value = data.totalElements
    totalPages.value = data.totalPages
  } catch (e) {
    showToast('error', '❌ ' + e.message)
  } finally {
    loading.value = false
  }
}

async function fetchStats() {
  // TEACHER không có quyền xem stats — bỏ qua
  if (isTeacher.value) return
  try {
    stats.value = await api('/lessons/stats')
  } catch {
    /* stats là phụ trợ, bỏ qua lỗi */
  }
}

async function fetchSubjects() {
  try {
    // Gọi API subjects — điều chỉnh path nếu cần
    const data = await api('/subjects?status=ACTIVE&size=100')
    subjects.value = data.content ?? data
  } catch {
    subjects.value = []
  }
}

async function fetchTeachers() {
  try {
    const data = await api('/teachers?status=ACTIVE&size=200')
    teachers.value = data.content ?? data
  } catch {
    teachers.value = []
  }
}

// ── CRUD Actions ──────────────────────────────────────────────────
function openCreate() {
  Object.assign(form, emptyForm())
  Object.keys(errors).forEach((k) => delete errors[k])
  editMode.value = false
  activeTab.value = 'info'
  showModal.value = true
}

function openEdit(lesson) {
  Object.assign(form, {
    lessonId: lesson.lessonId,
    title: lesson.title || '',
    subjectId: lesson.subjectId || '',
    teacherId: lesson.teacherId || '',
    branchId: lesson.branchId || 1,
    gradeLevel: lesson.gradeLevel || '',
    duration: lesson.duration || null,
    difficultyLevel: lesson.difficultyLevel || '',
    status: lesson.status || 'DRAFT',
    description: lesson.description || '',
    content: lesson.content || '',
    files: lesson.files ? lesson.files.map((f) => ({ ...f })) : [],
  })
  Object.keys(errors).forEach((k) => delete errors[k])
  editMode.value = true
  activeTab.value = 'info'
  showModal.value = true
}

async function openDetail(lesson) {
  try {
    currentLesson.value = await api(`/lessons/${lesson.lessonId}`)
    showDetail.value = true
  } catch (e) {
    showToast('error', '❌ ' + e.message)
  }
}

function openEditFromDetail() {
  showDetail.value = false
  openEdit(currentLesson.value)
}

function closeModal() {
  showModal.value = false
}

function validateForm() {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.title.trim()) errors.title = 'Tiêu đề không được trống'
  if (!form.subjectId) errors.subjectId = 'Vui lòng chọn môn học'
  return Object.keys(errors).length === 0
}

async function save() {
  if (!validateForm()) {
    activeTab.value = 'info'
    return
  }
  saving.value = true
  try {
    const payload = {
      title: form.title.trim(),
      subjectId: Number(form.subjectId),
      teacherId: form.teacherId ? Number(form.teacherId) : null,
      branchId: Number(form.branchId),
      gradeLevel: form.gradeLevel || null,
      duration: form.duration || null,
      difficultyLevel: form.difficultyLevel || null,
      status: form.status,
      description: form.description || null,
      content: form.content || null,
      files: form.files.map((f) => ({
        fileName: f.fileName,
        fileUrl: f.fileUrl,
        fileType: f.fileType || null,
        fileSizeKb: f.fileSizeKb || null,
      })),
    }

    if (editMode.value) {
      await api(`/lessons/${form.lessonId}`, { method: 'PUT', body: JSON.stringify(payload) })
      showToast('success', '✅ Cập nhật bài giảng thành công')
    } else {
      await api('/lessons', { method: 'POST', body: JSON.stringify(payload) })
      showToast('success', '✅ Tạo bài giảng thành công')
    }
    closeModal()
    fetchLessons()
    fetchStats()
  } catch (e) {
    showToast('error', '❌ ' + e.message)
  } finally {
    saving.value = false
  }
}

function confirmDelete(lesson) {
  deleteTarget.value = lesson
  showDeleteConfirm.value = true
}

async function doDelete() {
  if (!deleteTarget.value) return
  saving.value = true
  try {
    await api(`/lessons/${deleteTarget.value.lessonId}`, { method: 'DELETE' })
    showToast('success', '🗑️ Đã xóa bài giảng')
    showDeleteConfirm.value = false
    fetchLessons()
    fetchStats()
  } catch (e) {
    showToast('error', '❌ ' + e.message)
  } finally {
    saving.value = false
  }
}

async function quickChangeStatus(lesson, newStatus) {
  try {
    await api(`/lessons/${lesson.lessonId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status: newStatus }),
    })
    lesson.status = newStatus
    fetchStats()
    showToast('success', '✅ Đổi trạng thái thành công')
  } catch (e) {
    showToast('error', '❌ ' + e.message)
  }
}

// ── File helpers ──────────────────────────────────────────────────
function addFileToForm() {
  if (!newFile.fileName || !newFile.fileUrl) return
  form.files.push({ ...newFile })
  Object.assign(newFile, { fileName: '', fileUrl: '', fileType: '', fileSizeKb: null })
}

function removeFileFromForm(idx) {
  form.files.splice(idx, 1)
}

// ── Filter / Pagination ───────────────────────────────────────────
function filterByStatus(status) {
  filter.status = status
  filter.page = 0
  fetchLessons()
}

function resetFilter() {
  Object.assign(filter, {
    keyword: '',
    subjectId: '',
    difficultyLevel: '',
    status: null,
    page: 0,
    sortBy: 'createdAt',
    sortDir: 'desc',
  })
  fetchLessons()
}

function goPage(p) {
  filter.page = p
  fetchLessons()
}

let searchTimer
function debouncedSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    filter.page = 0
    fetchLessons()
  }, 400)
}

// ── Toast ─────────────────────────────────────────────────────────
function showToast(type, msg) {
  const t = { id: Date.now(), type, msg, icon: type === 'success' ? '✅' : '❌' }
  toasts.value.push(t)
  setTimeout(() => {
    toasts.value = toasts.value.filter((x) => x.id !== t.id)
  }, 3500)
}

// ── Format helpers ────────────────────────────────────────────────
function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })
}
function diffLabel(d) {
  return { BASIC: 'Cơ bản', INTERMEDIATE: 'Trung cấp', ADVANCED: 'Nâng cao' }[d] || d
}
function statusLabel(s) {
  return { DRAFT: 'Bản nháp', PUBLISHED: 'Đã xuất bản', ARCHIVED: 'Lưu trữ' }[s] || s
}
function fileIcon(t) {
  return { pdf: '📄', pptx: '📊', mp4: '🎥', link: '🔗', docx: '📝' }[t] || '📎'
}
function formatSize(kb) {
  return kb >= 1024 ? (kb / 1024).toFixed(1) + ' MB' : kb + ' KB'
}

// ── Init ──────────────────────────────────────────────────────────
onMounted(() => {
  fetchSubjects()
  fetchTeachers()
  fetchLessons()
  fetchStats()
})
</script>

<style scoped>
/* ── Design tokens ─────────────────────────────────────────────── */
:root {
  --primary: #2563eb;
  --primary-h: #1d4ed8;
  --success: #16a34a;
  --warning: #d97706;
  --danger: #dc2626;
  --muted: #6b7280;
  --border: #e5e7eb;
  --surface: #f9fafb;
  --white: #ffffff;
  --text: #111827;
  --text-sub: #6b7280;
  --radius: 10px;
  --shadow: 0 1px 3px rgba(0, 0, 0, 0.1), 0 1px 2px rgba(0, 0, 0, 0.06);
  --shadow-lg: 0 10px 25px rgba(0, 0, 0, 0.15);
}

/* ── Base layout ───────────────────────────────────────────────── */
.lesson-management {
  font-family: 'Inter', 'Segoe UI', system-ui, sans-serif;
  max-width: 1280px;
  margin: 0 auto;
  padding: 28px 20px;
  color: var(--text);
  background: #f1f5f9;
  min-height: 100vh;
}

/* ── Page header ───────────────────────────────────────────────── */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}
.header-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.35);
}
.header-icon svg {
  width: 26px;
  height: 26px;
  color: white;
}
.page-title {
  font-size: 1.6rem;
  font-weight: 700;
  margin: 0;
  letter-spacing: -0.4px;
}
.page-subtitle {
  font-size: 0.875rem;
  color: var(--text-sub);
  margin: 2px 0 0;
}

/* ── Stats row ──────────────────────────────────────────────────── */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 20px;
}
.stat-card {
  background: var(--white);
  border-radius: var(--radius);
  padding: 16px 20px;
  cursor: pointer;
  border: 2px solid transparent;
  box-shadow: var(--shadow);
  transition: all 0.18s;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}
.stat-card.active {
  border-color: var(--primary);
  background: #eff6ff;
}
.stat-number {
  font-size: 1.9rem;
  font-weight: 800;
  line-height: 1;
}
.stat-label {
  font-size: 0.78rem;
  color: var(--text-sub);
  margin-top: 4px;
  font-weight: 500;
}
.stat-card--draft .stat-number {
  color: #6b7280;
}
.stat-card--published .stat-number {
  color: #16a34a;
}
.stat-card--archived .stat-number {
  color: #9ca3af;
}
.stat-card--total .stat-number {
  color: var(--primary);
}

/* ── Filter bar ─────────────────────────────────────────────────── */
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  background: var(--white);
  border-radius: var(--radius);
  padding: 14px 16px;
  margin-bottom: 16px;
  box-shadow: var(--shadow);
}
.search-wrap {
  position: relative;
  flex: 1 1 220px;
}
.search-icon {
  position: absolute;
  left: 11px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 0.9rem;
}
.search-input {
  width: 100%;
  padding: 8px 34px;
  border: 1px solid var(--border);
  border-radius: 8px;
  font-size: 0.875rem;
  background: var(--surface);
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.15s;
}
.search-input:focus {
  border-color: var(--primary);
  background: white;
}
.clear-btn {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  cursor: pointer;
  color: var(--muted);
  font-size: 0.8rem;
}
.filter-select {
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  font-size: 0.875rem;
  background: var(--surface);
  cursor: pointer;
  outline: none;
  transition: border-color 0.15s;
}
.filter-select:focus {
  border-color: var(--primary);
}

/* ── Buttons ────────────────────────────────────────────────────── */
.btn-primary {
  padding: 9px 20px;
  background: var(--primary);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition:
    background 0.15s,
    transform 0.12s,
    box-shadow 0.15s;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.3);
}
.btn-primary:hover:not(:disabled) {
  background: var(--primary-h);
  transform: translateY(-1px);
}
.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn-outline {
  padding: 8px 16px;
  background: white;
  color: var(--primary);
  border: 1.5px solid var(--primary);
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-outline:hover:not(:disabled) {
  background: #eff6ff;
}
.btn-outline:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-danger {
  padding: 8px 18px;
  background: var(--danger);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: background 0.15s;
}
.btn-danger:hover:not(:disabled) {
  background: #b91c1c;
}
.btn-danger:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn-sm {
  padding: 6px 12px;
  font-size: 0.82rem;
}
.btn-icon {
  font-size: 1rem;
  line-height: 1;
}

/* ── Table ──────────────────────────────────────────────────────── */
.table-container {
  background: var(--white);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  overflow: hidden;
}
.data-table {
  width: 100%;
  border-collapse: collapse;
}
.data-table thead {
  background: var(--surface);
}
.data-table th {
  padding: 12px 14px;
  text-align: left;
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--text-sub);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  border-bottom: 1px solid var(--border);
}
.th-action {
  text-align: center;
}
.data-row {
  transition: background 0.12s;
}
.data-row:hover {
  background: #f8faff;
}
.data-row:not(:last-child) td {
  border-bottom: 1px solid var(--border);
}
.data-table td {
  padding: 13px 14px;
  font-size: 0.875rem;
  vertical-align: middle;
}

.td-title {
  max-width: 260px;
}
.lesson-title {
  display: block;
  font-weight: 600;
  color: var(--text);
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.lesson-teacher {
  display: block;
  font-size: 0.75rem;
  color: var(--text-sub);
  margin-top: 2px;
}
.td-date {
  white-space: nowrap;
  color: var(--text-sub);
  font-size: 0.82rem;
}
.td-actions {
  text-align: center;
  white-space: nowrap;
}
.text-muted {
  color: var(--text-sub);
}

.subject-tag {
  display: inline-block;
  padding: 3px 8px;
  background: #ede9fe;
  color: #6d28d9;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 600;
}

/* Difficulty badges */
.badge-diff {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 600;
}
.diff--basic {
  background: #d1fae5;
  color: #065f46;
}
.diff--intermediate {
  background: #fef3c7;
  color: #92400e;
}
.diff--advanced {
  background: #fee2e2;
  color: #991b1b;
}

/* Inline status select */
.status-select {
  padding: 4px 8px;
  border-radius: 6px;
  border: 1.5px solid;
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  outline: none;
  transition: background 0.12s;
}
.status--draft {
  border-color: #d1d5db;
  background: #f3f4f6;
  color: #374151;
}
.status--published {
  border-color: #86efac;
  background: #f0fdf4;
  color: #166534;
}
.status--archived {
  border-color: #d1d5db;
  background: #f9fafb;
  color: #6b7280;
}

/* Icon buttons */
.icon-btn {
  width: 34px;
  height: 34px;
  border-radius: 7px;
  border: 1px solid var(--border);
  background: white;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.9rem;
  margin: 0 2px;
  transition: all 0.12s;
}
.icon-btn.edit:hover {
  background: #eff6ff;
  border-color: #bfdbfe;
}
.icon-btn.delete:hover {
  background: #fef2f2;
  border-color: #fecaca;
}

/* File count */
.file-count {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.8rem;
  font-weight: 600;
  color: #4338ca;
  background: #eef2ff;
  padding: 2px 8px;
  border-radius: 12px;
}

/* ── Skeleton ────────────────────────────────────────────────────── */
.skeleton-wrap {
  padding: 12px 14px;
}
.skeleton-row {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}
.skeleton-cell {
  height: 18px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  border-radius: 6px;
  animation: shimmer 1.5s infinite;
}
.w8 {
  width: 8%;
}
.w10 {
  width: 10%;
}
.w12 {
  width: 12%;
}
.w15 {
  width: 15%;
}
.w20 {
  width: 20%;
}
@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

/* ── Empty state ─────────────────────────────────────────────────── */
.empty-state {
  text-align: center;
  padding: 60px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.empty-icon {
  font-size: 3.5rem;
  opacity: 0.6;
}
.empty-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text);
  margin: 0;
}
.empty-sub {
  color: var(--text-sub);
  margin: 0;
  font-size: 0.875rem;
}

/* ── Pagination ──────────────────────────────────────────────────── */
.pagination {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: center;
  padding: 16px;
}
.page-btn {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: white;
  cursor: pointer;
  font-size: 0.875rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.12s;
}
.page-btn:hover:not(:disabled) {
  border-color: var(--primary);
  color: var(--primary);
}
.page-btn.active {
  background: var(--primary);
  color: white;
  border-color: var(--primary);
}
.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.page-info {
  font-size: 0.8rem;
  color: var(--text-sub);
  margin-left: 8px;
}

/* ── Modal overlay ───────────────────────────────────────────────── */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
  backdrop-filter: blur(2px);
  animation: fadeIn 0.18s ease;
}
@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.modal {
  background: white;
  border-radius: 16px;
  width: 100%;
  max-width: 680px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25);
  animation: slideUp 0.2s ease;
}
.modal--wide {
  max-width: 900px;
}
.modal--sm {
  max-width: 420px;
}
@keyframes slideUp {
  from {
    transform: translateY(20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.modal-header {
  padding: 20px 24px 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}
.modal-title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}
.modal-icon {
  font-size: 1.3rem;
}
.modal-title {
  font-size: 1.15rem;
  font-weight: 700;
  margin: 0;
}
.modal-close {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: var(--surface);
  cursor: pointer;
  font-size: 0.9rem;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.12s;
}
.modal-close:hover {
  background: #fee2e2;
  border-color: #fecaca;
}
.detail-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* Tabs */
.modal-tabs {
  display: flex;
  gap: 0;
  padding: 16px 24px 0;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}
.tab-btn {
  padding: 9px 16px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-sub);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: all 0.15s;
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
}
.tab-btn:hover {
  color: var(--primary);
}
.tab-btn.active {
  color: var(--primary);
  border-bottom-color: var(--primary);
  font-weight: 700;
}
.tab-badge {
  background: var(--primary);
  color: white;
  border-radius: 12px;
  font-size: 0.7rem;
  padding: 1px 6px;
  font-weight: 700;
}

/* Modal body */
.modal-body {
  padding: 20px 24px;
  overflow-y: auto;
  flex: 1;
}
.modal-footer {
  padding: 16px 24px;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-shrink: 0;
}

/* Form elements */
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.span-full {
  grid-column: 1 / -1;
}
.form-group {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.form-label {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text);
}
.form-label.required::after {
  content: ' *';
  color: var(--danger);
}
.form-input {
  padding: 9px 12px;
  border: 1.5px solid var(--border);
  border-radius: 8px;
  font-size: 0.875rem;
  outline: none;
  background: var(--surface);
  transition:
    border-color 0.15s,
    background 0.15s;
  font-family: inherit;
}
.form-input:focus {
  border-color: var(--primary);
  background: white;
}
.form-input.error {
  border-color: var(--danger);
}
.form-error {
  font-size: 0.78rem;
  color: var(--danger);
}
.form-textarea {
  padding: 10px 12px;
  border: 1.5px solid var(--border);
  border-radius: 8px;
  font-size: 0.875rem;
  resize: vertical;
  outline: none;
  background: var(--surface);
  font-family: inherit;
  transition: border-color 0.15s;
  line-height: 1.5;
}
.form-textarea:focus {
  border-color: var(--primary);
  background: white;
}
.content-editor {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 0.82rem;
}
.form-hint {
  font-size: 0.78rem;
  color: var(--text-sub);
  margin: 0 0 6px;
}
.char-count {
  font-size: 0.72rem;
  color: var(--text-sub);
  text-align: right;
}

/* Files section in modal */
.file-add-form {
  background: var(--surface);
  border-radius: 10px;
  padding: 16px;
  border: 1px dashed var(--border);
  margin-bottom: 20px;
}
.file-section-title {
  font-size: 0.875rem;
  font-weight: 700;
  margin: 0 0 12px;
  color: var(--text);
}
.file-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.file-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 12px;
}
.file-type-icon {
  font-size: 1.6rem;
  flex-shrink: 0;
}
.file-info {
  flex: 1;
  min-width: 0;
}
.file-name {
  font-size: 0.875rem;
  font-weight: 600;
  display: block;
}
.file-url {
  font-size: 0.78rem;
  color: var(--text-sub);
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.file-link {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--primary);
  text-decoration: none;
}
.file-link:hover {
  text-decoration: underline;
}
.file-meta {
  display: flex;
  gap: 8px;
  margin-top: 4px;
  flex-wrap: wrap;
}
.file-tag {
  font-size: 0.7rem;
  font-weight: 700;
  padding: 2px 7px;
  background: #e0e7ff;
  color: #3730a3;
  border-radius: 5px;
  text-transform: uppercase;
}
.file-size {
  font-size: 0.75rem;
  color: var(--text-sub);
}
.empty-files {
  text-align: center;
  padding: 30px;
  color: var(--text-sub);
  background: var(--surface);
  border-radius: 10px;
  border: 1px dashed var(--border);
}
.empty-files span {
  font-size: 2rem;
  display: block;
  margin-bottom: 6px;
}

/* Detail view */
.detail-body {
  padding: 20px 24px;
}
.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0;
  border: 1px solid var(--border);
  border-radius: 10px;
  overflow: hidden;
  margin-bottom: 20px;
}
.detail-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 16px;
  border-bottom: 1px solid var(--border);
}
.detail-row:last-child {
  border-bottom: none;
}
.detail-key {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--text-sub);
  min-width: 80px;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}
.detail-val {
  font-size: 0.875rem;
  color: var(--text);
}
.detail-section {
  margin-bottom: 20px;
}
.section-label {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--text-sub);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin: 0 0 8px;
}
.detail-description {
  font-size: 0.875rem;
  line-height: 1.6;
  color: var(--text);
  margin: 0;
}
.content-preview {
  background: #1e1e2e;
  color: #cdd6f4;
  padding: 16px;
  border-radius: 10px;
  font-size: 0.8rem;
  line-height: 1.6;
  overflow-x: auto;
  white-space: pre-wrap;
  font-family: 'Fira Code', 'Consolas', monospace;
  max-height: 300px;
  overflow-y: auto;
}
.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.dot--draft {
  background: #9ca3af;
}
.dot--published {
  background: #22c55e;
  box-shadow: 0 0 6px #22c55e;
}
.dot--archived {
  background: #d1d5db;
}
.status-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 0.78rem;
  font-weight: 700;
}
.status-badge--draft {
  background: #f3f4f6;
  color: #6b7280;
}
.status-badge--published {
  background: #dcfce7;
  color: #16a34a;
}
.status-badge--archived {
  background: #f9fafb;
  color: #9ca3af;
}

/* ── Confirm modal ───────────────────────────────────────────────── */
.confirm-text {
  font-size: 0.95rem;
  margin: 0 0 6px;
  line-height: 1.5;
}
.confirm-sub {
  font-size: 0.82rem;
  color: var(--text-sub);
  margin: 0;
}

/* ── Toast ───────────────────────────────────────────────────────── */
.toast-stack {
  position: fixed;
  bottom: 24px;
  right: 24px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  z-index: 9999;
}
.toast {
  padding: 12px 20px;
  border-radius: 10px;
  font-size: 0.875rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: var(--shadow-lg);
  animation: slideIn 0.25s ease;
}
@keyframes slideIn {
  from {
    transform: translateX(60px);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}
.toast--success {
  background: #f0fdf4;
  color: #166534;
  border: 1px solid #bbf7d0;
}
.toast--error {
  background: #fef2f2;
  color: #991b1b;
  border: 1px solid #fecaca;
}

/* ── Spinner ──────────────────────────────────────────────────────── */
.spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* ── Responsive ──────────────────────────────────────────────────── */
@media (max-width: 768px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
  .form-grid {
    grid-template-columns: 1fr;
  }
  .detail-grid {
    grid-template-columns: 1fr;
  }
  .modal {
    max-width: 100%;
    margin: 0;
    border-radius: 16px 16px 0 0;
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
  }
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }
  .filter-select,
  .search-wrap {
    width: 100%;
  }
}
</style>
