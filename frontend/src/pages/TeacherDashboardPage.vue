<script setup>
// Dashboard GIÁO VIÊN — cùng phong cách dashboard admin. Dữ liệu mẫu (sau thay bằng API).
import { computed } from 'vue'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import StatCard from '@/components/ui/StatCard.vue'
import MiniBars from '@/components/charts/MiniBars.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const firstName = computed(() => auth.user?.fullName || 'Giáo viên')

const stats = [
  {
    icon: 'schedule',
    label: 'Buổi dạy hôm nay',
    value: 3,
    hint: 'còn 2 buổi chiều',
    color: '#f97316',
  },
  {
    icon: 'clock',
    label: 'Giờ công tháng này',
    value: 72,
    hint: 'mục tiêu 96h',
    trend: 9,
    color: '#0ea5e9',
  },
  {
    icon: 'assignment',
    label: 'Buổi dạy tuần này',
    value: 14,
    hint: 'so với tuần trước',
    trend: 6,
    color: '#f59e0b',
  },
  {
    icon: 'evaluation',
    label: 'Điểm đánh giá',
    value: '4.7/5',
    hint: 'học kỳ này',
    color: '#2563eb',
  },
]

// Lịch dạy hôm nay
const todaySchedule = [
  { time: '08:00', subject: 'Robotics', school: 'THCS Lê Quý Đôn', room: 'P.A1', color: '#f97316' },
  { time: '09:30', subject: 'Scratch', school: 'THCS Lê Quý Đôn', room: 'P.A2', color: '#0ea5e9' },
  { time: '13:30', subject: 'AI cơ bản', school: 'TH Nguyễn Du', room: 'P.B1', color: '#2563eb' },
]

// Thông báo gần đây
const notifications = [
  { title: 'Lịch dạy thứ 6 được duyệt', time: '2 giờ trước', type: 'ok' },
  { title: 'Bảng công tháng 5 đã chốt', time: 'Hôm qua', type: 'info' },
  { title: 'Cập nhật hồ sơ trước 15/06', time: '2 ngày trước', type: 'warn' },
]
const dotClass = (t) => (t === 'ok' ? 'is-ok' : t === 'warn' ? 'is-warn' : 'is-info')

// Giờ dạy 7 ngày gần nhất (mini chart)
const miniStats = [
  {
    label: 'Giờ dạy 7 ngày',
    value: '21h',
    data: [3, 4, 2, 5, 4, 3, 0],
    color: '#f97316',
    trend: 8,
  },
  {
    label: 'Tỉ lệ đúng giờ',
    value: '98%',
    data: [9, 8, 10, 9, 10, 9, 10],
    color: '#2563eb',
    trend: 2,
  },
  { label: 'Buổi sắp tới', value: '5', data: [1, 2, 1, 0, 1, 0, 0], color: '#f59e0b', trend: -3 },
]
</script>

<template>
  <div class="page-head">
    <div>
      <h1 class="page-head__title">Xin chào, {{ firstName }}</h1>
      <p class="page-head__crumb">Tổng quan / Giáo viên</p>
    </div>
    <button class="btn-primary"><SvgIcon name="clock" :size="18" /> Check-in</button>
  </div>

  <section class="stat-grid">
    <StatCard v-for="s in stats" :key="s.label" v-bind="s" />
  </section>

  <section class="grid-2">
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
            <small>{{ t.school }} · {{ t.room }}</small>
          </div>
        </li>
      </ul>
    </div>

    <!-- Thông báo -->
    <div class="card">
      <div class="card__head">
        <h2 class="card__title">Thông báo</h2>
        <a href="#" class="card__more">Tất cả</a>
      </div>
      <ul class="notis">
        <li v-for="n in notifications" :key="n.title" class="noti">
          <span class="noti__dot" :class="dotClass(n.type)" />
          <div class="noti__body">
            <strong>{{ n.title }}</strong>
            <small>{{ n.time }}</small>
          </div>
        </li>
      </ul>
    </div>
  </section>

  <section class="mini-grid">
    <div v-for="s in miniStats" :key="s.label" class="card mini-card">
      <div class="mini-card__top">
        <span class="mini-card__label">{{ s.label }}</span>
        <span class="mini-card__trend" :class="s.trend >= 0 ? 'is-up' : 'is-down'">
          <SvgIcon :name="s.trend >= 0 ? 'up' : 'down'" :size="13" />{{ Math.abs(s.trend) }}%
        </span>
      </div>
      <div class="mini-card__value">{{ s.value }}</div>
      <MiniBars :data="s.data" :color="s.color" />
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
  background: var(--c-surface);
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
  grid-template-columns: 1.2fr 1fr;
  gap: 1.1rem;
  margin-bottom: 1.4rem;
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

/* Notifications */
.notis {
  list-style: none;
  margin: 0;
  padding: 0;
}
.noti {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  padding: 0.6rem 0;
  border-bottom: 1px solid var(--a-border);
}
.noti:last-child {
  border-bottom: none;
}
.noti__dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  margin-top: 0.35rem;
  flex: 0 0 auto;
}
.noti__dot.is-ok {
  background: #22c55e;
}
.noti__dot.is-warn {
  background: #f59e0b;
}
.noti__dot.is-info {
  background: #0ea5e9;
}
.noti__body {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.noti__body strong {
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--a-text);
}
.noti__body small {
  font-size: 0.76rem;
  color: var(--a-text-muted);
}

/* Mini cards */
.mini-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
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

@media (max-width: 1100px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
