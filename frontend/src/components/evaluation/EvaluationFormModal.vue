<script setup>
/**
 * Modal tạo / sửa đánh giá.
 * - Chọn GV: ô tìm kiếm + danh sách (hữu ích khi nhiều GV)
 * - Ưu tiên hiển thị chưa chấm kỳ; badge đã chấm / TB
 * - Kỳ bắt buộc; trùng kỳ → confirm
 */
import { reactive, ref, watch, computed } from 'vue'
import { evaluationApi } from '@/api/evaluations'
import StarRating from '@/components/evaluation/StarRating.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  mode: { type: String, default: 'create' },
  /** Fallback list nếu chưa load theo period */
  teachers: { type: Array, default: () => [] },
  periodPresets: { type: Array, default: () => [] },
  suggestedPeriod: { type: String, default: '' },
  initial: { type: Object, default: null },
  /** Prefill khi tạo từ panel “chưa đánh giá” */
  prefill: { type: Object, default: null },
  saving: { type: Boolean, default: false },
  error: { type: String, default: '' },
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
const summary = ref(null)
const summaryLoading = ref(false)

const teacherQuery = ref('')
const teacherOptions = ref([])
const teachersLoading = ref(false)
const pickerOpen = ref(false)
let searchTimer = null

const canSubmit = computed(() => {
  return (
    !!form.teacherId &&
    form.score >= 1 &&
    form.score <= 5 &&
    !!(form.periodNote && form.periodNote.trim()) &&
    (!form.comment || form.comment.length <= 1000) &&
    form.periodNote.trim().length <= 50
  )
})

const selectedTeacher = computed(() => {
  const id = form.teacherId
  if (!id) return null
  return (
    teacherOptions.value.find((t) => t.id === id) ||
    props.teachers.find((t) => t.id === id) ||
    null
  )
})

const summaryText = computed(() => {
  const s = summary.value
  if (!s) return ''
  if (!s.count) return 'Chưa có đánh giá trước đó trong phạm vi của bạn.'
  const avg = s.averageScore != null ? Number(s.averageScore).toFixed(1) : '—'
  return `TB ${avg}/5 · ${s.count} lượt đánh giá`
})

const filteredLocal = computed(() => {
  // Hiển thị list đã load (BE đã sort + filter keyword khi gõ)
  return teacherOptions.value
})

watch(
  () => [props.open, props.initial, props.prefill, props.suggestedPeriod],
  async () => {
    if (!props.open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    summary.value = null
    teacherQuery.value = ''
    pickerOpen.value = false

    if (props.mode === 'edit' && props.initial) {
      form.teacherId = props.initial.teacherId
      form.score = props.initial.score ?? 0
      form.comment = props.initial.comment ?? ''
      form.periodNote = props.initial.periodNote ?? props.suggestedPeriod ?? ''
    } else {
      form.teacherId = props.prefill?.teacherId ?? null
      form.score = 0
      form.comment = ''
      form.periodNote = props.prefill?.periodNote || props.suggestedPeriod || ''
    }
    await loadTeachers('')
    if (form.teacherId) {
      const t = teacherOptions.value.find((x) => x.id === form.teacherId)
      if (t) teacherQuery.value = t.name
      loadSummary(form.teacherId)
    }
  },
  { immediate: true },
)

watch(
  () => form.periodNote,
  () => {
    if (!props.open) return
    // Đổi kỳ → refresh cờ “đã chấm kỳ” trên list
    loadTeachers(teacherQuery.value)
  },
)

watch(
  () => form.teacherId,
  (id) => {
    if (!props.open) return
    summary.value = null
    if (id) loadSummary(id)
  },
)

async function loadTeachers(keyword) {
  teachersLoading.value = true
  try {
    const { data } = await evaluationApi.teachers({
      periodNote: form.periodNote || props.suggestedPeriod || undefined,
      keyword: keyword?.trim() || undefined,
    })
    teacherOptions.value = data || []
  } catch {
    // fallback props
    teacherOptions.value = (props.teachers || []).map((t) => ({
      id: t.id,
      name: t.name,
      status: t.status,
      totalCount: t.totalCount ?? 0,
      averageScore: t.averageScore ?? null,
      evaluatedInPeriod: t.evaluatedInPeriod ?? false,
      evalsInPeriod: t.evalsInPeriod ?? 0,
    }))
    if (keyword?.trim()) {
      const kw = keyword.trim().toLowerCase()
      teacherOptions.value = teacherOptions.value.filter((t) =>
        (t.name || '').toLowerCase().includes(kw),
      )
    }
  } finally {
    teachersLoading.value = false
  }
}

function onTeacherQueryInput() {
  pickerOpen.value = true
  // Gõ tên → clear selection nếu lệch
  if (selectedTeacher.value && teacherQuery.value !== selectedTeacher.value.name) {
    form.teacherId = null
  }
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => loadTeachers(teacherQuery.value), 250)
}

function pickTeacher(t) {
  form.teacherId = t.id
  teacherQuery.value = t.name
  pickerOpen.value = false
  if (errors.teacherId) delete errors.teacherId
}

function clearTeacher() {
  form.teacherId = null
  teacherQuery.value = ''
  summary.value = null
  pickerOpen.value = true
  loadTeachers('')
}

async function loadSummary(teacherId) {
  summaryLoading.value = true
  try {
    const { data } = await evaluationApi.teacherSummary(teacherId)
    summary.value = data
  } catch {
    summary.value = null
  } finally {
    summaryLoading.value = false
  }
}

function validate() {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.teacherId) errors.teacherId = 'Chọn giáo viên (gõ tên để tìm)'
  if (!form.score || form.score < 1 || form.score > 5) errors.score = 'Chọn điểm từ 1 đến 5 sao'
  const period = (form.periodNote || '').trim()
  if (!period) errors.periodNote = 'Kỳ đánh giá bắt buộc'
  else if (period.length > 50) errors.periodNote = 'Kỳ đánh giá tối đa 50 ký tự'
  if (form.comment && form.comment.length > 1000) errors.comment = 'Nhận xét tối đa 1000 ký tự'
  return Object.keys(errors).length === 0
}

function buildPayload(forceDuplicate = false) {
  return {
    teacherId: Number(form.teacherId),
    score: Number(form.score),
    comment: form.comment?.trim() || null,
    periodNote: form.periodNote.trim(),
    forceDuplicate: forceDuplicate || undefined,
  }
}

async function submit() {
  if (!validate()) return
  if (props.mode === 'create') {
    try {
      const { data } = await evaluationApi.duplicateCheck({
        teacherId: Number(form.teacherId),
        periodNote: form.periodNote.trim(),
      })
      if (data.duplicate) {
        const ok = window.confirm(data.message || 'Đã có đánh giá cùng kỳ. Vẫn lưu?')
        if (!ok) return
        emit('save', buildPayload(true))
        return
      }
    } catch {
      /* BE 409 fallback */
    }
  }
  emit('save', buildPayload(false))
}

function pickPreset(p) {
  form.periodNote = p
}

function optionHint(t) {
  const parts = []
  if (t.averageScore != null) parts.push(`TB ${Number(t.averageScore).toFixed(1)}`)
  if (t.totalCount) parts.push(`${t.totalCount} lượt`)
  if (t.evaluatedInPeriod) parts.push(`đã chấm kỳ (${t.evalsInPeriod})`)
  else parts.push('chưa chấm kỳ')
  return parts.join(' · ')
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

        <!-- Teacher searchable picker -->
        <div class="field">
          <span>Giáo viên <em>*</em></span>
          <div class="picker" :class="{ 'picker--locked': lockTeacher && mode === 'edit' }">
            <div class="picker__row">
              <input
                v-model="teacherQuery"
                type="search"
                autocomplete="off"
                placeholder="Gõ tên để tìm trong danh sách…"
                :disabled="lockTeacher && mode === 'edit'"
                @focus="pickerOpen = true"
                @input="onTeacherQueryInput"
              />
              <button
                v-if="form.teacherId && !(lockTeacher && mode === 'edit')"
                type="button"
                class="picker__clear"
                title="Bỏ chọn"
                @click="clearTeacher"
              >
                ×
              </button>
            </div>
            <div v-if="pickerOpen && !(lockTeacher && mode === 'edit')" class="picker__list">
              <div v-if="teachersLoading" class="picker__empty">Đang tải…</div>
              <div v-else-if="filteredLocal.length === 0" class="picker__empty">
                Không tìm thấy giáo viên
              </div>
              <button
                v-for="t in filteredLocal"
                :key="t.id"
                type="button"
                class="picker__item"
                :class="{
                  'is-selected': t.id === form.teacherId,
                  'is-done': t.evaluatedInPeriod,
                }"
                @click="pickTeacher(t)"
              >
                <span class="picker__name">{{ t.name }}</span>
                <span class="picker__meta">{{ optionHint(t) }}</span>
                <span v-if="!t.evaluatedInPeriod" class="pill pill--todo">Cần chấm</span>
                <span v-else class="pill pill--done">Đã chấm</span>
              </button>
            </div>
          </div>
          <small v-if="errors.teacherId" class="field__err">{{ errors.teacherId }}</small>
          <div v-if="form.teacherId" class="summary-box">
            <span v-if="summaryLoading">Đang tải thống kê…</span>
            <span v-else-if="summary">{{ summaryText }}</span>
          </div>
        </div>

        <label class="field">
          <span>Điểm (1–5) <em>*</em></span>
          <div class="score-row">
            <StarRating v-model="form.score" interactive :size="28" />
            <strong v-if="form.score" class="score-num">{{ form.score }}/5</strong>
          </div>
          <small v-if="errors.score" class="field__err">{{ errors.score }}</small>
        </label>

        <label class="field">
          <span>Kỳ đánh giá <em>*</em></span>
          <input
            v-model="form.periodNote"
            maxlength="50"
            placeholder="vd: HK2 2025-2026"
            list="period-presets"
          />
          <datalist id="period-presets">
            <option v-for="p in periodPresets" :key="p" :value="p" />
          </datalist>
          <div v-if="periodPresets.length" class="chips">
            <button
              v-for="p in periodPresets"
              :key="p"
              type="button"
              class="chip"
              :class="{ 'chip--on': form.periodNote === p }"
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
        <button type="button" class="btn" :disabled="saving || !canSubmit" @click="submit">
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
  width: min(540px, 100%);
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
.field textarea,
.picker__row input {
  border: 1px solid var(--c-input-border, #d5dde8);
  border-radius: 8px;
  padding: 0.55rem 0.7rem;
  background: var(--c-surface, #fff);
  color: var(--c-text);
  font: inherit;
  width: 100%;
  box-sizing: border-box;
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
}
.picker {
  position: relative;
}
.picker__row {
  position: relative;
  display: flex;
  align-items: center;
}
.picker__clear {
  position: absolute;
  right: 0.45rem;
  border: 0;
  background: var(--c-surface-2);
  border-radius: 50%;
  width: 1.4rem;
  height: 1.4rem;
  cursor: pointer;
  color: var(--c-text-muted);
  line-height: 1;
}
.picker__list {
  margin-top: 0.35rem;
  max-height: 220px;
  overflow: auto;
  border: 1px solid var(--c-border);
  border-radius: 10px;
  background: var(--c-surface);
  box-shadow: var(--a-shadow, 0 4px 16px rgba(15, 40, 80, 0.07));
}
.picker__item {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr auto;
  grid-template-rows: auto auto;
  gap: 0.1rem 0.5rem;
  text-align: left;
  border: 0;
  border-bottom: 1px solid var(--c-border-soft);
  background: transparent;
  padding: 0.55rem 0.7rem;
  cursor: pointer;
  color: var(--c-text);
  font: inherit;
}
.picker__item:last-child {
  border-bottom: 0;
}
.picker__item:hover,
.picker__item.is-selected {
  background: var(--c-surface-2);
}
.picker__name {
  font-weight: 600;
  grid-column: 1;
}
.picker__meta {
  grid-column: 1;
  font-size: 0.75rem;
  color: var(--c-text-muted);
}
.picker__item .pill {
  grid-column: 2;
  grid-row: 1 / span 2;
  align-self: center;
}
.picker__empty {
  padding: 0.85rem;
  text-align: center;
  color: var(--c-text-muted);
  font-size: 0.88rem;
}
.pill {
  font-size: 0.7rem;
  font-weight: 700;
  padding: 0.15rem 0.45rem;
  border-radius: 999px;
  white-space: nowrap;
}
.pill--todo {
  background: #fff7ed;
  color: #c2410c;
}
.pill--done {
  background: #ecfdf5;
  color: #047857;
}
.summary-box {
  margin-top: 0.15rem;
  padding: 0.45rem 0.65rem;
  border-radius: 8px;
  background: var(--c-surface-2, #f1f5f9);
  color: var(--c-text-muted);
  font-size: 0.82rem;
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
.chip:hover,
.chip--on {
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
  opacity: 0.55;
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
