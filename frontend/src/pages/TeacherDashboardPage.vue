<script setup>
// Dashboard GIÁO VIÊN — chỉ hiển thị SỐ LIỆU, không so sánh kỳ trước/kỳ sau.
// Toàn bộ lịch lấy từ MỘT nguồn weekSchedule (dữ liệu mẫu, sau thay bằng API):
// các con số (buổi hôm nay / buổi tuần / số trường / lớp / môn) đều TÍNH ra từ đó
// nên khi nối API chỉ cần thay weekSchedule, các khối khác tự khớp.
import { computed } from 'vue'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const firstName = computed(() => auth.user?.fullName || 'Giáo viên')

// Mỗi môn 1 màu nhấn cố định để chip lịch tuần & chấm timeline đồng bộ nhau
const SUBJECT_COLORS = {
  Robotics: '#f97316',
  Scratch: '#0ea5e9',
  'AI cơ bản': '#2563eb',
}
const subjectColor = (subject) => SUBJECT_COLORS[subject] || '#f97316'

// Lịch dạy trong tuần (T2 → CN). cls = lớp phụ trách tại trường đó.
const weekSchedule = [
  {
    day: 'T2',
    sessions: [
      { time: '08:00', subject: 'Robotics', school: 'THCS Lê Quý Đôn', cls: '6A1', room: 'P.A1' },
      { time: '09:30', subject: 'Scratch', school: 'THCS Lê Quý Đôn', cls: '6A2', room: 'P.A2' },
      { time: '13:30', subject: 'AI cơ bản', school: 'TH Nguyễn Du', cls: '4A3', room: 'P.B1' },
    ],
  },
  {
    day: 'T3',
    sessions: [
      { time: '08:00', subject: 'Scratch', school: 'TH Nguyễn Du', cls: '5B2', room: 'P.B2' },
      { time: '09:30', subject: 'AI cơ bản', school: 'TH Nguyễn Du', cls: '4A3', room: 'P.B1' },
    ],
  },
  {
    day: 'T4',
    sessions: [
      { time: '08:00', subject: 'Robotics', school: 'THCS Lê Quý Đôn', cls: '7B1', room: 'P.A1' },
      { time: '09:30', subject: 'Scratch', school: 'THCS Lê Quý Đôn', cls: '6A1', room: 'P.A2' },
      { time: '13:30', subject: 'AI cơ bản', school: 'TH Nguyễn Du', cls: '5B2', room: 'P.B1' },
    ],
  },
  {
    day: 'T5',
    sessions: [
      { time: '08:00', subject: 'Robotics', school: 'THCS Lê Quý Đôn', cls: '6A2', room: 'P.A1' },
      { time: '13:30', subject: 'Scratch', school: 'TH Nguyễn Du', cls: '4A3', room: 'P.B2' },
    ],
  },
  {
    day: 'T6',
    sessions: [
      { time: '08:00', subject: 'Robotics', school: 'THCS Lê Quý Đôn', cls: '6A1', room: 'P.A1' },
      { time: '09:30', subject: 'AI cơ bản', school: 'THCS Lê Quý Đôn', cls: '7B1', room: 'P.A3' },
      { time: '13:30', subject: 'Scratch', school: 'TH Nguyễn Du', cls: '5B2', room: 'P.B1' },
    ],
  },
  {
    day: 'T7',
    sessions: [
      { time: '08:00', subject: 'Robotics', school: 'THCS Lê Quý Đôn', cls: '7B1', room: 'P.A1' },
    ],
  },
  { day: 'CN', sessions: [] },
]

// getDay(): 0 = CN, 1 = T2... → đổi về chỉ số 0 = T2 ... 6 = CN cho khớp mảng trên
const todayIdx = (new Date().getDay() + 6) % 7
const todaySessions = computed(() => weekSchedule[todayIdx].sessions)
const weekCount = computed(() => weekSchedule.reduce((sum, d) => sum + d.sessions.length, 0))
const todayLabel = new Intl.DateTimeFormat('vi-VN', {
  weekday: 'long',
  day: '2-digit',
  month: '2-digit',
}).format(new Date())

// 4 thẻ đầu trang: chỉ con số, không hint/không % tăng giảm
const stats = computed(() => [
  {
    icon: 'schedule',
    label: 'Buổi dạy hôm nay',
    value: todaySessions.value.length,
    color: '#f97316',
  },
  { icon: 'assignment', label: 'Buổi dạy tuần này', value: weekCount.value, color: '#0ea5e9' },
  { icon: 'clock', label: 'Giờ công tháng này', value: '72h', color: '#f59e0b' },
  { icon: 'evaluation', label: 'Điểm đánh giá', value: '4.7/5', color: '#2563eb' },
])

// Đếm số phần tử KHÁC NHAU theo 1 trường dữ liệu (Set tự loại trùng)
const countUnique = (field) =>
  new Set(weekSchedule.flatMap((d) => d.sessions.map((s) => s[field]))).size

const quickStats = computed(() => [
  { icon: 'school', label: 'Trường đang dạy', value: countUnique('school') },
  { icon: 'teacher', label: 'Lớp phụ trách', value: countUnique('cls') },
  { icon: 'subject', label: 'Môn đảm nhiệm', value: countUnique('subject') },
  { icon: 'attendance', label: 'Buổi đã dạy tháng này', value: 38 },
])

// Chip buổi dạy ở lưới tuần: nền mờ 9% + vạch trái đậm theo màu môn (hợp cả theme tối)
const sessionStyle = (s) => ({
  background: subjectColor(s.subject) + '17',
  borderLeftColor: subjectColor(s.subject),
})
</script>

<template>
  <div class="page-head">
    <h1 class="page-head__title">Xin chào, {{ firstName }}</h1>
  </div>

  <section class="stat-grid">
    <StatCard v-for="s in stats" :key="s.label" v-bind="s" />
  </section>

  <section class="grid-2">
    <!-- Lịch dạy hôm nay -->
    <div class="card">
      <div class="card__head">
        <h2 class="card__title">Lịch dạy hôm nay</h2>
        <span class="card__date">{{ todayLabel }}</span>
      </div>
      <ul v-if="todaySessions.length" class="timeline">
        <li v-for="t in todaySessions" :key="t.time" class="timeline__item">
          <span class="timeline__time">{{ t.time }}</span>
          <span class="timeline__dot" :style="{ background: subjectColor(t.subject) }" />
          <div class="timeline__body">
            <strong>{{ t.subject }}</strong>
            <small>{{ t.school }} · Lớp {{ t.cls }} · {{ t.room }}</small>
          </div>
        </li>
      </ul>
      <p v-else class="empty-note">Hôm nay không có buổi dạy.</p>
    </div>

    <!-- Số liệu giảng dạy -->
    <div class="card">
      <div class="card__head">
        <h2 class="card__title">Số liệu giảng dạy</h2>
      </div>
      <ul class="qstats">
        <li v-for="q in quickStats" :key="q.label" class="qstat">
          <span class="qstat__icon"><SvgIcon :name="q.icon" :size="17" /></span>
          <span class="qstat__label">{{ q.label }}</span>
          <strong class="qstat__value">{{ q.value }}</strong>
        </li>
      </ul>
    </div>
  </section>

  <!-- Lịch dạy tuần này -->
  <section class="card week-card">
    <div class="card__head">
      <h2 class="card__title">Lịch dạy tuần này</h2>
      <span class="week-total">{{ weekCount }} buổi</span>
    </div>
    <div class="week-scroll">
      <div class="week-grid">
        <div
          v-for="(d, i) in weekSchedule"
          :key="d.day"
          class="wday"
          :class="{ 'is-today': i === todayIdx }"
        >
          <div class="wday__head">
            <span class="wday__name">{{ d.day }}</span>
            <span v-if="d.sessions.length" class="wday__count">{{ d.sessions.length }} buổi</span>
          </div>
          <div v-for="s in d.sessions" :key="s.time" class="wsession" :style="sessionStyle(s)">
            <strong class="wsession__time">{{ s.time }}</strong>
            <span class="wsession__subject">{{ s.subject }}</span>
            <small class="wsession__cls">Lớp {{ s.cls }}</small>
          </div>
          <p v-if="!d.sessions.length" class="wday__off">Nghỉ</p>
        </div>
      </div>
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
  border-color: rgba(249, 115, 22, 0.35);
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
.card__date {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--c-primary);
  white-space: nowrap;
  text-transform: capitalize;
}
.grid-2 {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 1.1rem;
  margin-bottom: 1.4rem;
}
.empty-note {
  margin: 0;
  padding: 1.2rem 0;
  text-align: center;
  color: var(--a-text-muted);
  font-size: 0.9rem;
}

/* Timeline hôm nay */
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
  border: 3px solid var(--c-surface);
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

/* Số liệu giảng dạy */
.qstats {
  list-style: none;
  margin: 0;
  padding: 0;
}
.qstat {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.72rem 0;
  border-bottom: 1px solid var(--c-border-soft);
}
.qstat:last-child {
  border-bottom: none;
}
.qstat__icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: rgba(249, 115, 22, 0.1);
  color: var(--c-primary);
  flex: 0 0 auto;
}
.qstat__label {
  flex: 1;
  font-size: 0.88rem;
  color: var(--a-text);
}
.qstat__value {
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--a-text);
  font-variant-numeric: tabular-nums;
}

/* Lưới lịch tuần */
.week-total {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--c-primary-dark);
  background: rgba(249, 115, 22, 0.09);
  border: 1px solid rgba(249, 115, 22, 0.3);
  border-radius: 9999px;
  padding: 0.14rem 0.6rem;
  white-space: nowrap;
}
:root[data-theme='dark'] .week-total {
  color: #fdba74;
}
.week-scroll {
  overflow-x: auto;
}
.week-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(112px, 1fr));
  gap: 0.6rem;
}
.wday {
  border: 1px solid var(--a-border);
  border-radius: 12px;
  padding: 0.55rem 0.55rem 0.4rem;
  min-height: 120px;
  display: flex;
  flex-direction: column;
}
.wday.is-today {
  border-color: var(--c-primary);
  box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.16);
  background: rgba(249, 115, 22, 0.04);
}
.wday__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.3rem;
  margin-bottom: 0.45rem;
}
.wday__name {
  font-size: 0.76rem;
  font-weight: 800;
  text-transform: uppercase;
  color: var(--a-text-muted);
}
.wday.is-today .wday__name {
  color: var(--c-primary);
}
.wday__count {
  font-size: 0.68rem;
  font-weight: 700;
  color: var(--a-text-muted);
  white-space: nowrap;
}
.wsession {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 0.3rem 0.45rem;
  border-radius: 7px;
  border-left: 3px solid transparent;
  margin-bottom: 4px;
  line-height: 1.25;
}
.wsession__time {
  font-size: 0.72rem;
  color: var(--a-text);
}
.wsession__subject {
  font-size: 0.74rem;
  font-weight: 600;
  color: var(--a-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.wsession__cls {
  font-size: 0.68rem;
  color: var(--a-text-muted);
}
.wday__off {
  margin: auto 0;
  text-align: center;
  font-size: 0.76rem;
  color: var(--a-text-muted);
  opacity: 0.7;
}

@media (max-width: 1100px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
