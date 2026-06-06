<script setup>
// Dashboard quản trị TSDMS — dữ liệu mẫu (sau này thay bằng API).
import SvgIcon from '@/components/ui/SvgIcon.vue'
import StatCard from '@/components/ui/StatCard.vue'
import LineChart from '@/components/charts/LineChart.vue'
import MiniBars from '@/components/charts/MiniBars.vue'

const stats = [
  { icon: 'teacher', label: 'Giáo viên đang dạy', value: 128, hint: 'so với tháng trước', trend: 8, color: '#0d9488' },
  { icon: 'school', label: 'Trường khách hàng', value: 42, hint: 'so với tháng trước', trend: 5, color: '#22c55e' },
  { icon: 'assignment', label: 'Phân công đang chạy', value: 96, hint: 'so với tháng trước', trend: 6, color: '#f59e0b' },
  { icon: 'schedule', label: 'Buổi dạy tuần này', value: 318, hint: 'so với tuần trước', trend: 14, color: '#0ea5e9' },
]

// Biểu đồ: số buổi dạy theo tháng cho 3 mảng môn.
const chartLabels = ['T1', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'T8']
const chartSeries = [
  { name: 'STEM', color: '#0d9488', data: [120, 145, 138, 170, 162, 190, 210, 235] },
  { name: 'Công dân số', color: '#0ea5e9', data: [80, 92, 110, 138, 150, 142, 168, 180] },
  { name: 'Ngoại ngữ', color: '#22c55e', data: [60, 72, 85, 90, 120, 132, 128, 150] },
]

const sideStats = [
  { label: 'Giờ dạy tháng này', value: '4.820', data: [4, 6, 5, 8, 7, 9, 11], color: '#0d9488', trend: 12 },
  { label: 'Tỉ lệ chấm công đúng giờ', value: '96%', data: [7, 8, 6, 9, 8, 9, 10], color: '#22c55e', trend: 3 },
  { label: 'Điểm đánh giá trung bình', value: '4.6/5', data: [6, 7, 7, 8, 9, 8, 9], color: '#0ea5e9', trend: 2 },
  { label: 'Buổi dạy chờ duyệt', value: '8', data: [9, 7, 8, 5, 6, 4, 3], color: '#f59e0b', trend: -18 },
]

// Phân công gần đây (trung tâm tự tạo — không còn yêu cầu từ trường).
const assignments = [
  { teacher: 'Nguyễn Minh', school: 'THCS Lê Quý Đôn', subject: 'Robotics', date: '06/06', status: 'Đang dạy' },
  { teacher: 'Trần Lan', school: 'TH Nguyễn Du', subject: 'Scratch', date: '05/06', status: 'Đang dạy' },
  { teacher: 'Phạm Hùng', school: 'THPT Trần Phú', subject: 'AI cơ bản', date: '05/06', status: 'Sắp bắt đầu' },
  { teacher: 'Lê Hoa', school: 'TH Kim Đồng', subject: 'Tiếng Anh STEM', date: '04/06', status: 'Đang dạy' },
  { teacher: 'Võ Nam', school: 'THCS Chu Văn An', subject: 'Lập trình Python', date: '03/06', status: 'Đã hủy' },
]

const statusClass = (s) =>
  s === 'Đang dạy' ? 'is-ok' : s === 'Sắp bắt đầu' ? 'is-wait' : 'is-no'

// Lịch dạy hôm nay (mẫu).
const todaySchedule = [
  { time: '08:00', teacher: 'Nguyễn Minh', subject: 'Robotics', school: 'THCS Lê Quý Đôn', color: '#0d9488' },
  { time: '09:30', teacher: 'Trần Lan', subject: 'Scratch', school: 'TH Nguyễn Du', color: '#0ea5e9' },
  { time: '13:00', teacher: 'Phạm Hùng', subject: 'AI cơ bản', school: 'THPT Trần Phú', color: '#22c55e' },
  { time: '15:00', teacher: 'Lê Hoa', subject: 'Python', school: 'THCS Chu Văn An', color: '#f59e0b' },
]

// Top giáo viên theo giờ dạy.
const topTeachers = [
  { name: 'Nguyễn Minh', subject: 'Robotics', hours: 86, img: 5 },
  { name: 'Trần Lan', subject: 'Scratch', hours: 78, img: 32 },
  { name: 'Phạm Hùng', subject: 'AI cơ bản', hours: 74, img: 13 },
  { name: 'Lê Hoa', subject: 'Tiếng Anh', hours: 69, img: 45 },
]
</script>

<template>
  <!-- Tiêu đề trang -->
  <div class="page-head">
    <div>
      <h1 class="page-head__title">Bảng điều khiển</h1>
      <p class="page-head__crumb">Tổng quan / Dashboard</p>
    </div>
    <button class="btn-primary">
      <SvgIcon name="plus" :size="18" /> Tạo phân công
    </button>
  </div>

  <!-- Thẻ thống kê -->
  <section class="stat-grid">
    <StatCard v-for="s in stats" :key="s.label" v-bind="s" />
  </section>

  <!-- Biểu đồ + chỉ số phụ -->
  <section class="main-grid">
    <div class="card chart-card">
      <div class="card__head">
        <div>
          <h2 class="card__title">Buổi dạy theo tháng</h2>
          <p class="card__sub">Thống kê số buổi dạy theo nhóm môn (năm 2026)</p>
        </div>
        <select class="select">
          <option>8 tháng gần nhất</option>
          <option>Cả năm</option>
        </select>
      </div>
      <LineChart :labels="chartLabels" :series="chartSeries" :height="300" />
    </div>

    <div class="side-stats">
      <div v-for="s in sideStats" :key="s.label" class="card mini-card">
        <div class="mini-card__top">
          <span class="mini-card__label">{{ s.label }}</span>
          <span class="mini-card__trend" :class="s.trend >= 0 ? 'is-up' : 'is-down'">
            <SvgIcon :name="s.trend >= 0 ? 'up' : 'down'" :size="13" />{{ Math.abs(s.trend) }}%
          </span>
        </div>
        <div class="mini-card__value">{{ s.value }}</div>
        <MiniBars :data="s.data" :color="s.color" />
      </div>
    </div>
  </section>

  <!-- Bảng phân công + lịch hôm nay -->
  <section class="bottom-grid">
    <div class="card">
      <div class="card__head">
        <h2 class="card__title">Phân công gần đây</h2>
        <a href="#" class="card__more">Xem tất cả</a>
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
            <tr v-for="r in assignments" :key="r.teacher + r.subject">
              <td class="td-strong">{{ r.teacher }}</td>
              <td>{{ r.school }}</td>
              <td>{{ r.subject }}</td>
              <td>{{ r.date }}</td>
              <td>
                <span class="badge" :class="statusClass(r.status)">{{ r.status }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="card">
      <div class="card__head">
        <h2 class="card__title">Lịch dạy hôm nay</h2>
        <a href="#" class="card__more">Lịch tuần</a>
      </div>
      <ul class="timeline">
        <li v-for="t in todaySchedule" :key="t.time + t.teacher" class="timeline__item">
          <span class="timeline__time">{{ t.time }}</span>
          <span class="timeline__dot" :style="{ background: t.color }" />
          <div class="timeline__body">
            <strong>{{ t.subject }}</strong>
            <small>{{ t.teacher }} · {{ t.school }}</small>
          </div>
        </li>
      </ul>
    </div>
  </section>

  <!-- Top giáo viên -->
  <section class="card">
    <div class="card__head">
      <h2 class="card__title">Giáo viên nổi bật tháng này</h2>
      <a href="#" class="card__more">Bảng xếp hạng</a>
    </div>
    <div class="teacher-grid">
      <div v-for="(t, i) in topTeachers" :key="t.name" class="teacher">
        <span class="teacher__rank">#{{ i + 1 }}</span>
        <img :src="`https://i.pravatar.cc/80?img=${t.img}`" :alt="t.name" />
        <div>
          <strong>{{ t.name }}</strong>
          <small>{{ t.subject }}</small>
        </div>
        <span class="teacher__hours">{{ t.hours }}h</span>
      </div>
    </div>
  </section>
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
.page-head__crumb {
  margin: 0.2rem 0 0;
  font-size: 0.82rem;
  color: var(--a-text-muted);
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
  box-shadow: 0 8px 18px rgba(13, 148, 136, 0.32);
  transition: transform var(--t-fast), box-shadow var(--t-fast), filter var(--t-fast);
}
.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(13, 148, 136, 0.42);
  filter: brightness(1.05);
}
.btn-primary:active {
  transform: translateY(0);
}
/* icon "+" quay nhẹ khi rê chuột */
.btn-primary:hover :deep(svg) {
  transform: rotate(90deg);
  transition: transform var(--t);
}

/* Lưới thẻ */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1.1rem;
  margin-bottom: 1.4rem;
}

/* Card chung */
.card {
  background: #fff;
  border: 1px solid var(--a-border);
  border-radius: 16px;
  padding: 1.3rem;
  box-shadow: var(--a-shadow);
  transition: transform var(--t), box-shadow var(--t), border-color var(--t);
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
  text-decoration: none;
  font-weight: 600;
  white-space: nowrap;
}
/* gạch chân chạy từ trái sang khi rê chuột (mượt hơn underline mặc định) */
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
  transition: border-color var(--t-fast), box-shadow var(--t-fast);
}
.select:hover {
  border-color: var(--c-primary-light);
}
.select:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(13, 148, 136, 0.12);
}

/* Lưới biểu đồ */
.main-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 1.1rem;
  margin-bottom: 1.4rem;
}
.side-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.1rem;
}
.mini-card {
  padding: 1.1rem;
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
.table tbody tr:hover {
  background: var(--a-bg);
}
.td-strong {
  font-weight: 600;
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
  padding: 0.6rem 0;
  position: relative;
  border-radius: 8px;
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
  transition: box-shadow var(--t), transform var(--t), border-color var(--t);
}
.teacher:hover {
  transform: translateY(-3px);
  box-shadow: var(--a-shadow-lg);
  border-color: #d2e8e2;
}
.teacher:hover img {
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
.teacher img {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  object-fit: cover;
  transition: transform var(--t);
}
.teacher strong {
  display: block;
  font-size: 0.9rem;
  color: var(--a-text);
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
}
</style>
