<script setup>
/**
 * Trang thêm / sửa bài giảng.
 * Chọn Danh mục (từ bảng SubjectCategory) → lọc Môn học trong danh mục đó.
 * GradeLevel là text tự do (chọn từ dropdown gợi ý).
 * Hỗ trợ: upload file PDF (multipart), thêm link Canva (JSON), xóa file đính kèm.
 */
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { lessonApi } from '@/api/lessons'
import { branchApi } from '@/api/branches'
import { subjectCategoryApi } from '@/api/subjectCategories'

const branches = ref([])

const route = useRoute()
const router = useRouter()

const isEdit = computed(() => !!route.params.id)
const lessonId = computed(() => (isEdit.value ? Number(route.params.id) : null))

// FIX (2026-07-10): trang này cũng được dùng chung cho 2 khu vực, giống
// LessonListPage.vue (xem ghi chú ở đó). route.name ở đây là
// 'admin-lesson-new' / 'admin-lesson-edit' (khu ADMIN) hoặc
// 'lesson-new' / 'lesson-edit' (khu STAFF) tuỳ nơi người dùng bấm vào.
// Trước đây mọi điều hướng (nút "Danh sách", sau khi Tạo mới) đều push cứng
// sang tên route khu STAFF -> ADMIN bị route guard đá về dashboard.
const isAdminArea = computed(() => route.name?.toString().startsWith('admin-'))
const listRouteName = computed(() => (isAdminArea.value ? 'admin-lesson-list' : 'lesson-list'))
const editRouteName = computed(() => (isAdminArea.value ? 'admin-lesson-edit' : 'lesson-edit'))

const subjects = ref([]) // tất cả subject ACTIVE
const gradeLevels = ref([])

// Danh sách nhóm môn từ bảng SubjectCategory: [{ id, code, name, ... }]
const categories = ref([])

// Danh mục đang được chọn (bước 1) — lưu theo name (String) để khớp filter backend
const selectedCategory = ref('')

// Môn học lọc theo danh mục đã chọn (bước 2)
const filteredSubjects = computed(() => {
  if (!selectedCategory.value) return subjects.value
  return subjects.value.filter((s) => s.category === selectedCategory.value)
})

// Khi đổi danh mục → reset subjectId nếu subject hiện tại không thuộc danh mục mới
watch(selectedCategory, (newCat) => {
  if (!newCat) return
  const currentSubject = subjects.value.find((s) => s.id === form.subjectId)
  if (currentSubject && currentSubject.category !== newCat) {
    form.subjectId = null
  }
})
const closeCanvaForm = () => {
  showCanvaForm.value = false
  canvaError.value = ''
}

const STATUS_OPTIONS = [
  { value: 'DRAFT', label: 'Bản nháp (DRAFT)' },
  { value: 'PUBLISHED', label: 'Đã đăng (PUBLISHED)' },
  { value: 'ARCHIVED', label: 'Lưu kho (ARCHIVED)' },
]

const form = reactive({
  subjectId: null,
  branchId: null,
  teacherId: null,
  title: '',
  description: '',
  gradeLevel: '',
  status: 'DRAFT',
})

const loadingPage = ref(false)
const saving = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const attachedFiles = ref([])

const pdfInput = ref(null)
const pdfFiles = ref([])
const uploadingPDF = ref(false)
const pdfError = ref('')

const showCanvaForm = ref(false)
const canvaForm = reactive({ fileName: '', canvaUrl: '' })
const addingCanva = ref(false)
const canvaError = ref('')

const deletingFileId = ref(null)

async function loadMeta() {
  try {
    const [catsRes, subRes, gradeRes, brRes] = await Promise.all([
      subjectCategoryApi.listActive(),
      lessonApi.subjects(),
      lessonApi.gradeLevels(),
      branchApi.list(),
    ])
    categories.value = catsRes.data
    subjects.value = subRes.data
    gradeLevels.value = gradeRes.data
    branches.value = brRes.data
  } catch (e) {
    console.error('loadMeta', e)
  }
}

async function loadLesson() {
  if (!isEdit.value) return
  loadingPage.value = true
  try {
    const { data } = await lessonApi.detail(lessonId.value)
    form.subjectId = data.subjectId
    form.branchId = data.branchId
    form.teacherId = data.teacherId
    form.title = data.title
    form.description = data.description || ''
    form.gradeLevel = data.gradeLevel || ''
    form.status = data.status
    attachedFiles.value = data.files || []
    // Khôi phục category dựa trên category trả về từ API
    if (data.category) {
      selectedCategory.value = data.category
    }
  } catch {
    errorMsg.value = 'Không tải được bài giảng. Vui lòng thử lại.'
  } finally {
    loadingPage.value = false
  }
}

async function onSubmit() {
  // Chặn double-submit: click 2 lần liên tiếp/double-click trước khi Vue kịp
  // re-render nút :disabled="saving" có thể khiến onSubmit() chạy 2 lần, gửi
  // 2 request POST/PUT chồng nhau (1 thành công set successMsg, 1 fail set
  // errorMsg) -> UI hiện đồng thời cả 2 thông báo trái ngược nhau.
  if (saving.value) return

  errorMsg.value = ''
  successMsg.value = ''

  if (!form.branchId) {
    errorMsg.value = 'Vui lòng chọn chi nhánh.'
    return
  }
  if (!form.subjectId) {
    errorMsg.value = 'Vui lòng chọn môn học.'
    return
  }
  if (!form.title.trim()) {
    errorMsg.value = 'Tiêu đề không được để trống.'
    return
  }

  const body = {
    subjectId: Number(form.subjectId),
    branchId: Number(form.branchId),
    teacherId: form.teacherId ? Number(form.teacherId) : null,
    title: form.title.trim(),
    description: form.description.trim() || null,
    gradeLevel: form.gradeLevel || null,
    status: form.status,
  }

  saving.value = true
  try {
    if (isEdit.value) {
      await lessonApi.update(lessonId.value, body)
      successMsg.value = 'Đã cập nhật bài giảng.'
    } else {
      const { data } = await lessonApi.create(body)
      successMsg.value = 'Tạo bài giảng thành công!'
      // FIX: editRouteName là computed ref — trong <script setup> (không phải
      // template) Vue KHÔNG tự unwrap ref, nên phải lấy .value. Thiếu .value
      // khiến router.replace() nhận cả object ComputedRefImpl làm "name",
      // Vue Router không khớp được route -> throw lỗi ngay tại đây (dù POST
      // /lessons vừa mới THÀNH CÔNG) -> rơi xuống catch bên dưới -> UI hiện
      // đồng thời "Tạo bài giảng thành công!" VÀ "Lưu thất bại, thử lại sau."
      router.replace({ name: editRouteName.value, params: { id: data.id } })
    }
  } catch (e) {
    errorMsg.value = e.response?.data?.message || 'Lưu thất bại, thử lại sau.'
  } finally {
    saving.value = false
  }
}

function onPdfChange(e) {
  pdfFiles.value = Array.from(e.target.files || [])
}

async function uploadPDF() {
  if (uploadingPDF.value) return
  if (!pdfFiles.value.length) {
    pdfError.value = 'Chọn ít nhất 1 file PDF.'
    return
  }
  pdfError.value = ''
  uploadingPDF.value = true
  try {
    const { data } = await lessonApi.uploadFiles(lessonId.value, pdfFiles.value)
    attachedFiles.value.push(...data)
    pdfFiles.value = []
    if (pdfInput.value) pdfInput.value.value = ''
  } catch (e) {
    pdfError.value = e.response?.data?.message || 'Upload thất bại.'
  } finally {
    uploadingPDF.value = false
  }
}

async function addCanva() {
  if (addingCanva.value) return
  canvaError.value = ''
  if (!canvaForm.fileName.trim()) {
    canvaError.value = 'Nhập tên hiển thị.'
    return
  }
  if (!canvaForm.canvaUrl.startsWith('https://')) {
    canvaError.value = 'Link Canva phải bắt đầu bằng https://'
    return
  }
  addingCanva.value = true
  try {
    const { data } = await lessonApi.addCanvaLink(lessonId.value, {
      fileName: canvaForm.fileName.trim(),
      canvaUrl: canvaForm.canvaUrl.trim(),
    })
    attachedFiles.value.push(data)
    canvaForm.fileName = ''
    canvaForm.canvaUrl = ''
    showCanvaForm.value = false
  } catch (e) {
    canvaError.value = e.response?.data?.message || 'Thêm link thất bại.'
  } finally {
    addingCanva.value = false
  }
}

async function deleteFile(fileId) {
  if (deletingFileId.value !== null) return
  deletingFileId.value = fileId
  try {
    await lessonApi.removeFile(lessonId.value, fileId)
    attachedFiles.value = attachedFiles.value.filter((f) => f.id !== fileId)
  } catch {
    errorMsg.value = 'Xóa file thất bại.'
  } finally {
    deletingFileId.value = null
  }
}

function fileIcon(type) {
  if (type === 'canva') return '🎨'
  if (type === 'pptx' || type === 'ppt') return '📊'
  if (type === 'pdf') return '📄'
  return '📎'
}

function fileLabel(type) {
  if (type === 'canva') return 'Canva'
  if (type === 'pptx' || type === 'ppt') return 'PPT'
  if (type === 'pdf') return 'PDF'
  return type?.toUpperCase() || 'File'
}

onMounted(async () => {
  await Promise.all([loadMeta(), loadLesson()])
})
</script>

<template>
  <div class="page">
    <div class="page__head">
      <button class="back-btn" @click="router.push({ name: listRouteName })">← Danh sách</button>
      <h1 class="page__title">{{ isEdit ? 'Sửa bài giảng' : 'Thêm bài giảng' }}</h1>
    </div>
    <div v-if="loadingPage" class="loading">Đang tải dữ liệu…</div>

    <div v-else class="layout">
      <!-- Cột trái: form chính -->
      <div class="col-main">
        <form class="card" @submit.prevent="onSubmit">
          <h2 class="card__title">Thông tin bài giảng</h2>

          <!-- Hàng 0: Chi nhánh -->
          <div class="grid-1">
            <label class="field">
              <span>Chi nhánh <span class="req">*</span></span>
              <select v-model="form.branchId" required>
                <option :value="null">-- Chọn chi nhánh --</option>
                <option v-for="b in branches" :key="b.id" :value="b.id">{{ b.name }}</option>
              </select>
            </label>
          </div>

          <!-- Hàng 1: Danh mục + Môn học (lọc theo danh mục) -->
          <div class="grid-2">
            <label class="field">
              <span>Danh mục <span class="req">*</span></span>
              <select v-model="selectedCategory" required>
                <option value="">-- Chọn danh mục --</option>
                <option v-for="cat in categories" :key="cat.id" :value="cat.name">
                  {{ cat.name }}
                </option>
              </select>
            </label>

            <label class="field">
              <span>Môn học <span class="req">*</span></span>
              <select v-model="form.subjectId" :disabled="!selectedCategory" required>
                <option :value="null">-- Chọn môn học --</option>
                <option v-for="s in filteredSubjects" :key="s.id" :value="s.id">
                  {{ s.name }}
                </option>
              </select>
              <span v-if="!selectedCategory" class="field-hint">Chọn danh mục trước</span>
            </label>
          </div>

          <!-- Hàng 1b: Khối lớp -->
          <div class="grid-1">
            <label class="field">
              <span>Khối lớp</span>
              <select v-model="form.gradeLevel" required>
                <option value="">— Chọn khối —</option>
                <option v-for="g in gradeLevels" :key="g" :value="g">{{ g }}</option>
              </select>
            </label>
          </div>

          <!-- Hàng 2: Tiêu đề -->
          <label class="field field--req field--full">
            <span>Tiêu đề bài giảng</span>
            <input v-model="form.title" type="text" placeholder="Nhập tiêu đề..." required />
          </label>

          <!-- Hàng 3: Mô tả -->
          <label class="field field--full">
            <span>Mô tả ngắn</span>
            <textarea
              v-model="form.description"
              rows="2"
              placeholder="Tóm tắt ngắn về bài giảng..."
            ></textarea>
          </label>

          <!-- Hàng 5: Trạng thái -->
          <div class="grid-1">
            <label class="field field--req">
              <span>Trạng thái</span>
              <select v-model="form.status" required>
                <option v-for="s in STATUS_OPTIONS" :key="s.value" :value="s.value">
                  {{ s.label }}
                </option>
              </select>
            </label>
          </div>

          <p v-if="errorMsg" class="msg msg--error">{{ errorMsg }}</p>
          <p v-if="successMsg" class="msg msg--ok">{{ successMsg }}</p>

          <div class="form-footer">
            <button
              class="btn btn--ghost"
              type="button"
              @click="router.push({ name: listRouteName })"
            >
              Hủy
            </button>
            <button class="btn" type="submit" :disabled="saving">
              {{ saving ? 'Đang lưu…' : isEdit ? 'Cập nhật' : 'Tạo bài giảng' }}
            </button>
          </div>
        </form>
      </div>

      <!-- Cột phải: file đính kèm (chỉ hiện khi đang sửa) -->
      <div v-if="isEdit" class="col-side">
        <!-- Giáo án -->
        <div class="card">
          <h2 class="card__title">📄 Tải lên giáo án</h2>

          <input
            ref="pdfInput"
            type="file"
            accept=".pdf"
            multiple
            class="file-input"
            @change="onPdfChange"
          />

          <div v-if="pdfFiles.length" class="file-preview">
            <span v-for="f in pdfFiles" :key="f.name" class="file-chip">📄 {{ f.name }}</span>
          </div>

          <p v-if="pdfError" class="msg msg--error">{{ pdfError }}</p>

          <button class="btn" :disabled="uploadingPDF || !pdfFiles.length" @click="uploadPDF">
            {{ uploadingPDF ? 'Đang tải…' : 'Tải lên' }}
          </button>
        </div>

        <!-- Bài giảng (link Canva) -->
        <div class="card">
          <h2 class="card__title">🎨 Tải lên bài giảng</h2>
          <p class="card__sub">Dán link trình chiếu Canva đã publish để xem trực tiếp.</p>

          <button
            v-if="!showCanvaForm"
            class="btn btn--ghost btn--full"
            @click="showCanvaForm = true"
          >
            + Tải lên bài giảng
          </button>

          <div v-else class="canva-form">
            <label class="field">
              <span>Tên hiển thị</span>
              <input
                v-model="canvaForm.fileName"
                type="text"
                placeholder="VD: Slide bài giảng STEM"
              />
            </label>
            <label class="field">
              <span>Link Canva (https://...)</span>
              <input
                v-model="canvaForm.canvaUrl"
                type="url"
                placeholder="https://www.canva.com/design/..."
              />
            </label>
            <p v-if="canvaError" class="msg msg--error">{{ canvaError }}</p>
            <div class="canva-form__actions">
              <button class="btn btn--ghost" @click="closeCanvaForm">Hủy</button>
              <button class="btn" :disabled="addingCanva" @click="addCanva">
                {{ addingCanva ? 'Đang thêm…' : 'Thêm' }}
              </button>
            </div>
          </div>
        </div>

        <!-- Danh sách file đính kèm hiện có -->
        <div class="card">
          <h2 class="card__title">Tài liệu đính kèm</h2>
          <div v-if="!attachedFiles.length" class="empty-files">Chưa có tài liệu nào.</div>
          <ul v-else class="file-list">
            <li v-for="f in attachedFiles" :key="f.id" class="file-item">
              <span class="file-icon">{{ fileIcon(f.fileType) }}</span>
              <div class="file-info">
                <a
                  v-if="f.fileType === 'canva'"
                  :href="f.fileUrl"
                  target="_blank"
                  class="file-name"
                  >{{ f.fileName }}</a
                >
                <span v-else class="file-name">{{ f.fileName }}</span>
                <span class="file-meta">
                  {{ fileLabel(f.fileType) }}
                  <template v-if="f.fileSizeKb"> · {{ f.fileSizeKb }} KB</template>
                </span>
              </div>
              <button
                class="del-btn"
                :disabled="deletingFileId === f.id"
                title="Xóa file"
                @click="deleteFile(f.id)"
              >
                {{ deletingFileId === f.id ? '…' : '✕' }}
              </button>
            </li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 1060px;
}
.page__head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}
.page__title {
  margin: 0;
  font-size: 22px;
  color: var(--a-text, #0f172a);
}
.back-btn {
  background: none;
  border: none;
  color: var(--c-primary, #f97316);
  font-size: 14px;
  cursor: pointer;
  padding: 0;
}
.back-btn:hover {
  text-decoration: underline;
}
.loading {
  color: var(--c-text-muted);
  padding: 32px;
  text-align: center;
}

.layout {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 20px;
  align-items: start;
}
@media (max-width: 900px) {
  .layout {
    grid-template-columns: 1fr;
  }
}

.card {
  background: var(--c-surface);
  border: 1px solid var(--a-border, #e2e8f0);
  border-radius: 14px;
  padding: 20px;
  margin-bottom: 16px;
}
.card:last-child {
  margin-bottom: 0;
}
.card__title {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
  color: var(--c-text);
}
.card__sub {
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--c-text-muted);
}

.grid-1 {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
  max-width: 50%;
}
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.grid-3 {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 14px;
}
@media (max-width: 600px) {
  .grid-1,
  .grid-2,
  .grid-3 {
    grid-template-columns: 1fr;
    max-width: 100%;
  }
}
.field-hint {
  font-size: 11px;
  color: var(--c-text-muted);
  margin-top: 2px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 5px;
  font-size: 13px;
  color: var(--c-text);
  margin-bottom: 14px;
}
.field--full {
  grid-column: 1 / -1;
}
.field--req > span::after {
  content: ' *';
  color: #ef4444;
}
.field input,
.field select,
.field textarea {
  padding: 9px 11px;
  border: 1px solid var(--c-input-border);
  border-radius: 9px;
  font-size: 14px;
  outline: none;
  background: var(--c-surface);
  resize: vertical;
  font-family: inherit;
}
.field input:focus,
.field select:focus,
.field textarea:focus {
  border-color: var(--c-primary, #f97316);
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}

.file-input {
  display: block;
  font-size: 13px;
  color: var(--c-text);
  margin-bottom: 10px;
  width: 100%;
}
.file-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}
.file-chip {
  background: var(--c-surface-2);
  border-radius: 6px;
  padding: 3px 8px;
  font-size: 12px;
  color: var(--c-text);
}

.canva-form {
  margin-top: 10px;
}
.canva-form__actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 8px;
}

.empty-files {
  color: var(--c-text-muted);
  font-size: 13px;
  padding: 8px 0;
}
.file-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.file-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px solid var(--c-border-soft);
}
.file-item:last-child {
  border-bottom: none;
}
.file-icon {
  font-size: 20px;
  flex-shrink: 0;
  margin-top: 1px;
}
.file-info {
  flex: 1;
  min-width: 0;
}
.file-name {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--c-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-decoration: none;
}
a.file-name:hover {
  color: var(--c-primary, #f97316);
  text-decoration: underline;
}
.file-meta {
  font-size: 11px;
  color: var(--c-text-muted);
}
.del-btn {
  background: none;
  border: none;
  color: var(--c-text-muted);
  cursor: pointer;
  font-size: 14px;
  padding: 2px 6px;
  border-radius: 4px;
  flex-shrink: 0;
}
.del-btn:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}
.del-btn:disabled {
  opacity: 0.5;
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
}
.btn:hover:not(:disabled) {
  filter: brightness(1.05);
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn--ghost {
  background: var(--c-surface-2);
  color: var(--c-text);
}
.btn--ghost:hover:not(:disabled) {
  background: var(--c-border);
  filter: none;
}
.btn--full {
  width: 100%;
}

.msg {
  font-size: 13px;
  border-radius: 8px;
  padding: 9px 12px;
  margin: 10px 0 0;
}
.msg--error {
  background: rgba(239, 68, 68, 0.1);
  color: #b91c1c;
}
.msg--ok {
  background: rgba(34, 197, 94, 0.12);
  color: #047857;
}
:root[data-theme='dark'] .msg--error {
  color: #f87171;
}
:root[data-theme='dark'] .msg--ok {
  color: #4ade80;
}
</style>
