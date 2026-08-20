<script setup>
/**
 * Bảng thống kê chi tiết, 3 tab: theo giáo viên / theo trường / theo môn.
 * Ba tab cùng bộ cột nên dùng chung một bảng, chỉ đổi nguồn dữ liệu.
 *
 * Sắp xếp - tìm kiếm - phân trang đều làm ở client vì API đã gom sẵn, mỗi tab chỉ
 * vài chục tới hơn trăm dòng.
 */
import { computed, ref, watch } from 'vue'
import Pagination from '@/components/ui/Pagination.vue'
import { soNguyen, soLe, tienDay, phanTram } from '@/utils/thongKe'

const props = defineProps({
  theoGiaoVien: { type: Array, default: () => [] },
  theoTruong: { type: Array, default: () => [] },
  theoMon: { type: Array, default: () => [] },
})
const emit = defineEmits(['xuat'])

const TABS = [
  { ma: 'GIAO_VIEN', nhan: 'Theo giáo viên', cot1: 'Giáo viên' },
  { ma: 'TRUONG', nhan: 'Theo trường', cot1: 'Trường' },
  { ma: 'MON', nhan: 'Theo môn', cot1: 'Môn học' },
]
const MOI_TRANG = 10

const tab = ref('GIAO_VIEN')
const tuKhoa = ref('')
const sapXep = ref({ cot: 'buoiDay', giam: true })
const page = ref(0)

const nguon = computed(
  () =>
    ({ GIAO_VIEN: props.theoGiaoVien, TRUONG: props.theoTruong, MON: props.theoMon })[tab.value] ??
    [],
)
const tabHienTai = computed(() => TABS.find((t) => t.ma === tab.value))

const COT = computed(() =>
  [
    { ma: 'ten', nhan: tabHienTai.value.cot1, kieu: 'chu' },
    { ma: 'buoiDay', nhan: 'Số buổi', kieu: 'so' },
    { ma: 'gioGiang', nhan: 'Giờ giảng', kieu: 'gio' },
    { ma: 'tyLeDuyet', nhan: 'Tỉ lệ duyệt', kieu: 'pt' },
    { ma: 'chuyenCan', nhan: 'Chuyên cần', kieu: 'pt' },
    // Đánh giá là chấm điểm con người nên tab "Theo môn" không có cột này
    tab.value !== 'MON' ? { ma: 'diemDanhGia', nhan: 'Đánh giá', kieu: 'diem' } : null,
    { ma: 'chiPhi', nhan: 'Chi phí', kieu: 'tien' },
  ].filter(Boolean),
)

const danhSach = computed(() => {
  const k = tuKhoa.value.trim().toLowerCase()
  const ds = k
    ? nguon.value.filter(
        (d) => d.ten?.toLowerCase().includes(k) || d.phu?.toLowerCase().includes(k),
      )
    : [...nguon.value]

  const { cot, giam } = sapXep.value
  ds.sort((a, b) => {
    const x = a[cot]
    const y = b[cot]
    if (typeof x === 'string' || typeof y === 'string') {
      return (giam ? -1 : 1) * String(x ?? '').localeCompare(String(y ?? ''), 'vi')
    }
    // Ô null (chưa có đánh giá / chưa chấm công) luôn xuống cuối, không coi là 0
    if (x == null && y == null) return 0
    if (x == null) return 1
    if (y == null) return -1
    return giam ? y - x : x - y
  })
  return ds
})

const totalPages = computed(() => Math.max(1, Math.ceil(danhSach.value.length / MOI_TRANG)))
const dongHienThi = computed(() =>
  danhSach.value.slice(page.value * MOI_TRANG, (page.value + 1) * MOI_TRANG),
)

function doiCot(ma) {
  if (sapXep.value.cot === ma) sapXep.value = { cot: ma, giam: !sapXep.value.giam }
  else sapXep.value = { cot: ma, giam: ma !== 'ten' }
  page.value = 0
}

watch([tab, tuKhoa], () => {
  page.value = 0
})

function hienThi(v, kieu) {
  if (v == null) return '—'
  if (kieu === 'tien') return tienDay(v)
  if (kieu === 'pt') return phanTram(v)
  if (kieu === 'diem') return soLe(v, 1) + '/5'
  if (kieu === 'gio') return soLe(v, 1)
  return soNguyen(v)
}
</script>

<template>
  <div>
    <div class="toolbar">
      <button
        v-for="t in TABS"
        :key="t.ma"
        class="btn btn-sm"
        :class="tab === t.ma ? 'btn-primary' : 'btn-outline'"
        @click="tab = t.ma"
      >
        {{ t.nhan }}
      </button>
      <span class="divider" />
      <input v-model="tuKhoa" type="search" :placeholder="'Tìm ' + tabHienTai.cot1.toLowerCase()" />
      <button class="btn btn-outline btn-sm" @click="emit('xuat', tab)">Xuất CSV</button>
    </div>

    <div class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th
              v-for="c in COT"
              :key="c.ma"
              :class="c.kieu === 'chu' ? '' : 'num'"
              :aria-sort="sapXep.cot === c.ma ? (sapXep.giam ? 'descending' : 'ascending') : 'none'"
              @click="doiCot(c.ma)"
            >
              {{ c.nhan }}
              <span v-if="sapXep.cot === c.ma">{{ sapXep.giam ? '▼' : '▲' }}</span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(r, i) in dongHienThi" :key="r.id ?? r.ten">
            <td v-for="c in COT" :key="c.ma" :class="c.kieu === 'chu' ? '' : 'num'">
              <template v-if="c.kieu === 'chu'">
                <span class="stt">{{ page * MOI_TRANG + i + 1 }}.</span>
                <strong>{{ r.ten }}</strong>
                <div class="sub">{{ r.phu }}</div>
              </template>
              <template v-else>{{ hienThi(r[c.ma], c.kieu) }}</template>
            </td>
          </tr>
          <tr v-if="!dongHienThi.length">
            <td :colspan="COT.length" class="empty">Không có dữ liệu.</td>
          </tr>
        </tbody>
      </table>
    </div>

    <Pagination v-if="totalPages > 1" v-model="page" :total-pages="totalPages" />
  </div>
</template>

<style scoped>
.toolbar input[type='search'] {
  padding: 0.45rem 0.7rem;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  font-size: 0.88rem;
  background: var(--c-surface);
  color: var(--c-text);
  min-width: 12rem;
}
.divider {
  width: 1px;
  height: 22px;
  background: var(--c-border);
  margin: 0 0.3rem;
}
.table th {
  cursor: pointer;
  user-select: none;
}
.table th.num,
.table td.num {
  text-align: right;
  white-space: nowrap;
}
.stt {
  color: var(--c-text-muted);
  margin-right: 0.25rem;
}
.sub {
  font-size: 0.76rem;
  color: var(--c-text-muted);
}
.empty {
  text-align: center;
  color: var(--c-text-muted);
  padding: 1.6rem;
}
</style>
