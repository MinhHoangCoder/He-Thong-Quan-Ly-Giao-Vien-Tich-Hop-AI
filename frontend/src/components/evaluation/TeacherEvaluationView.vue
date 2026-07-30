<script setup>
/**
 * View read-only cho giáo viên — dùng trong EvaluationPage.
 */
import { ref, computed, onMounted } from 'vue'
import { evaluationApi } from '@/api/evaluations'
import { useLatestRequest } from '@/composables/useLatestRequest'
import { formatDateTime } from '@/utils/format'
import StarRating from '@/components/evaluation/StarRating.vue'
import StatCard from '@/components/ui/StatCard.vue'

const loading = ref(false)
const error = ref('')
const items = ref([])
const stats = ref(null)
const page = ref(0)
const totalPages = ref(0)
const totalItems = ref(0)
/** 12 = chia hết cho 2/3/4 cột → lưới không bị lẻ hàng cuối. */
const PAGE_SIZE = 12
const detail = ref(null)
/** '' = tất cả sao; lọc server-side qua param `score`. */
const scoreFilter = ref('')

const avgLabel = computed(() => {
  const a = stats.value?.averageScore
  if (a == null) return '—'
  return `${Number(a).toFixed(1)}/5`
})

const highPct = computed(() => {
  if (!stats.value?.totalCount) return ''
  return `${Math.round((stats.value.highScoreCount / stats.value.totalCount) * 100)}% lượt ≥ 4 sao`
})

/** Chỉ nhận kết quả của lần bấm SAU CÙNG — bấm chip lọc sao liên tiếp không lệch dữ liệu. */
const latestLoad = useLatestRequest()

async function load() {
  loading.value = true
  error.value = ''
  await latestLoad(
    () => {
      const params = { page: page.value, size: PAGE_SIZE }
      if (scoreFilter.value) params.score = Number(scoreFilter.value)
      return Promise.all([evaluationApi.list(params), evaluationApi.stats()])
    },
    ([listRes, statsRes]) => {
      items.value = listRes.data.content || []
      totalPages.value = listRes.data.totalPages || 0
      totalItems.value = listRes.data.totalElements || 0
      stats.value = statsRes.data
      loading.value = false
    },
    (e) => {
      console.error(e)
      error.value = e.response?.data?.message || 'Không tải được đánh giá của bạn.'
      loading.value = false
    },
  )
}

function goPage(p) {
  if (p < 0 || p >= totalPages.value) return
  page.value = p
  load()
}

/** Bấm lại chip đang chọn = bỏ lọc. */
function pickScore(n) {
  scoreFilter.value = scoreFilter.value === n ? '' : n
  page.value = 0
  load()
}

/** Số lượt theo từng mức sao — dùng cho badge trên chip lọc. */
function countFor(n) {
  return stats.value?.['score' + n] || 0
}

/** Chỉ ngày/tháng/năm — ô đánh giá đã thu nhỏ nên bỏ giờ phút. */
function shortDate(v) {
  if (!v) return '—'
  return new Date(v).toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page__head">
      <div>
        <h1 class="page__title">Đánh giá của tôi</h1>
      </div>
    </div>

    <section v-if="stats" class="stat-grid">
      <StatCard
        icon="evaluation"
        label="Số lượt đánh giá"
        :value="stats.totalCount"
        color="#f97316"
      />
      <StatCard icon="evaluation" label="Điểm trung bình" :value="avgLabel" color="#2563eb" />
      <StatCard
        icon="evaluation"
        label="Lượt điểm cao"
        :value="stats.highScoreCount"
        :hint="highPct"
        color="#22c55e"
      />
    </section>

    <div v-if="stats && stats.totalCount" class="dist">
      <span class="dist__label">Phân bố điểm nhận được</span>
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

    <p v-if="error" class="msg msg--error">{{ error }}</p>

    <!-- Thanh lọc nhanh theo mức sao — bấm lại chip đang chọn để bỏ lọc -->
    <div v-if="stats && stats.totalCount" class="toolbar">
      <div class="chips">
        <button
          type="button"
          class="chip"
          :class="{ 'chip--on': scoreFilter === '' }"
          @click="pickScore('')"
        >
          Tất cả
          <span class="chip__n">{{ stats.totalCount }}</span>
        </button>
        <button
          v-for="n in [5, 4, 3, 2, 1]"
          :key="n"
          type="button"
          class="chip"
          :class="{ 'chip--on': scoreFilter === n, 'chip--empty': !countFor(n) }"
          :disabled="!countFor(n)"
          @click="pickScore(n)"
        >
          {{ n }}★
          <span class="chip__n">{{ countFor(n) }}</span>
        </button>
      </div>
      <span v-if="!loading" class="total">
        <strong>{{ totalItems }}</strong> nhận xét
      </span>
    </div>

    <div v-if="loading" class="empty">Đang tải…</div>
    <!-- API lỗi: nói đúng là lỗi + cho thử lại — không hiện nhầm "chưa có đánh giá nào" -->
    <div v-else-if="error" class="empty">
      Không tải được dữ liệu —
      <button type="button" class="linkish" @click="load">thử lại</button>
    </div>
    <div v-else-if="items.length === 0" class="empty">
      <template v-if="scoreFilter">
        Không có nhận xét nào ở mức {{ scoreFilter }}★ —
        <button type="button" class="linkish" @click="pickScore('')">xem tất cả</button>
      </template>
      <template v-else>
        Bạn chưa có đánh giá nào. Khi có phiếu chấm điểm, kết quả sẽ hiện tại đây.
      </template>
    </div>

    <!-- Lưới ô đánh giá thu gọn: 1 ô = 1 phiếu, tự xếp 2–4 cột theo bề rộng -->
    <div v-else class="cards">
      <article
        v-for="row in items"
        :key="row.id"
        class="card"
        :class="`card--s${row.score}`"
        tabindex="0"
        role="button"
        @click="detail = row"
        @keyup.enter="detail = row"
      >
        <header class="card__top">
          <span class="card__score">{{ row.score }}<small>/5</small></span>
          <StarRating :model-value="row.score" :size="13" />
          <time class="card__date" :title="formatDateTime(row.createdAt)">
            {{ shortDate(row.createdAt) }}
          </time>
        </header>
        <p class="card__comment" :class="{ 'card__comment--none': !row.comment }">
          {{ row.comment || 'Không có nhận xét chi tiết.' }}
        </p>
        <footer class="card__foot">
          <span class="card__period">{{ row.periodNote || 'Không ghi kỳ' }}</span>
        </footer>
      </article>
    </div>

    <div v-if="totalPages > 1" class="pagination">
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

    <div v-if="detail" class="modal-backdrop" @click.self="detail = null">
      <div class="modal">
        <div class="modal__head">
          <h2 class="modal__title">Chi tiết nhận xét</h2>
          <button type="button" class="modal__x" @click="detail = null">×</button>
        </div>
        <div class="modal__body">
          <p>
            <StarRating :model-value="detail.score" />
            <strong> {{ detail.score }}/5</strong>
          </p>
          <p><strong>Kỳ:</strong> {{ detail.periodNote || '—' }}</p>
          <p><strong>Thời gian:</strong> {{ formatDateTime(detail.createdAt) }}</p>
          <p class="detail-comment">{{ detail.comment || '(Không có nhận xét)' }}</p>
        </div>
        <div class="modal__foot">
          <button type="button" class="btn" @click="detail = null">Đóng</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page__head {
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
.dist {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 12px;
  padding: 0.9rem 1rem;
  margin-bottom: 1rem;
}
.dist__label {
  font-weight: 600;
  font-size: 0.9rem;
  display: block;
  margin-bottom: 0.5rem;
  color: var(--c-text);
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
}
.dist__n {
  font-size: 0.8rem;
  color: var(--c-text-muted);
  text-align: right;
}
.total {
  color: var(--c-text-muted);
  font-size: 0.9rem;
}
.msg--error {
  color: color-mix(in srgb, #ef4444 80%, var(--c-text));
  background: color-mix(in srgb, #ef4444 14%, var(--c-surface));
  border: 1px solid color-mix(in srgb, #ef4444 30%, var(--c-border));
  padding: 0.55rem 0.75rem;
  border-radius: 8px;
}
/* —— Thanh lọc nhanh —— */
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem 0.75rem;
  margin-bottom: 0.75rem;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  color: var(--c-text-muted);
  border-radius: 999px;
  padding: 0.25rem 0.6rem;
  font: inherit;
  font-size: 0.8rem;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    color 0.15s ease;
}
.chip:hover:not(:disabled) {
  border-color: var(--c-primary);
  color: var(--c-primary);
}
.chip--on {
  border-color: var(--c-primary);
  color: var(--c-primary);
  background: color-mix(in srgb, var(--c-primary, #f97316) 12%, var(--c-surface));
  font-weight: 600;
}
.chip:disabled {
  opacity: 0.45;
  cursor: default;
}
.chip__n {
  font-size: 0.72rem;
  font-variant-numeric: tabular-nums;
  background: var(--c-surface-2);
  border-radius: 999px;
  padding: 0 0.35rem;
}

/* —— Lưới ô đánh giá thu gọn —— */
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 0.7rem;
  align-items: stretch;
}
.card {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  /* Dải màu trái = mức sao → quét mắt nhanh mà không cần đọc số */
  border-left: 3px solid var(--c-border);
  border-radius: 10px;
  padding: 0.7rem 0.8rem;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease,
    transform 0.15s ease;
}
.card:hover,
.card:focus-visible {
  box-shadow: var(--a-shadow);
  transform: translateY(-2px);
  outline: none;
}
.card--s5 {
  border-left-color: #22c55e;
}
.card--s4 {
  border-left-color: #84cc16;
}
.card--s3 {
  border-left-color: #f59e0b;
}
.card--s2 {
  border-left-color: #fb923c;
}
.card--s1 {
  border-left-color: #ef4444;
}
.card__top {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
.card__score {
  font-weight: 700;
  font-size: 1rem;
  line-height: 1;
  color: var(--c-text);
  font-variant-numeric: tabular-nums;
}
.card__score small {
  font-size: 0.7rem;
  font-weight: 500;
  color: var(--c-text-muted);
}
.card__date {
  margin-left: auto;
  font-size: 0.72rem;
  color: var(--c-text-muted);
  white-space: nowrap;
}
.card__comment {
  margin: 0;
  flex: 1;
  color: var(--c-text);
  font-size: 0.85rem;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card__comment--none {
  color: var(--c-text-muted);
  font-style: italic;
}
.card__foot {
  display: flex;
  align-items: center;
}
.card__period {
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--c-text-muted);
  background: var(--c-surface-2);
  border-radius: 999px;
  padding: 0.1rem 0.5rem;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.linkish {
  border: 0;
  background: none;
  padding: 0;
  font: inherit;
  color: var(--c-accent, #2563eb);
  cursor: pointer;
}
.empty {
  text-align: center;
  color: var(--c-text-muted);
  padding: 2.5rem 1rem;
  background: var(--c-surface);
  border-radius: 12px;
  border: 1px dashed var(--c-border);
}
.pagination {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 1rem;
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
  width: min(440px, 100%);
  background: var(--c-surface);
  border-radius: 14px;
  border: 1px solid var(--c-border);
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
  padding: 0.85rem 1.1rem;
  border-top: 1px solid var(--c-border-soft);
}
.btn {
  border: 0;
  border-radius: 8px;
  padding: 0.5rem 1rem;
  background: var(--grad-primary);
  color: #fff;
  font-weight: 600;
  cursor: pointer;
  font: inherit;
}
.detail-comment {
  white-space: pre-wrap;
  margin-top: 0.5rem;
}
</style>
