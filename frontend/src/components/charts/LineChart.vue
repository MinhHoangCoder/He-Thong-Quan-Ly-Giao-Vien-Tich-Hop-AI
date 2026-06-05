<script setup>
// Biểu đồ đường mượt vẽ bằng SVG thuần (đa series, có vùng tô gradient).
// props.series: [{ name, color, data: number[] }] — các series cùng độ dài với labels.
import { computed } from 'vue'

const props = defineProps({
  labels: { type: Array, required: true },
  series: { type: Array, required: true },
  height: { type: Number, default: 280 },
})

// Toạ độ vẽ (viewBox). Chừa lề cho nhãn trục.
const W = 760
const H = computed(() => props.height)
const pad = { top: 16, right: 16, bottom: 28, left: 36 }

const maxVal = computed(() => {
  const all = props.series.flatMap((s) => s.data)
  const m = Math.max(1, ...all)
  // Làm tròn lên bậc đẹp để trục Y gọn.
  const step = Math.pow(10, Math.floor(Math.log10(m)))
  return Math.ceil(m / step) * step
})

const innerW = computed(() => W - pad.left - pad.right)
const innerH = computed(() => H.value - pad.top - pad.bottom)

// ⭐ Quy đổi SỐ LIỆU → TOẠ ĐỘ PIXEL trong SVG.
// SVG có gốc (0,0) ở GÓC TRÊN-TRÁI, và y TĂNG khi đi XUỐNG (ngược trực giác toán).
function x(i, len) {
  // Điểm thứ i nằm ngang ở đâu: chia đều bề rộng cho (len-1) khoảng.
  // i=0 → sát trái (pad.left); i cuối → sát phải.
  return pad.left + (innerW.value * i) / Math.max(1, len - 1)
}
function y(v) {
  // v/maxVal ra tỉ lệ 0..1. Vì y tăng xuống dưới mà giá trị LỚN phải ở TRÊN,
  // nên lấy (1 - tỉ lệ): giá trị càng lớn → y càng nhỏ → càng lên cao.
  return pad.top + innerH.value * (1 - v / maxVal.value)
}

// ⭐ Vẽ ĐƯỜNG CONG MƯỢT (phần rối nhất). Nếu nối các điểm bằng đường thẳng sẽ gãy
// khúc. Mẹo: với mỗi đoạn p1→p2, thay vì kẻ thẳng, ta vẽ một đường cong Bézier bậc 3
// có 2 "điểm điều khiển" c1, c2 — tính từ điểm liền trước (p0) và liền sau (p3).
// Đây là công thức Catmull-Rom đổi sang Bézier: độ dốc tại mỗi điểm lấy theo hướng
// trung bình của 2 hàng xóm → đường liền mạch, không gãy.
// (Muốn đường GÃY KHÚC thay vì mượt: đổi lệnh 'C ...' thành 'L p2' là xong.)
function smoothPath(data) {
  const pts = data.map((v, i) => [x(i, data.length), y(v)]) // [ [x,y], ... ]
  if (pts.length < 2) return ''
  let d = `M ${pts[0][0]} ${pts[0][1]}` // M = nhấc bút, đặt tại điểm đầu
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] || pts[i] // hàng xóm trái (điểm đầu thì lấy chính nó)
    const p1 = pts[i] // điểm bắt đầu đoạn
    const p2 = pts[i + 1] // điểm kết thúc đoạn
    const p3 = pts[i + 2] || p2 // hàng xóm phải (điểm cuối thì lấy chính nó)
    // c1: hướng ĐI RA khỏi p1, ăn theo chiều p0→p2 (chia 6 cho cong vừa phải)
    const c1x = p1[0] + (p2[0] - p0[0]) / 6
    const c1y = p1[1] + (p2[1] - p0[1]) / 6
    // c2: hướng ĐI VÀO p2, ăn theo chiều p1→p3
    const c2x = p2[0] - (p3[0] - p1[0]) / 6
    const c2y = p2[1] - (p3[1] - p1[1]) / 6
    // C c1 c2 p2 = vẽ cong Bézier từ điểm hiện tại tới p2 qua 2 điểm điều khiển
    d += ` C ${c1x} ${c1y}, ${c2x} ${c2y}, ${p2[0]} ${p2[1]}`
  }
  return d
}

function areaPath(data) {
  const base = smoothPath(data)
  if (!base) return ''
  const lastX = x(data.length - 1, data.length)
  const firstX = x(0, data.length)
  const baseY = pad.top + innerH.value
  return `${base} L ${lastX} ${baseY} L ${firstX} ${baseY} Z`
}

// Lưới ngang + nhãn trục Y (5 mức).
const yTicks = computed(() => {
  const n = 4
  return Array.from({ length: n + 1 }, (_, i) => {
    const v = (maxVal.value / n) * i
    return { v, y: y(v) }
  })
})

const points = computed(() =>
  props.series.map((s) => ({
    ...s,
    dots: s.data.map((v, i) => ({ cx: x(i, s.data.length), cy: y(v) })),
    line: smoothPath(s.data),
    area: areaPath(s.data),
  })),
)
</script>

<template>
  <div class="chart">
    <svg :viewBox="`0 0 ${W} ${H}`" preserveAspectRatio="none" class="chart__svg">
      <defs>
        <linearGradient
          v-for="s in series"
          :key="'g-' + s.name"
          :id="'grad-' + s.name"
          x1="0"
          y1="0"
          x2="0"
          y2="1"
        >
          <stop offset="0%" :stop-color="s.color" stop-opacity="0.28" />
          <stop offset="100%" :stop-color="s.color" stop-opacity="0" />
        </linearGradient>
      </defs>

      <!-- Lưới + nhãn Y -->
      <g class="chart__grid">
        <template v-for="t in yTicks" :key="'y' + t.v">
          <line :x1="pad.left" :y1="t.y" :x2="W - pad.right" :y2="t.y" />
          <text :x="pad.left - 8" :y="t.y + 4" text-anchor="end">{{ t.v }}</text>
        </template>
      </g>

      <!-- Nhãn X -->
      <g class="chart__xlabels">
        <text
          v-for="(lb, i) in labels"
          :key="'x' + i"
          :x="pad.left + (innerW * i) / Math.max(1, labels.length - 1)"
          :y="H - 8"
          text-anchor="middle"
        >
          {{ lb }}
        </text>
      </g>

      <!-- Vùng tô + đường + điểm -->
      <g v-for="s in points" :key="s.name">
        <path :d="s.area" :fill="`url(#grad-${s.name})`" />
        <path :d="s.line" :stroke="s.color" fill="none" stroke-width="2.5" />
        <circle
          v-for="(d, i) in s.dots"
          :key="i"
          :cx="d.cx"
          :cy="d.cy"
          r="3.2"
          :fill="s.color"
          stroke="#fff"
          stroke-width="1.5"
        />
      </g>
    </svg>

    <!-- Chú thích -->
    <ul class="chart__legend">
      <li v-for="s in series" :key="s.name">
        <span class="dot" :style="{ background: s.color }" />{{ s.name }}
      </li>
    </ul>
  </div>
</template>

<style scoped>
.chart {
  width: 100%;
}
.chart__svg {
  width: 100%;
  height: auto;
  display: block;
  overflow: visible;
}
.chart__grid line {
  stroke: var(--a-border);
  stroke-dasharray: 4 4;
}
.chart__grid text,
.chart__xlabels text {
  font-size: 11px;
  fill: var(--a-text-muted);
}
.chart__legend {
  list-style: none;
  display: flex;
  gap: 1.25rem;
  margin: 0.5rem 0 0;
  padding: 0;
}
.chart__legend li {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.82rem;
  color: var(--a-text-muted);
}
.chart__legend .dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
</style>
