<script setup>
/**
 * Ô CHỌN CÓ TÌM KIẾM (combobox) — gõ để lọc, phím mũi tên để đi, Enter để chọn.
 *
 * VÌ SAO KHÔNG DÙNG <select>: danh sách giáo viên/lớp dài vài chục dòng và người xếp lịch
 * nhớ TÊN chứ không nhớ vị trí. <select> chỉ nhảy theo ký tự đầu, gõ "an" không ra
 * "Nguyễn Văn An". Ở đây lọc trên toàn chuỗi và BỎ DẤU: gõ "duc" ra "Đức", "an" ra "Ân".
 *
 * VÌ SAO LỌC Ở CLIENT: dữ liệu ở quy mô vài chục bản ghi, nạp một lần rồi lọc tại chỗ cho
 * kết quả ngay từng phím gõ — gọi server theo từng phím vừa chậm vừa thừa.
 *
 * Popup dùng Teleport + position: fixed vì component hay nằm trong .modal-box / .table-wrap
 * (những chỗ có overflow) — để trong cây DOM đó thì danh sách bị cắt mất.
 *
 * Dùng:
 *   <SearchSelect v-model="form.teacherId" :options="teachers" placeholder="Chọn giáo viên">
 *     <template #option="{ item }">…dòng hiển thị tùy ý…</template>
 *   </SearchSelect>
 */
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  /** Id đang chọn ('' | null = chưa chọn). */
  modelValue: { type: [Number, String], default: '' },
  /** Danh sách chọn — mỗi mục cần { id, name }, các trường khác tùy slot dùng. */
  options: { type: Array, default: () => [] },
  /** Chuỗi phụ để tìm kiếm (ngoài name), vd mã GV. Nhận item, trả chuỗi. */
  searchExtra: { type: Function, default: null },
  placeholder: { type: String, default: 'Chọn…' },
  /** Chữ mờ trong ô tìm kiếm của popup. */
  searchPlaceholder: { type: String, default: 'Gõ để tìm…' },
  disabled: { type: Boolean, default: false },
  invalid: { type: Boolean, default: false },
  /** Cho phép bỏ chọn (hiện nút ×). */
  clearable: { type: Boolean, default: false },
  /** Thông báo khi danh sách rỗng ngay từ đầu (khác với "không tìm thấy"). */
  emptyText: { type: String, default: 'Không có dữ liệu' },
  /**
   * Mục nào KHÔNG chọn được. Nhận item, trả về chuỗi lý do (hiện ngay dưới tên) hoặc
   * rỗng/null nếu chọn được.
   *
   * <p>Vẫn HIỆN mục bị khóa chứ không ẩn đi: ẩn thì người dùng tưởng dữ liệu bị mất và đi tìm,
   * còn hiện kèm lý do thì họ biết ngay phải làm gì để mở khóa.
   */
  optionDisabled: { type: Function, default: null },
})
const emit = defineEmits(['update:modelValue', 'change'])

const POPUP_MIN_WIDTH = 260
const POPUP_MAX_HEIGHT = 320

/** Bỏ dấu tiếng Việt + thường hóa — khớp đúng cách backend chuẩn hóa từ khóa. */
function norm(s) {
  return String(s ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '') // dấu thanh tách ra sau NFD
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .trim()
}

const open = ref(false)
const query = ref('')
const activeIndex = ref(0)
const root = ref(null)
const popup = ref(null)
const searchInput = ref(null)
const listEl = ref(null)
const popupStyle = ref({})

const selected = computed(
  () => props.options.find((o) => String(o.id) === String(props.modelValue)) ?? null,
)

const filtered = computed(() => {
  const q = norm(query.value)
  if (!q) return props.options
  return props.options.filter((o) => {
    const extra = props.searchExtra ? props.searchExtra(o) : ''
    return norm(o.name).includes(q) || norm(extra).includes(q)
  })
})

// Danh sách đổi (gõ thêm chữ) thì con trỏ về đầu, không để trỏ vào dòng đã biến mất.
watch(filtered, () => {
  activeIndex.value = 0
})

/** Đặt popup ngay dưới ô; không đủ chỗ bên dưới thì lật lên trên. */
function place() {
  if (!root.value) return
  const r = root.value.getBoundingClientRect()
  const h = popup.value?.offsetHeight || POPUP_MAX_HEIGHT
  const enoughBelow = window.innerHeight - r.bottom >= h + 8
  const top = enoughBelow || r.top < h + 8 ? r.bottom + 6 : r.top - h - 6
  const width = Math.max(r.width, POPUP_MIN_WIDTH)
  const left = Math.max(8, Math.min(r.left, window.innerWidth - width - 8))
  popupStyle.value = { top: `${top}px`, left: `${left}px`, width: `${width}px` }
}

function onDocPointerDown(e) {
  if (root.value?.contains(e.target) || popup.value?.contains(e.target)) return
  close()
}

function listen(on) {
  const fn = on ? window.addEventListener : window.removeEventListener
  fn('mousedown', onDocPointerDown, true)
  fn('scroll', place, true)
  fn('resize', place)
}

function openList() {
  if (props.disabled || open.value) return
  query.value = ''
  open.value = true
  // Trỏ sẵn vào mục đang chọn để mở ra là thấy mình đang ở đâu.
  const i = props.options.findIndex((o) => String(o.id) === String(props.modelValue))
  activeIndex.value = i >= 0 ? i : 0
  nextTick(() => {
    place()
    listen(true)
    searchInput.value?.focus()
    scrollActiveIntoView()
  })
}

function close() {
  if (!open.value) return
  open.value = false
  listen(false)
}

function toggle() {
  open.value ? close() : openList()
}

onBeforeUnmount(() => listen(false))

/** Lý do mục này không chọn được ('' = chọn được). */
function reasonOf(item) {
  return (props.optionDisabled ? props.optionDisabled(item) : '') || ''
}

function pick(item) {
  if (reasonOf(item)) return
  emit('update:modelValue', item.id)
  emit('change', item)
  close()
  root.value?.querySelector('.ss__control')?.focus()
}

function clear() {
  emit('update:modelValue', '')
  emit('change', null)
}

function scrollActiveIntoView() {
  nextTick(() => {
    const el = listEl.value?.children?.[activeIndex.value]
    el?.scrollIntoView({ block: 'nearest' })
  })
}

function move(step) {
  if (!filtered.value.length) return
  const n = filtered.value.length
  activeIndex.value = (activeIndex.value + step + n) % n
  scrollActiveIntoView()
}

/** Bàn phím trên ô tìm kiếm trong popup. */
function onSearchKeydown(e) {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    move(1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    move(-1)
  } else if (e.key === 'Enter') {
    e.preventDefault()
    const item = filtered.value[activeIndex.value]
    if (item) pick(item)
  } else if (e.key === 'Escape') {
    e.preventDefault()
    close()
    root.value?.querySelector('.ss__control')?.focus()
  } else if (e.key === 'Tab') {
    close()
  }
}

/** Bàn phím trên chính ô đóng: Enter/Space/mũi tên đều mở danh sách. */
function onControlKeydown(e) {
  if (['Enter', ' ', 'ArrowDown', 'ArrowUp'].includes(e.key)) {
    e.preventDefault()
    openList()
  }
}
</script>

<template>
  <div ref="root" class="ss">
    <button
      type="button"
      class="ss__control"
      :class="{ 'ss__control--invalid': invalid, 'ss__control--open': open }"
      :disabled="disabled"
      role="combobox"
      aria-haspopup="listbox"
      :aria-expanded="open"
      @click="toggle"
      @keydown="onControlKeydown"
    >
      <span v-if="selected" class="ss__value">
        <slot name="selected" :item="selected">{{ selected.name }}</slot>
      </span>
      <span v-else class="ss__placeholder">{{ placeholder }}</span>

      <span
        v-if="clearable && selected && !disabled"
        class="ss__clear"
        role="button"
        tabindex="-1"
        aria-label="Bỏ chọn"
        @click.stop="clear"
        >×</span
      >
      <svg class="ss__caret" viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
        <path d="M6 9l6 6 6-6" />
      </svg>
    </button>

    <Teleport to="body">
      <div v-if="open" ref="popup" class="sspop" :style="popupStyle">
        <div class="sspop__search">
          <svg viewBox="0 0 24 24" width="15" height="15" aria-hidden="true">
            <circle cx="11" cy="11" r="7" />
            <path d="M20 20l-3.5-3.5" />
          </svg>
          <input
            ref="searchInput"
            v-model="query"
            type="text"
            :placeholder="searchPlaceholder"
            autocomplete="off"
            aria-label="Tìm trong danh sách"
            @keydown="onSearchKeydown"
          />
        </div>

        <div v-if="!options.length" class="sspop__empty">{{ emptyText }}</div>
        <div v-else-if="!filtered.length" class="sspop__empty">Không tìm thấy “{{ query }}”</div>
        <div v-else ref="listEl" class="sspop__list" role="listbox">
          <div
            v-for="(item, i) in filtered"
            :key="item.id"
            class="sspop__item"
            :class="{
              'is-active': i === activeIndex,
              'is-selected': String(item.id) === String(modelValue),
              'is-disabled': !!reasonOf(item),
            }"
            role="option"
            :aria-selected="String(item.id) === String(modelValue)"
            :aria-disabled="!!reasonOf(item)"
            @mouseenter="activeIndex = i"
            @click="pick(item)"
          >
            <slot name="option" :item="item">{{ item.name }}</slot>
            <div v-if="reasonOf(item)" class="sspop__reason">{{ reasonOf(item) }}</div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.ss {
  position: relative;
  display: block;
  width: 100%;
}
.ss__control {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  width: 100%;
  min-height: 38px;
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  background: var(--c-surface);
  color: var(--c-text);
  font-size: 0.9rem;
  text-align: left;
  cursor: pointer;
  box-sizing: border-box;
}
.ss__control:focus-visible {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.12);
}
.ss__control--open {
  border-color: var(--c-primary);
}
.ss__control--invalid {
  border-color: var(--c-danger);
}
.ss__control:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.ss__value {
  flex: 1;
  min-width: 0;
}
.ss__placeholder {
  flex: 1;
  color: var(--c-text-muted);
}
.ss__clear {
  padding: 0 4px;
  border-radius: 4px;
  color: var(--c-text-muted);
  font-size: 1.05rem;
  line-height: 1;
  cursor: pointer;
}
.ss__clear:hover {
  color: var(--c-danger);
}
.ss__caret {
  flex: none;
  fill: none;
  stroke: var(--c-text-muted);
  stroke-width: 2;
  stroke-linecap: round;
}

/* ── Popup (teleport ra body nên position: fixed) ── */
.sspop {
  position: fixed;
  z-index: 2000;
  border: 1px solid var(--c-border);
  border-radius: 12px;
  background: var(--c-surface);
  box-shadow: 0 14px 32px rgba(15, 40, 80, 0.18);
  color: var(--c-text);
  overflow: hidden;
}
.sspop__search {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.5rem 0.6rem;
  border-bottom: 1px solid var(--c-border-soft);
}
.sspop__search svg {
  flex: none;
  fill: none;
  stroke: var(--c-text-muted);
  stroke-width: 2;
  stroke-linecap: round;
}
.sspop__search input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: none;
  color: var(--c-text);
  font-size: 0.88rem;
  outline: none;
}
.sspop__list {
  max-height: 260px;
  overflow-y: auto;
  padding: 0.25rem;
}
.sspop__item {
  padding: 0.45rem 0.55rem;
  border-radius: 8px;
  font-size: 0.87rem;
  cursor: pointer;
}
.sspop__item.is-active {
  background: var(--c-bg);
}
.sspop__item.is-selected {
  box-shadow: inset 0 0 0 1px var(--c-primary);
}
.sspop__item.is-disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.sspop__reason {
  margin-top: 0.1rem;
  color: var(--c-danger);
  font-size: 0.72rem;
}
.sspop__empty {
  padding: 0.9rem 0.7rem;
  color: var(--c-text-muted);
  font-size: 0.85rem;
  text-align: center;
}
</style>
