<script setup>
/**
 * ĐƠN XIN NGHỈ MỘT BUỔI DẠY — giáo viên tự gửi.
 *
 * Một hộp thoại làm hai việc: GỬI đơn mới (chọn buổi + lý do) và XEM các đơn đã gửi kèm kết
 * quả. Gộp lại vì đó đúng là hai câu hỏi liên tiếp của người dùng ("tôi xin nghỉ được không"
 * và "đơn hôm trước ra sao rồi"), tách thành hai màn chỉ tổ bắt họ đi tìm.
 *
 * Ô chọn là DANH SÁCH BUỔI CÓ THẬT trong lịch, không phải ô ngày để gõ: đơn phải trỏ đúng một
 * buổi thì trung tâm duyệt xong mới có cái để tắt, mà gõ ngày thì rất dễ trỏ vào hôm mình vốn
 * không có tiết. Dùng SearchSelect như các ô chọn khác trong hệ thống — danh sách bốn tuần
 * của người dạy dày lịch là vài chục dòng, gõ để tìm nhanh hơn cuộn.
 *
 * Đơn KHÔNG tự tắt buổi: trung tâm duyệt thì buổi hôm đó mới chuyển "Nghỉ có phép" (không tính
 * công, không tính lương). Phân công dài hạn giữ nguyên — tuần sau vẫn dạy lớp ấy.
 */
import { computed, onMounted, ref } from 'vue'
import { leaveRequestApi } from '@/api/leaveRequests'
import SearchSelect from '@/components/ui/SearchSelect.vue'

const emit = defineEmits(['close', 'sent'])

const STATUS_LABEL = {
  PENDING: 'Chờ trung tâm duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Bị từ chối',
}

const sessions = ref([])
const requests = ref([])
const loading = ref(false)
const busy = ref(false)
const errorMsg = ref('')

const form = ref({ key: '', reason: '' })

function fmtDate(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso ?? '')
  return m ? `${m[3]}/${m[2]}/${m[1]}` : '—'
}

/**
 * SearchSelect đòi mỗi mục có { id, name }. Khoá của một buổi là cặp (phân công, ngày) nên
 * backend đã ghép sẵn thành chuỗi `key` — dùng thẳng làm id, khỏi tự ghép lần thứ hai ở đây
 * rồi lệch quy ước với server.
 */
const options = computed(() =>
  sessions.value.map((s) => ({ ...s, id: s.key, name: `${s.sessionText} · ${s.className || '—'}` })),
)

/** Buổi đang chọn — hiện lại trường/lớp/môn để người gửi soát trước khi bấm. */
const selected = computed(() => sessions.value.find((s) => s.key === form.value.key))

/** Buổi thuộc phân công đã có đơn chờ thì khoá lại: mỗi phân công chỉ được một đơn chờ. */
function optionDisabled(item) {
  return item.pending ? 'Phân công này đang có một đơn chờ trung tâm xử lý' : ''
}

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const [opts, mine] = await Promise.all([leaveRequestApi.mySessions(), leaveRequestApi.mine()])
    sessions.value = opts.data ?? []
    requests.value = mine.data ?? []
  } catch (e) {
    errorMsg.value = e?.response?.data?.message || 'Không tải được danh sách buổi dạy.'
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function submit() {
  if (busy.value) return
  errorMsg.value = ''
  const buoi = selected.value
  if (!buoi) {
    errorMsg.value = 'Vui lòng chọn buổi cần xin nghỉ.'
    return
  }
  if (!form.value.reason.trim()) {
    errorMsg.value = 'Vui lòng nhập lý do xin nghỉ.'
    return
  }
  busy.value = true
  try {
    await leaveRequestApi.create({
      assignmentId: buoi.assignmentId,
      leaveDate: buoi.date,
      reason: form.value.reason.trim(),
    })
    form.value = { key: '', reason: '' }
    await load()
    emit('sent')
  } catch (e) {
    errorMsg.value = e?.response?.data?.message || 'Không gửi được đơn. Vui lòng thử lại.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal-box lr">
      <h3>Xin nghỉ một buổi dạy</h3>

      <p class="lr__hint">
        Đơn được gửi tới trung tâm. Khi được duyệt, <strong>chỉ buổi đã chọn</strong> chuyển sang
        “Nghỉ có phép” và không tính công; các buổi khác của phân công vẫn giữ nguyên.
      </p>

      <div v-if="loading" class="lr__state">Đang tải…</div>

      <template v-else>
        <div v-if="!sessions.length" class="lr__state">
          Bốn tuần tới thầy/cô không có buổi dạy nào đã được duyệt.
        </div>

        <template v-else>
          <div class="form-group">
            <label>Buổi cần nghỉ <span class="req">*</span></label>
            <SearchSelect
              v-model="form.key"
              :options="options"
              :option-disabled="optionDisabled"
              placeholder="— Chọn buổi dạy —"
              search-placeholder="Gõ ngày, lớp hoặc trường…"
            />
            <small v-if="selected">
              {{ selected.schoolName || '—' }} · lớp {{ selected.className || '—' }} ·
              {{ selected.subjectName || '—' }}
            </small>
          </div>

          <div class="form-group">
            <label>Lý do <span class="req">*</span></label>
            <textarea v-model="form.reason" rows="2" placeholder="vd: đi khám bệnh theo lịch hẹn" />
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
              Nghỉ buổi <b>{{ fmtDate(r.leaveDate) }}</b> · {{ r.schoolName || '—' }}
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
        <button v-if="sessions.length" class="btn btn-primary" :disabled="busy" @click="submit">
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
