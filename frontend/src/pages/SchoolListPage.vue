<!-- src/pages/SchoolListPage.vue -->
<script setup>
/**
 * Trang "Quản lý trường": CRUD School (trường khách hàng) + thùng rác.
 * Cùng bố cục page__head/filter-bar/table-wrap với SchoolClassListPage.vue và
 * SubjectCategoryListPage.vue; phân trang dùng chung components/ui/Pagination.vue.
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { schoolApi } from '@/api/schools'
import { branchApi } from '@/api/branches'
import { periodApi } from '@/api/periods'
import { formatCurrency, formatDate } from '@/utils/format'
import DateField from '@/components/ui/DateField.vue'
import Pagination from '@/components/ui/Pagination.vue'

/* ── State: danh sách trường ── */
const loading = ref(false)
const loadError = ref('')
const items = ref([])
const total = ref(0)
const keyword = ref('')
const branchFilter = ref('')
const statusFilter = ref('')
/** '' = mọi hợp đồng | '30' = còn hạn nhưng kết thúc trong 30 ngày tới. */
const expiringFilter = ref('')
const page = ref(0)
const pageSize = 10

/** 'list' = danh sách chính | 'trash' = thùng rác (giống trang Lớp học). */
const viewMode = ref('list')
const trashItems = ref([])
const trashLoading = ref(false)

/** Danh sách chi nhánh — dùng cho dropdown lọc + form thêm/sửa. */
const branches = ref([])

/** Dòng đang mở chi tiết + số liệu của nó (nạp riêng, xem SchoolDetailResponse). */
const expandedId = ref(null)
const detail = ref(null)
const detailLoading = ref(false)

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
  // Không gửi khi SỬA — backend suy cấp học từ chính tên trường.
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
const restoreTarget = ref(null)
const trashBusy = ref(false)
const trashError = ref('')

const STATUS_LABEL = { ACTIVE: 'Hoạt động', INACTIVE: 'Ngừng hoạt động', EXPIRED: 'Hết hạn' }

/** Trạng thái hợp đồng dịch vụ (bảng ServiceContract) — bộ mã khác với trạng thái trường. */
const CONTRACT_STATUS_LABEL = {
  DRAFT: 'Nháp',
  ACTIVE: 'Đang hiệu lực',
  EXPIRED: 'Hết hạn',
  TERMINATED: 'Đã chấm dứt',
}

/** Còn dưới ngần này ngày thì hiện cảnh báo hạn hợp đồng. Khớp bộ lọc "Sắp hết hạn". */
const NGUONG_SAP_HET_HAN = 30

const totalPages = computed(() => Math.ceil(total.value / pageSize))

/* ── Load danh sách trường ── */
async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await schoolApi.list({
      keyword: keyword.value || undefined,
      branchId: branchFilter.value || undefined,
      status: statusFilter.value || undefined,
      expiringInDays: expiringFilter.value || undefined,
      page: page.value,
      size: pageSize,
    })
    items.value = res.data.content
    total.value = res.data.totalElements
    // Trang mới có thể không còn dòng đang mở -> đóng luôn cho khỏi treo panel rỗng.
    if (expandedId.value && !items.value.some((i) => i.id === expandedId.value)) {
      expandedId.value = null
    }
  } catch (e) {
    // Nuốt lỗi ở đây là nói dối: backend chết mà bảng vẫn hiện "Không có dữ liệu",
    // người dùng tưởng chưa có trường nào và đi tạo lại từ đầu.
    items.value = []
    total.value = 0
    loadError.value = e.response?.data?.message ?? 'Không tải được danh sách trường'
  } finally {
    loading.value = false
  }
}

async function loadTrash() {
  trashLoading.value = true
  trashError.value = ''
  try {
    const { data } = await schoolApi.trash()
    trashItems.value = data
  } catch (e) {
    trashItems.value = []
    trashError.value = e.response?.data?.message ?? 'Không tải được thùng rác'
  } finally {
    trashLoading.value = false
  }
}

async function loadBranches() {
  try {
    const { data } = await branchApi.list()
    branches.value = data
  } catch {
    // Dropdown lỗi không chặn trang; tên chi nhánh vẫn có sẵn trong từng dòng (branchName).
  }
}

onMounted(() => {
  load()
  loadBranches()
})

function switchView(mode) {
  viewMode.value = mode
  expandedId.value = null
  if (mode === 'trash') loadTrash()
  else load()
}

function onSearch() {
  page.value = 0
  load()
}

function clearSearch() {
  keyword.value = ''
  branchFilter.value = ''
  statusFilter.value = ''
  expiringFilter.value = ''
  page.value = 0
  load()
}

function goPage(index) {
  page.value = index
  load()
}

function branchName(item) {
  return item.branchName ?? branches.value.find((b) => b.id === item.branchId)?.name ?? '-'
}

/* ── Chi tiết một trường (mở rộng dòng) ── */
async function toggleExpand(item) {
  if (expandedId.value === item.id) {
    expandedId.value = null
    return
  }
  expandedId.value = item.id
  detail.value = null
  detailLoading.value = true
  try {
    const { data } = await schoolApi.summary(item.id)
    detail.value = data
  } catch {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

/** Trường chưa có khung tiết thì không xếp phân công được — cho áp khung ngay tại dòng. */
const applyingFrameId = ref(null)
async function applyStandardFrame(item) {
  applyingFrameId.value = item.id
  try {
    const { data } = await periodApi.applyStandard(item.id)
    // Kèm cấp học vừa suy ra: backend đoán cấp từ khối lớp cao nhất, đoán sai thì cả
    // trường chạy sai giờ — người dùng phải thấy để bắt được ngay tại đây.
    alert(`Đã tạo ${data.created} tiết (${data.level}) cho ${item.name}.`)
    load()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Áp khung tiết thất bại')
  } finally {
    applyingFrameId.value = null
  }
}

/* ── Validate (mirror SchoolRequest phía backend) ── */
const PHONE_RE = /^$|^(\+84|0)\d{9,10}$/
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/**
 * Suy cấp học từ TÊN trường: 'TH' | 'THCS' | '' (không đoán được).
 *
 * Bản rút gọn của PeriodService#suyCapTuTen bên backend, chỉ để chọn sẵn ô Cấp học
 * cho người dùng đỡ một thao tác. Tên lưu vào DB do backend ghép, nên hai bên lệch
 * nhau cũng không sinh dữ liệu sai — cùng lắm là ô Cấp học chọn sẵn chưa đúng.
 *
 * Xét THCS TRƯỚC TH vì "THCS" cũng bắt đầu bằng "TH".
 */
function suyCapTuTen(name) {
  if (!name) return ''
  const s = name.trim().toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd')
  if (s.startsWith('thcs ') || s.includes(' thcs ') || s.includes('trung hoc co so')) return 'THCS'
  if (s.startsWith('th ') || s.includes(' th ') || s.includes('tieu hoc')) return 'TH'
  return ''
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
      // Nạp trạng thái ĐANG LƯU, không phải trạng thái hiển thị: một trường ACTIVE
      // đang hiện "Hết hạn" vì quá ngày mà nạp nhầm là bấm Lưu xong nó thành
      // INACTIVE/EXPIRED thật, gia hạn hợp đồng cũng không sống lại được.
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
 * Gõ tên trường thì tự chọn luôn cấp học tương ứng (chỉ khi THÊM MỚI).
 *
 * Người dùng gần như luôn đặt tên có sẵn cấp ("THCS Ban Mai"), nên để họ tự chọn
 * lại là thừa một thao tác và là chỗ để chọn nhầm. Vẫn cho đổi tay sau đó.
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

/* ── Xóa mềm / khôi phục ── */
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

async function confirmRestore() {
  if (!restoreTarget.value) return
  trashBusy.value = true
  try {
    await schoolApi.restore(restoreTarget.value.id)
    restoreTarget.value = null
    loadTrash()
  } catch (e) {
    alert(e.response?.data?.message ?? 'Khôi phục thất bại')
  } finally {
    trashBusy.value = false
  }
}
</script>

<template>
  <div class="page">
    <!-- ================= HEADER ================= -->
    <div class="page__head">
      <h1 class="page__title">Quản lý trường</h1>

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
        <button v-if="viewMode === 'list'" class="btn" @click="openCreate">+ Thêm trường</button>
      </div>
    </div>

    <!-- ================= VIEW: DANH SÁCH ================= -->
    <template v-if="viewMode === 'list'">
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
          <select v-model="branchFilter" @change="onSearch">
            <option value="">Tất cả chi nhánh</option>
            <option v-for="b in branches" :key="b.id" :value="b.id">{{ b.name }}</option>
          </select>
        </label>

        <label class="field">
          <span>Trạng thái</span>
          <select v-model="statusFilter" @change="onSearch">
            <option value="">Tất cả trạng thái</option>
            <option value="ACTIVE">Hoạt động</option>
            <option value="INACTIVE">Ngừng hoạt động</option>
            <option value="EXPIRED">Hết hạn</option>
          </select>
        </label>

        <label class="field">
          <span>Hợp đồng</span>
          <select v-model="expiringFilter" @change="onSearch">
            <option value="">Tất cả</option>
            <option :value="String(NGUONG_SAP_HET_HAN)">
              Sắp hết hạn ({{ NGUONG_SAP_HET_HAN }} ngày)
            </option>
          </select>
        </label>

        <div class="filter-actions">
          <button class="btn" @click="onSearch">Lọc</button>
          <button class="btn btn--ghost" @click="clearSearch">Xóa lọc</button>
        </div>
      </div>

      <p v-if="!loading && !loadError" class="total">
        Tổng cộng <strong>{{ total }}</strong> trường
      </p>

      <!-- ================= TABLE ================= -->
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="expand-cell"></th>
              <th>Tên trường</th>
              <th>Liên hệ</th>
              <th>Chi nhánh</th>
              <th>Hợp đồng</th>
              <th>Khung tiết</th>
              <th>Trạng thái</th>
              <th width="120">Thao tác</th>
            </tr>
          </thead>

          <tbody>
            <tr v-if="loading">
              <td colspan="8" class="empty">Đang tải...</td>
            </tr>

            <tr v-else-if="loadError">
              <td colspan="8" class="empty">
                {{ loadError }}
                <button class="btn btn--ghost btn--sm" @click="load">Thử lại</button>
              </td>
            </tr>

            <tr v-else-if="items.length === 0">
              <td colspan="8" class="empty">Không có dữ liệu</td>
            </tr>

            <template v-for="item in items" :key="item.id">
              <tr class="row-clickable" @click="toggleExpand(item)">
                <td class="expand-cell">
                  <span class="chevron" :class="{ open: expandedId === item.id }">›</span>
                </td>

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
                  <span class="cat-badge">{{ branchName(item) }}</span>
                </td>

                <td>
                  <span v-if="item.contractStartDate || item.contractEndDate" class="desc-text">
                    {{ formatDate(item.contractStartDate) || '?' }} →
                    {{ formatDate(item.contractEndDate) || '?' }}
                  </span>
                  <span v-else>-</span>
                  <!-- Cảnh báo chỉ hiện khi hợp đồng CÒN hạn: đã quá hạn thì cột Trạng
                       thái bên cạnh đã ghi "Hết hạn", nhắc thêm ở đây là thừa. -->
                  <div
                    v-if="
                      item.daysLeft != null &&
                      item.daysLeft >= 0 &&
                      item.daysLeft <= NGUONG_SAP_HET_HAN
                    "
                    class="badge badge--warn"
                  >
                    Còn {{ item.daysLeft }} ngày
                  </div>
                </td>

                <td @click.stop>
                  <span v-if="item.periodCount > 0" class="desc-text">
                    {{ item.periodCount }} tiết
                  </span>
                  <button
                    v-else
                    class="act-btn"
                    :disabled="applyingFrameId === item.id"
                    title="Trường chưa có khung tiết nên chưa xếp phân công được"
                    @click="applyStandardFrame(item)"
                  >
                    {{ applyingFrameId === item.id ? 'Đang tạo...' : 'Áp khung tiết' }}
                  </button>
                </td>

                <td>
                  <span
                    class="badge"
                    :class="{
                      'badge--pub': item.effectiveStatus === 'ACTIVE',
                      'badge--expired': item.effectiveStatus === 'EXPIRED',
                      'badge--draft': item.effectiveStatus === 'INACTIVE',
                    }"
                  >
                    {{ STATUS_LABEL[item.effectiveStatus] ?? item.effectiveStatus }}
                  </span>
                </td>

                <td class="col-actions" @click.stop>
                  <button class="act-btn" title="Sửa" @click="openEdit(item)">Sửa</button>
                  <button class="act-btn act-btn--del" title="Xóa" @click="deleteTarget = item">
                    Xóa
                  </button>
                </td>
              </tr>

              <!-- Dòng mở rộng: quy mô trường + hợp đồng dịch vụ -->
              <tr v-if="expandedId === item.id" class="row-expanded">
                <td colspan="8">
                  <p v-if="detailLoading" class="empty empty--inline">Đang tải chi tiết...</p>

                  <div v-else-if="detail" class="detail">
                    <div class="detail__stats">
                      <div>
                        <span class="num">{{ detail.classCount }}</span> lớp đang mở
                      </div>
                      <div>
                        <span class="num">{{ detail.teacherCount }}</span> giáo viên đang dạy
                      </div>
                      <div>
                        <span class="num">{{ detail.studentCount }}</span> học sinh
                      </div>
                      <div>
                        <span class="num">{{ detail.periodCount }}</span> tiết
                        <template v-if="detail.periodCount">
                          ({{ detail.morningPeriodCount }} sáng /
                          {{ detail.periodCount - detail.morningPeriodCount }} chiều)
                        </template>
                      </div>
                    </div>

                    <h4>Hợp đồng dịch vụ</h4>
                    <table v-if="detail.contracts.length" class="sub-table">
                      <thead>
                        <tr>
                          <th>Mã hợp đồng</th>
                          <th>Hiệu lực</th>
                          <th>Giá trị</th>
                          <th>Trạng thái</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="c in detail.contracts" :key="c.contractCode">
                          <td>{{ c.contractCode }}</td>
                          <td>{{ formatDate(c.startDate) }} → {{ formatDate(c.endDate) }}</td>
                          <td>{{ formatCurrency(c.value) }}</td>
                          <td>{{ CONTRACT_STATUS_LABEL[c.status] ?? c.status }}</td>
                        </tr>
                      </tbody>
                    </table>
                    <p v-else class="desc-text">Chưa có hợp đồng dịch vụ nào.</p>
                  </div>

                  <p v-else class="empty empty--inline">Không tải được chi tiết trường.</p>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>

      <Pagination
        :model-value="page"
        :total-pages="totalPages"
        show-jump
        @update:model-value="goPage"
      />
    </template>

    <!-- ================= VIEW: THÙNG RÁC ================= -->
    <template v-else>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Tên trường</th>
              <th>Chi nhánh</th>
              <th>Người liên hệ</th>
              <th width="220">Thao tác</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="trashLoading">
              <td colspan="4" class="empty">Đang tải...</td>
            </tr>
            <tr v-else-if="trashError">
              <td colspan="4" class="empty">
                {{ trashError }}
                <button class="btn btn--ghost btn--sm" @click="loadTrash">Thử lại</button>
              </td>
            </tr>
            <tr v-else-if="trashItems.length === 0">
              <td colspan="4" class="empty">Thùng rác trống</td>
            </tr>

            <tr v-for="item in trashItems" :key="item.id">
              <td class="col-title">
                <div class="title-text">{{ item.name }}</div>
                <div v-if="item.address" class="desc-text">{{ item.address }}</div>
              </td>
              <td>
                <span class="cat-badge">{{ branchName(item) }}</span>
              </td>
              <td>{{ item.contactPerson || '-' }}</td>
              <td class="col-actions">
                <button class="act-btn" @click="restoreTarget = item">Khôi phục</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

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
          <small>Trường không hoạt động sẽ không nhận lớp mới và phân công mới.</small>
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

    <!-- ================= MODAL: xác nhận xóa mềm ================= -->
    <div v-if="deleteTarget" class="overlay" @click.self="deleteTarget = null">
      <div class="modal">
        <h3>Xác nhận xóa</h3>
        <p>
          Bạn có chắc muốn xóa trường <strong>{{ deleteTarget.name }}</strong
          >? Trường sẽ nằm trong thùng rác và khôi phục lại được.
        </p>

        <div class="modal__actions">
          <button class="btn btn--ghost" @click="deleteTarget = null">Hủy</button>
          <button class="btn btn--danger" @click="confirmDelete">Xóa</button>
        </div>
      </div>
    </div>

    <!-- ================= MODAL: khôi phục ================= -->
    <div v-if="restoreTarget" class="overlay" @click.self="restoreTarget = null">
      <div class="modal">
        <h3>Khôi phục trường</h3>
        <p>
          Khôi phục <strong>{{ restoreTarget.name }}</strong> về danh sách? Trạng thái vẫn là "Ngừng
          hoạt động" cho tới khi bạn bật lại.
        </p>

        <div class="modal__actions">
          <button class="btn btn--ghost" @click="restoreTarget = null">Hủy</button>
          <button class="btn" :disabled="trashBusy" @click="confirmRestore">
            {{ trashBusy ? 'Đang xử lý...' : 'Khôi phục' }}
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

.page__head-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
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

.btn--sm {
  padding: 6px 12px;
  font-size: 13px;
  margin-left: 10px;
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

.empty--inline {
  padding: 14px;
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

/* ================= Dòng mở rộng ================= */
.row-clickable {
  cursor: pointer;
}

.expand-cell {
  width: 28px;
}

.chevron {
  display: inline-block;
  transition: transform 0.15s;
  color: var(--c-text-muted);
}

.chevron.open {
  transform: rotate(90deg);
}

.detail__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 26px;
  margin-bottom: 16px;
  color: var(--c-text-muted);
  font-size: 13px;
}

.num {
  font-size: 17px;
  font-weight: 700;
  color: var(--c-text);
}

.detail h4 {
  margin: 0 0 8px;
  font-size: 14px;
}

.sub-table {
  border: 1px solid var(--c-border);
  border-radius: 8px;
}

.sub-table th,
.sub-table td {
  padding: 8px 12px;
  font-size: 13px;
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

.badge--expired {
  background: rgba(239, 68, 68, 0.12);
  color: #b91c1c;
}

:root[data-theme='dark'] .badge--expired {
  color: #f87171;
}

.badge--warn {
  margin-top: 4px;
  background: rgba(245, 158, 11, 0.15);
  color: #b45309;
}

:root[data-theme='dark'] .badge--warn {
  color: #fbbf24;
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
  font-size: 13px;
  color: var(--c-text);
  transition: 0.2s;
}

.act-btn:hover {
  background: var(--c-surface-2);
}

.act-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.act-btn--del:hover {
  background: rgba(239, 68, 68, 0.12);
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

  .form-row {
    flex-direction: column;
    gap: 0;
  }
}
</style>
