<script setup>
/**
 * THÊM LỚP HÀNG LOẠT — hai tab, một luồng.
 *
 * Vì sao có màn này: mở một trường mới là phải nhập 12-15 lớp, mỗi lớp một lần bấm "Thêm" rồi
 * điền form rồi lưu. Nhân với vài chục trường thì đó là hàng trăm lần thao tác giống hệt nhau.
 *
 * TAB "SINH THEO MẪU" là cách chính xác tuyệt đối: chọn khối và số lớp mỗi khối, máy tự đặt
 * tên 1A1, 1A2… Không có file trung gian nên không có chỗ nào để gõ sai.
 *
 * TAB "NHẬP DỮ LIỆU" cho hai nguồn — dán từ Excel hoặc tải file .xlsx/.csv. Hai nguồn này dùng
 * CHUNG một bộ đọc, một bộ kiểm và một đường ghi ở backend.
 *
 * LUÔN XEM TRƯỚC RỒI MỚI LƯU. Bảng xem trước kể rõ từng dòng bị loại vì lý do gì ("dòng 7: lớp
 * 5A1 đã có ở trường"). Nhập 100 dòng sai 2 dòng mà bắt làm lại từ đầu là cách nhanh nhất để
 * người dùng quay về nhập tay.
 */
import { computed, reactive, ref } from 'vue'
import { classApi } from '@/api/classes'

const props = defineProps({
  /** Danh sách trường cho ô chọn. */
  schools: { type: Array, default: () => [] },
  /** Trường đang lọc ở màn danh sách — dùng làm giá trị mặc định. */
  defaultSchoolId: { type: [Number, String], default: '' },
})
const emit = defineEmits(['close', 'created'])

const KHOI = [1, 2, 3, 4, 5, 6, 7, 8, 9]

const tab = ref('mau') // 'mau' | 'nhap'
const nguon = ref('dan') // 'dan' | 'file'
const busy = ref(false)
const error = ref('')

const form = reactive({
  schoolId: props.defaultSchoolId || '',
  schoolYear: '',
  grades: [],
  soLopMoiKhoi: 3,
  duLieu: '',
})
const file = ref(null)

/** Kết quả xem trước từ server ({ rows, soHopLe, soDaTonTai, soLoi }). */
const xemTruoc = ref(null)

const coTheLuu = computed(() => !!xemTruoc.value && xemTruoc.value.soHopLe > 0)

const NHAN_TRANG_THAI = {
  HOP_LE: 'Sẽ tạo',
  DA_TON_TAI: 'Bỏ qua',
  LOI: 'Lỗi',
}

function toggleKhoi(k) {
  const i = form.grades.indexOf(k)
  if (i >= 0) form.grades.splice(i, 1)
  else form.grades.push(k)
}

function chonHetKhoi(tu, den) {
  form.grades = KHOI.filter((k) => k >= tu && k <= den)
}

function onFile(e) {
  file.value = e.target.files?.[0] ?? null
  xemTruoc.value = null
}

/** Bước 1: hỏi server xem danh sách sắp tạo trông thế nào. */
async function layXemTruoc() {
  error.value = ''
  xemTruoc.value = null
  if (!form.schoolId) {
    error.value = 'Vui lòng chọn trường.'
    return
  }
  busy.value = true
  try {
    const { data } =
      tab.value === 'nhap' && nguon.value === 'file'
        ? await classApi.bulkPreviewFile({
            schoolId: form.schoolId,
            schoolYear: form.schoolYear,
            file: file.value,
          })
        : await classApi.bulkPreview({
            schoolId: Number(form.schoolId),
            mode: tab.value === 'mau' ? 'GENERATE' : 'TEXT',
            schoolYear: form.schoolYear || undefined,
            grades: form.grades,
            soLopMoiKhoi: form.soLopMoiKhoi,
            duLieu: form.duLieu,
          })
    xemTruoc.value = data
  } catch (e) {
    error.value = e.response?.data?.message || 'Không đọc được dữ liệu.'
  } finally {
    busy.value = false
  }
}

/** Bước 2: ghi. Server kiểm lại từ đầu nên danh sách gửi lên chỉ là đề nghị. */
async function luu() {
  error.value = ''
  busy.value = true
  try {
    const { data } = await classApi.bulkCreate({
      schoolId: Number(form.schoolId),
      rows: xemTruoc.value.rows.filter((r) => r.trangThai === 'HOP_LE'),
    })
    emit('created', data)
  } catch (e) {
    error.value = e.response?.data?.message || 'Không tạo được lớp.'
  } finally {
    busy.value = false
  }
}

function doiTab(t) {
  tab.value = t
  xemTruoc.value = null
  error.value = ''
}
</script>

<template>
  <div class="modal" @click.self="emit('close')">
    <div class="modal-box bulk">
      <h2 class="modal-title">Thêm lớp hàng loạt</h2>

      <div class="tabs">
        <button :class="{ on: tab === 'mau' }" @click="doiTab('mau')">Sinh theo mẫu</button>
        <button :class="{ on: tab === 'nhap' }" @click="doiTab('nhap')">Nhập dữ liệu</button>
      </div>

      <div class="grid2">
        <label class="field">
          <span>Trường *</span>
          <select v-model="form.schoolId" @change="xemTruoc = null">
            <option value="">-- Chọn trường --</option>
            <option v-for="s in schools" :key="s.id" :value="s.id">{{ s.name }}</option>
          </select>
        </label>
        <label class="field">
          <span>Năm học</span>
          <input v-model="form.schoolYear" placeholder="2026-2027 (bỏ trống = năm học hiện tại)" />
        </label>
      </div>

      <!-- ═══════ TAB SINH THEO MẪU ═══════ -->
      <template v-if="tab === 'mau'">
        <div class="block">
          <div class="block__head">
            <span>Khối cần mở lớp</span>
            <span class="quick">
              <button class="link" @click="chonHetKhoi(1, 5)">Tiểu học (1–5)</button>
              <button class="link" @click="chonHetKhoi(6, 9)">THCS (6–9)</button>
              <button class="link" @click="form.grades = []">Bỏ chọn</button>
            </span>
          </div>
          <div class="khoi-list">
            <button
              v-for="k in KHOI"
              :key="k"
              class="khoi"
              :class="{ on: form.grades.includes(k) }"
              @click="toggleKhoi(k)"
            >
              Khối {{ k }}
            </button>
          </div>
        </div>

        <label class="field field--sm">
          <span>Số lớp mỗi khối</span>
          <input v-model.number="form.soLopMoiKhoi" type="number" min="1" max="20" />
        </label>
        <p class="hint">
          Ví dụ: chọn khối 1–5, mỗi khối 3 lớp → sinh 15 lớp tên 1A1, 1A2, 1A3, 2A1…
        </p>
      </template>

      <!-- ═══════ TAB NHẬP DỮ LIỆU ═══════ -->
      <template v-else>
        <div class="tabs tabs--sub">
          <button :class="{ on: nguon === 'dan' }" @click="((nguon = 'dan'), (xemTruoc = null))">
            Dán từ Excel
          </button>
          <button :class="{ on: nguon === 'file' }" @click="((nguon = 'file'), (xemTruoc = null))">
            Tải file
          </button>
        </div>

        <template v-if="nguon === 'dan'">
          <label class="field">
            <span>Dán dữ liệu</span>
            <textarea
              v-model="form.duLieu"
              rows="8"
              placeholder="1A1&#9;1&#9;2026-2027&#10;1A2&#9;1&#10;2A1"
            ></textarea>
          </label>
          <p class="hint">
            Mỗi dòng một lớp, cột cách nhau bằng Tab hoặc dấu phẩy, theo thứ tự
            <strong>Tên lớp · Khối · Năm học</strong>. Khối và Năm học bỏ trống được — khối suy từ
            chữ số đầu tên lớp, năm học lấy theo ô ở trên.
          </p>
        </template>

        <template v-else>
          <label class="field">
            <span>File .xlsx hoặc .csv</span>
            <input type="file" accept=".xlsx,.xls,.csv" @change="onFile" />
          </label>
          <p class="hint">
            Cột A là <strong>Tên lớp</strong>, cột B là <strong>Khối</strong>, cột C là
            <strong>Năm học</strong>. Có dòng tiêu đề cũng được, hệ thống tự bỏ qua.
          </p>
        </template>
      </template>

      <p v-if="error" class="msg msg--error">{{ error }}</p>

      <!-- ═══════ XEM TRƯỚC ═══════ -->
      <div v-if="xemTruoc" class="preview">
        <p class="preview__sum">
          <strong>{{ xemTruoc.soHopLe }}</strong> lớp sẽ được tạo ·
          <strong>{{ xemTruoc.soDaTonTai }}</strong> bỏ qua ·
          <strong>{{ xemTruoc.soLoi }}</strong> lỗi
        </p>
        <div class="preview__wrap">
          <table>
            <thead>
              <tr>
                <th width="52">Dòng</th>
                <th>Tên lớp</th>
                <th width="70">Khối</th>
                <th width="110">Năm học</th>
                <th width="90">Kết quả</th>
                <th>Ghi chú</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="r in xemTruoc.rows"
                :key="r.dong"
                :class="'row--' + r.trangThai.toLowerCase()"
              >
                <td>{{ r.dong }}</td>
                <td>{{ r.name }}</td>
                <td>{{ r.gradeLevel }}</td>
                <td>{{ r.schoolYear }}</td>
                <td>{{ NHAN_TRANG_THAI[r.trangThai] }}</td>
                <td class="note">{{ r.message }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="modal-actions">
        <button class="btn btn--ghost" @click="emit('close')">Đóng</button>
        <button class="btn btn--ghost" :disabled="busy" @click="layXemTruoc">
          {{ busy ? 'Đang đọc…' : 'Xem trước' }}
        </button>
        <button class="btn" :disabled="busy || !coTheLuu" @click="luu">
          Tạo {{ xemTruoc ? xemTruoc.soHopLe : '' }} lớp
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 60;
  padding: 20px;
}
.modal-box.bulk {
  background: var(--c-surface);
  border-radius: 14px;
  padding: 22px;
  width: min(860px, 100%);
  max-height: 90vh;
  overflow-y: auto;
  border: 1px solid var(--c-border);
}
.modal-title {
  margin: 0 0 14px;
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--c-text);
}
.tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 16px;
}
.tabs button {
  padding: 7px 14px;
  border: 1px solid var(--c-border);
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.88rem;
  color: var(--c-text-muted);
}
.tabs button.on {
  background: var(--c-primary);
  border-color: var(--c-primary);
  color: #fff;
  font-weight: 600;
}
.tabs--sub button {
  font-size: 0.82rem;
  padding: 5px 11px;
}
.grid2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.field--sm {
  max-width: 200px;
}
.field > span {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--c-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}
.field input,
.field select,
.field textarea {
  padding: 0.5rem 0.7rem;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  font-size: 0.9rem;
  background: var(--c-bg);
  color: var(--c-text);
  font-family: inherit;
}
.block {
  margin-bottom: 14px;
}
.block__head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--c-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
  margin-bottom: 8px;
}
.quick {
  display: flex;
  gap: 12px;
}
.link {
  background: none;
  border: none;
  color: var(--c-primary);
  cursor: pointer;
  font-size: 0.76rem;
  text-transform: none;
  letter-spacing: 0;
  padding: 0;
}
.khoi-list {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}
.khoi {
  padding: 6px 13px;
  border: 1px solid var(--c-border);
  background: transparent;
  border-radius: 20px;
  cursor: pointer;
  font-size: 0.85rem;
  color: var(--c-text);
}
.khoi.on {
  background: var(--c-primary);
  border-color: var(--c-primary);
  color: #fff;
  font-weight: 600;
}
.hint {
  font-size: 0.82rem;
  color: var(--c-text-muted);
  margin: 8px 0 0;
  line-height: 1.5;
}
.preview {
  margin-top: 18px;
  border-top: 1px solid var(--c-border);
  padding-top: 14px;
}
.preview__sum {
  font-size: 0.9rem;
  margin: 0 0 10px;
  color: var(--c-text);
}
.preview__wrap {
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid var(--c-border);
  border-radius: 10px;
}
.preview table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
}
.preview th,
.preview td {
  padding: 7px 10px;
  text-align: left;
  border-bottom: 1px solid var(--c-border);
  color: var(--c-text);
}
.preview th {
  position: sticky;
  top: 0;
  background: var(--c-surface);
  font-size: 0.74rem;
  text-transform: uppercase;
  color: var(--c-text-muted);
}
/* Ba trạng thái phải phân biệt được bằng MÀU NỀN chứ không chỉ bằng chữ: bảng 100 dòng thì
   mắt quét màu nhanh hơn đọc cột "Kết quả" từng dòng. */
.row--da_ton_tai td {
  background: color-mix(in srgb, var(--c-warning, #f59e0b) 12%, transparent);
}
.row--loi td {
  background: color-mix(in srgb, var(--c-danger, #ef4444) 12%, transparent);
}
.note {
  color: var(--c-text-muted);
  font-size: 0.8rem;
}
.msg--error {
  color: var(--c-danger, #ef4444);
  font-size: 0.88rem;
  margin: 10px 0 0;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 18px;
}
.btn {
  padding: 0.5rem 1.05rem;
  border-radius: 8px;
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  border: none;
  background: var(--c-primary);
  color: #fff;
}
.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn--ghost {
  background: transparent;
  border: 1px solid var(--c-border);
  color: var(--c-text);
}
@media (max-width: 640px) {
  .grid2 {
    grid-template-columns: 1fr;
  }
}
</style>
