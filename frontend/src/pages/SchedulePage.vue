<script setup>
/**
 * Trang Lịch dạy (chỉ xem): xem buổi dạy ĐÃ DUYỆT của giáo viên ở các trường/lớp/tiết
 * theo từng ngày. Hai chế độ: Lịch THÁNG (bấm ngày → chi tiết) và Thời khóa biểu TUẦN
 * (Thứ × Tiết). Lọc linh hoạt theo Giáo viên / Trường / Lớp.
 */
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { scheduleApi } from '@/api/schedules'
import { tietLabel, periodRows } from '@/utils/period'
import Pagination from '@/components/ui/Pagination.vue'

const DOW_LABELS = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'] // index 0 = Thứ 2, cuối là Chủ nhật

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
// Chế độ ban đầu: cho phép mở thẳng Thời khóa biểu TUẦN qua query ?view=week (VD: từ Dashboard).
const view = ref(route.query.view === 'week' ? 'week' : 'month') // 'month' | 'week'
const anchor = ref(new Date())
const filters = reactive({ teacherId: '', schoolId: '', classId: '' })
const teachers = ref([])
const schools = ref([])
const classes = ref([])
const events = ref([])
const loading = ref(false)
const selectedIso = ref(TODAY_ISO)

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
  try {
    const { from, to } = rangeIso()
    const { data } = await scheduleApi.list({
      from,
      to,
      teacherId: filters.teacherId,
      schoolId: filters.schoolId,
      classId: filters.classId,
    })
    events.value = data
  } catch {
    events.value = []
  } finally {
    loading.value = false
  }
}

/* ── Gom sự kiện theo ngày ── */
const eventsByDate = computed(() => {
  const m = {}
  for (const e of events.value) (m[e.date] ??= []).push(e)
  for (const k in m) m[k].sort((a, b) => (a.startTime || '').localeCompare(b.startTime || ''))
  return m
})

// Số TRƯỜNG khác nhau có buổi dạy trong một danh sách buổi (theo yêu cầu: đếm theo trường).
const countSchools = (list) => new Set(list.map((e) => e.schoolId ?? e.schoolName)).size

// Tổng số GIÁO VIÊN khác nhau trong khoảng đang xem (thay cho tổng số buổi ở thanh công cụ).
const teacherCount = computed(
  () => new Set(events.value.map((e) => e.teacherId ?? e.teacherName)).size,
)

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
      schoolCount: countSchools(dayEvents),
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
// Dòng của lưới tuần bám khung tiết THẬT trong dữ liệu (tiểu học 10 tiết, THCS 9).
const weekPeriodRows = computed(() => periodRows(events.value))

/* ── Chi tiết ngày (chế độ tháng) ── */
const selectedEvents = computed(() => eventsByDate.value[selectedIso.value] || [])
// Danh sách phẳng đã sắp xếp: theo trường (A→Z), rồi tiết tăng dần, rồi giờ bắt đầu.
const selectedRows = computed(() =>
  [...selectedEvents.value].sort(
    (a, b) =>
      (a.schoolName || '').localeCompare(b.schoolName || '', 'vi') ||
      (a.periodNumber ?? 0) - (b.periodNumber ?? 0) ||
      (a.startTime || '').localeCompare(b.startTime || ''),
  ),
)
const selectedSchoolCount = computed(() => countSchools(selectedEvents.value))
const selectedLabel = computed(() => {
  const [y, m, d] = selectedIso.value.split('-')
  return `${d}/${m}/${y}`
})

/* ── Phân trang chi tiết ngày: 10 DÒNG/trang; trong mỗi trang vẫn gom theo trường ── */
const ROWS_PER_PAGE = 10
const detailPage = ref(0)
const detailTotalPages = computed(() => Math.ceil(selectedRows.value.length / ROWS_PER_PAGE))
// Lấy đúng 10 dòng của trang hiện tại rồi gom lại theo trường (các dòng cùng trường đã liền nhau).
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
// Đổi ngày đang chọn hoặc dữ liệu đổi → quay về trang 1 của phần chi tiết.
watch([selectedIso, selectedRows], () => {
  detailPage.value = 0
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
  load()
}
function goToday() {
  anchor.value = new Date()
  selectedIso.value = TODAY_ISO
  load()
}
function setView(v) {
  view.value = v
  load()
}

/* ── Bộ lọc ── */
async function loadFilters() {
  try {
    const { data } = await scheduleApi.filters()
    teachers.value = data.teachers
    schools.value = data.schools
  } catch {
    /* ignore */
  }
}
async function onSchoolChange() {
  filters.classId = ''
  classes.value = []
  if (filters.schoolId) {
    try {
      const { data } = await scheduleApi.classes(filters.schoolId)
      classes.value = data
    } catch {
      /* ignore */
    }
  }
  load()
}
function resetFilters() {
  filters.teacherId = ''
  filters.schoolId = ''
  filters.classId = ''
  classes.value = []
  load()
}

onMounted(() => {
  loadFilters()
  load()
})
</script>

<template>
  <div class="page sched">
    <div class="page-head">
      <div>
        <h2 class="title">Lịch dạy</h2>
      </div>
      <div class="viewtoggle">
        <button :class="{ on: view === 'month' }" @click="setView('month')">Tháng</button>
        <button :class="{ on: view === 'week' }" @click="setView('week')">Tuần</button>
      </div>
    </div>

    <!-- Bộ lọc -->
    <div class="toolbar">
      <label>Giáo viên</label>
      <select v-model="filters.teacherId" @change="load">
        <option value="">Tất cả</option>
        <option v-for="t in teachers" :key="t.id" :value="t.id">{{ t.name }}</option>
      </select>
      <label>Trường</label>
      <select v-model="filters.schoolId" @change="onSchoolChange">
        <option value="">Tất cả</option>
        <option v-for="s in schools" :key="s.id" :value="s.id">{{ s.name }}</option>
      </select>
      <label>Lớp</label>
      <select v-model="filters.classId" :disabled="!classes.length" @change="load">
        <option value="">{{ classes.length ? 'Tất cả' : 'Chọn trường' }}</option>
        <option v-for="c in classes" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <button class="btn btn-outline btn-sm" @click="resetFilters">Xóa lọc</button>
      <span class="spacer" />
      <span class="count-info">{{ teacherCount }} giáo viên</span>
    </div>

    <!-- Thanh điều hướng -->
    <div class="calbar card">
      <button class="navbtn" @click="go(-1)">‹</button>
      <span class="calbar__title">{{ title }}</span>
      <button class="navbtn" @click="go(1)">›</button>
      <button class="btn btn-outline btn-sm today-btn" @click="goToday">Hôm nay</button>
      <span v-if="loading" class="loading-dot">Đang tải…</span>
    </div>

    <!-- ================= LỊCH THÁNG ================= -->
    <div v-if="view === 'month'" class="month-wrap">
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
            @click="selectedIso = c.iso"
          >
            <span class="daycell__num">{{ c.day }}</span>
            <span v-if="c.schoolCount" class="daycell__pill">{{ c.schoolCount }} trường</span>
          </button>
        </div>
      </div>

      <!-- Chi tiết ngày đã chọn — GOM THEO TRƯỜNG -->
      <div class="detail card">
        <h3 class="detail__title">
          Ngày {{ selectedLabel }} —
          <strong>{{ selectedSchoolCount }} trường</strong>
          <span class="detail__sub">&nbsp;· {{ selectedEvents.length }} buổi</span>
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
                <span>Giáo viên</span>
                <span>Lớp</span>
              </li>
              <li
                v-for="e in g.events"
                :key="e.id"
                class="evrow"
                :class="e.sessionType === 'AFTERNOON' ? 'pm' : 'am'"
              >
                <span class="evrow__period">{{
                  tietLabel(e.periodNumber, e.sessionType, e.indexInSession)
                }}</span>
                <span class="evrow__subject">{{ e.subjectName }}</span>
                <span class="evrow__teacher">{{ e.teacherName }}</span>
                <span class="evrow__class">{{ e.className }}</span>
              </li>
            </ul>
          </div>
        </div>

        <Pagination v-model="detailPage" :total-pages="detailTotalPages" />
      </div>
    </div>

    <!-- ================= THỜI KHÓA BIỂU TUẦN ================= -->
    <div v-else class="week card">
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
          <tr
            v-for="row in weekPeriodRows"
            :key="row.periodNumber"
            :class="{ 'week-sep': row.firstOfSession }"
          >
            <td class="wperiod">{{ row.label }}</td>
            <td v-for="d in weekDays" :key="d.iso" class="wcell">
              <div
                v-for="e in cellEvents(row.periodNumber, d.iso)"
                :key="e.id"
                class="wchip"
                :class="e.sessionType === 'AFTERNOON' ? 'pm' : 'am'"
                :title="`${tietLabel(e.periodNumber, e.sessionType, e.indexInSession)} · ${e.subjectName} · ${e.schoolName} ${e.className} · ${e.teacherName}`"
              >
                <span class="wchip__subj">{{ e.subjectName }}</span>
                <span class="wchip__meta">{{ e.className }} · {{ e.teacherName }}</span>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="week-hint text-muted small">
        Mẹo: chọn 1 Giáo viên hoặc 1 Lớp ở bộ lọc để xem thời khóa biểu gọn, không bị chồng buổi.
      </p>
    </div>
  </div>
</template>

<style scoped>
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
.spacer {
  flex: 1;
}
.count-info {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--c-primary-dark);
}

/* Thanh điều hướng lịch */
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
.evlist {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}
.detail__sub {
  font-weight: 600;
  color: var(--c-text-muted);
  font-size: 0.9rem;
}

/* Nhóm theo trường */
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
.schoolgroup .evlist {
  padding: 0.5rem;
}
/* Hàng tiêu đề cột, canh đúng 4 cột như .evrow (viền trái trong suốt để thẳng hàng) */
.evhead {
  display: grid;
  grid-template-columns: 108px 1.4fr 1fr 0.7fr;
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
.evhead span:last-child {
  text-align: right;
}
.evrow {
  display: grid;
  grid-template-columns: 108px 1.4fr 1fr 0.7fr;
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
.evrow__teacher {
  color: var(--c-text);
}
.evrow__class {
  color: var(--c-text-muted);
  font-size: 0.82rem;
  text-align: right;
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
/* Vạch ngăn giữa buổi Sáng và buổi Chiều trong thời khóa biểu tuần */
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
  padding: 0.3rem 0.45rem;
  border-radius: 7px;
  margin-bottom: 3px;
  background: rgba(249, 115, 22, 0.09);
  border-left: 3px solid var(--c-primary);
  font-size: 0.72rem;
  line-height: 1.25;
}
.wchip.pm {
  background: rgba(37, 99, 235, 0.1);
  border-left-color: var(--c-accent);
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
.wchip__time {
  color: var(--c-primary-dark);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.week-hint {
  padding: 0.6rem 1rem;
}

@media (max-width: 640px) {
  .evrow {
    grid-template-columns: 1fr;
    gap: 0.15rem;
  }
  .evhead {
    display: none;
  }
}
</style>
