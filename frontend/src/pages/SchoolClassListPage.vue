<!-- src/pages/SchoolClassListPage.vue -->
<script setup>
/**
 * Trang "Lớp học" (khu admin): CRUD SchoolClass.
 *
 *  1) Cấp trường: TH / THCS (không còn THPT)
 *  2) Khối theo cấp (TH: 1–5, THCS: 6–9)
 *  3) Tên lớp = {khối}{1 chữ}{số 1–20 bắt buộc} → 7A1, 7A20 (không 7A)
 *  4) GradeLevel lưu DB dạng "Khối 7" (không chỉ số 7)
 *  5) Không trùng (trường + tên lớp + năm học)
 */
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { classApi } from '@/api/classes'

/* ── Cấp trường: chỉ TH + THCS ── */
const SCHOOL_LEVELS = [
  { code: 'TH', short: 'TH', label: 'Tiểu học', grades: [1, 2, 3, 4, 5] },
  { code: 'THCS', short: 'THCS', label: 'THCS', grades: [6, 7, 8, 9] },
]

const YEAR_RANGE = 5
/** Hậu tố: đúng 1 chữ cái + số 1–20 (bắt buộc). VD: A1, B20 — không A, không A21, không AB. */
const CLASS_SUFFIX_RE = /^[A-Z](20|[1-9]|1[0-9])$/
const YEAR_RE = /^(\d{4})-(\d{4})$/

const loading = ref(false)
const items = ref([])
const total = ref(0)
const keyword = ref('')
const filterSchoolId = ref('')
const filterStatus = ref('')
const filterGradeLevel = ref('')
const page = ref(0)
const pageSize = 10
const pageInput = ref('')

const schools = ref([])
const existingGrades = ref([])

/** list = danh sách chính | trash = thùng rác (giống QL giáo viên). */
const viewMode = ref('list')
const deleteMode = ref(false)
const selectedIds = ref([])
const trashItems = ref([])
const trashLoading = ref(false)
const batchDeleting = ref(false)

/** Combobox trường: gõ để lọc + chọn trong 1 ô. */
const schoolQuery = ref('')
const schoolDropOpen = ref(false)
const schoolComboEl = ref(null)

const modal = reactive({
  open: false,
  mode: 'create', // 'create' | 'edit'
  id: null,
  form: {
    schoolLevel: '', // TH | THCS
    schoolId: '',
    gradeNum: '', // 1..9
    classSuffix: '', // A1, B2…
    schoolYear: '',
    status: 'ACTIVE',
  },
  errors: {},
  error: '',
  saving: false,
})

const deleteTarget = ref(null)
const batchDeleteOpen = ref(false)
const purgeTarget = ref(null)

/* =========================
   Helpers: cấp / năm / gộp tên
========================= */

/** Suy cấp trường từ tên (THPT/THCS trước TH để tránh khớp nhầm). */
function detectSchoolLevel(name) {
  const n = (name || '').toUpperCase().normalize('NFC')
  if (
    n.includes('THPT') ||
    n.includes('TRUNG HOC PHO THONG') ||
    n.includes('TRUNG HỌC PHỔ THÔNG')
  ) {
    return 'THPT'
  }
  if (n.includes('THCS') || n.includes('TRUNG HOC CO SO') || n.includes('TRUNG HỌC CƠ SỞ')) {
    return 'THCS'
  }
  if (
    n.includes('TIỂU HỌC') ||
    n.includes('TIEU HOC') ||
    /(^|[\s\-_/])TH([\s\-_/]|$)/.test(n) ||
    n.startsWith('TH ')
  ) {
    return 'TH'
  }
  return null
}

function currentSchoolYearStart() {
  const now = new Date()
  return now.getMonth() >= 8 ? now.getFullYear() : now.getFullYear() - 1
}

function defaultSchoolYear() {
  const s = currentSchoolYearStart()
  return `${s}-${s + 1}`
}

/** Danh sách năm học: hiện tại ± YEAR_RANGE, mới → cũ. */
const schoolYearOptions = computed(() => {
  const cur = currentSchoolYearStart()
  const list = []
  for (let y = cur + YEAR_RANGE; y >= cur - YEAR_RANGE; y--) {
    list.push(`${y}-${y + 1}`)
  }
  return list
})

/** Lưu DB: luôn "Khối 7" (không chỉ "7", không "Lớp 7"). */
function gradeLabel(num) {
  return `Khối ${num}`
}

/** Tên lớp gộp: {số khối}{1 chữ}{số 1–20} → 7A1 */
function composeClassName(gradeNum, suffix) {
  const g = String(gradeNum || '').trim()
  const s = (suffix || '').trim().toUpperCase()
  if (!g || !s) return ''
  return `${g}${s}`
}

const composedClassName = computed(() =>
  composeClassName(modal.form.gradeNum, modal.form.classSuffix),
)

/** Trường theo cấp đã chọn — chỉ hiện trường khớp cấp (không fallback THPT). */
const schoolsByLevel = computed(() => {
  const level = modal.form.schoolLevel
  if (!level) return []
  const matched = schools.value.filter((s) => detectSchoolLevel(s.name) === level)
  // Không khớp prefix: vẫn cho chọn (trường tên chưa có TH/THCS) trừ khi nhận diện là cấp khác
  if (matched.length) return matched
  return schools.value.filter((s) => {
    const d = detectSchoolLevel(s.name)
    return d === null || d === level
  })
})

/** Lọc thêm theo ô tìm kiếm văn bản (không phân biệt hoa thường). */
const schoolsForLevel = computed(() => {
  const q = schoolQuery.value.trim().toLowerCase()
  if (!q) return schoolsByLevel.value
  return schoolsByLevel.value.filter((s) => (s.name || '').toLowerCase().includes(q))
})

const gradesForLevel = computed(() => {
  const lvl = SCHOOL_LEVELS.find((l) => l.code === modal.form.schoolLevel)
  return lvl ? lvl.grades : []
})

function selectSchoolLevel(code) {
  if (modal.form.schoolLevel === code) return
  modal.form.schoolLevel = code
  schoolQuery.value = ''
  modal.form.schoolId = ''
  schoolDropOpen.value = false
  clearFieldError('schoolLevel')
  clearFieldError('schoolId')
}

function selectGrade(num) {
  modal.form.gradeNum = String(num)
  clearFieldError('gradeNum')
  // Đổi khối → tên gộp đổi → xóa lỗi trùng cũ
  clearFieldError('classSuffix')
}

function onSchoolComboInput() {
  clearFieldError('schoolId')
  schoolDropOpen.value = true
  // Đang gõ: bỏ id đã chọn nếu text không còn khớp đúng tên trường đã chọn
  if (!modal.form.schoolId) return
  const s = schools.value.find((x) => String(x.id) === String(modal.form.schoolId))
  if (!s || s.name !== schoolQuery.value.trim()) {
    modal.form.schoolId = ''
  }
}

function pickSchool(s) {
  modal.form.schoolId = s.id
  schoolQuery.value = s.name
  schoolDropOpen.value = false
  clearFieldError('schoolId')
}

function openSchoolDrop() {
  if (!modal.form.schoolLevel) return
  schoolDropOpen.value = true
}

function closeSchoolDrop() {
  schoolDropOpen.value = false
  // Blur: nếu gõ khớp đúng 1 trường → tự chọn
  resolveSchoolFromQuery()
}

/** Khớp chính xác (không phân biệt hoa thường) → gán schoolId. */
function resolveSchoolFromQuery() {
  const q = schoolQuery.value.trim().toLowerCase()
  if (!q || !modal.form.schoolLevel) return
  if (modal.form.schoolId) {
    const cur = schools.value.find((x) => String(x.id) === String(modal.form.schoolId))
    if (cur && cur.name.toLowerCase() === q) return
  }
  const exact = schoolsByLevel.value.filter((s) => (s.name || '').toLowerCase() === q)
  if (exact.length === 1) {
    modal.form.schoolId = exact[0].id
    schoolQuery.value = exact[0].name
    clearFieldError('schoolId')
  }
}

function onDocClick(e) {
  if (!schoolComboEl.value) return
  if (!schoolComboEl.value.contains(e.target)) {
    schoolDropOpen.value = false
  }
}

/* =========================
   Pagination
========================= */

const totalPages = computed(() => Math.ceil(total.value / pageSize) || 1)

const visiblePages = computed(() => {
  const totalP = totalPages.value
  const current = page.value + 1
  let start = Math.max(1, current - 2)
  let end = Math.min(totalP, current + 2)
  if (end - start < 4) {
    if (start === 1) end = Math.min(5, totalP)
    else if (end === totalP) start = Math.max(1, totalP - 4)
  }
  const arr = []
  for (let i = start; i <= end; i++) arr.push(i)
  return arr
})

function goPage(index) {
  if (index < 0 || index >= totalPages.value) return
  page.value = index
  load()
}

function jumpPage() {
  const p = Number(pageInput.value)
  if (isNaN(p) || p < 1 || p > totalPages.value) return
  goPage(p - 1)
}

/* ── Load ── */
const loadError = ref('')

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await classApi.list({
      keyword: keyword.value || undefined,
      schoolId: filterSchoolId.value || undefined,
      status: filterStatus.value || undefined,
      gradeLevel: filterGradeLevel.value || undefined,
      page: page.value,
      size: pageSize,
    })
    items.value = res.data.content
    total.value = res.data.totalElements
  } catch (e) {
    // Nói rõ là LỖI (vd 403 không có quyền) thay vì hiện "0 lớp học" như hệ thống trống.
    items.value = []
    total.value = 0
    loadError.value =
      e.response?.status === 403
        ? 'Tài khoản của bạn không có quyền xem lớp học.'
        : (e.response?.data?.message ?? 'Không tải được danh sách lớp học.')
  } finally {
    loading.value = false
  }
}

/**
 * Kẹp lại chỉ số trang sau khi xóa bớt `removed` bản ghi — xóa hết trang cuối
 * mà giữ nguyên page thì request kế trả trang rỗng dù vẫn còn dữ liệu.
 */
function clampPageAfterRemove(removed) {
  const maxPage = Math.max(0, Math.ceil((total.value - removed) / pageSize) - 1)
  if (page.value > maxPage) page.value = maxPage
}

async function loadSchools() {
  try {
    const { data } = await classApi.schoolOptions()
    schools.value = data
  } catch {
    schools.value = []
  }
}

async function loadExistingGrades() {
  try {
    const { data } = await classApi.gradeLevels()
    existingGrades.value = data
  } catch {
    existingGrades.value = []
  }
}

onMounted(() => {
  load()
  loadSchools()
  loadExistingGrades()
  document.addEventListener('click', onDocClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
})

function onSearch() {
  page.value = 0
  load()
}

/** Tìm kiếm chữ: debounce để không gọi API mỗi phím. */
let keywordTimer = null
function onKeywordInput() {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    onSearch()
  }, 350)
}

function clearSearch() {
  if (keywordTimer) clearTimeout(keywordTimer)
  keyword.value = ''
  filterSchoolId.value = ''
  filterStatus.value = ''
  filterGradeLevel.value = ''
  page.value = 0
  load()
}

/* ── Khi đổi cấp: reset trường/khối/tên nếu không còn hợp lệ ── */
watch(
  () => modal.form.schoolLevel,
  (level, prev) => {
    if (!modal.open || level === prev) return
    // Xóa trường nếu không thuộc cấp mới
    if (modal.form.schoolId) {
      const s = schools.value.find((x) => String(x.id) === String(modal.form.schoolId))
      const det = s ? detectSchoolLevel(s.name) : null
      if (det && det !== level) {
        modal.form.schoolId = ''
        schoolQuery.value = ''
      }
    }
    // Reset khối nếu không thuộc cấp
    if (modal.form.gradeNum) {
      const allowed = SCHOOL_LEVELS.find((l) => l.code === level)?.grades ?? []
      if (!allowed.includes(Number(modal.form.gradeNum))) {
        modal.form.gradeNum = ''
        modal.form.classSuffix = ''
      }
    }
    clearFieldError('schoolLevel')
    clearFieldError('schoolId')
    clearFieldError('gradeNum')
  },
)

/* ── Parse tên lớp: 7A1 → grade 7, suffix A1 ── */
function parseClassName(name, gradeLevel) {
  const n = (name || '').trim().toUpperCase()
  let gradeNum = null
  const fromKhối = (gradeLevel || '').match(/(?:Khối|Lớp)\s*(1[0-2]|[1-9])$/i)
  if (fromKhối) gradeNum = fromKhối[1]
  else {
    const onlyNum = (gradeLevel || '').match(/^(1[0-2]|[1-9])$/)
    if (onlyNum) gradeNum = onlyNum[1]
  }

  if (gradeNum && n.startsWith(gradeNum)) {
    return { gradeNum, classSuffix: n.slice(gradeNum.length) || '' }
  }
  const m = n.match(/^(1[0-2]|[1-9])([A-Z].*)$/)
  if (m) return { gradeNum: m[1], classSuffix: m[2] }
  return { gradeNum: gradeNum || '', classSuffix: n }
}

/* ── Validate ── */
function validateForm(form) {
  const errors = {}

  // Thử khớp trường từ ô combobox trước khi chặn
  resolveSchoolFromQuery()

  if (!form.schoolLevel) {
    errors.schoolLevel = 'Chọn cấp trường (TH / THCS)'
    return errors
  }
  if (!SCHOOL_LEVELS.some((l) => l.code === form.schoolLevel)) {
    errors.schoolLevel = 'Cấp trường không hợp lệ (chỉ TH hoặc THCS)'
    return errors
  }

  const q = schoolQuery.value.trim()
  if (!form.schoolId) {
    if (!q) {
      errors.schoolId = 'Chọn trường (gõ để tìm rồi chọn trong danh sách)'
    } else {
      const partial = schoolsByLevel.value.filter((s) =>
        (s.name || '').toLowerCase().includes(q.toLowerCase()),
      )
      if (partial.length === 0) {
        errors.schoolId = 'Không có trường khớp — chọn lại từ danh sách'
      } else {
        errors.schoolId = 'Phải chọn đúng 1 trường trong danh sách (không chỉ gõ tự do)'
      }
    }
  } else {
    const s = schools.value.find((x) => String(x.id) === String(form.schoolId))
    if (!s) {
      errors.schoolId = 'Trường không hợp lệ'
    } else {
      const det = detectSchoolLevel(s.name)
      if (det && det !== form.schoolLevel) {
        errors.schoolId = `Trường thuộc cấp ${det}, không khớp ${form.schoolLevel}`
      }
      // Text combobox phải khớp tên đã chọn (tránh lệch id / tên)
      if (q && s.name.toLowerCase() !== q.toLowerCase()) {
        errors.schoolId = 'Tên trường không khớp lựa chọn — chọn lại từ danh sách'
      }
    }
  }

  const allowed = SCHOOL_LEVELS.find((l) => l.code === form.schoolLevel)?.grades ?? []
  const gNum = Number(form.gradeNum)
  if (!form.gradeNum) {
    errors.gradeNum = 'Chọn khối'
  } else if (!allowed.includes(gNum)) {
    errors.gradeNum = `Khối ${gNum} không thuộc cấp ${form.schoolLevel}`
  }

  const suffix = (form.classSuffix || '').trim().toUpperCase()
  if (!suffix) {
    errors.classSuffix = 'Nhập hậu tố lớp (vd: A1, B20) — bắt buộc có số'
  } else if (!CLASS_SUFFIX_RE.test(suffix)) {
    errors.classSuffix =
      'Hậu tố: đúng 1 chữ cái + số 1–20 (bắt buộc). VD: A1, B20 — không A, không A21, không AB'
  }

  const year = (form.schoolYear || '').trim()
  if (!year) {
    errors.schoolYear = 'Chọn năm học'
  } else {
    const m = year.match(YEAR_RE)
    if (!m) errors.schoolYear = 'Năm học dạng YYYY-YYYY'
    else {
      const y1 = Number(m[1])
      const y2 = Number(m[2])
      if (y2 !== y1 + 1) errors.schoolYear = 'Hai năm phải liên tiếp (vd: 2025-2026)'
      else {
        const cur = currentSchoolYearStart()
        if (y1 < cur - YEAR_RANGE || y1 > cur + YEAR_RANGE) {
          errors.schoolYear = `Chỉ chọn năm trong khoảng ±${YEAR_RANGE} năm quanh năm học hiện tại`
        }
      }
    }
  }

  if (form.status && !['ACTIVE', 'INACTIVE'].includes(form.status)) {
    errors.status = 'Trạng thái không hợp lệ'
  }

  return errors
}

/** Kiểm tra trùng tên lớp cùng trường + năm học (gọi API, không chỉ trang hiện tại). */
async function isDuplicateClass(schoolId, name, schoolYear, excludeId) {
  try {
    const { data } = await classApi.list({
      schoolId,
      keyword: name,
      page: 0,
      size: 50,
    })
    const rows = data?.content ?? []
    const target = name.toUpperCase()
    return rows.some(
      (c) =>
        c.schoolId === schoolId &&
        String(c.name || '').toUpperCase() === target &&
        c.schoolYear === schoolYear &&
        c.id !== excludeId,
    )
  } catch {
    // Lỗi mạng: để backend chặn lúc lưu
    return false
  }
}

function openCreate() {
  schoolQuery.value = ''
  schoolDropOpen.value = false
  Object.assign(modal, {
    open: true,
    mode: 'create',
    id: null,
    form: {
      schoolLevel: '',
      schoolId: '',
      gradeNum: '',
      classSuffix: '',
      schoolYear: defaultSchoolYear(),
      status: 'ACTIVE',
    },
    errors: {},
    error: '',
    saving: false,
  })
}

function openEdit(item) {
  const school = schools.value.find((s) => s.id === item.schoolId)
  let level = detectSchoolLevel(school?.name) || detectSchoolLevel(item.schoolName) || ''
  // Không còn hỗ trợ THPT trên form — bắt chọn lại TH/THCS nếu dữ liệu cũ
  if (level === 'THPT') level = ''
  const parsed = parseClassName(item.name, item.gradeLevel)
  let gradeNum = parsed.gradeNum
  if (!gradeNum && item.gradeLevel) {
    const m = String(item.gradeLevel).match(/(\d{1,2})/)
    if (m) gradeNum = m[1]
  }
  // Khối 10–12 thuộc THPT cũ: để trống để user chọn lại cấp TH/THCS
  if (gradeNum && Number(gradeNum) >= 10) {
    gradeNum = ''
  }

  schoolQuery.value = school?.name || item.schoolName || ''
  schoolDropOpen.value = false
  Object.assign(modal, {
    open: true,
    mode: 'edit',
    id: item.id,
    form: {
      schoolLevel: level,
      schoolId: item.schoolId,
      gradeNum: gradeNum ? String(gradeNum) : '',
      classSuffix: (parsed.classSuffix || '').toUpperCase(),
      schoolYear: item.schoolYear || defaultSchoolYear(),
      status: item.status || 'ACTIVE',
    },
    errors: {},
    error: '',
    saving: false,
  })
}

function clearFieldError(field) {
  if (modal.errors[field]) delete modal.errors[field]
}

/** Chuẩn hóa hậu tố tên lớp khi gõ (A1/B20) — tách khỏi inline handler vì prettier
 * bỏ dấu ; giữa 2 câu lệnh inline làm Vue compiler vỡ khi build. */
function onClassSuffixInput() {
  modal.form.classSuffix = modal.form.classSuffix.toUpperCase().replace(/[^A-Z0-9]/g, '')
  clearFieldError('classSuffix')
}

async function saveModal() {
  modal.errors = validateForm(modal.form)
  if (Object.keys(modal.errors).length) return

  const name = composeClassName(modal.form.gradeNum, modal.form.classSuffix)
  const schoolId = Number(modal.form.schoolId)
  const schoolYear = modal.form.schoolYear.trim()

  modal.saving = true
  modal.error = ''
  try {
    // Chặn trùng tên lớp (cùng trường + năm) trước khi gửi
    const dup = await isDuplicateClass(
      schoolId,
      name,
      schoolYear,
      modal.mode === 'edit' ? modal.id : null,
    )
    if (dup) {
      modal.errors = {
        ...modal.errors,
        classSuffix: `Lớp "${name}" năm ${schoolYear} đã tồn tại ở trường này`,
      }
      return
    }

    const body = {
      schoolId,
      name,
      gradeLevel: gradeLabel(modal.form.gradeNum),
      schoolYear,
      status: modal.form.status,
    }

    if (modal.mode === 'create') {
      await classApi.create(body)
    } else {
      await classApi.update(modal.id, body)
    }
    modal.open = false
    await load()
    await loadExistingGrades()
  } catch (e) {
    const msg = e.response?.data?.message ?? 'Lỗi không xác định'
    // Map lỗi trùng từ backend về field tên lớp
    if (e.response?.status === 409 || /đã tồn tại/i.test(msg)) {
      modal.errors = { ...modal.errors, classSuffix: msg }
    } else {
      modal.error = msg
    }
  } finally {
    modal.saving = false
  }
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await classApi.remove(deleteTarget.value.id)
    deleteTarget.value = null
    clampPageAfterRemove(1)
    await load()
    await loadExistingGrades()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Xóa thất bại')
    deleteTarget.value = null
  }
}

/* ── Chọn nhiều + thùng rác (pattern QL giáo viên) ── */
function switchView(mode) {
  viewMode.value = mode
  deleteMode.value = false
  selectedIds.value = []
  if (mode === 'trash') loadTrash()
  else {
    load()
    loadExistingGrades()
  }
}

function toggleDeleteMode() {
  deleteMode.value = !deleteMode.value
  selectedIds.value = []
}

function toggleSelect(id) {
  const idx = selectedIds.value.indexOf(id)
  if (idx === -1) selectedIds.value.push(id)
  else selectedIds.value.splice(idx, 1)
}

/** Chọn/bỏ chọn chỉ các dòng trang hiện tại — giữ nguyên lựa chọn các trang khác. */
function toggleSelectAll() {
  const pageIds = items.value.map((i) => i.id)
  if (!pageIds.length) return
  const allPageSelected = pageIds.every((id) => selectedIds.value.includes(id))
  if (allPageSelected) {
    selectedIds.value = selectedIds.value.filter((id) => !pageIds.includes(id))
  } else {
    const set = new Set(selectedIds.value)
    pageIds.forEach((id) => set.add(id))
    selectedIds.value = [...set]
  }
}

const allSelected = computed(
  () => items.value.length > 0 && items.value.every((i) => selectedIds.value.includes(i.id)),
)

/** Chọn tất cả trong thùng rác (list 1 trang, không phân trang). */
function toggleSelectAllTrash() {
  const ids = trashItems.value.map((i) => i.id)
  if (!ids.length) return
  const all = ids.every((id) => selectedIds.value.includes(id))
  selectedIds.value = all ? [] : [...ids]
}

const allTrashSelected = computed(
  () =>
    trashItems.value.length > 0 && trashItems.value.every((i) => selectedIds.value.includes(i.id)),
)

const trashBatchBusy = ref(false)
const trashBatchRestoreOpen = ref(false)
const trashBatchPurgeOpen = ref(false)

/* Batch qua 1 endpoint BE (transaction): lỗi 1 lớp là rollback cả lô + báo đúng lớp lỗi —
 * thay cho N request song song từng lớp (nửa thành công nửa thất bại khó lần). */
async function confirmTrashBatchRestore() {
  if (!selectedIds.value.length) return
  trashBatchBusy.value = true
  try {
    await classApi.restoreMany([...selectedIds.value])
    trashBatchRestoreOpen.value = false
    selectedIds.value = []
    deleteMode.value = false
    await loadTrash()
  } catch (e) {
    alert(
      (e.response?.data?.message ?? 'Khôi phục hàng loạt thất bại') +
        ' — chưa lớp nào được khôi phục.',
    )
  } finally {
    trashBatchBusy.value = false
  }
}

async function confirmTrashBatchPurge() {
  if (!selectedIds.value.length) return
  trashBatchBusy.value = true
  try {
    await classApi.purgeMany([...selectedIds.value])
    trashBatchPurgeOpen.value = false
    selectedIds.value = []
    deleteMode.value = false
    await loadTrash()
  } catch (e) {
    alert(
      (e.response?.data?.message ?? 'Xóa vĩnh viễn hàng loạt thất bại') + ' — chưa lớp nào bị xóa.',
    )
  } finally {
    trashBatchBusy.value = false
  }
}

function requestBatchDelete() {
  if (!selectedIds.value.length) return
  batchDeleteOpen.value = true
}

async function confirmBatchDelete() {
  if (!selectedIds.value.length) return
  batchDeleting.value = true
  try {
    const removed = selectedIds.value.length
    await classApi.removeMany([...selectedIds.value])
    batchDeleteOpen.value = false
    selectedIds.value = []
    deleteMode.value = false
    clampPageAfterRemove(removed)
    await load()
    await loadExistingGrades()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Xóa hàng loạt thất bại')
  } finally {
    batchDeleting.value = false
  }
}

async function loadTrash() {
  trashLoading.value = true
  try {
    const { data } = await classApi.trash()
    trashItems.value = data
  } catch {
    trashItems.value = []
  } finally {
    trashLoading.value = false
  }
}

async function restoreClass(id) {
  try {
    await classApi.restore(id)
    selectedIds.value = selectedIds.value.filter((x) => x !== id)
    await loadTrash()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Khôi phục thất bại')
  }
}

async function confirmPurge() {
  if (!purgeTarget.value) return
  try {
    const id = purgeTarget.value.id
    await classApi.purge(id)
    purgeTarget.value = null
    selectedIds.value = selectedIds.value.filter((x) => x !== id)
    await loadTrash()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Xóa vĩnh viễn thất bại')
    purgeTarget.value = null
  }
}

function statusLabel(s) {
  return s === 'ACTIVE' ? 'Hoạt động' : 'Ngừng'
}

function formatDeletedAt(iso) {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleString('vi-VN')
  } catch {
    return iso
  }
}
</script>

<template>
  <div class="page">
    <!-- ================= HEADER ================= -->
    <div class="page__head">
      <div>
        <h1 class="page__title">Lớp học</h1>
      </div>
      <div class="page__head-actions">
        <button
          type="button"
          class="btn-tab"
          :class="{ 'btn-tab--active': viewMode === 'list' }"
          @click="switchView('list')"
        >
          Danh sách
        </button>
        <button
          type="button"
          class="btn-tab"
          :class="{ 'btn-tab--active': viewMode === 'trash' }"
          @click="switchView('trash')"
        >
          Thùng rác
        </button>
        <button v-if="viewMode === 'list'" type="button" class="btn" @click="openCreate">
          + Thêm lớp học
        </button>
      </div>
    </div>

    <!-- ================= VIEW: DANH SÁCH ================= -->
    <template v-if="viewMode === 'list'">
      <div class="filter-bar">
        <label class="field field--wide">
          <span>Tìm kiếm</span>
          <input
            v-model="keyword"
            placeholder="Tên lớp, khối, năm học..."
            @input="onKeywordInput"
            @keyup.enter="onSearch"
          />
        </label>

        <label class="field">
          <span>Trường</span>
          <select v-model="filterSchoolId" @change="onSearch">
            <option value="">Tất cả</option>
            <option v-for="s in schools" :key="s.id" :value="s.id">{{ s.name }}</option>
          </select>
        </label>

        <label class="field">
          <span>Khối</span>
          <select v-model="filterGradeLevel" @change="onSearch">
            <option value="">Tất cả khối</option>
            <option v-for="g in existingGrades" :key="g" :value="g">{{ g }}</option>
          </select>
        </label>

        <label class="field">
          <span>Trạng thái</span>
          <select v-model="filterStatus" @change="onSearch">
            <option value="">Tất cả</option>
            <option value="ACTIVE">Hoạt động</option>
            <option value="INACTIVE">Ngừng</option>
          </select>
        </label>

        <div class="filter-actions">
          <button type="button" class="btn btn--ghost" @click="clearSearch">Xóa lọc</button>
          <button
            type="button"
            class="btn btn--ghost"
            :class="{ 'btn--select-on': deleteMode }"
            @click="toggleDeleteMode"
          >
            {{ deleteMode ? 'Hủy chọn' : 'Chọn nhiều' }}
          </button>
          <button
            v-if="deleteMode && selectedIds.length"
            type="button"
            class="btn btn--danger"
            @click="requestBatchDelete"
          >
            Xóa {{ selectedIds.length }} lớp
          </button>
        </div>
      </div>

      <p v-if="!loading" class="total">
        Tổng cộng
        <strong>{{ total }}</strong>
        lớp học
        <span v-if="deleteMode" class="total-hint"> — đã chọn {{ selectedIds.length }} </span>
      </p>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th v-if="deleteMode" class="col-check">
                <input
                  type="checkbox"
                  :checked="allSelected"
                  title="Chọn tất cả trang này"
                  @change="toggleSelectAll"
                />
              </th>
              <th>Tên lớp</th>
              <th>Trường</th>
              <th>Khối</th>
              <th>Năm học</th>
              <th>Trạng thái</th>
              <th width="120">Thao tác</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td :colspan="deleteMode ? 7 : 6" class="empty">Đang tải...</td>
            </tr>
            <!-- Lỗi API (vd không có quyền): nói thẳng, đừng giả vờ "Không có dữ liệu" -->
            <tr v-else-if="loadError">
              <td :colspan="deleteMode ? 7 : 6" class="empty">
                ⚠️ {{ loadError }}
                <button type="button" class="act-btn" @click="load">Thử lại</button>
              </td>
            </tr>
            <tr v-else-if="items.length === 0">
              <td :colspan="deleteMode ? 7 : 6" class="empty">Không có dữ liệu</td>
            </tr>
            <tr
              v-for="item in items"
              :key="item.id"
              :class="{ 'row--selected': deleteMode && selectedIds.includes(item.id) }"
              @click="deleteMode && toggleSelect(item.id)"
            >
              <td v-if="deleteMode" class="col-check" @click.stop="toggleSelect(item.id)">
                <input
                  type="checkbox"
                  :checked="selectedIds.includes(item.id)"
                  @click.stop="toggleSelect(item.id)"
                />
              </td>
              <td class="col-title">
                <div class="title-text">{{ item.name }}</div>
              </td>
              <td>{{ item.schoolName || '—' }}</td>
              <td>{{ item.gradeLevel || '—' }}</td>
              <td>
                <code class="code-text">{{ item.schoolYear }}</code>
              </td>
              <td>
                <span
                  class="badge"
                  :class="item.status === 'ACTIVE' ? 'badge--pub' : 'badge--draft'"
                >
                  {{ statusLabel(item.status) }}
                </span>
              </td>
              <td class="col-actions" @click.stop>
                <button class="act-btn" title="Sửa" @click="openEdit(item)">Sửa</button>
                <button class="act-btn act-btn--del" title="Xóa" @click="deleteTarget = item">
                  Xóa
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="total > pageSize" class="pagination">
        <button class="pg-btn" :disabled="page === 0" @click="goPage(0)">«</button>
        <button class="pg-btn" :disabled="page === 0" @click="goPage(page - 1)">‹</button>
        <button
          v-for="p in visiblePages"
          :key="p"
          class="pg-btn"
          :class="{ 'pg-btn--active': page === p - 1 }"
          @click="goPage(p - 1)"
        >
          {{ p }}
        </button>
        <button class="pg-btn" :disabled="page >= totalPages - 1" @click="goPage(page + 1)">
          ›
        </button>
        <button class="pg-btn" :disabled="page >= totalPages - 1" @click="goPage(totalPages - 1)">
          »
        </button>
        <input
          v-model="pageInput"
          class="page-input"
          type="number"
          min="1"
          :max="totalPages"
          placeholder="Trang"
        />
        <button class="pg-btn" @click="jumpPage">Đi</button>
      </div>
    </template>

    <!-- ================= VIEW: THÙNG RÁC ================= -->
    <template v-else>
      <div class="trash-banner">
        <div class="trash-banner__row">
          <div>
            <h2 class="trash-banner__title">Thùng rác — lớp đã xóa</h2>
            <p class="trash-banner__sub">
              Lớp đã xóa mềm. Có thể khôi phục hoặc xóa vĩnh viễn (không hoàn tác).
            </p>
          </div>
          <div class="trash-banner__actions">
            <button
              type="button"
              class="btn btn--ghost"
              :class="{ 'btn--select-on': deleteMode }"
              :disabled="!trashItems.length"
              @click="toggleDeleteMode"
            >
              {{ deleteMode ? 'Hủy chọn' : 'Chọn nhiều' }}
            </button>
            <button
              v-if="deleteMode && selectedIds.length"
              type="button"
              class="btn"
              :disabled="trashBatchBusy"
              @click="trashBatchRestoreOpen = true"
            >
              Khôi phục {{ selectedIds.length }}
            </button>
            <button
              v-if="deleteMode && selectedIds.length"
              type="button"
              class="btn btn--danger"
              :disabled="trashBatchBusy"
              @click="trashBatchPurgeOpen = true"
            >
              Xóa hẳn {{ selectedIds.length }}
            </button>
          </div>
        </div>
        <p v-if="deleteMode && selectedIds.length" class="total-hint trash-banner__count">
          Đã chọn {{ selectedIds.length }} / {{ trashItems.length }} lớp
        </p>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th v-if="deleteMode" class="col-check">
                <input
                  type="checkbox"
                  :checked="allTrashSelected"
                  title="Chọn tất cả"
                  @change="toggleSelectAllTrash"
                />
              </th>
              <th>Tên lớp</th>
              <th>Trường</th>
              <th>Khối</th>
              <th>Năm học</th>
              <th>Ngày xóa</th>
              <th width="180">Thao tác</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="trashLoading">
              <td :colspan="deleteMode ? 7 : 6" class="empty">Đang tải...</td>
            </tr>
            <tr v-else-if="trashItems.length === 0">
              <td :colspan="deleteMode ? 7 : 6" class="empty">Thùng rác trống</td>
            </tr>
            <tr
              v-for="item in trashItems"
              :key="item.id"
              :class="{ 'row--selected': deleteMode && selectedIds.includes(item.id) }"
              @click="deleteMode && toggleSelect(item.id)"
            >
              <td v-if="deleteMode" class="col-check" @click.stop="toggleSelect(item.id)">
                <input
                  type="checkbox"
                  :checked="selectedIds.includes(item.id)"
                  @click.stop="toggleSelect(item.id)"
                />
              </td>
              <td class="col-title">
                <div class="title-text">{{ item.name }}</div>
              </td>
              <td>{{ item.schoolName || '—' }}</td>
              <td>{{ item.gradeLevel || '—' }}</td>
              <td>
                <code class="code-text">{{ item.schoolYear }}</code>
              </td>
              <td>{{ formatDeletedAt(item.deletedAt) }}</td>
              <td class="col-actions" @click.stop>
                <button class="act-btn act-btn--restore" @click="restoreClass(item.id)">
                  Khôi phục
                </button>
                <button class="act-btn act-btn--del" @click="purgeTarget = item">Xóa hẳn</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <!-- ================= MODAL: tạo/sửa ================= -->
    <div v-if="modal.open" class="overlay" @click.self="modal.open = false">
      <div class="modal">
        <h3>{{ modal.mode === 'create' ? 'Thêm lớp học' : 'Sửa lớp học' }}</h3>

        <!-- 1. Cấp trường — 3 nút (không phụ đề) -->
        <div class="form-group">
          <label>Cấp trường *</label>
          <div class="chip-row" :class="{ 'is-invalid': modal.errors.schoolLevel }">
            <button
              v-for="lv in SCHOOL_LEVELS"
              :key="lv.code"
              type="button"
              class="chip-btn"
              :class="{ 'is-active': modal.form.schoolLevel === lv.code }"
              :title="lv.label"
              @click="selectSchoolLevel(lv.code)"
            >
              {{ lv.short }}
            </button>
          </div>
          <small v-if="modal.errors.schoolLevel" class="field-error">{{
            modal.errors.schoolLevel
          }}</small>
        </div>

        <!-- 2. Trường: 1 combobox gõ + chọn -->
        <div class="form-group">
          <label>Trường *</label>
          <div
            ref="schoolComboEl"
            class="combo"
            :class="{ 'is-disabled': !modal.form.schoolLevel, 'is-open': schoolDropOpen }"
          >
            <input
              v-model="schoolQuery"
              type="text"
              class="combo__input"
              :disabled="!modal.form.schoolLevel"
              :placeholder="
                modal.form.schoolLevel ? 'Gõ hoặc chọn trường…' : 'Chọn cấp trường trước'
              "
              :class="{ 'input-error': modal.errors.schoolId }"
              autocomplete="off"
              role="combobox"
              :aria-expanded="schoolDropOpen"
              @focus="openSchoolDrop"
              @input="onSchoolComboInput"
              @blur="closeSchoolDrop"
              @keydown.escape="schoolDropOpen = false"
            />
            <button
              type="button"
              class="combo__caret"
              tabindex="-1"
              :disabled="!modal.form.schoolLevel"
              @mousedown.prevent="schoolDropOpen = !schoolDropOpen"
            >
              ▾
            </button>
            <ul
              v-show="schoolDropOpen && modal.form.schoolLevel"
              class="combo__list"
              role="listbox"
            >
              <li v-if="schoolsForLevel.length === 0" class="combo__empty">
                Không tìm thấy trường
              </li>
              <li
                v-for="s in schoolsForLevel"
                :key="s.id"
                class="combo__option"
                :class="{ 'is-selected': String(modal.form.schoolId) === String(s.id) }"
                role="option"
                @mousedown.prevent="pickSchool(s)"
              >
                {{ s.name }}
              </li>
            </ul>
          </div>
          <small v-if="modal.errors.schoolId" class="field-error">{{
            modal.errors.schoolId
          }}</small>
        </div>

        <!-- 3. Khối — nút -->
        <div class="form-group">
          <label>Khối *</label>
          <div
            v-if="modal.form.schoolLevel"
            class="chip-row chip-row--grades"
            :class="{ 'is-invalid': modal.errors.gradeNum }"
          >
            <button
              v-for="g in gradesForLevel"
              :key="g"
              type="button"
              class="chip-btn chip-btn--grade"
              :class="{ 'is-active': String(modal.form.gradeNum) === String(g) }"
              @click="selectGrade(g)"
            >
              {{ g }}
            </button>
          </div>
          <div v-else class="chip-row chip-row--placeholder">
            <span class="chip-placeholder">Chọn cấp trường để hiện khối</span>
          </div>
          <small v-if="modal.errors.gradeNum" class="field-error">{{
            modal.errors.gradeNum
          }}</small>
        </div>

        <!-- 4. Hậu tố lớp → gộp tên -->
        <div class="form-group">
          <label>Tên lớp *</label>
          <div class="name-compose">
            <span class="name-compose__prefix" :class="{ 'is-empty': !modal.form.gradeNum }">
              {{ modal.form.gradeNum || '?' }}
            </span>
            <input
              v-model="modal.form.classSuffix"
              :disabled="!modal.form.gradeNum"
              placeholder="A1 / B20"
              maxlength="3"
              :class="{ 'input-error': modal.errors.classSuffix }"
              @input="onClassSuffixInput"
            />
          </div>
          <small v-if="modal.errors.classSuffix" class="field-error">{{
            modal.errors.classSuffix
          }}</small>
          <small v-else-if="composedClassName" class="field-ok">
            Tên lớp: <strong>{{ composedClassName }}</strong> · DB khối:
            <strong>{{ gradeLabel(modal.form.gradeNum) }}</strong>
          </small>
        </div>

        <!-- 5. Năm học -->
        <div class="form-group">
          <label>Năm học *</label>
          <select
            v-model="modal.form.schoolYear"
            :class="{ 'input-error': modal.errors.schoolYear }"
            @change="clearFieldError('schoolYear')"
          >
            <option v-for="y in schoolYearOptions" :key="y" :value="y">
              {{ y }}{{ y === defaultSchoolYear() ? ' (hiện tại)' : '' }}
            </option>
          </select>
          <small v-if="modal.errors.schoolYear" class="field-error">{{
            modal.errors.schoolYear
          }}</small>
          <small v-else>Mặc định năm học hiện tại; có thể chọn ±{{ YEAR_RANGE }} năm.</small>
        </div>

        <div class="form-group">
          <label>Trạng thái</label>
          <select v-model="modal.form.status">
            <option value="ACTIVE">Hoạt động</option>
            <option value="INACTIVE">Ngừng</option>
          </select>
        </div>

        <p v-if="modal.error" class="msg msg--error">{{ modal.error }}</p>

        <div class="modal__actions">
          <button class="btn btn--ghost" @click="modal.open = false">Hủy</button>
          <button class="btn" :disabled="modal.saving" @click="saveModal">
            {{ modal.saving ? 'Đang lưu...' : 'Lưu' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ================= MODAL: xác nhận xóa 1 ================= -->
    <div v-if="deleteTarget" class="overlay" @click.self="deleteTarget = null">
      <div class="modal">
        <h3>Xác nhận xóa</h3>
        <p>
          Chuyển lớp
          <strong>{{ deleteTarget.name }}</strong>
          ({{ deleteTarget.schoolYear }}) vào thùng rác?
        </p>
        <div class="modal__actions">
          <button class="btn btn--ghost" @click="deleteTarget = null">Hủy</button>
          <button class="btn btn--danger" @click="confirmDelete">Xóa</button>
        </div>
      </div>
    </div>

    <!-- ================= MODAL: xóa nhiều ================= -->
    <div v-if="batchDeleteOpen" class="overlay" @click.self="batchDeleteOpen = false">
      <div class="modal">
        <h3>Xóa {{ selectedIds.length }} lớp?</h3>
        <p>Các lớp đã chọn sẽ chuyển vào thùng rác (có thể khôi phục sau).</p>
        <div class="modal__actions">
          <button class="btn btn--ghost" :disabled="batchDeleting" @click="batchDeleteOpen = false">
            Hủy
          </button>
          <button class="btn btn--danger" :disabled="batchDeleting" @click="confirmBatchDelete">
            {{ batchDeleting ? 'Đang xóa...' : 'Xóa vào thùng rác' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ================= MODAL: xóa vĩnh viễn ================= -->
    <div v-if="purgeTarget" class="overlay" @click.self="purgeTarget = null">
      <div class="modal">
        <h3>Xóa vĩnh viễn?</h3>
        <p>
          Xóa hẳn lớp
          <strong>{{ purgeTarget.name }}</strong>
          ({{ purgeTarget.schoolYear }}) — <strong>không hoàn tác</strong>.
        </p>
        <div class="modal__actions">
          <button class="btn btn--ghost" @click="purgeTarget = null">Hủy</button>
          <button class="btn btn--danger" @click="confirmPurge">Xóa vĩnh viễn</button>
        </div>
      </div>
    </div>

    <!-- ================= MODAL: khôi phục nhiều (thùng rác) ================= -->
    <div v-if="trashBatchRestoreOpen" class="overlay" @click.self="trashBatchRestoreOpen = false">
      <div class="modal">
        <h3>Khôi phục {{ selectedIds.length }} lớp?</h3>
        <p>Đưa các lớp đã chọn từ thùng rác về danh sách chính.</p>
        <div class="modal__actions">
          <button
            class="btn btn--ghost"
            :disabled="trashBatchBusy"
            @click="trashBatchRestoreOpen = false"
          >
            Hủy
          </button>
          <button class="btn" :disabled="trashBatchBusy" @click="confirmTrashBatchRestore">
            {{ trashBatchBusy ? 'Đang khôi phục...' : 'Khôi phục' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ================= MODAL: xóa hẳn nhiều (thùng rác) ================= -->
    <div v-if="trashBatchPurgeOpen" class="overlay" @click.self="trashBatchPurgeOpen = false">
      <div class="modal">
        <h3>Xóa vĩnh viễn {{ selectedIds.length }} lớp?</h3>
        <p>
          Thao tác <strong>không hoàn tác</strong>. Chỉ xóa được lớp không còn ràng buộc dữ liệu.
        </p>
        <div class="modal__actions">
          <button
            class="btn btn--ghost"
            :disabled="trashBatchBusy"
            @click="trashBatchPurgeOpen = false"
          >
            Hủy
          </button>
          <button
            class="btn btn--danger"
            :disabled="trashBatchBusy"
            @click="confirmTrashBatchPurge"
          >
            {{ trashBatchBusy ? 'Đang xóa...' : 'Xóa vĩnh viễn' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 1280px;
  margin: auto;
}

.page__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 22px;
  flex-wrap: wrap;
}

.page__head-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.page__title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  color: var(--c-text);
}

.page__sub {
  margin-top: 6px;
  color: var(--c-text-muted);
  font-size: 14px;
}

.btn-tab {
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  color: var(--c-text);
  border-radius: 8px;
  padding: 9px 14px;
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
  transition: 0.15s;
}

.btn-tab:hover {
  border-color: #fb923c;
}

.btn-tab--active {
  background: #f97316;
  border-color: #f97316;
  color: #fff;
}

.btn--select-on {
  border: 1px solid #f97316 !important;
  color: #ea580c !important;
  background: rgba(249, 115, 22, 0.1) !important;
}

.col-check {
  width: 40px;
  text-align: center;
}

.row--selected {
  background: rgba(249, 115, 22, 0.08);
}

.act-btn--restore {
  color: #15803d;
}

.act-btn--restore:hover {
  background: rgba(34, 197, 94, 0.12);
}

.trash-banner {
  margin-bottom: 16px;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--c-border);
  background: var(--c-surface-2);
}

.trash-banner__row {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.trash-banner__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.trash-banner__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--c-text);
}

.trash-banner__sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--c-text-muted);
}

.trash-banner__count {
  margin: 10px 0 0;
  display: block;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 18px;
  margin-bottom: 18px;
  background: var(--c-surface);
  border-radius: 14px;
  border: 1px solid var(--c-border);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 150px;
}

.field--wide {
  flex: 1;
  min-width: 180px;
}

.field span {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
}

.field input,
.field select {
  height: 40px;
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  padding: 0 12px;
  font-size: 14px;
  background: var(--c-surface);
  color: var(--c-text);
}

.field input:focus,
.field select:focus {
  outline: none;
  border-color: #f97316;
}

.filter-actions {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.btn {
  border: none;
  cursor: pointer;
  border-radius: 8px;
  padding: 10px 18px;
  font-weight: 600;
  background: #f97316;
  color: white;
  transition: 0.2s;
}

.btn:hover {
  transform: translateY(-1px);
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.btn--ghost {
  background: var(--c-surface-2);
  color: var(--c-text);
}

.btn--danger {
  background: #ef4444;
}

.table-wrap {
  overflow-x: auto;
  background: var(--c-surface);
  border-radius: 14px;
  border: 1px solid var(--c-border);
}

table {
  width: 100%;
  border-collapse: collapse;
}

thead {
  background: var(--c-surface-2);
}

th {
  padding: 14px;
  text-align: left;
  font-size: 13px;
  font-weight: 700;
  color: var(--c-text);
}

td {
  padding: 14px;
  border-top: 1px solid var(--c-border);
  vertical-align: middle;
}

tbody tr:hover {
  background: var(--c-surface-2);
}

.empty {
  text-align: center;
  color: var(--c-text-muted);
  padding: 35px;
}

.title-text {
  font-weight: 600;
}

.code-text {
  background: var(--c-surface-2);
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-family: monospace;
}

.badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.badge--draft {
  background: var(--c-surface-2);
  color: var(--c-text);
}

.badge--pub {
  background: rgba(34, 197, 94, 0.15);
  color: #15803d;
}

:root[data-theme='dark'] .badge--pub {
  color: #4ade80;
}

.col-actions {
  white-space: nowrap;
}

.act-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  margin-right: 4px;
  transition: 0.2s;
  color: var(--c-text);
}

.act-btn:hover {
  background: var(--c-surface-2);
}

.act-btn--del:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.12);
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 24px;
  flex-wrap: wrap;
}

.pg-btn {
  min-width: 38px;
  height: 38px;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  background: var(--c-surface);
  cursor: pointer;
  transition: 0.2s;
  color: var(--c-text);
}

.pg-btn:hover:not(:disabled) {
  background: #f97316;
  color: white;
}

.pg-btn--active {
  background: #f97316;
  color: white;
}

.pg-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.page-input {
  width: 70px;
  height: 38px;
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  text-align: center;
  background: var(--c-surface);
  color: var(--c-text);
}

.overlay {
  position: fixed;
  inset: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  background: rgba(0, 0, 0, 0.45);
  z-index: 999;
  padding: 20px;
}

.modal {
  width: 480px;
  max-width: 100%;
  max-height: 90vh;
  overflow-y: auto;
  background: var(--c-surface);
  border-radius: 16px;
  padding: 28px;
}

.modal h3 {
  margin-top: 0;
  color: var(--c-text);
}

.modal-hint {
  margin: -4px 0 16px;
  font-size: 13px;
  color: var(--c-text-muted);
  line-height: 1.45;
}

.modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
}

.form-group {
  margin-bottom: 14px;
}

.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
  color: var(--c-text);
}

.form-group input,
.form-group select {
  width: 100%;
  height: 40px;
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  padding: 0 12px;
  font-size: 14px;
  box-sizing: border-box;
  font-family: inherit;
  background: var(--c-surface);
  color: var(--c-text);
}

.form-group input:disabled,
.form-group select:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  background: var(--c-surface-2);
}

.form-group input:focus,
.form-group select:focus {
  outline: none;
  border-color: #f97316;
}

.form-group small {
  display: block;
  margin-top: 4px;
  color: var(--c-text-muted);
  font-size: 12px;
}

/* ── Chip buttons: cấp trường / khối ── */
.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip-row.is-invalid .chip-btn {
  border-color: #fca5a5;
}

.chip-row--placeholder {
  min-height: 44px;
  align-items: center;
  padding: 8px 12px;
  border: 1px dashed var(--c-border);
  border-radius: 10px;
  background: var(--c-surface-2);
}

.chip-placeholder {
  font-size: 13px;
  color: var(--c-text-muted);
}

.chip-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  min-height: 42px;
  padding: 8px 16px;
  border: 1.5px solid var(--c-border);
  border-radius: 10px;
  background: var(--c-surface);
  color: var(--c-text);
  cursor: pointer;
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.02em;
  transition:
    border-color 0.15s,
    background 0.15s,
    color 0.15s,
    box-shadow 0.15s,
    transform 0.12s;
}

.chip-btn:hover {
  border-color: #fb923c;
  background: rgba(249, 115, 22, 0.06);
  transform: translateY(-1px);
}

.chip-btn.is-active {
  border-color: #f97316;
  background: linear-gradient(135deg, rgba(251, 146, 60, 0.18), rgba(249, 115, 22, 0.12));
  color: #c2410c;
  box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.2);
}

.chip-btn--grade {
  min-width: 48px;
  min-height: 42px;
  padding: 8px 12px;
  font-size: 15px;
}

:root[data-theme='dark'] .chip-btn.is-active {
  color: #fdba74;
  background: rgba(249, 115, 22, 0.2);
  border-color: #f97316;
}

/* ── Combobox trường (gõ + chọn 1 ô) ── */
.combo {
  position: relative;
  display: flex;
  align-items: stretch;
}

.combo.is-disabled {
  opacity: 0.7;
}

.combo__input {
  flex: 1;
  width: 100%;
  height: 40px;
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  padding: 0 36px 0 12px;
  font-size: 14px;
  box-sizing: border-box;
  font-family: inherit;
  background: var(--c-surface);
  color: var(--c-text);
}

.combo.is-open .combo__input {
  border-color: #f97316;
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
}

.combo__input:focus {
  outline: none;
  border-color: #f97316;
}

.combo__input:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  background: var(--c-surface-2);
}

.combo__caret {
  position: absolute;
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: var(--c-text-muted);
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
}

.combo__caret:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.combo__list {
  position: absolute;
  left: 0;
  right: 0;
  top: 100%;
  z-index: 20;
  max-height: 200px;
  overflow-y: auto;
  margin: 0;
  padding: 4px 0;
  list-style: none;
  background: var(--c-surface);
  border: 1px solid #f97316;
  border-top: none;
  border-radius: 0 0 8px 8px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.12);
}

.combo__option {
  padding: 9px 12px;
  font-size: 14px;
  cursor: pointer;
  color: var(--c-text);
}

.combo__option:hover,
.combo__option.is-selected {
  background: rgba(249, 115, 22, 0.12);
  color: #c2410c;
}

.combo__empty {
  padding: 10px 12px;
  font-size: 13px;
  color: var(--c-text-muted);
}

.name-compose {
  display: flex;
  align-items: stretch;
  gap: 0;
}

.name-compose__prefix {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 48px;
  padding: 0 12px;
  font-weight: 700;
  font-size: 15px;
  background: rgba(249, 115, 22, 0.12);
  color: #ea580c;
  border: 1px solid var(--c-input-border);
  border-right: none;
  border-radius: 8px 0 0 8px;
}

.name-compose__prefix.is-empty {
  color: var(--c-text-muted);
  background: var(--c-surface-2);
}

.name-compose input {
  border-radius: 0 8px 8px 0 !important;
  flex: 1;
}

.input-error {
  border-color: #dc2626 !important;
}

.field-error {
  color: #dc2626 !important;
}

.field-ok {
  color: #15803d !important;
}

:root[data-theme='dark'] .field-ok {
  color: #4ade80 !important;
}

.msg {
  padding: 12px;
  margin-top: 10px;
  margin-bottom: 0;
  border-radius: 8px;
}

.msg--error {
  background: rgba(239, 68, 68, 0.1);
  color: #b91c1c;
}

:root[data-theme='dark'] .msg--error {
  color: #f87171;
}

.total {
  margin-bottom: 14px;
  color: var(--c-text-muted);
}

.total-hint {
  margin-left: 6px;
  font-size: 12px;
  opacity: 0.85;
}

@media (max-width: 900px) {
  .page__head {
    flex-direction: column;
    align-items: flex-start;
    gap: 15px;
  }

  .filter-bar {
    flex-direction: column;
  }

  .field {
    width: 100%;
  }

  .filter-actions {
    width: 100%;
  }

  .btn {
    width: 100%;
  }
}
</style>
