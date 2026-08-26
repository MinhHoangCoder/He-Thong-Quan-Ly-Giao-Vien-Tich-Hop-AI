<script setup>
/**
 * Trang Bảng điều khiển (admin): thống kê tổng hợp toàn trung tâm theo kỳ.
 *
 * Kỳ mặc định là NĂM HỌC hiện hành (01/9 - 31/8) chứ không phải tháng dương lịch: mở
 * dashboard vào tháng hè mà lấy "tháng này" thì cả trang ra số 0 trong khi năm học vừa
 * rồi có cả chục nghìn buổi dạy.
 *
 * Các API gọi song song và hiện độc lập — thẻ số về nhanh, biểu đồ nặng hơn nên về sau.
 *
 * BỘ LỌC CHỜ BẤM "ÁP DỤNG": người dùng thường đổi kỳ rồi đổi tiếp trường rồi đổi nhóm môn.
 * Gọi ngay mỗi lần đổi là 9 request cho một lần lọc, mà 6 request đầu không ai kịp đọc. Ở đây
 * ô chọn ghi vào `nhap`, bấm Áp dụng mới chép sang `boLoc` và gọi API.
 *
 * DỮ LIỆU NẰM Ở STORE, KHÔNG Ở COMPONENT (`stores/dashboard.js`): rời trang là Vue huỷ component,
 * quay lại là dựng mới — để state ở đây thì mỗi lần quay về phải tải lại từ đầu. Trang này giờ
 * chỉ giữ BỘ LỌC (thứ gắn với URL) và lo phần vẽ.
 */
import { reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { loiTaiFile } from '@/utils/download'
import StatCard from '@/components/ui/StatCard.vue'
import SearchSelect from '@/components/ui/SearchSelect.vue'
import LoadingSpinner from '@/components/ui/LoadingSpinner.vue'
import AlertPanel from '@/components/dashboard/AlertPanel.vue'
import AnalyticsTable from '@/components/dashboard/AnalyticsTable.vue'
import BarLineChart from '@/components/charts/BarLineChart.vue'
import PieChart from '@/components/charts/PieChart.vue'
import { dashboardApi } from '@/api/dashboard'
import { useDashboardStore } from '@/stores/dashboard'
import { cacKyDungSan, theoMa, gioNgan } from '@/utils/thongKe'

const router = useRouter()
const route = useRoute()
const kho = useDashboardStore()
const kyDungSan = cacKyDungSan()

/** Bộ lọc đang có hiệu lực (số liệu trên màn hình đang nói về kỳ này). */
const boLoc = reactive(docUrl())
/** Bộ lọc người dùng đang chọn dở, chưa bấm Áp dụng. */
const nhap = reactive({ ...boLoc })

// Chưa có gì để xem thì phủ kín. Đã có số cũ NHƯNG của bộ lọc khác thì làm mờ. Còn quay lại
// trang với đúng bộ lọc cũ thì không làm gì cả — số vẫn đúng, cứ hiện, lượt tải mới chạy ngầm.
const phuKin = computed(() => kho.tai.tomTat && !kho.coDuLieu)
const lamMo = computed(() => kho.dangTai && kho.coDuLieu && kho.lechLoc)

/* ══════════════════ Bộ lọc ══════════════════ */

function macDinh() {
  const k = kyDungSan[0]
  return { from: k.from, to: k.to, schoolId: null, categoryId: null }
}

/**
 * Đọc bộ lọc từ URL để F5 không mất và để copy link gửi được cho người khác.
 *
 * Tham số hỏng (ai đó sửa tay thanh địa chỉ) thì rơi về mặc định chứ không ném lỗi: URL là
 * đầu vào của người ngoài, không phải dữ liệu tin được.
 */
function docUrl() {
  const q = route.query
  const mac = macDinh()
  const ngay = (v) => (typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v) ? v : null)
  const so = (v) => (v != null && v !== '' && Number.isFinite(Number(v)) ? Number(v) : null)

  const from = ngay(q.from)
  const to = ngay(q.to)
  const hopLe = from && to && from <= to

  return {
    from: hopLe ? from : mac.from,
    to: hopLe ? to : mac.to,
    schoolId: so(q.schoolId),
    categoryId: so(q.categoryId),
  }
}

/** Chỉ đưa lên URL những khoá thực sự có giá trị, để link không lủng củng `&schoolId=`. */
function queryTu(f) {
  const q = { from: f.from, to: f.to }
  if (f.schoolId != null) q.schoolId = String(f.schoolId)
  if (f.categoryId != null) q.categoryId = String(f.categoryId)
  return q
}

const kyDangChon = computed(
  () => kyDungSan.find((k) => k.from === nhap.from && k.to === nhap.to)?.ma ?? '',
)
/** Có thay đổi chưa áp dụng — dùng để làm nổi nút Áp dụng. */
const choApDung = computed(() => Object.keys(boLoc).some((k) => boLoc[k] !== nhap[k]))
const dangLoc = computed(
  () => nhap.schoolId != null || nhap.categoryId != null || kyDangChon.value !== kyDungSan[0].ma,
)

function chonKy(ma) {
  const k = kyDungSan.find((x) => x.ma === ma)
  if (!k) return
  nhap.from = k.from
  nhap.to = k.to
}

/** SearchSelect trả '' khi bỏ chọn; phải đổi thành null chứ không phải 0. */
function chonMuc(khoa, giaTri) {
  nhap[khoa] = giaTri === '' || giaTri == null ? null : Number(giaTri)
}

function apDung() {
  Object.assign(boLoc, nhap)
  router.push({ query: queryTu(boLoc) })
  load()
}

function xoaLoc() {
  Object.assign(nhap, macDinh())
  apDung()
}

/** Danh mục trả về {id, ten}; SearchSelect cần {id, name}. */
const truongChon = computed(() => kho.danhMuc.truong.map((t) => ({ id: t.id, name: t.ten })))
const nhomMonChon = computed(() => kho.danhMuc.nhomMon.map((m) => ({ id: m.id, name: m.ten })))

/* ══════════════════ Tải số liệu ══════════════════ */

function load() {
  kho.nap({ ...boLoc })
}

/** Đổi tab bảng chi tiết — chiều nào chưa có trong bộ đệm thì mới gọi API. */
function doiTab(chieu) {
  kho.doiTab({ ...boLoc }, chieu)
}

/**
 * Vị trí cuộn: tự khôi phục thay vì nhờ `scrollBehavior` của router.
 *
 * Router chỉ khôi phục đúng khi trang đã cao trở lại như cũ, mà chiều cao ở đây phụ thuộc dữ
 * liệu. Nhờ store giữ sẵn số cũ nên ngay sau `nextTick` trang đã đủ cao — khôi phục ở đây thì
 * trúng, còn để router tự làm sẽ nhảy về đầu trang.
 *
 * BẮT BUỘC `behavior: 'instant'`: main.css đặt `html { scroll-behavior: smooth }`, nên một
 * lời gọi cuộn thường sẽ CHẠY HOẠT ẢNH — vừa vào trang đã thấy nội dung tự trôi xuống, trông
 * như lỗi. Khôi phục vị trí thì phải nhảy thẳng, không diễn.
 */
function nhoViTriCuon() {
  kho.viTriCuon = window.scrollY
}

onMounted(async () => {
  await kho.napDanhMuc()

  // Có sẵn số cũ thì trang đã cao trở lại ngay -> khôi phục đúng chỗ người dùng đang đứng.
  if (kho.coDuLieu && kho.viTriCuon) {
    await nextTick()
    window.scrollTo({ top: kho.viTriCuon, behavior: 'instant' })
  }
  load()
})

onBeforeUnmount(nhoViTriCuon)

// Nút Back/Forward của trình duyệt đổi URL mà không dựng lại component -> phải tự đồng bộ.
// So với boLoc trước khi tải: lượt push do chính apDung() gây ra đã khớp sẵn nên không tải hai lần.
watch(
  () => route.query,
  () => {
    const q = docUrl()
    if (Object.keys(q).every((k) => q[k] === boLoc[k])) return
    Object.assign(boLoc, q)
    Object.assign(nhap, q)
    load()
  },
)

/* ══════════════════ Dữ liệu cho từng khối ══════════════════ */

const chiSo = computed(() => kho.tomTat?.chiSo ?? [])
const capNhatLuc = computed(() => gioNgan(kho.tomTat?.tinhDenLuc))

const nhanThang = computed(() => kho.bieuDo?.theoThang.map((m) => m.nhan) ?? [])
const soBuoiThang = computed(() => kho.bieuDo?.theoThang.map((m) => m.buoiDay) ?? [])
const chiPhiThang = computed(() => kho.bieuDo?.theoThang.map((m) => m.chiPhi) ?? [])
const coSoLieu = computed(() => soBuoiThang.value.some((v) => v > 0))
const nhomMon = computed(() => kho.bieuDo?.coCauNhomMon ?? [])

/**
 * Khối biểu đồ đang chờ lượt tải ĐẦU TIÊN.
 *
 * Phải phân biệt với "đã tải xong và rỗng": trong lúc chờ mà in "Kỳ này chưa có buổi dạy nào"
 * thì màn hình đang khẳng định một điều SAI, ngay cạnh cái thẻ ghi 11.557 buổi.
 *
 * Lần lọc lại đã có số liệu cũ nên giữ nguyên biểu đồ cũ và chỉ làm mờ (xem lamMo).
 */
const dangTaiBieuDo = computed(() => kho.tai.bieuDo && !kho.bieuDo)

/**
 * Gom buổi dạy theo ngày để bảng có dòng tiêu đề "Hôm nay · Thứ Sáu 21/08".
 *
 * API đã trả sẵn thứ tự thời gian nên chỉ cần cắt khi nhãn ngày đổi — không cần sort lại.
 */
const lichTheoNgay = computed(() => {
  const nhom = []
  for (const b of kho.dieuHanh?.lichSapToi ?? []) {
    const cuoi = nhom[nhom.length - 1]
    if (cuoi?.nhan === b.nhomNgay) cuoi.buoi.push(b)
    else nhom.push({ nhan: b.nhomNgay, buoi: [b] })
  }
  return nhom
})

function mo(duongDan) {
  if (duongDan) router.push(duongDan)
}
/**
 * Xuất CSV. PHẢI bắt lỗi: `xuatCsv` là hàm async, gọi trần thì mọi thất bại (mất mạng, 403,
 * kỳ quá lớn bị server chặn) chỉ thành một unhandled rejection trong console — người dùng bấm
 * Xuất rồi KHÔNG thấy gì xảy ra và không biết vì sao.
 *
 * Lỗi của request `responseType: 'blob'` nằm trong blob chứ không phải `e.response.data.message`,
 * nên bóc bằng `loiTaiFile` để giữ đúng câu server đã nói.
 */
async function xuat(chieu) {
  try {
    await dashboardApi.xuatCsv(boLoc, chieu)
  } catch (e) {
    alert(await loiTaiFile(e, 'Không xuất được CSV'))
  }
}

const TONE = { ok: 'badge-green', wait: 'badge-amber', done: 'badge-gray', no: 'badge-red' }
</script>

<template>
  <div class="page dash">
    <h2 class="title">Bảng điều khiển</h2>

    <!-- Bộ lọc áp cho toàn trang. Xếp lưới để nhãn và ô chọn thẳng cột với nhau. -->
    <div class="card filters">
      <div class="filters__grid">
        <label class="field">
          <span class="field__label">Kỳ</span>
          <select :value="kyDangChon" @change="chonKy($event.target.value)">
            <!-- Tự gõ ngày ở hai ô bên cạnh thì không khớp kỳ dựng sẵn nào; phải có mục này
                 không thì ô chọn nhảy về mục đầu và nhìn như đang xem năm học. -->
            <option v-if="!kyDangChon" value="">Tuỳ chọn</option>
            <option v-for="k in kyDungSan" :key="k.ma" :value="k.ma">{{ k.nhan }}</option>
          </select>
        </label>

        <label class="field">
          <span class="field__label">Từ ngày</span>
          <input v-model="nhap.from" type="date" :max="nhap.to" />
        </label>

        <label class="field">
          <span class="field__label">Đến ngày</span>
          <input v-model="nhap.to" type="date" :min="nhap.from" />
        </label>

        <div class="field">
          <span class="field__label">Trường</span>
          <SearchSelect
            :model-value="nhap.schoolId ?? ''"
            :options="truongChon"
            clearable
            placeholder="Tất cả trường"
            search-placeholder="Gõ tên trường để tìm…"
            empty-text="Chưa có trường nào"
            @update:model-value="chonMuc('schoolId', $event)"
          />
        </div>

        <div class="field">
          <span class="field__label">Nhóm môn</span>
          <SearchSelect
            :model-value="nhap.categoryId ?? ''"
            :options="nhomMonChon"
            clearable
            placeholder="Tất cả nhóm môn"
            search-placeholder="Gõ tên nhóm môn…"
            empty-text="Chưa có nhóm môn nào"
            @update:model-value="chonMuc('categoryId', $event)"
          />
        </div>
      </div>

      <div class="filters__actions">
        <button class="btn btn-primary btn-sm" :class="{ 'is-pending': choApDung }" @click="apDung">
          Áp dụng
        </button>
        <button v-if="dangLoc" class="btn btn-outline btn-sm" @click="xoaLoc">Xoá lọc</button>
        <button class="btn btn-outline btn-sm" :disabled="kho.dangTai" @click="load">
          Làm mới
        </button>

        <LoadingSpinner v-if="kho.dangTai" bare :size="16" />
        <span v-else-if="capNhatLuc" class="filters__stamp" :title="kho.tomTat.tinhDenLuc">
          Cập nhật {{ capNhatLuc }}
        </span>
      </div>
    </div>

    <div v-if="kho.loi" class="alert-error">
      {{ kho.loi }}
      <button class="btn btn-outline btn-sm" @click="load">Thử lại</button>
    </div>

    <div class="dash__body" :class="{ 'is-first': phuKin, 'is-refresh': lamMo }">
      <LoadingSpinner v-if="phuKin" overlay text="Đang tải…" />

      <!-- Hàng 1 — thẻ chỉ số -->
      <div class="stat-grid">
        <StatCard
          v-for="k in chiSo"
          :key="k.key"
          :icon="k.icon"
          :label="k.nhan"
          :value="theoMa(k.giaTri, k.dinhDang)"
          :hint="k.phu"
          :trend="k.thayDoi"
          :color="k.mau"
          :invert-trend="k.key === 'chiPhi'"
          @click="mo(k.route)"
        />
      </div>

      <!-- Hàng 2 — biểu đồ -->
      <div class="row">
        <div class="card panel col-8">
          <h3 class="panel__title">Số buổi dạy và chi phí lương theo tháng</h3>
          <div class="panel__body">
            <LoadingSpinner v-if="dangTaiBieuDo" bare :size="30" />
            <BarLineChart
              v-else-if="coSoLieu"
              :nhan="nhanThang"
              :cot="soBuoiThang"
              :duong="chiPhiThang"
              ten-cot="Số buổi dạy"
              ten-duong="Chi phí lương"
            />
            <p v-else class="empty-box">Kỳ này chưa có buổi dạy nào.</p>
          </div>
        </div>

        <div class="card panel col-4">
          <h3 class="panel__title">Buổi dạy theo nhóm môn</h3>
          <div class="panel__body">
            <LoadingSpinner v-if="dangTaiBieuDo" bare :size="30" />
            <PieChart
              v-else-if="nhomMon.length"
              :nhan="nhomMon.map((c) => c.nhan)"
              :gia-tri="nhomMon.map((c) => c.giaTri)"
            />
            <p v-else class="empty-box">Chưa có dữ liệu.</p>
          </div>
        </div>
      </div>

      <!-- Hàng 3 — việc cần xử lý + lịch sắp tới -->
      <div class="row">
        <AlertPanel v-if="kho.dieuHanh" class="col-4" :canh-bao="kho.dieuHanh.canhBao" @mo="mo" />

        <div class="table-wrap col-8">
          <div class="panel-head">
            <h3>Buổi dạy sắp tới</h3>
            <button class="btn btn-outline btn-sm" @click="mo('/schedule')">Lịch tuần</button>
          </div>
          <table class="table">
            <thead>
              <tr>
                <th>Giờ</th>
                <th>Môn</th>
                <th>Giáo viên</th>
                <th>Trường</th>
              </tr>
            </thead>
            <tbody v-for="ngay in lichTheoNgay" :key="ngay.nhan">
              <tr class="rowgroup">
                <td colspan="4">{{ ngay.nhan }}</td>
              </tr>
              <tr v-for="b in ngay.buoi" :key="b.id">
                <td class="nowrap">
                  {{ b.batDau }}–{{ b.ketThuc }}
                  <span v-if="b.trangThaiThoiGian === 'dangDien'" class="badge badge-green">
                    Đang dạy
                  </span>
                </td>
                <td>{{ b.mon }}</td>
                <td>{{ b.giaoVien }}</td>
                <td>{{ b.truong }}</td>
              </tr>
            </tbody>
            <tbody v-if="kho.dieuHanh && !lichTheoNgay.length">
              <tr>
                <td colspan="4" class="empty">Chưa có buổi dạy nào được xếp.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Hàng 4 — bảng thống kê chi tiết -->
      <h3 class="section-title">Thống kê chi tiết</h3>
      <AnalyticsTable
        :dong="kho.bang[kho.tab] ?? []"
        :tab="kho.tab"
        :dang-tai="kho.tai.bang"
        @update:tab="doiTab"
        @xuat="xuat"
      />

      <!-- Hàng 5 — phân công gần đây -->
      <div class="table-wrap dash__last">
        <div class="panel-head">
          <h3>Phân công gần đây</h3>
          <button class="btn btn-outline btn-sm" @click="mo('/assignments')">Xem tất cả</button>
        </div>
        <table class="table">
          <thead>
            <tr>
              <th>Giáo viên</th>
              <th>Trường</th>
              <th>Môn</th>
              <th>Bắt đầu</th>
              <th>Trạng thái</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in kho.dieuHanh?.phanCongGanDay ?? []" :key="r.id">
              <td>{{ r.giaoVien }}</td>
              <td>{{ r.truong }}</td>
              <td>{{ r.mon }}</td>
              <td class="nowrap">{{ r.ngay }}</td>
              <td>
                <span class="badge" :class="TONE[r.tone]">{{ r.nhanTrangThai }}</span>
              </td>
            </tr>
            <tr v-if="kho.dieuHanh && !kho.dieuHanh.phanCongGanDay.length">
              <td colspan="5" class="empty">Chưa có phân công nào.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dash {
  max-width: 1320px;
}
.dash .title {
  margin-bottom: 0.85rem;
}

/* ── Thanh lọc ── */
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 0.7rem 1rem;
  padding: 0.85rem 1rem;
  margin-bottom: 0.85rem;
}
/* auto-fit + minmax: các ô luôn rộng bằng nhau và tự xuống dòng theo cột, không so le.
   148px là bề rộng nhỏ nhất còn đọc trọn "Tất cả nhóm môn"; để rộng hơn thì ở màn 1440px
   ô thứ năm bị đẩy xuống hàng hai và hở một mảng trống to bên phải hàng một. */
.filters__grid {
  flex: 1 1 620px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(148px, 1fr));
  gap: 0.6rem 0.7rem;
}
/* Ô ngày của trình duyệt tự đặt bề rộng tối thiểu khá lớn; không cho nó phá lưới. */
.field input[type='date'] {
  min-width: 0;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 0;
}
.field__label {
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--c-text-muted);
}
.field select,
.field input {
  width: 100%;
  padding: 0.42rem 0.6rem;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  font-size: 0.86rem;
  background: var(--c-surface);
  color: var(--c-text);
}
.filters__actions {
  display: flex;
  align-items: center;
  gap: 0.45rem;
}
/* Có thay đổi chưa áp dụng thì nút sáng lên — nếu không, người dùng đổi ô lọc rồi ngồi chờ
   số liệu tự đổi mà không hiểu vì sao màn hình đứng yên. */
.btn.is-pending {
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.25);
}
.filters__stamp {
  font-size: 0.76rem;
  color: var(--c-text-muted);
  white-space: nowrap;
}

.alert-error {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.6rem 0.9rem;
  margin-bottom: 0.85rem;
  border-radius: 8px;
  font-size: 0.86rem;
  color: #991b1b;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.28);
}
:root[data-theme='dark'] .alert-error {
  color: #f87171;
}

/* ── Vùng nội dung + trạng thái tải ── */
.dash__body {
  position: relative;
}
.dash__body.is-first {
  min-height: 60vh;
}
/* Lần lọc lại đã có số cũ trên màn: chỉ làm mờ chứ không che, đỡ chớp cả trang mỗi lần bấm */
.dash__body.is-refresh {
  opacity: 0.6;
  pointer-events: none;
}

/* ── Lưới 12 cột ── */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(196px, 1fr));
  gap: 0.85rem;
  margin-bottom: 0.85rem;
}
.stat-grid > * {
  cursor: pointer;
}
.row {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: 0.85rem;
  margin-bottom: 0.85rem;
  align-items: stretch; /* hai khối cùng hàng cao bằng nhau, không hở đáy */
}
.col-8 {
  grid-column: span 8;
}
.col-4 {
  grid-column: span 4;
}
@media (max-width: 1000px) {
  .col-8,
  .col-4 {
    grid-column: 1 / -1;
  }
}

/* ── Khối biểu đồ ── */
.panel {
  display: flex;
  flex-direction: column;
  padding: 0.85rem 1rem;
  min-width: 0;
}
.panel__title {
  margin: 0 0 0.7rem;
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--c-text);
}
/* Biểu đồ giãn hết chỗ còn lại để hai thẻ trong cùng hàng khớp mép dưới */
.panel__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 230px;
}
.section-title {
  margin: 1.1rem 0 0.6rem;
  font-size: 1rem;
  font-weight: 700;
  color: var(--c-text);
}
.empty-box {
  margin: 0;
  padding: 1.2rem 1rem;
  text-align: center;
  font-size: 0.86rem;
  color: var(--c-text-muted);
}

/* ── Bảng ── */
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.55rem 1rem;
  border-bottom: 1px solid var(--c-border);
  background: var(--c-surface-2);
}
.panel-head h3 {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--c-text);
}
/* Dòng tiêu đề ngày trong bảng lịch */
.rowgroup td {
  padding: 0.35rem 1rem;
  font-size: 0.76rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  color: var(--c-text-muted);
  background: var(--c-surface-2);
}
.nowrap {
  white-space: nowrap;
}
.empty {
  text-align: center;
  color: var(--c-text-muted);
  padding: 1.4rem;
}
.dash__last {
  margin-top: 0.85rem;
}
</style>
