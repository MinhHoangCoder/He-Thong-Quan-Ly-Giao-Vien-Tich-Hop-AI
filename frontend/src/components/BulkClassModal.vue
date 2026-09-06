<script setup>
/**
 * THÊM LỚP HÀNG LOẠT — một bảng nhập, một nút lưu.
 *
 * Vì sao có màn này: mở một trường mới là phải nhập 12-15 lớp, mỗi lớp một lần bấm "Thêm" rồi
 * điền form rồi lưu. Nhân với vài chục trường thì đó là hàng trăm lần thao tác giống hệt nhau.
 *
 * Thân hộp thoại là BẢNG NHIỀU DÒNG (Tên lớp · Khối · Năm học) — gõ thẳng vào đó, hoặc bấm
 * "Nạp từ file" để đổ sẵn một file Excel mẫu 3 cột rồi sửa tiếp tại chỗ. File chỉ là cách điền
 * bảng nhanh hơn, không phải một luồng riêng, nên không có bước "xem trước" tách rời: bảng
 * đang nhìn CHÍNH LÀ bản xem trước, sửa được ngay chứ không phải quay về nguồn nhập.
 *
 * ĐƯỢC ĂN CẢ, NGÃ VỀ KHÔNG. Một dòng trùng là cả lô dừng và không lớp nào được tạo. Trùng ngay
 * trong bảng thì chặn tại chỗ (thấy được thì không cần hỏi server); trùng với lớp đã có ở
 * trường thì chỉ server biết, nên nó trả về câu kể đích danh dòng nào trùng và hiện ở đây.
 */
import { computed, reactive, ref } from 'vue'
import { classApi } from '@/api/classes'
import SearchSelect from '@/components/ui/SearchSelect.vue'

const props = defineProps({
  /** Danh sách trường cho ô chọn. */
  schools: { type: Array, default: () => [] },
  /** Trường đang lọc ở màn danh sách — dùng làm giá trị mặc định. */
  defaultSchoolId: { type: [Number, String], default: '' },
})
const emit = defineEmits(['close', 'created'])

const KHOI = [1, 2, 3, 4, 5, 6, 7, 8, 9]

/** Số dòng trống mở sẵn — đủ để gõ ngay mà không phải bấm "Thêm dòng" trước. */
const SO_DONG_MO_SAN = 3

const busy = ref(false)
const error = ref('')

const form = reactive({
  schoolId: props.defaultSchoolId || '',
  schoolYear: '',
  rows: Array.from({ length: SO_DONG_MO_SAN }, dongTrong),
})

function dongTrong() {
  return { name: '', gradeLevel: '', schoolYear: '' }
}

function themDong() {
  form.rows.push(dongTrong())
}

/** Xóa dòng — luôn chừa lại ít nhất một dòng để bảng không rỗng trơ. */
function xoaDong(i) {
  form.rows.splice(i, 1)
  if (!form.rows.length) form.rows.push(dongTrong())
  error.value = ''
}

/** Dòng có nhập gì đó — dòng để trống hoàn toàn thì bỏ qua, không coi là lỗi. */
function coDuLieu(r) {
  return (r.name || '').trim() !== ''
}

const dongCoDuLieu = computed(() => form.rows.filter(coDuLieu))

/** Khóa trùng = Tên lớp + Năm học (trong cùng một trường đã chọn ở trên). */
function khoaTrung(r) {
  const nam = (r.schoolYear || form.schoolYear || '').trim().toUpperCase()
  return `${(r.name || '').trim().toUpperCase()}|${nam}`
}

/**
 * Các dòng trùng NGAY TRONG BẢNG — chỉ số của dòng thứ hai trở đi mang cùng khóa.
 *
 * Bắt tại chỗ vì đây là lỗi nhìn thấy được ngay trên màn hình: nạp một file có hai dòng 7A1 mà
 * phải bấm Lưu, chờ server, rồi mới biết là một vòng đi thừa.
 */
const trungTrongBang = computed(() => {
  const daGap = new Map()
  const trung = new Set()
  form.rows.forEach((r, i) => {
    if (!coDuLieu(r)) return
    const k = khoaTrung(r)
    if (daGap.has(k)) trung.add(i)
    else daGap.set(k, i)
  })
  return trung
})

const canhBaoTrung = computed(() => {
  if (!trungTrongBang.value.size) return ''
  const so = [...trungTrongBang.value].map((i) => i + 1).join(', ')
  return `Trùng ngay trong bảng: dòng ${so}. Sửa hoặc xóa các dòng đó rồi mới lưu được.`
})

const coTheLuu = computed(
  () => !!form.schoolId && dongCoDuLieu.value.length > 0 && !trungTrongBang.value.size,
)

/** Nạp file Excel/CSV mẫu 3 cột — server đọc và tự bỏ dòng tiêu đề. */
async function onFile(e) {
  const file = e.target.files?.[0]
  // Reset ngay để chọn lại đúng file vừa nạp vẫn kích hoạt được sự kiện change.
  e.target.value = ''
  if (!file) return
  error.value = ''
  busy.value = true
  try {
    const { data } = await classApi.bulkReadFile(file)
    if (!data.length) {
      error.value = 'File không có dòng lớp nào — kiểm tra lại cột Tên lớp.'
      return
    }
    form.rows = data.map((d) => ({
      name: d.name || '',
      gradeLevel: d.gradeLevel || '',
      schoolYear: d.schoolYear || '',
    }))
  } catch (err) {
    error.value = err.response?.data?.message || 'Không đọc được file.'
  } finally {
    busy.value = false
  }
}

async function luu() {
  error.value = ''
  if (!form.schoolId) {
    error.value = 'Vui lòng chọn trường.'
    return
  }
  busy.value = true
  try {
    // Đánh số dòng theo ĐÚNG vị trí trên bảng để câu báo lỗi của server chỉ được đúng dòng
    // người dùng đang nhìn, kể cả khi giữa bảng có dòng trống bị bỏ qua.
    const rows = form.rows
      .map((r, i) => ({ ...r, dong: i + 1 }))
      .filter(coDuLieu)
      .map((r) => ({
        dong: r.dong,
        name: r.name.trim(),
        gradeLevel: r.gradeLevel || null,
        schoolYear: r.schoolYear?.trim() || null,
      }))
    const { data } = await classApi.bulkCreate({
      schoolId: Number(form.schoolId),
      schoolYear: form.schoolYear.trim() || null,
      rows,
    })
    emit('created', data)
  } catch (e) {
    error.value = e.response?.data?.message || 'Không tạo được lớp.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal-box bulk">
      <h3>Thêm lớp hàng loạt</h3>

      <div class="grid2">
        <div class="form-group">
          <label>Trường <span class="req">*</span></label>
          <SearchSelect
            v-model="form.schoolId"
            :options="schools"
            placeholder="Chọn trường"
            search-placeholder="Gõ tên trường…"
            empty-text="Chưa có trường nào"
          />
        </div>
        <div class="form-group">
          <label>Năm học</label>
          <input v-model="form.schoolYear" placeholder="2026-2027" />
        </div>
      </div>

      <div class="bulk__bar">
        <span class="text-muted small">{{ dongCoDuLieu.length }} dòng sẽ được tạo</span>
        <span class="bulk__bar-actions">
          <label class="btn btn-outline btn-sm bulk__file">
            Nạp từ file
            <input type="file" accept=".xlsx,.xls,.csv" :disabled="busy" @change="onFile" />
          </label>
          <button type="button" class="btn btn-outline btn-sm" @click="themDong">Thêm dòng</button>
        </span>
      </div>

      <div class="table-wrap bulk__table">
        <table class="table">
          <thead>
            <tr>
              <th width="46">#</th>
              <th>Tên lớp</th>
              <th width="120">Khối</th>
              <th width="150">Năm học</th>
              <th width="70"></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(r, i) in form.rows"
              :key="i"
              :class="{ 'bulk__row--dup': trungTrongBang.has(i) }"
            >
              <td class="text-muted">{{ i + 1 }}</td>
              <td>
                <input v-model="r.name" placeholder="7A1" maxlength="20" @input="error = ''" />
              </td>
              <td>
                <select v-model="r.gradeLevel">
                  <option value="">Theo tên lớp</option>
                  <option v-for="k in KHOI" :key="k" :value="String(k)">{{ k }}</option>
                </select>
              </td>
              <td>
                <input
                  v-model="r.schoolYear"
                  :placeholder="form.schoolYear || '2026-2027'"
                  maxlength="20"
                />
              </td>
              <td>
                <button
                  type="button"
                  class="btn btn-outline btn-sm"
                  title="Xóa dòng"
                  @click="xoaDong(i)"
                >
                  Xóa
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <p v-if="canhBaoTrung" class="error-msg">{{ canhBaoTrung }}</p>
      <p v-if="error" class="error-msg">{{ error }}</p>

      <div class="modal-actions">
        <button class="btn btn-outline" :disabled="busy" @click="emit('close')">Đóng</button>
        <button class="btn btn-primary" :disabled="busy || !coTheLuu" @click="luu">
          {{ busy ? 'Đang lưu…' : `Tạo ${dongCoDuLieu.length} lớp` }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Khung hộp thoại, nút, bảng và ô nhập đều lấy từ assets/page-common.css — ở đây chỉ còn
   phần riêng của bảng nhập nhiều dòng. */
.bulk {
  max-width: 760px;
}
.bulk__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  margin: 0.4rem 0 0.6rem;
}
.bulk__bar-actions {
  display: flex;
  gap: 0.5rem;
}
/* Ô chọn file mặc định của trình duyệt trông lạc lõng giữa các nút — bọc trong <label> rồi
   giấu input đi để nó dùng đúng kiểu nút của hệ thống. */
.bulk__file {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
}
.bulk__file input {
  display: none;
}
.bulk__table {
  max-height: 46vh;
  overflow-y: auto;
}
.bulk__table td {
  padding: 0.35rem 0.5rem;
}
.bulk__table input,
.bulk__table select {
  width: 100%;
  padding: 0.35rem 0.5rem;
  border: 1px solid var(--c-border);
  border-radius: 6px;
  font-size: 0.88rem;
  box-sizing: border-box;
  background: var(--c-surface);
  color: var(--c-text);
}
.bulk__table input:focus,
.bulk__table select:focus {
  outline: none;
  border-color: var(--c-primary);
}
/* Dòng trùng phải nhận ra bằng MÀU chứ không chỉ bằng câu chữ bên dưới: bảng 40 dòng thì mắt
   quét màu nhanh hơn đọc danh sách số dòng rồi đếm ngược lên. */
.bulk__row--dup td {
  background: color-mix(in srgb, var(--c-danger) 12%, transparent);
}
.req {
  color: var(--c-danger);
}
</style>
