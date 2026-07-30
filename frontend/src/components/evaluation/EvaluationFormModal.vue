<script setup>
/**
 * Modal tạo / sửa đánh giá.
 * Tìm GV theo tên (gõ là tìm ngay) + dropdown + phân trang.
 * Sửa: khóa GV và hiển thị đúng tên giáo viên.
 */
import { reactive, ref, watch, computed } from 'vue'
import { evaluationApi } from '@/api/evaluations'
import { useLatestRequest } from '@/composables/useLatestRequest'
import StarRating from '@/components/evaluation/StarRating.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  mode: { type: String, default: 'create' },
  teachers: { type: Array, default: () => [] },
  /** [{ id, name }] — dropdown lọc trường khi tìm GV (optional). */
  schools: { type: Array, default: () => [] },
  periodPresets: { type: Array, default: () => [] },
  suggestedPeriod: { type: String, default: '' },
  initial: { type: Object, default: null },
  prefill: { type: Object, default: null },
  saving: { type: Boolean, default: false },
  error: { type: String, default: '' },
  lockTeacher: { type: Boolean, default: false },
  schoolScoped: { type: Boolean, default: false },
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
/** Lọc GV theo trường — '' = tất cả trường (không bắt buộc chọn). */
const schoolFilter = ref('')
const teacherOptions = ref([])
const teachersLoading = ref(false)
const pickerOpen = ref(false)
const teacherPage = ref(0)
const teacherTotalPages = ref(0)
const teacherTotal = ref(0)
const PAGE_SIZE = 20
/** Tên GV đã chọn (để hiện đúng khi sửa / prefill). */
const lockedTeacherName = ref('')

let searchTimer = null

/** Khóa chọn GV khi sửa — luôn hiện đúng tên, không hiện bộ lọc trường/CN. */
const teacherLocked = computed(() => props.mode === 'edit')

/** Chỉ hiện dropdown trường khi có >1 lựa chọn thật sự (school portal chỉ có 1). */
const canPickSchool = computed(
  () => !teacherLocked.value && !props.schoolScoped && (props.schools?.length || 0) > 1,
)

const COMMENT_MAX = 300

/**
 * Không gate độ dài nhận xét ở đây: phiếu cũ (thời hạn 1000 ký tự) có thể vượt 300 —
 * nếu khóa nút thì user không thấy lý do; để validate() báo lỗi rõ ràng khi bấm lưu.
 */
const canSubmit = computed(() => {
  return (
    !!form.teacherId &&
    form.score >= 1 &&
    form.score <= 5 &&
    !!(form.periodNote && form.periodNote.trim()) &&
    form.periodNote.trim().length <= 50
  )
})

const selectedTeacher = computed(() => {
  const id = form.teacherId
  if (!id) return null
  return (
    teacherOptions.value.find((t) => t.id === id) ||
    (lockedTeacherName.value
      ? { id, name: lockedTeacherName.value, schoolsLabel: props.initial?.schoolName || '' }
      : null)
  )
})

const summaryText = computed(() => {
  const s = summary.value
  if (!s) return ''
  if (!s.count) return 'Chưa có đánh giá trước đó trong phạm vi của bạn.'
  const avg = s.averageScore != null ? Number(s.averageScore).toFixed(1) : '—'
  return `TB ${avg}/5 · ${s.count} lượt đánh giá`
})

// KHÔNG theo dõi suggestedPeriod: prop này về muộn (sau khi meta load xong) sẽ
// kích hoạt lại watch và reset trắng form user đang nhập. Chỉ reset khi modal
// thực sự mở / đổi phiếu (initial/prefill được gán cùng lúc với open ở parent).
watch(
  () => [props.open, props.initial, props.prefill],
  async () => {
    if (!props.open) return
    Object.keys(errors).forEach((k) => delete errors[k])
    summary.value = null
    teacherQuery.value = ''
    schoolFilter.value = ''
    lockedTeacherName.value = ''
    pickerOpen.value = false
    teacherPage.value = 0

    if (props.mode === 'edit' && props.initial) {
      form.teacherId = props.initial.teacherId
      form.score = props.initial.score ?? 0
      form.comment = props.initial.comment ?? ''
      form.periodNote = props.initial.periodNote ?? props.suggestedPeriod ?? ''
      lockedTeacherName.value = props.initial.teacherName || ''
      teacherQuery.value = props.initial.teacherName || ''
    } else {
      form.teacherId = props.prefill?.teacherId ?? null
      form.score = 0
      form.comment = ''
      form.periodNote = props.prefill?.periodNote || props.suggestedPeriod || ''
      lockedTeacherName.value = props.prefill?.teacherName || ''
      teacherQuery.value = props.prefill?.teacherName || ''
    }

    if (!teacherLocked.value) {
      await loadTeachers({ reset: true })
    }
    if (form.teacherId) {
      if (!teacherQuery.value) {
        const t = teacherOptions.value.find((x) => x.id === form.teacherId)
        if (t) {
          teacherQuery.value = t.name
          lockedTeacherName.value = t.name
        }
      }
      loadSummary(form.teacherId)
    }
  },
  { immediate: true },
)

watch(
  () => form.periodNote,
  () => {
    if (!props.open || teacherLocked.value) return
    teacherPage.value = 0
    loadTeachers({ reset: true })
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

// Đóng modal: dọn debounce đang chờ + vô hiệu request đang bay (không còn request mồ côi).
watch(
  () => props.open,
  (open) => {
    if (!open) {
      clearTimeout(searchTimer)
      teacherReqSeq++
      latestSummary.invalidate()
      dupAsk.open = false
    }
  },
)

function buildTeacherParams(page) {
  return {
    periodNote: form.periodNote || props.suggestedPeriod || undefined,
    keyword: teacherQuery.value?.trim() || undefined,
    schoolId: schoolFilter.value ? Number(schoolFilter.value) : undefined,
    page,
    size: PAGE_SIZE,
  }
}

/** Đổi trường → tìm lại từ trang đầu, mở luôn danh sách gợi ý. */
function onSchoolFilterChange() {
  pickerOpen.value = true
  loadTeachers({ reset: true })
}

function clearSchoolFilter() {
  schoolFilter.value = ''
  onSchoolFilterChange()
}

/** Bỏ response đến muộn (gõ nhanh → request cũ có thể về sau request mới). */
let teacherReqSeq = 0

async function loadTeachers({ reset = false, append = false } = {}) {
  const seq = ++teacherReqSeq
  if (reset) {
    teacherPage.value = 0
    if (!append) teacherOptions.value = []
  }
  teachersLoading.value = true
  try {
    const { data } = await evaluationApi.teachers(buildTeacherParams(teacherPage.value))
    if (seq !== teacherReqSeq) return
    const list = data?.content || []
    teacherOptions.value = append ? [...teacherOptions.value, ...list] : list
    teacherTotalPages.value = data?.totalPages ?? 0
    teacherTotal.value = data?.totalElements ?? list.length
  } catch {
    if (seq !== teacherReqSeq) return
    if (!append) {
      teacherOptions.value = (props.teachers || []).map((t) => ({
        ...t,
        schoolsLabel: t.schoolsLabel || '—',
      }))
    }
  } finally {
    if (seq === teacherReqSeq) teachersLoading.value = false
  }
}

/** Gõ chữ cái nào là tìm ngay (debounce rất ngắn). */
function onTeacherQueryInput() {
  pickerOpen.value = true
  if (selectedTeacher.value && teacherQuery.value !== selectedTeacher.value.name) {
    form.teacherId = null
    lockedTeacherName.value = ''
  }
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => loadTeachers({ reset: true }), 120)
}

function loadMoreTeachers() {
  if (teacherPage.value + 1 >= teacherTotalPages.value) return
  teacherPage.value += 1
  loadTeachers({ append: true })
}

function pickTeacher(t) {
  form.teacherId = t.id
  teacherQuery.value = t.name
  lockedTeacherName.value = t.name
  pickerOpen.value = false
  if (errors.teacherId) delete errors.teacherId
}

function clearTeacher() {
  form.teacherId = null
  teacherQuery.value = ''
  lockedTeacherName.value = ''
  summary.value = null
  pickerOpen.value = true
  loadTeachers({ reset: true })
}

/** Chỉ nhận thống kê của GV chọn SAU CÙNG — đổi GV nhanh không hiện nhầm số liệu GV trước. */
const latestSummary = useLatestRequest()

async function loadSummary(teacherId) {
  summaryLoading.value = true
  await latestSummary(
    () => evaluationApi.teacherSummary(teacherId),
    ({ data }) => {
      summary.value = data
      summaryLoading.value = false
    },
    () => {
      summary.value = null
      summaryLoading.value = false
    },
  )
}

function validate() {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.teacherId) errors.teacherId = 'Chọn giáo viên (tìm theo tên)'
  if (!form.score || form.score < 1 || form.score > 5) errors.score = 'Chọn điểm từ 1 đến 5 sao'
  const period = (form.periodNote || '').trim()
  if (!period) errors.periodNote = 'Kỳ đánh giá bắt buộc'
  else if (period.length > 50) errors.periodNote = 'Kỳ đánh giá tối đa 50 ký tự'
  if (form.comment && form.comment.length > COMMENT_MAX)
    errors.comment = `Nhận xét tối đa ${COMMENT_MAX} ký tự`
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

/** Hỏi xác nhận trùng kỳ NGAY TRONG modal (thay window.confirm của trình duyệt). */
const dupAsk = reactive({ open: false, message: '' })

async function submit() {
  if (!validate()) return
  if (props.mode === 'create') {
    try {
      const { data } = await evaluationApi.duplicateCheck({
        teacherId: Number(form.teacherId),
        periodNote: form.periodNote.trim(),
      })
      if (data.duplicate) {
        dupAsk.message = data.message || 'Đã có đánh giá cùng kỳ. Vẫn lưu thêm?'
        dupAsk.open = true
        return
      }
    } catch {
      /* lỗi check trùng → cứ gửi, BE còn chốt chặn 409 */
    }
  }
  emit('save', buildPayload(false))
}

function cancelDupAsk() {
  dupAsk.open = false
}

function confirmDupAsk() {
  dupAsk.open = false
  emit('save', buildPayload(true))
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
  <!-- Đang lưu thì không cho đóng (backdrop lẫn nút ×) — lỗi lưu sẽ bị nuốt mất nếu modal biến mất -->
  <div v-if="open" class="modal-backdrop" @click.self="!saving && emit('close')">
    <div class="modal" role="dialog" aria-modal="true">
      <div class="modal__head">
        <h2 class="modal__title">
          {{ mode === 'edit' ? 'Sửa đánh giá' : 'Tạo đánh giá giáo viên' }}
        </h2>
        <button type="button" class="modal__x" :disabled="saving" @click="emit('close')">×</button>
      </div>

      <div class="modal__body">
        <p v-if="error" class="msg msg--error">{{ error }}</p>

        <div class="field">
          <span>Giáo viên <em>*</em></span>

          <!-- Chế độ sửa: chỉ hiện tên GV, không chọn lại -->
          <div v-if="teacherLocked" class="teacher-locked">
            <strong>{{ lockedTeacherName || teacherQuery || '—' }}</strong>
          </div>

          <div v-else class="picker">
            <!-- Lọc theo trường (không bắt buộc) — thu hẹp danh sách GV -->
            <div v-if="canPickSchool" class="picker__school">
              <select v-model="schoolFilter" @change="onSchoolFilterChange">
                <option value="">🏫 Tất cả trường</option>
                <option v-for="s in schools" :key="s.id" :value="s.id">{{ s.name }}</option>
              </select>
              <button
                v-if="schoolFilter"
                type="button"
                class="picker__school-clear"
                title="Bỏ lọc trường"
                @click="clearSchoolFilter"
              >
                ×
              </button>
            </div>
            <div class="picker__row">
              <input
                v-model="teacherQuery"
                type="search"
                autocomplete="off"
                placeholder="Nhập tên giáo viên để tìm…"
                @focus="pickerOpen = true"
                @input="onTeacherQueryInput"
              />
              <button
                v-if="form.teacherId"
                type="button"
                class="picker__clear"
                title="Bỏ chọn"
                @click="clearTeacher"
              >
                ×
              </button>
            </div>
            <div v-if="pickerOpen" class="picker__list">
              <div v-if="teachersLoading && !teacherOptions.length" class="picker__empty">
                Đang tải…
              </div>
              <div v-else-if="!teacherOptions.length" class="picker__empty">
                {{
                  schoolScoped
                    ? 'Không có GV phân công tại trường (hoặc không khớp tên).'
                    : schoolFilter
                      ? 'Không có giáo viên khớp tại trường đã chọn.'
                      : 'Không tìm thấy giáo viên khớp.'
                }}
              </div>
              <button
                v-for="t in teacherOptions"
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
                <span v-if="t.schoolsLabel" class="picker__schools">{{ t.schoolsLabel }}</span>
                <span class="picker__meta">{{ optionHint(t) }}</span>
                <span v-if="!t.evaluatedInPeriod" class="pill pill--todo">Cần chấm</span>
                <span v-else class="pill pill--done">Đã chấm</span>
              </button>
              <button
                v-if="teacherPage + 1 < teacherTotalPages"
                type="button"
                class="picker__more"
                :disabled="teachersLoading"
                @click="loadMoreTeachers"
              >
                {{
                  teachersLoading
                    ? 'Đang tải…'
                    : `Tải thêm (${teacherOptions.length}/${teacherTotal})`
                }}
              </button>
            </div>
          </div>
          <small v-if="errors.teacherId" class="field__err">{{ errors.teacherId }}</small>
          <div v-if="form.teacherId && selectedTeacher && !teacherLocked" class="summary-box">
            <div class="summary-box__line">
              <strong>{{ selectedTeacher.name }}</strong>
              <span v-if="selectedTeacher.phone" class="muted"> · {{ selectedTeacher.phone }}</span>
            </div>
            <div v-if="selectedTeacher.schoolsLabel" class="summary-box__line muted">
              {{ selectedTeacher.schoolsLabel }}
            </div>
            <div class="summary-box__line">
              <span v-if="summaryLoading">Đang tải thống kê…</span>
              <span v-else-if="summary">{{ summaryText }}</span>
            </div>
          </div>
          <div v-else-if="form.teacherId && teacherLocked" class="summary-box">
            <div class="summary-box__line">
              <span v-if="summaryLoading">Đang tải thống kê…</span>
              <span v-else-if="summary">{{ summaryText }}</span>
            </div>
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
            autocomplete="off"
          />
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
            :maxlength="COMMENT_MAX"
            placeholder="Nhận xét về chuyên môn, thái độ, quản lý lớp..."
          />
          <small
            class="field__hint"
            :class="{
              'field__hint--warn':
                (form.comment || '').length >= COMMENT_MAX - 30 &&
                (form.comment || '').length <= COMMENT_MAX,
              'field__hint--over': (form.comment || '').length > COMMENT_MAX,
            }"
          >
            {{ (form.comment || '').length }}/{{ COMMENT_MAX }}
          </small>
          <small v-if="errors.comment" class="field__err">{{ errors.comment }}</small>
        </label>
      </div>

      <!-- Xác nhận trùng kỳ ngay trong modal -->
      <div v-if="dupAsk.open" class="dup-ask">
        <p class="dup-ask__msg">⚠️ {{ dupAsk.message }}</p>
        <div class="dup-ask__actions">
          <button type="button" class="btn btn--ghost" @click="cancelDupAsk">Hủy</button>
          <button type="button" class="btn" @click="confirmDupAsk">Vẫn lưu thêm</button>
        </div>
      </div>

      <div v-else class="modal__foot">
        <button type="button" class="btn btn--ghost" :disabled="saving" @click="emit('close')">
          Hủy
        </button>
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
  width: min(560px, 100%);
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
  align-self: flex-end;
  font-variant-numeric: tabular-nums;
}
.field__hint--warn {
  color: var(--c-primary, #f97316);
  font-weight: 600;
}
.field__hint--over {
  color: var(--c-danger, #ef4444);
  font-weight: 700;
}
.teacher-locked {
  border: 1px solid var(--c-border, #e6ebf2);
  border-radius: 8px;
  padding: 0.6rem 0.75rem;
  background: var(--c-surface-2, #f1f5f9);
  color: var(--c-text);
  font-size: 0.95rem;
}
.picker {
  position: relative;
}
.picker__school {
  position: relative;
  display: flex;
  align-items: center;
  margin-bottom: 0.35rem;
}
.picker__school select {
  width: 100%;
  border: 1px solid var(--c-input-border, #d5dde8);
  border-radius: 8px;
  padding: 0.45rem 2rem 0.45rem 0.7rem;
  background: var(--c-surface-2, #f1f5f9);
  color: var(--c-text);
  font: inherit;
  font-size: 0.85rem;
  cursor: pointer;
  appearance: none;
  -webkit-appearance: none;
}
.picker__school-clear {
  position: absolute;
  right: 0.45rem;
  border: 0;
  background: var(--c-surface);
  border-radius: 50%;
  width: 1.3rem;
  height: 1.3rem;
  cursor: pointer;
  color: var(--c-text-muted);
  line-height: 1;
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
  max-height: 260px;
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
  grid-template-rows: auto auto auto;
  gap: 0.05rem 0.5rem;
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
.picker__schools {
  grid-column: 1;
  font-size: 0.78rem;
  color: var(--c-primary, #f97316);
  font-weight: 600;
}
.picker__meta {
  grid-column: 1;
  font-size: 0.72rem;
  color: var(--c-text-muted);
}
.picker__item .pill {
  grid-column: 2;
  grid-row: 1 / span 3;
  align-self: center;
}
.picker__empty,
.picker__more {
  padding: 0.75rem;
  text-align: center;
  color: var(--c-text-muted);
  font-size: 0.85rem;
}
.picker__more {
  width: 100%;
  border: 0;
  border-top: 1px solid var(--c-border-soft);
  background: var(--c-surface-2);
  cursor: pointer;
  font: inherit;
  font-weight: 600;
  color: var(--c-accent, #2563eb);
}
.picker__more:disabled {
  opacity: 0.6;
  cursor: wait;
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
  padding: 0.5rem 0.7rem;
  border-radius: 8px;
  background: var(--c-surface-2, #f1f5f9);
  font-size: 0.82rem;
}
.summary-box__line {
  margin: 0.1rem 0;
}
.muted {
  color: var(--c-text-muted);
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
.dup-ask {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 0.85rem 1.15rem;
  border-top: 1px solid var(--c-border-soft, #f1f5f9);
  background: #fffbeb;
}
.dup-ask__msg {
  margin: 0;
  color: #92400e;
  font-size: 0.88rem;
}
.dup-ask__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}
</style>
