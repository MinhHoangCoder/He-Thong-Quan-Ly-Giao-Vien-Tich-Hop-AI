<script setup>
/**
 * Biểu đồ THANH NGANG xếp hạng — dựng bằng CSS, không dùng SVG.
 *
 * Thanh NGANG chứ không phải cột đứng, vì nhãn ở đây là tên trường ("THCS Nguyễn Bá
 * Ngọc") chứ không phải "T9". Cột đứng buộc phải xoay nhãn 45° hoặc cắt bớt chữ; thanh
 * ngang thì tên nằm nguyên trên một dòng và mắt đọc theo đúng chiều tự nhiên.
 *
 * Mỗi thanh còn có một dải nền mờ chạy hết chiều rộng: nó là mốc 100%, giúp đọc được
 * "trường này bằng khoảng nửa trường dẫn đầu" mà không cần trục số.
 */
import { computed } from 'vue'

const props = defineProps({
  /** [{ id, nhan, giaTri, mau }] — đã sắp xếp giảm dần. */
  thanh: { type: Array, required: true },
  dangChon: { type: [Number, String], default: null },
  donVi: { type: String, default: '' },
})
const emit = defineEmits(['chon'])

const dinh = computed(() => Math.max(1, ...props.thanh.map((t) => t.giaTri)))
const soGon = (v) => new Intl.NumberFormat('vi-VN').format(v)
</script>

<template>
  <ul class="rank">
    <li
      v-for="(t, i) in thanh"
      :key="t.id ?? t.nhan"
      class="rank__row"
      :class="{ 'is-chon': dangChon === t.id, 'is-mo': dangChon !== null && dangChon !== t.id }"
      @click="emit('chon', dangChon === t.id ? null : t.id)"
    >
      <span class="rank__no">{{ i + 1 }}</span>
      <span class="rank__ten" :title="t.nhan">{{ t.nhan }}</span>
      <span class="rank__track">
        <span
          class="rank__fill"
          :style="{ width: (t.giaTri / dinh) * 100 + '%', background: t.mau }"
        />
      </span>
      <!-- Khoảng trắng viết bằng thực thể HTML: Vue cắt sạch khoảng trắng đứng đầu một thẻ
           trong template, nên viết " {{ donVi }}" sẽ ra "825buổi" dính liền. -->
      <span class="rank__so"
        >{{ soGon(t.giaTri) }}<em v-if="donVi">&nbsp;{{ donVi }}</em></span
      >
    </li>
  </ul>
</template>

<style scoped>
.rank {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}
.rank__row {
  display: grid;
  /* Cột thanh co giãn, ba cột còn lại giữ nguyên bề rộng để các dòng thẳng hàng. */
  grid-template-columns: 1.5rem minmax(6rem, 11rem) 1fr auto;
  align-items: center;
  gap: 0.6rem;
  padding: 0.42rem 0.45rem;
  border-radius: 8px;
  cursor: pointer;
  transition:
    background var(--t-fast),
    opacity var(--t-fast);
}
.rank__row:hover,
.rank__row.is-chon {
  background: color-mix(in srgb, var(--c-primary) 9%, transparent);
}
.rank__row.is-mo {
  opacity: 0.45;
}
.rank__no {
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--a-text-muted);
  text-align: right;
}
.rank__ten {
  font-size: 0.85rem;
  color: var(--a-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rank__track {
  height: 10px;
  border-radius: 5px;
  background: var(--a-border);
  overflow: hidden;
}
.rank__fill {
  display: block;
  height: 100%;
  border-radius: 5px;
  /* Thanh chạy ra từ 0 khi dữ liệu đổi — người xem thấy được là số vừa được nạp lại. */
  transition: width 0.55s var(--ease, ease-out);
}
.rank__so {
  font-size: 0.84rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--a-text);
  white-space: nowrap;
}
.rank__so em {
  font-style: normal;
  font-weight: 400;
  font-size: 0.76rem;
  color: var(--a-text-muted);
}
</style>
