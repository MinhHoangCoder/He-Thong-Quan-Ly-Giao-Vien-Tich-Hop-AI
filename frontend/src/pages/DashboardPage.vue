<script setup>
/**
 * Trang Bảng điều khiển (admin): thống kê tổng hợp toàn trung tâm theo kỳ.
 *
 * Kỳ mặc định là NĂM HỌC hiện hành (01/9 - 31/8) chứ không phải tháng dương lịch: mở
 * dashboard vào tháng hè mà lấy "tháng này" thì cả trang ra số 0 trong khi năm học vừa
 * rồi có cả chục nghìn buổi dạy.
 *
 * Ba API gọi song song và hiện độc lập — thẻ số về nhanh, biểu đồ nặng hơn nên về sau.
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import StatCard from '@/components/ui/StatCard.vue'
import AlertPanel from '@/components/dashboard/AlertPanel.vue'
import AnalyticsTable from '@/components/dashboard/AnalyticsTable.vue'
import BarLineChart from '@/components/charts/BarLineChart.vue'
import PieChart from '@/components/charts/PieChart.vue'
import HBarChart from '@/components/charts/HBarChart.vue'
import HeatmapChart from '@/components/charts/HeatmapChart.vue'
import { dashboardApi } from '@/api/dashboard'
import { cacKyDungSan, theoMa } from '@/utils/thongKe'

const router = useRouter()

const kyDungSan = cacKyDungSan()
const boLoc = reactive({
  from: kyDungSan[0].from,
  to: kyDungSan[0].to,
  branchId: null,
  schoolId: null,
  categoryId: null,
})

const danhMuc = ref({ chiNhanh: [], truong: [], nhomMon: [] })
const tomTat = ref(null)
const phanTich = ref(null)
const dieuHanh = ref(null)
const dangTai = ref(true)
const loi = ref('')

const kyDangChon = computed(
  () => kyDungSan.find((k) => k.from === boLoc.from && k.to === boLoc.to)?.ma ?? '',
)

async function load() {
  dangTai.value = true
  loi.value = ''
  const p = { ...boLoc }

  // allSettled chứ không all: một khối lỗi thì hai khối kia vẫn hiện được
  const [a, b, c] = await Promise.allSettled([
    dashboardApi.summary(p),
    dashboardApi.analytics(p),
    dashboardApi.operations(p),
  ])
  if (a.status === 'fulfilled') tomTat.value = a.value.data
  if (b.status === 'fulfilled') phanTich.value = b.value.data
  if (c.status === 'fulfilled') dieuHanh.value = c.value.data

  const hong = [a, b, c].find((r) => r.status === 'rejected')
  if (hong) {
    loi.value = hong.reason?.response?.data?.message || 'Không tải được số liệu.'
  }
  dangTai.value = false
}

onMounted(async () => {
  try {
    danhMuc.value = (await dashboardApi.filters()).data
  } catch {
    // Không có danh mục thì chỉ mất mấy ô lọc, phần còn lại vẫn chạy
  }
  load()
})

function chonKy(ma) {
  const k = kyDungSan.find((x) => x.ma === ma)
  if (!k) return
  boLoc.from = k.from
  boLoc.to = k.to
  load()
}

/** Ô chọn "Tất cả" trả về chuỗi rỗng, phải đổi thành null chứ không phải 0. */
function doiLoc(khoa, giaTri) {
  boLoc[khoa] = giaTri === '' ? null : Number(giaTri)
  load()
}

const chiSo = computed(() => tomTat.value?.chiSo ?? [])

const nhanThang = computed(() => phanTich.value?.theoThang.map((m) => m.nhan) ?? [])
const soBuoiThang = computed(() => phanTich.value?.theoThang.map((m) => m.buoiDay) ?? [])
const chiPhiThang = computed(() => phanTich.value?.theoThang.map((m) => m.chiPhi) ?? [])
const coSoLieu = computed(() => soBuoiThang.value.some((v) => v > 0))

const nhomMon = computed(() => phanTich.value?.coCauNhomMon ?? [])
const topTruong = computed(() => phanTich.value?.topTruong ?? [])

function mo(duongDan) {
  if (duongDan) router.push(duongDan)
}
function xuat(chieu = 'GIAO_VIEN') {
  dashboardApi.xuatCsv(boLoc, chieu)
}

const TONE = { ok: 'badge-green', wait: 'badge-amber', done: 'badge-gray', no: 'badge-red' }
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="title">Bảng điều khiển</h2>
        <p v-if="tomTat" class="subtitle">
          {{ tomTat.ky }} · so với {{ tomTat.kyTruoc }} · số liệu tính đến {{ tomTat.tinhDenLuc }}
        </p>
      </div>
    </div>

    <!-- Bộ lọc kỳ + phạm vi, áp cho toàn trang -->
    <div class="toolbar">
      <label>Kỳ</label>
      <select :value="kyDangChon" @change="chonKy($event.target.value)">
        <!-- Tự gõ ngày ở hai ô dưới thì không khớp kỳ dựng sẵn nào; phải có mục này
             không thì ô chọn nhảy về mục đầu tiên và nhìn như đang xem năm học. -->
        <option v-if="!kyDangChon" value="">Tuỳ chọn</option>
        <option v-for="k in kyDungSan" :key="k.ma" :value="k.ma">{{ k.nhan }}</option>
      </select>

      <label>Từ</label>
      <input v-model="boLoc.from" type="date" :max="boLoc.to" @change="load" />
      <label>Đến</label>
      <input v-model="boLoc.to" type="date" :min="boLoc.from" @change="load" />

      <span class="divider" />

      <label>Trường</label>
      <select :value="boLoc.schoolId ?? ''" @change="doiLoc('schoolId', $event.target.value)">
        <option value="">Tất cả</option>
        <option v-for="t in danhMuc.truong" :key="t.id" :value="t.id">{{ t.ten }}</option>
      </select>

      <label>Nhóm môn</label>
      <select :value="boLoc.categoryId ?? ''" @change="doiLoc('categoryId', $event.target.value)">
        <option value="">Tất cả</option>
        <option v-for="m in danhMuc.nhomMon" :key="m.id" :value="m.id">{{ m.ten }}</option>
      </select>

      <button class="btn btn-outline btn-sm" :disabled="dangTai" @click="load">Làm mới</button>
      <button class="btn btn-primary btn-sm" @click="xuat('GIAO_VIEN')">Xuất Excel</button>
      <span v-if="dangTai" class="info-text">Đang tải…</span>
    </div>

    <div v-if="loi" class="alert-error">
      {{ loi }}
      <button class="btn btn-outline btn-sm" @click="load">Thử lại</button>
    </div>

    <!-- Thẻ chỉ số -->
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

    <!-- Biểu đồ chính -->
    <div class="card chart-card">
      <h3 class="card-title">Số buổi dạy và chi phí lương theo tháng</h3>
      <BarLineChart
        v-if="coSoLieu"
        :nhan="nhanThang"
        :cot="soBuoiThang"
        :duong="chiPhiThang"
        ten-cot="Số buổi dạy"
        ten-duong="Chi phí lương"
      />
      <p v-else class="empty-box">Kỳ này chưa có buổi dạy nào.</p>
    </div>

    <div class="two-col">
      <div class="card chart-card">
        <h3 class="card-title">Buổi dạy theo nhóm môn</h3>
        <PieChart
          v-if="nhomMon.length"
          :nhan="nhomMon.map((c) => c.nhan)"
          :gia-tri="nhomMon.map((c) => c.giaTri)"
        />
        <p v-else class="empty-box">Chưa có dữ liệu.</p>
      </div>

      <div class="card chart-card">
        <h3 class="card-title">Mật độ dạy theo thứ và tiết</h3>
        <HeatmapChart v-if="phanTich" :o="phanTich.nhietDo" :so-tiet="phanTich.soTietToiDa" />
        <p v-else class="empty-box">Chưa có dữ liệu.</p>
      </div>
    </div>

    <div class="two-col">
      <div class="card chart-card">
        <h3 class="card-title">10 trường có nhiều buổi dạy nhất</h3>
        <HBarChart
          v-if="topTruong.length"
          :nhan="topTruong.map((t) => t.nhan)"
          :gia-tri="topTruong.map((t) => t.giaTri)"
        />
        <p v-else class="empty-box">Chưa có dữ liệu.</p>
      </div>

      <AlertPanel v-if="dieuHanh" :canh-bao="dieuHanh.canhBao" @mo="mo" />
    </div>

    <!-- Bảng thống kê chi tiết -->
    <h3 class="section-title">Thống kê chi tiết</h3>
    <AnalyticsTable
      v-if="phanTich"
      :theo-giao-vien="phanTich.theoGiaoVien"
      :theo-truong="phanTich.theoTruong"
      :theo-mon="phanTich.theoMon"
      @xuat="xuat"
    />

    <!-- Lịch dạy + phân công gần đây -->
    <div class="two-col">
      <div class="table-wrap">
        <div class="panel-head">
          <h3>{{ dieuHanh?.lichNhan || 'Lịch dạy' }}</h3>
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
          <tbody>
            <tr v-for="b in dieuHanh?.lich ?? []" :key="b.id">
              <td class="nowrap">{{ b.batDau }}–{{ b.ketThuc }}</td>
              <td>{{ b.mon }}</td>
              <td>{{ b.giaoVien }}</td>
              <td>{{ b.truong }}</td>
            </tr>
            <tr v-if="dieuHanh && !dieuHanh.lich.length">
              <td colspan="4" class="empty">Không có buổi dạy nào.</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="table-wrap">
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
            <tr v-for="r in dieuHanh?.phanCongGanDay ?? []" :key="r.id">
              <td>{{ r.giaoVien }}</td>
              <td>{{ r.truong }}</td>
              <td>{{ r.mon }}</td>
              <td class="nowrap">{{ r.ngay }}</td>
              <td>
                <span class="badge" :class="TONE[r.tone]">{{ r.nhanTrangThai }}</span>
              </td>
            </tr>
            <tr v-if="dieuHanh && !dieuHanh.phanCongGanDay.length">
              <td colspan="5" class="empty">Chưa có phân công nào.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 1280px;
}
.toolbar input[type='date'],
.toolbar select {
  min-width: 8rem;
}
.divider {
  width: 1px;
  height: 22px;
  background: var(--c-border);
  margin: 0 0.3rem;
}
.info-text {
  font-size: 0.82rem;
  color: var(--c-accent);
}
.alert-error {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.7rem 1rem;
  margin-bottom: 1rem;
  border-radius: 8px;
  font-size: 0.86rem;
  color: #991b1b;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.28);
}
:root[data-theme='dark'] .alert-error {
  color: #f87171;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1rem;
  margin-bottom: 1.2rem;
}
.stat-grid > * {
  cursor: pointer;
}

.two-col {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
  gap: 1.2rem;
  margin-bottom: 1.2rem;
  align-items: start;
}
.chart-card {
  padding: 1rem 1.15rem;
}
.card-title {
  margin: 0 0 0.9rem;
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--c-text);
}
.section-title {
  margin: 1.6rem 0 0.8rem;
  font-size: 1rem;
  font-weight: 700;
  color: var(--c-text);
}
.empty-box {
  margin: 0;
  padding: 2.5rem 1rem;
  text-align: center;
  font-size: 0.86rem;
  color: var(--c-text-muted);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.6rem 1rem;
  border-bottom: 1px solid var(--c-border);
  background: var(--c-surface-2);
}
.panel-head h3 {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--c-text);
}
.nowrap {
  white-space: nowrap;
}
.empty {
  text-align: center;
  color: var(--c-text-muted);
  padding: 1.6rem;
}
</style>
