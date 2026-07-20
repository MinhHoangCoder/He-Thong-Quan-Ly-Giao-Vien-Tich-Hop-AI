<script setup>
/**
 * Hiển thị / chọn điểm 1–5 sao.
 * - interactive=false: chỉ xem (mặc định)
 * - interactive=true: click để chọn (v-model)
 */
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: [Number, String, null], default: 0 },
  interactive: { type: Boolean, default: false },
  size: { type: Number, default: 18 },
})

const emit = defineEmits(['update:modelValue'])

const value = computed(() => Number(props.modelValue) || 0)

function pick(n) {
  if (!props.interactive) return
  emit('update:modelValue', n)
}
</script>

<template>
  <span
    class="stars"
    :class="{ 'stars--interactive': interactive }"
    role="img"
    :aria-label="`${value}/5`"
  >
    <button
      v-for="n in 5"
      :key="n"
      type="button"
      class="stars__btn"
      :class="{ 'is-on': n <= value }"
      :style="{ fontSize: size + 'px' }"
      :disabled="!interactive"
      :tabindex="interactive ? 0 : -1"
      @click="pick(n)"
    >
      ★
    </button>
  </span>
</template>

<style scoped>
.stars {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  line-height: 1;
}
.stars__btn {
  border: 0;
  background: transparent;
  padding: 0;
  color: #d1d5db;
  cursor: default;
  line-height: 1;
  transition:
    color var(--t-fast, 0.15s ease),
    transform var(--t-fast, 0.15s ease);
}
.stars--interactive .stars__btn {
  cursor: pointer;
}
.stars--interactive .stars__btn:hover {
  transform: scale(1.15);
}
.stars__btn.is-on {
  color: #f59e0b;
}
.stars__btn:disabled {
  cursor: default;
}
</style>
