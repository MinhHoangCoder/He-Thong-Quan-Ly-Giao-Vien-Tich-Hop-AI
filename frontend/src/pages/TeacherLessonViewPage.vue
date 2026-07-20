<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { lessonApi } from '@/api/lessons'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const error = ref('')

const lesson = ref(null)

// FIX (2026-07-10): id file đang được tải, dùng để disable nút + hiện "Đang tải…"
// tránh GV bấm nhiều lần liên tiếp trong lúc chờ file lớn tải xong.
const downloadingFileId = ref(null)
const downloadError = ref('')

/**
 * Bấm "Xem" trên 1 tài liệu đính kèm:
 *  - fileType === 'canva' -> mở LUÔN link Canva mới nhất ở tab mới.
 *    (fileUrl của file canva chính là URL Canva thật, không phải file vật lý,
 *    nên không cần gọi API tải blob).
 *  - Các loại còn lại (pdf/png/jpg...) -> gọi API tải file dạng blob (có kèm
 *    Bearer token qua http.js) rồi tự tạo thẻ <a download> để trình duyệt
 *    TỰ ĐỘNG TẢI VỀ MÁY, thay vì mở xem trực tiếp trong tab (và thay vì trỏ
 *    thẳng vào "/uploads/..." — đường dẫn này KHÔNG có proxy ở dev nên trước
 *    đây xem/tải bị lỗi âm thầm, xem dev-notes).
 */
async function openFile(file) {
  if (file.fileType === 'canva') {
    window.open(file.fileUrl, '_blank', 'noopener')
    return
  }

  downloadError.value = ''
  downloadingFileId.value = file.id
  try {
    const response = await lessonApi.downloadFile(lesson.value.id, file.id)
    const blobUrl = window.URL.createObjectURL(new Blob([response.data]))

    const link = document.createElement('a')
    link.href = blobUrl
    link.download = file.fileName || 'tai-lieu'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)

    // Giải phóng bộ nhớ blob sau khi trình duyệt đã kịp bắt đầu tải.
    window.URL.revokeObjectURL(blobUrl)
  } catch (e) {
    downloadError.value = 'Không tải được file. Vui lòng thử lại.'
  } finally {
    downloadingFileId.value = null
  }
}

async function load() {
  loading.value = true
  error.value = ''

  try {
    const { data } = await lessonApi.detail(route.params.id)
    lesson.value = data
  } catch (e) {
    error.value = 'Không tải được bài giảng.'
  } finally {
    loading.value = false
  }
}

function back() {
  router.back()
}

onMounted(load)
</script>
<template>
  <div class="page">
    <button class="btn-back" @click="back">← Quay lại</button>

    <div v-if="loading" class="loading">Đang tải...</div>

    <div v-else-if="error" class="error">
      {{ error }}
    </div>

    <div v-else-if="lesson">
      <h1>{{ lesson.title }}</h1>

      <div class="info">
        <div>
          <strong>Danh mục:</strong>
          {{ lesson.category }}
        </div>

        <div>
          <strong>Môn học:</strong>
          {{ lesson.subjectName }}
        </div>

        <div>
          <strong>Khối:</strong>
          {{ lesson.gradeLevel }}
        </div>
      </div>

      <div class="description">
        <h3>Mô tả</h3>

        <p>
          {{ lesson.description || 'Không có mô tả.' }}
        </p>
      </div>

      <div class="files">
        <h3>Tài liệu</h3>

        <div v-if="downloadError" class="error">{{ downloadError }}</div>

        <div v-if="lesson.files && lesson.files.length">
          <div class="file-item" v-for="file in lesson.files" :key="file.id">
            <span>
              <span class="file-type">{{ file.fileType === 'canva' ? 'Canva' : 'Tệp' }}</span>
              {{ file.fileName }}
            </span>

            <!-- FIX (2026-07-10): thay <a href> tĩnh bằng nút gọi openFile() —
                 canva mở link mới nhất ở tab mới, file khác tự động tải về máy. -->
            <button
              class="btn-view"
              :disabled="downloadingFileId === file.id"
              @click="openFile(file)"
            >
              {{
                downloadingFileId === file.id
                  ? 'Đang tải…'
                  : file.fileType === 'canva'
                    ? 'Xem'
                    : 'Tải về'
              }}
            </button>
          </div>
        </div>

        <div v-else>Không có tài liệu.</div>
      </div>
    </div>
  </div>
</template>
<style scoped>
.page {
  max-width: 900px;
  margin: auto;
}

.btn-back {
  background: none;
  border: none;
  color: var(--c-primary, #f97316);
  font-size: 14px;
  cursor: pointer;
  padding: 0;
  margin-bottom: 20px;
}

.btn-back:hover {
  text-decoration: underline;
}

.info {
  display: flex;
  gap: 20px;
  margin: 20px 0;
}

.description {
  background: var(--c-surface);

  padding: 20px;

  border-radius: 10px;

  margin-bottom: 25px;
}

.files {
  background: var(--c-surface);

  padding: 20px;

  border-radius: 10px;
}

.file-item {
  display: flex;

  justify-content: space-between;

  padding: 10px 0;

  border-bottom: 1px solid var(--c-border-soft);
}

.file-item:last-child {
  border-bottom: none;
}

.file-type {
  display: inline-block;
  margin-right: 8px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--c-surface-2, rgba(249, 115, 22, 0.12));
  color: var(--c-primary, #f97316);
  font-size: 12px;
  font-weight: 600;
}

.loading {
  text-align: center;

  padding: 60px;
}

.error {
  color: red;
}

/* FIX (2026-07-10): style cho nút "Xem"/"Tải về" thay cho thẻ <a> cũ,
   giữ nguyên hình thức link để không đổi giao diện đã có. */
.btn-view {
  background: none;
  border: none;
  color: var(--c-primary, #f97316);
  cursor: pointer;
  font: inherit;
  padding: 0;
  text-decoration: underline;
}

.btn-view:disabled {
  color: var(--c-text-muted);
  cursor: default;
  text-decoration: none;
}
</style>
