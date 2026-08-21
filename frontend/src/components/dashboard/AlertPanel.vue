<script setup>
/**
 * Danh sách việc cần xử lý trên Bảng điều khiển.
 * Mỗi dòng bấm được, dẫn sang trang xử lý tương ứng.
 */
import { computed } from 'vue'

const props = defineProps({
  canhBao: { type: Array, required: true },
})
const emit = defineEmits(['mo'])

// on = mục đang không có việc gì; vẫn hiện để biết hệ thống có kiểm tra mục đó
const BADGE = { khan: 'badge-red', luuY: 'badge-amber', tin: 'badge-gray', on: 'badge-green' }

const tongViec = computed(() =>
  props.canhBao.filter((c) => c.muc !== 'on').reduce((s, c) => s + c.soLuong, 0),
)

// Việc khẩn lên trước, mục đã ổn xuống cuối
const THU_TU = { khan: 0, luuY: 1, tin: 2, on: 3 }
const dsSapXep = computed(() =>
  [...props.canhBao].sort((a, b) => THU_TU[a.muc] - THU_TU[b.muc] || b.soLuong - a.soLuong),
)
</script>

<template>
  <div class="table-wrap">
    <div class="panel-head">
      <h3>Việc cần xử lý</h3>
      <span class="panel-count">{{ tongViec }}</span>
    </div>
    <table class="table">
      <tbody>
        <tr v-for="c in dsSapXep" :key="c.key" @click="emit('mo', c.route)">
          <td>
            <div class="alert-name">{{ c.nhan }}</div>
            <div class="alert-desc">{{ c.moTa }}</div>
          </td>
          <td class="num">
            <span class="badge" :class="BADGE[c.muc]">{{ c.soLuong }}</span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.7rem 1rem;
  border-bottom: 1px solid var(--c-border);
  background: var(--c-surface-2);
}
.panel-head h3 {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--c-text);
}
.panel-count {
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--c-text-muted);
}
.table tbody tr {
  cursor: pointer;
}
.alert-name {
  font-size: 0.86rem;
  font-weight: 600;
  color: var(--c-text);
}
.alert-desc {
  font-size: 0.78rem;
  color: var(--c-text-muted);
  margin-top: 0.1rem;
}
.num {
  text-align: right;
  white-space: nowrap;
}
</style>
