<!-- src/pages/SchoolListPage.vue -->
<script setup>
/**
 * Trang "Quản lý trường": CRUD School (trường khách hàng).
 * Đồng bộ giao diện với SubjectCategoryListPage.vue / LessonListPage.vue —
 * cùng bố cục page__head/filter-bar/table-wrap/pagination, cùng token màu & badge.
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { schoolApi } from '@/api/schools'
import { branchApi } from '@/api/branches'
import DateField from '@/components/ui/DateField.vue'

/* ── State: danh sách trường ── */
const loading = ref(false)
const items = ref([])
const total = ref(0)
const keyword = ref('')
const branchFilter = ref('')
const statusFilter = ref('')
const page = ref(0)
const pageSize = 10
const pageInput = ref('')

/** Danh sách chi nhánh — dùng cho dropdown lọc + form thêm/sửa. */
const branches = ref([])

const emptyForm = () => ({
  branchId: '',
  name: '',
  address: '',
  phone: '',
  email: '',
  contactPerson: '',
  contractStartDate: '',
  contractEndDate: '',
  status: 'ACTIVE',
  // Chỉ dùng lúc THÊM MỚI: quyết định bộ khung tiết sinh sẵn cho trường.
  // Để trống thì backend tự đoán theo tên trường. Không gửi khi SỬA.
  educationLevel: '',
})

const modal = reactive({
  open: false,
  mode: 'create', // 'create' | 'edit'
  id: null,
  form: emptyForm(),
  errors: {},
  error: '',
  saving: false,
})

const deleteTarget = ref(null)

/* =========================
   Pagination
========================= */

const totalPages = computed(() => Math.ceil(total.value / pageSize))

const visiblePages = computed(() => {
  const totalP = totalPages.value
  const current = page.value + 1

  let start = Math.max(1, current - 2)
  let end = Math.min(totalP, current + 2)

  if (end - start < 4) {
    if (start === 1) {
      end = Math.min(5, totalP)
    } else if (end === totalP) {
      start = Math.max(1, totalP - 4)
    }
  }

  const arr = []
  for (let i = start; i <= end; i++) {
    arr.push(i)
  }
  return arr
})

function goPage(index) {
  if (index < 0 || index >= totalPages.value) return
  page.value = index
  load()
}

function jumpPage() {
  const p = Number(pageInput.value)
  if (isNaN(p)) return
  if (p < 1) return
  if (p > totalPages.value) return
  goPage(p - 1)
}

/* ── Load danh sách trường ── */
async function load() {
  loading.value = true
  try {
    const res = await schoolApi.list({
      keyword: keyword.value || undefined,
      branchId: branchFilter.value || undefined,
      status: statusFilter.value || undefined,
      page: page.value,
      size: pageSize,
    })
    items.value = res.data.content
    total.value = res.data.totalElements
  } catch {
    // handle error
  } finally {
    loading.value = false
  }
}

async function loadBranches() {
  try {
    const { data } = await branchApi.list()
    branches.value = data
  } catch {
    // dropdown lỗi không block trang
  }
}

onMounted(() => {
  load()
  loadBranches()
})

function onSearch() {
  page.value = 0
  load()
}

function clearSearch() {
  keyword.value = ''
  branchFilter.value = ''
  statusFilter.value = ''
  page.value = 0
  load()
}

function branchName(branchId) {
  return branches.value.find((b) => b.id === branchId)?.name ?? '-'
}

/* ── Validate (mirror SchoolRequest phía backend) ── */
const PHONE_RE = /^$|^(\+84|0)\d{9,10}$/
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/**
 * Suy cấp học từ TÊN trường: 'TH' | 'THCS' | '' (không đoán được).
 *
 * BẢN SAO của PeriodService#suyCapTuTen bên backend — sửa quy tắc một bên thì
 * phải sửa cả hai, nếu không form sẽ cho qua rồi backend mới trả lỗi.
 *
 * Bỏ dấu trước khi so để "Tiểu học" và "Tieu hoc" cho cùng kết quả. Phải xét
 * THCS TRƯỚC TH vì "THCS" cũng bắt đầu bằng "TH".
 */
function suyCapTuTen(name) {
  if (!name) return ''
  const s = boDau(name.trim().toLowerCase())
  if (s.startsWith('thcs ') || s.includes(' thcs ') || s.includes('trung hoc co so')) return 'THCS'
  if (s.startsWith('th ') || s.includes(' th ') || s.includes('tieu hoc')) return 'TH'
  return ''
}

function boDau(s) {
  return s
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
}

/**
 * Cụm chỉ cấp học có thể đứng đầu tên trường (không dấu, tách token), dài trước ngắn sau.
 * BẢN SAO của SchoolService#TIEN_TO_CAP bên backend — sửa một bên phải sửa cả hai.
 */
const TIEN_TO_CAP = [
  ['truong', 'trung', 'hoc', 'co', 'so'],
  ['trung', 'hoc', 'co', 'so'],
  ['truong', 'tieu', 'hoc'],
  ['tieu', 'hoc'],
  ['truong', 'thcs'],
  ['truong', 'th'],
  ['thcs'],
  ['th'],
]

/**
 * Ghép tên hiển thị = tiền tố cấp học + tên riêng, giống hệt SchoolService#ghepTenTheoCap.
 * Dùng cho dòng xem trước dưới ô Tên trường, để người dùng thấy trước tên sẽ lưu.
 */
function ghepTenTheoCap(name, cap) {
  const ten = (name || '').trim().replace(/\s+/g, ' ')
  if (!cap || !ten) return ten
  let token = ten.split(' ')
  const khongDau = token.map((t) => boDau(t.toLowerCase()))
  for (const cum of TIEN_TO_CAP) {
    if (token.length > cum.length && cum.every((w, i) => w === khongDau[i])) {
      token = token.slice(cum.length)
      break
    }
  }
  const tenRieng = token.join(' ')
  return tenRieng ? `${cap} ${tenRieng}` : ten
}

function validateForm(form, isCreate) {
  const errors = {}
  const name = form.name.trim()

  if (!form.branchId) errors.branchId = 'Vui lòng chọn chi nhánh phụ trách'
  // Cấp học BẮT BUỘC khi thêm mới, và cố ý KHÔNG đặt sẵn giá trị mặc định:
  // chọn nhầm cấp là cả trường chạy sai khung giờ (tiểu học 35 phút vs THCS
  // 45 phút) mà không có lỗi nào bắn ra. Thà bắt chọn còn hơn đoán hộ.
  if (isCreate && !form.educationLevel) errors.educationLevel = 'Vui lòng chọn cấp học'
  if (!name) errors.name = 'Tên trường không được để trống'
  else if (name.length > 200) errors.name = 'Tên tối đa 200 ký tự'
  if (form.address && form.address.length > 255) errors.address = 'Địa chỉ tối đa 255 ký tự'
  if (form.phone && !PHONE_RE.test(form.phone)) errors.phone = 'Số điện thoại không hợp lệ'
  if (form.email && !EMAIL_RE.test(form.email)) errors.email = 'Email không hợp lệ'
  if (form.contactPerson && form.contactPerson.length > 150)
    errors.contactPerson = 'Tên người liên hệ tối đa 150 ký tự'
  if (
    form.contractStartDate &&
    form.contractEndDate &&
    form.contractEndDate < form.contractStartDate
  ) {
    errors.contractEndDate = 'Ngày kết thúc phải sau ngày bắt đầu'
  }

  return errors
}

/* ── Modal tạo/sửa ── */
function openCreate() {
  Object.assign(modal, {
    open: true,
    mode: 'create',
    id: null,
    form: emptyForm(),
    errors: {},
    error: '',
    saving: false,
  })
}

function openEdit(item) {
  Object.assign(modal, {
    open: true,
    mode: 'edit',
    id: item.id,
    form: {
      branchId: item.branchId,
      name: item.name,
      address: item.address ?? '',
      phone: item.phone ?? '',
      email: item.email ?? '',
      contactPerson: item.contactPerson ?? '',
      contractStartDate: item.contractStartDate ?? '',
      contractEndDate: item.contractEndDate ?? '',
      status: item.status,
    },
    errors: {},
    error: '',
    saving: false,
  })
}

function clearFieldError(field) {
  if (modal.errors[field]) delete modal.errors[field]
}

/**
 * Tên đầy đủ sẽ được lưu — hiện ngay khi THÊM MỚI có đủ tên + cấp học.
 *
 * Cố tình hiện cả khi kết quả trùng đúng chuỗi người dùng vừa gõ (gõ sẵn
 * "THCS Ban Mai" rồi chọn THCS): dòng này là câu trả lời cho "rốt cuộc bảng
 * sẽ hiện tên gì", ẩn đi lúc trùng thì đúng lúc cần trấn an lại không có gì.
 */
const tenSeLuu = computed(() => {
  if (modal.mode !== 'create') return ''
  if (!modal.form.name.trim() || !modal.form.educationLevel) return ''
  return ghepTenTheoCap(modal.form.name, modal.form.educationLevel)
})

/**
 * Gõ tên trường thì tự chọn luôn cấp học tương ứng (chỉ khi THÊM MỚI).
 *
 * Người dùng gần như luôn đặt tên có sẵn cấp ("THCS Ban Mai"), nên để họ tự
 * chọn lại là thừa một thao tác và là chỗ để chọn nhầm. Vẫn cho đổi tay sau
 * đó — nhưng nếu đổi thành cấp chỏi với tên thì validateForm sẽ chặn.
 */
function onNameInput() {
  clearFieldError('name')
  if (modal.mode !== 'create') return
  const theoTen = suyCapTuTen(modal.form.name)
  if (theoTen && theoTen !== modal.form.educationLevel) {
    modal.form.educationLevel = theoTen
    clearFieldError('educationLevel')
  }
}

async function saveModal() {
  modal.errors = validateForm(modal.form, modal.mode === 'create')
  if (Object.keys(modal.errors).length) return

  modal.saving = true
  modal.error = ''

  const body = {
    ...modal.form,
    branchId: Number(modal.form.branchId),
    address: modal.form.address || null,
    phone: modal.form.phone || null,
    email: modal.form.email || null,
    contactPerson: modal.form.contactPerson || null,
    contractStartDate: modal.form.contractStartDate || null,
    contractEndDate: modal.form.contractEndDate || null,
  }
  // Cấp học chỉ có nghĩa lúc TẠO (để sinh khung tiết). Khi sửa thì bỏ hẳn khỏi
  // payload: khung tiết đã dùng xếp lịch, không được đổi ngầm qua form sửa.
  if (modal.mode === 'create') {
    body.educationLevel = modal.form.educationLevel || null
  } else {
    delete body.educationLevel
  }

  try {
    if (modal.mode === 'create') {
      await schoolApi.create(body)
    } else {
      await schoolApi.update(modal.id, body)
    }
    modal.open = false
    load()
  } catch (e) {
    modal.error = e.response?.data?.message ?? 'Lỗi không xác định'
  } finally {
    modal.saving = false
  }
}

/* ── Xóa trường ── */
async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await schoolApi.remove(deleteTarget.value.id)
    deleteTarget.value = null
    load()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Xóa thất bại')
    deleteTarget.value = null
  }
}

const STATUS_LABEL = { ACTIVE: 'Hoạt động', INACTIVE: 'Ngừng hoạt động', EXPIRED: 'Hết hạn' }
</script>

<template>
  <div class="page">
    <!-- ================= HEADER ================= -->
    <div class="page__head">
      <div>
        <h1 class="page__title">Quản lý trường</h1>
        <p class="page__sub">Danh sách trường khách hàng đang hợp tác</p>
      </div>

      <button class="btn" @click="openCreate">+ Thêm trường</button>
    </div>

    <!-- ================= FILTER ================= -->
    <div class="filter-bar">
      <label class="field field--wide">
        <span>Tìm kiếm</span>
        <input
          v-model="keyword"
          placeholder="Tên trường, địa chỉ, người liên hệ, SĐT..."
          @keyup.enter="onSearch"
        />
      </label>

      <label class="field">
        <span>Chi nhánh</span>
        <select v-model="branchFilter">
          <option value="">Tất cả chi nhánh</option>
          <option v-for="b in branches" :key="b.id" :value="b.id">{{ b.name }}</option>
        </select>
      </label>

      <label class="field">
        <span>Trạng thái</span>
        <select v-model="statusFilter">
          <option value="">Tất cả trạng thái</option>
          <option value="ACTIVE">Hoạt động</option>
          <option value="INACTIVE">Ngừng hoạt động</option>
          <option value="EXPIRED">Hết hạn</option>
        </select>
      </label>

      <div class="filter-actions">
        <button class="btn" @click="onSearch">Lọc</button>
        <button class="btn btn--ghost" @click="clearSearch">Xóa lọc</button>
      </div>
    </div>

    <!-- ================= INFO ================= -->
    <p v-if="!loading" class="total">
      Tổng cộng <strong>{{ total }}</strong> trường
    </p>

    <!-- ================= TABLE ================= -->
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Tên trường</th>
            <th>Liên hệ</th>
            <th>Chi nhánh</th>
            <th>Hợp đồng</th>
            <th>Trạng thái</th>
            <th width="120">Thao tác</th>
          </tr>
        </thead>

        <tbody>
          <tr v-if="loading">
            <td colspan="6" class="empty">Đang tải...</td>
          </tr>

          <tr v-else-if="items.length === 0">
            <td colspan="6" class="empty">Không có dữ liệu</td>
          </tr>

          <tr v-for="item in items" :key="item.id" class="row-clickable" @click="openEdit(item)">
            <td class="col-title">
              <div class="title-text">{{ item.name }}</div>
              <div v-if="item.address" class="desc-text">{{ item.address }}</div>
            </td>

            <td>
              <div v-if="item.contactPerson" class="title-text">{{ item.contactPerson }}</div>
              <div v-if="item.phone" class="desc-text">SĐT: {{ item.phone }}</div>
              <div v-if="item.email" class="desc-text">{{ item.email }}</div>
              <span v-if="!item.contactPerson && !item.phone && !item.email">-</span>
            </td>

            <td>
              <span class="cat-badge">{{ item.branchName ?? branchName(item.branchId) }}</span>
            </td>

            <td>
              <span v-if="item.contractStartDate || item.contractEndDate" class="desc-text">
                {{ item.contractStartDate ?? '?' }} → {{ item.contractEndDate ?? '?' }}
              </span>
              <span v-else>-</span>
            </td>

            <td>
              <span class="badge" :class="item.status === 'ACTIVE' ? 'badge--pub' : 'badge--draft'">
                {{ STATUS_LABEL[item.status] ?? item.status }}
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

    <!-- ================= PAGINATION ================= -->
    <div v-if="totalPages > 1" class="pagination">
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
      />
      <button class="pg-btn" @click="jumpPage">Đi</button>
    </div>

    <!-- ================= MODAL: tạo/sửa trường ================= -->
    <div v-if="modal.open" class="overlay" @click.self="modal.open = false">
      <div class="modal">
        <h3>{{ modal.mode === 'create' ? 'Thêm trường' : 'Sửa trường' }}</h3>

        <div class="form-group">
          <label>Chi nhánh phụ trách *</label>
          <select
            v-model="modal.form.branchId"
            :class="{ 'input-error': modal.errors.branchId }"
            @change="clearFieldError('branchId')"
          >
            <option value="">-- Chọn chi nhánh --</option>
            <option v-for="b in branches" :key="b.id" :value="b.id">{{ b.name }}</option>
          </select>
          <small v-if="modal.errors.branchId" class="field-error">{{
            modal.errors.branchId
          }}</small>
        </div>

        <div class="form-group">
          <label>Tên trường *</label>
          <input
            v-model="modal.form.name"
            placeholder="VD: Ban Mai"
            :class="{ 'input-error': modal.errors.name }"
            @input="onNameInput"
          />
          <small v-if="modal.errors.name" class="field-error">{{ modal.errors.name }}</small>
          <!-- Xem trước tên sẽ lưu: ô trên chỉ nhận TÊN RIÊNG, tiền tố cấp học do
               ô Cấp học sinh ra. Cho thấy trước để không ai bất ngờ khi mở bảng. -->
          <small v-if="tenSeLuu" class="name-preview">
            Lưu vào hệ thống: <strong>{{ tenSeLuu }}</strong>
          </small>
        </div>

        <!-- Cấp học: CHỈ hiện khi thêm mới. Nó không phải thuộc tính lưu trong bảng
             School mà chỉ quyết định bộ khung tiết sinh sẵn cho trường (tiểu học 10
             tiết 35 phút, THCS 9 tiết 45 phút). Khi SỬA thì ẩn đi — khung tiết đã
             dùng để xếp lịch, đổi ngầm là lệch giờ những buổi đã sinh ra. -->
        <div v-if="modal.mode === 'create'" class="form-group">
          <label>Cấp học *</label>
          <select
            v-model="modal.form.educationLevel"
            :class="{ 'input-error': modal.errors.educationLevel }"
            @change="clearFieldError('educationLevel')"
          >
            <!-- Dòng này disabled + hidden nên KHÔNG hiện trong danh sách xổ xuống
                 (người dùng chỉ thấy đúng 2 lựa chọn), nhưng vẫn giữ ô ở trạng thái
                 "chưa chọn" lúc mở form. Bỏ nó đi thì trình duyệt tự chọn mục đầu
                 tiên, và một trường THCS sẽ âm thầm nhận khung tiểu học 35 phút. -->
            <!-- Nhãn để đúng "TH"/"THCS" trùng với tiền tố sẽ ghép vào tên trường,
                 để cái người dùng chọn và cái hiện ra ở bảng là một. -->
            <option value="" disabled hidden>-- Chọn cấp học --</option>
            <option value="TH">TH</option>
            <option value="THCS">THCS</option>
          </select>
          <small v-if="modal.errors.educationLevel" class="field-error">{{
            modal.errors.educationLevel
          }}</small>
        </div>

        <div class="form-group">
          <label>Địa chỉ</label>
          <input
            v-model="modal.form.address"
            placeholder="VD: Lê Chân, Hải Phòng"
            :class="{ 'input-error': modal.errors.address }"
            @input="clearFieldError('address')"
          />
          <small v-if="modal.errors.address" class="field-error">{{ modal.errors.address }}</small>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label>Số điện thoại</label>
            <input
              v-model="modal.form.phone"
              placeholder="10 số"
              :class="{ 'input-error': modal.errors.phone }"
              @input="clearFieldError('phone')"
            />
            <small v-if="modal.errors.phone" class="field-error">{{ modal.errors.phone }}</small>
          </div>

          <div class="form-group">
            <label>Email</label>
            <input
              v-model="modal.form.email"
              placeholder="school@example.com"
              :class="{ 'input-error': modal.errors.email }"
              @input="clearFieldError('email')"
            />
            <small v-if="modal.errors.email" class="field-error">{{ modal.errors.email }}</small>
          </div>
        </div>

        <div class="form-group">
          <label>Người liên hệ</label>
          <input
            v-model="modal.form.contactPerson"
            placeholder="VD: Cô Nguyễn Thu Hằng"
            :class="{ 'input-error': modal.errors.contactPerson }"
            @input="clearFieldError('contactPerson')"
          />
          <small v-if="modal.errors.contactPerson" class="field-error">{{
            modal.errors.contactPerson
          }}</small>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label>Ngày bắt đầu hợp đồng</label>
            <DateField v-model="modal.form.contractStartDate" />
          </div>

          <div class="form-group">
            <label>Ngày hết hạn hợp đồng</label>
            <DateField
              v-model="modal.form.contractEndDate"
              :invalid="!!modal.errors.contractEndDate"
              @update:model-value="clearFieldError('contractEndDate')"
            />
            <small v-if="modal.errors.contractEndDate" class="field-error">{{
              modal.errors.contractEndDate
            }}</small>
          </div>
        </div>

        <div class="form-group">
          <label>Trạng thái</label>
          <select v-model="modal.form.status">
            <option value="ACTIVE">Hoạt động</option>
            <option value="INACTIVE">Ngừng hoạt động</option>
            <option value="EXPIRED">Hết hạn</option>
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

    <!-- ================= MODAL: xác nhận xóa ================= -->
    <div v-if="deleteTarget" class="overlay" @click.self="deleteTarget = null">
      <div class="modal">
        <h3>Xác nhận xóa</h3>
        <p>
          Bạn có chắc muốn xóa trường <strong>{{ deleteTarget.name }}</strong
          >?
        </p>

        <div class="modal__actions">
          <button class="btn btn--ghost" @click="deleteTarget = null">Hủy</button>
          <button class="btn btn--danger" @click="confirmDelete">Xóa</button>
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

/* ================= Header ================= */
.page__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 22px;
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

/* ================= Filter ================= */
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
  min-width: 170px;
}

.field--wide {
  flex: 1;
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

/* ================= Buttons ================= */
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

/* ================= Table ================= */
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

.desc-text {
  margin-top: 2px;
  color: var(--c-text-muted);
  font-size: 12px;
}

.cat-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.12);
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
}

:root[data-theme='dark'] .cat-badge {
  color: #93c5fd;
}

/* ================= Status ================= */
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

/* ================= Action ================= */
.col-actions {
  white-space: nowrap;
}

.act-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 6px;
  border-radius: 6px;
  font-size: 16px;
  transition: 0.2s;
}

.act-btn:hover {
  background: var(--c-surface-2);
}

.act-btn--del:hover {
  background: rgba(239, 68, 68, 0.12);
}

/* ================= Pagination ================= */
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
}

/* ================= Modal ================= */
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

.form-row {
  display: flex;
  gap: 12px;
}

.form-row .form-group {
  flex: 1;
  min-width: 0;
}

.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
  color: var(--c-text);
}

.form-group input,
.form-group select,
.form-group textarea {
  width: 100%;
  height: 40px;
  border: 1px solid var(--c-input-border);
  border-radius: 8px;
  padding: 0 12px;
  font-size: 14px;
  box-sizing: border-box;
  font-family: inherit;
}

.form-group input:focus,
.form-group select:focus,
.form-group textarea:focus {
  outline: none;
  border-color: #f97316;
}

.form-group small {
  display: block;
  margin-top: 4px;
  color: var(--c-text-muted);
  font-size: 12px;
}

.input-error {
  border-color: #dc2626 !important;
}

.field-error {
  color: #dc2626 !important;
}

/* Dòng xem trước tên trường sẽ lưu (tiền tố cấp học + tên riêng). */
.name-preview {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--c-text-muted);
}

/* ================= Message ================= */
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

.form-row {
  flex-direction: column;
  gap: 0;
}
</style>
