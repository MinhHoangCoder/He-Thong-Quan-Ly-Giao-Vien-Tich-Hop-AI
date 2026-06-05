<script setup>
// Thẻ thống kê nhỏ ở đầu dashboard.
import SvgIcon from '@/components/ui/SvgIcon.vue'

defineProps({
  icon: { type: String, required: true },
  label: { type: String, required: true },
  value: { type: [String, Number], required: true },
  hint: { type: String, default: '' },
  trend: { type: Number, default: null }, // % tăng/giảm; >0 xanh, <0 đỏ
  color: { type: String, default: '#3b6fd4' }, // màu nhấn của icon
})
</script>

<template>
  <article class="stat">
    <div class="stat__icon" :style="{ background: color + '1a', color }">
      <SvgIcon :name="icon" :size="22" />
    </div>
    <div class="stat__body">
      <p class="stat__label">{{ label }}</p>
      <p class="stat__value">{{ value }}</p>
      <p v-if="hint || trend !== null" class="stat__meta">
        <span
          v-if="trend !== null"
          class="stat__trend"
          :class="trend >= 0 ? 'is-up' : 'is-down'"
        >
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
  display: flex;
  align-items: center;
  gap: 0.9rem;
  background: #fff;
  border: 1px solid var(--a-border);
  border-radius: 14px;
  padding: 1.15rem 1.25rem;
  box-shadow: var(--a-shadow);
  transition: transform 0.18s, box-shadow 0.18s;
}
.stat:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 28px rgba(31, 45, 80, 0.12);
}
.stat__icon {
  flex: 0 0 auto;
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 12px;
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
  color: #1a8f5a;
  background: #1a8f5a14;
}
.stat__trend.is-down {
  color: #d23b4e;
  background: #d23b4e14;
}
</style>
