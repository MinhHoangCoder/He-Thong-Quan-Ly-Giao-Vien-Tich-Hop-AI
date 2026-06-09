<script setup>
// Thẻ thống kê nhỏ ở đầu dashboard.
import { ref, onMounted, computed } from 'vue'
import SvgIcon from '@/components/ui/SvgIcon.vue'

const props = defineProps({
  icon: { type: String, required: true },
  label: { type: String, required: true },
  value: { type: [String, Number], required: true },
  hint: { type: String, default: '' },
  trend: { type: Number, default: null }, // % tăng/giảm; >0 xanh, <0 đỏ
  color: { type: String, default: '#f97316' }, // màu nhấn của icon
})

// ⭐ HIỆU ỨNG ĐẾM SỐ (count-up): khi thẻ vừa hiện, số nhảy từ 0 → giá trị thật.
// Chỉ áp dụng khi value là số. Nếu là chuỗi (vd "96%") thì hiển thị nguyên.
const isNumber = typeof props.value === 'number'
const display = ref(isNumber ? 0 : props.value)

onMounted(() => {
  if (!isNumber) return
  const target = props.value
  const duration = 900 // ms
  const start = performance.now()
  // requestAnimationFrame = trình duyệt gọi lại hàm này ~60 lần/giây để vẽ mượt.
  const tick = (now) => {
    const p = Math.min(1, (now - start) / duration)
    // easeOutCubic: nhanh lúc đầu, chậm dần về cuối cho tự nhiên.
    const eased = 1 - Math.pow(1 - p, 3)
    display.value = Math.round(target * eased)
    if (p < 1) requestAnimationFrame(tick)
  }
  requestAnimationFrame(tick)
})

// nền nhạt cho ô icon (mã màu + độ trong suốt '1a' ≈ 10%)
const iconStyle = computed(() => ({ background: props.color + '1a', color: props.color }))
</script>

<template>
  <article class="stat" :style="{ '--accent': color }">
    <div class="stat__icon" :style="iconStyle">
      <SvgIcon :name="icon" :size="22" />
    </div>
    <div class="stat__body">
      <p class="stat__label">{{ label }}</p>
      <p class="stat__value">{{ display }}</p>
      <p v-if="hint || trend !== null" class="stat__meta">
        <span v-if="trend !== null" class="stat__trend" :class="trend >= 0 ? 'is-up' : 'is-down'">
          <SvgIcon :name="trend >= 0 ? 'up' : 'down'" :size="13" />
          {{ Math.abs(trend) }}%
        </span>
        <span class="stat__hint">{{ hint }}</span>
      </p>
    </div>
  </article>
</template>

<style scoped>
.stat {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.9rem;
  background: #fff;
  border: 1px solid var(--a-border);
  border-radius: 14px;
  padding: 1.15rem 1.25rem;
  box-shadow: var(--a-shadow);
  overflow: hidden;
  transition:
    transform var(--t),
    box-shadow var(--t);
}
/* vạch màu nhấn bên trái, hiện ra khi rê chuột */
.stat::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: var(--accent);
  transform: scaleY(0);
  transform-origin: bottom;
  transition: transform var(--t);
}
.stat:hover {
  transform: translateY(-4px);
  box-shadow: var(--a-shadow-lg);
}
.stat:hover::before {
  transform: scaleY(1);
}
/* ô icon phóng to nhẹ khi hover */
.stat:hover .stat__icon {
  transform: scale(1.08);
}
.stat__icon {
  flex: 0 0 auto;
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  transition: transform var(--t);
}
.stat__label {
  margin: 0;
  font-size: 0.82rem;
  color: var(--a-text-muted);
}
.stat__value {
  margin: 0.15rem 0 0;
  font-size: 1.55rem;
  font-weight: 700;
  color: var(--a-text);
  line-height: 1.1;
}
.stat__meta {
  margin: 0.35rem 0 0;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.78rem;
  color: var(--a-text-muted);
}
.stat__trend {
  display: inline-flex;
  align-items: center;
  gap: 0.15rem;
  font-weight: 700;
  padding: 0.1rem 0.4rem;
  border-radius: 6px;
}
.stat__trend.is-up {
  color: #15803d;
  background: #22c55e1f;
}
.stat__trend.is-down {
  color: #dc2626;
  background: #ef44441f;
}
</style>
