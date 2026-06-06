<script setup>
// Sparkline dạng cột nhỏ cho thẻ chỉ số bên cạnh.
import { computed } from 'vue'

const props = defineProps({
  data: { type: Array, required: true },
  color: { type: String, default: '#0d9488' },
})

const max = computed(() => Math.max(1, ...props.data))
</script>

<template>
  <div class="bars">
    <span
      v-for="(v, i) in data"
      :key="i"
      class="bars__bar"
      :style="{ height: (v / max) * 100 + '%', background: color }"
    />
  </div>
</template>

<style scoped>
.bars {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 40px;
}
.bars__bar {
  flex: 1;
  min-width: 4px;
  border-radius: 3px 3px 0 0;
  opacity: 0.55;
  transition: height var(--t-slow), opacity var(--t-fast);
}
.bars__bar:last-child {
  opacity: 1;
}
/* rê chuột vào cụm cột → tất cả sáng lên */
.bars:hover .bars__bar {
  opacity: 1;
}
</style>
