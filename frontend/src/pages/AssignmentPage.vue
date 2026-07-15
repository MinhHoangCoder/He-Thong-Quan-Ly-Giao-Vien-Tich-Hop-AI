<script setup>
/**
 * Trang Phân công giảng dạy: danh sách phân công + tạo mới (chọn GV/môn/trường/lớp,
 * khoảng thời gian và các tiết Thứ+Tiết). Khi tạo, backend tự trải các tiết thành
 * buổi dạy (Schedule) — nguồn cho Chấm công & Bảng lương.
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { assignmentApi } from '@/api/assignments'

const DAYS = [
  { code: 'MON', label: 'Thứ 2' },
  { code: 'TUE', label: 'Thứ 3' },
  { code: 'WED', label: 'Thứ 4' },
  { code: 'THU', label: 'Thứ 5' },
  { code: 'FRI', label: 'Thứ 6' },
  { code: 'SAT', label: 'Thứ 7' },
  { code: 'SUN', label: 'Chủ nhật' },
]
const STATUS_LABEL = { ACTIVE: 'Đang chạy', COMPLETED: 'Hoàn thành', CANCELLED: 'Đã hủy' }

const loading = ref(false)
const items = ref([])

const options = reactive({ teachers: [], subjects: [], schools: [] })
const scoped = reactive({ classes: [], periods: [] })

const modal = reactive({
  open: false,
  saving: false,
  error: '',
  form: {
    teacherId: '',
    subjectId: '',
    schoolId: '',
    classId: '',
    startDate: new Date().toISOString().slice(0, 10),
    endDate: '',
    slots: [],
  },
  slotDraft: { dayOfWeek: 'MON', periodId: '' },
})

const cancelTarget = ref(null)

async function load() {
  loading.value = true
  try {
    const { data } = await assignmentApi.list()
    items.value = data
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function openCreate() {
  Object.assign(modal.form, {
    teacherId: '',
    subjectId: '',
    schoolId: '',
    classId: '',
    startDate: new Date().toISOString().slice(0, 10),
    endDate: '',
    slots: [],
  })
  modal.slotDraft = { dayOfWeek: 'MON', periodId: '' }
  modal.error = ''
  modal.open = true
  scoped.classes = []
  scoped.periods = []
  try {
    const { data } = await assignmentApi.options()
    options.teachers = data.teachers
    options.subjects = data.subjects
    options.schools = data.schools
  } catch (e) {
    modal.error = 'Không tải được dữ liệu form: ' + (e.response?.data?.message ?? e.message)
  }
}

async function onSchoolChange() {
  modal.form.classId = ''
  modal.form.slots = []
  scoped.classes = []
  scoped.periods = []
  if (!modal.form.schoolId) return
  try {
    const { data } = await assignmentApi.schoolOptions(modal.form.schoolId)
    scoped.classes = data.classes
    scoped.periods = data.periods
    modal.slotDraft.periodId = data.periods[0]?.id ?? ''
  } catch {
    /* giữ trống */
  }
}

function addSlot() {
  const { dayOfWeek, periodId } = modal.slotDraft
  if (!periodId) return
  const exists = modal.form.slots.some(
    (s) => s.dayOfWeek === dayOfWeek && s.periodId === Number(periodId),
  )
  if (exists) return
  modal.form.slots.push({ dayOfWeek, periodId: Number(periodId) })
}

function removeSlot(i) {
  modal.form.slots.splice(i, 1)
}

function slotLabel(s) {
  const d = DAYS.find((x) => x.code === s.dayOfWeek)?.label ?? s.dayOfWeek
  const p = scoped.periods.find((x) => x.id === s.periodId)
  return `${d} · ${p ? p.label : 'Tiết #' + s.periodId}`
}

const canSubmit = computed(
  () =>
    modal.form.teacherId &&
    modal.form.subjectId &&
    modal.form.schoolId &&
    modal.form.classId &&
    modal.form.startDate &&
    modal.form.slots.length > 0,
)

async function submit() {
  if (!canSubmit.value) {
    modal.error = 'Vui lòng điền đủ GV, môn, trường, lớp, ngày bắt đầu và ít nhất 1 tiết.'
    return
  }
  modal.saving = true
  modal.error = ''
  try {
    await assignmentApi.create({
      teacherId: Number(modal.form.teacherId),
      subjectId: Number(modal.form.subjectId),
      schoolId: Number(modal.form.schoolId),
      classId: Number(modal.form.classId),
      startDate: modal.form.startDate,
      endDate: modal.form.endDate || null,
      slots: modal.form.slots,
    })
    modal.open = false
    load()
  } catch (e) {
    modal.error = e.response?.data?.message ?? 'Tạo phân công thất bại'
  } finally {
    modal.saving = false
  }
}

async function confirmCancel() {
  if (!cancelTarget.value) return
  try {
    await assignmentApi.cancel(cancelTarget.value.id)
    cancelTarget.value = null
    load()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Hủy thất bại')
    cancelTarget.value = null
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="title">Phân công giảng dạy</h2>
        <p class="subtitle">
          Gán giáo viên ↔ trường ↔ lớp ↔ môn; hệ thống tự sinh buổi dạy hằng tuần.
        </p>
      </div>
      <button class="btn btn-primary" @click="openCreate">+ Tạo phân công</button>
    </div>

    <div class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>Giáo viên</th>
            <th>Trường</th>
            <th>Lớp</th>
            <th>Môn</th>
            <th>Giai đoạn</th>
            <th>Tiết / tuần</th>
            <th>Trạng thái</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="8" class="text-center text-muted">Đang tải…</td>
          </tr>
          <tr v-else-if="!items.length">
            <td colspan="8" class="text-center text-muted">Chưa có phân công nào</td>
          </tr>
          <tr v-for="a in items" :key="a.id">
            <td class="font-medium">{{ a.teacherName }}</td>
            <td>{{ a.schoolName }}</td>
            <td>{{ a.className ?? '—' }}</td>
            <td>{{ a.subjectName }}</td>
            <td class="text-muted small">
              {{ a.startDate }} → {{ a.endDate ?? 'không giới hạn' }}
            </td>
            <td>
              <span v-for="s in a.slots" :key="s.id" class="chip"
                >{{ s.dayOfWeekLabel }} · T{{ s.periodNumber }}</span
              >
              <span v-if="!a.slots?.length" class="text-muted">—</span>
            </td>
            <td>
              <span
                class="badge"
                :class="{
                  'badge-green': a.status === 'ACTIVE',
                  'badge-gray': a.status === 'CANCELLED',
                  'badge-blue': a.status === 'COMPLETED',
                }"
                >{{ STATUS_LABEL[a.status] ?? a.status }}</span
              >
            </td>
            <td class="actions">
              <button
                v-if="a.status === 'ACTIVE'"
                class="btn btn-sm btn-danger"
                @click="cancelTarget = a"
              >
                Hủy
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Modal tạo phân công -->
    <div v-if="modal.open" class="modal-overlay" @click.self="modal.open = false">
      <div class="modal-box modal-lg">
        <h3>Tạo phân công</h3>

        <div class="grid2">
          <div class="form-group">
            <label>Giáo viên *</label>
            <select v-model="modal.form.teacherId">
              <option value="">-- Chọn giáo viên --</option>
              <option v-for="t in options.teachers" :key="t.id" :value="t.id">{{ t.name }}</option>
            </select>
          </div>
          <div class="form-group">
            <label>Môn học *</label>
            <select v-model="modal.form.subjectId">
              <option value="">-- Chọn môn --</option>
              <option v-for="s in options.subjects" :key="s.id" :value="s.id">{{ s.name }}</option>
            </select>
          </div>
          <div class="form-group">
            <label>Trường *</label>
            <select v-model="modal.form.schoolId" @change="onSchoolChange">
              <option value="">-- Chọn trường --</option>
              <option v-for="s in options.schools" :key="s.id" :value="s.id">{{ s.name }}</option>
            </select>
          </div>
          <div class="form-group">
            <label>Lớp *</label>
            <select v-model="modal.form.classId" :disabled="!scoped.classes.length">
              <option value="">
                {{ scoped.classes.length ? '-- Chọn lớp --' : 'Chọn trường trước' }}
              </option>
              <option v-for="c in scoped.classes" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </div>
          <div class="form-group">
            <label>Ngày bắt đầu *</label>
            <input type="date" v-model="modal.form.startDate" />
          </div>
          <div class="form-group">
            <label>Ngày kết thúc</label>
            <input type="date" v-model="modal.form.endDate" />
            <small>Bỏ trống = sinh lịch 8 tuần từ ngày bắt đầu.</small>
          </div>
        </div>

        <!-- Slots -->
        <div class="slots-block">
          <label class="slots-label">Các tiết dạy trong tuần *</label>
          <div class="slot-add">
            <select v-model="modal.slotDraft.dayOfWeek">
              <option v-for="d in DAYS" :key="d.code" :value="d.code">{{ d.label }}</option>
            </select>
            <select v-model="modal.slotDraft.periodId" :disabled="!scoped.periods.length">
              <option value="">
                {{ scoped.periods.length ? '-- Chọn tiết --' : 'Chọn trường trước' }}
              </option>
              <option v-for="p in scoped.periods" :key="p.id" :value="p.id">{{ p.label }}</option>
            </select>
            <button
              class="btn btn-outline btn-sm"
              type="button"
              :disabled="!modal.slotDraft.periodId"
              @click="addSlot"
            >
              + Thêm tiết
            </button>
          </div>
          <div class="chips">
            <span v-for="(s, i) in modal.form.slots" :key="i" class="chip chip-lg">
              {{ slotLabel(s) }}
              <button class="chip-x" type="button" @click="removeSlot(i)">×</button>
            </span>
            <span v-if="!modal.form.slots.length" class="text-muted small"
              >Chưa thêm tiết nào.</span
            >
          </div>
        </div>

        <p v-if="modal.error" class="error-msg">{{ modal.error }}</p>

        <div class="modal-actions">
          <button class="btn btn-outline" @click="modal.open = false">Hủy</button>
          <button class="btn btn-primary" :disabled="modal.saving || !canSubmit" @click="submit">
            {{ modal.saving ? 'Đang lưu…' : 'Tạo phân công' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Confirm hủy -->
    <div v-if="cancelTarget" class="modal-overlay" @click.self="cancelTarget = null">
      <div class="modal-box modal-sm">
        <h3>Xác nhận hủy</h3>
        <p>
          Hủy phân công của <strong>{{ cancelTarget.teacherName }}</strong> tại
          {{ cancelTarget.schoolName }}? Các buổi chưa diễn ra sẽ bị hủy theo.
        </p>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="cancelTarget = null">Không</button>
          <button class="btn btn-danger" @click="confirmCancel">Hủy phân công</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.slots-block {
  margin-top: 0.5rem;
  border-top: 1px dashed #e5e7eb;
  padding-top: 0.9rem;
}
.slots-label {
  display: block;
  font-size: 0.85rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
  color: #374151;
}
.slot-add {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-bottom: 0.6rem;
}
.slot-add select {
  padding: 0.4rem 0.6rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.88rem;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #eef2ff;
  color: #3730a3;
  border-radius: 9999px;
  padding: 0.12rem 0.55rem;
  font-size: 0.74rem;
  font-weight: 600;
  margin: 0 2px 2px 0;
}
.chip-lg {
  font-size: 0.8rem;
  padding: 0.2rem 0.7rem;
}
.chip-x {
  border: none;
  background: transparent;
  color: #6366f1;
  cursor: pointer;
  font-size: 0.95rem;
  line-height: 1;
}
.badge-blue {
  background: #dbeafe;
  color: #1e40af;
}
</style>
