<script setup>
// Dashboard TRƯỜNG — chỉ XEM (theo quy ước nghiệp vụ). Cùng phong cách dashboard admin.
// Dữ liệu mẫu (sau thay bằng API báo cáo theo trường).
import { computed } from 'vue'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const schoolName = computed(() => auth.user?.fullName || 'Trường')

const stats = [
  { icon: 'teacher', label: 'Giáo viên đang dạy', value: 12, hint: 'tại trường', color: '#f97316' },
  {
    icon: 'attendance',
    label: 'Lớp đang học',
    value: 18,
    hint: 'học kỳ này',
    trend: 4,
    color: '#0ea5e9',
  },
  {
    icon: 'schedule',
    label: 'Buổi dạy tuần này',
    value: 40,
    hint: 'so với tuần trước',
    trend: 7,
    color: '#f59e0b',
  },
  {
    icon: 'subject',
    label: 'Môn triển khai',
    value: 6,
    hint: 'STEM & Công dân số',
    color: '#2563eb',
  },
]

// Giáo viên đang dạy tại trường (trung tâm phân công tới)
const teachers = [
  { name: 'Nguyễn Minh', subject: 'Robotics', classes: 4, sessions: 8, status: 'Đang dạy' },
  { name: 'Trần Lan', subject: 'Scratch', classes: 3, sessions: 6, status: 'Đang dạy' },
  { name: 'Phạm Hùng', subject: 'AI cơ bản', classes: 2, sessions: 5, status: 'Sắp bắt đầu' },
  { name: 'Lê Hoa', subject: 'Tiếng Anh STEM', classes: 3, sessions: 6, status: 'Đang dạy' },
]
const statusClass = (s) => (s === 'Đang dạy' ? 'is-ok' : s === 'Sắp bắt đầu' ? 'is-wait' : 'is-no')

// Lịch dạy hôm nay tại trường
const todaySchedule = [
  { time: '08:00', subject: 'Robotics', teacher: 'Nguyễn Minh', room: 'P.A1', color: '#f97316' },
  { time: '09:30', subject: 'Scratch', teacher: 'Trần Lan', room: 'P.A2', color: '#0ea5e9' },
  { time: '14:00', subject: 'Tiếng Anh STEM', teacher: 'Lê Hoa', room: 'P.B3', color: '#2563eb' },
]
</script>

<template>
  <div class="page-head">
    <div>
      <h1 class="page-head__title">{{ schoolName }}</h1>
      <p class="page-head__crumb">Tổng quan / Trường</p>
    </div>
    <button class="btn-primary"><SvgIcon name="evaluation" :size="18" /> Xem báo cáo</button>
  </div>

  <section class="stat-grid">
    <StatCard v-for="s in stats" :key="s.label" v-bind="s" />
  </section>

  <section class="grid-2">
    <!-- Giáo viên tại trường -->
    <div class="card">
      <div class="card__head">
        <h2 class="card__title">Giáo viên đang dạy tại trường</h2>
        <a href="#" class="card__more">Xem tất cả</a>
      </div>
      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>Giáo viên</th>
              <th>Môn</th>
              <th>Lớp</th>
              <th>Buổi/tuần</th>
              <th>Trạng thái</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="t in teachers" :key="t.name">
              <td class="td-strong">{{ t.name }}</td>
              <td>{{ t.subject }}</td>
              <td>{{ t.classes }}</td>
              <td>{{ t.sessions }}</td>
              <td>
                <span class="badge" :class="statusClass(t.status)">{{ t.status }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Lịch dạy hôm nay -->
    <div class="card">
      <div class="card__head">
        <h2 class="card__title">Lịch dạy hôm nay</h2>
        <a href="#" class="card__more">Lịch tuần</a>
      </div>
      <ul class="timeline">
        <li v-for="t in todaySchedule" :key="t.time" class="timeline__item">
          <span class="timeline__time">{{ t.time }}</span>
          <span class="timeline__dot" :style="{ background: t.color }" />
          <div class="timeline__body">
            <strong>{{ t.subject }}</strong>
            <small>{{ t.teacher }} · {{ t.room }}</small>
          </div>
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
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
  box-shadow: 0 8px 18px rgba(249, 115, 22, 0.32);
  transition:
    transform var(--t-fast),
    box-shadow var(--t-fast);
}
.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(249, 115, 22, 0.42);
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1.1rem;
  margin-bottom: 1.4rem;
}
.card {
  background: #fff;
  border: 1px solid var(--a-border);
  border-radius: 16px;
  padding: 1.3rem;
  box-shadow: var(--a-shadow);
  transition:
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
.card__more {
  position: relative;
  font-size: 0.82rem;
  color: var(--c-primary);
  text-decoration: none;
  font-weight: 600;
  white-space: nowrap;
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
.grid-2 {
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

/* Timeline */
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

@media (max-width: 1100px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
