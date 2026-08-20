<script setup>
/**
 * Biểu đồ KẾT HỢP: cột (khối lượng) + đường (chi phí), hai trục dọc — SVG thuần.
 *
 * VÌ SAO GHÉP HAI THỨ VÀO MỘT KHUNG: khối lượng dạy và chi phí lương là hai đại lượng
 * khác đơn vị nhưng phải đọc CÙNG NHAU mới ra thông tin. Đặt cạnh nhau ở hai biểu đồ
 * riêng thì mắt không so được; chồng lên một khung là thấy ngay tháng nào chi phí đi
 * lệch khỏi khối lượng — đó chính là tháng có vấn đề.
 *
 * Hai trục dọc có tỉ lệ độc lập, mỗi trục tô đúng màu của chuỗi mà nó phục vụ, nếu
 * không người đọc sẽ không biết đường đang đo theo trục nào.
 */
import { computed, ref } from 'vue'

const props = defineProps({
  /** [{ nhan, cot, duong }] — cùng độ dài, đã sắp xếp theo thời gian. */
  points: { type: Array, required: true },
  nhanCot: { type: String, default: 'Cột' },
  nhanDuong: { type: String, default: 'Đường' },
  mauCot: { type: String, default: '#f97316' },
  mauDuong: { type: String, default: '#22c55e' },
  /** Hàm định dạng giá trị cho tooltip và nhãn trục. */
  dinhDangCot: { type: Function, default: (v) => String(v) },
  dinhDangDuong: { type: Function, default: (v) => String(v) },
})

// Hệ toạ độ nội bộ. Mọi thứ vẽ theo khung này rồi để viewBox co giãn theo bề rộng
// thật của thẻ — nhờ vậy biểu đồ tự thích ứng mà không cần đo DOM hay theo dõi resize.
const W = 900
const H = 340
const pad = { top: 22, right: 62, bottom: 42, left: 62 }
const innerW = W - pad.left - pad.right
const innerH = H - pad.top - pad.bottom

/** Làm tròn LÊN một bậc "đẹp" (1 · 2 · 2,5 · 5 · 10) để nhãn trục là số tròn. */
function tranDep(max) {
  if (max <= 0) return 1
  const bac = Math.pow(10, Math.floor(Math.log10(max)))
  const tiLe = max / bac
  const nac = tiLe <= 1 ? 1 : tiLe <= 2 ? 2 : tiLe <= 2.5 ? 2.5 : tiLe <= 5 ? 5 : 10
  return nac * bac
}

const maxCot = computed(() => tranDep(Math.max(0, ...props.points.map((p) => p.cot))))
const maxDuong = computed(() => tranDep(Math.max(0, ...props.points.map((p) => p.duong))))

const soCot = computed(() => Math.max(1, props.points.length))
/** Bề rộng một ô (mỗi tháng chiếm một ô, cột nằm giữa ô). */
const oRong = computed(() => innerW / soCot.value)
const cotRong = computed(() => Math.min(46, oRong.value * 0.52))

const tamX = (i) => pad.left + oRong.value * (i + 0.5)
const yCot = (v) => pad.top + innerH * (1 - v / maxCot.value)
const yDuong = (v) => pad.top + innerH * (1 - v / maxDuong.value)

const cot = computed(() =>
  props.points.map((p, i) => {
    const y = yCot(p.cot)
    return { ...p, i, x: tamX(i) - cotRong.value / 2, y, w: cotRong.value, h: pad.top + innerH - y }
  }),
)

/** Đường chi phí vẽ GÃY KHÚC chứ không làm mượt: mỗi đỉnh là một kỳ lương có thật,
 *  đường cong Bézier sẽ tạo ra những chỗ phình không ứng với dữ liệu nào cả. */
const duongPath = computed(() =>
  props.points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${tamX(i)} ${yDuong(p.duong)}`).join(' '),
)

const moc = computed(() =>
  Array.from({ length: 5 }, (_, i) => {
    const tiLe = i / 4
    return {
      y: pad.top + innerH * (1 - tiLe),
      trai: maxCot.value * tiLe,
      phai: maxDuong.value * tiLe,
    }
  }),
)

/* ── Tương tác: rê chuột tới đâu, cả cột lẫn điểm ở đó cùng sáng lên ── */
const dangXem = ref(-1)

function theoDoi(e) {
  const khung = e.currentTarget.getBoundingClientRect()
  const tiLe = (e.clientX - khung.left) / khung.width
  const x = tiLe * W - pad.left
  const i = Math.floor(x / oRong.value)
  dangXem.value = i >= 0 && i < props.points.length ? i : -1
}

const diem = computed(() => (dangXem.value < 0 ? null : props.points[dangXem.value]))
/** Tooltip bám cột đang xem; quá nửa bên phải thì lật sang trái để không tràn khỏi thẻ. */
const viTriTooltip = computed(() => {
  if (dangXem.value < 0) return {}
  const tiLe = (tamX(dangXem.value) / W) * 100
  return tiLe > 55
    ? { right: `${100 - tiLe}%`, transform: 'translateX(-8px)' }
    : { left: `${tiLe}%`, transform: 'translateX(8px)' }
})

const moTa = computed(
  () =>
    `Biểu đồ ${props.nhanCot} và ${props.nhanDuong} theo tháng, ${props.points.length} mốc thời gian.`,
)
</script>

<template>
  <div class="combo">
    <svg
      :viewBox="`0 0 ${W} ${H}`"
      class="combo__svg"
      role="img"
      :aria-label="moTa"
      @mousemove="theoDoi"
      @mouseleave="dangXem = -1"
    >
      <!-- Lưới ngang + hai trục số -->
      <g class="combo__grid">
        <template v-for="(m, i) in moc" :key="'m' + i">
          <line :x1="pad.left" :y1="m.y" :x2="W - pad.right" :y2="m.y" />
          <text :x="pad.left - 10" :y="m.y + 4" text-anchor="end" :fill="mauCot">
            {{ dinhDangCot(m.trai) }}
          </text>
          <text :x="W - pad.right + 10" :y="m.y + 4" text-anchor="start" :fill="mauDuong">
            {{ dinhDangDuong(m.phai) }}
          </text>
        </template>
      </g>

      <!-- Vệt sáng đánh dấu tháng đang rê chuột -->
      <rect
        v-if="dangXem >= 0"
        class="combo__highlight"
        :x="pad.left + oRong * dangXem"
        :y="pad.top"
        :width="oRong"
        :height="innerH"
      />

      <!-- Cột khối lượng -->
      <g>
        <rect
          v-for="c in cot"
          :key="'c' + c.i"
          class="combo__bar"
          :class="{ 'is-mo': dangXem >= 0 && dangXem !== c.i }"
          :x="c.x"
          :y="c.y"
          :width="c.w"
          :height="Math.max(0, c.h)"
          :fill="mauCot"
          rx="4"
        />
      </g>

      <!-- Đường chi phí -->
      <path
        :d="duongPath"
        :stroke="mauDuong"
        fill="none"
        stroke-width="2.6"
        stroke-linejoin="round"
      />
      <circle
        v-for="(p, i) in points"
        :key="'d' + i"
        :cx="tamX(i)"
        :cy="yDuong(p.duong)"
        :r="dangXem === i ? 6 : 3.6"
        :fill="mauDuong"
        class="combo__dot"
      />

      <!-- Nhãn tháng -->
      <text
        v-for="(p, i) in points"
        :key="'x' + i"
        class="combo__xlabel"
        :x="tamX(i)"
        :y="H - 14"
        text-anchor="middle"
      >
        {{ p.nhan }}
      </text>
    </svg>

    <!-- Tooltip là thẻ HTML thường, không phải foreignObject: chữ trong SVG không
         xuống dòng được và cũng không nhận CSS của trang. -->
    <div v-if="diem" class="combo__tip" :style="viTriTooltip">
      <strong>{{ diem.nhan }}</strong>
      <span><i :style="{ background: mauCot }" />{{ nhanCot }}: {{ dinhDangCot(diem.cot) }}</span>
      <span
        ><i :style="{ background: mauDuong }" />{{ nhanDuong }}:
        {{ dinhDangDuong(diem.duong) }}</span
      >
    </div>

    <ul class="combo__legend">
      <li><i class="bar" :style="{ background: mauCot }" />{{ nhanCot }}</li>
      <li><i class="line" :style="{ background: mauDuong }" />{{ nhanDuong }}</li>
    </ul>
  </div>
</template>

<style scoped>
.combo {
  position: relative;
  width: 100%;
}
.combo__svg {
  width: 100%;
  height: auto;
  display: block;
}
.combo__grid line {
  stroke: var(--a-border);
  stroke-dasharray: 4 5;
}
.combo__grid text {
  font-size: 11px;
  opacity: 0.75;
}
.combo__xlabel {
  font-size: 11.5px;
  fill: var(--a-text-muted);
}
.combo__highlight {
  fill: var(--c-text);
  opacity: 0.05;
}
.combo__bar {
  transition: opacity var(--t-fast);
}
/* Cột không được trỏ tới thì mờ đi — mắt bám đúng tháng đang đọc mà không cần viền. */
.combo__bar.is-mo {
  opacity: 0.38;
}
.combo__dot {
  transition: r var(--t-fast);
}
.combo__tip {
  position: absolute;
  top: 8px;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.55rem 0.75rem;
  border-radius: 10px;
  background: var(--c-surface);
  border: 1px solid var(--a-border);
  box-shadow: var(--a-shadow-lg);
  font-size: 0.8rem;
  color: var(--a-text);
  pointer-events: none;
  white-space: nowrap;
  z-index: 3;
}
.combo__tip strong {
  font-size: 0.84rem;
}
.combo__tip span {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--a-text-muted);
}
.combo__tip i {
  width: 9px;
  height: 9px;
  border-radius: 2px;
  flex: none;
}
.combo__legend {
  list-style: none;
  display: flex;
  gap: 1.4rem;
  margin: 0.35rem 0 0;
  padding: 0;
}
.combo__legend li {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.82rem;
  color: var(--a-text-muted);
}
.combo__legend .bar {
  width: 11px;
  height: 11px;
  border-radius: 3px;
}
.combo__legend .line {
  width: 16px;
  height: 3px;
  border-radius: 2px;
}
</style>
