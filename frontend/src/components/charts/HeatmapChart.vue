<script setup>
/**
 * Bảng mật độ buổi dạy theo (thứ × tiết). Ô càng đậm càng nhiều buổi.
 * Dùng CSS Grid vì Chart.js không có sẵn dạng này.
 *
 * Vẽ đủ mọi khung tiết mà các trường có đăng ký, kể cả khung chưa xếp buổi nào — ô trống
 * chính là khung giờ còn để không.
 */
import { computed } from 'vue'

const props = defineProps({
  o: { type: Array, required: true }, // [{ thu, tiet, soBuoi }], thu: 1 = T2 ... 7 = CN
  soTiet: { type: Number, default: 10 },
})

const THU = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']
const MAU = '#f97316'

const tra = computed(() => {
  const m = new Map()
  props.o.forEach((c) => m.set(c.thu + '-' + c.tiet, c.soBuoi))
  return m
})

const dinh = computed(() => Math.max(1, ...props.o.map((c) => c.soBuoi)))

const hang = computed(() =>
  Array.from({ length: Math.max(1, props.soTiet) }, (_, i) => {
    const tiet = i + 1
    return {
      tiet,
      o: THU.map((ten, j) => {
        const soBuoi = tra.value.get(j + 1 + '-' + tiet) || 0
        return {
          thu: j + 1,
          soBuoi,
          dam: soBuoi ? 0.15 + 0.85 * (soBuoi / dinh.value) : 0,
          title: `${ten}, tiết ${tiet}: ${soBuoi} buổi`,
        }
      }),
    }
  }),
)
</script>

<template>
  <div class="heat">
    <div class="heat__grid">
      <span />
      <span v-for="t in THU" :key="t" class="heat__head">{{ t }}</span>

      <template v-for="h in hang" :key="h.tiet">
        <span class="heat__side">Tiết {{ h.tiet }}</span>
        <span
          v-for="c in h.o"
          :key="c.thu"
          class="heat__cell"
          :style="c.soBuoi ? { background: MAU, opacity: c.dam } : null"
          :title="c.title"
        >
          {{ c.soBuoi || '' }}
        </span>
      </template>
    </div>

    <div class="heat__scale">
      <span>Ít</span>
      <i v-for="n in 5" :key="n" :style="{ background: MAU, opacity: n / 5 }" />
      <span>Nhiều</span>
    </div>
  </div>
</template>

<style scoped>
.heat__grid {
  display: grid;
  grid-template-columns: 4.2rem repeat(7, minmax(34px, 1fr));
  gap: 3px;
}
.heat__head,
.heat__side {
  font-size: 0.76rem;
  color: var(--c-text-muted);
  display: flex;
  align-items: center;
}
.heat__head {
  justify-content: center;
}
.heat__cell {
  aspect-ratio: 1.7;
  border-radius: 4px;
  background: var(--c-surface-2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.7rem;
  font-weight: 700;
  color: #fff;
}
.heat__scale {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  margin-top: 0.6rem;
  font-size: 0.76rem;
  color: var(--c-text-muted);
}
.heat__scale i {
  width: 14px;
  height: 10px;
  border-radius: 2px;
}
</style>
