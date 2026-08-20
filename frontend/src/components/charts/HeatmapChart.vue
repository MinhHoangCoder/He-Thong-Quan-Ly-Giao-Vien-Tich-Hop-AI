<script setup>
/**
 * BẢN ĐỒ NHIỆT mật độ buổi dạy theo (thứ × tiết) — dựng bằng CSS Grid, không dùng SVG.
 *
 * Chọn Grid vì bản đồ nhiệt là một cái BẢNG: mỗi ô cần tooltip riêng, cần bắt được
 * phím Tab, cần tự xuống dòng trên màn hình hẹp. Vẽ bằng SVG thì phải tự làm lại cả ba
 * thứ đó bằng tay.
 *
 * Ô TRỐNG QUAN TRỌNG NGANG Ô ĐẬM. Bảng luôn vẽ đủ mọi khung tiết mà các trường có đăng
 * ký, kể cả khung chưa từng xếp buổi nào — mảng trống chính là năng lực còn bỏ ngỏ, và
 * đó thường là điều người điều hành cần thấy nhất. Chỉ vẽ những khung đã dùng thì bản
 * đồ lúc nào cũng đầy kín và không nói lên điều gì.
 */
import { computed } from 'vue'

const props = defineProps({
  /** [{ thu, tiet, soBuoi }] — thu: 1 = Thứ Hai … 7 = Chủ Nhật. */
  o: { type: Array, required: true },
  soTiet: { type: Number, default: 10 },
  mau: { type: String, default: '#f97316' },
})

const TEN_THU = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']
const TEN_THU_DAY = ['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ Nhật']

const tra = computed(() => {
  const m = new Map()
  props.o.forEach((c) => m.set(`${c.thu}-${c.tiet}`, c.soBuoi))
  return m
})

const dinh = computed(() => Math.max(1, ...props.o.map((c) => c.soBuoi)))

const hang = computed(() =>
  Array.from({ length: Math.max(1, props.soTiet) }, (_, i) => {
    const tiet = i + 1
    return {
      tiet,
      o: TEN_THU.map((_, j) => {
        const thu = j + 1
        const soBuoi = tra.value.get(`${thu}-${tiet}`) || 0
        return {
          thu,
          tiet,
          soBuoi,
          // Căn theo CĂN BẬC HAI thay vì tuyến tính: các ô thưa vẫn hiện rõ thay vì
          // chìm hết thành màu nhạt như nhau, trong khi thứ tự đậm nhạt không đổi.
          dam: soBuoi === 0 ? 0 : 0.12 + 0.88 * Math.sqrt(soBuoi / dinh.value),
          nhan: `${TEN_THU_DAY[j]}, tiết ${tiet}: ${soBuoi === 0 ? 'không có buổi nào' : soBuoi + ' buổi'}`,
        }
      }),
    }
  }),
)

/** Số khung tiết thực sự được khai thác — con số này thường gây bất ngờ nhất. */
const khungDaDung = computed(
  () => new Set(props.o.filter((c) => c.soBuoi > 0).map((c) => c.tiet)).size,
)
</script>

<template>
  <div class="heat">
    <div class="heat__grid" role="table" aria-label="Mật độ buổi dạy theo thứ và tiết">
      <span class="heat__corner" />
      <span v-for="t in TEN_THU" :key="t" class="heat__head">{{ t }}</span>

      <template v-for="h in hang" :key="h.tiet">
        <span class="heat__side">Tiết {{ h.tiet }}</span>
        <span
          v-for="c in h.o"
          :key="c.thu"
          class="heat__cell"
          :class="{ 'is-trong': c.soBuoi === 0 }"
          :style="{ background: c.soBuoi ? mau : undefined, opacity: c.soBuoi ? c.dam : undefined }"
          :title="c.nhan"
          tabindex="0"
        >
          <span class="heat__val">{{ c.soBuoi || '' }}</span>
        </span>
      </template>
    </div>

    <div class="heat__foot">
      <span class="heat__scale">
        Ít
        <i v-for="n in 5" :key="n" :style="{ background: mau, opacity: 0.12 + (0.88 * n) / 5 }" />
        Nhiều
      </span>
      <span class="heat__note">
        Đang khai thác <strong>{{ khungDaDung }}/{{ soTiet }}</strong> khung tiết
      </span>
    </div>
  </div>
</template>

<style scoped>
.heat {
  width: 100%;
  overflow-x: auto;
}
.heat__grid {
  display: grid;
  /* Cột đầu là nhãn tiết; bảy cột còn lại chia đều nhau. */
  grid-template-columns: 4.4rem repeat(7, minmax(38px, 1fr));
  gap: 4px;
  min-width: 380px;
}
.heat__corner {
  content: '';
}
.heat__head,
.heat__side {
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--a-text-muted);
  display: flex;
  align-items: center;
}
.heat__head {
  justify-content: center;
  padding-bottom: 0.15rem;
}
.heat__cell {
  aspect-ratio: 1.6;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: default;
  transition: transform var(--t-fast);
}
.heat__cell:hover,
.heat__cell:focus-visible {
  transform: scale(1.09);
  outline: 2px solid var(--c-primary);
  outline-offset: 1px;
}
/* Ô trống vẫn phải nhìn thấy được: nó là "khung giờ chưa ai dùng", không phải chỗ khuyết. */
.heat__cell.is-trong {
  background: var(--a-border);
  opacity: 0.32;
}
.heat__val {
  font-size: 0.7rem;
  font-weight: 700;
  color: #fff;
  mix-blend-mode: luminosity;
}
.heat__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
  margin-top: 0.7rem;
  font-size: 0.78rem;
  color: var(--a-text-muted);
}
.heat__scale {
  display: flex;
  align-items: center;
  gap: 0.28rem;
}
.heat__scale i {
  width: 15px;
  height: 11px;
  border-radius: 3px;
}
.heat__note strong {
  color: var(--a-text);
}
</style>
