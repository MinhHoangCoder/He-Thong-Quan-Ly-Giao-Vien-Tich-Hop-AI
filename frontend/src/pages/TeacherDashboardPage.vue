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

/* ── Check in/out ──
 * Luật đơn giản: hôm nay có buổi dạy đã duyệt thì check-in/out được;
 * giờ vào/ra do SERVER ghi — FE chỉ gửi scheduleId và hiển thị trạng thái. */
const checkin = ref(null) // CheckinToday { date, sessions[] }
const checkinBusy = ref(false)
const checkinMsg = ref('') // thông báo kết quả/lỗi dưới header
const checkinMsgType = ref('info') // info | ok | err

/**
 * Buổi mà nút header hành động được. BE trả state theo cửa sổ giờ:
 * OPEN (trong cửa sổ) / CHECKED_IN / DONE / NOT_YET (chưa mở) / MISSED (quá giờ)
 * / LOCKED (kế toán đã ghi Vắng/Nghỉ phép) — nên OPEN luôn là buổi đúng giờ hiện tại.
 */
const activeSession = computed(() => {
  const list = checkin.value?.sessions || []
  return list.find((s) => s.state === 'CHECKED_IN') || list.find((s) => s.state === 'OPEN') || null
})

const allDone = computed(() => {
  const list = checkin.value?.sessions || []
  return list.length > 0 && list.every((s) => s.state === 'DONE')
})

/** HH:MM từ chuỗi LocalDateTime "2026-07-26T07:00:00". */
const hhmmDT = (dt) => String(dt || '').slice(11, 16)

/** Giờ MỞ cửa sổ check-in của một buổi (trước giờ dạy 30 phút — khớp BE). */
function openAt(s) {
  const d = new Date(s.startTime)
  if (Number.isNaN(d.getTime())) return ''
  d.setMinutes(d.getMinutes() - 30)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** Nhãn trạng thái từng buổi trong panel chấm công. */
function stateLabel(s) {
  switch (s.state) {
    case 'OPEN':
      return 'Đang mở'
    case 'CHECKED_IN':
      return `Đã vào ${hhmm(s.checkIn)}`
    case 'DONE':
      return `✓ ${hhmm(s.checkIn)}–${hhmm(s.checkOut)}`
    case 'NOT_YET':
      return `Mở lúc ${openAt(s)}`
    case 'MISSED':
      return 'Quá giờ — chưa chấm'
    case 'LOCKED':
      return 'Kế toán đã ghi nhận'
    default:
      return s.state || ''
  }
}

/** Nút LUÔN hiển thị — không có lịch thì disabled kèm ghi chú (tránh "biến mất" khó hiểu). */
const checkinButton = computed(() => {
  const s = activeSession.value
  if (s?.state === 'CHECKED_IN') return { label: 'Check out', mode: 'out', disabled: false }
  if (s?.state === 'OPEN') return { label: 'Check in', mode: 'in', disabled: false }
  if (allDone.value) return { label: '✓ Đã chấm công hôm nay', mode: null, disabled: true }
  const list = checkin.value?.sessions || []
  const notYet = list.find((x) => x.state === 'NOT_YET')
  if (notYet) {
    return {
      label: 'Check in',
      mode: null,
      disabled: true,
      hint: `Chưa tới giờ — buổi ${hhmmDT(notYet.startTime)} mở check-in lúc ${openAt(notYet)}`,
    }
  }
  if (list.some((x) => x.state === 'MISSED')) {
    return {
      label: 'Check in',
      mode: null,
      disabled: true,
      hint: 'Đã quá giờ chấm công buổi hôm nay.',
    }
  }
  if (list.some((x) => x.state === 'LOCKED')) {
    return {
      label: 'Check in',
      mode: null,
      disabled: true,
      hint: 'Buổi hôm nay đã được kế toán ghi nhận Vắng/Nghỉ phép',
    }
  }
  return {
    label: 'Check in',
    mode: null,
    disabled: true,
    hint: checkin.value
      ? 'Hôm nay không có lịch dạy — không cần chấm công'
      : 'Chưa tải được trạng thái chấm công',
  }
})

async function loadCheckin() {
  try {
    const { data } = await attendanceApi.checkinToday()
    checkin.value = data
  } catch (e) {
    checkin.value = null
    // Nói rõ vì sao nút bị khóa thay vì im lặng (trừ khi đã có thông báo khác).
    if (!checkinMsg.value) {
      checkinMsgType.value = 'err'
      checkinMsg.value =
        e?.response?.data?.message || 'Không tải được trạng thái chấm công hôm nay.'
    }
  }
}

/** Chấm công 1 buổi cụ thể — gọi từ nút header (buổi active) hoặc nút riêng từng buổi. */
async function doCheckin(session, mode) {
  if (!session || !mode || checkinBusy.value) return

  let note = undefined
  if (mode === 'in') {
    const startTimeMs = session.startTime ? new Date(session.startTime).getTime() : 0
    const isLate = startTimeMs > 0 && Date.now() > startTimeMs + 15 * 60 * 1000
    if (isLate) {
      const input = window.prompt(
        'Bạn đang check-in muộn (>15 phút). Vui lòng nhập lý do đi muộn (không bắt buộc):',
        '',
      )
      if (input === null) return // Người dùng bấm Hủy, hủy thao tác check-in
      note = input.trim() || undefined
    }
  }

  checkinBusy.value = true
  checkinMsg.value = ''
  try {
    const body = { scheduleId: session.scheduleId, note }
    const { data } =
      mode === 'out' ? await attendanceApi.checkOut(body) : await attendanceApi.checkIn(body)
    checkinMsgType.value = 'ok'
    checkinMsg.value =
      mode === 'out'
        ? `Đã check-out lúc ${hhmm(data.checkOut)} — ${session.schoolName || 'trường'}.`
        : `Đã check-in lúc ${hhmm(data.checkIn)} — ${session.schoolName || 'trường'}${
            data.status === 'LATE' ? ' (ghi nhận vào muộn)' : ''
          }.`
    // Làm mới trạng thái nút + bảng công tháng (giờ công thay đổi).
    await loadCheckin()
    try {
      const att = await attendanceApi.mine({ from: iso(monStart), to: iso(monEnd) })
      monthAttendance.value = att.data || []
    } catch {
      /* giữ số liệu cũ */
    }
  } catch (e) {
    checkinMsgType.value = 'err'
    checkinMsg.value = e?.response?.data?.message || e?.message || 'Chấm công thất bại.'
    // Trạng thái có thể đã đổi phía server (vd buổi vừa hết giờ) — tải lại cho khớp.
    await loadCheckin()
  } finally {
    checkinBusy.value = false
  }
}

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
  await loadCheckin()
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

/** Tra trạng thái chấm công theo scheduleId — gắn chip + nút vào thẻ "Lịch dạy hôm nay". */
const checkinBySchedule = computed(() => {
  const map = {}
  for (const s of checkin.value?.sessions || []) map[s.scheduleId] = s
  return map
})
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
/** Ghi chú dưới thẻ Điểm đánh giá — nói rõ "chưa có phiếu nào" thay vì chỉ dấu gạch. */
const avgScoreHint = computed(() => {
  if (!evalStats.value) return ''
  const n = evalStats.value.totalCount || 0
  return n ? `${n} lượt đánh giá` : 'Chưa có đánh giá nào'
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
  {
    icon: 'evaluation',
    label: 'Điểm đánh giá',
    value: avgScore.value,
    hint: avgScoreHint.value,
    color: '#2563eb',
  },
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
    <div class="page-head__actions">
      <span v-if="loading" class="dash-loading">Đang tải…</span>
      <button
        class="btn btn-primary checkin-btn"
        :class="{ 'checkin-btn--out': checkinButton.mode === 'out' }"
        :disabled="checkinButton.disabled || checkinBusy"
        :title="checkinButton.hint || activeSession?.schoolName || ''"
        @click="doCheckin(activeSession, checkinButton.mode)"
      >
        <SvgIcon name="attendance" :size="17" />
        {{ checkinBusy ? 'Đang xử lý…' : checkinButton.label }}
      </button>
    </div>
  </div>

  <!-- Trạng thái chấm công từng buổi nằm NGAY trong thẻ "Lịch dạy hôm nay" bên dưới
       (không lặp lại thông tin buổi dạy ở đây) — chỉ giữ 1 dòng lý do khi nút bị khóa. -->
  <p v-if="checkinButton.hint" class="checkin-target">{{ checkinButton.hint }}</p>

  <p v-if="checkinMsg" class="checkin-msg" :class="`checkin-msg--${checkinMsgType}`">
    {{ checkinMsg }}
  </p>

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
          <!-- Trạng thái chấm công của chính buổi này + nút khi đang trong cửa sổ -->
          <div v-if="checkinBySchedule[t.id]" class="timeline__checkin">
            <span
              class="checkin-state"
              :class="`checkin-state--${checkinBySchedule[t.id].state.toLowerCase()}`"
            >
              {{ stateLabel(checkinBySchedule[t.id]) }}
            </span>
            <button
              v-if="['OPEN', 'CHECKED_IN'].includes(checkinBySchedule[t.id].state)"
              type="button"
              class="btn btn-primary checkin-state__btn"
              :class="{ 'checkin-btn--out': checkinBySchedule[t.id].state === 'CHECKED_IN' }"
              :disabled="checkinBusy"
              @click="
                doCheckin(
                  checkinBySchedule[t.id],
                  checkinBySchedule[t.id].state === 'CHECKED_IN' ? 'out' : 'in',
                )
              "
            >
              {{ checkinBySchedule[t.id].state === 'CHECKED_IN' ? 'Check out' : 'Check in' }}
            </button>
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
.page-head__actions {
  display: flex;
  align-items: center;
  gap: 0.7rem;
}
/* Nút Check in/out — cùng vị trí/kiểu với "Tạo phân công" bên admin (.btn-primary global) */
.checkin-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}
.checkin-btn--out {
  background: linear-gradient(135deg, #34d399, #059669);
  box-shadow: 0 4px 12px rgba(5, 150, 105, 0.28);
}
.checkin-target {
  margin: -0.9rem 0 1rem;
  font-size: 0.82rem;
  color: var(--a-text-muted);
  text-align: right;
}
/* Chip + nút chấm công gắn trong dòng "Lịch dạy hôm nay" */
.timeline__checkin {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  white-space: nowrap;
}
.checkin-state {
  font-size: 0.75rem;
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
}
.checkin-state--open {
  background: #ecfdf5;
  color: #047857;
}
.checkin-state--checked_in {
  background: #eff6ff;
  color: #1d4ed8;
}
.checkin-state--done {
  background: #f0fdf4;
  color: #15803d;
}
.checkin-state--missed,
.checkin-state--locked {
  background: #fef2f2;
  color: #b91c1c;
}
.checkin-state__btn {
  padding: 0.25rem 0.7rem;
  font-size: 0.78rem;
}
:root[data-theme='dark'] .checkin-state {
  background: rgba(148, 163, 184, 0.15);
  color: #cbd5e1;
}
.checkin-msg {
  margin: -0.6rem 0 1rem;
  padding: 0.55rem 0.9rem;
  border-radius: 10px;
  font-size: 0.86rem;
}
.checkin-msg--info {
  background: rgba(37, 99, 235, 0.09);
  color: #2563eb;
}
.checkin-msg--ok {
  background: rgba(22, 163, 74, 0.1);
  color: #15803d;
}
.checkin-msg--err {
  background: rgba(239, 68, 68, 0.1);
  color: #b91c1c;
}
:root[data-theme='dark'] .checkin-msg--ok {
  color: #4ade80;
}
:root[data-theme='dark'] .checkin-msg--err {
  color: #f87171;
}
:root[data-theme='dark'] .checkin-msg--info {
  color: #93c5fd;
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
  /* cột 4 (auto) dành cho chip/nút chấm công — tự biến mất khi buổi không có dữ liệu */
  grid-template-columns: 48px 16px 1fr auto;
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
