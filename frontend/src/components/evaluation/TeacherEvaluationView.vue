<script setup>
/**
 * View read-only cho giáo viên — dùng trong EvaluationPage.
 */
import { ref, computed, onMounted } from 'vue'
import { evaluationApi } from '@/api/evaluations'
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
const PAGE_SIZE = 10
const detail = ref(null)

const avgLabel = computed(() => {
  const a = stats.value?.averageScore
  if (a == null) return '—'
  return `${Number(a).toFixed(1)}/5`
})

const highPct = computed(() => {
  if (!stats.value?.totalCount) return ''
  return `${Math.round((stats.value.highScoreCount / stats.value.totalCount) * 100)}% lượt ≥ 4 sao`
})

/** Insight 1 dòng theo rule đơn giản (không cần AI). */
const insight = computed(() => {
  const s = stats.value
  if (!s || !s.totalCount) {
    return 'Chưa có đánh giá — khi trường hoặc trung tâm chấm điểm, gợi ý sẽ hiện tại đây.'
  }
  const avg = s.averageScore != null ? Number(s.averageScore) : null
  const highRatio = Math.round((s.highScoreCount / s.totalCount) * 100)
  if (avg == null) return `Bạn có ${s.totalCount} lượt đánh giá.`
  if (avg >= 4.5) {
    return `Xuất sắc — điểm TB ${avg.toFixed(1)}/5, ${highRatio}% lượt ≥ 4 sao. Giữ vững phong độ!`
  }
  if (avg >= 4) {
    return `Tốt — điểm TB ${avg.toFixed(1)}/5, ${highRatio}% lượt ≥ 4 sao. Tiếp tục phát huy.`
  }
  if (avg >= 3) {
    return `Khá — điểm TB ${avg.toFixed(1)}/5. Nên xem nhận xét chi tiết để cải thiện.`
  }
  return `Cần cải thiện — điểm TB ${avg.toFixed(1)}/5. Đọc kỹ nhận xét và trao đổi với trung tâm nếu cần.`
})

function sourceLabel(row) {
  if (row.source === 'CENTER') return 'Trung tâm'
  return row.schoolName || 'Trường'
}

function isNew(row) {
  if (!row?.createdAt) return false
  const t = new Date(row.createdAt).getTime()
  if (Number.isNaN(t)) return false
  return Date.now() - t < 7 * 24 * 60 * 60 * 1000
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [listRes, statsRes] = await Promise.all([
      evaluationApi.list({ page: page.value, size: PAGE_SIZE }),
      evaluationApi.stats(),
    ])
    items.value = listRes.data.content || []
    totalPages.value = listRes.data.totalPages || 0
    totalItems.value = listRes.data.totalElements || 0
    stats.value = statsRes.data
  } catch (e) {
    console.error(e)
    error.value = e.response?.data?.message || 'Không tải được đánh giá của bạn.'
  } finally {
    loading.value = false
  }
}

function goPage(p) {
  if (p < 0 || p >= totalPages.value) return
  page.value = p
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page__head">
      <div>
        <h1 class="page__title">Đánh giá của tôi</h1>
        <p class="page__sub">Xem điểm & nhận xét từ trường và trung tâm — chỉ đọc</p>
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

    <div class="insight" role="status"><strong>Gợi ý:</strong> {{ insight }}</div>

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
    <p v-if="!loading" class="total">
      <strong>{{ totalItems }}</strong> nhận xét
    </p>

    <div class="cards">
      <div v-if="loading" class="empty">Đang tải…</div>
      <div v-else-if="items.length === 0" class="empty">
        Bạn chưa có đánh giá nào. Khi trường hoặc trung tâm chấm điểm, kết quả sẽ hiện tại đây.
      </div>
      <article v-for="row in items" :key="row.id" class="card" @click="detail = row">
        <div class="card__top">
          <StarRating :model-value="row.score" :size="18" />
          <span class="card__score">{{ row.score }}/5</span>
          <span v-if="isNew(row)" class="badge badge--new">Mới</span>
          <span class="badge" :class="row.source === 'CENTER' ? 'badge--center' : 'badge--school'">
            {{ sourceLabel(row) }}
          </span>
        </div>
        <p class="card__period">{{ row.periodNote || 'Không ghi kỳ' }}</p>
        <p class="card__comment">
          {{ row.comment || 'Không có nhận xét chi tiết.' }}
        </p>
        <div class="card__meta">
          <span>{{ formatDateTime(row.createdAt) }}</span>
        </div>
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
          <p><strong>Nguồn:</strong> {{ sourceLabel(detail) }}</p>
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
.insight {
  margin-bottom: 1rem;
  padding: 0.75rem 1rem;
  border-radius: 10px;
  /* Mix token theme — tránh nền #fff7ed/#eff6ff cứng, dark mode không đọc được */
  background: color-mix(in srgb, var(--c-primary, #f97316) 12%, var(--c-surface));
  border: 1px solid color-mix(in srgb, var(--c-primary, #f97316) 35%, var(--c-border));
  color: var(--c-text);
  font-size: 0.92rem;
  line-height: 1.45;
}
.insight strong {
  color: var(--c-primary, #f97316);
  margin-right: 0.25rem;
}
.badge--new {
  background: color-mix(in srgb, #22c55e 18%, var(--c-surface-2));
  color: color-mix(in srgb, #22c55e 75%, var(--c-text));
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
.cards {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.card {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 12px;
  padding: 1rem 1.1rem;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}
.card:hover {
  border-color: var(--c-primary);
  box-shadow: var(--a-shadow);
}
.card__top {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.35rem;
}
.card__score {
  font-weight: 700;
  color: var(--c-primary);
}
.badge {
  margin-left: auto;
  padding: 0.15rem 0.55rem;
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
.card__period {
  margin: 0 0 0.35rem;
  font-size: 0.85rem;
  color: var(--c-text-muted);
}
.card__comment {
  margin: 0;
  color: var(--c-text);
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card__meta {
  margin-top: 0.55rem;
  font-size: 0.8rem;
  color: var(--c-text-muted);
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
