<script setup>
/**
 * Bảng CHẤM CÔNG HÔM NAY của giáo viên — mỗi buổi dạy một dòng, bấm Check in / Check out
 * ngay tại chỗ.
 *
 * VÌ SAO CÓ: nút chấm công trước đây chỉ nằm ở Trang chủ, còn trang Chấm công thì chỉ để
 * xem lại — giáo viên vào đúng chỗ "Chấm công" lại không chấm được.
 *
 * Trạng thái do backend quyết (checkinToday), frontend chỉ vẽ:
 *   OPEN → bấm được | CHECKED_IN → đã vào, chờ ra | DONE → xong
 *   NOT_YET → chưa tới cửa sổ | MISSED → hết buổi mà chưa chấm | LOCKED → kế toán đã ghi Vắng/Nghỉ
 */
import { computed, onMounted, ref } from 'vue'
import { attendanceApi } from '@/api/attendance'

const emit = defineEmits(['changed'])

const data = ref(null)
const loading = ref(false)
const busyId = ref(null)
const msg = ref('')
const msgType = ref('info')

const sessions = computed(() => data.value?.sessions ?? [])
const hhmm = (t) => (t ? String(t).slice(0, 5) : '')
const timeOf = (iso) => (iso ? String(iso).slice(11, 16) : '')

const STATE = {
  OPEN: { label: 'Đang mở', tone: 'ok' },
  CHECKED_IN: { label: 'Đã vào lớp', tone: 'ok' },
  DONE: { label: 'Đã chấm đủ', tone: 'done' },
  NOT_YET: { label: 'Chưa tới giờ', tone: 'muted' },
  MISSED: { label: 'Đã lỡ', tone: 'warn' },
  LOCKED: { label: 'Kế toán đã ghi', tone: 'muted' },
}
const stateOf = (s) => STATE[s.state] ?? { label: s.state, tone: 'muted' }

async function load() {
  loading.value = true
  try {
    const { data: d } = await attendanceApi.checkinToday()
    data.value = d
  } catch (e) {
    data.value = null
    msgType.value = 'err'
    msg.value = e?.response?.data?.message || 'Không tải được trạng thái chấm công hôm nay.'
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function act(session, mode) {
  if (busyId.value) return
  busyId.value = session.scheduleId
  msg.value = ''
  try {
    const body = { scheduleId: session.scheduleId }
    if (mode === 'in') await attendanceApi.checkIn(body)
    else await attendanceApi.checkOut(body)
    msgType.value = 'ok'
    msg.value = mode === 'in' ? 'Đã check-in.' : 'Đã check-out.'
    await load()
    // Bảng chấm công của trang cha phải nạp lại, không thì giáo viên vừa chấm xong
    // nhìn xuống vẫn thấy dữ liệu cũ và tưởng hỏng.
    emit('changed')
  } catch (e) {
    msgType.value = 'err'
    msg.value = e?.response?.data?.message || 'Chấm công thất bại.'
  } finally {
    busyId.value = null
  }
}
</script>

<template>
  <section class="ckp card">
    <div class="ckp__head">
      <h3>Chấm công hôm nay</h3>
      <button class="ckp__reload" :disabled="loading" @click="load">⟳ Làm mới</button>
    </div>

    <p v-if="msg" class="ckp__msg" :class="'is-' + msgType">{{ msg }}</p>

    <p v-if="loading && !data" class="ckp__empty">Đang tải…</p>
    <p v-else-if="!sessions.length" class="ckp__empty">Hôm nay bạn không có buổi dạy nào.</p>

    <ul v-else class="ckp__list">
      <li v-for="s in sessions" :key="s.scheduleId" class="ckp__item">
        <div class="ckp__when">
          <strong>{{ timeOf(s.startTime) }}–{{ timeOf(s.endTime) }}</strong>
          <span class="ckp__tag" :class="'is-' + stateOf(s).tone">{{ stateOf(s).label }}</span>
        </div>
        <div class="ckp__where">
          {{ s.schoolName }}
          <template v-if="s.className"> · Lớp {{ s.className }}</template>
          <template v-if="s.subjectName"> · {{ s.subjectName }}</template>
        </div>
        <div class="ckp__times">
          Vào: <b>{{ hhmm(s.checkIn) || '—' }}</b> · Ra: <b>{{ hhmm(s.checkOut) || '—' }}</b>
        </div>
        <div class="ckp__act">
          <button
            v-if="s.state === 'OPEN'"
            class="ckp__btn ckp__btn--in"
            :disabled="busyId === s.scheduleId"
            @click="act(s, 'in')"
          >
            Check in
          </button>
          <button
            v-else-if="s.state === 'CHECKED_IN'"
            class="ckp__btn ckp__btn--out"
            :disabled="busyId === s.scheduleId"
            @click="act(s, 'out')"
          >
            Check out
          </button>
          <span v-else class="ckp__hint">
            {{
              s.state === 'NOT_YET'
                ? 'Mở trước giờ dạy 30 phút'
                : s.state === 'MISSED'
                  ? 'Liên hệ kế toán'
                  : '—'
            }}
          </span>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.ckp {
  padding: 1rem 1.1rem;
  margin-bottom: 1rem;
}
.ckp__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.7rem;
}
.ckp__head h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
}
.ckp__reload {
  border: 1px solid var(--c-border);
  border-radius: 8px;
  padding: 0.3rem 0.7rem;
  background: var(--c-surface);
  color: var(--c-text);
  font-size: 0.8rem;
  cursor: pointer;
}
.ckp__msg {
  margin: 0 0 0.7rem;
  padding: 0.45rem 0.7rem;
  border-radius: 8px;
  font-size: 0.83rem;
}
.ckp__msg.is-ok {
  background: rgba(34, 197, 94, 0.12);
  color: var(--c-success);
}
.ckp__msg.is-err {
  background: rgba(239, 68, 68, 0.12);
  color: var(--c-danger);
}
.ckp__empty {
  margin: 0;
  color: var(--c-text-muted);
  font-size: 0.86rem;
}
.ckp__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.5rem;
}
.ckp__item {
  display: grid;
  grid-template-columns: minmax(150px, auto) 1fr auto auto;
  align-items: center;
  gap: 0.5rem 0.9rem;
  padding: 0.55rem 0.7rem;
  border: 1px solid var(--c-border);
  border-radius: 10px;
}
.ckp__when {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.86rem;
}
.ckp__tag {
  padding: 0.1rem 0.45rem;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 600;
}
.ckp__tag.is-ok {
  background: rgba(249, 115, 22, 0.14);
  color: var(--c-primary);
}
.ckp__tag.is-done {
  background: rgba(34, 197, 94, 0.14);
  color: var(--c-success);
}
.ckp__tag.is-warn {
  background: rgba(245, 158, 11, 0.16);
  color: var(--c-amber);
}
.ckp__tag.is-muted {
  background: var(--c-surface-2);
  color: var(--c-text-muted);
}
.ckp__where,
.ckp__times {
  font-size: 0.82rem;
  color: var(--c-text-muted);
}
.ckp__btn {
  padding: 0.4rem 0.9rem;
  border: 0;
  border-radius: 8px;
  font-size: 0.83rem;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
}
.ckp__btn--in {
  background: var(--c-primary);
}
.ckp__btn--out {
  background: var(--c-accent);
}
.ckp__btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.ckp__hint {
  font-size: 0.78rem;
  color: var(--c-text-muted);
}
@media (max-width: 720px) {
  .ckp__item {
    grid-template-columns: 1fr;
  }
}
</style>
