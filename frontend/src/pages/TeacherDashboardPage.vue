<script setup>
// Dashboard GIÁO VIÊN — số liệu THẬT lấy từ API (đã bỏ dữ liệu mẫu):
//  • Lịch tuần / hôm nay: GET /schedules/mine (chỉ buổi ĐÃ DUYỆT của chính GV).
//  • Giờ công + số buổi đã dạy tháng này: GET /attendance/mine.
//  • Điểm đánh giá trung bình: GET /evaluations/stats (backend tự ép về chính GV).
import { ref, computed, onMounted } from 'vue'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { useAuthStore } from '@/stores/auth'
import { scheduleApi } from '@/api/schedules'
import { attendanceApi } from '@/api/attendance'
import { evaluationApi } from '@/api/evaluations'

const auth = useAuthStore()
const firstName = computed(() => auth.user?.fullName || 'Giáo viên')

/* ── Helper ngày theo GIỜ ĐỊA PHƯƠNG (tránh lệch UTC như bug timezone đã sửa) ── */
const iso = (d) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const addDays = (d, n) => {
  const x = new Date(d)
  x.setDate(x.getDate() + n)
  return x
}
// Đầu tuần = Thứ 2 (getDay: CN=0..T7=6 → lùi (getDay()+6)%7 ngày về Thứ 2).
const startOfWeek = (d) => addDays(d, -((d.getDay() + 6) % 7))

const now = new Date()
const TODAY_ISO = iso(now)
const wkStart = startOfWeek(now)
const monStart = new Date(now.getFullYear(), now.getMonth(), 1)
const monEnd = new Date(now.getFullYear(), now.getMonth() + 1, 0)
// index 0 = Thứ 2 ... 6 = CN — để tô sáng cột "hôm nay" trong lưới tuần.
const todayIdx = (now.getDay() + 6) % 7

/* ── State ── */
const loading = ref(false)
const error = ref('')
const weekEvents = ref([])
const monthEvents = ref([])
const monthAttendance = ref([])
const evalStats = ref(null)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [wk, mo, att] = await Promise.all([
      scheduleApi.mine({ from: iso(wkStart), to: iso(addDays(wkStart, 6)) }),
      scheduleApi.mine({ from: iso(monStart), to: iso(monEnd) }),
      attendanceApi.mine({ from: iso(monStart), to: iso(monEnd) }),
    ])
    weekEvents.value = wk.data || []
    monthEvents.value = mo.data || []
    monthAttendance.value = att.data || []
  } catch (e) {
    error.value = e?.response?.data?.message || 'Không tải được dữ liệu bảng điều khiển.'
  } finally {
    loading.value = false
  }
  // Đánh giá tải riêng: nếu tài khoản không có quyền xem, dashboard vẫn hiện phần còn lại.
  try {
    const { data } = await evaluationApi.stats()
    evalStats.value = data
  } catch {
    evalStats.value = null
  }
}
onMounted(load)

/* ── Màu theo môn (theo subjectId; hợp cả theme tối) ── */
const PALETTE = [
  '#f97316',
  '#0ea5e9',
  '#2563eb',
  '#16a34a',
  '#a855f7',
  '#e11d48',
  '#f59e0b',
  '#0d9488',
]
const subjectColor = (id) => PALETTE[(Number(id) || 0) % PALETTE.length]
const hhmm = (t) => (t ? String(t).slice(0, 5) : '')
const countBy = (list, field) => new Set(list.map((e) => e[field]).filter((v) => v != null)).size

/* ── Hôm nay (lọc từ lịch tuần, sắp theo giờ) ── */
const todaySessions = computed(() =>
  weekEvents.value
    .filter((e) => e.date === TODAY_ISO)
    .slice()
    .sort((a, b) => (a.startTime || '').localeCompare(b.startTime || '')),
)
const weekCount = computed(() => weekEvents.value.length)
const todayLabel = new Intl.DateTimeFormat('vi-VN', {
  weekday: 'long',
  day: '2-digit',
  month: '2-digit',
}).format(now)

/* ── Số liệu tháng này ── */
const monthHours = computed(() =>
  monthAttendance.value.reduce((s, r) => s + Number(r.hours || 0), 0),
)
const taughtThisMonth = computed(
  () => monthAttendance.value.filter((r) => r.status === 'PRESENT' || r.status === 'LATE').length,
)
const avgScore = computed(() => {
  const a = evalStats.value?.averageScore
  return a == null ? '—' : `${Number(a).toFixed(1)}/5`
})

/* ── 4 thẻ đầu trang ── */
const stats = computed(() => [
  {
    icon: 'schedule',
    label: 'Buổi dạy hôm nay',
    value: todaySessions.value.length,
    color: '#f97316',
  },
  { icon: 'assignment', label: 'Buổi dạy tuần này', value: weekCount.value, color: '#0ea5e9' },
  {
    icon: 'clock',
    label: 'Giờ công tháng này',
    value: `${Math.round(monthHours.value)}h`,
    color: '#f59e0b',
  },
  { icon: 'evaluation', label: 'Điểm đánh giá', value: avgScore.value, color: '#2563eb' },
])

/* ── Số liệu giảng dạy (phạm vi tháng này) ── */
const quickStats = computed(() => [
  { icon: 'school', label: 'Trường đang dạy', value: countBy(monthEvents.value, 'schoolId') },
  { icon: 'teacher', label: 'Lớp phụ trách', value: countBy(monthEvents.value, 'classId') },
  { icon: 'subject', label: 'Môn đảm nhiệm', value: countBy(monthEvents.value, 'subjectId') },
  { icon: 'attendance', label: 'Buổi đã dạy tháng này', value: taughtThisMonth.value },
])

/* ── Lưới lịch tuần: 7 cột T2..CN dựng từ /schedules/mine ── */
const DOW = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']
const weekSchedule = computed(() => {
  const cols = DOW.map((day, i) => ({ day, iso: iso(addDays(wkStart, i)), sessions: [] }))
  const byIso = Object.fromEntries(cols.map((c) => [c.iso, c]))
  for (const e of weekEvents.value) {
    if (byIso[e.date]) byIso[e.date].sessions.push(e)
  }
  cols.forEach((c) =>
    c.sessions.sort((a, b) => (a.startTime || '').localeCompare(b.startTime || '')),
  )
  return cols
})

/* Chip buổi dạy ở lưới tuần: nền mờ + vạch trái theo màu môn (hợp cả theme tối) */
const sessionStyle = (s) => ({
  background: subjectColor(s.subjectId) + '17',
  borderLeftColor: subjectColor(s.subjectId),
})
</script>

<template>
  <div class="page-head">
    <h1 class="page-head__title">Xin chào, {{ firstName }}</h1>
    <span v-if="loading" class="dash-loading">Đang tải…</span>
  </div>

  <p v-if="error" class="dash-error">{{ error }}</p>

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
        <li v-for="t in todaySessions" :key="t.id" class="timeline__item">
          <span class="timeline__time">{{ hhmm(t.startTime) }}</span>
          <span class="timeline__dot" :style="{ background: subjectColor(t.subjectId) }" />
          <div class="timeline__body">
            <strong>{{ t.subjectName }}</strong>
            <small>
              {{ t.schoolName }}<template v-if="t.className"> · Lớp {{ t.className }}</template>
            </small>
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
          <div v-for="s in d.sessions" :key="s.id" class="wsession" :style="sessionStyle(s)">
            <strong class="wsession__time">{{ hhmm(s.startTime) }}</strong>
            <span class="wsession__subject">{{ s.subjectName }}</span>
            <small v-if="s.className" class="wsession__cls">Lớp {{ s.className }}</small>
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
.dash-loading {
  font-size: 0.82rem;
  color: var(--a-text-muted);
}
.dash-error {
  margin: 0 0 1rem;
  padding: 0.7rem 1rem;
  border-radius: 10px;
  background: rgba(239, 68, 68, 0.1);
  color: #b91c1c;
  font-size: 0.88rem;
}
:root[data-theme='dark'] .dash-error {
  color: #f87171;
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
