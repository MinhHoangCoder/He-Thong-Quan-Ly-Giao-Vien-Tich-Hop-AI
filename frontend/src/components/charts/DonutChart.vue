<script setup>
/**
 * Biểu đồ TRÒN KHUYẾT + bảng chú giải có số liệu — SVG thuần.
 *
 * Bấm vào một lát sẽ phát sự kiện `chon` để cả trang lọc theo lát đó (lọc chéo). Đây là
 * điều biến biểu đồ từ một bức tranh thành một công cụ: thấy "STEM chiếm 34%" rồi bấm
 * vào là xem được ngay 34% ấy gồm những trường nào, giáo viên nào.
 *
 * Lỗ ở giữa không phải để trang trí — nó là chỗ đặt TỔNG, con số mà người xem biểu đồ
 * tròn nào cũng phải tự cộng nhẩm nếu thiếu.
 */
import { computed, ref } from 'vue'
// Dùng chung bộ định dạng của Bảng điều khiển: tiếng Việt dùng dấu PHẨY thập phân, nên
// `toFixed()` của JavaScript ("33.5%") sẽ lệch với mọi con số khác trên cùng màn hình.
import { phanTram } from '@/utils/thongKe'

const props = defineProps({
  /** [{ id, nhan, giaTri, mau }] */
  lat: { type: Array, required: true },
  /** Id của lát đang được chọn để lọc; null = chưa lọc. */
  dangChon: { type: [Number, String], default: null },
  donVi: { type: String, default: '' },
})
const emit = defineEmits(['chon'])

const R = 100
const DAY = 26 // độ dày vành
const tong = computed(() => props.lat.reduce((s, l) => s + l.giaTri, 0))
const dangDi = ref(null)

/**
 * Chuyển một lát (từ góc a1 tới a2) thành lệnh vẽ SVG.
 *
 * Góc 0 đặt ở vị trí 12 giờ (trừ 90°) rồi chạy theo chiều kim đồng hồ, vì người đọc
 * tiếng Việt bắt đầu nhìn biểu đồ tròn từ đỉnh. Cờ `large-arc` phải bật khi lát rộng
 * hơn nửa vòng, không thì SVG vẽ ngược ra phần bù.
 */
function cung(a1, a2, r) {
  const toXY = (a) => {
    const rad = ((a - 90) * Math.PI) / 180
    return [Math.cos(rad) * r, Math.sin(rad) * r]
  }
  const [x1, y1] = toXY(a1)
  const [x2, y2] = toXY(a2)
  return { x1, y1, x2, y2, large: a2 - a1 > 180 ? 1 : 0 }
}

const cacLat = computed(() => {
  let goc = 0
  return props.lat.map((l) => {
    // Đặt tên `tiLe` chứ không phải `phanTram`: trùng tên với hàm định dạng vừa import ở trên
    // thì tuy vẫn chạy đúng (khác tầng khai báo) nhưng người đọc sau này rất dễ tưởng đang gọi
    // hàm và sửa nhầm.
    const tiLe = tong.value === 0 ? 0 : (l.giaTri / tong.value) * 100
    // Chừa 1° khe giữa các lát cho ranh giới rõ mà không cần vẽ viền trắng
    // (viền trắng sẽ biến mất khi trang chuyển sang nền tối).
    const a1 = goc
    const a2 = goc + (tiLe / 100) * 360
    goc = a2
    const ngoai = cung(a1, Math.max(a1, a2 - 1), R)
    const trong = cung(a1, Math.max(a1, a2 - 1), R - DAY)
    return {
      ...l,
      phanTram: tiLe,
      d:
        `M ${ngoai.x1} ${ngoai.y1} A ${R} ${R} 0 ${ngoai.large} 1 ${ngoai.x2} ${ngoai.y2}` +
        ` L ${trong.x2} ${trong.y2} A ${R - DAY} ${R - DAY} 0 ${trong.large} 0 ${trong.x1} ${trong.y1} Z`,
    }
  })
})

function bam(l) {
  emit('chon', props.dangChon === l.id ? null : l.id)
}

const soGon = (v) => new Intl.NumberFormat('vi-VN').format(Math.round(v))
</script>

<template>
  <div class="donut">
    <svg viewBox="-115 -115 230 230" class="donut__svg" role="img" aria-label="Biểu đồ cơ cấu">
      <g>
        <path
          v-for="l in cacLat"
          :key="l.nhan"
          :d="l.d"
          :fill="l.mau"
          class="donut__lat"
          :class="{
            'is-chon': dangChon === l.id,
            'is-mo':
              (dangChon !== null && dangChon !== l.id) || (dangDi !== null && dangDi !== l.id),
          }"
          @mouseenter="dangDi = l.id"
          @mouseleave="dangDi = null"
          @click="bam(l)"
        />
      </g>
      <text class="donut__tong" x="0" y="-2" text-anchor="middle">{{ soGon(tong) }}</text>
      <text class="donut__donvi" x="0" y="18" text-anchor="middle">{{ donVi }}</text>
    </svg>

    <ul class="donut__legend">
      <li
        v-for="l in cacLat"
        :key="l.nhan"
        :class="{ 'is-chon': dangChon === l.id }"
        @mouseenter="dangDi = l.id"
        @mouseleave="dangDi = null"
        @click="bam(l)"
      >
        <i :style="{ background: l.mau }" />
        <span class="ten">{{ l.nhan }}</span>
        <span class="so">{{ soGon(l.giaTri) }}</span>
        <span class="pt">{{ phanTram(l.phanTram) }}</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.donut {
  display: flex;
  align-items: center;
  gap: 1.4rem;
  flex-wrap: wrap;
}
.donut__svg {
  width: 190px;
  height: 190px;
  flex: none;
}
.donut__lat {
  cursor: pointer;
  transition:
    opacity var(--t-fast),
    transform var(--t-fast);
  transform-origin: center;
}
.donut__lat.is-mo {
  opacity: 0.3;
}
.donut__lat.is-chon {
  transform: scale(1.045);
}
.donut__tong {
  font-size: 26px;
  font-weight: 700;
  fill: var(--a-text);
}
.donut__donvi {
  font-size: 11px;
  fill: var(--a-text-muted);
}
.donut__legend {
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1 1 240px;
  min-width: 0;
}
.donut__legend li {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.36rem 0.5rem;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.85rem;
  color: var(--a-text);
  transition: background var(--t-fast);
}
.donut__legend li:hover,
.donut__legend li.is-chon {
  background: color-mix(in srgb, var(--c-primary) 10%, transparent);
}
.donut__legend i {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex: none;
}
.donut__legend .ten {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.donut__legend .so {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
.donut__legend .pt {
  font-variant-numeric: tabular-nums;
  color: var(--a-text-muted);
  width: 3.4rem;
  text-align: right;
}
</style>
