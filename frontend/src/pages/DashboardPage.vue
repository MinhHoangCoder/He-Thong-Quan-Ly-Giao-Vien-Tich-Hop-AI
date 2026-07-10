<script setup>
// Dashboard quản trị TSDMS — DỮ LIỆU THẬT từ /api/v1/dashboard.
// Mọi số liệu tính trực tiếp từ DB; chỗ chưa có dữ liệu hiển thị 0 / "—".
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import StatCard from '@/components/ui/StatCard.vue'
import LineChart from '@/components/charts/LineChart.vue'
import MiniBars from '@/components/charts/MiniBars.vue'
import { dashboardApi } from '@/api/dashboard'

const router = useRouter()

const loading = ref(true)
const refreshing = ref(false)
const error = ref('')
const data = ref(null)
const months = ref(8)

const periodOptions = [
  { label: '6 tháng gần nhất', value: 6 },
  { label: '8 tháng gần nhất', value: 8 },
  { label: '12 tháng gần nhất', value: 12 },
]

// Nhãn thẻ thống kê (ghi rõ "tổng ..."); đồng thời bỏ dòng "so với ..." và % thay đổi.
const STAT_LABELS = {
  teachers: 'Tổng giáo viên',
  schools: 'Tổng trường',
  assignments: 'Tổng phân công trong tháng',
  lessons: 'Tổng số buổi dạy trong tuần này',
}
const statLabel = (s) => STAT_LABELS[s.key] || s.label

async function load(isRefresh = false) {
  if (isRefresh) refreshing.value = true
  else loading.value = true
  error.value = ''
  try {
    const { data: res } = await dashboardApi.summary(months.value)
    data.value = res
  } catch (e) {
    error.value = e?.response?.data?.message || 'Không tải được dữ liệu bảng điều khiển. Vui lòng thử lại.'
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

onMounted(() => load())

// Đổi khoảng thời gian biểu đồ -> nạp lại từ server.
function onPeriodChange() {
  load(true)
}

/* ─── Điều hướng (mọi thẻ/nút đều bấm được) ─── */
const statRoutes = {
  teachers: '/dashboard/teacher',
  schools: '/dashboard/teacher',
  assignments: '/assignments',
  lessons: '/schedule',
}
const sideRoutes = {
  hours: '/attendance',
  ontime: '/attendance',
  rating: '/dashboard/teacher',
  pending: '/schedule',
}
function goStat(key) {
  const to = statRoutes[key]
  if (to) router.push(to)
}
function goSide(key) {
  const to = sideRoutes[key]
  if (to) router.push(to)
}
function goCreateAssignment() {
  router.push('/assignments')
}
function goAssignments() {
  router.push('/assignments')
}
function goSchedule() {
  router.push('/schedule')
}
function goTeachers() {
  router.push('/dashboard/teacher')
}

const chartLabels = computed(() => data.value?.chart?.labels ?? [])
const chartSeries = computed(() => data.value?.chart?.series ?? [])
const hasChart = computed(() => chartSeries.value.some((s) => (s.data ?? []).some((v) => v > 0)))

const recentAssignments = computed(() => data.value?.recentAssignments ?? [])
const todaySchedule = computed(() => data.value?.todaySchedule ?? [])
const topTeachers = computed(() => data.value?.topTeachers ?? [])

function toneClass(tone) {
  return tone === 'ok' ? 'is-ok' : tone === 'wait' ? 'is-wait' : 'is-no'
}
</script>

<template>
  <!-- Tiêu đề trang -->
  <div class="page-head">
    <div>
      <h1 class="page-head__title">Bảng điều khiển</h1>
    </div>
    <div class="page-head__actions">
      <button class="btn-primary" @click="goCreateAssignment">
        <SvgIcon name="plus" :size="18" /> Tạo phân công
      </button>
    </div>
  </div>

  <!-- Lỗi -->
  <div v-if="error" class="state state--error">
    <SvgIcon name="bell" :size="20" />
    <span>{{ error }}</span>
    <button class="btn-ghost" @click="load()">Thử lại</button>
  </div>

  <!-- Đang tải -->
  <div v-else-if="loading" class="state state--loading">
    <span class="spinner" /> Đang tải dữ liệu…
  </div>

  <template v-else-if="data">
    <!-- Thẻ thống kê -->
    <section class="stat-grid">
      <button
        v-for="s in data.stats"
        :key="s.key"
        class="stat-link fade-up"
        @click="goStat(s.key)"
      >
        <StatCard :icon="s.icon" :label="statLabel(s)" :value="s.value" :color="s.color" />
      </button>
    </section>

    <!-- Biểu đồ + chỉ số phụ -->
    <section class="main-grid">
      <div class="card chart-card">
        <div class="card__head">
          <div>
            <h2 class="card__title">Buổi dạy theo tháng</h2>
            <p class="card__sub">Thống kê số buổi dạy theo nhóm môn</p>
          </div>
          <select v-model.number="months" class="select" @change="onPeriodChange">
            <option v-for="o in periodOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
          </select>
        </div>
        <div class="chart-wrap">
          <LineChart v-if="hasChart" :labels="chartLabels" :series="chartSeries" :height="300" />
          <div v-else class="state state--empty chart-empty">
            <SvgIcon name="schedule" :size="26" />
            <p>Chưa có buổi dạy nào trong khoảng thời gian này.</p>
          </div>
          <span v-if="refreshing" class="chart-refresh"><span class="spinner spinner--sm" /></span>
        </div>
      </div>

      <div class="side-stats">
        <button
          v-for="s in data.sideStats"
          :key="s.key"
          class="card mini-card"
          @click="goSide(s.key)"
        >
          <div class="mini-card__top">
            <span class="mini-card__label">{{ s.label }}</span>
            <span
              v-if="s.trend !== null && s.trend !== undefined"
              class="mini-card__trend"
              :class="s.trend >= 0 ? 'is-up' : 'is-down'"
            >
              <SvgIcon :name="s.trend >= 0 ? 'up' : 'down'" :size="13" />{{ Math.abs(s.trend) }}%
            </span>
          </div>
          <div class="mini-card__value">{{ s.value }}</div>
          <MiniBars :data="s.data" :color="s.color" />
        </button>
      </div>
    </section>

    <!-- Bảng phân công + lịch hôm nay -->
    <section class="bottom-grid">
      <div class="card">
        <div class="card__head">
          <h2 class="card__title">Phân công gần đây</h2>
          <button class="card__more" @click="goAssignments">Xem tất cả</button>
        </div>
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th>Giáo viên</th>
                <th>Trường</th>
                <th>Môn</th>
                <th>Ngày</th>
                <th>Trạng thái</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="r in recentAssignments"
                :key="r.id"
                class="row-link"
                @click="goAssignments"
              >
                <td class="td-strong">{{ r.teacher }}</td>
                <td>{{ r.school }}</td>
                <td>{{ r.subject }}</td>
                <td>{{ r.date }}</td>
                <td><span class="badge" :class="toneClass(r.tone)">{{ r.statusLabel }}</span></td>
              </tr>
              <tr v-if="!recentAssignments.length">
                <td colspan="5" class="td-empty">Chưa có phân công nào.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="card">
        <div class="card__head">
          <h2 class="card__title">Lịch dạy hôm nay</h2>
          <button class="card__more" @click="goSchedule">Lịch tuần</button>
        </div>
        <ul v-if="todaySchedule.length" class="timeline">
          <li
            v-for="t in todaySchedule"
            :key="t.id"
            class="timeline__item"
            @click="goSchedule"
          >
            <span class="timeline__time">{{ t.time }}</span>
            <span class="timeline__dot" :style="{ background: t.color, color: t.color }" />
            <div class="timeline__body">
              <strong>{{ t.subject }}</strong>
              <small>{{ t.teacher }}{{ t.school ? ' · ' + t.school : '' }}</small>
            </div>
          </li>
        </ul>
        <div v-else class="state state--empty">
          <SvgIcon name="clock" :size="26" />
          <p>Hôm nay chưa có buổi dạy nào.</p>
        </div>
      </div>
    </section>

    <!-- Top giáo viên -->
    <section class="card">
      <div class="card__head">
        <h2 class="card__title">Giáo viên nổi bật tháng này</h2>
        <button class="card__more" @click="goTeachers">Danh sách GV</button>
      </div>
      <div v-if="topTeachers.length" class="teacher-grid">
        <div
          v-for="(t, i) in topTeachers"
          :key="t.id"
          class="teacher"
          @click="goTeachers"
        >
          <span class="teacher__rank">#{{ i + 1 }}</span>
          <span class="teacher__avatar">{{ t.initials }}</span>
          <div class="teacher__info">
            <strong>{{ t.name }}</strong>
            <small>{{ t.subject }}</small>
          </div>
          <span class="teacher__hours">{{ t.hours }}h</span>
        </div>
      </div>
      <div v-else class="state state--empty">
        <SvgIcon name="teacher" :size="26" />
        <p>Chưa có dữ liệu giờ dạy trong tháng này.</p>
      </div>
    </section>
  </template>
</template>

<style scoped>
/* Tiêu đề trang */
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1.4rem;
  gap: 1rem;
  flex-wrap: wrap;
}
.page-head__title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--a-text);
}
.page-head__actions {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  border: none;
  cursor: pointer;
  padding: 0.65rem 1.15rem;
  border-radius: 10px;
  font-weight: 600;
  font-size: 0.9rem;
  color: #fff;
  background: var(--grad-primary);
  box-shadow: 0 8px 18px rgba(249, 115, 22, 0.32);
  transition:
    transform var(--t-fast),
    box-shadow var(--t-fast),
    filter var(--t-fast);
}
.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(249, 115, 22, 0.42);
  filter: brightness(1.05);
}
.btn-primary:active {
  transform: translateY(0);
}
.btn-primary:hover :deep(svg) {
  transform: rotate(90deg);
  transition: transform var(--t);
}
.btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  border: 1px solid var(--a-border);
  background: #fff;
  color: var(--a-text-muted);
  cursor: pointer;
  padding: 0.6rem 0.95rem;
  border-radius: 10px;
  font-weight: 600;
  font-size: 0.86rem;
  transition:
    border-color var(--t-fast),
    color var(--t-fast),
    background var(--t-fast);
}
.btn-ghost:hover:not(:disabled) {
  border-color: var(--c-primary);
  color: var(--c-primary);
  background: #fff7ed;
}
.btn-ghost:disabled {
  opacity: 0.6;
  cursor: default;
}

/* Trạng thái tải / lỗi / rỗng */
.state {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 1rem 1.2rem;
  border-radius: 12px;
  font-size: 0.9rem;
}
.state--loading {
  color: var(--a-text-muted);
  background: #fff;
  border: 1px solid var(--a-border);
}
.state--error {
  color: #b91c1c;
  background: #fef2f2;
  border: 1px solid #fecaca;
  flex-wrap: wrap;
}
.state--error .btn-ghost {
  margin-left: auto;
}
.state--empty {
  flex-direction: column;
  color: var(--a-text-muted);
  text-align: center;
  padding: 2.2rem 1rem;
}
.state--empty p {
  margin: 0.2rem 0 0;
}
.spinner {
  width: 18px;
  height: 18px;
  border: 2.5px solid var(--a-border);
  border-top-color: var(--c-primary);
  border-radius: 50%;
  display: inline-block;
  animation: spin 0.7s linear infinite;
}
.spinner--sm {
  width: 15px;
  height: 15px;
  border-width: 2px;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.spinning {
  animation: spin 0.8s linear infinite;
}

/* Lưới thẻ */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1.1rem;
  margin-bottom: 1.4rem;
}
.stat-link {
  border: none;
  background: none;
  padding: 0;
  margin: 0;
  text-align: left;
  cursor: pointer;
  display: block;
  width: 100%;
}

/* Card chung */
.card {
  background: #fff;
  border: 1px solid var(--a-border);
  border-radius: 16px;
  padding: 1.3rem;
  box-shadow: var(--a-shadow);
  transition:
    transform var(--t),
    box-shadow var(--t),
    border-color var(--t);
}
.card:hover {
  box-shadow: var(--a-shadow-lg);
  border-color: #d2e8e2;
}
.card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.1rem;
}
.card__title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--a-text);
}
.card__sub {
  margin: 0.25rem 0 0;
  font-size: 0.82rem;
  color: var(--a-text-muted);
}
.card__more {
  position: relative;
  font-size: 0.82rem;
  color: var(--c-primary);
  background: none;
  border: none;
  cursor: pointer;
  text-decoration: none;
  font-weight: 600;
  white-space: nowrap;
  padding: 0;
}
.card__more::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: -2px;
  width: 100%;
  height: 2px;
  background: var(--c-primary);
  transform: scaleX(0);
  transform-origin: left;
  transition: transform var(--t);
}
.card__more:hover::after {
  transform: scaleX(1);
}
.select {
  border: 1px solid var(--a-border);
  border-radius: 9px;
  padding: 0.45rem 0.7rem;
  font-size: 0.82rem;
  color: var(--a-text);
  background: #fff;
  cursor: pointer;
  transition:
    border-color var(--t-fast),
    box-shadow var(--t-fast);
}
.select:hover {
  border-color: var(--c-primary-light);
}
.select:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.12);
}

/* Lưới biểu đồ */
.main-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 1.1rem;
  margin-bottom: 1.4rem;
}
.chart-wrap {
  position: relative;
  min-height: 300px;
}
.chart-empty {
  min-height: 300px;
  justify-content: center;
}
.chart-refresh {
  position: absolute;
  top: 0;
  right: 0;
}
.side-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.1rem;
}
.mini-card {
  padding: 1.1rem;
  cursor: pointer;
  text-align: left;
  font: inherit;
}
.mini-card:hover {
  transform: translateY(-3px);
}
.mini-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}
.mini-card__label {
  font-size: 0.78rem;
  color: var(--a-text-muted);
}
.mini-card__trend {
  display: inline-flex;
  align-items: center;
  gap: 0.1rem;
  font-size: 0.72rem;
  font-weight: 700;
}
.mini-card__trend.is-up {
  color: #15803d;
}
.mini-card__trend.is-down {
  color: #dc2626;
}
.mini-card__value {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--a-text);
  margin: 0.35rem 0 0.7rem;
}

/* Lưới dưới */
.bottom-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 1.1rem;
  margin-bottom: 1.4rem;
}

/* Bảng */
.table-wrap {
  overflow-x: auto;
}
.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.88rem;
}
.table th {
  text-align: left;
  padding: 0.6rem 0.7rem;
  color: var(--a-text-muted);
  font-weight: 600;
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  border-bottom: 1px solid var(--a-border);
}
.table td {
  padding: 0.7rem;
  border-bottom: 1px solid var(--a-border);
  color: var(--a-text);
}
.table tbody tr:last-child td {
  border-bottom: none;
}
.row-link {
  cursor: pointer;
  transition: background var(--t-fast);
}
.row-link:hover {
  background: var(--a-bg);
}
.td-strong {
  font-weight: 600;
}
.td-empty {
  text-align: center;
  color: var(--a-text-muted);
  padding: 1.6rem 0.7rem;
}
.badge {
  display: inline-block;
  padding: 0.2rem 0.6rem;
  border-radius: 20px;
  font-size: 0.75rem;
  font-weight: 600;
}
.badge.is-ok {
  color: #15803d;
  background: #22c55e1f;
}
.badge.is-wait {
  color: #b45309;
  background: #f59e0b26;
}
.badge.is-no {
  color: #dc2626;
  background: #ef44441f;
}

/* Timeline lịch hôm nay */
.timeline {
  list-style: none;
  margin: 0;
  padding: 0;
}
.timeline__item {
  display: grid;
  grid-template-columns: 48px 16px 1fr;
  align-items: center;
  gap: 0.6rem;
  padding: 0.6rem 0.4rem;
  position: relative;
  border-radius: 8px;
  cursor: pointer;
  transition: background var(--t-fast);
}
.timeline__item:hover {
  background: var(--a-bg);
}
.timeline__item:hover .timeline__dot {
  transform: scale(1.25);
}
.timeline__item:not(:last-child)::after {
  content: '';
  position: absolute;
  left: 55px;
  top: 28px;
  bottom: -12px;
  width: 2px;
  background: var(--a-border);
}
.timeline__time {
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--a-text-muted);
}
.timeline__dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 3px solid #fff;
  box-shadow: 0 0 0 2px currentColor;
  z-index: 1;
  transition: transform var(--t);
}
.timeline__body {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}
.timeline__body strong {
  font-size: 0.9rem;
  color: var(--a-text);
}
.timeline__body small {
  font-size: 0.78rem;
  color: var(--a-text-muted);
}

/* Top giáo viên */
.teacher-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1rem;
}
.teacher {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.8rem;
  border: 1px solid var(--a-border);
  border-radius: 12px;
  position: relative;
  cursor: pointer;
  transition:
    box-shadow var(--t),
    transform var(--t),
    border-color var(--t);
}
.teacher:hover {
  transform: translateY(-3px);
  box-shadow: var(--a-shadow-lg);
  border-color: #d2e8e2;
}
.teacher:hover .teacher__avatar {
  transform: scale(1.07);
}
.teacher__rank {
  position: absolute;
  top: 0.5rem;
  right: 0.7rem;
  font-size: 0.75rem;
  font-weight: 800;
  color: var(--a-text-muted);
}
.teacher__avatar {
  width: 46px;
  height: 46px;
  flex: 0 0 auto;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 0.95rem;
  color: #fff;
  background: var(--grad-primary);
  transition: transform var(--t);
}
.teacher__info {
  min-width: 0;
}
.teacher strong {
  display: block;
  font-size: 0.9rem;
  color: var(--a-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.teacher small {
  font-size: 0.78rem;
  color: var(--a-text-muted);
}
.teacher__hours {
  margin-left: auto;
  font-weight: 700;
  color: var(--c-primary);
  font-size: 0.95rem;
}

/* Responsive */
@media (max-width: 1100px) {
  .main-grid {
    grid-template-columns: 1fr;
  }
  .bottom-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 520px) {
  .side-stats {
    grid-template-columns: 1fr;
  }
  .page-head__actions {
    width: 100%;
  }
}
</style>
