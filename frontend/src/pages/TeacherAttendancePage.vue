<script setup>
/**
 * Trang "Bảng chấm công" của GIÁO VIÊN (tự phục vụ, READ-ONLY).
 * Chỉ xem chấm công của CHÍNH mình — dữ liệu do admin/kế toán sinh từ lịch dạy đã duyệt.
 * Mỗi dòng = 1 buổi/tiết, ghép sẵn trường/lớp/môn/tiết. Không sửa, không tự chấm.
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { attendanceApi } from '@/api/attendance'
import { tietLabel } from '@/utils/period'
import Pagination from '@/components/ui/Pagination.vue'

const STATUSES = [
  { code: 'PRESENT', label: 'Có mặt', cls: 'badge-green' },
  { code: 'LATE', label: 'Đi muộn', cls: 'badge-amber' },
  { code: 'LEAVE', label: 'Nghỉ phép', cls: 'badge-gray' },
  { code: 'ABSENT', label: 'Vắng', cls: 'badge-red' },
]
const statusMeta = (code) =>
  STATUSES.find((s) => s.code === code) ?? { label: code, cls: 'badge-gray' }

const today = new Date()
const firstOfMonth = new Date(today.getFullYear(), today.getMonth(), 1).toISOString().slice(0, 10)
const lastOfMonth = new Date(today.getFullYear(), today.getMonth() + 1, 0)
  .toISOString()
  .slice(0, 10)

const filter = reactive({ from: firstOfMonth, to: lastOfMonth, status: '' })
const rows = ref([])
const loading = ref(false)
const info = ref('')

/* ── Phân trang phía client ── */
const PAGE_SIZE = 10
const page = ref(0)
const totalPages = computed(() => Math.ceil(rows.value.length / PAGE_SIZE))
const pagedRows = computed(() => {
  const start = page.value * PAGE_SIZE
  return rows.value.slice(start, start + PAGE_SIZE)
})

/* ── Nhãn thứ trong tuần (CN, Th 2..Th 7) từ ngày làm việc ── */
const WEEKDAYS = ['CN', 'Th 2', 'Th 3', 'Th 4', 'Th 5', 'Th 6', 'Th 7']
function weekdayLabel(iso) {
  if (!iso) return ''
  const d = new Date(iso + 'T00:00:00')
  return WEEKDAYS[d.getDay()] ?? ''
}

async function load() {
  loading.value = true
  info.value = ''
  page.value = 0
  try {
    const { data } = await attendanceApi.mine({
      from: filter.from,
      to: filter.to,
      status: filter.status || undefined,
    })
    rows.value = data
  } catch (e) {
    rows.value = []
    info.value = e.response?.data?.message ?? 'Không tải được dữ liệu'
  } finally {
    loading.value = false
  }
}

onMounted(load)

/* ── Thẻ thống kê (tính trên toàn bộ kết quả đang lọc) ── */
const totalRows = computed(() => rows.value.length)
const workedCount = computed(
  () => rows.value.filter((r) => r.status === 'PRESENT' || r.status === 'LATE').length,
)
const lateCount = computed(() => rows.value.filter((r) => r.status === 'LATE').length)
const absentCount = computed(() => rows.value.filter((r) => r.status === 'ABSENT').length)
const totalHours = computed(() =>
  rows.value.reduce((sum, r) => sum + Number(r.hours || 0), 0).toFixed(2),
)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="title">Bảng chấm công</h2>
        <p class="subtitle">Chấm công của bạn, tổng hợp từ các buổi dạy đã duyệt</p>
      </div>
    </div>

    <!-- Thẻ tổng quan -->
    <div class="stats">
      <div class="stat card">
        <span class="stat-label">Tổng buổi</span>
        <span class="stat-value">{{ totalRows }}</span>
      </div>
      <div class="stat card">
        <span class="stat-label">Buổi có công</span>
        <span class="stat-value">{{ workedCount }}</span>
      </div>
      <div class="stat card">
        <span class="stat-label">Đi muộn</span>
        <span class="stat-value">{{ lateCount }}</span>
      </div>
      <div class="stat card">
        <span class="stat-label">Vắng</span>
        <span class="stat-value">{{ absentCount }}</span>
      </div>
      <div class="stat card">
        <span class="stat-label">Tổng giờ dạy</span>
        <span class="stat-value">{{ totalHours }}h</span>
      </div>
    </div>

    <!-- Bộ lọc -->
    <div class="toolbar">
      <label>Từ</label>
      <input type="date" v-model="filter.from" />
      <label>Đến</label>
      <input type="date" v-model="filter.to" />
      <label>Trạng thái</label>
      <select v-model="filter.status">
        <option value="">Tất cả</option>
        <option v-for="s in STATUSES" :key="s.code" :value="s.code">{{ s.label }}</option>
      </select>
      <button class="btn btn-outline btn-sm" @click="load">Lọc</button>
      <span v-if="info" class="info-text">{{ info }}</span>
    </div>

    <div class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>Ngày</th>
            <th>Thứ</th>
            <th>Buổi · Tiết</th>
            <th>Trường</th>
            <th>Lớp</th>
            <th>Môn</th>
            <th>Vào</th>
            <th>Ra</th>
            <th>Giờ</th>
            <th>Trạng thái</th>
            <th>Ghi chú</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="11" class="text-center text-muted">Đang tải…</td>
          </tr>
          <tr v-else-if="!rows.length">
            <td colspan="11" class="text-center text-muted">
              Chưa có dữ liệu chấm công cho kỳ này.
            </td>
          </tr>
          <tr v-for="r in pagedRows" :key="r.id">
            <td class="mono">{{ r.workDate }}</td>
            <td>{{ weekdayLabel(r.workDate) }}</td>
            <td>{{ tietLabel(r.periodNumber, r.sessionType) || '—' }}</td>
            <td>{{ r.schoolName ?? '—' }}</td>
            <td>{{ r.className ? 'Lớp ' + r.className : '—' }}</td>
            <td class="font-medium">{{ r.subjectName ?? '—' }}</td>
            <td class="mono">{{ r.checkIn ? r.checkIn.slice(0, 5) : '—' }}</td>
            <td class="mono">{{ r.checkOut ? r.checkOut.slice(0, 5) : '—' }}</td>
            <td class="mono">{{ Number(r.hours).toFixed(2) }}</td>
            <td>
              <span class="badge" :class="statusMeta(r.status).cls">{{
                statusMeta(r.status).label
              }}</span>
            </td>
            <td class="text-muted small">{{ r.note ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <Pagination v-model="page" :total-pages="totalPages" />
  </div>
</template>

<style scoped>
.subtitle {
  margin: 0.15rem 0 0;
  font-size: 0.9rem;
  color: var(--c-text-muted);
}
.stats {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 1rem;
  margin-bottom: 1.2rem;
}
.stat {
  padding: 1rem 1.2rem;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}
.stat-label {
  font-size: 0.8rem;
  color: var(--c-text-muted);
  font-weight: 600;
}
.stat-value {
  font-size: 1.5rem;
  font-weight: 800;
  color: var(--c-text);
}
.info-text {
  font-size: 0.82rem;
  color: var(--c-accent);
  margin-left: auto;
}
@media (max-width: 960px) {
  .stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 640px) {
  .stats {
    grid-template-columns: 1fr;
  }
}
</style>
