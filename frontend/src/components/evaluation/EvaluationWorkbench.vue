<script setup>
/**
 * Workbench dùng chung cho Staff / Admin / School:
 * KPI + filter + bảng + modal tạo/sửa + xóa.
 * portal: 'staff' | 'admin' | 'school' — khác nhãn / scope school.
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { evaluationApi } from '@/api/evaluations'
import { useLatestRequest } from '@/composables/useLatestRequest'
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
const suggestedPeriod = ref('')
/** Dropdown lọc trường (dùng cho panel "GV cần đánh giá" + modal tạo). */
const schools = ref([])
const schoolScoped = ref(false)

const filter = reactive({
  keyword: '',
  score: '',
  periodNote: '',
})

/**
 * Bộ lọc ĐÃ ÁP DỤNG (bấm Lọc/Enter) — bảng và phân trang chỉ dùng bản này.
 * Tách khỏi `filter` (đang gõ dở) để chuyển trang không lôi keyword chưa xác nhận vào request.
 */
const applied = reactive({
  keyword: '',
  score: '',
  periodNote: '',
})

const route = useRoute()
const router = useRouter()

let unevalSearchTimer = null

const modal = reactive({
  open: false,
  mode: 'create',
  initial: null,
  prefill: null,
  saving: false,
  error: '',
})
const deleteTarget = ref(null)
const detail = ref(null)

/** Panel GV chưa đánh giá trong kỳ */
const uneval = reactive({
  open: false,
  loading: false,
  keyword: '',
  schoolId: '', // '' = tất cả trường
  data: null, // UnevaluatedTeachersResponse
})

/** Chỉ hiện nút chọn trường khi thực sự có nhiều trường để chọn. */
const canPickSchool = computed(() => !schoolScoped.value && schools.value.length > 1)

/** Tên trường đang lọc — hiển thị trên chip trạng thái. */
const unevalSchoolName = computed(() => {
  if (!uneval.schoolId) return ''
  return schools.value.find((s) => String(s.id) === String(uneval.schoolId))?.name || ''
})

const avgLabel = computed(() => {
  const a = stats.value?.averageScore
  if (a == null) return '—'
  return `${Number(a).toFixed(1)}/5`
})

/** Kỳ dùng cho “chưa đánh giá”: filter kỳ nếu chọn, không thì gợi ý hiện tại. */
const coveragePeriod = computed(() => filter.periodNote || suggestedPeriod.value || '')

const unevalCount = computed(() => uneval.data?.unevaluatedCount ?? null)

/** % đã chấm trong kỳ (0–100). */
const coveragePct = computed(() => {
  const d = uneval.data
  if (!d || !d.totalTeachers) return 0
  return Math.round((d.evaluatedCount / d.totalTeachers) * 100)
})

const coverageDone = computed(() => uneval.data != null && uneval.data.unevaluatedCount === 0)

const coverageTitle = computed(() => {
  if (unevalCount.value == null) return 'Đang tải coverage kỳ…'
  if (coverageDone.value) return 'Đã chấm đủ mọi giáo viên trong kỳ'
  if (unevalCount.value === 1) return 'Còn 1 giáo viên chưa được đánh giá'
  return `Còn ${unevalCount.value} giáo viên chưa được đánh giá`
})

/** Nhãn cạnh con số lớn — không lặp lại số đã hiển thị. */
const coverageHeadline = computed(() => {
  if (unevalCount.value == null) return 'Đang tính tiến độ kỳ…'
  if (coverageDone.value) return 'Đã chấm đủ mọi giáo viên trong kỳ'
  return 'giáo viên chưa được đánh giá'
})

/** Chưa có filter + chưa có data (và KHÔNG phải đang lỗi API) → empty “lần đầu”. */
const isPristineEmpty = computed(() => {
  return (
    !loading.value &&
    !error.value &&
    totalItems.value === 0 &&
    !applied.keyword &&
    !applied.score &&
    !applied.periodNote
  )
})

async function loadMeta() {
  try {
    const tasks = [
      evaluationApi.periodMeta(),
      evaluationApi.stats(),
      evaluationApi.filterMeta().catch(() => ({ data: null })),
    ]
    const [metaRes, statsRes, filterRes] = await Promise.all(tasks)
    periodPresets.value = metaRes.data?.presets || []
    suggestedPeriod.value = metaRes.data?.suggested || ''
    stats.value = statsRes.data
    schools.value = filterRes.data?.schools || []
    schoolScoped.value = !!filterRes.data?.schoolScoped
    // preload 1 trang GV (form tự load đầy đủ khi mở)
    const period = filter.periodNote || metaRes.data?.suggested
    const { data: teacherData } = await evaluationApi
      .teachers({ periodNote: period, page: 0, size: 20 })
      .catch(() => ({ data: { content: [] } }))
    teachers.value = teacherData?.content || teacherData || []
    await loadUnevaluated()
  } catch (e) {
    console.error(e)
    try {
      const { data } = await evaluationApi.periodPresets()
      periodPresets.value = data || []
    } catch {
      /* ignore */
    }
  }
}

/** Bỏ response đến muộn (gõ nhanh → request cũ có thể về sau request mới). */
let unevalReqSeq = 0

async function loadUnevaluated() {
  const seq = ++unevalReqSeq
  uneval.loading = true
  try {
    const params = {
      periodNote: coveragePeriod.value || undefined,
      keyword: uneval.keyword || undefined,
      schoolId: uneval.schoolId ? Number(uneval.schoolId) : undefined,
    }
    const { data } = await evaluationApi.unevaluatedTeachers(params)
    if (seq !== unevalReqSeq) return
    uneval.data = data
  } catch (e) {
    if (seq !== unevalReqSeq) return
    console.error(e)
    uneval.data = null
  } finally {
    if (seq === unevalReqSeq) uneval.loading = false
  }
}

function openUnevaluatedPanel() {
  uneval.open = true
  uneval.keyword = ''
  loadUnevaluated()
}

/**
 * Đóng panel: từ khóa chỉ có nghĩa trong panel — phải bỏ đi và tải lại,
 * nếu không card coverage ngoài dashboard sẽ hiển thị số liệu đã bị lọc theo tên.
 * (Lọc trường thì giữ — card có hiện tên trường đang lọc.)
 */
function closeUnevalPanel() {
  uneval.open = false
  clearTimeout(unevalSearchTimer)
  if (uneval.keyword) {
    uneval.keyword = ''
    loadUnevaluated()
  }
}

/** Tìm ngay khi gõ từng chữ (debounce ngắn). */
function onUnevalKeywordInput() {
  clearTimeout(unevalSearchTimer)
  unevalSearchTimer = setTimeout(() => {
    if (uneval.open) loadUnevaluated()
  }, 150)
}

/** Đổi trường lọc trong panel → tải lại ngay. */
function onUnevalSchoolChange() {
  loadUnevaluated()
}

function clearUnevalFilters() {
  uneval.keyword = ''
  uneval.schoolId = ''
  loadUnevaluated()
}

function evaluateTeacher(t) {
  closeUnevalPanel()
  Object.assign(modal, {
    open: true,
    mode: 'create',
    initial: null,
    prefill: {
      teacherId: t.id,
      teacherName: t.name,
      periodNote: uneval.data?.periodNote || coveragePeriod.value || suggestedPeriod.value,
    },
    saving: false,
    error: '',
  })
}

function buildListParams() {
  const params = { page: page.value, size: PAGE_SIZE }
  if (applied.keyword) params.keyword = applied.keyword
  if (applied.score) params.score = Number(applied.score)
  if (applied.periodNote) params.periodNote = applied.periodNote
  return params
}

/** Chỉ nhận kết quả của lượt gọi mới nhất — đổi lọc/trang nhanh không bị dữ liệu cũ đè. */
const latestList = useLatestRequest()

async function loadList() {
  loading.value = true
  error.value = ''
  await latestList(
    () => evaluationApi.list(buildListParams()),
    ({ data }) => {
      items.value = data.content || []
      totalPages.value = data.totalPages || 0
      totalItems.value = data.totalElements || 0
      loading.value = false
    },
    (e) => {
      console.error(e)
      error.value = e.response?.data?.message || 'Không tải được danh sách đánh giá.'
      loading.value = false
    },
  )
}

/** Ghi bộ lọc + trang lên URL query — F5/Back không mất trạng thái đang xem. */
function syncQuery() {
  const q = {}
  if (applied.keyword) q.keyword = applied.keyword
  if (applied.score) q.score = applied.score
  if (applied.periodNote) q.period = applied.periodNote
  if (page.value > 0) q.page = String(page.value + 1)
  router.replace({ query: q }).catch(() => {})
}

async function refreshStats() {
  try {
    const { data } = await evaluationApi.stats()
    stats.value = data
  } catch {
    /* ignore */
  }
}

function applyFilter() {
  applied.keyword = filter.keyword
  applied.score = filter.score
  applied.periodNote = filter.periodNote
  page.value = 0
  syncQuery()
  loadList()
  refreshStats()
  loadUnevaluated()
}

function clearFilter() {
  filter.keyword = ''
  filter.score = ''
  filter.periodNote = ''
  applyFilter()
}

function goPage(p) {
  if (p < 0 || p >= totalPages.value) return
  page.value = p
  syncQuery()
  loadList()
}

function openCreate() {
  Object.assign(modal, {
    open: true,
    mode: 'create',
    initial: null,
    prefill: null,
    saving: false,
    error: '',
  })
}

/** Từ drawer chi tiết → mở modal sửa và đóng drawer. */
function editFromDetail() {
  const row = detail.value
  detail.value = null
  if (row) openEdit(row)
}

function openEdit(row) {
  Object.assign(modal, {
    open: true,
    mode: 'edit',
    initial: { ...row },
    prefill: null,
    saving: false,
    error: '',
  })
}

/** Dialog in-app xác nhận lưu trùng kỳ (thay window.confirm — hiện được thông tin phiếu cũ). */
const dupConfirm = reactive({ open: false, message: '', payload: null })

// Mở form Sửa từ modal chi tiết rồi đóng modal chi tiết. Gộp thành 1 hàm để KHÔNG
// nhồi nhiều câu lệnh vào @click — Prettier (cấu hình no-semi) từng gỡ dấu ; khiến
// trình biên dịch template Vue báo "Unexpected token, expected ,".
function editFromDetail() {
  openEdit(detail.value)
  detail.value = null
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
    await loadUnevaluated()
  } catch (e) {
    // BE 409 trùng kỳ (cả tạo lẫn sửa) — hỏi lại bằng dialog rồi gửi kèm forceDuplicate.
    if (e.response?.status === 409 && !payload.forceDuplicate) {
      dupConfirm.message = e.response?.data?.message || 'Đã có đánh giá cùng kỳ. Vẫn lưu thêm?'
      dupConfirm.payload = payload
      dupConfirm.open = true
    } else {
      modal.error = e.response?.data?.message || 'Lưu đánh giá thất bại'
    }
  } finally {
    modal.saving = false
  }
}

function cancelDuplicateSave() {
  dupConfirm.open = false
  dupConfirm.payload = null
}

async function confirmDuplicateSave() {
  const payload = { ...dupConfirm.payload, forceDuplicate: true }
  dupConfirm.open = false
  dupConfirm.payload = null
  await saveModal(payload)
}

async function doDelete() {
  if (!deleteTarget.value) return
  try {
    await evaluationApi.remove(deleteTarget.value.id)
    deleteTarget.value = null
    // Vừa xóa bản ghi cuối của trang cuối → lùi 1 trang, khỏi kẹt ở "Trang 3/2" trống.
    if (items.value.length === 1 && page.value > 0) {
      page.value -= 1
      syncQuery()
    }
    await loadList()
    await refreshStats()
    await loadUnevaluated()
  } catch (e) {
    error.value = e.response?.data?.message || 'Xóa thất bại'
    deleteTarget.value = null
  }
}

/** Cắt preview nhận xét theo KÝ TỰ thật (Array.from) — slice code-unit có thể chém đôi emoji. */
function previewComment(comment) {
  if (!comment) return '—'
  const chars = Array.from(comment)
  return chars.length > 60 ? chars.slice(0, 60).join('') + '…' : comment
}

/** Khôi phục bộ lọc + trang từ URL query (đồng bộ 2 chiều với syncQuery). */
function restoreFromQuery() {
  const q = route.query
  if (q.keyword) filter.keyword = String(q.keyword)
  if (q.score) filter.score = String(q.score)
  if (q.period) filter.periodNote = String(q.period)
  applied.keyword = filter.keyword
  applied.score = filter.score
  applied.periodNote = filter.periodNote
  const p = Number(q.page)
  if (Number.isInteger(p) && p > 1) page.value = p - 1
}

onMounted(async () => {
  restoreFromQuery()
  await loadMeta()
  await loadList()
})
</script>

<template>
  <div class="page">
    <div class="page__head">
      <div>
        <h1 class="page__title">{{ title }}</h1>
        <p v-if="subtitle" class="page__sub">{{ subtitle }}</p>
      </div>
      <button class="btn" type="button" @click="openCreate">
        <SvgIcon name="plus" :size="16" /> Tạo đánh giá
      </button>
    </div>

    <!-- KPI -->
    <section v-if="stats" class="stat-grid">
      <StatCard
        icon="evaluation"
        label="Tổng lượt đánh giá"
        :value="stats.totalCount"
        color="#f97316"
      />
      <StatCard icon="evaluation" label="Điểm trung bình" :value="avgLabel" color="#2563eb" />
      <StatCard
        icon="evaluation"
        label="Điểm cao (≥4)"
        :value="stats.highScoreCount"
        :hint="
          stats.totalCount
            ? `${Math.round((stats.highScoreCount / stats.totalCount) * 100)}% tổng lượt`
            : ''
        "
        color="#22c55e"
      />
      <StatCard
        icon="teacher"
        label="GV đã được đánh giá"
        :value="stats.teacherCountEvaluated"
        color="#0ea5e9"
      />
    </section>

    <!--
      Hai panel cân đối: coverage kỳ (trái) ↔ phân bố điểm (phải).
      Cùng chiều cao, cùng bo góc; xuống 1 cột khi hẹp.
    -->
    <section class="insight-grid">
      <!--
        Coverage kỳ: thẻ hành động — số còn lại + progress + chevron.
        Không dùng chữ “bấm để xem…”; clickability = layout + hover + mũi tên.
      -->
      <button
        type="button"
        class="coverage"
        :class="{
          'coverage--alert': unevalCount > 0,
          'coverage--ok': coverageDone,
          'coverage--loading': unevalCount == null,
        }"
        :disabled="unevalCount == null"
        :aria-label="coverageTitle"
        @click="openUnevaluatedPanel"
      >
        <div class="coverage__head">
          <div class="coverage__icon" aria-hidden="true">
            <SvgIcon :name="coverageDone ? 'attendance' : 'teacher'" :size="20" />
          </div>
          <span class="coverage__label">Tiến độ chấm điểm</span>
          <span v-if="uneval.data?.periodNote || coveragePeriod" class="coverage__period">
            {{ uneval.data?.periodNote || coveragePeriod }}
          </span>
          <span class="coverage__chev" aria-hidden="true">›</span>
        </div>

        <div class="coverage__main">
          <span v-if="unevalCount > 0" class="coverage__count">{{ unevalCount }}</span>
          <span v-else-if="coverageDone" class="coverage__check">✓</span>
          <span class="coverage__title">{{ coverageHeadline }}</span>
        </div>

        <div class="coverage__foot">
          <div class="coverage__bar" aria-hidden="true">
            <div class="coverage__fill" :style="{ width: coveragePct + '%' }" />
          </div>
          <div class="coverage__meta">
            <template v-if="uneval.data">
              Đã chấm <strong>{{ uneval.data.evaluatedCount }}</strong> /
              {{ uneval.data.totalTeachers }} · {{ coveragePct }}%
              <template v-if="unevalSchoolName"> · {{ unevalSchoolName }}</template>
            </template>
            <template v-else>Đang tính theo kỳ hiện tại…</template>
          </div>
        </div>
      </button>

      <!-- Phân bố sao -->
      <div v-if="stats && stats.totalCount" class="dist card-soft">
        <div class="dist__head">
          <span class="dist__label">Phân bố điểm</span>
          <span class="dist__total">{{ stats.totalCount }} lượt</span>
        </div>
        <div class="dist__rows">
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
      </div>
    </section>

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
      <label class="field field--period">
        <span>Kỳ</span>
        <select v-model="filter.periodNote" class="period-select" @change="applyFilter">
          <option value="">Tất cả</option>
          <option v-if="suggestedPeriod" :value="suggestedPeriod">
            {{ suggestedPeriod }} (gợi ý)
          </option>
          <option
            v-for="p in periodPresets.filter((x) => x !== suggestedPeriod)"
            :key="p"
            :value="p"
          >
            {{ p }}
          </option>
        </select>
      </label>
      <div class="filter-actions">
        <button class="btn" type="button" @click="applyFilter">Lọc</button>
        <button class="btn btn--ghost" type="button" @click="clearFilter">Xóa lọc</button>
      </div>
    </div>

    <p v-if="!loading && !error && totalItems > 0" class="total">
      Tổng cộng <strong>{{ totalItems }}</strong> lượt đánh giá
    </p>

    <!-- API lỗi: nói thẳng là lỗi + nút thử lại — không giả vờ "chưa có đánh giá nào" -->
    <div v-if="error" class="empty-hero">
      <div class="empty-hero__icon">⚠️</div>
      <h3 class="empty-hero__title">Không tải được danh sách đánh giá</h3>
      <p class="empty-hero__sub">{{ error }}</p>
      <button class="btn" type="button" @click="loadList">Thử lại</button>
    </div>

    <!-- Empty state lần đầu -->
    <div v-else-if="isPristineEmpty" class="empty-hero">
      <div class="empty-hero__icon">★</div>
      <h3 class="empty-hero__title">Chưa có đánh giá nào</h3>
      <p class="empty-hero__sub">
        Tạo phiếu chấm điểm đầu tiên để theo dõi chất lượng giảng dạy theo kỳ.
      </p>
      <button class="btn" type="button" @click="openCreate">
        <SvgIcon name="plus" :size="16" /> Tạo đánh giá đầu tiên
      </button>
    </div>

    <div v-else class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Giáo viên</th>
            <th>Điểm</th>
            <th>Kỳ</th>
            <th>Người đánh giá</th>
            <th>Nhận xét</th>
            <th>Thời gian</th>
            <th width="110">Thao tác</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7" class="empty">Đang tải…</td>
          </tr>
          <tr v-else-if="items.length === 0">
            <td colspan="7" class="empty">
              Không có kết quả khớp bộ lọc —
              <button type="button" class="linkish" @click="clearFilter">xóa lọc</button>
            </td>
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
            <td>{{ row.evaluatorName }}</td>
            <td class="col-comment">
              <button type="button" class="linkish" @click="detail = row">
                {{ previewComment(row.comment) }}
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

    <div v-if="!isPristineEmpty && totalPages > 1" class="pagination">
      <button class="pg-btn" type="button" :disabled="page === 0" @click="goPage(page - 1)">
        ‹
      </button>
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
      :schools="schools"
      :period-presets="periodPresets"
      :suggested-period="suggestedPeriod"
      :initial="modal.initial"
      :prefill="modal.prefill"
      :saving="modal.saving"
      :error="modal.error"
      :lock-teacher="isSchool"
      :school-scoped="isSchool || schoolScoped"
      @close="modal.open = false"
      @save="saveModal"
    />

    <!-- Panel: GV chưa đánh giá trong kỳ -->
    <div v-if="uneval.open" class="modal-backdrop" @click.self="closeUnevalPanel">
      <div class="modal uneval-modal" role="dialog" aria-modal="true">
        <div class="modal__head">
          <div>
            <h2 class="modal__title">
              {{
                uneval.data?.unevaluatedCount === 0 ? 'Coverage kỳ này' : 'Giáo viên cần đánh giá'
              }}
            </h2>
            <p v-if="uneval.data" class="modal__lead">
              <span class="modal__period-tag">{{ uneval.data.periodNote }}</span>
              · Đã chấm {{ uneval.data.evaluatedCount }}/{{ uneval.data.totalTeachers }} · Còn
              <strong>{{ uneval.data.unevaluatedCount }}</strong>
              <template v-if="unevalSchoolName"> · {{ unevalSchoolName }}</template>
            </p>
          </div>
          <button type="button" class="modal__x" @click="closeUnevalPanel">×</button>
        </div>
        <div class="modal__body">
          <div class="uneval-search-row">
            <input
              v-model="uneval.keyword"
              type="search"
              class="uneval-search-input"
              placeholder="Nhập tên giáo viên để tìm…"
              @input="onUnevalKeywordInput"
            />
            <select
              v-if="canPickSchool"
              v-model="uneval.schoolId"
              class="uneval-school-select"
              aria-label="Lọc theo trường"
              @change="onUnevalSchoolChange"
            >
              <option value="">🏫 Tất cả trường</option>
              <option v-for="s in schools" :key="s.id" :value="s.id">{{ s.name }}</option>
            </select>
            <button
              v-if="uneval.keyword || uneval.schoolId"
              type="button"
              class="btn btn--ghost btn--sm"
              @click="clearUnevalFilters"
            >
              Xóa lọc
            </button>
          </div>
          <div v-if="uneval.loading" class="empty">Đang tải…</div>
          <div v-else-if="uneval.data?.teachers?.length" class="uneval-table-wrap">
            <table class="uneval-table">
              <thead>
                <tr>
                  <th>Giáo viên</th>
                  <th>Liên hệ</th>
                  <th>Trường</th>
                  <th>Chi nhánh</th>
                  <th>Thống kê</th>
                  <th width="120">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="t in uneval.data.teachers" :key="t.id">
                  <td>
                    <div class="uneval-name-cell">
                      <div class="uneval-item__avatar" aria-hidden="true">
                        {{ (t.name || '?').trim().charAt(0).toUpperCase() }}
                      </div>
                      <div class="title-text">{{ t.name }}</div>
                    </div>
                  </td>
                  <td class="muted">{{ t.phone || '—' }}</td>
                  <td>{{ t.schoolsLabel || 'Chưa phân công trường' }}</td>
                  <td class="muted">{{ t.branchName || '—' }}</td>
                  <td class="muted">
                    <template v-if="t.totalCount">
                      TB {{ t.averageScore != null ? Number(t.averageScore).toFixed(1) : '—' }}/5 ·
                      {{ t.totalCount }} lượt
                    </template>
                    <template v-else>Chưa từng có đánh giá</template>
                  </td>
                  <td>
                    <button type="button" class="btn btn--sm" @click="evaluateTeacher(t)">
                      Chấm điểm
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="empty empty--ok">
            {{
              uneval.data?.unevaluatedCount === 0
                ? 'Không còn ai trong hàng đợi — kỳ này đã phủ đủ.'
                : 'Không khớp bộ lọc hiện tại.'
            }}
          </div>
        </div>
        <div class="modal__foot">
          <button type="button" class="btn btn--ghost" @click="closeUnevalPanel">Đóng</button>
        </div>
      </div>
    </div>

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
          <p><strong>Người đánh giá:</strong> {{ detail.evaluatorName }}</p>
          <p><strong>Thời gian:</strong> {{ formatDateTime(detail.createdAt) }}</p>
          <p class="detail-comment">
            <strong>Nhận xét:</strong><br />{{ detail.comment || '(Không có)' }}
          </p>
        </div>
        <div class="modal__foot">
          <button
            v-if="detail.canEdit"
            type="button"
            class="btn btn--ghost"
            @click="editFromDetail"
          >
            Sửa
          </button>
          <button type="button" class="btn" @click="detail = null">Đóng</button>
        </div>
      </div>
    </div>

    <!-- Xác nhận lưu trùng kỳ (in-app, thay window.confirm) -->
    <div v-if="dupConfirm.open" class="modal-backdrop" @click.self="cancelDuplicateSave">
      <div class="modal confirm-modal">
        <div class="modal__head">
          <h2 class="modal__title">Trùng kỳ đánh giá</h2>
          <button type="button" class="modal__x" @click="cancelDuplicateSave">×</button>
        </div>
        <div class="modal__body">
          <p>{{ dupConfirm.message }}</p>
        </div>
        <div class="modal__foot">
          <button type="button" class="btn btn--ghost" @click="cancelDuplicateSave">Hủy</button>
          <button type="button" class="btn" @click="confirmDuplicateSave">Vẫn lưu thêm</button>
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
            Xóa mềm đánh giá của <strong>{{ deleteTarget.teacherName }}</strong> ({{
              deleteTarget.score
            }}/5)? Dữ liệu vẫn lưu trong hệ thống để audit.
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
/* —— Hai panel cân đối: coverage ↔ phân bố điểm —— */
.insight-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 0.85rem;
  align-items: stretch;
  margin-bottom: 1rem;
}
.insight-grid > * {
  margin: 0 !important;
  min-height: 100%;
}

/* —— Coverage panel (chưa đánh giá) —— */
.coverage {
  width: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 0.75rem;
  text-align: left;
  padding: 0.9rem 1.1rem;
  border-radius: 12px;
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  cursor: pointer;
  font: inherit;
  color: var(--c-text);
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}
.coverage__head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.coverage__label {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--c-text);
}
.coverage__head .coverage__chev {
  margin-left: auto;
}
.coverage__main {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  min-height: 2.2rem;
}
.coverage__foot {
  margin-top: auto;
}
.coverage:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: var(--a-shadow-lg);
  border-color: color-mix(in srgb, var(--c-primary, #f97316) 55%, var(--c-border));
}
.coverage:disabled {
  cursor: default;
  opacity: 0.85;
}
/* Dùng color-mix với --c-surface → sáng/tối đều đọc được, không hard-code #fff */
.coverage--alert {
  border-color: color-mix(in srgb, #f97316 45%, var(--c-border));
  background: color-mix(in srgb, #f97316 14%, var(--c-surface));
}
.coverage--alert .coverage__icon {
  background: color-mix(in srgb, #f97316 22%, var(--c-surface-2));
  color: var(--c-primary, #f97316);
}
.coverage--alert .coverage__fill {
  background: linear-gradient(90deg, #fb923c, #ef4444);
}
.coverage--alert .coverage__count {
  color: #f97316;
}
.coverage--ok {
  border-color: color-mix(in srgb, #22c55e 40%, var(--c-border));
  background: color-mix(in srgb, #22c55e 12%, var(--c-surface));
}
.coverage--ok .coverage__icon {
  background: color-mix(in srgb, #22c55e 20%, var(--c-surface-2));
  color: #22c55e;
}
.coverage--ok .coverage__fill {
  background: linear-gradient(90deg, #4ade80, #22c55e);
}
.coverage--ok .coverage__check {
  color: #22c55e;
  font-size: 2rem;
  font-weight: 800;
  line-height: 1;
}
.coverage__icon {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: var(--c-surface-2);
  color: var(--c-text-muted);
  flex-shrink: 0;
}
.coverage__title {
  font-weight: 600;
  font-size: 0.95rem;
  line-height: 1.3;
}
.coverage__period {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--c-text-muted);
  background: var(--c-surface-2);
  border-radius: 999px;
  padding: 0.12rem 0.55rem;
}
.coverage__bar {
  height: 7px;
  border-radius: 999px;
  background: var(--c-surface-2);
  overflow: hidden;
}
.coverage__fill {
  height: 100%;
  border-radius: 999px;
  background: var(--c-primary, #f97316);
  min-width: 0;
  transition: width 0.4s ease;
}
.coverage__meta {
  margin-top: 0.35rem;
  font-size: 0.8rem;
  color: var(--c-text-muted);
}
.coverage__meta strong {
  color: var(--c-text);
}
.coverage__count {
  font-size: 2rem;
  font-weight: 800;
  line-height: 1;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.coverage__chev {
  font-size: 1.5rem;
  font-weight: 300;
  color: var(--c-text-muted);
  line-height: 1;
  transition:
    transform 0.15s ease,
    color 0.15s ease;
}
.coverage:hover:not(:disabled) .coverage__chev {
  transform: translateX(3px);
  color: var(--c-primary, #f97316);
}

/* .modal khai báo sau nên cần tăng specificity mới ghi đè được width */
.modal.uneval-modal {
  width: min(1240px, 96vw);
  max-height: 90vh;
  display: flex;
  flex-direction: column;
}
.modal.uneval-modal .modal__body {
  overflow: auto;
}
.modal__lead {
  margin: 0.25rem 0 0;
  font-size: 0.85rem;
  color: var(--c-text-muted);
}
.modal__period-tag {
  display: inline-block;
  font-weight: 600;
  color: var(--c-primary, #f97316);
}
.uneval-search-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.85rem;
}
.uneval-search-input {
  flex: 1;
  min-width: 200px;
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  padding: 0.5rem 0.7rem;
  background: var(--c-surface);
  color: var(--c-text);
  font: inherit;
}
.uneval-school-select {
  min-width: 200px;
  max-width: 280px;
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  padding: 0.5rem 0.7rem;
  background: var(--c-surface-2);
  color: var(--c-text);
  font: inherit;
  font-size: 0.9rem;
  cursor: pointer;
  appearance: none;
  -webkit-appearance: none;
}
.uneval-table-wrap {
  max-height: min(58vh, 560px);
  overflow: auto;
  border: 1px solid var(--c-border);
  border-radius: 12px;
  background: var(--c-surface);
}
.uneval-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}
.uneval-table th,
.uneval-table td {
  padding: 0.7rem 0.9rem;
  text-align: left;
  border-bottom: 1px solid var(--c-border-soft);
  color: var(--c-text);
  vertical-align: middle;
}
.uneval-table th {
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: var(--c-text-muted);
  background: var(--c-surface-2);
  position: sticky;
  top: 0;
  z-index: 1;
}
.uneval-table tbody tr:hover {
  background: var(--c-surface-2);
}
.uneval-table tbody tr:last-child td {
  border-bottom: 0;
}
.uneval-name-cell {
  display: flex;
  align-items: center;
  gap: 0.65rem;
}
.uneval-item__avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--c-primary, #f97316) 22%, var(--c-surface-2));
  color: var(--c-primary, #f97316);
  font-weight: 700;
  font-size: 0.95rem;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}
.empty--ok {
  color: color-mix(in srgb, #22c55e 85%, var(--c-text));
  background: color-mix(in srgb, #22c55e 12%, var(--c-surface));
  border: 1px solid color-mix(in srgb, #22c55e 35%, var(--c-border));
  border-radius: 12px;
  padding: 1.25rem;
  text-align: center;
}
.btn--sm {
  padding: 0.35rem 0.7rem;
  font-size: 0.82rem;
  white-space: nowrap;
}
.card-soft {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 12px;
  padding: 0.9rem 1.1rem;
}
.dist {
  display: flex;
  flex-direction: column;
}
.dist__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
  margin-bottom: 0.6rem;
}
.dist__label {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--c-text);
}
.dist__total {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--c-text-muted);
  background: var(--c-surface-2);
  border-radius: 999px;
  padding: 0.12rem 0.55rem;
}
/* Chia đều chiều cao còn lại → cân với panel coverage bên trái */
.dist__rows {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0.35rem;
}
.dist__row {
  display: grid;
  grid-template-columns: 30px 1fr 34px;
  gap: 0.5rem;
  align-items: center;
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
  font-variant-numeric: tabular-nums;
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
.field--period {
  min-width: 160px;
}
/* Ẩn icon mũi tên dropdown của select Kỳ */
.period-select {
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  background-image: none;
  padding-right: 0.6rem;
}
.period-select::-ms-expand {
  display: none;
}
.empty-hero {
  text-align: center;
  padding: 2.5rem 1.25rem;
  background: var(--c-surface);
  border: 1px dashed var(--c-border);
  border-radius: 14px;
  margin-bottom: 1rem;
}
.empty-hero__icon {
  font-size: 2rem;
  color: #f59e0b;
  margin-bottom: 0.5rem;
}
.empty-hero__title {
  margin: 0 0 0.35rem;
  color: var(--c-text);
  font-size: 1.1rem;
}
.empty-hero__sub {
  margin: 0 0 1rem;
  color: var(--c-text-muted);
  font-size: 0.92rem;
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
  color: color-mix(in srgb, #ef4444 80%, var(--c-text));
  background: color-mix(in srgb, #ef4444 14%, var(--c-surface));
  border: 1px solid color-mix(in srgb, #ef4444 30%, var(--c-border));
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
  background: var(--c-surface);
}
.title-text {
  font-weight: 600;
  color: var(--c-text);
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
  color: var(--c-text);
}
.act-btn--del:hover {
  background: color-mix(in srgb, #ef4444 22%, var(--c-surface-2));
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
  background: rgba(0, 0, 0, 0.55);
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
  color: var(--c-text);
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
  color: var(--c-text);
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
