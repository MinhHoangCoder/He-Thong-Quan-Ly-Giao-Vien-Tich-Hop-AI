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
const coverageSchoolId = ref('')
const coverageBranchId = ref('')
const filterMeta = ref({ schools: [], branches: [], schoolScoped: false })

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
  data: null, // UnevaluatedTeachersResponse
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

/** Chưa có filter + chưa có data → empty “lần đầu”. */
const isPristineEmpty = computed(() => {
  return (
    !loading.value &&
    totalItems.value === 0 &&
    !filter.keyword &&
    !filter.score &&
    !filter.source &&
    !filter.periodNote
  )
})

function isNew(row) {
  if (!row?.createdAt) return false
  const t = new Date(row.createdAt).getTime()
  if (Number.isNaN(t)) return false
  return Date.now() - t < 7 * 24 * 60 * 60 * 1000
}

async function loadMeta() {
  try {
    const tasks = [
      evaluationApi.periodMeta(),
      evaluationApi.stats(buildStatsParams()),
      evaluationApi.filterMeta().catch(() => ({ data: null })),
    ]
    const [metaRes, statsRes, filterRes] = await Promise.all(tasks)
    periodPresets.value = metaRes.data?.presets || []
    suggestedPeriod.value = metaRes.data?.suggested || ''
    stats.value = statsRes.data
    if (filterRes?.data) filterMeta.value = filterRes.data
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

async function loadUnevaluated() {
  uneval.loading = true
  try {
    const params = {
      periodNote: coveragePeriod.value || undefined,
      keyword: uneval.keyword || undefined,
    }
    if (!isSchool.value && coverageSchoolId.value) {
      params.schoolId = Number(coverageSchoolId.value)
    }
    if (coverageBranchId.value) {
      params.branchId = Number(coverageBranchId.value)
    }
    const { data } = await evaluationApi.unevaluatedTeachers(params)
    uneval.data = data
  } catch (e) {
    console.error(e)
    uneval.data = null
  } finally {
    uneval.loading = false
  }
}

function openUnevaluatedPanel() {
  uneval.open = true
  uneval.keyword = ''
  loadUnevaluated()
}

function onUnevalSearch() {
  loadUnevaluated()
}

function evaluateTeacher(t) {
  uneval.open = false
  Object.assign(modal, {
    open: true,
    mode: 'create',
    initial: null,
    prefill: {
      teacherId: t.id,
      periodNote: uneval.data?.periodNote || coveragePeriod.value || suggestedPeriod.value,
    },
    saving: false,
    error: '',
  })
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
  loadUnevaluated()
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
  Object.assign(modal, {
    open: true,
    mode: 'create',
    initial: null,
    prefill: null,
    saving: false,
    error: '',
  })
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

async function saveModal(payload) {
  modal.saving = true
  modal.error = ''
  try {
    if (modal.mode === 'edit' && modal.initial?.id) {
      await evaluationApi.update(modal.initial.id, payload)
    } else {
      try {
        await evaluationApi.create(payload)
      } catch (e) {
        // BE 409 trùng kỳ — hỏi lại + force
        if (e.response?.status === 409 && !payload.forceDuplicate) {
          const ok = window.confirm(
            e.response?.data?.message || 'Đã có đánh giá cùng kỳ. Vẫn lưu thêm?',
          )
          if (!ok) return
          await evaluationApi.create({ ...payload, forceDuplicate: true })
        } else {
          throw e
        }
      }
    }
    modal.open = false
    await loadList()
    await refreshStats()
    await loadUnevaluated()
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
    await loadUnevaluated()
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
      Coverage kỳ: thẻ hành động full-width — số còn lại + progress + chevron.
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
      <div class="coverage__icon" aria-hidden="true">
        <SvgIcon :name="coverageDone ? 'attendance' : 'teacher'" :size="22" />
      </div>
      <div class="coverage__body">
        <div class="coverage__top">
          <span class="coverage__title">{{ coverageTitle }}</span>
          <span v-if="uneval.data?.periodNote || coveragePeriod" class="coverage__period">
            {{ uneval.data?.periodNote || coveragePeriod }}
          </span>
        </div>
        <div class="coverage__bar" aria-hidden="true">
          <div class="coverage__fill" :style="{ width: coveragePct + '%' }" />
        </div>
        <div class="coverage__meta">
          <template v-if="uneval.data">
            Đã chấm <strong>{{ uneval.data.evaluatedCount }}</strong>
            /
            {{ uneval.data.totalTeachers }}
            · {{ coveragePct }}%
          </template>
          <template v-else>Đang tính theo kỳ hiện tại…</template>
        </div>
      </div>
      <div class="coverage__side">
        <span v-if="unevalCount > 0" class="coverage__count">{{ unevalCount }}</span>
        <span v-else-if="coverageDone" class="coverage__check">✓</span>
        <span class="coverage__chev" aria-hidden="true">›</span>
      </div>
    </button>

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
      <label class="field field--period">
        <span>Kỳ</span>
        <select v-model="filter.periodNote" @change="applyFilter">
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

    <p v-if="error" class="msg msg--error">{{ error }}</p>
    <p v-if="!loading && totalItems > 0" class="total">
      Tổng cộng <strong>{{ totalItems }}</strong> lượt đánh giá
    </p>

    <!-- Empty state lần đầu -->
    <div v-if="isPristineEmpty" class="empty-hero">
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
            <td :colspan="isSchool ? 7 : 8" class="empty">
              Không có kết quả khớp bộ lọc —
              <button type="button" class="linkish" @click="clearFilter">xóa lọc</button>
            </td>
          </tr>
          <tr v-for="row in items" :key="row.id">
            <td class="col-title">
              <div class="title-text">
                {{ row.teacherName }}
                <span v-if="isNew(row)" class="badge badge--new">Mới</span>
              </div>
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
                {{
                  row.comment
                    ? row.comment.length > 60
                      ? row.comment.slice(0, 60) + '…'
                      : row.comment
                    : '—'
                }}
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
      :period-presets="periodPresets"
      :suggested-period="suggestedPeriod"
      :initial="modal.initial"
      :prefill="modal.prefill"
      :saving="modal.saving"
      :error="modal.error"
      :lock-teacher="isSchool"
      :school-scoped="isSchool"
      @close="modal.open = false"
      @save="saveModal"
    />

    <!-- Panel: GV chưa đánh giá trong kỳ -->
    <div v-if="uneval.open" class="modal-backdrop" @click.self="uneval.open = false">
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
            </p>
          </div>
          <button type="button" class="modal__x" @click="uneval.open = false">×</button>
        </div>
        <div class="modal__body">
          <div class="uneval-filters">
            <select
              v-if="!isSchool && filterMeta.schools?.length"
              v-model="coverageSchoolId"
              class="uneval-select"
              @change="onUnevalSearch"
            >
              <option value="">Mọi trường</option>
              <option v-for="s in filterMeta.schools" :key="s.id" :value="String(s.id)">
                {{ s.name }}
              </option>
            </select>
            <select
              v-if="filterMeta.branches?.length"
              v-model="coverageBranchId"
              class="uneval-select"
              @change="onUnevalSearch"
            >
              <option value="">Mọi chi nhánh</option>
              <option v-for="b in filterMeta.branches" :key="b.id" :value="String(b.id)">
                {{ b.name }}
              </option>
            </select>
          </div>
          <div class="uneval-search-row">
            <input
              v-model="uneval.keyword"
              type="search"
              class="uneval-search-input"
              placeholder="Tìm theo tên / SĐT…"
              @keyup.enter="onUnevalSearch"
            />
            <button type="button" class="btn btn--ghost" @click="onUnevalSearch">Lọc</button>
          </div>
          <div v-if="uneval.loading" class="empty">Đang tải…</div>
          <ul v-else-if="uneval.data?.teachers?.length" class="uneval-list">
            <li v-for="t in uneval.data.teachers" :key="t.id" class="uneval-item">
              <div class="uneval-item__avatar" aria-hidden="true">
                {{ (t.name || '?').trim().charAt(0).toUpperCase() }}
              </div>
              <div class="uneval-item__info">
                <div class="title-text">{{ t.name }}</div>
                <div class="uneval-item__schools">
                  {{ t.schoolsLabel || 'Chưa phân công trường' }}
                </div>
                <div class="muted">
                  <template v-if="t.branchName">{{ t.branchName }} · </template>
                  <template v-if="t.totalCount">
                    TB {{ t.averageScore != null ? Number(t.averageScore).toFixed(1) : '—' }}/5 ·
                    {{ t.totalCount }} lượt
                  </template>
                  <template v-else>Chưa từng có đánh giá</template>
                </div>
              </div>
              <button type="button" class="btn btn--sm" @click="evaluateTeacher(t)">
                Chấm điểm
              </button>
            </li>
          </ul>
          <div v-else class="empty empty--ok">
            {{
              uneval.data?.unevaluatedCount === 0
                ? 'Không còn ai trong hàng đợi — kỳ này đã phủ đủ.'
                : 'Không khớp tên tìm kiếm.'
            }}
          </div>
        </div>
        <div class="modal__foot">
          <button type="button" class="btn btn--ghost" @click="uneval.open = false">Đóng</button>
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
          <p><strong>Nguồn:</strong> {{ sourceLabel(detail) }}</p>
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
/* —— Coverage strip (chưa đánh giá) —— */
.coverage {
  width: 100%;
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 0.9rem;
  align-items: center;
  text-align: left;
  margin: 0 0 1rem;
  padding: 0.95rem 1.1rem;
  border-radius: 14px;
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  box-shadow: var(--a-shadow);
  cursor: pointer;
  font: inherit;
  color: var(--c-text);
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
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
  font-size: 1.35rem;
  font-weight: 700;
}
.coverage__icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: var(--c-surface-2);
  color: var(--c-text-muted);
}
.coverage__body {
  min-width: 0;
}
.coverage__top {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.4rem 0.75rem;
  margin-bottom: 0.4rem;
}
.coverage__title {
  font-weight: 700;
  font-size: 0.98rem;
  line-height: 1.25;
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
.coverage__side {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  flex-shrink: 0;
}
.coverage__count {
  font-size: 1.65rem;
  font-weight: 800;
  line-height: 1;
  min-width: 1.5rem;
  text-align: right;
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

.uneval-modal {
  width: min(520px, 100%);
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
.uneval-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin-bottom: 0.55rem;
}
.uneval-select {
  flex: 1;
  min-width: 140px;
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  padding: 0.45rem 0.6rem;
  background: var(--c-surface);
  color: var(--c-text);
  font: inherit;
  font-size: 0.88rem;
}
.uneval-search-row {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.85rem;
}
.uneval-search-input {
  flex: 1;
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  padding: 0.5rem 0.7rem;
  background: var(--c-surface);
  color: var(--c-text);
  font: inherit;
}
.uneval-item__schools {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--c-primary, #f97316);
  margin: 0.1rem 0;
}
.uneval-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 360px;
  overflow: auto;
  border: 1px solid var(--c-border);
  border-radius: 12px;
  background: var(--c-surface);
}
.uneval-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 0.9rem;
  border-bottom: 1px solid var(--c-border-soft);
  background: var(--c-surface);
  color: var(--c-text);
}
.uneval-item:hover {
  background: var(--c-surface-2);
}
.uneval-item:last-child {
  border-bottom: 0;
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
.uneval-item__info {
  flex: 1;
  min-width: 0;
  color: var(--c-text);
}
.uneval-item__info .title-text {
  color: var(--c-text);
}
.uneval-item__info .muted {
  color: var(--c-text-muted);
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
.field--period {
  min-width: 160px;
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
.badge--new {
  margin-left: 0.4rem;
  background: color-mix(in srgb, #22c55e 18%, var(--c-surface-2));
  color: color-mix(in srgb, #22c55e 75%, var(--c-text));
  vertical-align: middle;
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
.badge {
  display: inline-block;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
}
.badge--center {
  background: color-mix(in srgb, #2563eb 18%, var(--c-surface-2));
  color: color-mix(in srgb, #60a5fa 70%, var(--c-text));
}
.badge--school {
  background: color-mix(in srgb, #f97316 18%, var(--c-surface-2));
  color: color-mix(in srgb, #fb923c 70%, var(--c-text));
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
