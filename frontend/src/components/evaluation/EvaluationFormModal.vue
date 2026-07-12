<script setup>
/**
 * Modal tạo / sửa đánh giá giáo viên.
 * Props: open, mode ('create'|'edit'), teachers[], periodPresets[], initial
 */
import { reactive, watch } from 'vue'
import StarRating from '@/components/evaluation/StarRating.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  mode: { type: String, default: 'create' }, // create | edit
  teachers: { type: Array, default: () => [] },
  periodPresets: { type: Array, default: () => [] },
  initial: { type: Object, default: null },
  saving: { type: Boolean, default: false },
  error: { type: String, default: '' },
  /** School không cho đổi GV khi sửa. */
  lockTeacher: { type: Boolean, default: false },
})

const emit = defineEmits(['close', 'save'])

const form = reactive({
  teacherId: null,
  score: 0,
  comment: '',
  periodNote: '',
})
const errors = reactive({})

watch(
  () => [props.open, props.initial],
  () => {
    if (!props.open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    if (props.mode === 'edit' && props.initial) {
      form.teacherId = props.initial.teacherId
      form.score = props.initial.score ?? 0
      form.comment = props.initial.comment ?? ''
      form.periodNote = props.initial.periodNote ?? ''
    } else {
      form.teacherId = null
      form.score = 0
      form.comment = ''
      form.periodNote = ''
    }
  },
  { immediate: true },
)

function validate() {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.teacherId) errors.teacherId = 'Chọn giáo viên'
  if (!form.score || form.score < 1 || form.score > 5) errors.score = 'Chọn điểm từ 1 đến 5 sao'
  if (form.comment && form.comment.length > 1000) errors.comment = 'Nhận xét tối đa 1000 ký tự'
  if (form.periodNote && form.periodNote.length > 50) errors.periodNote = 'Kỳ đánh giá tối đa 50 ký tự'
  return Object.keys(errors).length === 0
}

function submit() {
  if (!validate()) return
  emit('save', {
    teacherId: Number(form.teacherId),
    score: Number(form.score),
    comment: form.comment?.trim() || null,
    periodNote: form.periodNote?.trim() || null,
  })
}

function pickPreset(p) {
  form.periodNote = p
}
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="emit('close')">
    <div class="modal" role="dialog" aria-modal="true">
      <div class="modal__head">
        <h2 class="modal__title">{{ mode === 'edit' ? 'Sửa đánh giá' : 'Tạo đánh giá giáo viên' }}</h2>
        <button type="button" class="modal__x" @click="emit('close')">×</button>
      </div>

      <div class="modal__body">
        <p v-if="error" class="msg msg--error">{{ error }}</p>

        <label class="field">
          <span>Giáo viên <em>*</em></span>
          <select v-model="form.teacherId" :disabled="lockTeacher && mode === 'edit'">
            <option :value="null" disabled>— Chọn giáo viên —</option>
            <option v-for="t in teachers" :key="t.id" :value="t.id">{{ t.name }}</option>
          </select>
          <small v-if="errors.teacherId" class="field__err">{{ errors.teacherId }}</small>
        </label>

        <label class="field">
          <span>Điểm (1–5) <em>*</em></span>
          <div class="score-row">
            <StarRating v-model="form.score" interactive :size="28" />
            <strong v-if="form.score" class="score-num">{{ form.score }}/5</strong>
          </div>
          <small v-if="errors.score" class="field__err">{{ errors.score }}</small>
        </label>

        <label class="field">
          <span>Kỳ đánh giá</span>
          <input v-model="form.periodNote" maxlength="50" placeholder="vd: HK2 2025-2026" list="period-presets" />
          <datalist id="period-presets">
            <option v-for="p in periodPresets" :key="p" :value="p" />
          </datalist>
          <div v-if="periodPresets.length" class="chips">
            <button
              v-for="p in periodPresets"
              :key="p"
              type="button"
              class="chip"
              @click="pickPreset(p)"
            >
              {{ p }}
            </button>
          </div>
          <small v-if="errors.periodNote" class="field__err">{{ errors.periodNote }}</small>
        </label>

        <label class="field">
          <span>Nhận xét</span>
          <textarea
            v-model="form.comment"
            rows="4"
            maxlength="1000"
            placeholder="Nhận xét về chuyên môn, thái độ, quản lý lớp..."
          />
          <small class="field__hint">{{ (form.comment || '').length }}/1000</small>
          <small v-if="errors.comment" class="field__err">{{ errors.comment }}</small>
        </label>
      </div>

      <div class="modal__foot">
        <button type="button" class="btn btn--ghost" :disabled="saving" @click="emit('close')">Hủy</button>
        <button type="button" class="btn" :disabled="saving" @click="submit">
          {{ saving ? 'Đang lưu…' : mode === 'edit' ? 'Cập nhật' : 'Gửi đánh giá' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 1rem;
}
.modal {
  width: min(520px, 100%);
  background: var(--c-surface, #fff);
  border-radius: 14px;
  box-shadow: var(--a-shadow-lg, 0 14px 32px rgba(15, 40, 80, 0.14));
  border: 1px solid var(--c-border, #e6ebf2);
  display: flex;
  flex-direction: column;
  max-height: 90vh;
}
.modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.15rem;
  border-bottom: 1px solid var(--c-border-soft, #f1f5f9);
}
.modal__title {
  margin: 0;
  font-size: 1.05rem;
  color: var(--c-text);
}
.modal__x {
  border: 0;
  background: transparent;
  font-size: 1.4rem;
  line-height: 1;
  color: var(--c-text-muted);
  cursor: pointer;
}
.modal__body {
  padding: 1rem 1.15rem;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}
.modal__foot {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  padding: 0.85rem 1.15rem;
  border-top: 1px solid var(--c-border-soft, #f1f5f9);
}
.field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.9rem;
  color: var(--c-text);
}
.field span em {
  color: var(--c-danger, #ef4444);
  font-style: normal;
}
.field select,
.field input,
.field textarea {
  border: 1px solid var(--c-input-border, #d5dde8);
  border-radius: 8px;
  padding: 0.55rem 0.7rem;
  background: var(--c-surface, #fff);
  color: var(--c-text);
  font: inherit;
}
.field textarea {
  resize: vertical;
  min-height: 96px;
}
.field__err {
  color: var(--c-danger, #ef4444);
  font-size: 0.8rem;
}
.field__hint {
  color: var(--c-text-muted);
  font-size: 0.78rem;
  text-align: right;
}
.score-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.score-num {
  color: var(--c-primary, #f97316);
  font-size: 1rem;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}
.chip {
  border: 1px solid var(--c-border, #e6ebf2);
  background: var(--c-surface-2, #f1f5f9);
  color: var(--c-text-muted);
  border-radius: 999px;
  padding: 0.2rem 0.55rem;
  font-size: 0.75rem;
  cursor: pointer;
}
.chip:hover {
  border-color: var(--c-primary, #f97316);
  color: var(--c-primary, #f97316);
}
.btn {
  border: 0;
  border-radius: 8px;
  padding: 0.5rem 1rem;
  background: var(--grad-primary, linear-gradient(135deg, #fb923c, #f97316));
  color: #fff;
  font-weight: 600;
  cursor: pointer;
  font: inherit;
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn--ghost {
  background: transparent;
  color: var(--c-text-muted);
  border: 1px solid var(--c-border, #e6ebf2);
}
.msg--error {
  margin: 0;
  padding: 0.55rem 0.7rem;
  border-radius: 8px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 0.88rem;
}
</style>
