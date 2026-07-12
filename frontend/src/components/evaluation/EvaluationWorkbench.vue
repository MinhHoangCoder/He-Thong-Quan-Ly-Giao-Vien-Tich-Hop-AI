<script setup>
/**
 * Workbench dùng chung cho Staff / Admin / School:
 * KPI + filter + bảng + modal tạo/sửa + xóa.
 * portal: 'staff' | 'admin' | 'school' — chỉ khác nhãn & ẩn filter nguồn với school.
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { evaluationApi } from '@/api/evaluations'
import { formatDateTime } from '@/utils/format'
import StarRating from '@/components/evaluation/StarRating.vue'
import EvaluationFormModal from '@/components/evaluation/EvaluationFormModal.vue'
import StatCard from '@/components/ui/StatCard.vue'
import SvgIcon from '@/components/ui/SvgIcon.vue'

const props = defineProps({
  portal: { type: String, default: 'staff' }, // staff | admin | school
  title: { type: String, default: 'Đánh giá giáo viên' },
  subtitle: { type: String, default: 'Chấm điểm & nhận xét chất lượng giảng dạy' },
})

const isSchool = computed(() => props.portal === 'school')
const PAGE_SIZE = 10

const loading = ref(false)
const error = ref('')
const items = ref([])
const page = ref(0)
const totalPages = ref(0)
const totalItems = ref(0)

const stats = ref(null)
const teachers = ref([])
const periodPresets = ref([])

const filter = reactive({
  keyword: '',
  score: '',
  source: '',
  periodNote: '',
})

const modal = reactive({
  open: false,
  mode: 'create',
  initial: null,
  saving: false,
  error: '',
})
const deleteTarget = ref(null)
const detail = ref(null)

const avgLabel = computed(() => {
  const a = stats.value?.averageScore
  if (a == null) return '—'
  return `${Number(a).toFixed(1)}/5`
})

async function loadMeta() {
  try {
    const tasks = [evaluationApi.periodPresets(), evaluationApi.stats(buildStatsParams())]
    // teachers chỉ cần khi có quyền manage — school/staff đều có MANAGE
    tasks.push(evaluationApi.teachers().catch(() => ({ data: [] })))
    const [presetsRes, statsRes, teachersRes] = await Promise.all(tasks)
    periodPresets.value = presetsRes.data || []
    stats.value = statsRes.data
    teachers.value = teachersRes?.data || []
  } catch (e) {
    console.error(e)
  }
}

function buildListParams() {
  const params = { page: page.value, size: PAGE_SIZE }
  if (filter.keyword) params.keyword = filter.keyword
  if (filter.score) params.score = Number(filter.score)
  if (filter.periodNote) params.periodNote = filter.periodNote
  if (!isSchool.value && filter.source) params.source = filter.source
  return params
}

function buildStatsParams() {
  const params = {}
  if (!isSchool.value && filter.source) params.source = filter.source
  return params
}

async function loadList() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await evaluationApi.list(buildListParams())
    items.value = data.content || []
    totalPages.value = data.totalPages || 0
    totalItems.value = data.totalElements || 0
  } catch (e) {
    console.error(e)
    error.value = e.response?.data?.message || 'Không tải được danh sách đánh giá.'
  } finally {
    loading.value = false
  }
}

async function refreshStats() {
  try {
    const { data } = await evaluationApi.stats(buildStatsParams())
    stats.value = data
  } catch {
    /* ignore */
  }
}

function applyFilter() {
  page.value = 0
  loadList()
  refreshStats()
}

function clearFilter() {
  filter.keyword = ''
  filter.score = ''
  filter.source = ''
  filter.periodNote = ''
  applyFilter()
}

function goPage(p) {
  if (p < 0 || p >= totalPages.value) return
  page.value = p
  loadList()
}

function openCreate() {
  Object.assign(modal, { open: true, mode: 'create', initial: null, saving: false, error: '' })
}

function openEdit(row) {
  Object.assign(modal, { open: true, mode: 'edit', initial: { ...row }, saving: false, error: '' })
}

async function saveModal(payload) {
  modal.saving = true
  modal.error = ''
  try {
    if (modal.mode === 'edit' && modal.initial?.id) {
      await evaluationApi.update(modal.initial.id, payload)
    } else {
      await evaluationApi.create(payload)
    }
    modal.open = false
    await loadList()
    await refreshStats()
  } catch (e) {
    modal.error = e.response?.data?.message || 'Lưu đánh giá thất bại'
  } finally {
    modal.saving = false
  }
}

async function doDelete() {
  if (!deleteTarget.value) return
  try {
    await evaluationApi.remove(deleteTarget.value.id)
    deleteTarget.value = null
    await loadList()
    await refreshStats()
  } catch (e) {
    error.value = e.response?.data?.message || 'Xóa thất bại'
    deleteTarget.value = null
  }
}

function sourceLabel(row) {
  if (row.source === 'CENTER') return 'Trung tâm'
  return row.schoolName || 'Trường'
}

function sourceClass(row) {
  return row.source === 'CENTER' ? 'badge--center' : 'badge--school'
}

onMounted(async () => {
  await loadMeta()
  await loadList()
})
</script>

<template>
  <div class="page">
    <div class="page__head">
      <div>
        <h1 class="page__title">{{ title }}</h1>
        <p class="page__sub">{{ subtitle }}</p>
      </div>
      <button class="btn" type="button" @click="openCreate">
        <SvgIcon name="plus" :size="16" /> Tạo đánh giá
      </button>
    </div>

    <!-- KPI -->
    <section v-if="stats" class="stat-grid">
      <StatCard icon="evaluation" label="Tổng lượt đánh giá" :value="stats.totalCount" color="#f97316" />
      <StatCard icon="evaluation" label="Điểm trung bình" :value="avgLabel" color="#2563eb" />
      <StatCard
        icon="evaluation"
        label="Điểm cao (≥4)"
        :value="stats.highScoreCount"
        :hint="stats.totalCount ? `${Math.round((stats.highScoreCount / stats.totalCount) * 100)}% tổng lượt` : ''"
        color="#22c55e"
      />
      <StatCard
        icon="teacher"
        label="GV đã được đánh giá"
        :value="stats.teacherCountEvaluated"
        color="#0ea5e9"
      />
    </section>

    <!-- Phân bố sao -->
    <div v-if="stats && stats.totalCount" class="dist card-soft">
      <span class="dist__label">Phân bố điểm</span>
      <div v-for="n in [5, 4, 3, 2, 1]" :key="n" class="dist__row">
        <span class="dist__star">{{ n }}★</span>
        <div class="dist__bar">
          <div
            class="dist__fill"
            :style="{
              width:
                stats.totalCount > 0
                  ? Math.round((stats['score' + n] / stats.totalCount) * 100) + '%'
                  : '0%',
            }"
          />
        </div>
        <span class="dist__n">{{ stats['score' + n] || 0 }}</span>
      </div>
    </div>

    <!-- Filter -->
    <div class="filter-bar">
      <label class="field field--wide">
        <span>Tìm kiếm</span>
        <input
          v-model="filter.keyword"
          placeholder="Tên GV, nhận xét, kỳ…"
          @keyup.enter="applyFilter"
        />
      </label>
      <label class="field">
        <span>Điểm</span>
        <select v-model="filter.score" @change="applyFilter">
          <option value="">Tất cả</option>
          <option v-for="n in 5" :key="n" :value="n">{{ n }} sao</option>
        </select>
      </label>
      <label v-if="!isSchool" class="field">
        <span>Nguồn</span>
        <select v-model="filter.source" @change="applyFilter">
          <option value="">Tất cả</option>
          <option value="SCHOOL">Trường</option>
          <option value="CENTER">Trung tâm</option>
        </select>
      </label>
      <label class="field">
        <span>Kỳ</span>
        <input
          v-model="filter.periodNote"
          list="filter-periods"
          placeholder="HK2…"
          @keyup.enter="applyFilter"
        />
        <datalist id="filter-periods">
          <option v-for="p in periodPresets" :key="p" :value="p" />
        </datalist>
      </label>
      <div class="filter-actions">
        <button class="btn" type="button" @click="applyFilter">Lọc</button>
        <button class="btn btn--ghost" type="button" @click="clearFilter">Xóa lọc</button>
      </div>
    </div>

    <p v-if="error" class="msg msg--error">{{ error }}</p>
    <p v-if="!loading" class="total">
      Tổng cộng <strong>{{ totalItems }}</strong> lượt đánh giá
    </p>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Giáo viên</th>
            <th>Điểm</th>
            <th>Kỳ</th>
            <th v-if="!isSchool">Nguồn</th>
            <th>Người đánh giá</th>
            <th>Nhận xét</th>
            <th>Thời gian</th>
            <th width="110">Thao tác</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td :colspan="isSchool ? 7 : 8" class="empty">Đang tải…</td>
          </tr>
          <tr v-else-if="items.length === 0">
            <td :colspan="isSchool ? 7 : 8" class="empty">Chưa có đánh giá nào</td>
          </tr>
          <tr v-for="row in items" :key="row.id">
            <td class="col-title">
              <div class="title-text">{{ row.teacherName }}</div>
            </td>
            <td>
              <StarRating :model-value="row.score" :size="14" />
              <span class="score-inline">{{ row.score }}</span>
            </td>
            <td>{{ row.periodNote || '—' }}</td>
            <td v-if="!isSchool">
              <span class="badge" :class="sourceClass(row)">{{ sourceLabel(row) }}</span>
            </td>
            <td>{{ row.evaluatorName }}</td>
            <td class="col-comment">
              <button type="button" class="linkish" @click="detail = row">
                {{ row.comment ? (row.comment.length > 60 ? row.comment.slice(0, 60) + '…' : row.comment) : '—' }}
              </button>
            </td>
            <td class="muted">{{ formatDateTime(row.createdAt) }}</td>
            <td class="col-actions">
              <button
                v-if="row.canEdit"
                type="button"
                class="act-btn"
                title="Sửa"
                @click="openEdit(row)"
              >
                ✏️
              </button>
              <button
                v-if="row.canDelete"
                type="button"
                class="act-btn act-btn--del"
                title="Xóa"
                @click="deleteTarget = row"
              >
                🗑️
              </button>
              <button type="button" class="act-btn" title="Xem" @click="detail = row">👁️</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="totalPages > 1" class="pagination">
      <button class="pg-btn" type="button" :disabled="page === 0" @click="goPage(page - 1)">‹</button>
      <span class="pg-info">Trang {{ page + 1 }} / {{ totalPages }}</span>
      <button
        class="pg-btn"
        type="button"
        :disabled="page >= totalPages - 1"
        @click="goPage(page + 1)"
      >
        ›
      </button>
    </div>

    <EvaluationFormModal
      :open="modal.open"
      :mode="modal.mode"
      :teachers="teachers"
      :period-presets="periodPresets"
      :initial="modal.initial"
      :saving="modal.saving"
      :error="modal.error"
      :lock-teacher="isSchool"
      @close="modal.open = false"
      @save="saveModal"
    />

    <!-- Detail drawer -->
    <div v-if="detail" class="modal-backdrop" @click.self="detail = null">
      <div class="modal detail-modal">
        <div class="modal__head">
          <h2 class="modal__title">Chi tiết đánh giá</h2>
          <button type="button" class="modal__x" @click="detail = null">×</button>
        </div>
        <div class="modal__body">
          <p><strong>Giáo viên:</strong> {{ detail.teacherName }}</p>
          <p>
            <strong>Điểm:</strong>
            <StarRating :model-value="detail.score" /> {{ detail.score }}/5
          </p>
          <p><strong>Kỳ:</strong> {{ detail.periodNote || '—' }}</p>
          <p><strong>Nguồn:</strong> {{ sourceLabel(detail) }}</p>
          <p><strong>Người đánh giá:</strong> {{ detail.evaluatorName }}</p>
          <p><strong>Thời gian:</strong> {{ formatDateTime(detail.createdAt) }}</p>
          <p class="detail-comment"><strong>Nhận xét:</strong><br />{{ detail.comment || '(Không có)' }}</p>
        </div>
        <div class="modal__foot">
          <button
            v-if="detail.canEdit"
            type="button"
            class="btn btn--ghost"
            @click="
              openEdit(detail);
              detail = null
            "
          >
            Sửa
          </button>
          <button type="button" class="btn" @click="detail = null">Đóng</button>
        </div>
      </div>
    </div>

    <!-- Delete confirm -->
    <div v-if="deleteTarget" class="modal-backdrop" @click.self="deleteTarget = null">
      <div class="modal confirm-modal">
        <div class="modal__head">
          <h2 class="modal__title">Xóa đánh giá?</h2>
          <button type="button" class="modal__x" @click="deleteTarget = null">×</button>
        </div>
        <div class="modal__body">
          <p>
            Xóa mềm đánh giá của <strong>{{ deleteTarget.teacherName }}</strong>
            ({{ deleteTarget.score }}/5)? Dữ liệu vẫn lưu trong hệ thống để audit.
          </p>
        </div>
        <div class="modal__foot">
          <button type="button" class="btn btn--ghost" @click="deleteTarget = null">Hủy</button>
          <button type="button" class="btn btn--danger" @click="doDelete">Xóa</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.25rem;
}
.page__title {
  margin: 0;
  font-size: 1.45rem;
  color: var(--c-text);
}
.page__sub {
  margin: 0.25rem 0 0;
  color: var(--c-text-muted);
  font-size: 0.92rem;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0.85rem;
  margin-bottom: 1rem;
}
.card-soft {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 12px;
  padding: 0.9rem 1rem;
  margin-bottom: 1rem;
}
.dist__label {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--c-text);
  display: block;
  margin-bottom: 0.5rem;
}
.dist__row {
  display: grid;
  grid-template-columns: 36px 1fr 32px;
  gap: 0.5rem;
  align-items: center;
  margin-bottom: 0.3rem;
}
.dist__star {
  font-size: 0.8rem;
  color: var(--c-text-muted);
}
.dist__bar {
  height: 8px;
  background: var(--c-surface-2);
  border-radius: 999px;
  overflow: hidden;
}
.dist__fill {
  height: 100%;
  background: linear-gradient(90deg, #fbbf24, #f59e0b);
  border-radius: 999px;
  min-width: 0;
  transition: width 0.35s ease;
}
.dist__n {
  font-size: 0.8rem;
  color: var(--c-text-muted);
  text-align: right;
}
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: flex-end;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 12px;
  padding: 0.85rem 1rem;
  margin-bottom: 0.85rem;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  font-size: 0.82rem;
  color: var(--c-text-muted);
  min-width: 120px;
}
.field--wide {
  flex: 1;
  min-width: 180px;
}
.field input,
.field select {
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  padding: 0.45rem 0.6rem;
  background: var(--c-surface);
  color: var(--c-text);
  font: inherit;
}
.filter-actions {
  display: flex;
  gap: 0.4rem;
}
.btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  border: 0;
  border-radius: 8px;
  padding: 0.5rem 0.95rem;
  background: var(--grad-primary);
  color: #fff;
  font-weight: 600;
  cursor: pointer;
  font: inherit;
  font-size: 0.9rem;
}
.btn--ghost {
  background: transparent;
  color: var(--c-text-muted);
  border: 1px solid var(--c-border);
}
.btn--danger {
  background: var(--c-danger, #ef4444);
}
.total {
  color: var(--c-text-muted);
  font-size: 0.9rem;
  margin: 0 0 0.5rem;
}
.msg--error {
  color: #b91c1c;
  background: #fef2f2;
  padding: 0.55rem 0.75rem;
  border-radius: 8px;
}
.table-wrap {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 12px;
  overflow: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}
th,
td {
  padding: 0.7rem 0.85rem;
  text-align: left;
  border-bottom: 1px solid var(--c-border-soft);
  color: var(--c-text);
}
th {
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: var(--c-text-muted);
  background: var(--c-surface-2);
}
.empty {
  text-align: center;
  color: var(--c-text-muted);
  padding: 2rem !important;
}
.title-text {
  font-weight: 600;
}
.score-inline {
  margin-left: 0.35rem;
  font-weight: 600;
  color: var(--c-primary);
  font-size: 0.85rem;
}
.col-comment {
  max-width: 220px;
}
.linkish {
  border: 0;
  background: none;
  color: var(--c-accent, #2563eb);
  cursor: pointer;
  text-align: left;
  font: inherit;
  padding: 0;
}
.muted {
  color: var(--c-text-muted);
  font-size: 0.85rem;
  white-space: nowrap;
}
.badge {
  display: inline-block;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
}
.badge--center {
  background: #eff6ff;
  color: #1d4ed8;
}
.badge--school {
  background: #fff7ed;
  color: #c2410c;
}
.col-actions {
  white-space: nowrap;
}
.act-btn {
  border: 0;
  background: var(--c-surface-2);
  border-radius: 6px;
  padding: 0.25rem 0.4rem;
  cursor: pointer;
  margin-right: 0.2rem;
}
.act-btn--del:hover {
  background: #fee2e2;
}
.pagination {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.85rem;
  justify-content: center;
}
.pg-btn {
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  border-radius: 6px;
  padding: 0.3rem 0.65rem;
  cursor: pointer;
  color: var(--c-text);
}
.pg-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.pg-info {
  font-size: 0.88rem;
  color: var(--c-text-muted);
}
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 1rem;
}
.modal {
  width: min(480px, 100%);
  background: var(--c-surface);
  border-radius: 14px;
  border: 1px solid var(--c-border);
  box-shadow: var(--a-shadow-lg);
}
.modal__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.9rem 1.1rem;
  border-bottom: 1px solid var(--c-border-soft);
}
.modal__title {
  margin: 0;
  font-size: 1.05rem;
}
.modal__x {
  border: 0;
  background: none;
  font-size: 1.4rem;
  cursor: pointer;
  color: var(--c-text-muted);
}
.modal__body {
  padding: 1rem 1.1rem;
  color: var(--c-text);
  line-height: 1.5;
}
.modal__foot {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  padding: 0.85rem 1.1rem;
  border-top: 1px solid var(--c-border-soft);
}
.detail-comment {
  margin-top: 0.5rem;
  white-space: pre-wrap;
}
</style>
