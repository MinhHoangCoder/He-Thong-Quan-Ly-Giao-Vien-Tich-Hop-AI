<script setup>
/**
 * Ô CHỌN NGÀY tiếng Việt, hiển thị dd/mm/yyyy.
 *
 * VÌ SAO KHÔNG DÙNG <input type="date">: lịch bung ra là của TRÌNH DUYỆT — ngôn ngữ và
 * định dạng lấy từ cài đặt Chrome/Edge của từng máy, KHÔNG theo lang="vi" của trang.
 * Máy cài trình duyệt tiếng Anh luôn thấy "September 2026" và mm/dd/yyyy, không có cách
 * nào ép từ phía web. Nên tự dựng lịch.
 *
 * v-model vẫn là chuỗi ISO "yyyy-MM-dd" y hệt <input type="date"> → các trang gọi API
 * không phải đổi gì; chuỗi rỗng = chưa chọn.
 *
 * Popup dùng Teleport + position: fixed vì .modal-box có overflow-y: auto — để lịch nằm
 * trong cây DOM của modal thì nó bị cắt mất.
 *
 * Dùng: <DateField v-model="form.startDate" />
 */
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  /** Ngày dạng ISO "yyyy-MM-dd" ('' = chưa chọn). */
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: 'dd/mm/yyyy' },
  disabled: { type: Boolean, default: false },
  /** Tô viền đỏ khi trang báo lỗi trường này. */
  invalid: { type: Boolean, default: false },
  /**
   * Ngày sớm nhất được chọn, dạng ISO "yyyy-MM-dd" ('' = không giới hạn).
   * Chỉ khóa trong LỊCH; gõ tay vẫn nhận để trang gọi tự quyết cách báo lỗi.
   */
  min: { type: String, default: '' },
  /** Ngày muộn nhất được chọn, cùng quy ước với {@link min}. */
  max: { type: String, default: '' },
})
const emit = defineEmits(['update:modelValue'])

const WEEKDAYS = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'] // tuần bắt đầu từ Thứ 2
const MONTHS = Array.from({ length: 12 }, (_, i) => `Tháng ${i + 1}`)
const YEAR_MIN = 1940 // đủ lùi cho ô "Ngày sinh"
const POPUP_WIDTH = 288

const pad = (n) => String(n).padStart(2, '0')
const toIso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const TODAY = new Date()
const TODAY_ISO = toIso(TODAY)

/** "2026-09-01" → "01/09/2026" ('' nếu chưa có/không đúng dạng). */
function isoToText(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso ?? '')
  return m ? `${m[3]}/${m[2]}/${m[1]}` : ''
}

/** "1/9/2026" → "2026-09-01"; null nếu KHÔNG phải ngày có thật (31/02 bị loại). */
function textToIso(text) {
  const m = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec((text ?? '').trim())
  if (!m) return null
  const day = Number(m[1])
  const month = Number(m[2])
  const year = Number(m[3])
  const d = new Date(year, month - 1, day)
  // new Date(2026, 1, 31) tự trôi sang 03/03 → so lại để bắt ngày không tồn tại.
  if (d.getFullYear() !== year || d.getMonth() !== month - 1 || d.getDate() !== day) return null
  return `${year}-${pad(month)}-${pad(day)}`
}

const text = ref(isoToText(props.modelValue))
// Chỉ nạp lại chữ khi giá trị ngoài THỰC SỰ khác thứ đang gõ — tránh nhảy con trỏ giữa chừng.
watch(
  () => props.modelValue,
  (v) => {
    if (textToIso(text.value) !== (v || null)) text.value = isoToText(v)
  },
)

/* ── Lịch ── */
const open = ref(false)
const root = ref(null)
const popup = ref(null)
const popupStyle = ref({})
const viewYear = ref(TODAY.getFullYear())
const viewMonth = ref(TODAY.getMonth())

const years = computed(() => {
  const max = Math.max(TODAY.getFullYear() + 10, viewYear.value)
  const min = Math.min(YEAR_MIN, viewYear.value)
  const out = []
  for (let y = max; y >= min; y--) out.push(y)
  return out
})

/** 6 tuần × 7 ngày, luôn bắt đầu từ Thứ 2 của tuần chứa ngày 1. */
const cells = computed(() => {
  const first = new Date(viewYear.value, viewMonth.value, 1)
  const offset = (first.getDay() + 6) % 7 // Chủ nhật (0) là ngày thứ 7 trong tuần Việt
  const out = []
  for (let i = 0; i < 42; i++) {
    const d = new Date(viewYear.value, viewMonth.value, 1 - offset + i)
    const iso = toIso(d)
    out.push({
      iso,
      day: d.getDate(),
      outside: d.getMonth() !== viewMonth.value,
      // So chuỗi ISO trực tiếp được vì cùng định dạng yyyy-MM-dd.
      blocked: (!!props.min && iso < props.min) || (!!props.max && iso > props.max),
    })
  }
  return out
})

function shiftMonth(step) {
  const d = new Date(viewYear.value, viewMonth.value + step, 1)
  viewYear.value = d.getFullYear()
  viewMonth.value = d.getMonth()
}

/** Đặt lịch ở vị trí ô nhập; không đủ chỗ bên dưới thì lật lên trên. */
function place() {
  if (!root.value) return
  const r = root.value.getBoundingClientRect()
  const h = popup.value?.offsetHeight || 330
  const enoughBelow = window.innerHeight - r.bottom >= h + 8
  const top = enoughBelow || r.top < h + 8 ? r.bottom + 6 : r.top - h - 6
  const left = Math.max(8, Math.min(r.left, window.innerWidth - POPUP_WIDTH - 8))
  popupStyle.value = { top: `${top}px`, left: `${left}px`, width: `${POPUP_WIDTH}px` }
}

function onDocPointerDown(e) {
  if (root.value?.contains(e.target) || popup.value?.contains(e.target)) return
  close()
}
function onDocKeydown(e) {
  if (e.key === 'Escape') close()
}

function listen(on) {
  const fn = on ? window.addEventListener : window.removeEventListener
  fn('mousedown', onDocPointerDown, true)
  fn('keydown', onDocKeydown, true)
  fn('scroll', place, true)
  fn('resize', place)
}

function openPicker() {
  if (props.disabled || open.value) return
  const base = /^\d{4}-\d{2}-\d{2}$/.test(props.modelValue)
    ? new Date(`${props.modelValue}T00:00:00`)
    : TODAY
  viewYear.value = base.getFullYear()
  viewMonth.value = base.getMonth()
  open.value = true
  nextTick(() => {
    place()
    listen(true)
  })
}
function close() {
  if (!open.value) return
  open.value = false
  listen(false)
}
function toggle() {
  open.value ? close() : openPicker()
}
onBeforeUnmount(() => listen(false))

/* ── Nhập tay ── */
// Tự chèn dấu "/" theo từng chữ số gõ vào; sửa giữa chuỗi thì con trỏ nhảy về cuối
// (đánh đổi chấp nhận được để giữ định dạng luôn đúng).
function onInput(e) {
  const digits = e.target.value.replace(/\D/g, '').slice(0, 8)
  let out = digits.slice(0, 2)
  if (digits.length > 2) out += `/${digits.slice(2, 4)}`
  if (digits.length > 4) out += `/${digits.slice(4, 8)}`
  text.value = out
  e.target.value = out

  const iso = textToIso(out)
  if (iso) {
    emit('update:modelValue', iso)
    const d = new Date(`${iso}T00:00:00`)
    viewYear.value = d.getFullYear()
    viewMonth.value = d.getMonth()
  } else if (out === '') {
    emit('update:modelValue', '')
  }
}

// Gõ dở dang rồi bỏ đi → trả về đúng giá trị đang lưu, không để chuỗi rác trên màn hình.
function onBlur() {
  if (text.value !== '' && !textToIso(text.value)) text.value = isoToText(props.modelValue)
}

function pick(iso) {
  emit('update:modelValue', iso)
  text.value = isoToText(iso)
  close()
}
function pickToday() {
  pick(TODAY_ISO)
}
function clear() {
  emit('update:modelValue', '')
  text.value = ''
  close()
}
</script>

<template>
  <div ref="root" class="df">
    <input
      class="df__input"
      :class="{ 'df__input--invalid': invalid }"
      :value="text"
      :placeholder="placeholder"
      :disabled="disabled"
      inputmode="numeric"
      autocomplete="off"
      @input="onInput"
      @blur="onBlur"
      @click="openPicker"
    />
    <button
      type="button"
      class="df__toggle"
      :disabled="disabled"
      :aria-label="open ? 'Đóng lịch' : 'Mở lịch'"
      @click="toggle"
    >
      <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
        <rect x="3" y="5" width="18" height="16" rx="2" />
        <path d="M3 10h18M8 3v4M16 3v4" />
      </svg>
    </button>

    <Teleport to="body">
      <div v-if="open" ref="popup" class="dfpop" :style="popupStyle">
        <div class="dfpop__head">
          <button type="button" class="dfpop__nav" aria-label="Tháng trước" @click="shiftMonth(-1)">
            ‹
          </button>
          <select v-model.number="viewMonth" class="dfpop__sel" aria-label="Chọn tháng">
            <option v-for="(m, i) in MONTHS" :key="m" :value="i">{{ m }}</option>
          </select>
          <select v-model.number="viewYear" class="dfpop__sel" aria-label="Chọn năm">
            <option v-for="y in years" :key="y" :value="y">{{ y }}</option>
          </select>
          <button type="button" class="dfpop__nav" aria-label="Tháng sau" @click="shiftMonth(1)">
            ›
          </button>
        </div>

        <div class="dfpop__dow">
          <span v-for="w in WEEKDAYS" :key="w">{{ w }}</span>
        </div>

        <div class="dfpop__grid">
          <button
            v-for="c in cells"
            :key="c.iso"
            type="button"
            class="dfpop__day"
            :class="{
              'is-outside': c.outside,
              'is-today': c.iso === TODAY_ISO,
              'is-active': c.iso === modelValue,
              'is-blocked': c.blocked,
            }"
            :disabled="c.blocked"
            @click="pick(c.iso)"
          >
            {{ c.day }}
          </button>
        </div>

        <div class="dfpop__foot">
          <button type="button" class="dfpop__link" @click="pickToday">Hôm nay</button>
          <button type="button" class="dfpop__link" @click="clear">Xóa</button>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.df {
  position: relative;
  display: block;
  width: 100%;
}
/* Dùng trong thanh lọc (.toolbar) — ở đó ô nhập nằm ngang, không kéo full chiều rộng.
   Trang chỉ cần <DateField class="df--inline" />: class rơi xuống thẻ gốc của component. */
.df--inline {
  width: 168px;
}
.df__input {
  width: 100%;
  padding: 0.5rem 2rem 0.5rem 0.7rem;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  font-size: 0.9rem;
  /* KHÔNG đặt font-family: các ô nhập khác trong app đều để font mặc định của trình duyệt,
     ép "inherit" ở đây làm ô cao hơn hàng xóm ~3px và lệch hẳn khỏi form. */
  box-sizing: border-box;
  background: var(--c-surface);
  color: var(--c-text);
}
.df__input:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.12);
}
.df__input--invalid {
  border-color: var(--c-danger);
}
.df__input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.df__toggle {
  position: absolute;
  top: 50%;
  right: 6px;
  transform: translateY(-50%);
  display: flex;
  padding: 4px;
  border: 0;
  border-radius: 6px;
  background: none;
  color: var(--c-text-muted);
  cursor: pointer;
}
.df__toggle:hover:not(:disabled) {
  color: var(--c-primary);
}
.df__toggle svg {
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
}

/* ── Lịch (teleport ra body nên dùng position: fixed) ── */
.dfpop {
  position: fixed;
  z-index: 2000;
  padding: 10px;
  border: 1px solid var(--c-border);
  border-radius: 12px;
  background: var(--c-surface);
  box-shadow: 0 14px 32px rgba(15, 40, 80, 0.18);
  color: var(--c-text);
  font-size: 0.85rem;
}
.dfpop__head {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
}
.dfpop__nav {
  width: 26px;
  height: 26px;
  border: 1px solid var(--c-border);
  border-radius: 6px;
  background: var(--c-surface);
  color: var(--c-text);
  font-size: 1rem;
  line-height: 1;
  cursor: pointer;
}
.dfpop__nav:hover {
  border-color: var(--c-primary);
  color: var(--c-primary);
}
.dfpop__sel {
  flex: 1;
  min-width: 0;
  padding: 4px 6px;
  border: 1px solid var(--c-border);
  border-radius: 6px;
  background: var(--c-surface);
  color: var(--c-text);
  font-size: 0.82rem;
}
.dfpop__dow,
.dfpop__grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 2px;
}
.dfpop__dow span {
  padding: 4px 0;
  text-align: center;
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--c-text-muted);
}
.dfpop__day {
  height: 30px;
  border: 0;
  border-radius: 7px;
  background: none;
  color: var(--c-text);
  font-size: 0.82rem;
  cursor: pointer;
}
.dfpop__day:hover {
  background: var(--c-surface-2);
}
.dfpop__day.is-outside {
  color: var(--c-text-muted);
  opacity: 0.55;
}
.dfpop__day.is-today {
  box-shadow: inset 0 0 0 1px var(--c-primary);
}
.dfpop__day.is-active {
  background: var(--c-primary);
  color: #fff;
  font-weight: 700;
}
.dfpop__day.is-blocked {
  opacity: 0.3;
  cursor: not-allowed;
  text-decoration: line-through;
}
.dfpop__day.is-blocked:hover {
  background: none;
}
.dfpop__foot {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--c-border-soft);
}
.dfpop__link {
  border: 0;
  background: none;
  padding: 2px 4px;
  color: var(--c-primary);
  font-size: 0.8rem;
  cursor: pointer;
}
.dfpop__link:hover {
  text-decoration: underline;
}
</style>
