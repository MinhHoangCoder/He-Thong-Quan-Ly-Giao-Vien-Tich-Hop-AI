<script setup>
/**
 * THANH LỌC dùng chung cho mọi màn danh sách.
 *
 * Trước đây mỗi màn tự dựng lấy một thanh: Phân công có ô tìm + hai nút, Lịch nghỉ có bốn ô
 * lọc + ô tìm, Lịch dạy thì lọc tại chỗ không nút nào. Ba màn cạnh nhau hành xử khác nhau, và
 * cùng một khối CSS `.filter-bar/.field` bị chép lại trong sáu file.
 *
 * Chuẩn chốt theo màn Phân công: gõ tới đâu lọc tới đó (chờ 300ms cho phím ngừng rồi mới gọi
 * server), kèm nút "Lọc" cho người quen bấm và "Xóa lọc" để về trạng thái đầu.
 *
 * Ô lọc riêng của từng màn (Loại, Phạm vi, Từ ngày…) đặt vào slot mặc định — chúng đứng
 * TRƯỚC ô tìm kiếm để mắt đi từ bộ lọc hẹp sang ô tự do.
 *
 * Dùng:
 *   <FilterBar v-model="keyword" @apply="load" @clear="resetVaLoad">
 *     <label class="field"><span>Loại</span><select …/></label>
 *   </FilterBar>
 */
import { onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  /** Từ khóa tìm kiếm (v-model). */
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '' },
  /** Nhãn cho trình đọc màn hình — nói rõ tìm được theo những gì. */
  ariaLabel: { type: String, default: 'Tìm kiếm' },
  /** Chờ bao lâu sau phím cuối mới lọc. 0 = tắt hẳn, chỉ lọc khi bấm nút/Enter. */
  debounce: { type: Number, default: 300 },
})
const emit = defineEmits(['update:modelValue', 'apply', 'clear'])

const text = ref(props.modelValue)
watch(
  () => props.modelValue,
  (v) => {
    if (v !== text.value) text.value = v
  },
)

let timer = null
function onInput() {
  emit('update:modelValue', text.value)
  if (!props.debounce) return
  clearTimeout(timer)
  timer = setTimeout(() => emit('apply'), props.debounce)
}

/** Bấm "Lọc" hoặc Enter: hủy hẹn giờ đang chờ để không lọc hai lần liền nhau. */
function apply() {
  clearTimeout(timer)
  emit('update:modelValue', text.value)
  emit('apply')
}

function clear() {
  clearTimeout(timer)
  text.value = ''
  emit('update:modelValue', '')
  emit('clear')
}

onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <div class="filter-bar">
    <slot />

    <label class="field field--wide">
      <span>Tìm kiếm</span>
      <input
        v-model="text"
        type="search"
        :placeholder="placeholder"
        :aria-label="ariaLabel"
        @input="onInput"
        @keyup.enter="apply"
      />
    </label>

    <div class="filter-actions">
      <button class="btn btn-primary" @click="apply">Lọc</button>
      <button class="btn btn-outline" @click="clear">Xóa lọc</button>
    </div>
  </div>
</template>
