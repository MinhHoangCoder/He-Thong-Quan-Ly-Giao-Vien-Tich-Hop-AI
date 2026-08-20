<script setup>
/**
 * Thẻ chỉ số của Bảng điều khiển.
 *
 * Ba điểm khác thẻ cũ, mỗi điểm sửa một chỗ từng gây hiểu nhầm:
 *
 * 1. CÓ ĐỐI CHIẾU KỲ TRƯỚC. Một con số trần không nói được điều gì — 11.557 buổi là nhiều hay
 *    ít? Chỉ khi đặt cạnh kỳ trước nó mới thành thông tin.
 * 2. CÓ DẤU HỎI GIẢI THÍCH CÔNG THỨC. Câu đầu tiên người chấm hỏi trước một con số thống kê
 *    luôn là "cái này tính thế nào"; câu trả lời nằm ngay trên thẻ.
 * 3. TĂNG KHÔNG MẶC NHIÊN LÀ TỐT. Chi phí lương tăng 20% là tin xấu. Thẻ nhận thêm cờ
 *    `tangLaTot` để tô màu theo Ý NGHĨA nghiệp vụ chứ không theo dấu của phép trừ.
 */
import { computed, ref } from 'vue'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import { theoMa, phanTram } from '@/utils/thongKe'

const props = defineProps({
  /** Đối tượng Kpi do API /dashboard/summary trả về. */
  kpi: { type: Object, required: true },
  /** false = giá trị tăng là tín hiệu XẤU (dùng cho chi phí). */
  tangLaTot: { type: Boolean, default: true },
})
const emit = defineEmits(['mo'])

const hienGiaiThich = ref(false)

const giaTri = computed(() => theoMa(props.kpi.giaTri, props.kpi.dinhDang))
const coSoSanh = computed(() => props.kpi.thayDoi !== null && props.kpi.thayDoi !== undefined)
const tang = computed(() => (props.kpi.thayDoi ?? 0) >= 0)
/** Màu của chip so sánh: xanh khi diễn biến CÓ LỢI, đỏ khi bất lợi. */
const tot = computed(() => (tang.value ? props.tangLaTot : !props.tangLaTot))
</script>

<template>
  <div class="kpi" :style="{ '--nhan': kpi.mau }">
    <button class="kpi__main" type="button" @click="emit('mo', kpi.route)">
      <span class="kpi__icon"><SvgIcon :name="kpi.icon" :size="19" /></span>

      <span class="kpi__label">{{ kpi.nhan }}</span>

      <span class="kpi__value">{{ giaTri }}</span>

      <span class="kpi__row">
        <span v-if="coSoSanh" class="kpi__delta" :class="tot ? 'is-tot' : 'is-xau'">
          <SvgIcon :name="tang ? 'up' : 'down'" :size="12" />
          {{ phanTram(Math.abs(kpi.thayDoi)) }}
        </span>
        <span v-else class="kpi__delta is-trong" title="Kỳ trước không có dữ liệu để đối chiếu">
          chưa có kỳ đối chiếu
        </span>
      </span>

      <span class="kpi__sub">{{ kpi.phu }}</span>
    </button>

    <!-- Dấu hỏi tách khỏi nút chính: bấm để xem công thức chứ không nhảy sang trang khác. -->
    <button
      class="kpi__help"
      type="button"
      :aria-expanded="hienGiaiThich"
      :aria-label="'Cách tính ' + kpi.nhan"
      @click.stop="hienGiaiThich = !hienGiaiThich"
    >
      ?
    </button>

    <transition name="fade">
      <p v-if="hienGiaiThich" class="kpi__note">
        {{ kpi.giaiThich }}
        <template v-if="kpi.giaTriKyTruoc !== null && kpi.giaTriKyTruoc !== undefined">
          <br /><b>Kỳ trước:</b> {{ theoMa(kpi.giaTriKyTruoc, kpi.dinhDang) }}
        </template>
      </p>
    </transition>
  </div>
</template>

<style scoped>
.kpi {
  position: relative;
  background: var(--c-surface);
  border: 1px solid var(--a-border);
  border-radius: 14px;
  overflow: hidden;
  transition:
    transform var(--t-fast),
    box-shadow var(--t-fast),
    border-color var(--t-fast);
}
/* Vệt màu trái là thứ duy nhất phân biệt sáu thẻ khi liếc nhanh. */
.kpi::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: var(--nhan);
}
.kpi:hover {
  transform: translateY(-2px);
  box-shadow: var(--a-shadow);
  border-color: color-mix(in srgb, var(--nhan) 45%, var(--a-border));
}
.kpi__main {
  display: grid;
  gap: 0.28rem;
  width: 100%;
  border: none;
  background: none;
  cursor: pointer;
  text-align: left;
  padding: 0.95rem 2.2rem 0.95rem 1.05rem;
  font: inherit;
  color: inherit;
}
.kpi__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  color: var(--nhan);
  background: color-mix(in srgb, var(--nhan) 13%, transparent);
}
.kpi__label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--a-text-muted);
  margin-top: 0.3rem;
}
.kpi__value {
  font-size: 1.55rem;
  font-weight: 700;
  line-height: 1.15;
  color: var(--a-text);
  font-variant-numeric: tabular-nums;
}
.kpi__row {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  min-height: 1.25rem;
}
.kpi__delta {
  display: inline-flex;
  align-items: center;
  gap: 0.18rem;
  font-size: 0.75rem;
  font-weight: 700;
  padding: 0.1rem 0.4rem;
  border-radius: 999px;
}
.kpi__delta.is-tot {
  color: #15803d;
  background: rgba(34, 197, 94, 0.14);
}
.kpi__delta.is-xau {
  color: #b91c1c;
  background: rgba(239, 68, 68, 0.13);
}
.kpi__delta.is-trong {
  color: var(--a-text-muted);
  background: var(--a-border);
  font-weight: 500;
}
:root[data-theme='dark'] .kpi__delta.is-tot {
  color: #4ade80;
}
:root[data-theme='dark'] .kpi__delta.is-xau {
  color: #f87171;
}
.kpi__sub {
  font-size: 0.755rem;
  color: var(--a-text-muted);
  line-height: 1.35;
}
.kpi__help {
  position: absolute;
  top: 0.7rem;
  right: 0.7rem;
  width: 19px;
  height: 19px;
  border-radius: 50%;
  border: 1px solid var(--a-border);
  background: var(--c-surface);
  color: var(--a-text-muted);
  font-size: 0.68rem;
  font-weight: 700;
  cursor: help;
  line-height: 1;
  transition:
    color var(--t-fast),
    border-color var(--t-fast);
}
.kpi__help:hover {
  color: var(--nhan);
  border-color: var(--nhan);
}
.kpi__note {
  margin: 0;
  padding: 0.65rem 1.05rem 0.85rem;
  font-size: 0.74rem;
  line-height: 1.5;
  color: var(--a-text-muted);
  background: color-mix(in srgb, var(--nhan) 6%, transparent);
  border-top: 1px solid var(--a-border);
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--t-fast);
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
