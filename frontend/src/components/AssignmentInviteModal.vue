<script setup>
/**
 * Bảng LỊCH DẠY CHI TIẾT của một lời mời phân công — giáo viên mở từ chuông thông báo.
 *
 * VÌ SAO CÓ: dòng thông báo trước đây liệt kê từng tiết ("Thứ 3 Tiết 6 lớp 1A1, Thứ 3
 * Tiết 7 lớp 1A2, …"), phiếu vài chục tiết là khối chữ không đọc nổi. Nay thông báo chỉ
 * nói quy mô, chi tiết dồn vào lưới thời khóa biểu ở đây.
 *
 * Dữ liệu đọc LIVE từ phân công (không phải chuỗi đã lưu trong thông báo) nên admin sửa
 * phiếu sau khi gửi thì giáo viên mở ra vẫn thấy bản mới nhất.
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { tietLabel } from '@/utils/period'
import SvgIcon from '@/components/ui/SvgIcon.vue'

const props = defineProps({
  /** AssignmentInviteDetail từ GET /notifications/{id}/assignment; null = đang tải. */
  detail: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  /** Còn cho bấm Xác nhận / Từ chối không (thông báo đã xử lý thì không). */
  actionable: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
})
const emit = defineEmits(['close', 'confirm', 'cancel', 'open-schedule'])

const DAY_ORDER = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']
const DAY_LABEL = {
  MON: 'Thứ 2',
  TUE: 'Thứ 3',
  WED: 'Thứ 4',
  THU: 'Thứ 5',
  FRI: 'Thứ 6',
  SAT: 'Thứ 7',
  SUN: 'Chủ nhật',
}

const slots = computed(() => props.detail?.slots ?? [])

// Lưới chỉ vẽ những THỨ và những TIẾT thật sự có trong phiếu — phiếu 4 tiết chiều thì
// không việc gì phải kéo đủ 10 dòng trống.
const days = computed(() => {
  const present = new Set(slots.value.map((s) => s.dayOfWeek))
  return DAY_ORDER.filter((d) => present.has(d)).map((d) => ({ code: d, label: DAY_LABEL[d] ?? d }))
})
const rows = computed(() => {
  const byNumber = new Map()
  for (const s of slots.value) {
    if (byNumber.has(s.periodNumber)) continue
    byNumber.set(s.periodNumber, {
      periodNumber: s.periodNumber,
      label: tietLabel(s.periodNumber, s.sessionType, s.indexInSession),
      time: `${hhmm(s.startTime)}–${hhmm(s.endTime)}`,
      afternoon: s.sessionType === 'AFTERNOON',
    })
  }
  return [...byNumber.values()].sort((a, b) => a.periodNumber - b.periodNumber)
})
function cellOf(dayCode, periodNumber) {
  return slots.value.find((s) => s.dayOfWeek === dayCode && s.periodNumber === periodNumber) ?? null
}

const hhmm = (t) => (t ? String(t).slice(0, 5) : '')
const fmtDate = (iso) => {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso ?? '')
  return m ? `${m[3]}/${m[2]}/${m[1]}` : '—'
}

/* ── Đếm ngược hạn xác nhận ── */
// Phiếu quá hạn tự hết hiệu lực nên giáo viên cần thấy áp lực thời gian, không phải tự
// nhẩm từ mốc giờ. Nhịp 30s là đủ: đơn vị nhỏ nhất hiển thị là phút.
const now = ref(Date.now())
let timer = null
onMounted(() => {
  timer = setInterval(() => (now.value = Date.now()), 30000)
})
onBeforeUnmount(() => clearInterval(timer))

const deadline = computed(() => {
  const raw = props.detail?.confirmDeadline
  return raw ? new Date(raw).getTime() : null
})
const countdown = computed(() => {
  if (!deadline.value) return null
  const left = deadline.value - now.value
  if (left <= 0) return { text: 'Đã quá hạn xác nhận', urgent: true, over: true }
  const hours = Math.floor(left / 3600000)
  const mins = Math.floor((left % 3600000) / 60000)
  const text =
    hours >= 24
      ? `Còn ${Math.floor(hours / 24)} ngày ${hours % 24} giờ`
      : hours >= 1
        ? `Còn ${hours} giờ ${mins} phút`
        : `Còn ${mins} phút`
  return { text, urgent: hours < 12, over: false }
})

const totalPerWeek = computed(() => slots.value.length)
const classNames = computed(() => props.detail?.classNames ?? [])

/* ── Từ chối: bắt buộc lý do, hỏi ngay trong modal ── */
const rejecting = ref(false)
const reason = ref('')
function submitReject() {
  const value = reason.value.trim()
  if (!value) {
    window.alert('Vui lòng nhập lý do từ chối.')
    return
  }
  emit('cancel', value)
}

// window không có trong scope template của <script setup> nên phải bọc lại.
function printTable() {
  window.print()
}

function onKey(e) {
  if (e.key === 'Escape') emit('close')
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="inv-overlay" @click.self="emit('close')">
    <div class="inv" role="dialog" aria-label="Lịch dạy chi tiết">
      <div class="inv__head">
        <h3>Lịch dạy chi tiết</h3>
        <button class="inv__x" aria-label="Đóng" @click="emit('close')">×</button>
      </div>

      <div v-if="loading" class="inv__state">Đang tải lịch dạy…</div>
      <div v-else-if="error" class="inv__state inv__state--error">{{ error }}</div>

      <template v-else-if="detail">
        <!-- Tóm tắt: câu hỏi đầu tiên của giáo viên luôn là "dạy mấy tiết, những lớp nào" -->
        <div class="inv__sum">
          <div class="inv__where">
            <strong>{{ detail.schoolName }}</strong>
            <span>{{ detail.subjectName }}</span>
          </div>
          <div class="inv__facts">
            <span class="inv__chip"
              ><b>{{ totalPerWeek }}</b> tiết/tuần</span
            >
            <span class="inv__chip"
              ><b>{{ classNames.length }}</b> lớp</span
            >
            <span class="inv__chip inv__chip--plain">
              {{ fmtDate(detail.startDate) }} →
              {{ detail.endDate ? fmtDate(detail.endDate) : 'không giới hạn' }}
            </span>
          </div>
          <div v-if="classNames.length" class="inv__classes">
            Lớp: <span v-for="c in classNames" :key="c" class="inv__class">{{ c }}</span>
          </div>
          <div
            v-if="countdown"
            class="inv__deadline"
            :class="{ 'is-urgent': countdown.urgent, 'is-over': countdown.over }"
          >
            <SvgIcon name="clock" :size="14" />
            {{ countdown.text }} để xác nhận
          </div>
        </div>

        <!-- Lưới TKB: cột = thứ có dạy, dòng = tiết có dạy -->
        <div class="inv__gridwrap">
          <table v-if="rows.length" class="inv__grid">
            <thead>
              <tr>
                <th class="inv__corner">Tiết</th>
                <th v-for="d in days" :key="d.code">{{ d.label }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in rows" :key="r.periodNumber">
                <th class="inv__period" :class="r.afternoon ? 'pm' : 'am'">
                  <span>{{ r.label }}</span>
                  <small>{{ r.time }}</small>
                </th>
                <td v-for="d in days" :key="d.code">
                  <span
                    v-if="cellOf(d.code, r.periodNumber)"
                    class="inv__cell"
                    :class="r.afternoon ? 'pm' : 'am'"
                  >
                    {{ cellOf(d.code, r.periodNumber).className || 'Chưa gán lớp' }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-else class="inv__state">Phiếu này chưa có tiết nào.</p>
        </div>

        <!-- Từ chối phải kèm lý do, hỏi ngay tại chỗ thay vì bắt đóng modal ra ngoài bấm -->
        <div v-if="rejecting" class="inv__reject">
          <label>Lý do từ chối *</label>
          <textarea v-model="reason" rows="2" placeholder="Vì sao bạn không nhận lịch này?" />
        </div>

        <div class="inv__foot">
          <button class="inv__link" @click="emit('open-schedule')">Xem trên lịch dạy</button>
          <button class="inv__link" @click="printTable">In bảng</button>
          <span class="inv__spacer" />
          <template v-if="actionable">
            <template v-if="rejecting">
              <button class="inv__btn inv__btn--ghost" :disabled="busy" @click="rejecting = false">
                Bỏ
              </button>
              <button class="inv__btn inv__btn--danger" :disabled="busy" @click="submitReject">
                Gửi từ chối
              </button>
            </template>
            <template v-else>
              <button class="inv__btn inv__btn--ghost" :disabled="busy" @click="rejecting = true">
                Từ chối
              </button>
              <button class="inv__btn inv__btn--primary" :disabled="busy" @click="emit('confirm')">
                <SvgIcon name="check-all" :size="14" /> Xác nhận
              </button>
            </template>
          </template>
          <button v-else class="inv__btn inv__btn--ghost" @click="emit('close')">Đóng</button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.inv-overlay {
  position: fixed;
  inset: 0;
  z-index: 1200; /* trên cả bảng chuông (z-index 1000) */
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  background: rgba(11, 42, 74, 0.45);
}
.inv {
  width: 100%;
  max-width: 640px;
  max-height: 90vh;
  overflow-y: auto;
  padding: 1.25rem;
  border-radius: 16px;
  background: var(--c-surface);
  color: var(--c-text);
  box-shadow: 0 24px 60px rgba(11, 42, 74, 0.3);
}
.inv__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.9rem;
}
.inv__head h3 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 700;
}
.inv__x {
  border: 0;
  background: none;
  color: var(--c-text-muted);
  font-size: 1.4rem;
  line-height: 1;
  cursor: pointer;
}
.inv__state {
  padding: 1.4rem 0;
  text-align: center;
  color: var(--c-text-muted);
  font-size: 0.9rem;
}
.inv__state--error {
  color: var(--c-danger);
}

/* ── Tóm tắt ── */
.inv__sum {
  padding: 0.8rem 0.9rem;
  border-radius: 10px;
  background: var(--c-surface-2);
  margin-bottom: 0.9rem;
}
.inv__where {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem 0.6rem;
  align-items: baseline;
  margin-bottom: 0.5rem;
}
.inv__where strong {
  font-size: 0.98rem;
}
.inv__where span {
  color: var(--c-text-muted);
  font-size: 0.86rem;
}
.inv__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}
.inv__chip {
  padding: 0.18rem 0.5rem;
  border-radius: 999px;
  background: rgba(249, 115, 22, 0.12);
  color: var(--c-text);
  font-size: 0.8rem;
}
.inv__chip--plain {
  background: transparent;
  border: 1px solid var(--c-border);
  color: var(--c-text-muted);
}
.inv__classes {
  margin-top: 0.5rem;
  font-size: 0.8rem;
  color: var(--c-text-muted);
}
.inv__class {
  display: inline-block;
  margin-left: 0.3rem;
  padding: 0.1rem 0.42rem;
  border-radius: 6px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  color: var(--c-text);
}
.inv__deadline {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.6rem;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--c-text-muted);
}
.inv__deadline.is-urgent {
  color: var(--c-amber);
}
.inv__deadline.is-over {
  color: var(--c-danger);
}

/* ── Lưới thời khóa biểu ── */
.inv__gridwrap {
  overflow-x: auto; /* màn hẹp thì cuộn ngang, không nuốt mất cột cuối */
}
.inv__grid {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.84rem;
}
.inv__grid th,
.inv__grid td {
  border: 1px solid var(--c-border);
  padding: 0.4rem 0.5rem;
  text-align: center;
}
.inv__grid thead th {
  background: var(--c-surface-2);
  font-size: 0.8rem;
  white-space: nowrap;
}
.inv__corner {
  min-width: 108px;
}
.inv__period {
  text-align: left;
  font-weight: 600;
  white-space: nowrap;
}
.inv__period small {
  display: block;
  font-weight: 400;
  font-size: 0.72rem;
  color: var(--c-text-muted);
}
/* Sáng / chiều tô khác nhau — đọc lướt biết ngay buổi nào, cùng quy ước với Lịch dạy. */
.inv__period.am {
  border-left: 3px solid var(--c-amber);
}
.inv__period.pm {
  border-left: 3px solid var(--c-accent);
}
.inv__cell {
  display: inline-block;
  padding: 0.16rem 0.5rem;
  border-radius: 6px;
  font-weight: 600;
}
.inv__cell.am {
  background: rgba(245, 158, 11, 0.15);
}
.inv__cell.pm {
  background: rgba(37, 99, 235, 0.14);
}

/* ── Từ chối + chân modal ── */
.inv__reject {
  margin-top: 0.9rem;
}
.inv__reject label {
  display: block;
  margin-bottom: 0.25rem;
  font-size: 0.82rem;
  font-weight: 600;
}
.inv__reject textarea {
  width: 100%;
  padding: 0.5rem 0.7rem;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  background: var(--c-surface);
  color: var(--c-text);
  font-size: 0.86rem;
  box-sizing: border-box;
}
.inv__foot {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 1rem;
  flex-wrap: wrap;
}
.inv__spacer {
  flex: 1;
}
.inv__link {
  border: 0;
  background: none;
  padding: 0.2rem 0;
  color: var(--c-primary);
  font-size: 0.82rem;
  cursor: pointer;
}
.inv__link:hover {
  text-decoration: underline;
}
.inv__btn {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.45rem 0.9rem;
  border: 1px solid transparent;
  border-radius: 8px;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
}
.inv__btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.inv__btn--primary {
  background: var(--c-primary);
  color: #fff;
}
.inv__btn--danger {
  background: var(--c-danger);
  color: #fff;
}
.inv__btn--ghost {
  border-color: var(--c-border);
  background: var(--c-surface);
  color: var(--c-text);
}

/* ── In ra giấy: chỉ còn bảng, bỏ nền mờ và các nút ── */
@media print {
  .inv-overlay {
    position: static;
    padding: 0;
    background: none;
  }
  .inv {
    max-width: none;
    max-height: none;
    box-shadow: none;
    overflow: visible;
  }
  .inv__x,
  .inv__foot,
  .inv__reject {
    display: none;
  }
}
</style>
