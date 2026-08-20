<script setup>
/**
 * BẢNG ĐIỀU KHIỂN QUẢN TRỊ — trung tâm thống kê của hệ thống.
 *
 * Trang chia làm bốn tầng, xếp theo đúng thứ tự người điều hành đọc số liệu:
 *
 *   1. THANH LỌC  — chọn kỳ và phạm vi; mọi thứ bên dưới đọc từ đây.
 *   2. SÁU CHỈ SỐ — trả lời "kỳ này thế nào" trong ba giây, có đối chiếu kỳ trước.
 *   3. PHÂN TÍCH  — bốn biểu đồ và một bảng ba tab trả lời "tại sao lại thế".
 *   4. ĐIỀU HÀNH  — việc cần xử lý, lịch dạy, phân công gần đây: "vậy giờ làm gì".
 *
 * BA LỜI GỌI API CHẠY SONG SONG VÀ HIỆN RA ĐỘC LẬP. Sáu thẻ chỉ số về trong chớp mắt và
 * hiện ngay, không phải chờ khu phân tích (vốn nặng hơn nhiều) chạy xong. Gộp một request
 * thì cả trang đứng im tới khi truy vấn chậm nhất trả về.
 *
 * BỘ LỌC ĐƯỢC GHI LÊN THANH ĐỊA CHỈ. F5 không mất bộ lọc, nút quay lại của trình duyệt
 * hoạt động đúng, và một câu hỏi số liệu gửi được cho người khác bằng cách dán đường dẫn.
 */
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import FilterBar from '@/components/dashboard/FilterBar.vue'
import KpiCard from '@/components/dashboard/KpiCard.vue'
import AlertPanel from '@/components/dashboard/AlertPanel.vue'
import AnalyticsTable from '@/components/dashboard/AnalyticsTable.vue'
import ComboChart from '@/components/charts/ComboChart.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import HeatmapChart from '@/components/charts/HeatmapChart.vue'
import RankBars from '@/components/charts/RankBars.vue'
import { dashboardApi } from '@/api/dashboard'
import { cacKyDungSan, soNguyen, tien } from '@/utils/thongKe'

const router = useRouter()
const route = useRoute()

/* ─────────── Bộ lọc ─────────── */

const kyMacDinh = cacKyDungSan()[0] // Năm học hiện hành — khớp với mặc định của backend

const boLoc = reactive({
  from: route.query.from || kyMacDinh.from,
  to: route.query.to || kyMacDinh.to,
  branchId: route.query.branchId ? Number(route.query.branchId) : null,
  schoolId: route.query.schoolId ? Number(route.query.schoolId) : null,
  categoryId: route.query.categoryId ? Number(route.query.categoryId) : null,
})

const danhMuc = ref({ chiNhanh: [], truong: [], nhomMon: [] })

/* ─────────── Dữ liệu ─────────── */

const tomTat = ref(null)
const phanTich = ref(null)
const dieuHanh = ref(null)

const dangTaiTomTat = ref(true)
const dangTaiPhanTich = ref(true)
const dangTaiDieuHanh = ref(true)
const loi = ref('')

const dangTai = computed(
  () => dangTaiTomTat.value || dangTaiPhanTich.value || dangTaiDieuHanh.value,
)

/** Chi phí tăng là tin XẤU — thẻ chi phí phải tô màu ngược với năm thẻ còn lại. */
const tangLaTot = (key) => key !== 'chiPhi'

async function nap() {
  loi.value = ''
  const thamSo = { ...boLoc }

  dangTaiTomTat.value = true
  dangTaiPhanTich.value = true
  dangTaiDieuHanh.value = true

  // Ba lời gọi độc lập: một cái hỏng thì hai cái kia vẫn hiện. Dùng allSettled thay vì all
  // để một truy vấn lỗi không xoá trắng cả màn hình.
  const [t, p, d] = await Promise.allSettled([
    dashboardApi.summary(thamSo),
    dashboardApi.analytics(thamSo),
    dashboardApi.operations(thamSo),
  ])

  if (t.status === 'fulfilled') tomTat.value = t.value.data
  if (p.status === 'fulfilled') phanTich.value = p.value.data
  if (d.status === 'fulfilled') dieuHanh.value = d.value.data

  dangTaiTomTat.value = false
  dangTaiPhanTich.value = false
  dangTaiDieuHanh.value = false

  const hong = [t, p, d].find((r) => r.status === 'rejected')
  if (hong) {
    loi.value =
      hong.reason?.response?.data?.message || 'Không tải được một phần số liệu. Vui lòng thử lại.'
  }
}

onMounted(async () => {
  try {
    danhMuc.value = (await dashboardApi.filters()).data
  } catch {
    // Không tải được danh mục thì thanh lọc chỉ mất mấy ô chọn phạm vi;
    // phần còn lại của trang vẫn phải chạy được nên nuốt lỗi ở đây.
  }
  nap()
})

/** Đổi bộ lọc → ghi lên URL → nạp lại. Ghi URL bằng replace để không dồn rác vào lịch sử. */
function doiBoLoc(moi) {
  Object.assign(boLoc, moi)
  const q = {}
  for (const [k, v] of Object.entries(boLoc)) {
    if (v !== null && v !== undefined && v !== '') q[k] = String(v)
  }
  router.replace({ query: q })
}

// Theo dõi URL thay vì theo dõi boLoc: nhờ vậy bấm "quay lại" trên trình duyệt cũng nạp lại
// đúng bộ lọc cũ, chứ không chỉ đổi thanh địa chỉ mà số liệu vẫn đứng yên.
watch(
  () => route.query,
  (q) => {
    boLoc.from = q.from || kyMacDinh.from
    boLoc.to = q.to || kyMacDinh.to
    boLoc.branchId = q.branchId ? Number(q.branchId) : null
    boLoc.schoolId = q.schoolId ? Number(q.schoolId) : null
    boLoc.categoryId = q.categoryId ? Number(q.categoryId) : null
    nap()
  },
)

/* ─────────── Dữ liệu cho biểu đồ ─────────── */

const diemThang = computed(
  () =>
    phanTich.value?.theoThang.map((m) => ({
      nhan: m.nhan,
      cot: m.buoiDay,
      duong: m.chiPhi,
    })) ?? [],
)
const coDuLieuThang = computed(() => diemThang.value.some((d) => d.cot > 0))
const coNhietDo = computed(() => (phanTich.value?.nhietDo ?? []).some((o) => o.soBuoi > 0))
const coCoCau = computed(() => (phanTich.value?.coCauNhomMon ?? []).length > 0)
const coTopTruong = computed(() => (phanTich.value?.topTruong ?? []).length > 0)

/* ─────────── Hành động ─────────── */

function mo(duongDan) {
  if (duongDan) router.push(duongDan)
}
function xuat(chieu = 'GIAO_VIEN') {
  dashboardApi.xuatCsv(boLoc, chieu)
}
function inBaoCao() {
  window.print()
}

const TRANG_THAI_BUOI = {
  daXong: 'Đã xong',
  dangDien: 'Đang diễn ra',
  sapToi: 'Sắp tới',
}
const TONE = { ok: 'is-ok', wait: 'is-wait', done: 'is-done', no: 'is-no' }
</script>

<template>
  <div class="db">
    <!-- ══ Tiêu đề ══ -->
    <header class="db__head">
      <div>
        <h1 class="db__title">Bảng điều khiển</h1>
        <p class="db__sub">
          <template v-if="tomTat"> {{ tomTat.ky }} · đối chiếu với {{ tomTat.kyTruoc }} </template>
          <template v-else>Đang tổng hợp số liệu…</template>
        </p>
      </div>
    </header>

    <!-- ══ Thanh lọc ══ -->
    <FilterBar
      :bo-loc="boLoc"
      :danh-muc="danhMuc"
      :dang-tai="dangTai"
      :tinh-den-luc="tomTat?.tinhDenLuc || ''"
      @doi="doiBoLoc"
      @lam-moi="nap"
      @xuat="xuat('GIAO_VIEN')"
      @in="inBaoCao"
    />

    <div v-if="loi" class="db__loi">
      <SvgIcon name="bell" :size="18" />
      <span>{{ loi }}</span>
      <button class="db__thulai" @click="nap">Thử lại</button>
    </div>

    <!-- ══ Tầng 1 — Sáu chỉ số ══ -->
    <section class="db__kpi">
      <template v-if="dangTaiTomTat">
        <div v-for="n in 6" :key="'sk' + n" class="skel skel--kpi" />
      </template>
      <KpiCard
        v-for="k in tomTat?.chiSo ?? []"
        v-else
        :key="k.key"
        :kpi="k"
        :tang-la-tot="tangLaTot(k.key)"
        @mo="mo"
      />
    </section>

    <!-- ══ Tầng 2 — Phân tích ══ -->
    <section class="card db__chart">
      <div class="card__head">
        <div>
          <h2 class="card__title">Khối lượng dạy và chi phí theo tháng</h2>
          <p class="card__sub">
            Cột là số buổi đã duyệt, đường là chi phí lương phân bổ. Hai đường đi lệch nhau là dấu
            hiệu chi phí mỗi buổi đang thay đổi.
          </p>
        </div>
      </div>
      <div v-if="dangTaiPhanTich" class="skel skel--chart" />
      <ComboChart
        v-else-if="coDuLieuThang"
        :points="diemThang"
        nhan-cot="Buổi dạy"
        nhan-duong="Chi phí lương"
        :dinh-dang-cot="soNguyen"
        :dinh-dang-duong="tien"
      />
      <p v-else class="db__trong">
        <SvgIcon name="schedule" :size="24" />
        Kỳ này chưa có buổi dạy nào được duyệt.
      </p>
    </section>

    <section class="db__doi">
      <!-- Bản đồ nhiệt -->
      <div class="card">
        <div class="card__head">
          <div>
            <h2 class="card__title">Mật độ dạy theo thứ và tiết</h2>
            <p class="card__sub">
              Ô càng đậm càng nhiều buổi. Mảng nhạt là khung giờ còn trống — năng lực chưa khai
              thác.
            </p>
          </div>
        </div>
        <div v-if="dangTaiPhanTich" class="skel skel--block" />
        <HeatmapChart v-else-if="coNhietDo" :o="phanTich.nhietDo" :so-tiet="phanTich.soTietToiDa" />
        <p v-else class="db__trong"><SvgIcon name="clock" :size="24" /> Chưa có buổi dạy nào.</p>
      </div>

      <!-- Cơ cấu nhóm môn -->
      <div class="card">
        <div class="card__head">
          <div>
            <h2 class="card__title">Cơ cấu theo nhóm môn</h2>
            <p class="card__sub">Bấm vào một nhóm để lọc cả trang theo nhóm đó.</p>
          </div>
        </div>
        <div v-if="dangTaiPhanTich" class="skel skel--block" />
        <DonutChart
          v-else-if="coCoCau"
          :lat="phanTich.coCauNhomMon"
          :dang-chon="boLoc.categoryId"
          don-vi="buổi dạy"
          @chon="doiBoLoc({ categoryId: $event })"
        />
        <p v-else class="db__trong"><SvgIcon name="subject" :size="24" /> Chưa có dữ liệu môn.</p>
      </div>
    </section>

    <!-- Xếp hạng trường -->
    <section class="card">
      <div class="card__head">
        <div>
          <h2 class="card__title">Trường theo số buổi dạy</h2>
          <p class="card__sub">Bấm vào một trường để lọc cả trang theo trường đó.</p>
        </div>
        <button class="card__more" @click="mo('/admin/schools')">Danh sách trường</button>
      </div>
      <div v-if="dangTaiPhanTich" class="skel skel--block" />
      <RankBars
        v-else-if="coTopTruong"
        :thanh="phanTich.topTruong"
        :dang-chon="boLoc.schoolId"
        don-vi="buổi"
        @chon="doiBoLoc({ schoolId: $event })"
      />
      <p v-else class="db__trong"><SvgIcon name="school" :size="24" /> Chưa có dữ liệu trường.</p>
    </section>

    <!-- Bảng phân tích ba tab -->
    <div v-if="dangTaiPhanTich" class="card"><div class="skel skel--block" /></div>
    <AnalyticsTable
      v-else-if="phanTich"
      :theo-giao-vien="phanTich.theoGiaoVien"
      :theo-truong="phanTich.theoTruong"
      :theo-mon="phanTich.theoMon"
      @xuat="xuat"
    />

    <!-- ══ Tầng 3 — Điều hành ══ -->
    <section class="db__doi db__doi--dieuhanh">
      <AlertPanel v-if="dieuHanh" :canh-bao="dieuHanh.canhBao" @mo="mo" />
      <div v-else class="card"><div class="skel skel--block" /></div>

      <div class="card">
        <div class="card__head">
          <div>
            <h2 class="card__title">{{ dieuHanh?.lichNhan || 'Lịch dạy' }}</h2>
            <!-- Khi hôm nay không có buổi nào, hệ thống nhảy tới ngày dạy gần nhất và NÓI RÕ
                 là đang xem ngày khác — một ô lịch trống không phân biệt được với lỗi. -->
            <p v-if="dieuHanh?.lichLaDuBao" class="card__sub">
              Hôm nay không có buổi nào nên đang hiển thị ngày dạy gần nhất.
            </p>
          </div>
          <button class="card__more" @click="mo('/schedule')">Lịch tuần</button>
        </div>

        <ul v-if="dieuHanh?.lich?.length" class="lich">
          <li
            v-for="b in dieuHanh.lich"
            :key="b.id"
            class="lich__item"
            :class="'is-' + b.trangThaiThoiGian"
            @click="mo('/schedule')"
          >
            <span class="lich__gio">
              <strong>{{ b.batDau }}</strong>
              <small>{{ b.ketThuc }}</small>
            </span>
            <span class="lich__cham" :style="{ background: b.mau }" />
            <span class="lich__than">
              <strong>{{ b.mon }}</strong>
              <small>{{ b.giaoVien }} · {{ b.truong }}</small>
            </span>
            <span class="lich__tt">{{ TRANG_THAI_BUOI[b.trangThaiThoiGian] }}</span>
          </li>
        </ul>
        <p v-else-if="dieuHanh" class="db__trong">
          <SvgIcon name="clock" :size="24" /> Không tìm thấy buổi dạy nào sắp tới.
        </p>
        <div v-else class="skel skel--block" />
      </div>
    </section>

    <!-- Phân công gần đây -->
    <section class="card">
      <div class="card__head">
        <h2 class="card__title">Phân công gần đây</h2>
        <button class="card__more" @click="mo('/assignments')">Xem tất cả</button>
      </div>
      <div class="db__wrap">
        <table class="db__table">
          <thead>
            <tr>
              <th>Giáo viên</th>
              <th>Trường</th>
              <th>Môn</th>
              <th>Bắt đầu</th>
              <th class="ta-r">Tiết/tuần</th>
              <th>Trạng thái</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in dieuHanh?.phanCongGanDay ?? []" :key="r.id" @click="mo('/assignments')">
              <td class="td-manh">{{ r.giaoVien }}</td>
              <td>{{ r.truong }}</td>
              <td>{{ r.mon }}</td>
              <td>{{ r.ngay }}</td>
              <td class="ta-r">{{ r.soTiet }}</td>
              <td>
                <span class="the" :class="TONE[r.tone]">{{ r.nhanTrangThai }}</span>
              </td>
            </tr>
            <tr v-if="dieuHanh && !dieuHanh.phanCongGanDay.length">
              <td colspan="6" class="td-trong">Chưa có phân công nào.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.db {
  max-width: 1320px;
}
.db__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
  margin-bottom: 1rem;
}
.db__title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--a-text);
}
.db__sub {
  margin: 0.2rem 0 0;
  font-size: 0.84rem;
  color: var(--a-text-muted);
}

/* ── Thẻ chung ── */
.card {
  background: var(--c-surface);
  border: 1px solid var(--a-border);
  border-radius: 14px;
  padding: 1.05rem 1.15rem;
  margin-bottom: 1.15rem;
}
.card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.85rem;
}
.card__title {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  color: var(--a-text);
}
.card__sub {
  margin: 0.2rem 0 0;
  font-size: 0.77rem;
  line-height: 1.45;
  color: var(--a-text-muted);
  max-width: 46ch;
}
.card__more {
  flex: none;
  border: 1px solid var(--a-border);
  background: none;
  color: var(--a-text-muted);
  font-size: 0.78rem;
  font-weight: 600;
  padding: 0.32rem 0.7rem;
  border-radius: 8px;
  cursor: pointer;
  transition: all var(--t-fast);
}
.card__more:hover {
  border-color: var(--c-primary);
  color: var(--c-primary);
}

/* ── Lưới ── */
.db__kpi {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(205px, 1fr));
  gap: 0.9rem;
  margin-bottom: 1.15rem;
}
.db__doi {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  gap: 1.15rem;
  align-items: start;
}
.db__doi > .card {
  margin-bottom: 1.15rem;
}
.db__doi--dieuhanh {
  align-items: stretch;
}

/* ── Trạng thái ── */
.db__loi {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
  padding: 0.85rem 1rem;
  margin-bottom: 1.1rem;
  border-radius: 12px;
  color: #b91c1c;
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.28);
  font-size: 0.86rem;
}
:root[data-theme='dark'] .db__loi {
  color: #f87171;
}
.db__thulai {
  margin-left: auto;
  border: 1px solid currentColor;
  background: none;
  color: inherit;
  font-weight: 600;
  font-size: 0.8rem;
  padding: 0.32rem 0.75rem;
  border-radius: 8px;
  cursor: pointer;
}
.db__trong {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 2.2rem 1rem;
  margin: 0;
  color: var(--a-text-muted);
  font-size: 0.86rem;
  text-align: center;
}

/* Khung xương lúc chờ dữ liệu: giữ đúng chỗ của khối sắp hiện ra nên trang không nhảy
   giật khi số liệu về — vòng xoay tròn không làm được điều đó. */
.skel {
  border-radius: 12px;
  background: linear-gradient(
    90deg,
    var(--a-border) 25%,
    color-mix(in srgb, var(--a-border) 45%, transparent) 50%,
    var(--a-border) 75%
  );
  background-size: 200% 100%;
  animation: troi 1.3s ease-in-out infinite;
}
@keyframes troi {
  to {
    background-position: -200% 0;
  }
}
.skel--kpi {
  height: 148px;
}
.skel--chart {
  height: 300px;
}
.skel--block {
  height: 210px;
}

/* ── Lịch dạy ── */
.lich {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.12rem;
}
.lich__item {
  display: grid;
  grid-template-columns: 3.2rem 8px 1fr auto;
  align-items: center;
  gap: 0.65rem;
  padding: 0.5rem 0.5rem;
  border-radius: 10px;
  cursor: pointer;
  transition: background var(--t-fast);
}
.lich__item:hover {
  background: color-mix(in srgb, var(--c-primary) 7%, transparent);
}
/* Buổi đã qua mờ đi, buổi đang diễn ra được viền sáng — đọc được trạng thái mà không cần
   nhìn đồng hồ rồi so với từng mốc giờ. */
.lich__item.is-daXong {
  opacity: 0.5;
}
.lich__item.is-dangDien {
  background: color-mix(in srgb, var(--c-primary) 10%, transparent);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--c-primary) 35%, transparent);
}
.lich__gio {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}
.lich__gio strong {
  font-size: 0.83rem;
  font-variant-numeric: tabular-nums;
  color: var(--a-text);
}
.lich__gio small {
  font-size: 0.7rem;
  color: var(--a-text-muted);
  font-variant-numeric: tabular-nums;
}
.lich__cham {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.lich__than {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.lich__than strong {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--a-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lich__than small {
  font-size: 0.73rem;
  color: var(--a-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lich__tt {
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--a-text-muted);
  white-space: nowrap;
}

/* ── Bảng phân công ── */
.db__wrap {
  overflow-x: auto;
}
.db__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
  min-width: 620px;
}
.db__table th {
  text-align: left;
  padding: 0 0.55rem 0.5rem;
  border-bottom: 1px solid var(--a-border);
  font-size: 0.74rem;
  font-weight: 600;
  color: var(--a-text-muted);
  white-space: nowrap;
}
.db__table td {
  padding: 0.6rem 0.55rem;
  border-bottom: 1px solid var(--a-border);
  color: var(--a-text-muted);
}
.db__table tbody tr {
  cursor: pointer;
  transition: background var(--t-fast);
}
.db__table tbody tr:hover td {
  background: color-mix(in srgb, var(--c-primary) 5%, transparent);
}
.td-manh {
  color: var(--a-text);
  font-weight: 600;
}
.td-trong {
  text-align: center;
  padding: 2rem 1rem;
}
.ta-r {
  text-align: right;
}
.the {
  display: inline-block;
  padding: 0.16rem 0.55rem;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 600;
  white-space: nowrap;
}
.the.is-ok {
  color: #15803d;
  background: rgba(34, 197, 94, 0.14);
}
.the.is-wait {
  color: #b45309;
  background: rgba(245, 158, 11, 0.16);
}
.the.is-done {
  color: #0369a1;
  background: rgba(14, 165, 233, 0.14);
}
.the.is-no {
  color: #b91c1c;
  background: rgba(239, 68, 68, 0.13);
}
:root[data-theme='dark'] .the.is-ok {
  color: #4ade80;
}
:root[data-theme='dark'] .the.is-wait {
  color: #fbbf24;
}
:root[data-theme='dark'] .the.is-done {
  color: #38bdf8;
}
:root[data-theme='dark'] .the.is-no {
  color: #f87171;
}

/* ── Bản in ──
   Ctrl+P phải ra một bản báo cáo nộp được: bỏ nền màu, bỏ nút bấm, và ép mỗi thẻ không bị
   cắt ngang qua hai trang giấy. */
@media print {
  .card,
  .db__kpi {
    break-inside: avoid;
    box-shadow: none;
  }
  .card {
    border-color: #ccc;
  }
  .card__more,
  .db__thulai {
    display: none;
  }
  .db {
    max-width: none;
  }
}
</style>
