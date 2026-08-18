<script setup>
//    DÙNG CHUNG cho modal "Thêm" và "Sửa gviên"
//    Trước 2 modal này Giờ gộp lại 1 nơi, điều khiển qua prop `mode` ('create' | 'edit')
//    giao diện giữ nguyên so với bản gốc
import { reactive, ref, watch } from 'vue'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import DateField from '@/components/ui/DateField.vue'
import { teacherApi } from '@/api/teacher'
import { authApi } from '@/api/auth'
import { isStrongPassword, PASSWORD_HINT } from '@/utils/password'

const props = defineProps({
  open: { type: Boolean, default: false },
  mode: { type: String, required: true }, // 'create' | 'edit'
  // Khi mode = 'edit': object giáo viên lấy từ dòng bảng đang chọn (đã có sẵn ở list)
  teacher: { type: Object, default: null },
  branches: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:open', 'saved'])

/* ── Regex / hằng số validate — dùng chung cho cả 2 mode ── */
const NAME_RE = /^[\p{L} ]+$/u
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PHONE_RE = /^(\+84|0)\d{9,10}$/
const CCCD_RE = /^\d{9}(\d{3})?$/
const MIN_WORKING_AGE = 18
const EXPERIENCE_MAX = 500

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

/* ── Tabs — giống hệt nhau cho cả Tạo & Sửa ── */
const tabs = [
  { key: 'profile', label: 'Hồ sơ giáo viên', icon: 'teacher' },
  { key: 'degree', label: 'Bằng cấp', icon: 'assignment' },
  { key: 'experience', label: 'Kinh nghiệm', icon: 'history' },
  { key: 'status', label: 'Trạng thái', icon: 'attendance' },
]

/* ── Bằng cấp: nhóm chuyên ngành cố định theo 4 mảng môn học ── */
const DEGREE_LEVELS = ['Thạc sỹ', 'Cử nhân', 'Cao đẳng', 'Trung cấp']
const MAJOR_GROUPS = [
  {
    label: 'Tin học',
    options: [
      'Công nghệ thông tin', 'Khoa học máy tính', 'Kỹ thuật phần mềm', 'Hệ thống thông tin',
      'Mạng máy tính và truyền thông dữ liệu', 'An toàn thông tin', 'Kỹ thuật máy tính',
      'Tin học ứng dụng', 'Sư phạm Tin học',
    ],
  },
  { label: 'Tiếng Anh', options: ['Sư phạm Tiếng Anh', 'Ngôn ngữ Anh'] },
  {
    label: 'STEM - AI',
    options: ['Trí tuệ nhân tạo', 'Khoa học máy tính', 'Cơ điện tử', 'Kỹ thuật Robot', 'Kỹ thuật điều khiển & tự động hóa'],
  },
  { label: 'Kĩ năng sống', options: ['Sư phạm Giáo dục công dân', 'Giáo dục kỹ năng sống'] },
]
const MAJOR_OTHER = '__OTHER__'

function emptyDegree() {
  return { level: '', major: '', majorOther: '', file: null }
}
function degreeName(d) {
  const major = d.major === MAJOR_OTHER ? d.majorOther.trim() : d.major
  return [d.level, major].filter(Boolean).join(' - ')
}
function addDoc(list) {
  list.push(emptyDegree())
}
function removeDoc(list, i) {
  list.splice(i, 1)
}
function onPickFile(doc, e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (file.type !== 'application/pdf') {
    localError.value = 'Chỉ nhận file PDF'
    e.target.value = ''
    return
  }
  doc.file = file
}
// Lỗi nhỏ khi chọn sai loại file — hiển thị tạm qua form-error chung.
const localError = ref('')

/* ══════════════════════════════════════════════════════════
   STATE DÙNG CHUNG CHO CẢ 2 MODE
══════════════════════════════════════════════════════════ */
const saving = ref(false)
const error = ref('')
const activeTab = ref('profile')
const showPassword = ref(false) // chỉ dùng ở mode create

const account = reactive({ username: '', email: '', password: '' })
const profile = reactive({
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
  status: 'ACTIVE', // chỉ hiển thị chỉnh sửa ở mode edit
})
const existingCerts = ref([]) // chỉ có ở mode edit
const newDegrees = ref([emptyDegree()])
const teachingExperience = ref('')

const fieldErrors = reactive({
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

/* ── Validate (dùng chung, chỉ password là bắt buộc riêng cho create) ── */
function validateUsername() {
  const v = account.username.trim()
  if (!v) return (fieldErrors.username = 'Không được để trống'), false
  if (v.length < 3) return (fieldErrors.username = 'Ít nhất 3 ký tự'), false
  if (v.length > 50) return (fieldErrors.username = 'Tối đa 50 ký tự'), false
  fieldErrors.username = ''
  return true
}
function validateEmail() {
  const v = account.email.trim()
  if (!v) return (fieldErrors.email = 'Không được để trống'), false
  if (!EMAIL_RE.test(v)) return (fieldErrors.email = 'Email không hợp lệ (VD: gv@tsdms.local)'), false
  fieldErrors.email = ''
  return true
}
function validatePassword() {
  const v = account.password
  if (!v) return (fieldErrors.password = 'Không được để trống'), false
  if (!isStrongPassword(v)) return (fieldErrors.password = `Chưa đủ mạnh (${PASSWORD_HINT})`), false
  fieldErrors.password = ''
  return true
}
function validateLastName() {
  const v = profile.lastName.trim()
  if (!v) return (fieldErrors.lastName = 'Không được để trống'), false
  if (!NAME_RE.test(v)) return (fieldErrors.lastName = 'Chỉ được chứa chữ cái và khoảng trắng'), false
  fieldErrors.lastName = ''
  return true
}
function validateFirstName() {
  const v = profile.firstName.trim()
  if (!v) return (fieldErrors.firstName = 'Không được để trống'), false
  if (!NAME_RE.test(v)) return (fieldErrors.firstName = 'Chỉ được chứa chữ cái và khoảng trắng'), false
  fieldErrors.firstName = ''
  return true
}
function validatePhone() {
  const v = profile.phone.trim()
  if (!v) return (fieldErrors.phone = ''), true
  if (!PHONE_RE.test(v)) return (fieldErrors.phone = 'SĐT phải bắt đầu bằng 0 hoặc +84, có 10-11 chữ số'), false
  fieldErrors.phone = ''
  return true
}
function validateIdCard() {
  const v = profile.idCardNo.trim()
  if (!v) return (fieldErrors.idCardNo = ''), true
  if (!CCCD_RE.test(v)) return (fieldErrors.idCardNo = 'CCCD phải có 12 số'), false
  fieldErrors.idCardNo = ''
  return true
}
function validateDates() {
  fieldErrors.dateOfBirth = ''
  fieldErrors.hireDate = ''
  const err = checkDobHirePair(profile.dateOfBirth, profile.hireDate)
  if (err) {
    fieldErrors[err.field] = err.msg
    return false
  }
  return true
}

function validateAll() {
  const checks =
    props.mode === 'create'
      ? [validateUsername(), validateEmail(), validatePassword(), validateLastName(), validateFirstName(), validatePhone(), validateIdCard(), validateDates()]
      : [validateLastName(), validateFirstName(), validatePhone(), validateIdCard(), validateDates()]
  if (!profile.branchId) {
    error.value = 'Vui lòng chọn chi nhánh.'
    activeTab.value = 'profile'
    return false
  }
  if (!checks.every(Boolean)) {
    error.value = 'Vui lòng kiểm tra lại các trường được đánh dấu đỏ.'
    activeTab.value = 'profile'
    return false
  }
  return true
}

/* ══════════════════════════════════════════════════════════
   MỞ MODAL — khởi tạo state theo mode
══════════════════════════════════════════════════════════ */
async function init() {
  error.value = ''
  localError.value = ''
  saving.value = false
  activeTab.value = 'profile'
  showPassword.value = false
  Object.keys(fieldErrors).forEach((k) => (fieldErrors[k] = ''))
  newDegrees.value = [emptyDegree()]
  existingCerts.value = []

  if (props.mode === 'create') {
    Object.assign(account, { username: '', email: '', password: '' })
    Object.assign(profile, {
      branchId: props.branches[0]?.id || '',
      lastName: '',
      firstName: '',
      gender: null,
      phone: '',
      idCardNo: '',
      dateOfBirth: '',
      hireDate: '',
      address: '',
      employmentType: '',
      status: 'ACTIVE',
    })
    teachingExperience.value = ''
    return
  }

  // mode === 'edit'
  const t = props.teacher
  if (!t) return
  Object.assign(account, { username: '', email: '', password: '' })
  Object.assign(profile, {
    branchId: t.branchId,
    lastName: t.lastName,
    firstName: t.firstName,
    gender: t.gender,
    phone: t.phone || '',
    idCardNo: t.idCardNo || '',
    dateOfBirth: t.dateOfBirth || '',
    hireDate: t.hireDate || '',
    address: t.address || '',
    employmentType: t.employmentType || '',
    status: t.status,
  })
  teachingExperience.value = t.teachingExperience || ''

  try {
    const { data } = await teacherApi.get(t.id)
    existingCerts.value = data.certificates || []
  } catch {
    /* bằng cấp không tải được thì bỏ qua, không chặn form */
  }
  try {
    const { data } = await teacherApi.getAccount(t.id)
    account.username = data.username || ''
    account.email = data.email || ''
  } catch {
    /* tài khoản không tải được thì bỏ qua, không chặn form */
  }
}

watch(
  () => props.open,
  (v) => {
    if (v) init()
  },
)

/* ══════════════════════════════════════════════════════════
   BẰNG CẤP ĐÃ LƯU (chỉ mode edit)
══════════════════════════════════════════════════════════ */
const viewingCertId = ref(null)

async function removeExistingCert(certId) {
  try {
    await teacherApi.deleteCertificate(props.teacher.id, certId)
    existingCerts.value = existingCerts.value.filter((c) => c.id !== certId)
  } catch (e) {
    error.value = e?.response?.data?.message || 'Xóa bằng cấp thất bại'
  }
}

async function viewCert(cert) {
  viewingCertId.value = cert.id
  try {
    const response = await teacherApi.openCertificateFile(props.teacher.id, cert.id)
    const blobUrl = window.URL.createObjectURL(new Blob([response.data], { type: 'application/pdf' }))
    window.open(blobUrl, '_blank', 'noopener')
    setTimeout(() => window.URL.revokeObjectURL(blobUrl), 60_000)
  } catch (e) {
    error.value = e?.response?.data?.message || 'Không mở được file (có thể chưa có file đính kèm)'
  } finally {
    viewingCertId.value = null
  }
}

/* ══════════════════════════════════════════════════════════
   SUBMIT — Tạo mới HOẶC Lưu sửa, tùy props.mode
══════════════════════════════════════════════════════════ */
async function saveNewDegrees(teacherId) {
  const validOnes = newDegrees.value.filter((d) => d.level)
  for (const d of validOnes) {
    const { data: cert } = await teacherApi.addCertificate(teacherId, {
      name: degreeName(d),
      issuer: null,
      issueDate: null,
    })
    if (d.file) {
      try {
        await teacherApi.uploadCertificateFile(teacherId, cert.id, d.file)
      } catch {
        error.value = `Đã lưu "${degreeName(d)}" nhưng upload file thất bại, thử lại ở tab Bằng cấp`
      }
    }
  }
}

async function submitCreate() {
  let createdTeacherId = null
  try {
    const { data: userInfo } = await authApi.register({
      role: 'TEACHER',
      username: account.username,
      email: account.email,
      password: account.password,
      firstName: profile.firstName,
      lastName: profile.lastName,
      phone: profile.phone,
      branchId: Number(profile.branchId),
    })

    const { data: allTeachers } = await teacherApi.list()
    const created = allTeachers.find((t) => t.appUserId === userInfo.id)
    if (!created) throw new Error('Tạo tài khoản thành công nhưng không tìm thấy hồ sơ giáo viên vừa tạo.')
    createdTeacherId = created.id

    await teacherApi.update(created.id, {
      email: account.email,
      branchId: Number(profile.branchId),
      firstName: profile.firstName,
      lastName: profile.lastName,
      status: 'ACTIVE',
      employmentType: profile.employmentType || null,
      dateOfBirth: profile.dateOfBirth || null,
      hireDate: profile.hireDate || null,
      gender: profile.gender === '' ? null : profile.gender,
      idCardNo: profile.idCardNo || null,
      phone: profile.phone || null,
      address: profile.address || null,
      teachingExperience: teachingExperience.value || null,
    })

    await saveNewDegrees(created.id)

    account.password = '' // không giữ mật khẩu trong bộ nhớ FE sau khi đã dùng
    emit('saved', 'Đã thêm giáo viên mới thành công')
    emit('update:open', false)
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || 'Tạo giáo viên thất bại'
    if (createdTeacherId) {
      try {
        await teacherApi.delete(createdTeacherId)
        error.value = `${msg} — Đã tự rollback, bạn có thể thử lại.`
      } catch {
        error.value = `${msg} — Tài khoản đã được tạo. Vui lòng vào danh sách, bấm Sửa để bổ sung thông tin còn thiếu.`
      }
    } else {
      error.value = msg
    }
  }
}

async function submitEdit() {
  const teacherId = props.teacher.id
  try {
    await teacherApi.update(teacherId, {
      ...profile,
      email: account.email,
      username: account.username || '',
      branchId: Number(profile.branchId),
      gender: profile.gender === '' ? null : profile.gender,
      employmentType: profile.employmentType || null,
      teachingExperience: teachingExperience.value || null,
    })

    const accountPayload = {}
    if (account.username?.trim()) accountPayload.username = account.username.trim()
    if (account.email?.trim()) accountPayload.email = account.email.trim()
    if (Object.keys(accountPayload).length > 0) {
      await teacherApi.updateAccount(teacherId, accountPayload)
    }

    await saveNewDegrees(teacherId)

    emit('saved', 'Cập nhật giáo viên thành công')
    emit('update:open', false)
  } catch (e) {
    error.value = e?.response?.data?.message || 'Cập nhật thất bại'
  }
}

async function submit() {
  if (!validateAll()) return
  saving.value = true
  error.value = ''
  if (props.mode === 'create') await submitCreate()
  else await submitEdit()
  saving.value = false
}

function close() {
  emit('update:open', false)
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="open" class="overlay" @click.self="close">
        <div class="modal modal--xl">
          <div class="modal__head">
            <h3 class="modal__title">{{ mode === 'create' ? 'Thêm giáo viên mới' : 'Chỉnh sửa Giáo viên' }}</h3>
            <button class="modal__close" @click="close">❌</button>
          </div>

          <div class="create-body">
            <!-- Menu trái -->
            <div class="create-nav">
              <button
                v-for="tab in tabs" :key="tab.key"
                :class="['create-nav__item', activeTab === tab.key && 'create-nav__item--active']"
                @click="activeTab = tab.key"
              >
                <SvgIcon :name="tab.icon" :size="16" /> {{ tab.label }}
              </button>
            </div>

            <!-- Form phải -->
            <div class="create-form">
              <!-- Tab: Hồ sơ giáo viên -->
              <template v-if="activeTab === 'profile'">
                <template v-if="mode === 'create'">
                  <h4 class="create-section-title">Tài khoản đăng nhập</h4>
                  <p class="create-section-hint">Giáo viên cần 1 tài khoản để đăng nhập hệ thống.</p>
                </template>
                <h4 v-else class="create-section-title">Thông tin cá nhân</h4>

                <div class="form-row">
                  <div class="form-field">
                    <label class="form-label" >Tên đăng nhập <span class="req">*</span>
                      <input
                        v-model="account.username" class="form-input"
                        :class="{ 'form-input--error': fieldErrors.username }"
                        :readonly="mode === 'edit' ? false : false"
                        placeholder="VD: abc123"
                        @blur="mode === 'create' && validateUsername()"
                      />
                    </label>
                    <span v-if="fieldErrors.username" class="field-error">{{ fieldErrors.username }}</span>
                  </div>
                  <div class="form-field">
                    <label class="form-label" >Email <span class="req">*</span>
                      <input
                        v-model="account.email" type="email" class="form-input"
                        :class="{ 'form-input--error': fieldErrors.email }"
                        placeholder="VD: abc123@gmail.com"
                        @blur="mode === 'create' && validateEmail()"
                      />
                    </label>
                    <span v-if="fieldErrors.email" class="field-error">{{ fieldErrors.email }}</span>
                  </div>
                </div>

                <div v-if="mode === 'create'" class="form-row">
                  <div class="form-field">
                    <label class="form-label" >Mật khẩu <span class="req">*</span>
                      <div class="pwd-wrap">
                        <input
                          v-model="account.password"
                          :type="showPassword ? 'text' : 'password'"  class="form-input"
                          :class="{ 'form-input--error': fieldErrors.password }"
                          autocomplete="new-password"
                          placeholder="Đặt mật khẩu đăng nhập"
                          @blur="validatePassword"
                        />
                        <button
                          type="button" class="pwd-toggle"
                          :title="showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'"
                          @click="showPassword = !showPassword"
                        >
                          <SvgIcon :name="showPassword ? 'eye-off' : 'eye'" :size="16" />
                        </button>
                      </div>
                    </label>
                    <span v-if="fieldErrors.password" class="field-error">{{ fieldErrors.password }}</span>
                    <span v-else class="field-hint">{{ PASSWORD_HINT }}</span>
                  </div>
                </div>

                <h4 v-if="mode === 'create'" class="create-section-title create-section-title--gap">Thông tin cá nhân</h4>
                <div class="form-row">
                  <div class="form-field">
                    <label class="form-label" >Họ và tên đệm <span class="req">*</span>
                      <input
                        v-model="profile.lastName" class="form-input"
                        :class="{ 'form-input--error': fieldErrors.lastName }"
                        placeholder="VD: Trần Đức"
                        @blur="validateLastName"
                      />
                    </label>
                    <span v-if="fieldErrors.lastName" class="field-error">{{ fieldErrors.lastName }}</span>
                  </div>
                  <div class="form-field">
                    <label class="form-label" >Tên gọi <span class="req">*</span>
                      <input
                        v-model="profile.firstName" class="form-input"
                        :class="{ 'form-input--error': fieldErrors.firstName }" placeholder="VD: A Đức"
                        @blur="validateFirstName"
                      />
                    </label>
                    <span v-if="fieldErrors.firstName" class="field-error">{{ fieldErrors.firstName }}</span>
                  </div>
                </div>

                <div class="form-row">
                  <label class="form-label" >Chi nhánh <span class="req">*</span>
                    <select v-model="profile.branchId" class="form-input">
                      <option value="">-- Chọn chi nhánh --</option>
                      <option v-for="b in branches" :key="b.id" :value="b.id">{{ b.name }}</option>
                    </select>
                  </label>
                  <label class="form-label" >Giới tính
                    <select v-model="profile.gender" class="form-input">
                      <option :value="null">-- Chọn --</option>
                      <option :value="true">Nam</option>
                      <option :value="false">Nữ</option>
                    </select>
                  </label>
                </div>

                <div class="form-row">
                  <div class="form-field">
                    <label class="form-label" >Số điện thoại
                      <input
                        v-model="profile.phone" class="form-input"
                        :class="{ 'form-input--error': fieldErrors.phone }"
                        placeholder="VD: 0901234567"
                        @blur="validatePhone"
                      />
                    </label>
                    <span v-if="fieldErrors.phone" class="field-error">{{ fieldErrors.phone }}</span>
                  </div>
                  <div class="form-field">
                    <label class="form-label" >Số CCCD
                      <input
                        v-model="profile.idCardNo"  class="form-input"
                        :class="{ 'form-input--error': fieldErrors.idCardNo }"
                        placeholder="12 số"
                        @blur="validateIdCard"
                      />
                    </label>
                    <span v-if="fieldErrors.idCardNo" class="field-error">{{ fieldErrors.idCardNo }}</span>
                  </div>
                </div>

                <div class="form-row">
                  <div class="form-field">
                    <label class="form-label">Ngày sinh
                      <DateField v-model="profile.dateOfBirth" :invalid="!!fieldErrors.dateOfBirth" @update:model-value="validateDates" />
                    </label>
                    <span v-if="fieldErrors.dateOfBirth" class="field-error">{{ fieldErrors.dateOfBirth }}</span>
                  </div>
                  <div class="form-field">
                    <label class="form-label">Ngày vào làm
                      <DateField v-model="profile.hireDate" :invalid="!!fieldErrors.hireDate" @update:model-value="validateDates" />
                    </label>
                    <span v-if="fieldErrors.hireDate" class="field-error">{{ fieldErrors.hireDate }}</span>
                  </div>
                </div>

                <label class="form-label" >Loại hình
                  <select v-model="profile.employmentType" class="form-input">
                    <option value="">-- Chọn loại hình hợp đồng --</option>
                    <option value="CO_HUU">Cơ hữu</option>
                    <option value="THINH_GIANG">Thỉnh giảng</option>
                  </select>
                </label>
                <label class="form-label" >Địa chỉ
                  <input v-model="profile.address" class="form-input" placeholder="Địa chỉ thường trú" />
                </label>
              </template>

              <!-- Tab: Bằng cấp -->
              <template v-else-if="activeTab === 'degree'">
                <template v-if="mode === 'edit'">
                  <h4 class="create-section-title">Đã lưu</h4>
                  <p v-if="!existingCerts.length" class="create-section-hint">Chưa có bằng cấp nào.</p>
                  <div v-for="c in existingCerts" :key="c.id" class="existing-doc">
                    <div>
                      <strong>{{ c.name }}</strong>
                      <span v-if="!c.fileUrl" class="existing-doc__nofile">— chưa có file kèm</span>
                    </div>
                    <div class="existing-doc__actions">
                      <button v-if="c.fileUrl" class="doc-view" :disabled="viewingCertId === c.id" @click="viewCert(c)">
                        {{ viewingCertId === c.id ? 'Đang mở…' : 'Xem' }}
                      </button>
                      <button class="doc-remove" title="Xóa" @click="removeExistingCert(c.id)">➖</button>
                    </div>
                  </div>
                  <h4 class="create-section-title create-section-title--gap">Thêm bằng cấp mới</h4>
                </template>
                <template v-else>
                  <h4 class="create-section-title">Bằng cấp</h4>
                  <p class="create-section-hint">Có thể bỏ trống nếu chưa có, sau này thêm cũng được.</p>
                </template>

                <div v-for="(d, i) in newDegrees" :key="i" class="degree-row">
                  <div class="degree-row__fields">
                    <label class="form-label form-label--sm"
                      >Trình độ chuyên môn
                      <select v-model="d.level" class="form-input">
                        <option value="">-- Chọn trình độ --</option>
                        <option v-for="lv in DEGREE_LEVELS" :key="lv" :value="lv">{{ lv }}</option>
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
                      v-model="d.majorOther" class="form-input degree-row__span"
                      placeholder="Nhập chuyên ngành"
                    />
                    <label class="doc-file degree-row__span">
                      <SvgIcon name="assignment" :size="14" />
                      <span class="doc-file__name">{{ d.file ? d.file.name : 'Chọn PDF' }}</span>
                      <input type="file" accept="application/pdf" hidden @change="onPickFile(d, $event)" />
                    </label>
                  </div>
                  <button v-if="newDegrees.length > 1" class="doc-remove" title="Xóa dòng này" @click="removeDoc(newDegrees, i)">❌</button>

                </div>
                <button class="btn-add-row" @click="addDoc(newDegrees)">
                  <SvgIcon name="plus" :size="14" /> Thêm bằng cấp
                </button>
              </template>

              <!-- Tab: Kinh nghiệm -->
              <template v-else-if="activeTab === 'experience'">
                <h4 class="create-section-title">Kinh nghiệm giảng dạy</h4>
                <p v-if="mode === 'edit'" class="create-section-hint">Không bắt buộc — ghi chú nhanh.</p>
                <textarea
                  v-model="teachingExperience"
                  class="form-input form-textarea"
                  rows="8"
                  :maxlength="EXPERIENCE_MAX"
                  placeholder="VD: 3 năm dạy Scratch tại trung tâm ABC, từng phụ trách CLB Robotics..."
                />
                <small
                  class="field__hint"
                  :class="{
                    'field__hint--warn': (teachingExperience || '').length >= EXPERIENCE_MAX - 30 && (teachingExperience || '').length <= EXPERIENCE_MAX,
                    'field__hint--over': (teachingExperience || '').length > EXPERIENCE_MAX,
                  }"
                >
                  {{ (teachingExperience || '').length }}/{{ EXPERIENCE_MAX }}
                </small>
              </template>

              <!-- Tab: Trạng thái -->
              <template v-else-if="activeTab === 'status'">
                <h4 class="create-section-title">Trạng thái</h4>
                <span v-if="mode === 'create'" class="badge badge--active badge--lg">Đang hoạt động</span>
                <select v-else v-model="profile.status" class="form-input" style="max-width: 280px">
                  <option value="ACTIVE">Đang hoạt động</option>
                  <option value="RETIRED">Ngừng hoạt động</option>
                  <option value="SUSPENDED">Đã nghỉ phép</option>
                </select>
              </template>

              <p v-if="error || localError" class="form-error">{{ error || localError }}</p>
            </div>
          </div>

          <div class="modal__footer">
            <button class="btn btn--ghost" @click="close">Hủy</button>
            <button class="btn btn--primary" :disabled="saving" @click="submit">
              <span v-if="saving" class="spinner spinner--sm" />
              {{ saving ? (mode === 'create' ? 'Đang tạo…' : 'Đang lưu…') : mode === 'create' ? 'Tạo giáo viên' : 'Lưu thay đổi' }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* ── Overlay / Modal khung chung ── */
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
.modal__footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 1.5rem;
  padding-top: 1.25rem;
  border-top: 1px solid var(--a-border);
}

/* ── Menu trái + form phải ── */
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
.field__hint {
  display: block;
  text-align: right;
  margin-top: 0.4rem;
  color: var(--a-text-muted);
  font-size: 0.78rem;
  font-variant-numeric: tabular-nums;
}
.field__hint--warn {
  color: var(--c-primary, #f97316);
  font-weight: 600;
}
.field__hint--over {
  color: #ef4444;
  font-weight: 700;
}

/* ── Bằng cấp ── */
.degree-row {
  display: flex;
  position: relative;
  gap: 0.6rem;
  padding: 0.9rem 2.5rem 0.9rem 0.9rem;
  margin-bottom: 0.7rem;
  border: 1px solid var(--a-border);
  border-radius: 10px;
  background: var(--a-bg);
}
.degree-row:last-of-type {
  margin-bottom: 0;
}
.degree-row__fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.6rem 0.5rem;
  flex: 1;
  align-items: start;

  }
/* Ô nhập "Chuyên ngành khác" và ô chọn PDF nằm riêng 1 dòng, rộng hết khung —
   không còn bị bóp chung với 2 select Trình độ/Chuyên ngành nữa. */
.degree-row__span {
  grid-column: 1 / -1;
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
  transition: border-color 0.15s, color 0.15s;
}
.doc-file:hover {
  border-color: var(--c-primary);
  color: var(--c-primary);
}
.doc-file__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.doc-remove {
  position: absolute;
  top: 0.6rem;
  right: 0.6rem;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
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

/* ── Form fields ── */
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
  transition: border-color 0.15s, box-shadow 0.15s;
  box-sizing: border-box;
}
.form-input:focus {
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.12);
  background: var(--c-surface);
}
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
.field-hint {
  font-size: 0.78rem;
  color: var(--a-text-muted);
  margin-top: 0.25rem;
}
.pwd-wrap {
  position: relative;
  display: flex;
  align-items: center;
}
.pwd-wrap .form-input {
  padding-right: 2.4rem;
}
.pwd-toggle {
  position: absolute;
  right: 0.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--a-text-muted);
  cursor: pointer;
  padding: 0.2rem;
  border-radius: 6px;
}
.pwd-toggle:hover {
  color: var(--a-text);
  background: var(--a-bg);
}

/* ── Badge (trạng thái) ── */
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
.badge--lg {
  font-size: 0.85rem;
  padding: 0.4rem 0.9rem;
}

/* ── Buttons / spinner ── */
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

/* ── Modal transition ── */
.modal-enter-active,
.modal-leave-active {
  transition: all 0.22s;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
  transform: scale(0.95);
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
  .degree-row__fields {
    grid-template-columns: 1fr;
  }
}
</style>