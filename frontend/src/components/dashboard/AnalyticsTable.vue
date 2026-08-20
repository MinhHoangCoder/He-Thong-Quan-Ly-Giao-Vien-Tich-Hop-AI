<script setup>
/**
 * Bảng phân tích sâu — ba tab (giáo viên / trường / môn) dùng CHUNG một bảng.
 *
 * Ba chiều phân tích có cùng bộ chỉ số nên chỉ cần một bảng đổi nguồn dữ liệu, thay vì ba
 * bảng gần giống nhau phải sửa ba chỗ mỗi lần thêm một cột.
 *
 * Sắp xếp, tìm kiếm và phân trang chạy hoàn toàn ở phía trình duyệt. Được phép làm vậy vì
 * tập dữ liệu đã được SQL gom lại chỉ còn cỡ trăm dòng; gửi thêm một request cho mỗi lần
 * bấm đổi cột sẽ chậm hơn hẳn so với sắp xếp tại chỗ.
 *
 * CỘT SỐ CÓ THANH NỀN. Đọc một cột số dài mà không có gì so sánh thì phải dò từng dòng;
 * thanh nền mờ sau con số cho thấy ngay dòng nào trội hẳn.
 */
import { computed, ref, watch } from 'vue'
import SvgIcon from '@/components/ui/SvgIcon.vue'
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

const tab = ref('GIAO_VIEN')
const tim = ref('')
const sapXep = ref({ cot: 'buoiDay', giam: true })
const trang = ref(1)
const MOI_TRANG = 10

const nguon = computed(
  () =>
    ({ GIAO_VIEN: props.theoGiaoVien, TRUONG: props.theoTruong, MON: props.theoMon })[tab.value] ??
    [],
)

const tabHienTai = computed(() => TABS.find((t) => t.ma === tab.value))

/** Cột "Điểm đánh giá" chỉ có nghĩa với người: hệ thống đánh giá giáo viên, không đánh giá môn. */
const coDanhGia = computed(() => tab.value !== 'MON')

const COT = computed(() =>
  [
    { ma: 'ten', nhan: tabHienTai.value.cot1, kieu: 'chu' },
    { ma: 'buoiDay', nhan: 'Buổi dạy', kieu: 'so' },
    // Kiểu riêng cho giờ giảng: LUÔN một chữ số thập phân. Để chung kiểu 'so' thì 144 hiện
    // là "144" còn 144,75 hiện là "144,8" — hai dòng cạnh nhau lệch định dạng, mắt đọc cột
    // số sẽ vấp ở đúng chỗ đáng lẽ phải lướt qua.
    { ma: 'gioGiang', nhan: 'Giờ giảng', kieu: 'gio' },
    { ma: 'tyLeDuyet', nhan: 'Tỉ lệ duyệt', kieu: 'pt' },
    { ma: 'chuyenCan', nhan: 'Chuyên cần', kieu: 'pt' },
    coDanhGia.value ? { ma: 'diemDanhGia', nhan: 'Đánh giá', kieu: 'diem' } : null,
    { ma: 'chiPhi', nhan: 'Chi phí', kieu: 'tien' },
  ].filter(Boolean),
)

const locVaSap = computed(() => {
  const tuKhoa = tim.value.trim().toLowerCase()
  const ds = tuKhoa
    ? nguon.value.filter(
        (d) => d.ten?.toLowerCase().includes(tuKhoa) || d.phu?.toLowerCase().includes(tuKhoa),
      )
    : [...nguon.value]

  const { cot, giam } = sapXep.value
  ds.sort((a, b) => {
    const x = a[cot]
    const y = b[cot]
    if (typeof x === 'string' || typeof y === 'string') {
      return (giam ? -1 : 1) * String(x ?? '').localeCompare(String(y ?? ''), 'vi')
    }
    // Ô chưa có dữ liệu (null) luôn nằm CUỐI dù đang sắp tăng hay giảm — coi null là 0
    // sẽ đẩy "chưa đánh giá" lên đầu bảng xếp hạng ngược và gây hiểu nhầm là điểm kém.
    if (x == null && y == null) return 0
    if (x == null) return 1
    if (y == null) return -1
    return giam ? y - x : x - y
  })
  return ds
})

const soTrang = computed(() => Math.max(1, Math.ceil(locVaSap.value.length / MOI_TRANG)))
const dongHienThi = computed(() =>
  locVaSap.value.slice((trang.value - 1) * MOI_TRANG, trang.value * MOI_TRANG),
)

/** Đỉnh của từng cột số, dùng để vẽ thanh nền tương đối. Tính trên TOÀN tập đã lọc,
 *  không phải trên trang đang xem — nếu không, mỗi lần lật trang thanh lại đổi tỉ lệ. */
const dinh = computed(() => {
  const d = {}
  for (const c of COT.value) {
    if (c.kieu === 'so' || c.kieu === 'gio' || c.kieu === 'tien') {
      d[c.ma] = Math.max(1, ...locVaSap.value.map((r) => r[c.ma] ?? 0))
    }
  }
  return d
})

function doiCot(ma) {
  if (sapXep.value.cot === ma) {
    sapXep.value = { cot: ma, giam: !sapXep.value.giam }
  } else {
    sapXep.value = { cot: ma, giam: ma !== 'ten' }
  }
  trang.value = 1
}

watch([tab, tim], () => {
  trang.value = 1
})

function hienThi(giaTri, kieu) {
  if (giaTri == null) return '—'
  switch (kieu) {
    case 'tien':
      return tienDay(giaTri)
    case 'pt':
      return phanTram(giaTri)
    case 'diem':
      return soLe(giaTri, 1) + '/5'
    case 'gio':
      return soLe(giaTri, 1)
    case 'so':
      return soNguyen(giaTri)
    default:
      return giaTri
  }
}
</script>

<template>
  <section class="bang card">
    <header class="bang__head">
      <div class="bang__tabs" role="tablist">
        <button
          v-for="t in TABS"
          :key="t.ma"
          role="tab"
          type="button"
          :aria-selected="tab === t.ma"
          class="bang__tab"
          :class="{ 'is-chon': tab === t.ma }"
          @click="tab = t.ma"
        >
          {{ t.nhan }}
        </button>
      </div>

      <div class="bang__cong-cu">
        <label class="bang__tim">
          <SvgIcon name="search" :size="15" />
          <input
            v-model="tim"
            type="search"
            :placeholder="'Tìm ' + tabHienTai.cot1.toLowerCase()"
          />
        </label>
        <button type="button" class="bang__xuat" @click="emit('xuat', tab)">
          <SvgIcon name="copy" :size="15" /> Xuất CSV
        </button>
      </div>
    </header>

    <div class="bang__wrap">
      <table class="bang__table">
        <thead>
          <tr>
            <th
              v-for="c in COT"
              :key="c.ma"
              :class="[c.kieu === 'chu' ? 'ta-l' : 'ta-r', { 'is-sap': sapXep.cot === c.ma }]"
              :aria-sort="sapXep.cot === c.ma ? (sapXep.giam ? 'descending' : 'ascending') : 'none'"
            >
              <button type="button" @click="doiCot(c.ma)">
                {{ c.nhan }}
                <SvgIcon
                  v-if="sapXep.cot === c.ma"
                  :name="sapXep.giam ? 'down' : 'up'"
                  :size="12"
                />
              </button>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(r, i) in dongHienThi" :key="r.id ?? r.ten">
            <td v-for="c in COT" :key="c.ma" :class="c.kieu === 'chu' ? 'ta-l' : 'ta-r'">
              <template v-if="c.kieu === 'chu'">
                <span class="bang__hang">{{ (trang - 1) * MOI_TRANG + i + 1 }}</span>
                <span class="bang__ten">
                  <strong>{{ r.ten }}</strong>
                  <small>{{ r.phu }}</small>
                </span>
              </template>
              <template v-else>
                <span
                  v-if="dinh[c.ma]"
                  class="bang__thanh"
                  :style="{ width: ((r[c.ma] ?? 0) / dinh[c.ma]) * 100 + '%' }"
                />
                <span class="bang__so">{{ hienThi(r[c.ma], c.kieu) }}</span>
              </template>
            </td>
          </tr>
          <tr v-if="!dongHienThi.length">
            <td :colspan="COT.length" class="bang__trong">
              Không có dòng nào khớp với bộ lọc đang áp.
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <footer v-if="soTrang > 1" class="bang__chan">
      <span>{{ locVaSap.length }} dòng · trang {{ trang }}/{{ soTrang }}</span>
      <div class="bang__lat">
        <button type="button" :disabled="trang === 1" @click="trang--">Trước</button>
        <button type="button" :disabled="trang === soTrang" @click="trang++">Sau</button>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.bang__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
  margin-bottom: 0.8rem;
}
.bang__tabs {
  display: flex;
  gap: 0.2rem;
  padding: 0.22rem;
  border-radius: 10px;
  background: var(--c-surface-2, var(--a-bg));
}
.bang__tab {
  padding: 0.4rem 0.85rem;
  border: none;
  border-radius: 8px;
  background: none;
  color: var(--a-text-muted);
  font-size: 0.83rem;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--t-fast);
}
.bang__tab.is-chon {
  background: var(--c-surface);
  color: var(--c-primary);
  box-shadow: var(--a-shadow);
}
.bang__cong-cu {
  display: flex;
  gap: 0.45rem;
  align-items: center;
}
.bang__tim {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 0.65rem;
  border: 1px solid var(--a-border);
  border-radius: 9px;
  color: var(--a-text-muted);
}
.bang__tim input {
  border: none;
  outline: none;
  background: none;
  color: var(--a-text);
  font-size: 0.83rem;
  font-family: inherit;
  width: 10rem;
}
.bang__xuat {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.42rem 0.75rem;
  border: 1px solid var(--a-border);
  border-radius: 9px;
  background: var(--c-surface);
  color: var(--a-text-muted);
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
}
.bang__xuat:hover {
  border-color: var(--c-primary);
  color: var(--c-primary);
}
.bang__wrap {
  overflow-x: auto;
}
.bang__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.84rem;
  min-width: 640px;
}
.bang__table th {
  padding: 0 0 0.5rem;
  border-bottom: 1px solid var(--a-border);
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--a-text-muted);
  white-space: nowrap;
}
.bang__table th button {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  border: none;
  background: none;
  color: inherit;
  font: inherit;
  cursor: pointer;
  padding: 0.3rem 0.5rem;
  border-radius: 6px;
}
.bang__table th button:hover {
  color: var(--c-primary);
}
.bang__table th.is-sap button {
  color: var(--c-primary);
}
.bang__table td {
  position: relative;
  padding: 0.5rem;
  border-bottom: 1px solid var(--a-border);
  color: var(--a-text);
  vertical-align: middle;
}
.bang__table tbody tr:hover td {
  background: color-mix(in srgb, var(--c-primary) 5%, transparent);
}
.ta-l {
  text-align: left;
}
.ta-r {
  text-align: right;
}
.bang__hang {
  display: inline-block;
  width: 1.7rem;
  font-size: 0.74rem;
  color: var(--a-text-muted);
  font-variant-numeric: tabular-nums;
}
.bang__ten {
  display: inline-flex;
  flex-direction: column;
  vertical-align: middle;
}
.bang__ten strong {
  font-weight: 600;
}
.bang__ten small {
  font-size: 0.72rem;
  color: var(--a-text-muted);
}
/* Thanh nền nằm DƯỚI con số (z-index âm) để chữ luôn đọc được trên mọi nền. */
.bang__thanh {
  position: absolute;
  right: 0.5rem;
  bottom: 0.28rem;
  height: 3px;
  border-radius: 2px;
  background: color-mix(in srgb, var(--c-primary) 38%, transparent);
  max-width: calc(100% - 1rem);
}
.bang__so {
  position: relative;
  font-variant-numeric: tabular-nums;
}
.bang__trong {
  text-align: center;
  color: var(--a-text-muted);
  padding: 2rem 1rem;
}
.bang__chan {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-top: 0.7rem;
  font-size: 0.78rem;
  color: var(--a-text-muted);
}
.bang__lat {
  display: flex;
  gap: 0.35rem;
}
.bang__lat button {
  padding: 0.32rem 0.7rem;
  border: 1px solid var(--a-border);
  border-radius: 8px;
  background: var(--c-surface);
  color: var(--a-text-muted);
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
}
.bang__lat button:disabled {
  opacity: 0.45;
  cursor: default;
}

@media print {
  .bang__cong-cu,
  .bang__chan {
    display: none;
  }
}
</style>
