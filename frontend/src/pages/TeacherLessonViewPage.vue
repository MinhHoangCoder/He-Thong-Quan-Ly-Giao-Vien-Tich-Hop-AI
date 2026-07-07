<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { lessonApi } from '@/api/lessons'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const error = ref('')

const lesson = ref(null)

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

        <div v-if="lesson.files && lesson.files.length">
          <div class="file-item" v-for="file in lesson.files" :key="file.id">
            <span> 📄 {{ file.fileName }} </span>

            <a :href="file.fileUrl" target="_blank"> Xem </a>
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
  margin-bottom: 20px;
}

.info {
  display: flex;
  gap: 20px;
  margin: 20px 0;
}

.description {
  background: #fff;

  padding: 20px;

  border-radius: 10px;

  margin-bottom: 25px;
}

.files {
  background: #fff;

  padding: 20px;

  border-radius: 10px;
}

.file-item {
  display: flex;

  justify-content: space-between;

  padding: 10px 0;

  border-bottom: 1px solid #eee;
}

.file-item:last-child {
  border-bottom: none;
}

.loading {
  text-align: center;

  padding: 60px;
}

.error {
  color: red;
}
</style>
