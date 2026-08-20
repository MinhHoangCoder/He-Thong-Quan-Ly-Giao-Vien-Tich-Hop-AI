<script setup>
/**
 * Tạo / sửa PHÂN CÔNG GIẢNG DẠY — wizard 3 bước trên trang riêng.
 *
 * Để là TRANG chứ không phải modal vì bước xếp tiết là lưới 10 tiết × 7 thứ cho TỪNG trường.
 *
 * MÔ HÌNH DỮ LIỆU (V27): một phiếu = 1 giáo viên + 1 MÔN + nhiều TRƯỜNG/LỚP/TIẾT. Mỗi ô lịch
 * tự mang trường của nó, nên tiết 1 ở trường A rồi tiết 2 sang trường B vẫn nằm trong MỘT phiếu.
 *
 * CÁCH XẾP TIẾT — "cây cọ": mỗi khối trường có ô "Lớp đang xếp", bấm ô lưới là gán lớp đó vào
 * tiết đó, đổi lớp rồi bấm tiếp.
 *
 * Ô lưới bị KHÓA theo đúng hai luật backend còn chặn:
 *   - giáo viên đã có lịch đè giờ (kể cả TRƯỜNG KHÁC) → so GIỜ THẬT, không so periodId
 *   - lớp đang xếp đã có giáo viên khác dạy khung giờ đó
 * KHÔNG còn luật "hai trường phải cách nhau 60 phút" — đã gỡ.
 */
import { computed, reactive, ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { assignmentApi } from '@/api/assignments'
import { periodApi } from '@/api/periods'
import { tietLabel } from '@/utils/period'
import SearchSelect from '@/components/ui/SearchSelect.vue'
import DateField from '@/components/ui/DateField.vue'

const route = useRoute()
const router = useRouter()

/** id phiếu đang sửa (undefined = tạo mới). */
const editId = computed(() => (route.params.id ? Number(route.params.id) : null))
const isEdit = computed(() => editId.value != null)

const DAYS = [
  { code: 'MON', label: 'Thứ 2', short: 'T2' },
  { code: 'TUE', label: 'Thứ 3', short: 'T3' },
  { code: 'WED', label: 'Thứ 4', short: 'T4' },
  { code: 'THU', label: 'Thứ 5', short: 'T5' },
  { code: 'FRI', label: 'Thứ 6', short: 'T6' },
  { code: 'SAT', label: 'Thứ 7', short: 'T7' },
  // KHÔNG có Chủ nhật: trung tâm không xếp lịch ngày này. Backend vẫn chấp nhận mã SUN ở tầng
  // dữ liệu để không làm hỏng phiếu cũ, nhưng không còn đường tạo mới từ giao diện.
]

const STEPS = [
  { n: 1, label: 'Thông tin chung' },
  { n: 2, label: 'Xếp tiết dạy' },
  { n: 3, label: 'Xem lại & gửi' },
]

// Hôm nay theo GIỜ ĐỊA PHƯƠNG. Tránh toISOString() vì nó quy về UTC → ở VN (UTC+7) lúc
// rạng sáng sẽ trả nhầm về ngày hôm qua.
const isoToday = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const step = ref(1)
const loading = ref(true)
const saving = ref(false)
const error = ref('')

const options = reactive({ teachers: [], subjects: [], schools: [] })

const form = reactive({
  teacherId: '',
  subjectId: '',
  startDate: isoToday(),
  endDate: '',
  /** [{ schoolId, dayOfWeek, periodId, classId }] — phẳng, đúng dạng API nhận. */
  slots: [],
})

/** Mỗi khối = một trường đang xếp: { key, schoolId, brushClassId, classes, periods, classBusy, loading } */
const blocks = ref([])
let blockSeq = 0

/** Giờ bận của GV trên MỌI trường (nguồn khóa ô "GV bận"). */
const teacherBusy = ref([])

/** Ngày bắt đầu ĐANG LƯU của phiếu đang sửa — mốc dưới cho luật "không lùi về quá khứ". */
const originalStartDate = ref('')

/* ══════════════════════ Nạp dữ liệu ══════════════════════ */

async function loadOptions() {
  const { data } = await assignmentApi.options()
  options.teachers = data.teachers ?? []
  options.subjects = data.subjects ?? []
  options.schools = data.schools ?? []
}

async function loadTeacherBusy() {
  teacherBusy.value = []
  if (!form.teacherId) return
  try {
    // Lấy trọn rồi tự lọc theo giai đoạn đang nhập — đổi ngày không phải gọi lại API.
    const { data } = await assignmentApi.teacherBusy({ teacherId: Number(form.teacherId) })
    teacherBusy.value = data ?? []
  } catch {
    teacherBusy.value = []
  }
}

/** Nạp lớp + khung tiết + bảng "lớp bận" cho một khối trường. */
async function loadBlock(block) {
  block.classes = []
  block.periods = []
  block.classBusy = []
  if (!block.schoolId) return
  block.loading = true
  try {
    const [opt, busy] = await Promise.all([
      assignmentApi.schoolOptions(block.schoolId),
      assignmentApi.classBusy({ schoolId: block.schoolId }),
    ])
    block.classes = opt.data.classes ?? []
    block.periods = opt.data.periods ?? []
    block.classBusy = busy.data ?? []
    if (!block.brushClassId && block.classes.length) block.brushClassId = block.classes[0].id
  } catch (e) {
    error.value = 'Không tải được dữ liệu trường: ' + msgOf(e)
  } finally {
    block.loading = false
  }
}

function msgOf(e) {
  return e.response?.data?.message ?? e.message ?? 'lỗi không rõ'
}

onMounted(async () => {
  loading.value = true
  try {
    await loadOptions()
    if (isEdit.value) await loadForEdit()
    else addBlock()
  } catch (e) {
    error.value = 'Không tải được dữ liệu: ' + msgOf(e)
  } finally {
    loading.value = false
  }
})

/** Nạp phiếu đang sửa vào form (mỗi trường của phiếu thành một khối lưới). */
async function loadForEdit() {
  const { data } = await assignmentApi.detail(editId.value)
  form.teacherId = data.teacherId
  form.subjectId = data.subjectId
  form.startDate = data.startDate
  originalStartDate.value = data.startDate
  form.endDate = data.endDate ?? ''
  form.slots = (data.slots ?? []).map((s) => ({
    schoolId: Number(s.schoolId),
    dayOfWeek: s.dayOfWeek,
    periodId: Number(s.periodId),
    classId: Number(s.classId),
  }))
  const schoolIds = [...new Set(form.slots.map((s) => s.schoolId))]
  blocks.value = schoolIds.map((id) => makeBlock(id))
  await Promise.all([loadTeacherBusy(), ...blocks.value.map(loadBlock)])
}

function makeBlock(schoolId = '') {
  return {
    key: ++blockSeq,
    schoolId,
    brushClassId: '',
    classes: [],
    periods: [],
    classBusy: [],
    loading: false,
    applying: false,
    frameNote: '',
  }
}

function addBlock() {
  blocks.value.push(makeBlock())
}

function removeBlock(block) {
  // Bỏ khối là bỏ luôn các tiết đã xếp ở trường đó — hỏi lại nếu có tiết, đừng xóa lặng lẽ.
  const mine = form.slots.filter((s) => s.schoolId === block.schoolId)
  if (mine.length && !window.confirm(`Bỏ trường này khỏi phiếu? ${mine.length} tiết đã xếp sẽ mất.`))
    return
  form.slots = form.slots.filter((s) => s.schoolId !== block.schoolId)
  blocks.value = blocks.value.filter((b) => b.key !== block.key)
}

async function onBlockSchoolChange(block, prevSchoolId) {
  // Đổi trường thì khung tiết và lớp đều khác → bỏ các tiết đã xếp của trường cũ.
  if (prevSchoolId) form.slots = form.slots.filter((s) => s.schoolId !== prevSchoolId)
  block.brushClassId = ''
  await loadBlock(block)
}

// Đổi giáo viên → nạp lại bảng "giờ bận". Đổi giai đoạn thì KHÔNG cần gọi lại API (đã lấy
// trọn từ đầu), các computed bên dưới tự lọc lại theo ngày.
watch(() => form.teacherId, loadTeacherBusy)

/* ══════════════════════ Luật khóa ô ══════════════════════ */

/** Hai giai đoạn [aStart,aEnd] và [bStart,bEnd] có chồng nhau không (end rỗng = vô thời hạn). */
function rangesOverlap(aStart, aEnd, bStart, bEnd) {
  if (aEnd && bStart && aEnd < bStart) return false
  if (bEnd && aStart && bEnd < aStart) return false
  return true
}

/** Hai khoảng giờ trong ngày có giao nhau không ("07:00:00" so chuỗi được vì cùng định dạng). */
function timesOverlap(aStart, aEnd, bStart, bEnd) {
  if (!aStart || !aEnd || !bStart || !bEnd) return false
  return aStart < bEnd && bStart < aEnd
}

/** Ô lịch bận của GV, đã lọc theo giai đoạn đang nhập và bỏ qua chính phiếu đang sửa. */
const busyByDay = computed(() => {
  const map = {}
  for (const b of teacherBusy.value) {
    if (isEdit.value && b.assignmentId === editId.value) continue
    if (form.startDate && !rangesOverlap(form.startDate, form.endDate, b.startDate, b.endDate))
      continue
    ;(map[b.dayOfWeek] ??= []).push(b)
  }
  return map
})

/** Tiết đã chọn TRONG FORM này, tra nhanh theo "thứ|periodId". */
const slotByKey = computed(() => {
  const map = {}
  for (const s of form.slots) map[`${s.dayOfWeek}|${s.periodId}`] = s
  return map
})

/** Mọi tiết đã chọn trong form, kèm giờ thật — để chặn tự đè giờ giữa hai khối trường. */
const chosenWithTime = computed(() => {
  const out = []
  for (const s of form.slots) {
    const block = blocks.value.find((b) => b.schoolId === s.schoolId)
    const p = block?.periods.find((x) => Number(x.id) === Number(s.periodId))
    if (p) out.push({ ...s, startTime: p.startTime, endTime: p.endTime })
  }
  return out
})

/**
 * Vì sao một ô (thứ × tiết) không dùng được, với LỚP ĐANG XẾP của khối.
 * @returns null nếu chọn được, ngược lại { kind, label }
 */
function cellBlock(block, day, period) {
  // 1) Chính ô này đã được chọn trong form → không phải "khóa", trả riêng ở cellState.
  // 2) Giáo viên bận ở bất kỳ trường nào (so GIỜ THẬT).
  const busy = (busyByDay.value[day] ?? []).find((b) =>
    timesOverlap(period.startTime, period.endTime, b.startTime, b.endTime),
  )
  if (busy) {
    return {
      kind: 'teacher',
      label: [busy.schoolName, busy.className].filter(Boolean).join(' · ') || 'trường khác',
    }
  }
  // 3) Tiết khác TRONG FORM đè giờ (khối trường khác, hoặc cùng trường khác periodId).
  const selfClash = chosenWithTime.value.find(
    (s) =>
      s.dayOfWeek === day &&
      Number(s.periodId) !== Number(period.id) &&
      timesOverlap(period.startTime, period.endTime, s.startTime, s.endTime),
  )
  if (selfClash) {
    const b = blocks.value.find((x) => x.schoolId === selfClash.schoolId)
    const cls = b?.classes.find((c) => Number(c.id) === Number(selfClash.classId))
    return {
      kind: 'self',
      label: [schoolName(selfClash.schoolId), cls?.name].filter(Boolean).join(' · '),
    }
  }
  // 4) LỚP đang xếp đã có giáo viên KHÁC dạy khung giờ đó.
  if (block.brushClassId) {
    const taken = (block.classBusy ?? []).find(
      (c) =>
        Number(c.classId) === Number(block.brushClassId) &&
        c.dayOfWeek === day &&
        (!isEdit.value || c.assignmentId !== editId.value) &&
        rangesOverlap(form.startDate, form.endDate, c.startDate, c.endDate) &&
        timesOverlap(period.startTime, period.endTime, c.startTime, c.endTime),
    )
    if (taken) {
      return {
        kind: 'class',
        label: taken.teacherName + (taken.pending ? ' (đang chờ xác nhận)' : ''),
      }
    }
  }
  return null
}

/** Trạng thái hiển thị của một ô: 'chosen' | 'teacher' | 'self' | 'class' | 'free'. */
function buildCellState(block, day, period) {
  const chosen = slotByKey.value[`${day}|${period.id}`]
  const st = chosen
    ? { kind: 'chosen', slot: chosen }
    : (() => {
        const blocked = cellBlock(block, day, period)
        return blocked ? { ...blocked, blocked: true } : { kind: 'free' }
      })()

  const dayLabel = DAYS.find((d) => d.code === day)?.label ?? day
  const time = `${(period.startTime ?? '').slice(0, 5)}–${(period.endTime ?? '').slice(0, 5)}`
  const head = `${dayLabel} · ${tietLabel(period.periodNumber, period.sessionType, period.indexInSession)} · ${time}`
  if (st.kind === 'chosen') {
    st.text = className(block, st.slot.classId)
    st.title = `${head}\nĐang xếp: ${st.text} — bấm để bỏ`
  } else if (st.kind === 'teacher') {
    st.text = 'bận'
    st.title = `${head}\nGiáo viên đang bận: ${st.label}`
  } else if (st.kind === 'self') {
    st.text = 'bận'
    st.title = `${head}\nĐè giờ với tiết bạn vừa xếp: ${st.label}`
  } else if (st.kind === 'class') {
    st.text = 'lớp bận'
    st.title = `${head}\nLớp này đã có ${st.label} dạy`
  } else {
    st.text = ''
    st.title = `${head}\nBấm để xếp lớp ${className(block, block.brushClassId) || '(chưa chọn lớp)'}`
  }
  return st
}

/**
 * Trạng thái của MỌI ô trên MỌI lưới, tính một lượt.
 *
 * <p>Gọi hàm dựng trạng thái ngay trong v-bind thì mỗi ô bị tính lại 5 lần (class, disabled,
 * title, nội dung…), nhân 10 tiết × 7 thứ × số trường là vài nghìn lượt quét mảng cho một lần
 * gõ phím. Gom vào computed để mỗi ô tính đúng một lần.
 */
const cellStates = computed(() => {
  const map = new Map()
  for (const block of blocks.value) {
    for (const p of block.periods) {
      for (const d of DAYS) {
        map.set(`${block.key}|${d.code}|${p.id}`, buildCellState(block, d.code, p))
      }
    }
  }
  return map
})

function cellAt(block, day, period) {
  return cellStates.value.get(`${block.key}|${day}|${period.id}`) ?? { kind: 'free', text: '' }
}

/* ══════════════════════ Thao tác trên lưới ══════════════════════ */

function toggleCell(block, day, period) {
  const key = `${day}|${period.id}`
  const existing = slotByKey.value[key]
  if (existing) {
    // Bấm lại ô đã chọn: đổi sang lớp đang xếp nếu khác, còn trùng lớp thì bỏ chọn.
    if (block.brushClassId && Number(existing.classId) !== Number(block.brushClassId)) {
      // Vẫn phải soát: lớp MỚI có thể đã có giáo viên khác dạy đúng khung giờ này.
      const blocked = cellBlock(block, day, period)
      if (blocked) {
        error.value =
          blocked.kind === 'class'
            ? `Không đổi được — lớp này đã có ${blocked.label} dạy khung giờ đó.`
            : 'Không đổi được — khung giờ này đã vướng lịch khác.'
        return
      }
      error.value = ''
      existing.classId = Number(block.brushClassId)
      return
    }
    form.slots = form.slots.filter((s) => s !== existing)
    return
  }
  if (!block.brushClassId) {
    error.value = 'Hãy chọn "Lớp đang xếp" trước khi bấm vào lưới.'
    return
  }
  if (cellBlock(block, day, period)) return // ô bị khóa
  error.value = ''
  form.slots.push({
    schoolId: block.schoolId,
    dayOfWeek: day,
    periodId: Number(period.id),
    classId: Number(block.brushClassId),
  })
}

function clearBlockSlots(block) {
  form.slots = form.slots.filter((s) => s.schoolId !== block.schoolId)
}

function slotsOfBlock(block) {
  return form.slots.filter((s) => s.schoolId === block.schoolId)
}

/* ══════════════════════ Nhãn ══════════════════════ */

function schoolName(id) {
  return options.schools.find((s) => Number(s.id) === Number(id))?.name ?? `Trường #${id}`
}

function className(block, classId) {
  if (!classId) return ''
  return block?.classes.find((c) => Number(c.id) === Number(classId))?.name ?? `Lớp #${classId}`
}

function teacherLabel(t) {
  const bits = []
  if (t.weeklyPeriods) bits.push(`${t.weeklyPeriods} tiết/tuần`)
  if (t.schoolCount) bits.push(`${t.schoolCount} trường`)
  return bits.join(' · ') || 'Chưa có lịch dạy'
}

/**
 * Danh sách giáo viên ĐÃ LỌC theo môn đang chọn (bảng TeacherSubject).
 *
 * Trung tâm có 90 giáo viên và 23 môn nên không lọc là dễ phân công sai người.
 *
 * KHÔNG lọc cứng: giáo viên chưa khai môn nào (subjectIds rỗng) vẫn giữ lại — dữ liệu thiếu
 * không nên chặn việc. Chỉ ẩn người đã khai môn mà không có môn đang chọn.
 */
const teachersForSubject = computed(() => {
  const sid = Number(form.subjectId)
  if (!sid) return options.teachers
  return options.teachers.filter(
    (t) => !t.subjectIds?.length || t.subjectIds.includes(sid),
  )
})

/** Số người bị ẩn vì không dạy được môn đang chọn — nói ra để không ai tưởng mất dữ liệu. */
const teachersHidden = computed(() => options.teachers.length - teachersForSubject.value.length)

/**
 * Trường chưa có khung tiết VÀ chưa có lớp nào thì KHÓA hẳn: không suy được cấp học nên cũng
 * không áp được khung chuẩn, chọn vào chỉ là ngõ cụt. Trường thiếu mỗi khung tiết thì vẫn cho
 * chọn — vào trong có nút áp khung tiết chuẩn để gỡ tại chỗ.
 */
function schoolBlockReason(s) {
  if (s.periodCount === 0 && s.classCount === 0) {
    return 'Chưa có lớp và chưa có khung tiết — hãy thêm lớp cho trường trước'
  }
  return ''
}

/** Áp bộ khung tiết chuẩn cho trường của khối đang mở, rồi nạp lại lưới. */
async function applyStandardFrame(block) {
  if (block.applying) return
  block.applying = true
  error.value = ''
  try {
    const { data } = await periodApi.applyStandard(block.schoolId)
    await loadBlock(block)
    block.frameNote = `Đã tạo ${data.created} tiết theo khung ${data.level} (mỗi tiết ${data.periodMinutes} phút).`
  } catch (e) {
    error.value = msgOf(e)
  } finally {
    block.applying = false
  }
}

/** Trường đã dùng ở khối khác thì ẩn khỏi danh sách chọn — một trường một khối. */
function schoolOptionsFor(block) {
  const used = blocks.value.filter((b) => b.key !== block.key).map((b) => Number(b.schoolId))
  return options.schools.filter((s) => !used.includes(Number(s.id)))
}

/* ══════════════════════ Bước & gửi ══════════════════════ */

/**
 * Ngày sớm nhất được chọn làm ngày bắt đầu.
 *
 * <p>Tạo mới: không được lùi về quá khứ — buổi dạy sinh cho ngày đã trôi qua thì giáo viên
 * không thể chấm công, mà chúng vẫn nằm trong lịch và bị đếm là vắng.
 *
 * <p>Sửa phiếu cũ: lấy mốc là ngày ĐANG LƯU của phiếu, để phiếu tạo hôm qua vẫn sửa được
 * (đổi giáo viên gấp) mà không bị chính luật này khóa cứng. Khớp đúng luật backend.
 */
const minStartDate = computed(() => {
  if (!isEdit.value) return isoToday()
  return originalStartDate.value && originalStartDate.value < isoToday()
    ? originalStartDate.value
    : isoToday()
})

const startDateInvalid = computed(
  () => !!form.startDate && form.startDate < minStartDate.value,
)

const step1Valid = computed(
  () => form.teacherId && form.subjectId && form.startDate && !startDateInvalid.value,
)
const step2Valid = computed(() => form.slots.length > 0)

const dateRangeInvalid = computed(
  () => !!form.endDate && !!form.startDate && form.endDate < form.startDate,
)

function goStep(n) {
  error.value = ''
  // Nêu ĐÚNG thứ đang thiếu: gộp hết vào một câu "hãy điền đủ" thì người dùng phải tự dò.
  if (n >= 2 && startDateInvalid.value) {
    error.value = 'Ngày bắt đầu không được là ngày trong quá khứ.'
    return
  }
  if (n >= 2 && dateRangeInvalid.value) {
    error.value = 'Ngày kết thúc phải sau ngày bắt đầu.'
    return
  }
  if (n >= 2 && !step1Valid.value) {
    error.value = 'Hãy chọn giáo viên, môn học và ngày bắt đầu.'
    return
  }
  if (n >= 3 && !step2Valid.value) {
    error.value = 'Hãy xếp ít nhất một tiết dạy.'
    return
  }
  step.value = n
}

/** Tiết đã xếp, gom theo trường → thứ, để bước 3 xem lại. */
const review = computed(() => {
  const bySchool = new Map()
  for (const s of form.slots) {
    if (!bySchool.has(s.schoolId)) bySchool.set(s.schoolId, new Map())
    const byDay = bySchool.get(s.schoolId)
    if (!byDay.has(s.dayOfWeek)) byDay.set(s.dayOfWeek, [])
    byDay.get(s.dayOfWeek).push(s)
  }
  return [...bySchool.entries()].map(([schoolId, byDay]) => {
    const block = blocks.value.find((b) => b.schoolId === schoolId)
    const days = DAYS.filter((d) => byDay.has(d.code)).map((d) => {
      const list = [...byDay.get(d.code)]
      list.sort((a, b) => periodNumberOf(block, a.periodId) - periodNumberOf(block, b.periodId))
      return {
        code: d.code,
        label: d.label,
        items: list.map((s) => {
          const p = block?.periods.find((x) => Number(x.id) === Number(s.periodId))
          return {
            key: `${s.dayOfWeek}|${s.periodId}`,
            tiet: p ? tietLabel(p.periodNumber, p.sessionType, p.indexInSession) : `#${s.periodId}`,
            time: p ? `${p.startTime.slice(0, 5)}–${p.endTime.slice(0, 5)}` : '',
            className: className(block, s.classId),
          }
        }),
      }
    })
    return { schoolId, schoolName: schoolName(schoolId), days, count: form.slots.filter((s) => s.schoolId === schoolId).length }
  })
})

function periodNumberOf(block, periodId) {
  return block?.periods.find((x) => Number(x.id) === Number(periodId))?.periodNumber ?? 0
}

const totalWeekly = computed(() => form.slots.length)

async function submit() {
  if (!step1Valid.value || !step2Valid.value) return
  saving.value = true
  error.value = ''
  try {
    const body = {
      teacherId: Number(form.teacherId),
      startDate: form.startDate,
      endDate: form.endDate || null,
      slots: form.slots.map((s) => ({
        schoolId: Number(s.schoolId),
        dayOfWeek: s.dayOfWeek,
        periodId: Number(s.periodId),
        classId: Number(s.classId),
      })),
    }
    if (isEdit.value) {
      await assignmentApi.update(editId.value, body)
    } else {
      await assignmentApi.create({
        ...body,
        subjectId: Number(form.subjectId),
        // Trường chính = trường của tiết đầu tiên; lớp cấp phiếu bỏ trống (lớp nằm ở từng tiết).
        schoolId: Number(form.slots[0].schoolId),
        classId: null,
      })
    }
    router.push({ name: 'assignments' })
  } catch (e) {
    error.value = msgOf(e)
    step.value = 3
  } finally {
    saving.value = false
  }
}

function cancel() {
  router.push({ name: 'assignments' })
}
</script>

<template>
  <div class="page afp">
    <div class="page-head">
      <div>
        <h2 class="title">{{ isEdit ? 'Sửa phân công' : 'Tạo phân công' }}</h2>
      </div>
      <button class="btn btn-outline" @click="cancel">← Quay lại danh sách</button>
    </div>

    <!-- Thanh bước -->
    <ol class="afp__steps">
      <li
        v-for="s in STEPS"
        :key="s.n"
        class="afp__step"
        :class="{ 'is-on': step === s.n, 'is-done': step > s.n }"
      >
        <button type="button" @click="goStep(s.n)">
          <span class="afp__stepn">{{ step > s.n ? '✓' : s.n }}</span>
          <span>{{ s.label }}</span>
        </button>
      </li>
    </ol>

    <p v-if="error" class="error-msg afp__error">{{ error }}</p>
    <div v-if="loading" class="card afp__loading">Đang tải…</div>

    <!-- ══════════ BƯỚC 1 ══════════ -->
    <div v-else-if="step === 1" class="card afp__card">
      <div class="grid2">
        <div class="form-group">
          <label>Môn học *</label>
          <SearchSelect
            v-model="form.subjectId"
            :options="options.subjects"
            :search-extra="(s) => s.code"
            :disabled="isEdit"
            placeholder="-- Chọn môn học --"
            search-placeholder=""
          >
            <template #option="{ item }">
              <div class="opt">
                <span class="opt__name">{{ item.name }}</span>
                <span class="opt__meta">{{ item.categoryName ?? 'Chưa xếp nhóm' }}</span>
              </div>
            </template>
          </SearchSelect>
          <small v-if="isEdit">
            Môn không đổi được khi sửa. Muốn đổi môn thì hủy phiếu và tạo phiếu mới.
          </small>
        </div>

        <div class="form-group">
          <label>Giáo viên *</label>
          <SearchSelect
            v-model="form.teacherId"
            :options="teachersForSubject"
            :search-extra="(t) => t.code"
            placeholder="-- Chọn giáo viên --"
            search-placeholder=""
          >
            <!-- Chỉ hiện TÊN. Mã (tên đăng nhập) vẫn tìm được qua search-extra ở trên, nhưng
                 không bày ra: người xếp lịch nhớ tên chứ không nhớ mã, in ra chỉ thêm nhiễu. -->
            <template #option="{ item }">
              <div class="opt">
                <span class="opt__name">{{ item.name }}</span>
                <span class="opt__meta">{{ teacherLabel(item) }}</span>
              </div>
            </template>
          </SearchSelect>
          <small v-if="form.subjectId && teachersHidden > 0" class="afp__hint">
            Đang lọc theo môn đã chọn — ẩn {{ teachersHidden }} giáo viên không dạy môn này.
          </small>
        </div>

        <div class="form-group">
          <label>Ngày bắt đầu *</label>
          <DateField v-model="form.startDate" :min="minStartDate" :invalid="startDateInvalid" />
          <small v-if="startDateInvalid" class="afp__fielderr">
            Ngày bắt đầu không được là ngày trong quá khứ.
          </small>
        </div>

        <div class="form-group">
          <label>Ngày kết thúc</label>
          <DateField v-model="form.endDate" :min="form.startDate" :invalid="dateRangeInvalid" />
          <small v-if="dateRangeInvalid" class="afp__fielderr">
            Ngày kết thúc phải sau ngày bắt đầu.
          </small>
          <small v-else>Bỏ trống = sinh lịch 8 tuần kể từ ngày bắt đầu.</small>
        </div>
      </div>

      <div class="modal-actions">
        <button class="btn btn-outline" @click="cancel">Hủy</button>
        <button class="btn btn-primary" :disabled="!step1Valid" @click="goStep(2)">
          Tiếp tục →
        </button>
      </div>
    </div>

    <!-- ══════════ BƯỚC 2 ══════════ -->
    <div v-else-if="step === 2" class="afp__step2">
      <div class="afp__legend card">
        <span class="lg"><i class="cell cell--free"></i> Trống</span>
        <span class="lg"><i class="cell cell--chosen"></i> Đang xếp</span>
        <span class="lg"><i class="cell cell--teacher"></i> Giáo viên bận</span>
        <span class="lg"><i class="cell cell--class"></i> Lớp đã có người dạy</span>
        <span class="lg lg--total">Tổng: <b>{{ totalWeekly }}</b> tiết/tuần</span>
      </div>

      <div v-for="block in blocks" :key="block.key" class="card afp__block">
        <div class="afp__blockhead">
          <div class="form-group afp__schoolpick">
            <label>Trường *</label>
            <SearchSelect
              :model-value="block.schoolId"
              :options="schoolOptionsFor(block)"
              placeholder="-- Chọn trường --"
              search-placeholder="Tìm trường…"
              :option-disabled="schoolBlockReason"
              @update:model-value="
                (v) => {
                  const prev = block.schoolId
                  block.schoolId = v
                  onBlockSchoolChange(block, prev)
                }
              "
            >
              <template #option="{ item }">
                <div class="opt">
                  <span class="opt__name">{{ item.name }}</span>
                  <span class="opt__meta">
                    {{ item.level ?? 'Chưa rõ cấp' }} · {{ item.classCount }} lớp ·
                    {{ item.periodCount }} tiết/ngày
                  </span>
                </div>
              </template>
            </SearchSelect>
          </div>

          <div class="form-group afp__brush">
            <label>Lớp đang xếp</label>
            <SearchSelect
              v-model="block.brushClassId"
              :options="block.classes"
              placeholder="-- Chọn lớp --"
              search-placeholder="Tìm lớp…"
              empty-text="Trường này chưa có lớp"
              :disabled="!block.schoolId"
            />
          </div>

          <div class="afp__blockactions">
            <span class="afp__count">{{ slotsOfBlock(block).length }} tiết</span>
            <button
              v-if="slotsOfBlock(block).length"
              class="btn btn-sm btn-outline"
              @click="clearBlockSlots(block)"
            >
              Xóa tiết
            </button>
            <button
              v-if="blocks.length > 1"
              class="btn btn-sm btn-danger"
              @click="removeBlock(block)"
            >
              Bỏ trường
            </button>
          </div>
        </div>

        <p v-if="block.loading" class="text-muted small">Đang tải khung tiết…</p>
        <p v-else-if="!block.schoolId" class="text-muted small">
          Chọn một trường để hiện lưới thời khóa biểu.
        </p>
        <!-- Trường chưa có khung tiết: KHÔNG bỏ mặc người dùng ở ngõ cụt. Hệ thống chưa có màn
             quản lý khung tiết, nên đưa luôn nút áp bộ chuẩn theo cấp học ngay tại đây. -->
        <div v-else-if="!block.periods.length" class="afp__warn">
          <p>
            Trường này <b>chưa có khung tiết</b> nên chưa xếp lịch được. Áp bộ khung chuẩn theo
            cấp học của trường để bắt đầu.
          </p>
          <button class="btn btn-primary btn-sm" :disabled="block.applying" @click="applyStandardFrame(block)">
            {{ block.applying ? 'Đang áp…' : 'Áp khung tiết chuẩn' }}
          </button>
          <small>
            Tiểu học 10 tiết/ngày (mỗi tiết 35 phút) · THCS 9 tiết/ngày (mỗi tiết 45 phút) — vào
            học 07:00 và 14:00, ra chơi sau tiết 2 mỗi buổi.
          </small>
        </div>
        <div v-else class="afp__gridwrap">
          <p v-if="block.frameNote" class="afp__done">{{ block.frameNote }}</p>
          <table class="afp__grid">
            <thead>
              <tr>
                <th class="afp__rowhead">Tiết</th>
                <th v-for="d in DAYS" :key="d.code">
                  <span class="afp__dfull">{{ d.label }}</span>
                  <span class="afp__dshort">{{ d.short }}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in block.periods" :key="p.id">
                <th class="afp__rowhead">
                  <span class="afp__tiet">{{
                    tietLabel(p.periodNumber, p.sessionType, p.indexInSession)
                  }}</span>
                  <span class="afp__time"
                    >{{ p.startTime.slice(0, 5) }}–{{ p.endTime.slice(0, 5) }}</span
                  >
                </th>
                <td v-for="d in DAYS" :key="d.code">
                  <button
                    type="button"
                    class="cell"
                    :class="`cell--${cellAt(block, d.code, p).kind}`"
                    :disabled="cellAt(block, d.code, p).blocked"
                    :title="cellAt(block, d.code, p).title"
                    @click="toggleCell(block, d.code, p)"
                  >
                    {{ cellAt(block, d.code, p).text }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <button class="btn btn-outline afp__addschool" @click="addBlock">
        + Thêm trường vào phiếu này
      </button>

      <div class="modal-actions">
        <button class="btn btn-outline" @click="goStep(1)">← Quay lại</button>
        <button class="btn btn-primary" :disabled="!step2Valid" @click="goStep(3)">
          Xem lại →
        </button>
      </div>
    </div>

    <!-- ══════════ BƯỚC 3 ══════════ -->
    <div v-else class="card afp__card">
      <dl class="afp__summary">
        <div>
          <dt>Giáo viên</dt>
          <dd>{{ options.teachers.find((t) => t.id === form.teacherId)?.name ?? '—' }}</dd>
        </div>
        <div>
          <dt>Môn học</dt>
          <dd>{{ options.subjects.find((s) => s.id === form.subjectId)?.name ?? '—' }}</dd>
        </div>
        <div>
          <dt>Giai đoạn</dt>
          <dd>{{ form.startDate }} → {{ form.endDate || 'không giới hạn' }}</dd>
        </div>
        <div>
          <dt>Tổng</dt>
          <dd>{{ totalWeekly }} tiết/tuần · {{ review.length }} trường</dd>
        </div>
      </dl>

      <div v-for="r in review" :key="r.schoolId" class="afp__rev">
        <h4>{{ r.schoolName }} <span class="text-muted small">· {{ r.count }} tiết</span></h4>
        <div v-for="d in r.days" :key="d.code" class="afp__revday">
          <span class="afp__revdaylabel">{{ d.label }}</span>
          <span v-for="it in d.items" :key="it.key" class="chip">
            {{ it.tiet }} · {{ it.className }}
            <span class="text-muted">{{ it.time }}</span>
          </span>
        </div>
      </div>

      <p class="afp__note">
        Sau khi gửi, phiếu ở trạng thái <b>Chờ xác nhận</b> — giáo viên nhận lời mời qua chuông
        thông báo và có 48 giờ để trả lời. Lịch chỉ có hiệu lực khi được xác nhận hoặc admin ép
        duyệt.
      </p>

      <div class="modal-actions">
        <button class="btn btn-outline" @click="goStep(2)">← Sửa tiết dạy</button>
        <button class="btn btn-primary" :disabled="saving" @click="submit">
          {{ saving ? 'Đang gửi…' : isEdit ? 'Lưu & gửi lại lời mời' : 'Tạo phân công' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Bỏ trần 1200px của .page dùng chung: bước 2 là lưới thời khóa biểu (6 thứ × 10 tiết cho
   TỪNG trường) — càng rộng càng đỡ phải cuộn ngang. */
.afp {
  max-width: none;
}
/* Nhưng bước 1 và 3 chỉ là form/tóm tắt: kéo ô nhập dài cả màn hình thì mắt phải quét ngang
   quá xa mới nối được nhãn với ô. Giữ hẹp riêng hai bước đó. */
.afp__card,
.afp__loading {
  max-width: 1100px;
}
.afp__error {
  margin-bottom: 0.8rem;
}
.afp__fielderr {
  color: var(--c-danger);
}
.afp__hint {
  color: var(--c-text-muted);
  font-size: 12.5px;
}
.afp__loading,
.afp__card {
  padding: 1.2rem;
}

/* ── Thanh bước ── */
.afp__steps {
  display: flex;
  gap: 0.5rem;
  list-style: none;
  margin: 0 0 1rem;
  padding: 0;
  flex-wrap: wrap;
}
.afp__step button {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.45rem 0.9rem;
  border: 1px solid var(--c-border);
  border-radius: 9999px;
  background: var(--c-surface);
  color: var(--c-text-muted);
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
}
.afp__step.is-on button {
  border-color: var(--c-primary);
  color: var(--c-primary);
}
.afp__step.is-done button {
  color: var(--c-text);
}
.afp__stepn {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--c-surface-2);
  font-size: 0.75rem;
}
.afp__step.is-on .afp__stepn {
  background: var(--grad-primary);
  color: #fff;
}

/* ── Bước 2 ── */
.afp__step2 {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}
.afp__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 0.9rem;
  padding: 0.6rem 0.9rem;
  font-size: 0.8rem;
  color: var(--c-text-muted);
}
.lg {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}
.lg i.cell {
  width: 18px;
  height: 18px;
  min-width: 18px;
  padding: 0;
  pointer-events: none;
}
.lg--total {
  margin-left: auto;
  color: var(--c-text);
}
.afp__block {
  padding: 1rem;
}
.afp__blockhead {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  align-items: flex-start;
  margin-bottom: 0.8rem;
}
.afp__schoolpick,
.afp__brush {
  flex: 1 1 260px;
  margin-bottom: 0;
}
.afp__blockactions {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding-top: 1.5rem;
}
.afp__count {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--c-text-muted);
}
.afp__warn {
  padding: 0.7rem 0.9rem;
  border: 1px solid var(--c-border);
  border-left: 3px solid var(--c-danger);
  border-radius: 8px;
  background: var(--c-bg);
  font-size: 0.85rem;
}
.afp__warn p {
  margin: 0 0 0.6rem;
}
.afp__warn small {
  display: block;
  margin-top: 0.5rem;
  color: var(--c-text-muted);
  font-size: 0.76rem;
}
.afp__done {
  margin: 0 0 0.6rem;
  padding: 0.5rem 0.75rem;
  border-left: 3px solid #22c55e;
  border-radius: 6px;
  background: rgba(34, 197, 94, 0.1);
  font-size: 0.82rem;
}
.afp__addschool {
  align-self: flex-start;
}

/* ── Lưới thời khóa biểu ── */
.afp__gridwrap {
  overflow-x: auto;
}
.afp__grid {
  width: 100%;
  border-collapse: separate;
  border-spacing: 3px;
  min-width: 640px;
}
.afp__grid th {
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--c-text-muted);
  text-align: center;
  padding: 0.2rem;
}
.afp__rowhead {
  width: 108px;
  text-align: left !important;
  line-height: 1.25;
}
.afp__tiet {
  display: block;
  color: var(--c-text);
  font-size: 0.76rem;
}
.afp__time {
  display: block;
  font-size: 0.68rem;
  font-variant-numeric: tabular-nums;
}
.afp__dshort {
  display: none;
}

.cell {
  display: grid;
  place-items: center;
  width: 100%;
  min-width: 62px;
  height: 40px;
  padding: 0.1rem 0.2rem;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  background: var(--c-surface);
  color: var(--c-text);
  font-size: 0.76rem;
  font-weight: 600;
  cursor: pointer;
  transition: 0.12s;
}
.cell--free:hover {
  border-color: var(--c-primary);
  background: var(--c-bg);
}
.cell--chosen {
  border-color: var(--c-primary);
  background: var(--grad-primary);
  color: #fff;
}
.cell--teacher,
.cell--self {
  background: var(--c-surface-2);
  color: var(--c-text-muted);
  font-weight: 500;
  cursor: not-allowed;
}
.cell--class {
  background: repeating-linear-gradient(
    45deg,
    var(--c-surface-2),
    var(--c-surface-2) 5px,
    transparent 5px,
    transparent 10px
  );
  color: var(--c-text-muted);
  font-weight: 500;
  cursor: not-allowed;
}

/* ── Mục trong danh sách chọn ── */
.opt {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}
.opt__name {
  font-weight: 600;
}
.opt__meta {
  color: var(--c-text-muted);
  font-size: 0.76rem;
}

/* ── Bước 3 ── */
.afp__summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0.8rem;
  margin: 0 0 1rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--c-border-soft);
}
.afp__summary dt {
  font-size: 0.76rem;
  font-weight: 700;
  color: var(--c-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}
.afp__summary dd {
  margin: 0.2rem 0 0;
  font-size: 0.92rem;
  font-weight: 600;
}
.afp__rev {
  margin-bottom: 1rem;
}
.afp__rev h4 {
  margin: 0 0 0.5rem;
  font-size: 0.95rem;
}
.afp__revday {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
  margin-bottom: 0.4rem;
}
.afp__revdaylabel {
  min-width: 74px;
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--c-text-muted);
}
.chip {
  display: inline-flex;
  gap: 0.3rem;
  padding: 0.2rem 0.55rem;
  border: 1px solid var(--c-border);
  border-radius: 9999px;
  background: var(--c-surface);
  font-size: 0.78rem;
}
.afp__note {
  padding: 0.7rem 0.9rem;
  border-radius: 8px;
  background: var(--c-bg);
  font-size: 0.83rem;
  color: var(--c-text-muted);
}

@media (max-width: 720px) {
  .afp__dfull {
    display: none;
  }
  .afp__dshort {
    display: inline;
  }
  .afp__grid {
    min-width: 520px;
  }
  .afp__rowhead {
    width: 84px;
  }
  .cell {
    min-width: 48px;
    font-size: 0.7rem;
  }
}
</style>
