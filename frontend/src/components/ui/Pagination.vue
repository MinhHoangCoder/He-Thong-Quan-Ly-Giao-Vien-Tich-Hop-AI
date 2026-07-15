<script setup>
/**
 * Cụm phân trang dùng chung: « ‹ 1 2 3 4 5 › »
 * Dùng cho các trang có bảng dữ liệu tải sẵn rồi cắt trang phía client
 * (Phân công / Chấm công / Bảng lương). Bám theo phong cách phân trang của trang
 * Kho bài giảng, nhưng BỎ ô nhập "Trang" và nút "Đi".
 *
 * Dùng: <Pagination v-model="page" :total-pages="totalPages" />
 * (page là chỉ số trang 0-based)
 */
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Number, required: true }, // trang hiện tại (0-based)
  totalPages: { type: Number, required: true },
})
const emit = defineEmits(['update:modelValue'])

// Cửa sổ tối đa 5 số trang, canh quanh trang hiện tại (giống trang Bài giảng).
const visiblePages = computed(() => {
  const total = props.totalPages
  const current = props.modelValue + 1

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
  for (let i = start; i <= end; i++) arr.push(i)
  return arr
})

function goPage(index) {
  if (index < 0 || index >= props.totalPages) return
  if (index === props.modelValue) return
  emit('update:modelValue', index)
}
</script>

<template>
  <div v-if="totalPages > 1" class="pagination">
    <button class="pg-btn" :disabled="modelValue === 0" @click="goPage(0)">«</button>
    <button class="pg-btn" :disabled="modelValue === 0" @click="goPage(modelValue - 1)">‹</button>

    <button
      v-for="p in visiblePages"
      :key="p"
      class="pg-btn"
      :class="{ 'pg-btn--active': modelValue === p - 1 }"
      @click="goPage(p - 1)"
    >
      {{ p }}
    </button>

    <button class="pg-btn" :disabled="modelValue === totalPages - 1" @click="goPage(modelValue + 1)">
      ›
    </button>
    <button class="pg-btn" :disabled="modelValue === totalPages - 1" @click="goPage(totalPages - 1)">
      »
    </button>
  </div>
</template>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 20px;
  flex-wrap: wrap;
}
.pg-btn {
  min-width: 38px;
  height: 38px;
  padding: 0 10px;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  background: var(--c-surface);
  color: var(--c-text);
  font-size: 0.9rem;
  cursor: pointer;
  transition: 0.2s;
}
.pg-btn:hover:not(:disabled) {
  background: var(--c-primary);
  border-color: var(--c-primary);
  color: #fff;
}
.pg-btn--active {
  background: var(--c-primary);
  border-color: var(--c-primary);
  color: #fff;
}
.pg-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
