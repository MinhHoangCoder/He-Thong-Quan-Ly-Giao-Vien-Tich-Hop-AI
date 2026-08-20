<script setup>
/**
 * Thanh lọc áp cho TOÀN Bảng điều khiển.
 *
 * Đây là thứ biến một trang báo cáo tĩnh thành công cụ phân tích: cùng một màn hình trả lời
 * được "cả trung tâm năm nay", "riêng THCS Chu Văn An quý này" hay "môn Tiếng Anh tháng 3".
 *
 * Bộ lọc được ĐỒNG BỘ LÊN THANH ĐỊA CHỈ (do trang cha lo). Nhờ vậy F5 không mất bộ lọc, nút
 * quay lại của trình duyệt hoạt động đúng, và một câu hỏi khó có thể gửi cho người khác bằng
 * cách dán đường dẫn thay vì mô tả sáu bước bấm chuột.
 */
import { computed } from 'vue'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import { cacKyDungSan } from '@/utils/thongKe'

const props = defineProps({
  /** { from, to, branchId, schoolId, categoryId } */
  boLoc: { type: Object, required: true },
  /** { chiNhanh: [], truong: [], nhomMon: [] } */
  danhMuc: { type: Object, default: () => ({ chiNhanh: [], truong: [], nhomMon: [] }) },
  dangTai: { type: Boolean, default: false },
  tinhDenLuc: { type: String, default: '' },
})
const emit = defineEmits(['doi', 'lamMoi', 'xuat', 'in'])

const kyDungSan = cacKyDungSan()

/** Kỳ dựng sẵn nào đang khớp với hai mốc ngày hiện tại (để tô sáng đúng nút). */
const kyDangChon = computed(
  () =>
    kyDungSan.find((k) => k.from === props.boLoc.from && k.to === props.boLoc.to)?.ma ?? 'tuychon',
)

function dat(phan) {
  emit('doi', { ...props.boLoc, ...phan })
}

function chonKy(k) {
  dat({ from: k.from, to: k.to })
}

/** Ô chọn trả về chuỗi rỗng khi ở mục "Tất cả" — phải đổi thành null, không phải 0. */
const soHoacNull = (v) => (v === '' || v === null ? null : Number(v))

const coLocPhu = computed(
  () => props.boLoc.branchId || props.boLoc.schoolId || props.boLoc.categoryId,
)

const chipDangAp = computed(() => {
  const ds = []
  const tim = (mang, id) => mang.find((x) => x.id === id)?.ten
  if (props.boLoc.branchId) {
    ds.push({ khoa: 'branchId', nhan: tim(props.danhMuc.chiNhanh, props.boLoc.branchId) })
  }
  if (props.boLoc.schoolId) {
    ds.push({ khoa: 'schoolId', nhan: tim(props.danhMuc.truong, props.boLoc.schoolId) })
  }
  if (props.boLoc.categoryId) {
    ds.push({ khoa: 'categoryId', nhan: tim(props.danhMuc.nhomMon, props.boLoc.categoryId) })
  }
  return ds.filter((c) => c.nhan)
})

function xoaChip(khoa) {
  dat({ [khoa]: null })
}
function xoaHet() {
  dat({ branchId: null, schoolId: null, categoryId: null })
}
</script>

<template>
  <section class="loc" :class="{ 'is-tai': dangTai }">
    <!-- Hàng 1: các kỳ dựng sẵn + hai ô ngày tuỳ chọn -->
    <div class="loc__hang">
      <div class="loc__ky" role="group" aria-label="Chọn kỳ báo cáo">
        <button
          v-for="k in kyDungSan"
          :key="k.ma"
          type="button"
          class="loc__chip"
          :class="{ 'is-chon': kyDangChon === k.ma }"
          @click="chonKy(k)"
        >
          {{ k.nhan }}
        </button>
      </div>

      <div class="loc__ngay">
        <label>
          <span class="sr">Từ ngày</span>
          <input
            type="date"
            :value="boLoc.from"
            :max="boLoc.to"
            @change="dat({ from: $event.target.value })"
          />
        </label>
        <span class="loc__gach">→</span>
        <label>
          <span class="sr">Đến ngày</span>
          <input
            type="date"
            :value="boLoc.to"
            :min="boLoc.from"
            @change="dat({ to: $event.target.value })"
          />
        </label>
      </div>
    </div>

    <!-- Hàng 2: ba ô lọc phạm vi + nhóm nút hành động -->
    <div class="loc__hang">
      <div class="loc__chon">
        <select
          v-if="danhMuc.chiNhanh.length > 1"
          :value="boLoc.branchId ?? ''"
          aria-label="Chi nhánh"
          @change="dat({ branchId: soHoacNull($event.target.value) })"
        >
          <option value="">Mọi chi nhánh</option>
          <option v-for="c in danhMuc.chiNhanh" :key="c.id" :value="c.id">{{ c.ten }}</option>
        </select>

        <select
          :value="boLoc.schoolId ?? ''"
          aria-label="Trường khách hàng"
          @change="dat({ schoolId: soHoacNull($event.target.value) })"
        >
          <option value="">Mọi trường</option>
          <option v-for="t in danhMuc.truong" :key="t.id" :value="t.id">{{ t.ten }}</option>
        </select>

        <select
          :value="boLoc.categoryId ?? ''"
          aria-label="Nhóm môn"
          @change="dat({ categoryId: soHoacNull($event.target.value) })"
        >
          <option value="">Mọi nhóm môn</option>
          <option v-for="m in danhMuc.nhomMon" :key="m.id" :value="m.id">{{ m.ten }}</option>
        </select>
      </div>

      <div class="loc__nut">
        <button type="button" class="nut" :disabled="dangTai" @click="emit('lamMoi')">
          <SvgIcon name="refresh" :size="15" :class="{ quay: dangTai }" /> Làm mới
        </button>
        <button type="button" class="nut" @click="emit('xuat')">
          <SvgIcon name="copy" :size="15" /> Xuất Excel
        </button>
        <button type="button" class="nut" @click="emit('in')">
          <SvgIcon name="eye" :size="15" /> In báo cáo
        </button>
      </div>
    </div>

    <!-- Hàng 3 chỉ hiện khi có lọc phạm vi: nhắc người dùng số liệu đang bị thu hẹp.
         Không có dòng này, người xem rất dễ tưởng đang nhìn toàn trung tâm. -->
    <div v-if="coLocPhu || tinhDenLuc" class="loc__dang">
      <template v-if="chipDangAp.length">
        <span class="loc__dangLabel">Đang lọc:</span>
        <button v-for="c in chipDangAp" :key="c.khoa" class="loc__ap" @click="xoaChip(c.khoa)">
          {{ c.nhan }} <span aria-hidden="true">×</span>
        </button>
        <button class="loc__xoa" @click="xoaHet">Bỏ hết bộ lọc</button>
      </template>
      <span v-if="tinhDenLuc" class="loc__moc">Số liệu tính đến {{ tinhDenLuc }}</span>
    </div>
  </section>
</template>

<style scoped>
.loc {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  padding: 0.85rem 1rem;
  margin-bottom: 1.25rem;
  background: var(--c-surface);
  border: 1px solid var(--a-border);
  border-radius: 14px;
  transition: opacity var(--t-fast);
}
.loc.is-tai {
  opacity: 0.72;
}
.loc__hang {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  flex-wrap: wrap;
  justify-content: space-between;
}
.loc__ky {
  display: flex;
  gap: 0.35rem;
  flex-wrap: wrap;
}
.loc__chip {
  padding: 0.4rem 0.75rem;
  border-radius: 999px;
  border: 1px solid var(--a-border);
  background: transparent;
  color: var(--a-text-muted);
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--t-fast);
}
.loc__chip:hover {
  border-color: var(--c-primary);
  color: var(--c-primary);
}
.loc__chip.is-chon {
  background: var(--grad-primary);
  border-color: transparent;
  color: #fff;
}
.loc__ngay {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
.loc__gach {
  color: var(--a-text-muted);
  font-size: 0.85rem;
}
.loc__ngay input,
.loc__chon select {
  padding: 0.42rem 0.6rem;
  border: 1px solid var(--a-border);
  border-radius: 9px;
  background: var(--c-surface);
  color: var(--a-text);
  font-size: 0.83rem;
  font-family: inherit;
}
.loc__chon {
  display: flex;
  gap: 0.45rem;
  flex-wrap: wrap;
}
.loc__chon select {
  max-width: 15rem;
}
.loc__nut {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}
.nut {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.45rem 0.8rem;
  border-radius: 9px;
  border: 1px solid var(--a-border);
  background: var(--c-surface);
  color: var(--a-text-muted);
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--t-fast);
}
.nut:hover:not(:disabled) {
  border-color: var(--c-primary);
  color: var(--c-primary);
}
.nut:disabled {
  opacity: 0.55;
  cursor: default;
}
.quay {
  animation: quay 0.8s linear infinite;
}
@keyframes quay {
  to {
    transform: rotate(360deg);
  }
}
.loc__dang {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex-wrap: wrap;
  padding-top: 0.55rem;
  border-top: 1px dashed var(--a-border);
  font-size: 0.78rem;
}
.loc__dangLabel {
  color: var(--a-text-muted);
  font-weight: 600;
}
.loc__ap {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.2rem 0.55rem;
  border-radius: 999px;
  border: none;
  background: color-mix(in srgb, var(--c-primary) 14%, transparent);
  color: var(--c-primary);
  font-size: 0.76rem;
  font-weight: 600;
  cursor: pointer;
}
.loc__xoa {
  border: none;
  background: none;
  color: var(--a-text-muted);
  font-size: 0.76rem;
  text-decoration: underline;
  cursor: pointer;
}
.loc__moc {
  margin-left: auto;
  color: var(--a-text-muted);
}
.sr {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

@media print {
  .loc__nut,
  .loc__ky,
  .loc__chon {
    display: none;
  }
}
</style>
