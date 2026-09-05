<script setup>
/**
 * ĐƠN XIN NGHỈ DẠY — giáo viên tự gửi (V39).
 *
 * Một hộp thoại làm hai việc: GỬI đơn mới (chọn phân công + ngày bắt đầu nghỉ + lý do) và
 * XEM các đơn đã gửi kèm kết quả. Gộp lại vì đó đúng là hai câu hỏi liên tiếp của người dùng
 * ("tôi xin nghỉ được không" và "đơn hôm trước ra sao rồi"), tách thành hai màn chỉ tổ bắt
 * họ đi tìm.
 *
 * Đơn KHÔNG tự hủy lịch: trung tâm duyệt thì hệ thống mới dừng phân công kể từ ngày đã xin.
 */
import { computed, onMounted, ref } from 'vue'
import { leaveRequestApi } from '@/api/leaveRequests'

const emit = defineEmits(['close', 'sent'])

const STATUS_LABEL = {
  PENDING: 'Chờ trung tâm duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Bị từ chối',
}

const assignments = ref([])
const requests = ref([])
const loading = ref(false)
const busy = ref(false)
const errorMsg = ref('')

const form = ref({ assignmentId: '', effectiveDate: todayIso(), reason: '' })

/** Hôm nay dạng yyyy-MM-dd — vừa làm giá trị mặc định vừa làm chặn dưới cho ô ngày. */
function todayIso() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function fmtDate(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso ?? '')
  return m ? `${m[3]}/${m[2]}/${m[1]}` : '—'
}

/** Phân công đang chọn — để hiện lại giai đoạn và chặn ngày nghỉ vượt ra ngoài. */
const selected = computed(() =>
  assignments.value.find((a) => String(a.id) === String(form.value.assignmentId)),
)

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const [opts, mine] = await Promise.all([leaveRequestApi.myAssignments(), leaveRequestApi.mine()])
    assignments.value = opts.data ?? []
    requests.value = mine.data ?? []
    if (assignments.value.length === 1) form.value.assignmentId = assignments.value[0].id
  } catch (e) {
    errorMsg.value = e?.response?.data?.message || 'Không tải được danh sách phân công.'
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function submit() {
  if (busy.value) return
  errorMsg.value = ''
  if (!form.value.assignmentId) {
    errorMsg.value = 'Vui lòng chọn phân công cần xin nghỉ.'
    return
  }
  if (!form.value.reason.trim()) {
    errorMsg.value = 'Vui lòng nhập lý do xin nghỉ.'
    return
  }
  busy.value = true
  try {
    await leaveRequestApi.create({
      assignmentId: Number(form.value.assignmentId),
      effectiveDate: form.value.effectiveDate,
      reason: form.value.reason.trim(),
    })
    form.value = { assignmentId: '', effectiveDate: todayIso(), reason: '' }
    await load()
    emit('sent')
  } catch (e) {
    errorMsg.value = e?.response?.data?.message || 'Không gửi được đơn. Vui lòng thử lại.'
  } finally {
    busy.value = false
  }
}

/** Mô tả một phân công trong ô chọn: trường · môn · giai đoạn. */
function optionLabel(a) {
  return `${a.schoolName || '—'} · ${a.subjectName || '—'} · ${fmtDate(a.startDate)}–${fmtDate(a.endDate)}`
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal-box lr">
      <h3>Xin nghỉ dạy</h3>

      <p class="lr__hint">
        Đơn sẽ được gửi tới trung tâm. Khi được duyệt, lịch dạy của thầy/cô
        <strong>dừng từ ngày đã chọn</strong>; các buổi trước ngày đó vẫn dạy bình thường và vẫn
        được tính công.
      </p>

      <div v-if="loading" class="lr__state">Đang tải…</div>

      <template v-else>
        <div v-if="!assignments.length" class="lr__state">
          Thầy/cô hiện không có phân công nào đang dạy.
        </div>

        <template v-else>
          <div class="form-group">
            <label>Phân công cần nghỉ <span class="req">*</span></label>
            <select v-model="form.assignmentId">
              <option value="">— Chọn phân công —</option>
              <option v-for="a in assignments" :key="a.id" :value="a.id">
                {{ optionLabel(a) }}
              </option>
            </select>
          </div>

          <div class="form-group">
            <label>Nghỉ từ ngày <span class="req">*</span></label>
            <input
              v-model="form.effectiveDate"
              type="date"
              :min="todayIso()"
              :max="selected?.endDate || undefined"
            />
            <small v-if="selected">
              Phân công này chạy tới {{ fmtDate(selected.endDate) }}.
            </small>
          </div>

          <div class="form-group">
            <label>Lý do <span class="req">*</span></label>
            <textarea v-model="form.reason" rows="2" placeholder="vd: nghỉ chế độ thai sản" />
          </div>
        </template>
      </template>

      <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>

      <!-- Đơn đã gửi + kết quả: câu hỏi tiếp theo của người vừa gửi đơn -->
      <div v-if="requests.length" class="lr__history">
        <strong>Đơn đã gửi</strong>
        <ul>
          <li v-for="r in requests" :key="r.id">
            <span class="lr__row">
              Nghỉ từ <b>{{ fmtDate(r.effectiveDate) }}</b> · {{ r.schoolName || '—' }}
              <span
                class="badge"
                :class="{
                  'badge-amber': r.status === 'PENDING',
                  'badge-green': r.status === 'APPROVED',
                  'badge-red': r.status === 'REJECTED',
                }"
                >{{ STATUS_LABEL[r.status] ?? r.status }}</span
              >
            </span>
            <small v-if="r.decisionNote">Trung tâm: “{{ r.decisionNote }}”</small>
          </li>
        </ul>
      </div>

      <div class="modal-actions">
        <button class="btn btn-outline" :disabled="busy" @click="emit('close')">Đóng</button>
        <button
          v-if="assignments.length"
          class="btn btn-primary"
          :disabled="busy"
          @click="submit"
        >
          Gửi đơn
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.lr {
  max-width: 560px;
}
.lr__hint {
  margin: 0 0 0.9rem;
  font-size: 0.85rem;
  color: var(--c-text-muted);
}
.lr__state {
  padding: 0.8rem 0;
  color: var(--c-text-muted);
  font-size: 0.9rem;
}
.lr__history {
  margin-top: 1rem;
  padding-top: 0.8rem;
  border-top: 1px solid var(--c-border);
  font-size: 0.85rem;
}
.lr__history ul {
  margin: 0.5rem 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}
.lr__row {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex-wrap: wrap;
}
.lr__history small {
  display: block;
  color: var(--c-text-muted);
  font-style: italic;
}
.req {
  color: var(--c-danger);
}
</style>
