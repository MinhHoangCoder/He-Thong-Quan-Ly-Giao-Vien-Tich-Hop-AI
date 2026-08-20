<script setup>
// Trang Quản lý Giáo viên — ĐÃ GỌN: chỉ còn bảng danh sách + thùng rác + xác nhận xóa
// Phần "Thêm/Sửa giáo viên" đã được gộp vào TeacherManager.vue
import { ref, computed, reactive, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import Pagination from '@/components/ui/Pagination.vue'
import TeacherManager from './TeacherManager.vue'
import { teacherApi } from '@/api/teacher'
import { branchApi } from '@/api/branches'
import { useAuthStore } from '@/stores/auth'

/* ══════════════════════════════════════════════════════════
   PHÂN QUYỀN THEO VAI TRÒ
   ADMIN / EMPLOYEE: đầy đủ chức năng (xem, sửa, xóa mềm, lịch sử)
   TEACHER: chỉ xem + sửa — KHÔNG có nút "Xóa" và tab "Lịch sử"
══════════════════════════════════════════════════════════ */
const auth = useAuthStore()
const canManage = computed(() => auth.roles.some((r) => ['ADMIN', 'EMPLOYEE'].includes(r)))
const isAdmin = computed(() => auth.roles.includes('ADMIN'))

/* ══════════════════════════════════════════════════════════
   STATE
══════════════════════════════════════════════════════════ */
const loading = ref(false)
const teachers = ref([])
const branches = ref([])

const filters = reactive({ keyword: '', status: '', employmentType: '', branchId: '' })

const viewMode = ref('list') // 'list' | 'trash'

// ── Phân trang (client-side) — dùng component dùng chung <Pagination /> ──
const PAGE_SIZE = 6
const page = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / PAGE_SIZE)))
const paginatedTeachers = computed(() => {
  const start = page.value * PAGE_SIZE
  return filtered.value.slice(start, start + PAGE_SIZE)
})
watch(filters, () => (page.value = 0))

// Xóa mềm — chọn nhiều
const deleteMode = ref(false)
const selectedIds = ref([])

// Thùng rác
const trashItems = ref([])
const trashLoading = ref(false)

// Confirm xóa
const confirmDelete = reactive({ open: false })
const confirmPurge = reactive({
  open: false,
  id: null,
  name: '',
  phone: '',
  typedName: '',
  purging: false,
})

// Toast
const toast = reactive({ show: false, msg: '', type: 'success' })
function showToast(msg, type = 'success') {
  toast.msg = msg
  toast.type = type
  toast.show = true
  setTimeout(() => (toast.show = false), 3000)
}

/* ══════════════════════════════════════════════════════════
   TeacherManager — modal Thêm/Sửa dùng chung
══════════════════════════════════════════════════════════ */
const managerOpen = ref(false)
const managerMode = ref('create') // 'create' | 'edit'
const managerTeacher = ref(null)

function openCreate() {
  managerMode.value = 'create'
  managerTeacher.value = null
  managerOpen.value = true
}
function openEdit(teacher) {
  managerMode.value = 'edit'
  managerTeacher.value = teacher
  managerOpen.value = true
}
function onManagerSaved(msg) {
  showToast(msg)
  loadTeachers()
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
// Khớp bộ giá trị Teacher.EmploymentType sau migration V20 (CO_HUU / THINH_GIANG).
const empLabel = { CO_HUU: 'Cơ hữu', THINH_GIANG: 'Thỉnh giảng' }
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
    // /branches trả về TOÀN BỘ chi nhánh (BranchController.list() dùng findAll(),
    // không lọc theo phạm vi người dùng). FE không hardcode gì cả.
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
  if (route.query.q) filters.keyword = String(route.query.q)
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
   XÓA MỀM (chỉ ADMIN / EMPLOYEE)
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
  if (!canManage.value || !selectedIds.value.length) return
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

/* ── Khôi phục từ thùng rác ── */
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

/* ── Xóa vĩnh viễn (chỉ ADMIN) — chỉ áp dụng cho GV trong thùng rác ── */
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
  if (confirmPurge.phone) return confirmPurge.typedName.trim() === confirmPurge.phone.trim()
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
    showToast(e?.response?.data?.message || 'Xóa vĩnh viễn thất bại', 'error')
  } finally {
    confirmPurge.purging = false
  }
}

/* ══════════════════════════════════════════════════════════
   HELPERS
══════════════════════════════════════════════════════════ */
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

      <div v-if="loading" class="tl__loading">
        <span class="spinner" />
        <span>Đang tải danh sách…</span>
      </div>

      <div v-else-if="filtered.length" class="table-wrap">
        <table class="teacher-table">
          <thead>
            <tr>
              <th v-if="deleteMode" class="col-check"></th>
              <th class="col-stt">STT</th>
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
              v-for="(t, i) in paginatedTeachers"
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

              <!-- STT: đếm liên tục qua các trang (trang 2 bắt đầu từ 7), KHÔNG
                   phải Id. Id là khóa cố định của hồ sơ nên sẽ thủng lỗ sau mỗi
                   lần xóa; STT luôn là 1..N theo đúng danh sách đang hiển thị. -->
              <td class="col-stt">
                <span class="t-stt">{{ page * PAGE_SIZE + i + 1 }}</span>
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

              <td>
                <div class="t-contact"><strong>SĐT:</strong> {{ t.phone || '—' }}</div>
                <div class="t-contact t-contact--muted">
                  <strong>CCCD:</strong>{{ t.idCardNo || '—' }}
                </div>
              </td>

              <td>
                <span class="badge badge--branch">{{ branchName(t.branchId) }}</span>
              </td>

              <td>{{ t.employmentType ? empLabel[t.employmentType] : '—' }}</td>

              <td>
                <span :class="['badge', statusClass[t.status]]">{{ statusLabel[t.status] }}</span>
              </td>

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

      <Pagination
        v-if="filtered.length && totalPages > 1"
        v-model="page"
        :total-pages="totalPages"
      />

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
              <th class="col-stt">STT</th>
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
            <tr v-for="(item, i) in trashItems" :key="item.id">
              <!-- Thùng rác không phân trang nên STT chạy thẳng 1..N. -->
              <td class="col-stt">
                <span class="t-stt">{{ i + 1 }}</span>
              </td>
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
         Modal Thêm/Sửa giáo viên — component dùng chung
    ═══════════════════════════════════════════════ -->
    <TeacherManager
      :open="managerOpen"
      :mode="managerMode"
      :teacher="managerTeacher"
      :branches="branches"
      @update:open="managerOpen = $event"
      @saved="onManagerSaved"
    />

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
            <p class="modal__body">Are you <strong>SURE ?</strong></p>
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
  </div>
</template>

<style scoped>
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
.col-stt {
  width: 52px;
  text-align: center;
}
.col-action {
  width: 130px;
}

/* Số thứ tự: chữ số canh giữa, dùng font tabular để các hàng thẳng cột
   khi số nhảy từ 1 chữ số lên 2-3 chữ số. */
.t-stt {
  font-variant-numeric: tabular-nums;
  font-size: 0.85rem;
  color: var(--a-text-muted);
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
.t-contact {
  white-space: nowrap;
}
.t-contact--muted {
  color: var(--a-text-muted);
  font-size: 0.8rem;
  margin-top: 0.1rem;
}
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
.badge--branch {
  background: #eff6ff;
  color: #1d4ed8;
}
.row-actions {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}
.ra-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  transition: 0.2s;
}
.ra-btn--edit {
  background: #f0fdf4;
  color: #16a34a;
}
.ra-btn--delete {
  background: #fef2f2;
  color: #dc2626;
}
.ra-btn--edit:hover,
.ra-btn--edit:focus-visible {
  background: #16a34a;
  color: #fff;
}
.ra-btn--delete:hover,
.ra-btn--delete:focus-visible {
  background: #dc2626;
  color: #fff;
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

/* ── Modal (xác nhận xóa / xóa vĩnh viễn) ── */
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
.modal__title {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--a-text);
  margin: 0;
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
/* Ô Kinh nghiệm dừng nhận chữ đúng ở 500 ký tự (bằng NVARCHAR(500) dưới DB).
   Không có số đếm thì người dùng gõ tới hạn sẽ tưởng bàn phím hỏng. */
.char-counter {
  font-size: 0.78rem;
  color: var(--a-text-muted);
  text-align: right;
  margin: 0.35rem 0 0;
}

/* ── Buttons ── */
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

/* Responsive */
@media (max-width: 640px) {
  .tl__header {
    flex-direction: column;
  }
  .filter-total {
    flex-direction: row;
    gap: 0.4rem;
  }
}
</style>
