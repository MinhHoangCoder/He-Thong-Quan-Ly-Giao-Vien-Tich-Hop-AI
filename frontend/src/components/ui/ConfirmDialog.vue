<script setup>
import { computed, ref } from 'vue'

/**
 * Hộp thoại xác nhận dùng chung.
 *
 * Trước đây mỗi trang tự làm một kiểu: Trường/Lớp có modal riêng, Phân công dùng
 * window.confirm(), Bảng lương dùng alert(). Ba kiểu hộp thoại cho cùng một việc là ba
 * lần phải đọc lại xem nút nào là nút nguy hiểm.
 *
 * Hai thứ window.confirm() không làm được, mà xóa dữ liệu thật thì cần cả hai:
 *  - liệt kê những gì đang CHẶN (còn 3 lớp, 2 phân công…) kèm link đi thẳng tới đó;
 *  - bắt gõ lại tên bản ghi trước khi cho bấm, dành cho thao tác không lùi được.
 *
 * Dùng:
 *   <ConfirmDialog
 *     v-if="target"
 *     title="Xóa kỳ nghỉ?"
 *     :name="target.name"
 *     :blockers="blockers"
 *     danger
 *     @confirm="doDelete"
 *     @cancel="target = null"
 *   >
 *     Câu mô tả hậu quả.
 *   </ConfirmDialog>
 */
const props = defineProps({
  title: { type: String, required: true },
  /** Tên bản ghi — hiện đậm trong hộp thoại, và là chuỗi phải gõ lại khi requireTyping. */
  name: { type: String, default: '' },
  confirmText: { type: String, default: 'Xóa' },
  cancelText: { type: String, default: 'Hủy' },
  /** Nút xác nhận màu đỏ (thao tác phá hủy) hay màu chính (thao tác thường). */
  danger: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
  error: { type: String, default: '' },
  /**
   * Những thứ đang chặn thao tác. Mỗi mục: { label, to? } — có `to` thì thành nút bấm
   * được, đi thẳng tới chỗ phải xử lý. Chỉ ra vấn đề mà không chỉ đường sửa là vô ích.
   */
  blockers: { type: Array, default: () => [] },
  /** Bắt gõ lại `name` mới cho bấm — dành cho thao tác không lùi lại được. */
  requireTyping: { type: Boolean, default: false },
})
const emit = defineEmits(['confirm', 'cancel', 'goto'])

const typed = ref('')
const canConfirm = computed(() => {
  if (props.busy) return false
  if (!props.requireTyping) return true
  return typed.value.trim().toLowerCase() === props.name.trim().toLowerCase()
})
</script>

<template>
  <div class="modal-overlay" @click.self="emit('cancel')">
    <div class="modal-box modal-sm">
      <h3>{{ title }}</h3>

      <p v-if="name" class="cfm__name">{{ name }}</p>

      <div class="cfm__body">
        <slot />
      </div>

      <!-- Những thứ đang chặn: kể hết một lần, kèm đường đi tới chỗ phải xử lý -->
      <ul v-if="blockers.length" class="cfm__blockers">
        <li v-for="(b, i) in blockers" :key="i">
          <button v-if="b.to" class="cfm__link" @click="emit('goto', b)">{{ b.label }} →</button>
          <span v-else>{{ b.label }}</span>
        </li>
      </ul>

      <div v-if="requireTyping" class="cfm__typing">
        <label
          >Gõ lại <strong>{{ name }}</strong> để xác nhận:</label
        >
        <input
          v-model="typed"
          type="text"
          :placeholder="name"
          @keyup.enter="canConfirm && emit('confirm')"
        />
      </div>

      <p v-if="error" class="error-msg">{{ error }}</p>

      <div class="modal-actions">
        <button class="btn btn-outline" :disabled="busy" @click="emit('cancel')">
          {{ cancelText }}
        </button>
        <button
          class="btn"
          :class="danger ? 'btn-danger' : 'btn-primary'"
          :disabled="!canConfirm"
          @click="emit('confirm')"
        >
          {{ busy ? 'Đang xử lý…' : confirmText }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cfm__name {
  margin: 0 0 0.6rem;
  font-weight: 700;
  color: var(--c-text);
}
.cfm__body {
  font-size: 0.9rem;
  color: var(--c-text-muted);
  line-height: 1.5;
}
.cfm__blockers {
  margin: 0.8rem 0 0;
  padding-left: 1.1rem;
  font-size: 0.86rem;
  color: var(--c-text);
}
.cfm__blockers li {
  margin-bottom: 0.3rem;
}
.cfm__link {
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  color: var(--c-primary);
  cursor: pointer;
  text-decoration: underline;
}
.cfm__typing {
  margin-top: 0.9rem;
}
.cfm__typing label {
  display: block;
  font-size: 0.84rem;
  color: var(--c-text-muted);
  margin-bottom: 0.35rem;
}
.cfm__typing input {
  width: 100%;
  padding: 0.45rem 0.7rem;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  font-size: 0.88rem;
  background: var(--c-surface);
  color: var(--c-text);
}
</style>
