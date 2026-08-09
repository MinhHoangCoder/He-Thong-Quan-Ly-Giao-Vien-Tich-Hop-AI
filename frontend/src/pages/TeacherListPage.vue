<script setup>
// Trang Quản lý Giáo viên — 1 FILE DUY NHẤT (giống phong cách TeacherDashboardPage.vue)
// để dễ đọc / dễ sửa, không tách nhỏ ra nhiều component.
// Giao diện danh sách dạng BẢNG (giống trang Quản lý Nhân viên) thay cho card cũ.
import { ref, computed, reactive, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import DateField from '@/components/ui/DateField.vue'
import { teacherApi } from '@/api/teacher'
import { branchApi } from '@/api/branches'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { isStrongPassword, PASSWORD_HINT } from '@/utils/password'
/* ══════════════════════════════════════════════════════════
   PHÂN QUYỀN THEO VAI TRÒjdnjdnvjdnsdjnvd
   ADMIN / EMPLOYEE: đầy đủ chức năng (xem, sửa, xóa mềm, lịch sử).
   TEACHER: chỉ xem + sửa — KHÔNG có nút "Xóa" và tab "Lịch sử".
══════════════════════════════════════════════════════════ */
const auth = useAuthStore()
const canManage = computed(() => auth.roles.some((r) => ['ADMIN', 'EMPLOYEE'].includes(r)))

/* ══════════════════════════════════════════════════════════
   STATE
══════════════════════════════════════════════════════════ */
const loading = ref(false)
const teachers = ref([])
const branches = ref([])
const showPassword = ref(false)

// Bộ lọc
const filters = reactive({
  keyword: '',
  status: '',
  employmentType: '',
  branchId: '',
})

// Chế độ giao diện
const viewMode = ref('list') // 'list' | 'trash'
/* ══════════════════════════════════════════════════════════
   PHÂN TRANG (client-side, dựa trên danh sách đã lọc)
══════════════════════════════════════════════════════════ */
const PAGE_SIZE = 6
const page = ref(0)
const pageInput = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / PAGE_SIZE)))

const paginatedTeachers = computed(() => {
  const start = page.value * PAGE_SIZE
  return filtered.value.slice(start, start + PAGE_SIZE)
})

const visiblePages = computed(() => {
  const total = totalPages.value
  const current = page.value + 1

  let start = Math.max(1, current - 2)
  let end = Math.min(total, current + 2)

  if (end - start < 4) {
    if (start === 1) {
      end = Math.min(5, total)
    } else if (end === total) {
      start = Math.max(1, total - 4)
    }
  }

  const arr = []
  for (let i = start; i <= end; i++) arr.push(i)
  return arr
})

function goPage(index) {
  if (index < 0 || index >= totalPages.value) return
  page.value = index
}

function jumpPage() {
  const p = Number(pageInput.value)

  if (isNaN(p)) return
  if (p < 1) return
  if (p > totalPages.value) return

  goPage(p - 1)
}

// Đổi bộ lọc → quay về trang 1
watch(filters, () => {
  page.value = 0
})

// Xóa mềm — chọn nhiều
const deleteMode = ref(false)
const selectedIds = ref([])

// Thùng rác
const trashItems = ref([])
const trashLoading = ref(false)

// Confirm xóa
const confirmDelete = reactive({ open: false })

// Confirm xóa VĨNH VIỄN (từ thùng rác) — cần gõ lại tên để chắc chắn không bấm nhầm.
const confirmPurge = reactive({ open: false, id: null, name: '', typedName: '', purging: false })

// Notification toast
const toast = reactive({ show: false, msg: '', type: 'success' })

/* ══════════════════════════════════════════════════════════
   REGEX / HẰNG SỐ VALIDATE DÙNG CHUNG (Tạo + Sửa)
══════════════════════════════════════════════════════════ */
const NAME_RE = /^[\p{L} ]+$/u
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PHONE_RE = /^(\+84|0)\d{9,10}$/
const CCCD_RE = /^\d{9}(\d{3})?$/
const MIN_WORKING_AGE = 18 // tuổi lao động tối thiểu — dùng để so ngày sinh với ngày vào làm

/**
 * So sánh cặp Ngày sinh / Ngày vào làm — dùng chung cho cả 2 form (Tạo & Sửa).
 * Trả về '' nếu hợp lệ, hoặc thông báo lỗi cụ thể.
 * Quy tắc:
 *   - Ngày sinh không được ở tương lai.
 *   - Ngày sinh và ngày vào làm không được trùng nhau.
 *   - Ngày vào làm không được trước ngày sinh.
 *   - Ngày vào làm phải sau ngày sinh ít nhất MIN_WORKING_AGE năm.
 */
function checkDobHirePair(dobStr, hireStr) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)

  if (dobStr) {
    const dob = new Date(dobStr)
    if (dob > today) return { field: 'dateOfBirth', msg: 'Ngày sinh không được ở tương lai.' }
  }
  if (dobStr && hireStr) {
    const dob = new Date(dobStr)
    const hire = new Date(hireStr)
    if (dob.getTime() === hire.getTime()) {
      return { field: 'hireDate', msg: 'Ngày vào làm không được trùng với ngày sinh.' }
    }
    if (hire < dob) {
      return { field: 'hireDate', msg: 'Ngày vào làm không được trước ngày sinh.' }
    }
    const ageAtHireYears = (hire - dob) / (365.25 * 24 * 3600 * 1000)
    if (ageAtHireYears < MIN_WORKING_AGE) {
      return {
        field: 'hireDate',
        msg: `Ngày vào làm phải sau ngày sinh ít nhất ${MIN_WORKING_AGE} năm (tuổi lao động tối thiểu).`,
      }
    }
  }
  return null
}

/* ══════════════════════════════════════════════════════════
   MODAL: THÊM GIÁO VIÊN (tab trái + form phải)
   — Hồ sơ giáo viên cần 1 tài khoản đăng nhập đi kèm (bảng AppUser bên DB
   ràng buộc Teacher.AppUserId NOT NULL) nên tab "Hồ sơ" có thêm 3 ô tài khoản.
══════════════════════════════════════════════════════════ */
const createTabs = [
  { key: 'profile', label: 'Hồ sơ giáo viên', icon: 'teacher' },
  { key: 'degree', label: 'Bằng cấp', icon: 'assignment' },
  { key: 'experience', label: 'Kinh nghiệm', icon: 'history' },
  { key: 'status', label: 'Trạng thái', icon: 'attendance' },
]

// Trình độ chuyên môn — 4 mức cố định theo yêu cầu khách hàng.
const DEGREE_LEVELS = ['Thạc sỹ', 'Cử nhân', 'Cao đẳng', 'Trung cấp']

// Chuyên ngành gợi ý theo ĐÚNG 4 mảng môn học cố định của trung tâm
// (xem api/lessons.js: 'Tin học' | 'Tiếng Anh' | 'STEM - AI' | 'Kĩ năng sống').
// Có "Khác" ở cuối để tự nhập khi chuyên ngành GV không nằm trong danh sách gợi ý.
const MAJOR_GROUPS = [
  {
    label: 'Tin học',
    options: ['Công nghệ thông tin', 'Khoa học máy tính', 'Sư phạm Tin học', 'Kỹ thuật phần mềm'],
  },
  {
    label: 'Tiếng Anh',
    options: ['Sư phạm Tiếng Anh', 'Ngôn ngữ Anh', 'Ngôn ngữ học ứng dụng'],
  },
  {
    label: 'STEM - AI',
    options: [
      'Trí tuệ nhân tạo',
      'Khoa học máy tính',
      'Cơ điện tử',
      'Kỹ thuật Robot',
      'Kỹ thuật điều khiển & tự động hóa',
    ],
  },
  {
    label: 'Kĩ năng sống',
    options: ['Tâm lý học', 'Giáo dục học', 'Công tác xã hội', 'Sư phạm Giáo dục công dân'],
  },
]
const MAJOR_OTHER = '__OTHER__'

/** 1 dòng "Bằng cấp": Trình độ chuyên môn + Chuyên ngành (+ chuyên ngành tự nhập nếu chọn Khác) + PDF. */
function emptyDegree() {
  return { level: '', major: '', majorOther: '', file: null }
}
/** Ghép Trình độ + Chuyên ngành thành 1 chuỗi để lưu vào field `name` (BE dùng chung 1 bảng Certificate). */
function degreeName(d) {
  const major = d.major === MAJOR_OTHER ? d.majorOther.trim() : d.major
  return [d.level, major].filter(Boolean).join(' - ')
}

function addDoc(list, factory = emptyDegree) {
  list.push(factory())
}
function removeDoc(list, i) {
  list.splice(i, 1)
}
function onPickFile(doc, e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (file.type !== 'application/pdf') {
    showToast('Chỉ nhận file PDF', 'error')
    e.target.value = ''
    return
  }
  doc.file = file
}

const createModal = reactive({
  open: false,
  saving: false,
  error: '',
  activeTab: 'profile',
  account: { username: '', email: '', password: '' },
  profile: {
    branchId: '',
    lastName: '',
    firstName: '',
    gender: null,
    phone: '',
    idCardNo: '',
    dateOfBirth: '',
    hireDate: '',
    address: '',
    employmentType: '',
  },
  degrees: [emptyDegree()],
})

// Validate inline cho form thêm mới
const createFieldErrors = reactive({
  username: '',
  email: '',
  password: '',
  lastName: '',
  firstName: '',
  phone: '',
  idCardNo: '',
  dateOfBirth: '',
  hireDate: '',
})

function validateUsername() {
  const v = createModal.account.username.trim()
  if (!v) {
    createFieldErrors.username = 'Không được để trống'
    return false
  }
  if (v.length < 3) {
    createFieldErrors.username = 'Ít nhất 3 ký tự'
    return false
  }
  if (v.length > 50) {
    createFieldErrors.username = 'Tối đa 50 ký tự'
    return false
  }
  createFieldErrors.username = ''
  return true
}

function validateEmail() {
  const v = createModal.account.email.trim()
  if (!v) {
    createFieldErrors.email = 'Không được để trống'
    return false
  }
  if (!EMAIL_RE.test(v)) {
    createFieldErrors.email = 'Email không hợp lệ (VD: gv@tsdms.local)'
    return false
  }
  createFieldErrors.email = ''
  return true
}

function validateCreatePassword() {
  const v = createModal.account.password
  if (!v) {
    createFieldErrors.password = 'Không được để trống'
    return false
  }
  if (!isStrongPassword(v)) {
    createFieldErrors.password = `Chưa đủ mạnh: ${PASSWORD_HINT}`
    return false
  }
  createFieldErrors.password = ''
  return true
}

function validateLastName() {
  const v = createModal.profile.lastName.trim()
  if (!v) {
    createFieldErrors.lastName = 'Không được để trống'
    return false
  }
  if (!NAME_RE.test(v)) {
    createFieldErrors.lastName = 'Chỉ được chứa chữ cái và khoảng trắng'
    return false
  }
  createFieldErrors.lastName = ''
  return true
}

function validateFirstName() {
  const v = createModal.profile.firstName.trim()
  if (!v) {
    createFieldErrors.firstName = 'Không được để trống'
    return false
  }
  if (!NAME_RE.test(v)) {
    createFieldErrors.firstName = 'Chỉ được chứa chữ cái và khoảng trắng'
    return false
  }
  createFieldErrors.firstName = ''
  return true
}

function validatePhone() {
  const v = createModal.profile.phone.trim()
  if (!v) {
    createFieldErrors.phone = ''
    return true
  }
  if (!PHONE_RE.test(v)) {
    createFieldErrors.phone = 'SĐT phải bắt đầu bằng 0 hoặc +84, có 10-11 chữ số'
    return false
  }
  createFieldErrors.phone = ''
  return true
}

function validateIdCard() {
  const v = createModal.profile.idCardNo.trim()
  if (!v) {
    createFieldErrors.idCardNo = ''
    return true
  }
  if (!CCCD_RE.test(v)) {
    createFieldErrors.idCardNo = 'CCCD phải có 12 số'
    return false
  }
  createFieldErrors.idCardNo = ''
  return true
}

/** Validate cặp Ngày sinh/Ngày vào làm cho form TẠO — set lỗi vào đúng field liên quan. */
function validateCreateDates() {
  createFieldErrors.dateOfBirth = ''
  createFieldErrors.hireDate = ''
  const err = checkDobHirePair(createModal.profile.dateOfBirth, createModal.profile.hireDate)
  if (err) {
    createFieldErrors[err.field] = err.msg
    return false
  }
  return true
}

function validateAllCreateFields() {
  const results = [
    validateUsername(),
    validateEmail(),
    validateCreatePassword(),
    validateLastName(),
    validateFirstName(),
    validatePhone(),
    validateIdCard(),
    validateCreateDates(),
  ]
  if (!createModal.profile.branchId) {
    createModal.error = 'Vui lòng chọn chi nhánh.'
    createModal.activeTab = 'profile'
    return false
  }
  if (!results.every(Boolean)) {
    createModal.error = 'Vui lòng kiểm tra lại các trường được đánh dấu đỏ.'
    createModal.activeTab = 'profile'
    return false
  }
  return true
}

function openCreate() {
  createModal.activeTab = 'profile'
  createModal.error = ''
  createModal.saving = false
  Object.keys(createFieldErrors).forEach((k) => (createFieldErrors[k] = ''))
  createModal.account = { username: '', email: '', password: '' }
  createModal.profile = {
    branchId: branches.value[0]?.id || '',
    lastName: '',
    firstName: '',
    gender: null,
    phone: '',
    idCardNo: '',
    dateOfBirth: '',
    hireDate: '',
    address: '',
    employmentType: '',
  }
  createModal.degrees = [emptyDegree()]
  createModal.teachingExperience = ''
  createModal.open = true
}

async function submitCreate() {
  if (!validateAllCreateFields()) return

  const p = createModal.profile
  const a = createModal.account

  createModal.saving = true
  createModal.error = ''
  let createdTeacherId = null
  try {
    // 1) Tạo tài khoản đăng nhập + hồ sơ GV cơ bản (BE tạo AppUser + Teacher cùng lúc)
    const { data: userInfo } = await authApi.register({
      role: 'TEACHER',
      username: a.username,
      email: a.email,
      password: a.password,
      firstName: p.firstName,
      lastName: p.lastName,
      phone: p.phone,
      branchId: Number(p.branchId),
    })

    // 2) Tìm lại GV vừa tạo (theo AppUserId) để bổ sung các trường hồ sơ chi tiết
    const { data: allTeachers } = await teacherApi.list()
    const created = allTeachers.find((t) => t.appUserId === userInfo.id)
    if (!created)
      throw new Error('Tạo tài khoản thành công nhưng không tìm thấy hồ sơ giáo viên vừa tạo.')
    createdTeacherId = created.id

    await teacherApi.update(created.id, {
      email: a.email,
      branchId: Number(p.branchId),
      firstName: p.firstName,
      lastName: p.lastName,
      status: 'ACTIVE',
      employmentType: p.employmentType || null,
      dateOfBirth: p.dateOfBirth || null,
      hireDate: p.hireDate || null,
      gender: p.gender === '' ? null : p.gender,
      idCardNo: p.idCardNo || null,
      phone: p.phone || null,
      address: p.address || null,
      teachingExperience: createModal.teachingExperience || null,
    })

    // 3) Bằng cấp + Trình độ  — lưu tên/nơi cấp/ngày; file PDF đính kèm chỉ lưu TÊN FILE
    const validDegrees = createModal.degrees.filter((d) => d.level)
    for (const d of validDegrees) {
      const { data: cert } = await teacherApi.addCertificate(created.id, {
        name: degreeName(d),
        issuer: null,
        issueDate: null,
      })
      if (d.file) {
        try {
          await teacherApi.uploadCertificateFile(created.id, cert.id, d.file)
        } catch {
          showToast(
            'Đã lưu "${degreeName(d)}" nhưng upload file thất bại, vào Sửa để tải lại',
            'error',
          )
        }
      }
    }

    createModal.open = false
    showToast('Đã thêm giáo viên mới thành công')
    await loadTeachers()
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || 'Tạo giáo viên thất bại'
    // Nếu bước 1 đã tạo xong mà bước 2-3 bị lỗi → rollbacks xóa_teacher vừa tạo
    if (createdTeacherId && canManage.value) {
      try {
        await teacherApi.delete(createdTeacherId)
      } catch {
        /* bỏ qua nếu rollbacks thất bại */
      }
      createModal.error = `${msg} — Đã tự rollbacks, bạn có thể thử lại.`
    } else if (createdTeacherId) {
      createModal.error = `${msg} — Tài khoản đã được tạo. Vui lòng vào danh sách, bấm Sửa để bổ sung thông tin còn thiếu.`
    } else {
      createModal.error = msg
    }
  } finally {
    createModal.saving = false
  }
}

/* ══════════════════════════════════════════════════════════
   NHÃN / MÀU DÙNG CHUNG TRONG TRANG
══════════════════════════════════════════════════════════ */
const statusLabel = {
  ACTIVE: 'Đang hoạt động',
  RETIRED: 'Ngừng hoạt động',
  SUSPENDED: 'Đã nghỉ phép',
}
const statusClass = {
  ACTIVE: 'badge--active',
  RETIRED: 'badge--retired',
  SUSPENDED: 'badge--suspended',
}
// Khớp bộ giá trị của cột Teacher.EmploymentType sau migration V20 (trước là
// FULL_TIME/PART_TIME/CONTRACT). Backend có @Pattern chặn giá trị lạ nên gửi mã cũ
// lên sẽ ăn 400. Lưu ý bảng Employee (nhân viên trung tâm) VẪN dùng FULL_TIME/PART_TIME.
const empLabel = { CO_HUU: 'Cơ hữu', THINH_GIANG: 'Thỉnh giảng' }
// Bảng màu avatar xoay vòng theo id — chỉ để phân biệt trực quan giữa các dòng.
const avatarPalette = ['#0ea5e9', '#22c55e', '#8b5cf6', '#f97316', '#ec4899', '#14b8a6', '#f43f5e']
function avatarColor(id) {
  return avatarPalette[id % avatarPalette.length]
}

/* ══════════════════════════════════════════════════════════
   COMPUTED — LỌC PHÍA FRONTEND
══════════════════════════════════════════════════════════ */
const filtered = computed(() => {
  const kw = filters.keyword.toLowerCase().trim()
  return teachers.value.filter((t) => {
    const matchKw =
      !kw ||
      (t.fullName || '').toLowerCase().includes(kw) ||
      (t.phone || '').includes(kw) ||
      (t.idCardNo || '').includes(kw)
    const matchStatus = !filters.status || t.status === filters.status
    const matchEmp = !filters.employmentType || t.employmentType === filters.employmentType
    const matchBranch = !filters.branchId || t.branchId === Number(filters.branchId)
    return matchKw && matchStatus && matchEmp && matchBranch
  })
})

/* ══════════════════════════════════════════════════════════
   LOAD DATA
══════════════════════════════════════════════════════════ */
async function loadTeachers() {
  loading.value = true
  try {
    const res = await teacherApi.list()
    teachers.value = res.data
  } catch {
    showToast('Không tải được danh sách giáo viên', 'error')
  } finally {
    loading.value = false
  }
}

async function loadBranches() {
  try {
    // /branches trả về TOÀN BỘ chi nhánh trong bảng Branch (BranchController.list() dùng
    // findAll(), không lọc theo phạm vi người dùng) — bao gồm đủ các chi nhánh trong file
    // seed database/seed/TSDMS_Seed_Demo.sql (Chi nhánh trung tâm, Cầu Giấy, Hà Đông, Đà Nẵng,
    // TP.HCM). Nếu dropdown đang thiếu chi nhánh, khả năng cao là seed CHƯA được chạy trên
    // DB đang dùng, chứ không phải do FE lọc bớt — FE ở đây không hardcode gì cả.
    const res = await branchApi.list()
    branches.value = res.data
  } catch {
    // Bỏ qua lỗi branch — không cản trang chính
  }
}

async function loadTrash() {
  trashLoading.value = true
  try {
    const res = await teacherApi.trash()
    trashItems.value = res.data
  } catch {
    showToast('Không tải được lịch sử', 'error')
  } finally {
    trashLoading.value = false
  }
}

const route = useRoute()
onMounted(async () => {
  // Ô tìm kiếm chung (topbar) điều hướng tới đây kèm ?q=... — prefill bộ lọc.
  if (route.query.q) {
    filters.keyword = String(route.query.q)
  }
  await Promise.all([loadTeachers(), loadBranches()])
})

/* ══════════════════════════════════════════════════════════
   SWITCH VIEW
══════════════════════════════════════════════════════════ */
function switchView(mode) {
  // TEACHER không được phép vào chế độ Lịch sử (thùng rác).
  if (mode === 'trash' && !canManage.value) return
  viewMode.value = mode
  deleteMode.value = false
  selectedIds.value = []
  page.value = 0
  if (mode === 'trash') loadTrash()
}

/* ══════════════════════════════════════════════════════════
   SỬA — modal dùng LẠI cấu trúc tab trái/phải giống modal Tạo, để có
   ĐẦY ĐỦ thông tin y hệt lúc tạo (hồ sơ + bằng cấp + chứng chỉ + kinh
   nghiệm + trạng thái), không phải chỉ 1 form phẳng như trước.
══════════════════════════════════════════════════════════ */
const editTabs = [
  { key: 'profile', label: 'Hồ sơ giáo viên', icon: 'teacher' },
  { key: 'degree', label: 'Bằng cấp', icon: 'assignment' },
  { key: 'experience', label: 'Kinh nghiệm', icon: 'history' },
  { key: 'status', label: 'Trạng thái', icon: 'attendance' },
]
const editModal = reactive({
  open: false,
  id: null,
  saving: false,
  error: '',
  activeTab: 'profile',
  form: {
    branchId: '',
    firstName: '',
    lastName: '',
    status: 'ACTIVE',
    employmentType: '',
    phone: '',
    idCardNo: '',
    address: '',
    dateOfBirth: '',
    hireDate: '',
    password: '',
    gender: null,
  },
  // Bằng cấp ĐÃ LƯU (BE dùng chung 1 bảng Certificate) + form thêm mới — gộp chung
  // 1 tab "Bằng cấp" duy nhất, không còn tab Chứng chỉ riêng.
  existingCerts: [],
  newDegrees: [emptyDegree()],
  experience: '',
})

const editFieldErrors = reactive({
  lastName: '',
  firstName: '',
  phone: '',
  idCardNo: '',
  dateOfBirth: '',
  hireDate: '',
  password: '',
})

/**
 * Ô mật khẩu ở tab Tài khoản: BỎ TRỐNG = giữ nguyên mật khẩu cũ, nên rỗng vẫn hợp lệ.
 * Có gõ thì phải đủ mạnh — cùng chính sách với backend (@Pattern ở AccountUpdateRequest)
 * và với các form khác trong dự án.
 */
function validateEditPassword() {
  const v = editModal.form.password
  if (v && !isStrongPassword(v)) {
    editFieldErrors.password = `Chưa đủ mạnh: ${PASSWORD_HINT}`
    return false
  }
  editFieldErrors.password = ''
  return true
}

function validateEditLastName() {
  const v = editModal.form.lastName.trim()
  if (!v) {
    editFieldErrors.lastName = 'Không được để trống'
    return false
  }
  if (!NAME_RE.test(v)) {
    editFieldErrors.lastName = 'Chỉ được chứa chữ cái và khoảng trắng'
    return false
  }
  editFieldErrors.lastName = ''
  return true
}
function validateEditFirstName() {
  const v = editModal.form.firstName.trim()
  if (!v) {
    editFieldErrors.firstName = 'Không được để trống'
    return false
  }
  if (!NAME_RE.test(v)) {
    editFieldErrors.firstName = 'Chỉ được chứa chữ cái và khoảng trắng'
    return false
  }
  editFieldErrors.firstName = ''
  return true
}
function validateEditPhone() {
  const v = editModal.form.phone.trim()
  if (!v) {
    editFieldErrors.phone = ''
    return true
  }
  if (!PHONE_RE.test(v)) {
    editFieldErrors.phone = 'SĐT phải bắt đầu bằng 0 hoặc +84, có 10-11 chữ số'
    return false
  }
  editFieldErrors.phone = ''
  return true
}
function validateEditIdCard() {
  const v = editModal.form.idCardNo.trim()
  if (!v) {
    editFieldErrors.idCardNo = ''
    return true
  }
  if (!CCCD_RE.test(v)) {
    editFieldErrors.idCardNo = 'CCCD phải có  12 số'
    return false
  }
  editFieldErrors.idCardNo = ''
  return true
}
/** Validate cặp Ngày sinh/Ngày vào làm cho form SỬA — cùng quy tắc với bên Tạo. */
function validateEditDates() {
  editFieldErrors.dateOfBirth = ''
  editFieldErrors.hireDate = ''
  const err = checkDobHirePair(editModal.form.dateOfBirth, editModal.form.hireDate)
  if (err) {
    editFieldErrors[err.field] = err.msg
    return false
  }
  return true
}

async function openEdit(teacher) {
  editModal.id = teacher.id
  editModal.error = ''
  editModal.saving = false
  editModal.activeTab = 'profile'
  editModal.existingCerts = []
  editModal.newDegrees = [emptyDegree()]
  editModal.teachingExperience = teacher.teachingExperience || ''
  Object.keys(editFieldErrors).forEach((k) => (editFieldErrors[k] = ''))
  Object.assign(editModal.form, {
    branchId: teacher.branchId,
    firstName: teacher.firstName,
    lastName: teacher.lastName,
    username: '',
    email: '',
    password: '',
    status: teacher.status,
    employmentType: teacher.employmentType || '',
    phone: teacher.phone || '',
    idCardNo: teacher.idCardNo || '',
    address: teacher.address || '',
    dateOfBirth: teacher.dateOfBirth || '',
    hireDate: teacher.hireDate || '',
    gender: teacher.gender,
  })
  editModal.open = true
  try {
    const { data } = await teacherApi.get(teacher.id)
    editModal.existingCerts = data.certificates || []
  } catch {
    /* lỗi bằng cấp không chặn form */
  }

  // Tải username/email riêng — BE xử lý qua appUserId trong service
  try {
    const { data } = await teacherApi.getAccount(teacher.id)
    editModal.form.username = data.username || ''
    editModal.form.email = data.email || ''
  } catch {
    // Lỗi tải bằng cấp không nên chặn cả form sửa — các tab khác vẫn dùng được
  }
}

async function removeExistingCert(certId) {
  try {
    await teacherApi.deleteCertificate(editModal.id, certId)
    editModal.existingCerts = editModal.existingCerts.filter((c) => c.id !== certId)
    showToast('Đã xóa bằng cấp')
  } catch (e) {
    showToast(e?.response?.data?.message || 'Xóa bằng cấp thất bại', 'error')
  }
}

//======================
const viewingCertId = ref(null)
/**
 * Mở file PDF của 1 bằng cấp — endpoint cần header Authorization nên KHÔNG thể
 * window.open() thẳng URL; phải tải về dạng blob rồi tự mở tab mới.
 */
async function viewCert(cert) {
  viewingCertId.value = cert.id
  try {
    const response = await teacherApi.openCertificateFile(editModal.id, cert.id)
    const blobUrl = window.URL.createObjectURL(
      new Blob([response.data], { type: 'application/pdf' }),
    )
    window.open(blobUrl, '_blank', 'noopener')
    setTimeout(() => window.URL.revokeObjectURL(blobUrl), 60_000)
  } catch (e) {
    showToast(
      e?.response?.data?.message || 'Không mở được file (có thể chưa có file đính kèm)',
      'error',
    )
  } finally {
    viewingCertId.value = null
  }
}
//===================

async function saveEdit() {
  const ok = [
    validateEditLastName(),
    validateEditFirstName(),
    validateEditPhone(),
    validateEditIdCard(),
    validateEditDates(),
    validateEditPassword(),
  ].every(Boolean)
  if (!ok) {
    editModal.error = 'Vui lòng kiểm tra lại các trường bị đánh dấu đỏ.'
    editModal.activeTab = 'profile' // mọi ô có validate (kể cả mật khẩu) đều nằm ở tab này
    return
  }

  editModal.saving = true
  editModal.error = ''
  try {
    await teacherApi.update(editModal.id, {
      ...editModal.form,
      branchId: Number(editModal.form.branchId),
      gender: editModal.form.gender === '' ? null : editModal.form.gender,
      employmentType: editModal.form.employmentType || null,
      teachingExperience: editModal.teachingExperience || null,
    })

    // Chỉ gửi những field người dùng thực sự thay đổi
    const accountPayload = {}

    if (editModal.form.username?.trim()) {
      accountPayload.username = editModal.form.username.trim()
    }

    if (editModal.form.email?.trim()) {
      accountPayload.email = editModal.form.email.trim()
    }

    // Mật khẩu: để TRỐNG = giữ nguyên mật khẩu cũ (backend hiểu chuỗi rỗng như vậy).
    // Chỉ gửi khi người dùng thực sự gõ vào ô này.
    if (editModal.form.password) {
      accountPayload.password = editModal.form.password
    }

    if (Object.keys(accountPayload).length > 0) {
      await teacherApi.updateAccount(editModal.id, accountPayload)
      // Không giữ mật khẩu trong bộ nhớ form sau khi đã lưu xong.
      editModal.form.password = ''
    }

    // Bằng cấp/chứng chỉ mới thêm — tạo record trước, upload file PDF thật ngay sau.
    const newOnes = editModal.newDegrees.filter((d) => d.level)
    for (const d of newOnes) {
      const { data: cert } = await teacherApi.addCertificate(editModal.id, {
        name: degreeName(d),
        issuer: null,
        issueDate: null,
      })
      if (d.file) {
        try {
          await teacherApi.uploadCertificateFile(editModal.id, cert.id, d.file)
        } catch {
          showToast(
            `Đã lưu "${degreeName(d)}" nhưng upload file thất bại, thử lại ở tab Bằng cấp`,
            'error',
          )
        }
      }
    }
    editModal.open = false
    showToast('Cập nhật giáo viên thành công')
    await loadTeachers()
  } catch (e) {
    editModal.error = e?.response?.data?.message || 'Cập nhật thất bại'
  } finally {
    editModal.saving = false
  }
}

/* ══════════════════════════════════════════════════════════
   XÓA MỀM (chỉ ADMIN / EMPLOYEE)
   Yêu cầu: khi ẩn GV khỏi danh sách, GV đó phải hiện trạng thái
   "Ngừng hoạt động" (RETIRED) ngay khi xem trong Lịch sử.
   BE hiện KHÔNG tự đổi status lúc xóa mềm, nên ở đây ta chủ động gọi
   update(status=RETIRED) cho từng GV TRƯỚC KHI xóa mềm (phải làm trước,
   vì sau khi xóa mềm bản ghi không còn "active" nên update sẽ báo lỗi).
══════════════════════════════════════════════════════════ */
function toggleDeleteMode() {
  if (!canManage.value) return
  deleteMode.value = !deleteMode.value
  selectedIds.value = []
}

function toggleSelect(id) {
  const idx = selectedIds.value.indexOf(id)
  if (idx === -1) selectedIds.value.push(id)
  else selectedIds.value.splice(idx, 1)
}

function quickDelete(id) {
  if (!canManage.value) return
  deleteMode.value = true
  selectedIds.value = [id]
  requestDelete()
}

function requestDelete() {
  if (!canManage.value) return
  if (!selectedIds.value.length) return
  confirmDelete.open = true
}

async function confirmDoDelete() {
  confirmDelete.open = false
  try {
    await teacherApi.deleteMany(selectedIds.value)
    showToast(`Đã chuyển ${selectedIds.value.length} giáo viên vào lịch sử`)
    selectedIds.value = []
    deleteMode.value = false
    await loadTeachers()
  } catch (e) {
    showToast(e?.response?.data?.message || 'Xóa thất bại, vui lòng thử lại', 'error')
  }
}

//  KHÔI PHỤC TỪ THÙNG RÁC

async function restore(id) {
  try {
    await teacherApi.restore(id)
    showToast('Đã khôi phục giáo viên thành công')
    await loadTrash()
    await loadTeachers()
  } catch {
    showToast('Khôi phục thất bại', 'error')
  }
}

//  XÓA VĨNH VIỄN (chỉ ADMIN) — CHỈ áp dụng cho GV đang trong thùng rác.

const isAdmin = computed(() => auth.roles.includes('ADMIN'))

function requestPurge(item) {
  if (!isAdmin.value) return
  confirmPurge.open = true
  confirmPurge.id = item.id
  confirmPurge.name = item.fullName
  confirmPurge.phone = item.phone || ''
  confirmPurge.typedName = ''
  confirmPurge.purging = false
}

function closePurge() {
  confirmPurge.open = false
}

const purgeConfirmValid = computed(() => {
  if (confirmPurge.phone) {
    return confirmPurge.typedName.trim() === confirmPurge.phone.trim()
  }
  return confirmPurge.typedName.trim().toLowerCase() === confirmPurge.name.trim().toLowerCase()
})

async function confirmDoPurge() {
  if (!purgeConfirmValid.value || confirmPurge.purging) return
  confirmPurge.purging = true
  try {
    await teacherApi.deleteTrue(confirmPurge.id)
    showToast(`Đã xóa vĩnh viễn "${confirmPurge.name}" khỏi hệ thống`)
    confirmPurge.open = false
    await loadTrash()
  } catch (e) {
    const msg = e?.response?.data?.message || 'Xóa vĩnh viễn thất bại'
    showToast(msg, 'error')
  } finally {
    confirmPurge.purging = false
  }
}

/* ══════════════════════════════════════════════════════════
   HELPERS
══════════════════════════════════════════════════════════ */
function showToast(msg, type = 'success') {
  toast.msg = msg
  toast.type = type
  toast.show = true
  setTimeout(() => (toast.show = false), 3000)
}

function branchName(branchId) {
  return branches.value.find((b) => b.id === branchId)?.name || `CN ${branchId}`
}

function formatDate(d) {
  if (!d) return '—'
  return new Date(d).toLocaleDateString('vi-VN')
}
</script>

<template>
  <div class="tl">
    <!-- ── Toast ────────────────────────────────────── -->
    <Transition name="toast">
      <div v-if="toast.show" :class="['toast', `toast--${toast.type}`]">
        <SvgIcon :name="toast.type === 'success' ? 'attendance' : 'close'" :size="16" />
        {{ toast.msg }}
      </div>
    </Transition>

    <!-- ── Tiêu đề + nút chuyển view ────────────────── -->
    <div class="tl__header">
      <div>
        <h1 class="tl__title">Quản lý Giáo viên</h1>
      </div>
      <div class="tl__header-actions">
        <button
          :class="['btn-tab', viewMode === 'list' && 'btn-tab--active']"
          @click="switchView('list')"
        >
          <SvgIcon name="teacher" :size="16" /> Danh sách
        </button>

        <button
          v-if="canManage"
          :class="['btn-tab btn-tab--history', viewMode === 'trash' && 'btn-tab--active']"
          @click="switchView('trash')"
          title="Lịch sử (giáo viên đã bị ẩn)"
        >
          🕒 Lịch sử
        </button>

        <button v-if="canManage" class="btn-add" @click="openCreate">
          <SvgIcon name="plus" :size="16" /> Thêm giáo viên
        </button>
      </div>
    </div>

    <!-- ═══════════════════════════════════════════════
         VIEW: DANH SÁCH CHÍNH (dạng bảng)
    ═══════════════════════════════════════════════ -->
    <template v-if="viewMode === 'list'">
      <!-- ── Thanh lọc: ô tổng số + tìm kiếm + select + xóa ── -->
      <div class="filters">
        <div class="filter-total">
          <span class="filter-total__num">{{ filtered.length }}</span>
          <span class="filter-total__label">Tổng giáo viên</span>
        </div>

        <div class="filter-search">
          <SvgIcon name="search" :size="16" />
          <input v-model="filters.keyword" type="text" placeholder="Tìm theo tên, SĐT, CCCD…" />
        </div>

        <select v-model="filters.status" class="filter-select">
          <option value="">Tất cả trạng thái</option>
          <option value="ACTIVE">Đang hoạt động</option>
          <option value="RETIRED">Dừng hoạt động</option>
          <option value="SUSPENDED">Đã nghỉ phép</option>
        </select>

        <select v-model="filters.employmentType" class="filter-select">
          <option value="">Tất cả loại hình</option>
          <option value="CO_HUU">Cơ hữu</option>
          <option value="THINH_GIANG">Thỉnh giảng</option>
        </select>

        <select v-model="filters.branchId" class="filter-select">
          <option value="">Tất cả chi nhánh</option>
          <option v-for="b in branches" :key="b.id" :value="b.id">{{ b.name }}</option>
        </select>

        <!-- Nút xóa: chỉ ADMIN / EMPLOYEE mới thấy -->
        <button
          v-if="canManage"
          :class="['btn-delete-toggle', deleteMode && 'btn-delete-toggle--active']"
          @click="toggleDeleteMode"
          title="Chọn giáo viên để xóa"
        >
          🗑️ {{ deleteMode ? 'Hủy chọn' : 'Xóa' }}
        </button>

        <button
          v-if="canManage && deleteMode && selectedIds.length"
          class="btn-confirm-delete"
          @click="requestDelete"
        >
          Xóa {{ selectedIds.length }} giáo viên
        </button>
      </div>

      <!-- ── Trạng thái loading ────────────────────── -->
      <div v-if="loading" class="tl__loading">
        <span class="spinner" />
        <span>Đang tải danh sách…</span>
      </div>

      <!-- ── Bảng danh sách giáo viên ─────────────── -->
      <div v-else-if="filtered.length" class="table-wrap">
        <table class="teacher-table">
          <thead>
            <tr>
              <th v-if="deleteMode" class="col-check"></th>
              <th>Giáo viên</th>
              <th>Liên hệ</th>
              <th>Chi nhánh</th>
              <th>Loại hình</th>
              <th>Trạng thái</th>
              <th class="col-action">Hành động</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="t in paginatedTeachers"
              :key="t.id"
              :class="['t-row', deleteMode && selectedIds.includes(t.id) && 't-row--selected']"
              :title="deleteMode ? '' : 'Bấm để xem'"
              @click="deleteMode ? toggleSelect(t.id) : openEdit(t)"
            >
              <td v-if="deleteMode" class="col-check" @click.stop="toggleSelect(t.id)">
                <span :class="['tick', selectedIds.includes(t.id) && 'tick--checked']">
                  <SvgIcon v-if="selectedIds.includes(t.id)" name="attendance" :size="13" />
                </span>
              </td>

              <!-- Tên + avatar + ID -->
              <td>
                <div class="t-name-cell">
                  <span class="t-avatar" :style="{ background: avatarColor(t.id) }">
                    {{ (t.firstName || '?')[0].toUpperCase() }}
                    <span
                      :class="['t-avatar-dot', t.status === 'ACTIVE' ? 'dot--active' : 'dot--off']"
                    />
                  </span>
                  <div>
                    <div class="t-name" :title="t.fullName">{{ t.fullName }}</div>
                    <div class="t-id">ID: {{ t.id }}</div>
                  </div>
                </div>
              </td>

              <!-- Liên hệ -->
              <td>
                <div class="t-contact"><strong>SĐT:</strong> {{ t.phone || '—' }}</div>
                <div class="t-contact t-contact--muted">
                  <strong>CCCD:</strong>{{ t.idCardNo || '—' }}
                </div>
              </td>

              <!-- Chi nhánh -->
              <td>
                <span class="badge badge--branch">{{ branchName(t.branchId) }}</span>
              </td>

              <!-- Loại hình -->
              <td>{{ t.employmentType ? empLabel[t.employmentType] : '—' }}</td>

              <!-- Trạng thái -->
              <td>
                <span :class="['badge', statusClass[t.status]]">{{ statusLabel[t.status] }}</span>
              </td>

              <!-- Hành động -->
              <td class="col-action" @click.stop>
                <div class="row-actions">
                  <button class="ra-btn ra-btn--edit" title="Chỉnh sửa" @click="openEdit(t)">
                    Xem
                  </button>
                  <button
                    v-if="canManage"
                    class="ra-btn ra-btn--delete"
                    title="Xóa giáo viên"
                    @click="quickDelete(t.id)"
                  >
                    Xóa
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- ── Pagination ─────────────────────────────── -->
      <div v-if="filtered.length && totalPages > 1" class="pagination">
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

        <button class="pg-btn" :disabled="page === totalPages - 1" @click="goPage(page + 1)">
          ›
        </button>

        <button class="pg-btn" :disabled="page === totalPages - 1" @click="goPage(totalPages - 1)">
          »
        </button>

        <input
          v-model="pageInput"
          class="page-input"
          type="number"
          min="1"
          :max="totalPages"
          placeholder="Trang"
          @keyup.enter="jumpPage"
        />

        <button class="pg-btn" @click="jumpPage">Đi</button>
      </div>

      <!-- Empty state -->
      <div v-if="!loading && !filtered.length" class="tl__empty">
        <SvgIcon name="teacher" :size="48" />
        <p>Không tìm thấy giáo viên phù hợp</p>
      </div>
    </template>

    <!-- ═══════════════════════════════════════════════
         VIEW: LỊCH SỬ (THÙNG RÁC)
    ═══════════════════════════════════════════════ -->
    <template v-else>
      <div class="trash-header">
        <div>
          <h2 class="trash-title"><i class="history" />📜 Lịch sử giáo viên đã xóa</h2>
          <p class="trash-sub">
            Các giáo viên bên dưới đã bị xóa khỏi danh sách chính. Có thể khôi phục bất kỳ lúc nào.
          </p>
        </div>
      </div>

      <div v-if="trashLoading" class="tl__loading"><span class="spinner" /> Đang tải…</div>

      <div v-else-if="trashItems.length" class="table-wrap">
        <table class="teacher-table">
          <thead>
            <tr>
              <th>Họ và tên</th>
              <th>SĐT</th>
              <th>CCCD</th>
              <th>Loại hình</th>
              <th>Trạng thái</th>
              <th>Ngày ẩn</th>
              <th>Chi nhánh</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in trashItems" :key="item.id">
              <td class="t-name">{{ item.fullName }}</td>
              <td>{{ item.phone || '—' }}</td>
              <td>{{ item.idCardNo || '—' }}</td>
              <td>{{ empLabel[item.employmentType] || '—' }}</td>
              <td>
                <span :class="['badge', statusClass[item.status]]">{{
                  statusLabel[item.status]
                }}</span>
              </td>
              <td>{{ formatDate(item.deletedAt) }}</td>
              <td>
                <span class="badge badge--branch">{{ branchName(item.branchId) }}</span>
              </td>
              <td>
                <div class="trash-actions">
                  <button class="btn-restore" @click="restore(item.id)">
                    <SvgIcon name="restore" :size="14" /> Khôi phục
                  </button>
                  <button v-if="isAdmin" class="btn-purge" @click="requestPurge(item)">
                    <SvgIcon name="trash" :size="14" /> Xóa
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else class="tl__empty">
        <p>Chưa có giáo viên nào trong lịch sử</p>
      </div>
    </template>

    <!-- ═══════════════════════════════════════════════
         MODAL: THÊM GIÁO VIÊN (tab trái + form phải)
    ═══════════════════════════════════════════════ -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="createModal.open" class="overlay" @click.self="createModal.open = false">
          <div class="modal modal--xl">
            <div class="modal__head">
              <h3 class="modal__title">Thêm giáo viên mới</h3>
              <button class="modal__close" @click="createModal.open = false">❌</button>
            </div>

            <div class="create-body">
              <!-- Menu trái -->
              <div class="create-nav">
                <button
                  v-for="tab in createTabs"
                  :key="tab.key"
                  :class="[
                    'create-nav__item',
                    createModal.activeTab === tab.key && 'create-nav__item--active',
                  ]"
                  @click="createModal.activeTab = tab.key"
                >
                  <SvgIcon :name="tab.icon" :size="16" /> {{ tab.label }}
                </button>
              </div>

              <!-- Form phải -->
              <div class="create-form">
                <!-- Tab: Hồ sơ giáo viên -->
                <template v-if="createModal.activeTab === 'profile'">
                  <h4 class="create-section-title">Tài khoản đăng nhập</h4>
                  <p class="create-section-hint">
                    Giáo viên cần 1 tài khoản để đăng nhập hệ thống.
                  </p>
                  <div class="form-row">
                    <div class="form-field">
                      <label class="form-label"
                        >Tên đăng nhập <span class="req">*</span>
                        <input
                          v-model="createModal.account.username"
                          class="form-input"
                          :class="{ 'form-input--error': createFieldErrors.username }"
                          placeholder="VD: gv.nguyenvana"
                          @blur="validateUsername"
                        />
                      </label>
                      <span v-if="createFieldErrors.username" class="field-error">{{
                        createFieldErrors.username
                      }}</span>
                    </div>
                    <div class="form-field">
                      <label class="form-label"
                        >Email <span class="req">*</span>
                        <input
                          v-model="createModal.account.email"
                          type="email"
                          class="form-input"
                          :class="{ 'form-input--error': createFieldErrors.email }"
                          placeholder="VD: gv@tsdms.local"
                          @blur="validateEmail"
                        />
                      </label>
                      <span v-if="createFieldErrors.email" class="field-error">{{
                        createFieldErrors.email
                      }}</span>
                    </div>
                  </div>
                  <div class="form-field">
                    <label class="form-label"
                      >Mật khẩu <span class="req">*</span>
                      <input
                        v-model="createModal.account.password"
                        :type="showPassword ? 'text' : 'password'"
                        class="form-input"
                        :class="{ 'form-input--error': createFieldErrors.password }"
                        placeholder="Ít nhất 8 ký tự, có hoa/thường/số"
                        @blur="validateCreatePassword"
                      />
                    </label>

                    <label style="margin-top: 4px; display: flex; align-items: center; gap: 6px">
                      <input type="checkbox" v-model="showPassword" /> Hiện mật khẩu
                    </label>

                    <span v-if="createFieldErrors.password" class="field-error">{{
                      createFieldErrors.password
                    }}</span>
                  </div>

                  <h4 class="create-section-title create-section-title--gap">Thông tin cá nhân</h4>
                  <div class="form-row">
                    <div class="form-field">
                      <label class="form-label"
                        >Họ và tên đệm <span class="req">*</span>
                        <input
                          v-model="createModal.profile.lastName"
                          class="form-input"
                          :class="{ 'form-input--error': createFieldErrors.lastName }"
                          placeholder="VD: Trần Nguyễn Văn"
                          @blur="validateLastName"
                        />
                      </label>
                      <span v-if="createFieldErrors.lastName" class="field-error">{{
                        createFieldErrors.lastName
                      }}</span>
                    </div>
                    <div class="form-field">
                      <label class="form-label"
                        >Tên gọi <span class="req">*</span>
                        <input
                          v-model="createModal.profile.firstName"
                          class="form-input"
                          :class="{ 'form-input--error': createFieldErrors.firstName }"
                          placeholder="VD: A"
                          @blur="validateFirstName"
                        />
                      </label>
                      <span v-if="createFieldErrors.firstName" class="field-error">{{
                        createFieldErrors.firstName
                      }}</span>
                    </div>
                  </div>
                  <div class="form-row">
                    <label class="form-label"
                      >Chi nhánh <span class="req">*</span>
                      <select v-model="createModal.profile.branchId" class="form-input">
                        <option value="">-- Chọn chi nhánh --</option>
                        <option v-for="b in branches" :key="b.id" :value="b.id">
                          {{ b.name }}
                        </option>
                      </select>
                    </label>
                    <label class="form-label"
                      >Giới tính
                      <select v-model="createModal.profile.gender" class="form-input">
                        <option :value="null">-- Chọn --</option>
                        <option :value="true">Nam</option>
                        <option :value="false">Nữ</option>
                      </select>
                    </label>
                  </div>
                  <div class="form-row">
                    <div class="form-field">
                      <label class="form-label"
                        >Số điện thoại
                        <input
                          v-model="createModal.profile.phone"
                          class="form-input"
                          :class="{ 'form-input--error': createFieldErrors.phone }"
                          placeholder="VD: 0901234567"
                          @blur="validatePhone"
                        />
                      </label>
                      <span v-if="createFieldErrors.phone" class="field-error">{{
                        createFieldErrors.phone
                      }}</span>
                    </div>
                    <div class="form-field">
                      <label class="form-label"
                        >Số CCCD
                        <input
                          v-model="createModal.profile.idCardNo"
                          class="form-input"
                          :class="{ 'form-input--error': createFieldErrors.idCardNo }"
                          placeholder="12 số"
                          @blur="validateIdCard"
                        />
                      </label>
                      <span v-if="createFieldErrors.idCardNo" class="field-error">{{
                        createFieldErrors.idCardNo
                      }}</span>
                    </div>
                  </div>
                  <div class="form-row">
                    <div class="form-field">
                      <label class="form-label"
                        >Ngày sinh
                        <DateField
                          v-model="createModal.profile.dateOfBirth"
                          :invalid="!!createFieldErrors.dateOfBirth"
                          @update:model-value="validateCreateDates"
                        />
                      </label>
                      <span v-if="createFieldErrors.dateOfBirth" class="field-error">{{
                        createFieldErrors.dateOfBirth
                      }}</span>
                    </div>
                    <div class="form-field">
                      <label class="form-label"
                        >Ngày vào làm
                        <DateField
                          v-model="createModal.profile.hireDate"
                          :invalid="!!createFieldErrors.hireDate"
                          @update:model-value="validateCreateDates"
                        />
                      </label>
                      <span v-if="createFieldErrors.hireDate" class="field-error">{{
                        createFieldErrors.hireDate
                      }}</span>
                    </div>
                  </div>
                  <label class="form-label"
                    >Loại hình
                    <select v-model="createModal.profile.employmentType" class="form-input">
                      <option value="">-- Chọn loại hình hợp đồng --</option>
                      <option value="CO_HUU">Cơ hữu</option>
                      <option value="THINH_GIANG">Thỉnh giảng</option>
                    </select>
                  </label>
                  <label class="form-label"
                    >Địa chỉ
                    <input
                      v-model="createModal.profile.address"
                      class="form-input"
                      placeholder="Địa chỉ thường trú"
                    />
                  </label>
                </template>

                <!-- Tab: Bằng cấp -->
                <template v-else-if="createModal.activeTab === 'degree'">
                  <h4 class="create-section-title">Bằng cấp</h4>
                  <p class="create-section-hint">
                    Có thể bỏ trống nếu chưa có, sau này thêm cũng được.
                  </p>
                  <div v-for="(d, i) in createModal.degrees" :key="i" class="degree-row">
                    <div class="degree-row__fields">
                      <label class="form-label form-label--sm"
                        >Trình độ chuyên môn
                        <select v-model="d.level" class="form-input">
                          <option value="">-- Chọn trình độ --</option>
                          <option v-for="lv in DEGREE_LEVELS" :key="lv" :value="lv">
                            {{ lv }}
                          </option>
                        </select>
                      </label>
                      <label class="form-label form-label--sm"
                        >Chuyên ngành
                        <select v-model="d.major" class="form-input">
                          <option value="">-- Chọn chuyên ngành --</option>
                          <optgroup v-for="g in MAJOR_GROUPS" :key="g.label" :label="g.label">
                            <option v-for="m in g.options" :key="m" :value="m">{{ m }}</option>
                          </optgroup>
                          <option :value="MAJOR_OTHER">Khác (tự nhập)…</option>
                        </select>
                      </label>
                      <input
                        v-if="d.major === MAJOR_OTHER"
                        v-model="d.majorOther"
                        class="form-input"
                        placeholder="Nhập chuyên ngành"
                      />
                      <label class="doc-file">
                        <SvgIcon name="assignment" :size="14" />
                        {{ d.file ? d.file.name : 'Chọn PDF' }}
                        <input
                          type="file"
                          accept="application/pdf"
                          hidden
                          @change="onPickFile(d, $event)"
                        />
                      </label>
                    </div>
                    <button
                      v-if="createModal.degrees.length > 1"
                      class="doc-remove"
                      @click="removeDoc(createModal.degrees, i)"
                    >
                      ❌
                    </button>
                  </div>
                  <button class="btn-add-row" @click="addDoc(createModal.degrees, emptyDegree)">
                    <SvgIcon name="plus" :size="14" /> Thêm bằng cấp
                  </button>
                </template>

                <!-- Tab: Kinh nghiệm -->
                <template v-else-if="createModal.activeTab === 'experience'">
                  <h4 class="create-section-title">Kinh nghiệm giảng dạy</h4>
                  <textarea
                    v-model="createModal.teachingExperience"
                    class="form-input form-textarea"
                    rows="8"
                    maxlength="500"
                    placeholder="VD: 3 năm dạy Scratch tại trung tâm ABC, từng phụ trách CLB Robotics..."
                  />
                  <p class="char-counter">
                    {{ (createModal.teachingExperience || '').length }}/500
                  </p>
                </template>

                <!-- Tab: Trạng thái -->
                <template v-else-if="createModal.activeTab === 'status'">
                  <h4 class="create-section-title">Trạng thái</h4>
                  <span class="badge badge--active badge--lg">Đang hoạt động</span>
                </template>

                <p v-if="createModal.error" class="form-error">{{ createModal.error }}</p>
              </div>
            </div>

            <div class="modal__footer">
              <button class="btn btn--ghost" @click="createModal.open = false">Hủy</button>
              <button class="btn btn--primary" :disabled="createModal.saving" @click="submitCreate">
                <span v-if="createModal.saving" class="spinner spinner--sm" />
                {{ createModal.saving ? 'Đang tạo…' : 'Tạo giáo viên' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- ═══════════════════════════════════════════════
         MODAL: XÁC NHẬN XÓA
    ═══════════════════════════════════════════════ -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="confirmDelete.open" class="overlay" @click.self="confirmDelete.open = false">
          <div class="modal modal--sm">
            <div class="modal__icon modal__icon--warn">❌</div>
            <h3 class="modal__title">Bạn chắc chắn muốn xóa không?</h3>
            <p class="modal__body">
              {{ selectedIds.length }} giáo viên sẽ bị xóa khỏi danh sách chính, và có thể khôi phục
              lại trong mục <strong>Lịch sử</strong>.
            </p>
            <div class="modal__footer">
              <button class="btn btn--ghost" @click="confirmDelete.open = false">Không</button>
              <button class="btn btn--danger" @click="confirmDoDelete">Có</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- ═══════════════════════════════════════════════
         MODAL: XÁC NHẬN XÓA VĨNH VIỄN (từ thùng rác)
    ═══════════════════════════════════════════════ -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="confirmPurge.open" class="overlay" @click.self="closePurge">
          <div class="modal modal--sm">
            <div class="modal__icon modal__icon--warn">⚠️</div>
            <h3 class="modal__title">Xóa vĩnh viễn giáo viên "{{ confirmPurge.name }}"?</h3>
            <p class="modal__body">Are you <strong> SURE ?</strong></p>
            <p class="modal__body">
              Gõ lại số điện thoại <strong>"{{ confirmPurge.phone }}"</strong> để xác nhận:
            </p>
            <input
              v-model="confirmPurge.typedName"
              type="text"
              class="form-input"
              :placeholder="confirmPurge.phone"
              @keyup.enter="confirmDoPurge"
            />
            <div class="modal__footer">
              <button class="btn btn--ghost" @click="closePurge">Hủy</button>
              <button
                class="btn btn--danger"
                :disabled="!purgeConfirmValid || confirmPurge.purging"
                @click="confirmDoPurge"
              >
                {{ confirmPurge.purging ? 'Đang xóa…' : 'Xóa vĩnh viễn' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- ═══════════════════════════════════════════════
         MODAL: SỬA GIÁO VIÊN (tab trái + form phải — giống hệt modal Tạo)
    ═══════════════════════════════════════════════ -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="editModal.open" class="overlay" @click.self="editModal.open = false">
          <div class="modal modal--xl">
            <div class="modal__head">
              <h3 class="modal__title">Chỉnh sửa Giáo viên</h3>
              <button class="modal__close" @click="editModal.open = false">❌</button>
            </div>

            <div class="create-body">
              <!-- Menu trái -->
              <div class="create-nav">
                <button
                  v-for="tab in editTabs"
                  :key="tab.key"
                  :class="[
                    'create-nav__item',
                    editModal.activeTab === tab.key && 'create-nav__item--active',
                  ]"
                  @click="editModal.activeTab = tab.key"
                >
                  <SvgIcon :name="tab.icon" :size="16" /> {{ tab.label }}
                </button>
              </div>

              <!-- Form phải -->
              <div class="create-form">
                <!-- Tab: Hồ sơ giáo viên -->
                <template v-if="editModal.activeTab === 'profile'">
                  <h4 class="create-section-title">Thông tin cá nhân</h4>

                  <div class="form-row">
                    <div class="form-field">
                      <label class="form-label">
                        Tên đăng nhập <span class="req">*</span>
                        <input
                          v-model="editModal.form.username"
                          class="form-input"
                          placeholder="VD: gv.nguyenvana"
                        />
                      </label>
                    </div>

                    <div class="form-field">
                      <label class="form-label">
                        Email <span class="req">*</span>
                        <input
                          v-model="editModal.form.email"
                          class="form-input"
                          placeholder="VD: gv@tsdms.local"
                        />
                      </label>
                    </div>

                    <div class="form-field">
                      <label class="form-label">
                        Mật khẩu
                        <input
                          v-model="editModal.form.password"
                          :type="showPassword ? 'text' : 'password'"
                          class="form-input"
                          :class="{ 'form-input--error': editFieldErrors.password }"
                          placeholder="Để trống nếu không đổi mật khẩu"
                          autocomplete="new-password"
                          @blur="validateEditPassword"
                        />
                      </label>
                      <span v-if="editFieldErrors.password" class="field-error">{{
                        editFieldErrors.password
                      }}</span>

                      <label style="margin-top: 4px; display: flex; align-items: center; gap: 6px">
                        <input type="checkbox" v-model="showPassword" /> Hiện mật khẩu
                      </label>
                    </div>
                  </div>

                  <div class="form-row">
                    <div class="form-field">
                      <label class="form-label"
                        >Họ và tên đệm <span class="req">*</span>
                        <input
                          v-model="editModal.form.lastName"
                          class="form-input"
                          :class="{ 'form-input--error': editFieldErrors.lastName }"
                          placeholder="VD: Trần Nguyễn Văn"
                          @blur="validateEditLastName"
                        />
                      </label>
                      <span v-if="editFieldErrors.lastName" class="field-error">{{
                        editFieldErrors.lastName
                      }}</span>
                    </div>
                    <div class="form-field">
                      <label class="form-label"
                        >Tên gọi <span class="req">*</span>
                        <input
                          v-model="editModal.form.firstName"
                          class="form-input"
                          :class="{ 'form-input--error': editFieldErrors.firstName }"
                          placeholder="VD: A"
                          @blur="validateEditFirstName"
                        />
                      </label>
                      <span v-if="editFieldErrors.firstName" class="field-error">{{
                        editFieldErrors.firstName
                      }}</span>
                    </div>
                  </div>

                  <div class="form-row">
                    <label class="form-label"
                      >Chi nhánh <span class="req">*</span>
                      <select v-model="editModal.form.branchId" class="form-input">
                        <option value="">-- Chọn chi nhánh --</option>
                        <option v-for="b in branches" :key="b.id" :value="b.id">
                          {{ b.name }}
                        </option>
                      </select>
                    </label>
                    <label class="form-label"
                      >Giới tính
                      <select v-model="editModal.form.gender" class="form-input">
                        <option :value="null">-- Chọn --</option>
                        <option :value="true">Nam</option>
                        <option :value="false">Nữ</option>
                      </select>
                    </label>
                  </div>

                  <div class="form-row">
                    <div class="form-field">
                      <label class="form-label"
                        >Số điện thoại
                        <input
                          v-model="editModal.form.phone"
                          class="form-input"
                          :class="{ 'form-input--error': editFieldErrors.phone }"
                          placeholder="VD: 0901234567"
                          @blur="validateEditPhone"
                        />
                      </label>
                      <span v-if="editFieldErrors.phone" class="field-error">{{
                        editFieldErrors.phone
                      }}</span>
                    </div>
                    <div class="form-field">
                      <label class="form-label"
                        >Số CCCD
                        <input
                          v-model="editModal.form.idCardNo"
                          class="form-input"
                          :class="{ 'form-input--error': editFieldErrors.idCardNo }"
                          placeholder=" 12 số"
                          @blur="validateEditIdCard"
                        />
                      </label>
                      <span v-if="editFieldErrors.idCardNo" class="field-error">{{
                        editFieldErrors.idCardNo
                      }}</span>
                    </div>
                  </div>

                  <div class="form-row">
                    <div class="form-field">
                      <label class="form-label"
                        >Ngày sinh
                        <DateField
                          v-model="editModal.form.dateOfBirth"
                          :invalid="!!editFieldErrors.dateOfBirth"
                          @update:model-value="validateEditDates"
                        />
                      </label>
                      <span v-if="editFieldErrors.dateOfBirth" class="field-error">{{
                        editFieldErrors.dateOfBirth
                      }}</span>
                    </div>
                    <div class="form-field">
                      <label class="form-label"
                        >Ngày vào làm
                        <DateField
                          v-model="editModal.form.hireDate"
                          :invalid="!!editFieldErrors.hireDate"
                          @update:model-value="validateEditDates"
                        />
                      </label>
                      <span v-if="editFieldErrors.hireDate" class="field-error">{{
                        editFieldErrors.hireDate
                      }}</span>
                    </div>
                  </div>

                  <label class="form-label"
                    >Loại hình
                    <select v-model="editModal.form.employmentType" class="form-input">
                      <option value="">-- Chọn loại hình hợp đồng --</option>
                      <option value="CO_HUU">Cơ hữu</option>
                      <option value="THINH_GIANG">Thỉnh giảng</option>
                    </select>
                  </label>
                  <label class="form-label"
                    >Địa chỉ
                    <input
                      v-model="editModal.form.address"
                      class="form-input"
                      placeholder="Địa chỉ thường trú"
                    />
                  </label>
                </template>

                <!-- Tab: Bằng cấp -->
                <template v-else-if="editModal.activeTab === 'degree'">
                  <h4 class="create-section-title">Đã lưu</h4>
                  <p v-if="!editModal.existingCerts.length" class="create-section-hint">
                    Chưa có bằng cấp nào.
                  </p>
                  <div v-for="c in editModal.existingCerts" :key="c.id" class="existing-doc">
                    <div>
                      <strong>{{ c.name }}</strong>
                      <span v-if="!c.fileUrl" class="existing-doc__nofile">— chưa có file kèm</span>
                    </div>
                    <div class="existing-doc__actions">
                      <button
                        v-if="c.fileUrl"
                        class="doc-view"
                        title="Xem file PDF"
                        :disabled="viewingCertId === c.id"
                        @click="viewCert(c)"
                      >
                        {{ viewingCertId === c.id ? 'Đang mở…' : 'Xem' }}
                      </button>
                      <button class="doc-remove" title="Xóa" @click="removeExistingCert(c.id)">
                        ➖
                      </button>
                    </div>
                  </div>

                  <h4 class="create-section-title create-section-title--gap">Thêm bằng cấp mới</h4>
                  <div v-for="(d, i) in editModal.newDegrees" :key="i" class="degree-row">
                    <div class="degree-row__fields">
                      <label class="form-label form-label--sm"
                        >Trình độ chuyên môn
                        <select v-model="d.level" class="form-input">
                          <option value="">-- Chọn trình độ --</option>
                          <option v-for="lv in DEGREE_LEVELS" :key="lv" :value="lv">
                            {{ lv }}
                          </option>
                        </select>
                      </label>
                      <label class="form-label form-label--sm"
                        >Chuyên ngành
                        <select v-model="d.major" class="form-input">
                          <option value="">-- Chọn chuyên ngành --</option>
                          <optgroup v-for="g in MAJOR_GROUPS" :key="g.label" :label="g.label">
                            <option v-for="m in g.options" :key="m" :value="m">{{ m }}</option>
                          </optgroup>
                          <option :value="MAJOR_OTHER">Khác (tự nhập)…</option>
                        </select>
                      </label>
                      <input
                        v-if="d.major === MAJOR_OTHER"
                        v-model="d.majorOther"
                        class="form-input"
                        placeholder="Nhập chuyên ngành"
                      />
                      <label class="doc-file">
                        <SvgIcon name="assignment" :size="14" />
                        {{ d.file ? d.file.name : 'Chọn PDF' }}
                        <input
                          type="file"
                          accept="application/pdf"
                          hidden
                          @change="onPickFile(d, $event)"
                        />
                      </label>
                    </div>
                    <button
                      v-if="editModal.newDegrees.length > 1"
                      class="doc-remove"
                      @click="removeDoc(editModal.newDegrees, i)"
                    >
                      ➖
                    </button>
                  </div>
                  <button class="btn-add-row" @click="addDoc(editModal.newDegrees, emptyDegree)">
                    <SvgIcon name="plus" :size="14" /> Thêm bằng cấp
                  </button>
                </template>

                <!-- Tab: Kinh nghiệm -->
                <template v-else-if="editModal.activeTab === 'experience'">
                  <h4 class="create-section-title">Kinh nghiệm giảng dạy</h4>
                  <p class="create-section-hint">Không bắt buộc — ghi chú nhanh.</p>
                  <textarea
                    v-model="editModal.teachingExperience"
                    class="form-input form-textarea"
                    rows="8"
                    maxlength="500"
                    placeholder="VD: 3 năm dạy ... tại ... từng phụ trách CLB ..."
                  />
                  <p class="char-counter">{{ (editModal.teachingExperience || '').length }}/500</p>
                </template>

                <!-- Tab: Trạng thái -->
                <template v-else-if="editModal.activeTab === 'status'">
                  <h4 class="create-section-title">Trạng thái</h4>
                  <select
                    v-model="editModal.form.status"
                    class="form-input"
                    style="max-width: 280px"
                  >
                    <option value="ACTIVE">Đang hoạt động</option>
                    <option value="RETIRED">Ngừng hoạt động</option>
                    <option value="SUSPENDED">Đã nghỉ phép</option>
                  </select>
                </template>

                <p v-if="editModal.error" class="form-error">{{ editModal.error }}</p>
              </div>
            </div>

            <div class="modal__footer">
              <button class="btn btn--ghost" @click="editModal.open = false">Hủy</button>
              <button class="btn btn--primary" :disabled="editModal.saving" @click="saveEdit">
                <span v-if="editModal.saving" class="spinner spinner--sm" />
                {{ editModal.saving ? 'Đang lưu…' : 'Lưu thay đổi' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
/* ════ Biến màu kế thừa từ design system ════ */
.tl {
  max-width: 1280px;
  position: relative;
}

/* ── Header ── */
.tl__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.4rem;
}
.tl__title {
  font-size: 1.4rem;
  font-weight: 700;
  color: var(--a-text);
  margin: 0 0 0.2rem;
}
.tl__sub {
  font-size: 0.85rem;
  color: var(--a-text-muted);
  margin: 0;
}
.tl__header-actions {
  display: flex;
  gap: 0.5rem;
  flex-shrink: 0;
  flex-wrap: wrap;
}
.btn-tab {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.5rem 1rem;
  border: 1.5px solid var(--a-border);
  border-radius: 9px;
  background: var(--c-surface);
  color: var(--a-text-muted);
  font-size: 0.86rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-tab:hover {
  border-color: var(--c-primary);
  color: var(--c-primary);
}
.btn-tab--active {
  background: var(--grad-primary);
  border-color: transparent;
  color: #fff;
}
.btn-add {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.5rem 1.1rem;
  border: none;
  border-radius: 9px;
  background: var(--grad-primary);
  color: #fff;
  font-size: 0.86rem;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 6px 14px rgba(249, 115, 22, 0.28);
  transition:
    filter 0.15s,
    transform 0.15s;
}
.btn-add:hover {
  filter: brightness(1.06);
  transform: translateY(-1px);
}

/* ── Filters ── */
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.65rem;
  margin-bottom: 1.4rem;
  padding: 1rem 1.1rem;
  background: var(--c-surface);
  border: 1px solid var(--a-border);
  border-radius: 12px;
}
.filter-total {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.1rem;
  padding: 0.5rem 1.1rem;
  background: var(--grad-primary);
  border-radius: 10px;
  color: #fff;
  flex-shrink: 0;
}
.filter-total__num {
  font-size: 1.15rem;
  font-weight: 800;
  line-height: 1;
}
.filter-total__label {
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 0.4px;
  text-transform: uppercase;
  opacity: 0.92;
}
.filter-search {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex: 1;
  min-width: 200px;
  height: 38px;
  padding: 0 0.75rem;
  background: var(--a-bg);
  border: 1px solid var(--a-border);
  border-radius: 8px;
  color: var(--a-text-muted);
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}
.filter-search:focus-within {
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.12);
}
.filter-search input {
  border: none;
  background: transparent;
  outline: none;
  width: 100%;
  font-size: 0.875rem;
  color: var(--a-text);
}
.filter-select {
  height: 38px;
  padding: 0 0.75rem;
  background: var(--a-bg);
  border: 1px solid var(--a-border);
  border-radius: 8px;
  font-size: 0.85rem;
  color: var(--a-text);
  cursor: pointer;
  outline: none;
  transition: border-color 0.15s;
}
.filter-select:focus {
  border-color: var(--c-primary);
}

.btn-delete-toggle {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.45rem 0.9rem;
  background: transparent;
  border: 1.5px solid rgba(239, 68, 68, 0.45);
  border-radius: 8px;
  color: #dc2626;
  font-size: 0.84rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  margin-left: auto;
}
.btn-delete-toggle:hover,
.btn-delete-toggle--active {
  background: rgba(239, 68, 68, 0.09);
  border-color: #dc2626;
}
:root[data-theme='dark'] .btn-delete-toggle {
  color: #f87171;
}
.btn-confirm-delete {
  padding: 0.45rem 1rem;
  background: #dc2626;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 0.84rem;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s;
}
.btn-confirm-delete:hover {
  background: #b91c1c;
}

/* ── Loading / Empty ── */
.tl__loading {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  color: var(--a-text-muted);
  font-size: 0.9rem;
  padding: 2rem 0;
}
.tl__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  padding: 3rem 0;
  color: var(--a-text-muted);
  opacity: 0.55;
  text-align: center;
}

/* ── Spinner ── */
.spinner {
  display: inline-block;
  width: 20px;
  height: 20px;
  border: 2.5px solid var(--a-border);
  border-top-color: var(--c-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
.spinner--sm {
  width: 14px;
  height: 14px;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* ── Bảng danh sách giáo viên ── */
.table-wrap {
  overflow-x: auto;
  background: var(--c-surface);
  border: 1px solid var(--a-border);
  border-radius: 12px;
}
.teacher-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
  min-width: 760px;
}
.teacher-table th {
  padding: 0.75rem 1rem;
  text-align: left;
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--a-text-muted);
  background: var(--a-bg);
  border-bottom: 1px solid var(--a-border);
  white-space: nowrap;
}
.teacher-table td {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--a-border);
  color: var(--a-text);
  vertical-align: middle;
}
.teacher-table tr:last-child td {
  border-bottom: none;
}
.t-row {
  transition: background 0.12s;
  cursor: pointer;
}
.t-row:hover {
  background: var(--a-bg);
}
.t-row--selected {
  background: rgba(239, 68, 68, 0.07);
}
.col-check {
  width: 36px;
}
.col-action {
  width: 130px;
}

/* Tick chọn (bảng) */
.tick {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border: 2px solid var(--a-border);
  border-radius: 6px;
  background: var(--c-surface);
  transition: all 0.15s;
  cursor: pointer;
}
.tick--checked {
  background: #dc2626;
  border-color: #dc2626;
  color: #fff;
}

/* Tên + avatar */
.t-name-cell {
  display: flex;
  align-items: center;
  gap: 0.65rem;
}
.t-avatar {
  position: relative;
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  color: #fff;
  font-size: 0.95rem;
  font-weight: 700;
  flex-shrink: 0;
}
.t-avatar-dot {
  position: absolute;
  bottom: -1px;
  right: -1px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid var(--c-surface);
}
.dot--active {
  background: #22c55e;
}
.dot--off {
  background: #94a3b8;
}
.t-name {
  font-weight: 700;
  color: var(--a-text);
  white-space: nowrap;
}
.t-id {
  font-size: 0.74rem;
  color: var(--a-text-muted);
}

/* Liên hệ */
.t-contact {
  white-space: nowrap;
}
.t-contact--muted {
  color: var(--a-text-muted);
  font-size: 0.8rem;
  margin-top: 0.1rem;
}

/* Badge */
.badge {
  display: inline-flex;
  align-items: center;
  padding: 0.15rem 0.55rem;
  border-radius: 20px;
  font-size: 0.72rem;
  font-weight: 700;
  white-space: nowrap;
}
.badge--active {
  background: #dcfce7;
  color: #166534;
}
.badge--retired {
  background: var(--c-surface-2);
  color: #64748b;
}
.badge--suspended {
  background: #fef9c3;
  color: #854d0e;
}
.badge--emp {
  background: #eff6ff;
  color: #1d4ed8;
}
.badge--branch {
  background: #eff6ff;
  color: #1d4ed8;
}
.badge--lg {
  font-size: 0.85rem;
  padding: 0.4rem 0.9rem;
}

/* Hành động (bảng) */
.row-actions {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}
.ra-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  line-height: 0;
}
.ra-btn--view {
  background: #eff6ff;
  color: #2563eb;
}
.ra-btn--edit {
  background: #f0fdf4;
  color: #16a34a;
}
.ra-btn--delete {
  background: #fef2f2;
  color: #dc2626;
}
.ra-btn:hover {
  filter: brightness(0.92);
  transform: scale(1.08);
}

/* ── Pagination ── */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  margin-top: 1.2rem;
  flex-wrap: wrap;
}
.pg-btn {
  min-width: 38px;
  height: 38px;
  border: 1px solid var(--a-border);
  border-radius: 8px;
  background: var(--c-surface);
  color: var(--a-text);
  cursor: pointer;
  font-size: 0.85rem;
  font-weight: 600;
  transition: all 0.15s;
}
.pg-btn:hover:not(:disabled) {
  background: var(--c-primary);
  border-color: var(--c-primary);
  color: #fff;
}
.pg-btn--active {
  background: var(--grad-primary);
  border-color: transparent;
  color: #fff;
}
.pg-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.page-input {
  width: 64px;
  height: 38px;
  border: 1px solid var(--a-border);
  border-radius: 8px;
  text-align: center;
  background: var(--a-bg);
  color: var(--a-text);
}

/* ── Trash table ── */
.trash-header {
  margin-bottom: 1.2rem;
}
.trash-title {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--a-text);
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0 0 0.3rem;
}
.trash-sub {
  font-size: 0.84rem;
  color: var(--a-text-muted);
  margin: 0;
}
.btn-restore {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 0.75rem;
  background: #f0fdf4;
  color: #16a34a;
  border: 1px solid #bbf7d0;
  border-radius: 7px;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-restore:hover {
  background: #dcfce7;
}
.trash-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.btn-purge {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 0.75rem;
  background: #fef2f2;
  color: #dc2626;
  border: 1px solid #fecaca;
  border-radius: 7px;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-purge:hover {
  background: #fee2e2;
}

/* ── Modal ── */
.overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(15, 23, 42, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
}
.modal {
  background: var(--c-surface);
  border-radius: 18px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.18);
  width: 100%;
  max-height: 90vh;
  overflow-y: auto;
  padding: 1.75rem 2rem;
}
.modal--sm {
  max-width: 420px;
  text-align: center;
}
.modal--lg {
  max-width: 680px;
}
.modal--xl {
  max-width: 880px;
  padding: 1.75rem 0;
}
.modal--xl .modal__head {
  padding: 0 2rem;
}
.modal--xl .modal__footer {
  padding: 1.25rem 2rem 0;
  margin-top: 1.5rem;
}
.modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1.4rem;
}
.modal__title {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--a-text);
  margin: 0;
}
.modal__close {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 8px;
  background: var(--a-bg);
  color: var(--a-text-muted);
  cursor: pointer;
}
.modal__close:hover {
  background: #fee2e2;
  color: #dc2626;
}
.modal__icon--warn {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: #fef2f2;
  color: #dc2626;
  margin: 0 auto 1rem;
}
.modal__body {
  font-size: 0.9rem;
  color: var(--a-text-muted);
  margin: 0.5rem 0 0;
}
.modal__footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 1.5rem;
  padding-top: 1.25rem;
  border-top: 1px solid var(--a-border);
}
.modal--sm .modal__footer {
  justify-content: center;
}

/* ── Modal Thêm/Sửa giáo viên: menu trái + form phải ── */
.create-body {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: 0;
  border-top: 1px solid var(--a-border);
  min-height: 420px;
}
.create-nav {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 1rem 0.75rem;
  border-right: 1px solid var(--a-border);
  background: var(--a-bg);
}
.create-nav__item {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.6rem 0.75rem;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--a-text-muted);
  font-size: 0.84rem;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: all 0.15s;
}
.create-nav__item:hover {
  background: var(--c-surface);
  color: var(--c-primary);
}
.create-nav__item--active {
  background: var(--grad-primary);
  color: #fff;
}
.create-form {
  padding: 1.25rem 2rem;
}
.create-section-title {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--a-text);
  margin: 0 0 0.2rem;
}
.create-section-title--gap {
  margin-top: 1.4rem;
}
.create-section-hint {
  font-size: 0.8rem;
  color: var(--a-text-muted);
  margin: 0 0 0.9rem;
}
/* Ô Kinh nghiệm dừng nhận chữ đúng ở 500 ký tự (bằng NVARCHAR(500) dưới DB).
   Không có số đếm thì người dùng gõ tới hạn sẽ tưởng bàn phím hỏng. */
.char-counter {
  font-size: 0.78rem;
  color: var(--a-text-muted);
  text-align: right;
  margin: 0.35rem 0 0;
}

/* Bằng cấp / Chứng chỉ: 1 dòng nhiều ô */
.doc-row {
  display: grid;
  grid-template-columns: 1.4fr 1fr 0.9fr 1fr auto;
  gap: 0.5rem;
  margin-bottom: 0.6rem;
  align-items: center;
}
.doc-file {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  height: 38px;
  padding: 0 0.7rem;
  border: 1.5px dashed var(--a-border);
  border-radius: 9px;
  font-size: 0.8rem;
  color: var(--a-text-muted);
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition:
    border-color 0.15s,
    color 0.15s;
}
.doc-file:hover {
  border-color: var(--c-primary);
  color: var(--c-primary);
}
.doc-remove {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border: none;
  border-radius: 8px;
  background: #fef2f2;
  color: #dc2626;
  cursor: pointer;
}
.btn-add-row {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.5rem 0.9rem;
  margin-top: 0.3rem;
  border: 1.5px dashed var(--a-border);
  border-radius: 9px;
  background: transparent;
  color: var(--a-text-muted);
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-add-row:hover {
  border-color: var(--c-primary);
  color: var(--c-primary);
}

/* Danh sách bằng cấp/chứng chỉ ĐÃ LƯU (modal Sửa) */
.existing-doc {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  padding: 0.55rem 0.8rem;
  margin-bottom: 0.5rem;
  background: var(--a-bg);
  border: 1px solid var(--a-border);
  border-radius: 9px;
  font-size: 0.84rem;
  color: var(--a-text);
}

/* Buttons */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.6rem 1.4rem;
  border-radius: 10px;
  font-size: 0.88rem;
  font-weight: 700;
  cursor: pointer;
  border: none;
  transition: all 0.15s;
}
.btn--primary {
  background: var(--grad-primary);
  color: #fff;
}
.btn--primary:hover {
  filter: brightness(1.08);
}
.btn--primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn--ghost {
  background: var(--a-bg);
  color: var(--a-text-muted);
  border: 1.5px solid var(--a-border);
}
.btn--ghost:hover {
  border-color: var(--c-primary);
  color: var(--c-primary);
}
.btn--danger {
  background: #dc2626;
  color: #fff;
}
.btn--danger:hover {
  background: #b91c1c;
}
.btn--danger:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background: #dc2626;
}

/* ── Detail modal ── */
.detail-hero {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.4rem;
}
.detail-avatar {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  color: #fff;
  font-size: 1.6rem;
  font-weight: 800;
  flex-shrink: 0;
}
.detail-name {
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--a-text);
}
.detail-meta {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
  margin-top: 0.4rem;
}
.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem 1.5rem;
  margin-bottom: 1rem;
}
.dg-item {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}
.dg-item--full {
  grid-column: 1 / -1;
}
.dg-label {
  font-size: 0.73rem;
  text-transform: uppercase;
  color: var(--a-text-muted);
  font-weight: 600;
}
.detail-section {
  margin-top: 1.2rem;
  padding-top: 1rem;
  border-top: 1px solid var(--a-border);
}
.detail-section-title {
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--a-text);
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin: 0 0 0.75rem;
}
.cert-list {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}
.cert-item {
  padding: 0.5rem 0.75rem;
  background: var(--a-bg);
  border-radius: 8px;
  font-size: 0.84rem;
  color: var(--a-text);
}

/* ── Edit / Create form ── */
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.85rem;
}
.form-label {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--a-text-muted);
  margin-bottom: 0.85rem;
}
.create-form .form-row {
  margin-bottom: 0;
}
.create-form .form-label {
  margin-bottom: 0.85rem;
}
.req {
  color: #dc2626;
}
.form-input {
  width: 100%;
  padding: 0.55rem 0.75rem;
  border: 1.5px solid var(--a-border);
  border-radius: 9px;
  font-size: 0.88rem;
  color: var(--a-text);
  background: var(--a-bg);
  outline: none;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
  box-sizing: border-box;
}
.form-input:focus {
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.12);
  background: var(--c-surface);
}
/* Ô chọn ngày dùng chung (DateField) kẻ theo .form-input để đứng cạnh các ô khác cho đều.
   Component có style scoped riêng nên phải :deep() mới với tới được thẻ input bên trong. */
:deep(.df__input) {
  padding: 0.55rem 2rem 0.55rem 0.75rem;
  border-width: 1.5px;
  border-color: var(--a-border);
  border-radius: 9px;
  background: var(--a-bg);
  color: var(--a-text);
  font-size: 0.88rem;
}
:deep(.df__input:focus) {
  border-color: var(--c-primary);
  background: var(--c-surface);
}
.form-textarea {
  resize: vertical;
  font-family: inherit;
  line-height: 1.5;
}
.form-error {
  font-size: 0.84rem;
  color: #dc2626;
  background: #fef2f2;
  border-radius: 8px;
  padding: 0.5rem 0.75rem;
  margin: 0.85rem 0 0;
}
.form-field {
  display: flex;
  flex-direction: column;
}
.form-field .form-label {
  margin-bottom: 0;
}
.form-input--error {
  border-color: #dc2626 !important;
}
.field-error {
  font-size: 0.78rem;
  color: #dc2626;
  margin-top: 0.25rem;
}

/* ── Toast ── */
.toast {
  position: fixed;
  top: 1.25rem;
  right: 1.25rem;
  z-index: 999;
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.75rem 1.2rem;
  border-radius: 12px;
  font-size: 0.88rem;
  font-weight: 600;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.14);
}
.toast--success {
  background: #16a34a;
  color: #fff;
}
.toast--error {
  background: #dc2626;
  color: #fff;
}
.toast-enter-active,
.toast-leave-active {
  transition: all 0.25s;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.95);
}

/* Modal transition */
.modal-enter-active,
.modal-leave-active {
  transition: all 0.22s;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
  transform: scale(0.95);
}

/* Responsive */
@media (max-width: 640px) {
  .detail-grid,
  .form-row {
    grid-template-columns: 1fr;
  }
  .tl__header {
    flex-direction: column;
  }
  .filter-total {
    flex-direction: row;
    gap: 0.4rem;
  }
}
@media (max-width: 760px) {
  .create-body {
    grid-template-columns: 1fr;
  }
  .create-nav {
    flex-direction: row;
    overflow-x: auto;
    border-right: none;
    border-bottom: 1px solid var(--a-border);
  }
  .doc-row {
    grid-template-columns: 1fr;
  }
}
</style>
