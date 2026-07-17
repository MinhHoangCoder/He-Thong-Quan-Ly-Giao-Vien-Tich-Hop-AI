<script setup>
/**
 * "Lịch dạy của tôi" (GIÁO VIÊN tự phục vụ) — chỉ xem các buổi dạy ĐÃ DUYỆT của CHÍNH mình.
 * Hai chế độ: Thời khóa biểu TUẦN (mặc định) và Lịch THÁNG (bấm ngày → chi tiết). Kèm:
 *  - Khối "Buổi dạy hôm nay" luôn hiển thị đúng (nạp riêng, không phụ thuộc khoảng đang xem).
 *  - Thẻ thống kê nhanh cho khoảng đang xem (số buổi / trường / lớp / môn).
 *  - Lọc theo Trường / Lớp (chỉ những nơi mình dạy).
 *  - In / Xuất PDF (in đúng phần lịch, ẩn khung app).
 *  - Bấm 1 buổi → mở nhanh Kho bài giảng theo môn.
 *
 * Dữ liệu lấy từ /schedules/mine (backend tự ép về hồ sơ GV đang đăng nhập — chống IDOR).
 */
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { scheduleApi } from '@/api/schedules'
import { tietLabel } from '@/utils/period'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import Pagination from '@/components/ui/Pagination.vue'

const DOW_LABELS = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'] // index 0 = Thứ 2, cuối là Chủ nhật
const PERIODS = [1, 2, 3, 4, 5, 6, 7, 8, 9]

/* ── Date helpers (local) ── */
const iso = (d) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const addDays = (d, n) => {
  const x = new Date(d)
  x.setDate(x.getDate() + n)
  return x
}
const addMonths = (d, n) => {
  const x = new Date(d)
  x.setDate(1)
  x.setMonth(x.getMonth() + n)
  return x
}
// Đầu tuần = Thứ 2. getDay(): CN=0,T2=1..T7=6 → lùi (getDay()+6)%7 ngày về Thứ 2.
const startOfWeek = (d) => addDays(d, -((d.getDay() + 6) % 7))
const TODAY_ISO = iso(new Date())

/* ── State ── */
const route = useRoute()
const router = useRouter()
// Mặc định: Thời khóa biểu TUẦN (theo yêu cầu). Cho phép ?view=month để mở thẳng lịch tháng.
const view = ref(route.query.view === 'month' ? 'month' : 'week') // 'week' | 'month'
const anchor = ref(new Date())
const filters = reactive({ schoolId: '', classId: '' })
const schoolOpts = ref([])
const classOptsAll = ref([]) // [{ id, name, schoolId }]
const events = ref([])
const todayEvents = ref([])
const loading = ref(false)
const errorMsg = ref('')
const selectedIso = ref(TODAY_ISO)

/* ── Lớp hiển thị trong dropdown: lọc theo trường đang chọn ── */
const classOpts = computed(() =>
  filters.schoolId
    ? classOptsAll.value.filter((c) => String(c.schoolId) === String(filters.schoolId))
    : classOptsAll.value,
)

/* ── Range theo chế độ ── */
function rangeIso() {
  if (view.value === 'week') {
    const s = startOfWeek(anchor.value)
    return { from: iso(s), to: iso(addDays(s, 6)) }
  }
  const gridStart = startOfWeek(new Date(anchor.value.getFullYear(), anchor.value.getMonth(), 1))
  return { from: iso(gridStart), to: iso(addDays(gridStart, 41)) }
}

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const { from, to } = rangeIso()
    const { data } = await scheduleApi.mine({
      from,
      to,
      schoolId: filters.schoolId,
      classId: filters.classId,
    })
    events.value = data
  } catch (e) {
    events.value = []
    errorMsg.value = e?.response?.data?.message || 'Không tải được lịch dạy.'
  } finally {
    loading.value = false
  }
}

// "Hôm nay" nạp riêng để luôn đúng dù đang xem tuần/tháng khác. Cũng áp bộ lọc trường/lớp.
async function loadToday() {
  try {
    const { data } = await scheduleApi.mine({
      from: TODAY_ISO,
      to: TODAY_ISO,
      schoolId: filters.schoolId,
      classId: filters.classId,
    })
    todayEvents.value = data
  } catch {
    todayEvents.value = []
  }
}

/* ── Gom sự kiện theo ngày ── */
const eventsByDate = computed(() => {
  const m = {}
  for (const e of events.value) (m[e.date] ??= []).push(e)
  for (const k in m) m[k].sort((a, b) => (a.startTime || '').localeCompare(b.startTime || ''))
  return m
})

// Đếm số phần tử KHÁC NHAU theo 1 trường dữ liệu trong danh sách buổi.
const countBy = (list, field) => new Set(list.map((e) => e[field]).filter((v) => v != null)).size

/* ── Thẻ thống kê nhanh cho khoảng đang xem ── */
const rangeStats = computed(() => [
  { icon: 'schedule', label: 'Buổi dạy', value: events.value.length, color: '#f97316' },
  { icon: 'school', label: 'Trường', value: countBy(events.value, 'schoolId'), color: '#0ea5e9' },
  { icon: 'teacher', label: 'Lớp', value: countBy(events.value, 'classId'), color: '#2563eb' },
  { icon: 'subject', label: 'Môn', value: countBy(events.value, 'subjectId'), color: '#f59e0b' },
])
const statsScopeLabel = computed(() => (view.value === 'week' ? 'tuần đang xem' : 'tháng đang xem'))

/* ── Buổi dạy hôm nay (đã sắp theo giờ) ── */
const todaySorted = computed(() =>
  [...todayEvents.value].sort((a, b) => (a.startTime || '').localeCompare(b.startTime || '')),
)
const todayLabel = new Intl.DateTimeFormat('vi-VN', {
  weekday: 'long',
  day: '2-digit',
  month: '2-digit',
}).format(new Date())

/* ── Màu theo môn (chip tuần / chấm hôm nay) — bảng màu cố định, hợp cả theme tối ── */
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
const subjectColor = (id) => {
  const n = Number(id) || 0
  return PALETTE[n % PALETTE.length]
}
const hhmm = (t) => (t ? String(t).slice(0, 5) : '')

/* ── Lưới tháng (6 tuần × 7) ── */
const monthCells = computed(() => {
  const start = startOfWeek(new Date(anchor.value.getFullYear(), anchor.value.getMonth(), 1))
  const cells = []
  for (let i = 0; i < 42; i++) {
    const d = addDays(start, i)
    const k = iso(d)
    const dayEvents = eventsByDate.value[k] || []
    cells.push({
      iso: k,
      day: d.getDate(),
      inMonth: d.getMonth() === anchor.value.getMonth(),
      isToday: k === TODAY_ISO,
      count: dayEvents.length,
    })
  }
  return cells
})

/* ── Lưới tuần ── */
const weekDays = computed(() => {
  const s = startOfWeek(anchor.value)
  const arr = []
  for (let i = 0; i < 7; i++) {
    const d = addDays(s, i)
    arr.push({
      iso: iso(d),
      label: DOW_LABELS[i],
      dnum: d.getDate(),
      isToday: iso(d) === TODAY_ISO,
    })
  }
  return arr
})
function cellEvents(period, dayIso) {
  return (eventsByDate.value[dayIso] || []).filter((e) => e.periodNumber === period)
}

/* ── Chi tiết ngày (chế độ tháng) ── */
const selectedEvents = computed(() => eventsByDate.value[selectedIso.value] || [])
// Sắp theo trường (A→Z), rồi tiết tăng dần, rồi giờ bắt đầu.
const selectedRows = computed(() =>
  [...selectedEvents.value].sort(
    (a, b) =>
      (a.schoolName || '').localeCompare(b.schoolName || '', 'vi') ||
      (a.periodNumber ?? 0) - (b.periodNumber ?? 0) ||
      (a.startTime || '').localeCompare(b.startTime || ''),
  ),
)
const selectedSchoolCount = computed(() => countBy(selectedEvents.value, 'schoolId'))
const selectedLabel = computed(() => {
  const [y, m, d] = selectedIso.value.split('-')
  return `${d}/${m}/${y}`
})

/* ── Phân trang chi tiết ngày: 10 dòng/trang, vẫn gom theo trường ── */
const ROWS_PER_PAGE = 10
const detailPage = ref(0)
const detailTotalPages = computed(() => Math.ceil(selectedRows.value.length / ROWS_PER_PAGE))
const pagedSchoolGroups = computed(() => {
  const start = detailPage.value * ROWS_PER_PAGE
  const slice = selectedRows.value.slice(start, start + ROWS_PER_PAGE)
  const groups = []
  for (const e of slice) {
    const key = e.schoolId ?? e.schoolName
    const last = groups[groups.length - 1]
    if (last && last.key === key) last.events.push(e)
    else groups.push({ key, schoolName: e.schoolName, events: [e] })
  }
  return groups
})

/* ── Tiêu đề + điều hướng ── */
const title = computed(() => {
  if (view.value === 'week') {
    const s = startOfWeek(anchor.value)
    const e = addDays(s, 6)
    return `${s.getDate()}/${s.getMonth() + 1} – ${e.getDate()}/${e.getMonth() + 1}/${e.getFullYear()}`
  }
  return `Tháng ${anchor.value.getMonth() + 1}, ${anchor.value.getFullYear()}`
})
function go(dir) {
  anchor.value =
    view.value === 'week' ? addDays(anchor.value, dir * 7) : addMonths(anchor.value, dir)
  detailPage.value = 0
  load()
}
function goToday() {
  anchor.value = new Date()
  selectedIso.value = TODAY_ISO
  detailPage.value = 0
  load()
}
function setView(v) {
  view.value = v
  load()
}
function selectDay(k) {
  selectedIso.value = k
  detailPage.value = 0
}

/* ── Bộ lọc ── */
async function loadFilters() {
  try {
    const { data } = await scheduleApi.myFilters()
    schoolOpts.value = data.schools || []
    classOptsAll.value = data.classes || []
  } catch {
    /* im lặng — vẫn xem được lịch, chỉ là không có dropdown lọc */
  }
}
function onSchoolChange() {
  // Đổi trường → lớp đang chọn có thể không còn thuộc trường mới, reset cho an toàn.
  if (filters.classId && !classOpts.value.some((c) => String(c.id) === String(filters.classId))) {
    filters.classId = ''
  }
  reloadAll()
}
function resetFilters() {
  filters.schoolId = ''
  filters.classId = ''
  reloadAll()
}
function reloadAll() {
  load()
  loadToday()
}

/* ── Mở nhanh Kho bài giảng theo môn của buổi ── */
function openLessons(ev) {
  router.push({ name: 'teacher-lessons', query: ev.subjectName ? { keyword: ev.subjectName } : {} })
}

/* ── In / Xuất PDF: gắn cờ body để CSS @media print chỉ chừa phần lịch ── */
function printSchedule() {
  document.body.classList.add('tsched-printing')
  const cleanup = () => document.body.classList.remove('tsched-printing')
  window.addEventListener('afterprint', cleanup, { once: true })
  window.print()
  // Dự phòng nếu trình duyệt không bắn afterprint.
  setTimeout(cleanup, 1500)
}

onMounted(() => {
  loadFilters()
  load()
  loadToday()
})
onBeforeUnmount(() => document.body.classList.remove('tsched-printing'))
</script>

<template>
  <div class="page tsched">
    <div class="page-head tsched__noprint">
      <div>
        <h1 class="tsched__title">Lịch dạy của tôi</h1>
        <p class="tsched__sub">Các buổi dạy đã được duyệt của bạn</p>
      </div>
      <div class="head-actions">
        <div class="viewtoggle">
          <button :class="{ on: view === 'week' }" @click="setView('week')">Tuần</button>
          <button :class="{ on: view === 'month' }" @click="setView('month')">Tháng</button>
        </div>
        <button class="btn btn-primary btn-print" @click="printSchedule">
          <SvgIcon name="payroll" :size="16" /> In / Xuất PDF
        </button>
      </div>
    </div>

    <p v-if="errorMsg" class="alert tsched__noprint">{{ errorMsg }}</p>

    <!-- ===== Buổi dạy hôm nay ===== -->
    <section class="card today-card tsched__noprint">
      <div class="card__head">
        <h2 class="card__title">Buổi dạy hôm nay</h2>
        <span class="card__date">{{ todayLabel }}</span>
      </div>
      <ul v-if="todaySorted.length" class="timeline">
        <li v-for="t in todaySorted" :key="t.id" class="timeline__item" @click="openLessons(t)">
          <span class="timeline__time">{{ hhmm(t.startTime) }}</span>
          <span class="timeline__dot" :style="{ background: subjectColor(t.subjectId) }" />
          <div class="timeline__body">
            <strong>{{ t.subjectName }}</strong>
            <small>
              {{ tietLabel(t.periodNumber, t.sessionType) }} · {{ t.schoolName }}
              <template v-if="t.className"> · Lớp {{ t.className }}</template>
            </small>
          </div>
          <span class="timeline__go"><SvgIcon name="subject" :size="15" /> Bài giảng</span>
        </li>
      </ul>
      <p v-else class="empty-note">Hôm nay bạn không có buổi dạy.</p>
    </section>

    <!-- ===== Thống kê nhanh (khoảng đang xem) ===== -->
    <section class="stat-grid tsched__noprint">
      <div v-for="s in rangeStats" :key="s.label" class="statcard">
        <span class="statcard__icon" :style="{ background: s.color + '1a', color: s.color }">
          <SvgIcon :name="s.icon" :size="18" />
        </span>
        <div class="statcard__body">
          <strong class="statcard__value">{{ s.value }}</strong>
          <span class="statcard__label">{{ s.label }}</span>
        </div>
      </div>
      <p class="stat-scope">Tính theo {{ statsScopeLabel }}</p>
    </section>

    <!-- ===== Bộ lọc ===== -->
    <div class="toolbar tsched__noprint">
      <label>Trường</label>
      <select v-model="filters.schoolId" @change="onSchoolChange">
        <option value="">Tất cả</option>
        <option v-for="s in schoolOpts" :key="s.id" :value="s.id">{{ s.name }}</option>
      </select>
      <label>Lớp</label>
      <select v-model="filters.classId" :disabled="!classOpts.length" @change="reloadAll">
        <option value="">{{ classOpts.length ? 'Tất cả' : 'Không có lớp' }}</option>
        <option v-for="c in classOpts" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <button class="btn btn-outline btn-sm" @click="resetFilters">Xóa lọc</button>
    </div>

    <!-- ===== Thanh điều hướng ===== -->
    <div class="calbar card">
      <button class="navbtn tsched__noprint" @click="go(-1)">‹</button>
      <span class="calbar__title">{{ title }}</span>
      <button class="navbtn tsched__noprint" @click="go(1)">›</button>
      <button class="btn btn-outline btn-sm today-btn tsched__noprint" @click="goToday">
        Hôm nay
      </button>
      <span v-if="loading" class="loading-dot tsched__noprint">Đang tải…</span>
    </div>

    <!-- ================= THỜI KHÓA BIỂU TUẦN ================= -->
    <div v-if="view === 'week'" class="week card">
      <table class="wtable">
        <thead>
          <tr>
            <th class="wcorner">Buổi · Tiết</th>
            <th v-for="d in weekDays" :key="d.iso" :class="{ today: d.isToday }">
              <div class="wday">{{ d.label }}</div>
              <div class="wdnum">{{ d.dnum }}</div>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in PERIODS" :key="p" :class="{ 'week-sep': p === 6 }">
            <td class="wperiod">{{ tietLabel(p) }}</td>
            <td v-for="d in weekDays" :key="d.iso" class="wcell">
              <button
                v-for="e in cellEvents(p, d.iso)"
                :key="e.id"
                class="wchip"
                :style="{
                  background: subjectColor(e.subjectId) + '17',
                  borderLeftColor: subjectColor(e.subjectId),
                }"
                :title="`${tietLabel(e.periodNumber, e.sessionType)} · ${e.subjectName} · ${e.schoolName}${e.className ? ' · Lớp ' + e.className : ''} — bấm để mở bài giảng`"
                @click="openLessons(e)"
              >
                <span class="wchip__subj">{{ e.subjectName }}</span>
                <span class="wchip__meta"
                  >{{ e.schoolName
                  }}<template v-if="e.className"> · {{ e.className }}</template></span
                >
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!events.length && !loading" class="week-hint text-muted small tsched__noprint">
        Không có buổi dạy đã duyệt trong tuần này.
      </p>
    </div>

    <!-- ================= LỊCH THÁNG ================= -->
    <div v-else class="month-wrap">
      <div class="cal card">
        <div class="cal__dow">
          <div v-for="d in DOW_LABELS" :key="d" class="cal__dowcell">{{ d }}</div>
        </div>
        <div class="cal__grid">
          <button
            v-for="c in monthCells"
            :key="c.iso"
            class="daycell"
            :class="{ out: !c.inMonth, today: c.isToday, sel: c.iso === selectedIso }"
            @click="selectDay(c.iso)"
          >
            <span class="daycell__num">{{ c.day }}</span>
            <span v-if="c.count" class="daycell__pill">{{ c.count }} buổi</span>
          </button>
        </div>
      </div>

      <!-- Chi tiết ngày đã chọn — gom theo trường -->
      <div class="detail card">
        <h3 class="detail__title">
          Ngày {{ selectedLabel }} —
          <strong>{{ selectedEvents.length }} buổi</strong>
          <span class="detail__sub">&nbsp;· {{ selectedSchoolCount }} trường</span>
        </h3>
        <div v-if="!selectedEvents.length" class="detail__empty text-muted">Không có buổi dạy.</div>
        <div v-else class="schoolgroups">
          <div v-for="g in pagedSchoolGroups" :key="g.key" class="schoolgroup">
            <div class="schoolgroup__head">
              <span class="schoolgroup__name">{{ g.schoolName }}</span>
              <span class="schoolgroup__count">{{ g.events.length }} buổi</span>
            </div>
            <ul class="evlist">
              <li class="evhead">
                <span>Buổi · Tiết</span>
                <span>Môn</span>
                <span>Lớp</span>
                <span></span>
              </li>
              <li
                v-for="e in g.events"
                :key="e.id"
                class="evrow"
                :class="e.sessionType === 'AFTERNOON' ? 'pm' : 'am'"
              >
                <span class="evrow__period">{{ tietLabel(e.periodNumber, e.sessionType) }}</span>
                <span class="evrow__subject">{{ e.subjectName }}</span>
                <span class="evrow__class">{{ e.className || '—' }}</span>
                <button
                  class="evrow__link tsched__noprint"
                  title="Mở kho bài giảng"
                  @click="openLessons(e)"
                >
                  <SvgIcon name="subject" :size="14" /> Bài giảng
                </button>
              </li>
            </ul>
          </div>
        </div>

        <Pagination v-model="detailPage" :total-pages="detailTotalPages" class="tsched__noprint" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.tsched__title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--a-text, var(--c-text));
}
.tsched__sub {
  margin: 0.25rem 0 0;
  font-size: 0.88rem;
  color: var(--c-text-muted);
}
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
  margin-bottom: 1.2rem;
}
.head-actions {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
}
.viewtoggle {
  display: inline-flex;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 10px;
  overflow: hidden;
}
.viewtoggle button {
  border: none;
  background: transparent;
  padding: 0.5rem 1.1rem;
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--c-text-muted);
  cursor: pointer;
}
.viewtoggle button.on {
  background: var(--grad-primary);
  color: #fff;
}
.btn-print {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.alert {
  margin: 0 0 1rem;
  padding: 0.7rem 1rem;
  border-radius: 10px;
  background: rgba(239, 68, 68, 0.1);
  color: #b91c1c;
  font-size: 0.88rem;
}
:root[data-theme='dark'] .alert {
  color: #f87171;
}

/* ===== Card chung ===== */
.card {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 14px;
}
.card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.6rem;
}
.card__title {
  margin: 0;
  font-size: 1.02rem;
  font-weight: 700;
  color: var(--a-text, var(--c-text));
}
.card__date {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--c-primary);
  text-transform: capitalize;
}

/* ===== Hôm nay ===== */
.today-card {
  padding: 1.1rem 1.2rem;
  margin-bottom: 1.1rem;
}
.timeline {
  list-style: none;
  margin: 0;
  padding: 0;
}
.timeline__item {
  display: grid;
  grid-template-columns: 52px 14px 1fr auto;
  align-items: center;
  gap: 0.6rem;
  padding: 0.55rem 0.4rem;
  border-radius: 9px;
  cursor: pointer;
  transition: background var(--t-fast);
}
.timeline__item:hover {
  background: var(--c-surface-2);
}
.timeline__time {
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--c-text-muted);
  font-variant-numeric: tabular-nums;
}
.timeline__dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}
.timeline__body {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
  min-width: 0;
}
.timeline__body strong {
  font-size: 0.9rem;
  color: var(--a-text, var(--c-text));
}
.timeline__body small {
  font-size: 0.78rem;
  color: var(--c-text-muted);
}
.timeline__go {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--c-primary);
  white-space: nowrap;
}
.empty-note {
  margin: 0;
  padding: 1rem 0;
  text-align: center;
  color: var(--c-text-muted);
  font-size: 0.9rem;
}

/* ===== Thống kê ===== */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 0.9rem;
  margin-bottom: 1.1rem;
  position: relative;
}
.statcard {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.85rem 1rem;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 14px;
}
.statcard__icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 11px;
  flex: 0 0 auto;
}
.statcard__body {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}
.statcard__value {
  font-size: 1.4rem;
  font-weight: 800;
  color: var(--a-text, var(--c-text));
  font-variant-numeric: tabular-nums;
}
.statcard__label {
  font-size: 0.8rem;
  color: var(--c-text-muted);
}
.stat-scope {
  position: absolute;
  right: 2px;
  top: -1.25rem;
  margin: 0;
  font-size: 0.74rem;
  color: var(--c-text-muted);
}

/* ===== Toolbar lọc ===== */
.toolbar {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
  padding: 0.75rem 1rem;
  margin-bottom: 1rem;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 12px;
}
.toolbar label {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--c-text-muted);
}
.toolbar select {
  height: 38px;
  min-width: 150px;
  padding: 0 0.6rem;
  border: 1px solid var(--c-input-border, var(--c-border));
  border-radius: 8px;
  background: var(--c-surface);
  color: var(--c-text);
  font-size: 0.88rem;
}
.toolbar select:disabled {
  opacity: 0.55;
}

/* ===== Thanh điều hướng lịch ===== */
.calbar {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.6rem 1rem;
  margin-bottom: 1rem;
}
.calbar__title {
  font-size: 1.05rem;
  font-weight: 700;
  min-width: 160px;
  text-align: center;
  color: var(--a-text, var(--c-text));
}
.navbtn {
  width: 34px;
  height: 34px;
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  border-radius: 8px;
  font-size: 1.2rem;
  line-height: 1;
  color: var(--c-text);
  cursor: pointer;
}
.navbtn:hover {
  border-color: var(--c-primary);
  color: var(--c-primary);
}
.today-btn {
  margin-left: 0.25rem;
}
.loading-dot {
  margin-left: auto;
  font-size: 0.8rem;
  color: var(--c-text-muted);
}

/* ===== Lịch tháng ===== */
.month-wrap {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
}
.cal {
  padding: 0.5rem;
}
.cal__dow,
.cal__grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
}
.cal__dowcell {
  text-align: center;
  padding: 0.5rem 0;
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--c-text-muted);
  text-transform: uppercase;
}
.cal__grid {
  gap: 6px;
}
.daycell {
  position: relative;
  min-height: 78px;
  border: 1px solid var(--c-border);
  border-radius: 10px;
  background: var(--c-surface);
  padding: 0.4rem 0.5rem;
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--t-fast),
    box-shadow var(--t-fast),
    transform var(--t-fast);
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}
.daycell:hover {
  border-color: var(--c-primary);
  transform: translateY(-1px);
}
.daycell.out {
  background: var(--c-bg);
  color: var(--c-text-muted);
  opacity: 0.65;
}
.daycell.today {
  border-color: var(--c-accent);
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.15);
}
.daycell.sel {
  border-color: var(--c-primary);
  box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.25);
}
.daycell__num {
  font-size: 0.9rem;
  font-weight: 700;
}
.daycell__pill {
  align-self: flex-start;
  font-size: 0.68rem;
  font-weight: 700;
  padding: 0.08rem 0.45rem;
  border-radius: 9999px;
  background: rgba(249, 115, 22, 0.09);
  color: var(--c-primary-dark);
  border: 1px solid rgba(249, 115, 22, 0.3);
}

/* Chi tiết ngày */
.detail {
  padding: 1rem 1.2rem;
}
.detail__title {
  margin: 0 0 0.8rem;
  font-size: 1rem;
  font-weight: 600;
}
.detail__sub {
  font-weight: 600;
  color: var(--c-text-muted);
  font-size: 0.9rem;
}
.schoolgroups {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}
.schoolgroup {
  border: 1px solid var(--c-border);
  border-radius: 11px;
  overflow: hidden;
}
.schoolgroup__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  padding: 0.55rem 0.8rem;
  background: rgba(249, 115, 22, 0.09);
  border-bottom: 1px solid rgba(249, 115, 22, 0.3);
}
.schoolgroup__name {
  font-weight: 700;
  color: var(--c-primary-dark);
  font-size: 0.9rem;
}
.schoolgroup__count {
  font-size: 0.74rem;
  font-weight: 700;
  color: var(--c-primary-dark);
  background: var(--c-surface);
  border: 1px solid rgba(249, 115, 22, 0.3);
  border-radius: 9999px;
  padding: 0.08rem 0.5rem;
  white-space: nowrap;
}
.evlist {
  list-style: none;
  margin: 0;
  padding: 0.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}
.evhead {
  display: grid;
  grid-template-columns: 120px 1.4fr 0.8fr 110px;
  gap: 0.6rem;
  align-items: center;
  padding: 0.15rem 0.7rem 0.3rem;
  border-left: 3px solid transparent;
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  color: var(--c-text-muted);
}
.evrow {
  display: grid;
  grid-template-columns: 120px 1.4fr 0.8fr 110px;
  gap: 0.6rem;
  align-items: center;
  padding: 0.5rem 0.7rem;
  border-radius: 9px;
  background: var(--c-surface-2);
  border-left: 3px solid var(--c-primary);
  font-size: 0.86rem;
}
.evrow.pm {
  border-left-color: var(--c-accent);
}
.evrow__period {
  font-size: 0.76rem;
  font-weight: 700;
  color: var(--c-primary-dark);
}
.evrow__subject {
  font-weight: 600;
}
.evrow__class {
  color: var(--c-text-muted);
  font-size: 0.82rem;
}
.evrow__link {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  justify-self: end;
  padding: 0.28rem 0.5rem;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  background: var(--c-surface);
  color: var(--c-primary);
  font-size: 0.74rem;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition:
    border-color var(--t-fast),
    background var(--t-fast);
}
.evrow__link:hover {
  border-color: var(--c-primary);
  background: rgba(249, 115, 22, 0.08);
}

/* ===== Thời khóa biểu tuần ===== */
.week {
  padding: 0;
  overflow-x: auto;
}
.wtable {
  width: 100%;
  border-collapse: collapse;
  min-width: 760px;
}
.wtable th,
.wtable td {
  border: 1px solid var(--c-border);
}
.wtable thead th {
  background: var(--c-surface-2);
  padding: 0.5rem;
  text-align: center;
}
.wtable thead th.today {
  background: rgba(37, 99, 235, 0.12);
}
.wday {
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--c-text-muted);
}
.wdnum {
  font-size: 1.1rem;
  font-weight: 800;
}
.wcorner {
  width: 104px;
}
.wperiod {
  width: 104px;
  text-align: left;
  padding-left: 0.5rem;
  font-size: 0.76rem;
  font-weight: 700;
  color: var(--c-text-muted);
  background: var(--c-surface-2);
  white-space: nowrap;
}
.week-sep td {
  border-top: 2px solid var(--c-primary-light);
}
.wcell {
  vertical-align: top;
  padding: 4px;
  height: 60px;
}
.wchip {
  display: flex;
  flex-direction: column;
  gap: 1px;
  width: 100%;
  text-align: left;
  padding: 0.3rem 0.45rem;
  border: none;
  border-left: 3px solid var(--c-primary);
  border-radius: 7px;
  margin-bottom: 3px;
  font-size: 0.72rem;
  line-height: 1.25;
  cursor: pointer;
  transition: transform var(--t-fast);
}
.wchip:hover {
  transform: translateY(-1px);
  filter: brightness(1.03);
}
.wchip__subj {
  font-weight: 700;
  color: var(--c-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.wchip__meta {
  color: var(--c-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.week-hint {
  padding: 0.8rem 1rem;
}

@media (max-width: 640px) {
  .evhead {
    display: none;
  }
  .evrow {
    grid-template-columns: 1fr auto;
    gap: 0.15rem 0.6rem;
  }
  .evrow__link {
    grid-row: 1 / span 3;
    grid-column: 2;
    align-self: center;
  }
}
</style>

<!-- CSS in ấn KHÔNG scoped: cần với tới khung app (sidebar/topbar) nằm ngoài component.
     Chỉ áp khi body có cờ .tsched-printing (bật lúc bấm In) → không ảnh hưởng trang khác. -->
<style>
@media print {
  body.tsched-printing .sidebar,
  body.tsched-printing .topbar,
  body.tsched-printing .backdrop,
  body.tsched-printing .tsched__noprint {
    display: none !important;
  }
  body.tsched-printing .content {
    padding: 0 !important;
  }
  body.tsched-printing .week {
    overflow: visible !important;
  }
  body.tsched-printing .wtable {
    min-width: 0 !important;
  }
  body.tsched-printing .card {
    border: none !important;
  }
}
</style>
