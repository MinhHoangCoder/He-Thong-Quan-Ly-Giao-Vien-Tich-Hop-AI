<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import { teacherApi } from '@/api/teacher'
import { branchApi } from '@/api/branches'

const router = useRouter()

// Thêm mới GV = tạo TÀI KHOẢN vai trò TEACHER (AppUser + Teacher được tạo cùng lúc
// ở RegistrationService), nên điều hướng sang trang "Tạo tài khoản" thay vì mở modal riêng.
function goToCreateTeacher() {
  router.push({ path: '/users/new', query: { role: 'TEACHER' } })
}
 
/* ══════════════════════════════════════════════════════════
   STATE
══════════════════════════════════════════════════════════ */
const loading = ref(false)
const teachers = ref([])
const branches = ref([])
 
// Bộ lọc
const filters = reactive({
  keyword: '',
  status: '',
  employmentType: '',
  branchId: '',
})
 
// Chế độ giao diện
const viewMode = ref('list') // 'list' | 'trash'
 
// Xóa mềm — chọn nhiều
const deleteMode = ref(false)
const selectedIds = ref([])
 
// Thùng rác
const trashItems = ref([])
const trashLoading = ref(false)
 
// Modal chi tiết / sửa
const detailModal = reactive({ open: false, teacher: null })
const editModal = reactive({
  open: false,
  id: null,
  saving: false,
  error: '',
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
    gender: null,
  },
})
 
// Confirm xóa
const confirmDelete = reactive({ open: false })
 
// Notification toast
const toast = reactive({ show: false, msg: '', type: 'success' })
 
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
 
onMounted(async () => {
  await Promise.all([loadTeachers(), loadBranches()])
})
 
/* ══════════════════════════════════════════════════════════
   SWITCH VIEW
══════════════════════════════════════════════════════════ */
function switchView(mode) {
  viewMode.value = mode
  deleteMode.value = false
  selectedIds.value = []
  if (mode === 'trash') loadTrash()
}
 
/* ══════════════════════════════════════════════════════════
   XEM CHI TIẾT
══════════════════════════════════════════════════════════ */
async function openDetail(teacher) {
  try {
    const res = await teacherApi.get(teacher.id)
    detailModal.teacher = res.data
    detailModal.open = true
  } catch {
    showToast('Không tải được chi tiết giáo viên', 'error')
  }
}
 
/* ══════════════════════════════════════════════════════════
   SỬA
══════════════════════════════════════════════════════════ */
function openEdit(teacher) {
  editModal.id = teacher.id
  editModal.error = ''
  editModal.saving = false
  Object.assign(editModal.form, {
    branchId: teacher.branchId,
    firstName: teacher.firstName,
    lastName: teacher.lastName,
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
}
 
async function saveEdit() {
  editModal.saving = true
  editModal.error = ''
  try {
    await teacherApi.update(editModal.id, {
      ...editModal.form,
      branchId: Number(editModal.form.branchId),
      gender: editModal.form.gender === '' ? null : editModal.form.gender,
    })
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
   XÓA MỀM
══════════════════════════════════════════════════════════ */
function toggleDeleteMode() {
  deleteMode.value = !deleteMode.value
  selectedIds.value = []
}
 
function toggleSelect(id) {
  const idx = selectedIds.value.indexOf(id)
  if (idx === -1) selectedIds.value.push(id)
  else selectedIds.value.splice(idx, 1)
}
 
function requestDelete() {
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
  } catch {
    showToast('Xóa thất bại, vui lòng thử lại', 'error')
  }
}
 
/* ══════════════════════════════════════════════════════════
   KHÔI PHỤC TỪ THÙNG RÁC
══════════════════════════════════════════════════════════ */
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
 
const statusLabel = { ACTIVE: 'Đang hoạt động', RETIRED: 'Đã nghỉ', SUSPENDED: 'Tạm dừng' }
const statusClass = { ACTIVE: 'badge--active', RETIRED: 'badge--retired', SUSPENDED: 'badge--suspended' }
const empLabel = { FULL_TIME: 'Toàn thời gian', PART_TIME: 'Bán thời gian', CONTRACT: 'Hợp đồng' }
 
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
        <p class="tl__sub">Danh sách, tìm kiếm và quản lý thông tin toàn bộ giáo viên</p>
      </div>
      <div class="tl__header-actions">
        <button
          :class="['btn-tab', viewMode === 'list' && 'btn-tab--active']"
          @click="switchView('list')"
        >
          <SvgIcon name="teacher" :size="16" /> Danh sách
        </button>
        <button
          :class="['btn-tab btn-tab--history', viewMode === 'trash' && 'btn-tab--active']"
          @click="switchView('trash')"
          title="Lịch sử (giáo viên đã bị ẩn)"
        >
          <SvgIcon name="history" :size="16" /> Lịch sử
        </button>
        <button
          class="btn-tab btn-tab--add"
          title="Tạo tài khoản giáo viên mới"
          @click="goToCreateTeacher"
        >
          <SvgIcon name="plus" :size="16" /> Thêm mới
        </button>
      </div>
    </div>
 
    <!-- ═══════════════════════════════════════════════
         VIEW: DANH SÁCH CHÍNH
    ═══════════════════════════════════════════════ -->
    <template v-if="viewMode === 'list'">
 
      <!-- ── Thanh lọc ──────────────────────────────── -->
      <div class="filters">
        <div class="filter-search">
          <SvgIcon name="search" :size="16" />
          <input
            v-model="filters.keyword"
            type="text"
            placeholder="Tìm theo tên, SĐT, CCCD…"
          />
        </div>
 
        <select v-model="filters.status" class="filter-select">
          <option value="">Tất cả trạng thái</option>
          <option value="ACTIVE">Đang hoạt động</option>
          <option value="RETIRED">Đã nghỉ</option>
          <option value="SUSPENDED">Tạm dừng</option>
        </select>
 
        <select v-model="filters.employmentType" class="filter-select">
          <option value="">Tất cả loại hình</option>
          <option value="FULL_TIME">Toàn thời gian</option>
          <option value="PART_TIME">Bán thời gian</option>
          <option value="CONTRACT">Hợp đồng</option>
        </select>
 
        <select v-model="filters.branchId" class="filter-select">
          <option value="">Tất cả chi nhánh</option>
          <option v-for="b in branches" :key="b.id" :value="b.id">{{ b.name }}</option>
        </select>
 
        <span class="filter-count">{{ filtered.length }} giáo viên</span>
 
        <!-- Nút xóa -->
        <button
          :class="['btn-delete-toggle', deleteMode && 'btn-delete-toggle--active']"
          @click="toggleDeleteMode"
          title="Chọn giáo viên để ẩn"
        >
          <SvgIcon name="trash" :size="16" />
          {{ deleteMode ? 'Hủy chọn' : 'Xóa' }}
        </button>
 
        <!-- Nút xác nhận xóa (hiện khi đã chọn) -->
        <button
          v-if="deleteMode && selectedIds.length"
          class="btn-confirm-delete"
          @click="requestDelete"
        >
          Ẩn {{ selectedIds.length }} giáo viên
        </button>
      </div>
 
      <!-- ── Trạng thái loading ────────────────────── -->
      <div v-if="loading" class="tl__loading">
        <span class="spinner" />
        <span>Đang tải danh sách…</span>
      </div>
 
      <!-- ── Danh sách thẻ giáo viên ─────────────── -->
      <div v-else-if="filtered.length" class="teacher-grid">
        <div
          v-for="t in filtered"
          :key="t.id"
          :class="['teacher-card', deleteMode && selectedIds.includes(t.id) && 'teacher-card--selected']"
        >
          <!-- Tick chọn khi delete mode -->
          <div v-if="deleteMode" class="card-tick" @click="toggleSelect(t.id)">
            <span :class="['tick', selectedIds.includes(t.id) && 'tick--checked']">
              <SvgIcon v-if="selectedIds.includes(t.id)" name="attendance" :size="13" />
            </span>
          </div>
 
          <!-- Avatar -->
          <div class="card-avatar">
            <span class="card-avatar__initials">
              {{ (t.firstName || '?')[0].toUpperCase() }}
            </span>
            <span :class="['card-status-dot', t.status === 'ACTIVE' ? 'dot--active' : 'dot--off']" />
          </div>
 
          <!-- Thông tin -->
          <div class="card-info">
            <div class="card-name">{{ t.fullName }}</div>
            <div class="card-meta">
              <span :class="['badge', statusClass[t.status]]">{{ statusLabel[t.status] }}</span>
              <span v-if="t.employmentType" class="badge badge--emp">{{ empLabel[t.employmentType] }}</span>
            </div>
            <div class="card-detail">
              <span v-if="t.phone"><SvgIcon name="phone" :size="12" /> {{ t.phone }}</span>
              <span><SvgIcon name="school" :size="12" /> {{ branchName(t.branchId) }}</span>
            </div>
          </div>
 
          <!-- CRUD Icons -->
          <div v-if="!deleteMode" class="card-actions">
            <button class="ca-btn ca-btn--view" title="Xem chi tiết" @click="openDetail(t)">
              <SvgIcon name="eye" :size="16" />
            </button>
            <button class="ca-btn ca-btn--edit" title="Chỉnh sửa" @click="openEdit(t)">View
              <SvgIcon name="edit" :size="16" />
            </button>
            <button class="ca-btn ca-btn--delete" title="Ẩn giáo viên" @click="() => { deleteMode = true; selectedIds = [t.id]; requestDelete() }">
              <SvgIcon name="trash" :size="16" />
            </button>
          </div>
 
          <!-- Click toàn thẻ khi delete mode -->
          <div v-if="deleteMode" class="card-overlay" @click="toggleSelect(t.id)" />
        </div>
      </div>
 
      <!-- Empty state -->
      <div v-else class="tl__empty">
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
          <h2 class="trash-title">
            <SvgIcon name="history" :size="20" /> Lịch sử giáo viên đã ẩn
          </h2>
          <p class="trash-sub">Các giáo viên bên dưới đã bị ẩn khỏi danh sách chính. Bạn có thể khôi phục bất kỳ lúc nào.</p>
        </div>
      </div>
 
      <div v-if="trashLoading" class="tl__loading">
        <span class="spinner" /> Đang tải…
      </div>
 
      <div v-else-if="trashItems.length" class="trash-table-wrap">
        <table class="trash-table">
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
              <td class="trash-name">{{ item.fullName }}</td>
              <td>{{ item.phone || '—' }}</td>
              <td>{{ item.idCardNo || '—' }}</td>
              <td>{{ empLabel[item.employmentType] || '—' }}</td>
              <td><span :class="['badge', statusClass[item.status]]">{{ statusLabel[item.status] }}</span></td>
              <td>{{ formatDate(item.deletedAt) }}</td>
              <td>{{ branchName(item.branchId) }}</td>
              <td>
                <button class="btn-restore" @click="restore(item.id)">
                  <SvgIcon name="restore" :size="14" /> Khôi phục
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
 
      <div v-else class="tl__empty">
        <SvgIcon name="history" :size="48" />
        <p>Chưa có giáo viên nào trong lịch sử</p>
      </div>
    </template>
 
    <!-- ═══════════════════════════════════════════════
         MODAL: XÁC NHẬN XÓA
    ═══════════════════════════════════════════════ -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="confirmDelete.open" class="overlay" @click.self="confirmDelete.open = false">
          <div class="modal modal--sm">
            <div class="modal__icon modal__icon--warn">
              <SvgIcon name="trash" :size="28" />
            </div>
            <h3 class="modal__title">Bạn chắc chắn muốn ẩn không?</h3>
            <p class="modal__body">
              {{ selectedIds.length }} giáo viên sẽ bị ẩn khỏi danh sách chính và
              có thể khôi phục lại trong mục <strong>Lịch sử</strong>.
            </p>
            <div class="modal__footer">
              <button class="btn btn--ghost" @click="confirmDelete.open = false">Không</button>
              <button class="btn btn--danger" @click="confirmDoDelete">Có, ẩn đi</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
 
    <!-- ═══════════════════════════════════════════════
         MODAL: CHI TIẾT GIÁO VIÊN
    ═══════════════════════════════════════════════ -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="detailModal.open" class="overlay" @click.self="detailModal.open = false">
          <div class="modal modal--lg">
            <div class="modal__head">
              <h3 class="modal__title">Chi tiết Giáo viên</h3>
              <button class="modal__close" @click="detailModal.open = false">
                <SvgIcon name="close" :size="18" />
              </button>
            </div>
 
            <template v-if="detailModal.teacher">
              <div class="detail-hero">
                <div class="detail-avatar">
                  {{ (detailModal.teacher.firstName || '?')[0].toUpperCase() }}
                </div>
                <div>
                  <div class="detail-name">{{ detailModal.teacher.fullName }}</div>
                  <div class="detail-meta">
                    <span :class="['badge', statusClass[detailModal.teacher.status]]">{{ statusLabel[detailModal.teacher.status] }}</span>
                    <span v-if="detailModal.teacher.employmentType" class="badge badge--emp">{{ empLabel[detailModal.teacher.employmentType] }}</span>
                  </div>
                </div>
              </div>
 
              <div class="detail-grid">
                <div class="dg-item"><span class="dg-label">Chi nhánh</span><span>{{ branchName(detailModal.teacher.branchId) }}</span></div>
                <div class="dg-item"><span class="dg-label">SĐT</span><span>{{ detailModal.teacher.phone || '—' }}</span></div>
                <div class="dg-item"><span class="dg-label">CCCD</span><span>{{ detailModal.teacher.idCardNo || '—' }}</span></div>
                <div class="dg-item"><span class="dg-label">Giới tính</span><span>{{ detailModal.teacher.gender === true ? 'Nam' : detailModal.teacher.gender === false ? 'Nữ' : '—' }}</span></div>
                <div class="dg-item"><span class="dg-label">Ngày sinh</span><span>{{ formatDate(detailModal.teacher.dateOfBirth) }}</span></div>
                <div class="dg-item"><span class="dg-label">Ngày vào làm</span><span>{{ formatDate(detailModal.teacher.hireDate) }}</span></div>
                <div class="dg-item dg-item--full"><span class="dg-label">Địa chỉ</span><span>{{ detailModal.teacher.address || '—' }}</span></div>
              </div>
 
              <!-- Hợp đồng -->
              <div v-if="detailModal.teacher.contract" class="detail-section">
                <h4 class="detail-section-title"><SvgIcon name="assignment" :size="15" /> Hợp đồng</h4>
                <div class="detail-grid">
                  <div class="dg-item"><span class="dg-label">Số HĐ</span><span>{{ detailModal.teacher.contract.contractNo }}</span></div>
                  <div class="dg-item"><span class="dg-label">Bắt đầu</span><span>{{ formatDate(detailModal.teacher.contract.startDate) }}</span></div>
                  <div class="dg-item"><span class="dg-label">Kết thúc</span><span>{{ formatDate(detailModal.teacher.contract.endDate) }}</span></div>
                  <div class="dg-item"><span class="dg-label">Lương cơ bản</span><span>{{ detailModal.teacher.contract.baseSalary?.toLocaleString('vi-VN') || '—' }} ₫</span></div>
                  <div class="dg-item"><span class="dg-label">Phụ cấp</span><span>{{ detailModal.teacher.contract.allowance?.toLocaleString('vi-VN') || '—' }} ₫</span></div>
                  <div class="dg-item"><span class="dg-label">Trạng thái</span><span>{{ detailModal.teacher.contract.status }}</span></div>
                </div>
              </div>
 
              <!-- Chứng chỉ -->
              <div v-if="detailModal.teacher.certificates?.length" class="detail-section">
                <h4 class="detail-section-title"><SvgIcon name="subject" :size="15" /> Chứng chỉ ({{ detailModal.teacher.certificates.length }})</h4>
                <div class="cert-list">
                  <div v-for="c in detailModal.teacher.certificates" :key="c.id" class="cert-item">
                    <strong>{{ c.name }}</strong>
                    <span v-if="c.issuer"> · {{ c.issuer }}</span>
                    <span v-if="c.issueDate"> · {{ formatDate(c.issueDate) }}</span>
                    <span v-if="c.expiryDate"> → {{ formatDate(c.expiryDate) }}</span>
                  </div>
                </div>
              </div>
            </template>
 
            <div class="modal__footer">
              <button class="btn btn--primary" @click="detailModal.open = false">Đóng</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
 
    <!-- ═══════════════════════════════════════════════
         MODAL: SỬA GIÁO VIÊN
    ═══════════════════════════════════════════════ -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="editModal.open" class="overlay" @click.self="editModal.open = false">
          <div class="modal modal--lg">
            <div class="modal__head">
              <h3 class="modal__title">Chỉnh sửa Giáo viên</h3>
              <button class="modal__close" @click="editModal.open = false">
                <SvgIcon name="close" :size="18" />
              </button>
            </div>
 
            <div class="edit-form">
              <div class="form-row">
                <label class="form-label">Họ và tên đệm <span class="req">*</span>
                  <input v-model="editModal.form.lastName" class="form-input" placeholder="VD: Trần Nguyễn Văn" />
                </label>
                <label class="form-label">Tên gọi <span class="req">*</span>
                  <input v-model="editModal.form.firstName" class="form-input" placeholder="VD: A" />
                </label>
              </div>
 
              <div class="form-row">
                <label class="form-label">Chi nhánh <span class="req">*</span>
                  <select v-model="editModal.form.branchId" class="form-input">
                    <option value="">-- Chọn chi nhánh --</option>
                    <option v-for="b in branches" :key="b.id" :value="b.id">{{ b.name }}</option>
                  </select>
                </label>
                <label class="form-label">Trạng thái <span class="req">*</span>
                  <select v-model="editModal.form.status" class="form-input">
                    <option value="ACTIVE">Đang hoạt động</option>
                    <option value="RETIRED">Đã nghỉ</option>
                    <option value="SUSPENDED">Tạm dừng</option>
                  </select>
                </label>
              </div>
 
              <div class="form-row">
                <label class="form-label">Loại hình
                  <select v-model="editModal.form.employmentType" class="form-input">
                    <option value="">-- Chọn loại hình --</option>
                    <option value="FULL_TIME">Toàn thời gian</option>
                    <option value="PART_TIME">Bán thời gian</option>
                    <option value="CONTRACT">Hợp đồng</option>
                  </select>
                </label>
                <label class="form-label">Giới tính
                  <select v-model="editModal.form.gender" class="form-input">
                    <option :value="null">-- Chọn --</option>
                    <option :value="true">Nam</option>
                    <option :value="false">Nữ</option>
                  </select>
                </label>
              </div>
 
              <div class="form-row">
                <label class="form-label">Số điện thoại
                  <input v-model="editModal.form.phone" class="form-input" placeholder="VD: 0901234567" />
                </label>
                <label class="form-label">Số CCCD
                  <input v-model="editModal.form.idCardNo" class="form-input" placeholder="9 hoặc 12 chữ số" />
                </label>
              </div>
 
              <div class="form-row">
                <label class="form-label">Ngày sinh
                  <input v-model="editModal.form.dateOfBirth" type="date" class="form-input" />
                </label>
                <label class="form-label">Ngày vào làm
                  <input v-model="editModal.form.hireDate" type="date" class="form-input" />
                </label>
              </div>
 
              <label class="form-label">Địa chỉ
                <input v-model="editModal.form.address" class="form-input" placeholder="Địa chỉ thường trú" />
              </label>
 
              <p v-if="editModal.error" class="form-error">{{ editModal.error }}</p>
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
  max-width: 1200px;
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
}
.btn-tab {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.5rem 1rem;
  border: 1.5px solid var(--a-border);
  border-radius: 9px;
  background: #fff;
  color: var(--a-text-muted);
  font-size: 0.86rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-tab:hover { border-color: var(--c-primary); color: var(--c-primary); }
.btn-tab--active { background: var(--grad-primary); border-color: transparent; color: #fff; }
.btn-tab--history:not(.btn-tab--active) { color: var(--a-text-muted); }
.btn-tab--add {
  background: var(--grad-primary);
  border-color: transparent;
  color: #fff;
  margin-left: 0.25rem;
}
.btn-tab--add:hover { filter: brightness(1.08); }
 
/* ── Filters ── */
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.65rem;
  margin-bottom: 1.4rem;
  padding: 1rem 1.1rem;
  background: #fff;
  border: 1px solid var(--a-border);
  border-radius: 12px;
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
  transition: border-color 0.15s, box-shadow 0.15s;
}
.filter-search:focus-within {
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(249,115,22,.12);
}
.filter-search input {
  border: none; background: transparent; outline: none; width: 100%;
  font-size: 0.875rem; color: var(--a-text);
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
.filter-select:focus { border-color: var(--c-primary); }
.filter-count {
  font-size: 0.82rem;
  color: var(--a-text-muted);
  white-space: nowrap;
  margin-left: auto;
}
 
.btn-delete-toggle {
  display: flex; align-items: center; gap: 0.4rem;
  padding: 0.45rem 0.9rem;
  background: transparent; border: 1.5px solid #fca5a5;
  border-radius: 8px; color: #dc2626; font-size: 0.84rem;
  font-weight: 600; cursor: pointer; transition: all 0.15s;
}
.btn-delete-toggle:hover, .btn-delete-toggle--active {
  background: #fef2f2; border-color: #dc2626;
}
.btn-confirm-delete {
  padding: 0.45rem 1rem;
  background: #dc2626; color: #fff;
  border: none; border-radius: 8px;
  font-size: 0.84rem; font-weight: 700;
  cursor: pointer; transition: background 0.15s;
}
.btn-confirm-delete:hover { background: #b91c1c; }
 
/* ── Loading / Empty ── */
.tl__loading {
  display: flex; align-items: center; gap: 0.75rem;
  color: var(--a-text-muted); font-size: 0.9rem;
  padding: 2rem 0;
}
.tl__empty {
  display: flex; flex-direction: column; align-items: center;
  gap: 1rem; padding: 3rem 0; color: var(--a-text-muted);
  opacity: 0.55; text-align: center;
}
 
/* ── Spinner ── */
.spinner {
  display: inline-block;
  width: 20px; height: 20px;
  border: 2.5px solid var(--a-border);
  border-top-color: var(--c-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
.spinner--sm { width: 14px; height: 14px; }
@keyframes spin { to { transform: rotate(360deg); } }
 
/* ── Teacher grid ── */
.teacher-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1rem;
}
.teacher-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.85rem;
  background: #fff;
  border: 1.5px solid var(--a-border);
  border-radius: 14px;
  padding: 1rem 1.1rem;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
  overflow: hidden;
}
.teacher-card:hover {
  border-color: var(--c-primary);
  box-shadow: 0 4px 16px rgba(249,115,22,.1);
  transform: translateY(-2px);
}
.teacher-card--selected {
  border-color: #dc2626;
  background: #fff5f5;
}
 
/* Tick chọn */
.card-tick {
  position: absolute; top: 0.6rem; right: 0.6rem; z-index: 2;
  cursor: pointer;
}
.tick {
  display: grid; place-items: center;
  width: 20px; height: 20px;
  border: 2px solid var(--a-border);
  border-radius: 6px; background: #fff;
  transition: all 0.15s;
}
.tick--checked { background: #dc2626; border-color: #dc2626; color: #fff; }
 
/* Avatar */
.card-avatar {
  position: relative; flex-shrink: 0;
  width: 48px; height: 48px;
}
.card-avatar__initials {
  display: grid; place-items: center;
  width: 100%; height: 100%;
  border-radius: 50%;
  background: var(--grad-primary);
  color: #fff; font-size: 1.2rem; font-weight: 700;
}
.card-status-dot {
  position: absolute; bottom: 1px; right: 1px;
  width: 11px; height: 11px;
  border-radius: 50%; border: 2px solid #fff;
}
.dot--active { background: #22c55e; }
.dot--off { background: #94a3b8; }
 
/* Card info */
.card-info { flex: 1; min-width: 0; }
.card-name {
  font-size: 0.95rem; font-weight: 700;
  color: var(--a-text);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.card-meta { display: flex; gap: 0.35rem; flex-wrap: wrap; margin: 0.3rem 0 0.35rem; }
.card-detail {
  display: flex; gap: 0.75rem; flex-wrap: wrap;
  font-size: 0.78rem; color: var(--a-text-muted);
}
.card-detail span { display: flex; align-items: center; gap: 0.3rem; }
 
/* Badge */
.badge {
  display: inline-flex; align-items: center;
  padding: 0.15rem 0.55rem;
  border-radius: 20px; font-size: 0.72rem; font-weight: 700;
}
.badge--active { background: #dcfce7; color: #166534; }
.badge--retired { background: #f1f5f9; color: #64748b; }
.badge--suspended { background: #fef9c3; color: #854d0e; }
.badge--emp { background: #eff6ff; color: #1d4ed8; }
 
/* CRUD Actions */
.card-actions {
  display: flex; flex-direction: column; gap: 0.3rem;
  flex-shrink: 0; opacity: 0; transition: opacity 0.15s;
}
.teacher-card:hover .card-actions { opacity: 1; }
.ca-btn {
  display: grid; place-items: center;
  width: 30px; height: 30px;
  border: none; border-radius: 8px;
  cursor: pointer; transition: all 0.15s;
}
.ca-btn--view  { background: #eff6ff; color: #2563eb; }
.ca-btn--edit  { background: #f0fdf4; color: #16a34a; }
.ca-btn--delete { background: #fef2f2; color: #dc2626; }
.ca-btn:hover { filter: brightness(0.92); transform: scale(1.1); }
 
/* Overlay khi delete mode */
.card-overlay {
  position: absolute; inset: 0; cursor: pointer; z-index: 1;
}
 
/* ── Trash table ── */
.trash-header { margin-bottom: 1.2rem; }
.trash-title {
  font-size: 1.1rem; font-weight: 700; color: var(--a-text);
  display: flex; align-items: center; gap: 0.5rem; margin: 0 0 0.3rem;
}
.trash-sub { font-size: 0.84rem; color: var(--a-text-muted); margin: 0; }
.trash-table-wrap { overflow-x: auto; background: #fff; border: 1px solid var(--a-border); border-radius: 12px; }
.trash-table { width: 100%; border-collapse: collapse; font-size: 0.875rem; }
.trash-table th {
  padding: 0.75rem 1rem; text-align: left;
  font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.5px;
  color: var(--a-text-muted); background: var(--a-bg);
  border-bottom: 1px solid var(--a-border);
}
.trash-table td {
  padding: 0.85rem 1rem;
  border-bottom: 1px solid var(--a-border);
  color: var(--a-text);
}
.trash-table tr:last-child td { border-bottom: none; }
.trash-name { font-weight: 600; }
.btn-restore {
  display: flex; align-items: center; gap: 0.4rem;
  padding: 0.35rem 0.75rem;
  background: #f0fdf4; color: #16a34a;
  border: 1px solid #bbf7d0; border-radius: 7px;
  font-size: 0.8rem; font-weight: 600; cursor: pointer;
  transition: all 0.15s;
}
.btn-restore:hover { background: #dcfce7; }
 
/* ── Modal ── */
.overlay {
  position: fixed; inset: 0; z-index: 100;
  background: rgba(15,23,42,.5);
  display: flex; align-items: center; justify-content: center;
  padding: 1.5rem;
}
.modal {
  background: #fff; border-radius: 18px;
  box-shadow: 0 20px 60px rgba(0,0,0,.18);
  width: 100%; max-height: 90vh;
  overflow-y: auto; padding: 1.75rem 2rem;
}
.modal--sm { max-width: 420px; text-align: center; }
.modal--lg { max-width: 680px; }
.modal__head {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 1.4rem;
}
.modal__title { font-size: 1.1rem; font-weight: 700; color: var(--a-text); margin: 0; }
.modal__close {
  display: grid; place-items: center;
  width: 34px; height: 34px;
  border: none; border-radius: 8px; background: var(--a-bg);
  color: var(--a-text-muted); cursor: pointer;
}
.modal__close:hover { background: #fee2e2; color: #dc2626; }
.modal__icon--warn {
  display: grid; place-items: center;
  width: 64px; height: 64px; border-radius: 50%;
  background: #fef2f2; color: #dc2626;
  margin: 0 auto 1rem;
}
.modal__body { font-size: 0.9rem; color: var(--a-text-muted); margin: 0.5rem 0 0; }
.modal__footer {
  display: flex; justify-content: flex-end; gap: 0.75rem;
  margin-top: 1.5rem; padding-top: 1.25rem;
  border-top: 1px solid var(--a-border);
}
.modal--sm .modal__footer { justify-content: center; }
 
/* Buttons */
.btn {
  display: inline-flex; align-items: center; gap: 0.4rem;
  padding: 0.6rem 1.4rem; border-radius: 10px;
  font-size: 0.88rem; font-weight: 700; cursor: pointer;
  border: none; transition: all 0.15s;
}
.btn--primary { background: var(--grad-primary); color: #fff; }
.btn--primary:hover { filter: brightness(1.08); }
.btn--primary:disabled { opacity: 0.6; cursor: not-allowed; }
.btn--ghost {
  background: var(--a-bg); color: var(--a-text-muted);
  border: 1.5px solid var(--a-border);
}
.btn--ghost:hover { border-color: var(--c-primary); color: var(--c-primary); }
.btn--danger { background: #dc2626; color: #fff; }
.btn--danger:hover { background: #b91c1c; }
 
/* ── Detail modal ── */
.detail-hero {
  display: flex; align-items: center; gap: 1rem; margin-bottom: 1.4rem;
}
.detail-avatar {
  display: grid; place-items: center;
  width: 64px; height: 64px; border-radius: 50%;
  background: var(--grad-primary); color: #fff;
  font-size: 1.6rem; font-weight: 800; flex-shrink: 0;
}
.detail-name { font-size: 1.15rem; font-weight: 700; color: var(--a-text); }
.detail-meta { display: flex; gap: 0.4rem; flex-wrap: wrap; margin-top: 0.4rem; }
.detail-grid {
  display: grid; grid-template-columns: 1fr 1fr;
  gap: 0.75rem 1.5rem; margin-bottom: 1rem;
}
.dg-item { display: flex; flex-direction: column; gap: 0.2rem; }
.dg-item--full { grid-column: 1 / -1; }
.dg-label { font-size: 0.73rem; text-transform: uppercase; color: var(--a-text-muted); font-weight: 600; }
.detail-section { margin-top: 1.2rem; padding-top: 1rem; border-top: 1px solid var(--a-border); }
.detail-section-title {
  font-size: 0.88rem; font-weight: 700; color: var(--a-text);
  display: flex; align-items: center; gap: 0.4rem; margin: 0 0 0.75rem;
}
.cert-list { display: flex; flex-direction: column; gap: 0.4rem; }
.cert-item {
  padding: 0.5rem 0.75rem;
  background: var(--a-bg); border-radius: 8px;
  font-size: 0.84rem; color: var(--a-text);
}
 
/* ── Edit form ── */
.edit-form { display: flex; flex-direction: column; gap: 0.85rem; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 0.85rem; }
.form-label {
  display: flex; flex-direction: column; gap: 0.4rem;
  font-size: 0.82rem; font-weight: 600; color: var(--a-text-muted);
}
.req { color: #dc2626; }
.form-input {
  width: 100%; padding: 0.55rem 0.75rem;
  border: 1.5px solid var(--a-border); border-radius: 9px;
  font-size: 0.88rem; color: var(--a-text);
  background: var(--a-bg); outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
  box-sizing: border-box;
}
.form-input:focus {
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(249,115,22,.12);
  background: #fff;
}
.form-error {
  font-size: 0.84rem; color: #dc2626;
  background: #fef2f2; border-radius: 8px; padding: 0.5rem 0.75rem; margin: 0;
}
 
/* ── Toast ── */
.toast {
  position: fixed; top: 1.25rem; right: 1.25rem; z-index: 999;
  display: flex; align-items: center; gap: 0.6rem;
  padding: 0.75rem 1.2rem;
  border-radius: 12px;
  font-size: 0.88rem; font-weight: 600;
  box-shadow: 0 8px 24px rgba(0,0,0,.14);
}
.toast--success { background: #16a34a; color: #fff; }
.toast--error   { background: #dc2626; color: #fff; }
.toast-enter-active, .toast-leave-active { transition: all 0.25s; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateY(-8px) scale(0.95); }
 
/* Modal transition */
.modal-enter-active, .modal-leave-active { transition: all 0.22s; }
.modal-enter-from, .modal-leave-to { opacity: 0; transform: scale(0.95); }
 
/* Responsive */
@media (max-width: 640px) {
  .teacher-grid { grid-template-columns: 1fr; }
  .detail-grid, .form-row { grid-template-columns: 1fr; }
  .tl__header { flex-direction: column; }
}
</style>