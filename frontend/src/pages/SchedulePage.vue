<script setup>
/**
 * Trang Lịch dạy (chỉ xem): buổi dạy của giáo viên ở các trường/lớp/tiết theo từng ngày.
 * Hai chế độ: Lịch THÁNG (bấm ngày → chi tiết) và Thời khóa biểu TUẦN (Thứ × Tiết).
 *
 * MỌI bộ lọc — kể cả ô tìm tự do — đều gọi lại server. Bản cũ lọc ô tìm ngay trên trình
 * duyệt, nghĩa là một tháng vài nghìn buổi vẫn phải tải hết về rồi giấu đi 99%; đó là trả
 * giá đường truyền cho thứ người dùng không nhìn thấy.
 * Với hơn trăm giáo viên thì thẻ <select> thường bắt cuộn tìm bằng mắt, nên ba dropdown đều
 * dùng SearchSelect (gõ được, bỏ dấu vẫn khớp).
 *
 * Ngày nghỉ được TÔ RIÊNG kèm tên kỳ nghỉ: ô trống không phân biệt được "trường đóng cửa"
 * với "quên xếp lịch", mà hai thứ đó cần hai hành động khác hẳn nhau.
 */
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { scheduleApi } from '@/api/schedules'
import { tietLabel, periodRows } from '@/utils/period'
import Pagination from '@/components/ui/Pagination.vue'
import SearchSelect from '@/components/ui/SearchSelect.vue'
import FilterBar from '@/components/ui/FilterBar.vue'

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
const filters = reactive({ teacherId: '', schoolId: '', classId: '', status: 'APPROVED' })
/** Ô tìm tự do — gửi thẳng cho server cùng khoảng ngày đang xem. */
const keyword = ref('')
const teachers = ref([])
const schools = ref([])
const classes = ref([])
const events = ref([])
const holidays = ref([])
const loading = ref(false)
const selectedIso = ref(TODAY_ISO)

const STATUSES = [
  { code: 'APPROVED', label: 'Đã duyệt' },
  { code: 'PENDING', label: 'Chờ xác nhận' },
  { code: 'CANCELLED', label: 'Đã hủy' },
]

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
    const [ev, hol] = await Promise.all([
      scheduleApi.list({
        from,
        to,
        teacherId: filters.teacherId,
        schoolId: filters.schoolId,
        classId: filters.classId,
        status: filters.status,
        keyword: keyword.value,
      }),
      scheduleApi.holidays({ from, to, schoolId: filters.schoolId }),
    ])
    events.value = ev.data
    holidays.value = hol.data
  } catch {
    events.value = []
    holidays.value = []
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

/** iso ngày → tên kỳ nghỉ phủ nó. Kỳ nghỉ lưu theo KHOẢNG nên phải trải ra từng ngày. */
const holidayByDate = computed(() => {
  const m = {}
  for (const h of holidays.value) {
    for (let d = new Date(h.fromDate); iso(d) <= h.toDate; d = addDays(d, 1)) {
      m[iso(d)] = h.name
    }
  }
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
      holiday: holidayByDate.value[k] ?? null,
    })
  }
  return cells
})

/* ── Lưới tuần ──
   CHỈ 6 cột T2→T7: trung tâm không xếp lịch Chủ nhật, vẽ thêm một cột luôn trống chỉ làm
   lưới hẹp lại. Lịch THÁNG bên dưới vẫn đủ 7 cột — đó là lịch thật, không phải TKB. */
const WEEK_DAY_COUNT = 6
const weekDays = computed(() => {
  const s = startOfWeek(anchor.value)
  const arr = []
  for (let i = 0; i < WEEK_DAY_COUNT; i++) {
    const d = addDays(s, i)
    arr.push({
      iso: iso(d),
      label: DOW_LABELS[i],
      dnum: d.getDate(),
      isToday: iso(d) === TODAY_ISO,
      holiday: holidayByDate.value[iso(d)] ?? null,
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
/** Dọn sạch MỌI bộ lọc (nút "Xóa lọc" của FilterBar gọi vào đây). */
function resetFilters() {
  filters.teacherId = ''
  filters.schoolId = ''
  filters.classId = ''
  filters.status = 'APPROVED'
  keyword.value = ''
  classes.value = []
  load()
}

/* ── Mang lịch ra khỏi màn hình ── */

/** In khoảng đang xem. CSS @media print bên dưới ẩn sidebar/toolbar, chỉ chừa cái lịch. */
function doPrint() {
  window.print()
}

/**
 * Xuất CSV khoảng ngày đang xem. Tự ghép chuỗi thay vì thêm thư viện: dữ liệu ở đây là bảng
 * phẳng, một hàm 15 dòng làm đủ việc.
 *
 * Ký tự BOM ở đầu file là bắt buộc — thiếu nó thì Excel đọc CSV theo bảng mã hệ thống và
 * mọi tên tiếng Việt thành ký tự lạ.
 */
const CSV_BOM = '﻿' // Excel cần BOM mới đọc đúng tiếng Việt trong CSV

function exportCsv() {
  const header = ['Ngày', 'Thứ', 'Tiết', 'Giờ', 'Trường', 'Lớp', 'Môn', 'Giáo viên', 'Trạng thái']
  const esc = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`
  const rows = [...events.value]
    .sort(
      (a, b) =>
        a.date.localeCompare(b.date) || (a.startTime || '').localeCompare(b.startTime || ''),
    )
    .map((e) =>
      [
        e.date,
        DOW_LABELS[(new Date(e.date).getDay() + 6) % 7],
        tietLabel(e.periodNumber, e.sessionType, e.indexInSession),
        `${(e.startTime || '').slice(0, 5)}-${(e.endTime || '').slice(0, 5)}`,
        e.schoolName,
        e.className,
        e.subjectName,
        e.teacherName,
        STATUSES.find((s) => s.code === e.status)?.label ?? e.status,
      ]
        .map(esc)
        .join(','),
    )
  const blob = new Blob([CSV_BOM + [header.map(esc).join(','), ...rows].join('\r\n')], {
    type: 'text/csv;charset=utf-8',
  })
  const { from, to } = rangeIso()
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `lich-day_${from}_${to}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
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

    <!-- Bộ lọc — cùng dáng với màn Phân công và Lịch nghỉ -->
    <FilterBar
      v-model="keyword"
      class="no-print"
      placeholder="Tên giáo viên, trường, lớp, môn…"
      aria-label="Tìm buổi dạy theo giáo viên, trường, lớp, môn"
      @apply="load"
      @clear="resetFilters"
    >
      <label class="field">
        <span>Giáo viên</span>
        <SearchSelect
          v-model="filters.teacherId"
          :options="teachers"
          placeholder="Tất cả"
          search-placeholder="Gõ tên giáo viên…"
          clearable
          @change="load"
        />
      </label>
      <label class="field">
        <span>Trường</span>
        <SearchSelect
          v-model="filters.schoolId"
          :options="schools"
          placeholder="Tất cả"
          search-placeholder="Gõ tên trường…"
          clearable
          @change="onSchoolChange"
        />
      </label>
      <label class="field">
        <span>Lớp</span>
        <SearchSelect
          v-model="filters.classId"
          :options="classes"
          :disabled="!classes.length"
          :placeholder="classes.length ? 'Tất cả' : 'Chọn trường'"
          search-placeholder="Gõ tên lớp…"
          clearable
          @change="load"
        />
      </label>
      <label class="field">
        <span>Trạng thái</span>
        <select v-model="filters.status" @change="load">
          <option v-for="s in STATUSES" :key="s.code" :value="s.code">{{ s.label }}</option>
        </select>
      </label>
    </FilterBar>

    <div class="toolbar no-print">
      <span class="count-info">{{ teacherCount }} giáo viên</span>
      <span class="spacer" />
      <button class="btn btn-outline btn-sm" @click="exportCsv">Xuất CSV</button>
      <button class="btn btn-outline btn-sm" @click="doPrint">In</button>
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
            :class="{
              out: !c.inMonth,
              today: c.isToday,
              sel: c.iso === selectedIso,
              off: !!c.holiday,
            }"
            :title="c.holiday || ''"
            @click="selectedIso = c.iso"
          >
            <span class="daycell__num">{{ c.day }}</span>
            <!-- Ngày nghỉ phải NÓI RA: ô trống không phân biệt được "trường đóng cửa"
                 với "quên xếp lịch", mà hai thứ đó cần hai hành động khác hẳn nhau. -->
            <span v-if="c.holiday" class="daycell__off">{{ c.holiday }}</span>
            <span v-else-if="c.schoolCount" class="daycell__pill">{{ c.schoolCount }} trường</span>
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
            <th v-for="d in weekDays" :key="d.iso" :class="{ today: d.isToday, off: !!d.holiday }">
              <div class="wday">{{ d.label }}</div>
              <div class="wdnum">{{ d.dnum }}</div>
              <div v-if="d.holiday" class="woff" :title="d.holiday">{{ d.holiday }}</div>
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

/* ── Ngày nghỉ ── */
.daycell.off {
  background: var(--c-bg);
}
.daycell__off {
  font-size: 0.66rem;
  font-weight: 600;
  line-height: 1.15;
  color: var(--c-text-muted);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
th.off .wday,
th.off .wdnum {
  color: var(--c-text-muted);
}
.woff {
  font-size: 0.62rem;
  font-weight: 600;
  color: var(--c-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ── In thời khóa biểu ──
   Trường thật vẫn cần bản giấy dán phòng hội đồng. Không thêm thư viện nào: ẩn phần
   điều khiển, bỏ nền tối, cho bảng bám khổ giấy. Chỉ dùng @page landscape cho lưới tuần
   vì nó rộng theo chiều ngang. */
@media print {
  .no-print,
  .viewtoggle {
    display: none !important;
  }
  .card {
    box-shadow: none;
    border: 1px solid #999;
  }
  .wchip,
  .daycell {
    break-inside: avoid;
  }
}
</style>
