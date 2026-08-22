<script setup>
/**
 * HỆ THỐNG — hai câu hỏi mà trước đây phần mềm không trả lời được.
 *
 * Tab "Nhật ký thao tác" trả lời: **"cái này ai xóa, lúc nào?"**
 *   Các bảng đều có DeletedBy/DeletedAt, nhưng chúng chỉ giữ LẦN CUỐI và chỉ cho thao tác xóa.
 *   Khôi phục rồi xóa lại thì dấu vết cũ mất; còn những việc không-xóa-nhưng-nguy-hiểm (mở lại
 *   kỳ lương đã chốt, đổi đơn giá tiết dạy) thì không để lại gì cả.
 *
 * Tab "Dữ liệu mồ côi" trả lời: **"xóa xong thì phần dữ liệu con đi đâu?"**
 *   Mồ côi = dòng CON đang sống nhưng trỏ vào dòng CHA đã bị xóa mềm. Chúng vô hình vì không
 *   câu query nghiệp vụ nào lọc theo cờ IsDeleted của bảng CHA — một lớp học trỏ vào trường đã
 *   xóa vẫn hiện đầy đủ ở mọi màn hình, chỉ là trỏ vào một cái tên đã biến mất.
 *
 * Cả hai tab đều CHỈ ĐỌC. Nhật ký mà sửa được thì không còn là nhật ký; còn phần mồ côi thì mỗi
 * cặp có hai cách xử lý trái ngược nhau và chỉ con người mới chọn được — một trường bị xóa nhầm
 * mà còn 12 lớp đang học thì việc đúng là KHÔI PHỤC trường, không phải xóa nốt 12 lớp.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { auditApi, orphanApi } from '@/api/system'
import { PAGE_SIZE } from '@/utils/pagination'
import FilterBar from '@/components/ui/FilterBar.vue'
import Pagination from '@/components/ui/Pagination.vue'
import DateField from '@/components/ui/DateField.vue'
import { formatDateTime } from '@/utils/format'

const tab = ref('nhatky') // 'nhatky' | 'mocoi'

/* ══════════════ Nhật ký thao tác ══════════════ */
const loading = ref(false)
const error = ref('')
const rows = ref([])
const total = ref(0)
const page = ref(0)
const filter = reactive({ action: '', entity: '', from: '', to: '', keyword: '' })
const options = reactive({ actions: [], entities: [] })

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

async function load(giuTrang = false) {
  loading.value = true
  error.value = ''
  if (!giuTrang) page.value = 0
  try {
    const { data } = await auditApi.list({
      action: filter.action || undefined,
      entity: filter.entity || undefined,
      from: filter.from || undefined,
      to: filter.to || undefined,
      page: page.value,
      size: PAGE_SIZE,
    })
    rows.value = data.content ?? []
    total.value = data.totalElements ?? 0
  } catch (e) {
    error.value = e.response?.data?.message || 'Không tải được nhật ký.'
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function goPage(p) {
  page.value = p
  load(true)
}

function clearFilter() {
  filter.action = ''
  filter.entity = ''
  filter.from = ''
  filter.to = ''
  load()
}

/* ══════════════ Dữ liệu mồ côi ══════════════ */
const scanning = ref(false)
const scan = ref(null)
const scanError = ref('')

async function quet() {
  scanning.value = true
  scanError.value = ''
  try {
    const { data } = await orphanApi.scan()
    scan.value = data
  } catch (e) {
    scanError.value = e.response?.data?.message || 'Không chạy được rà soát.'
  } finally {
    scanning.value = false
  }
}

function doiTab(t) {
  tab.value = t
  if (t === 'mocoi' && !scan.value) quet()
}

onMounted(async () => {
  await load()
  try {
    const { data } = await auditApi.filterOptions()
    options.actions = data.actions ?? []
    options.entities = data.entities ?? []
  } catch {
    /* thiếu ô lọc không chặn việc xem nhật ký */
  }
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h2 class="title">Hệ thống</h2>
    </div>

    <div class="tabs">
      <button :class="{ on: tab === 'nhatky' }" @click="doiTab('nhatky')">Nhật ký thao tác</button>
      <button :class="{ on: tab === 'mocoi' }" @click="doiTab('mocoi')">Dữ liệu mồ côi</button>
    </div>

    <!-- ══════════ NHẬT KÝ ══════════ -->
    <template v-if="tab === 'nhatky'">
      <FilterBar
        v-model="filter.keyword"
        placeholder="(dùng các ô lọc bên trái)"
        aria-label="Lọc nhật ký"
        :debounce="0"
        @apply="load"
        @clear="clearFilter"
      >
        <label class="field">
          <span>Thao tác</span>
          <select v-model="filter.action" @change="load">
            <option value="">Tất cả</option>
            <option v-for="a in options.actions" :key="a.code" :value="a.code">{{ a.label }}</option>
          </select>
        </label>
        <label class="field">
          <span>Bảng dữ liệu</span>
          <select v-model="filter.entity" @change="load">
            <option value="">Tất cả</option>
            <option v-for="e in options.entities" :key="e" :value="e">{{ e }}</option>
          </select>
        </label>
        <label class="field">
          <span>Từ ngày</span>
          <DateField v-model="filter.from" @update:model-value="load" />
        </label>
        <label class="field">
          <span>Đến ngày</span>
          <DateField v-model="filter.to" @update:model-value="load" />
        </label>
      </FilterBar>

      <p v-if="error" class="msg msg--error">{{ error }}</p>
      <p v-if="!loading" class="total">
        Tổng cộng <strong>{{ total }}</strong> thao tác
      </p>

      <p v-if="loading" class="empty-state">Đang tải…</p>
      <p v-else-if="!rows.length" class="empty-state">
        Chưa có thao tác nào được ghi. Nhật ký chỉ ghi các việc NGUY HIỂM (xóa, hủy phân công,
        chốt / mở lại lương, đổi đơn giá) — thao tác xem không được ghi.
      </p>

      <div v-else class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th width="150">Thời điểm</th>
              <th width="130">Người thực hiện</th>
              <th width="180">Thao tác</th>
              <th width="110">Bản ghi</th>
              <th>Chi tiết</th>
              <th width="120">Địa chỉ IP</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="r.id">
              <td class="mono small">{{ formatDateTime(r.createdAt) }}</td>
              <td>{{ r.actorName }}</td>
              <td>
                <span class="act">{{ r.actionLabel }}</span>
              </td>
              <td class="mono small">{{ r.entity }}<template v-if="r.entityId"> #{{ r.entityId }}</template></td>
              <td class="small">
                <div v-if="r.oldValue" class="val val--old">{{ r.oldValue }}</div>
                <div v-if="r.newValue" class="val">{{ r.newValue }}</div>
              </td>
              <td class="mono small text-muted">{{ r.ipAddress ?? '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <Pagination v-if="totalPages > 1" :model-value="page" :total-pages="totalPages" @update:model-value="goPage" />
    </template>

    <!-- ══════════ DỮ LIỆU MỒ CÔI ══════════ -->
    <template v-else>
      <div class="toolbar">
        <button class="btn btn-primary btn-sm" :disabled="scanning" @click="quet">
          {{ scanning ? 'Đang quét…' : 'Quét lại' }}
        </button>
        <span v-if="scan?.quetLuc" class="text-muted small">
          Quét lúc {{ formatDateTime(scan.quetLuc) }}
        </span>
      </div>

      <p v-if="scanError" class="msg msg--error">{{ scanError }}</p>

      <template v-if="scan">
        <div class="sum" :class="{ 'sum--ok': scan.tongSoDongMoCoi === 0 }">
          <strong v-if="scan.tongSoDongMoCoi === 0">Không có dòng mồ côi nào cần xử lý.</strong>
          <strong v-else>{{ scan.tongSoDongMoCoi }} dòng đang trỏ vào bản ghi cha đã bị xóa.</strong>
          <span v-if="scan.chenhLechSoVoiLanTruoc != null" class="delta">
            <template v-if="scan.chenhLechSoVoiLanTruoc > 0">
              Tăng {{ scan.chenhLechSoVoiLanTruoc }} so với lần quét trước — nghĩa là còn một
              đường sinh mồ côi lọt qua chốt chặn.
            </template>
            <template v-else-if="scan.chenhLechSoVoiLanTruoc < 0">
              Giảm {{ -scan.chenhLechSoVoiLanTruoc }} so với lần quét trước.
            </template>
            <template v-else>Không đổi so với lần quét trước.</template>
          </span>
        </div>

        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th>Bảng cha (đã xóa)</th>
                <th>Bảng con (còn sống)</th>
                <th width="150">Khóa ngoại</th>
                <th width="90">Số dòng</th>
                <th>Ghi chú</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="c in scan.cacCap" :key="c.bangCha + c.bangCon" :class="{ vohai: c.voHai }">
                <td class="mono">{{ c.bangCha }}</td>
                <td class="mono">{{ c.bangCon }}</td>
                <td class="mono small">{{ c.cotKhoaNgoai }}</td>
                <td class="num">
                  {{ c.soDong }}
                  <span v-if="c.voHai" class="tag">vô hại</span>
                </td>
                <td class="small text-muted">{{ c.giaiThich ?? '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <p class="note">
          Màn này <strong>chỉ báo cáo, không có nút dọn</strong>. Mỗi cặp mồ côi có hai cách xử lý
          trái ngược nhau và chỉ con người mới chọn được: một trường bị xóa nhầm mà còn 12 lớp
          đang học thì việc đúng là <em>khôi phục trường</em>, không phải xóa nốt 12 lớp.
        </p>
      </template>
    </template>
  </div>
</template>

<style scoped>
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
.total {
  font-size: 0.86rem;
  color: var(--c-text-muted);
  margin: 0 0 10px;
}
.empty-state {
  padding: 28px 20px;
  text-align: center;
  color: var(--c-text-muted);
  font-size: 0.9rem;
  line-height: 1.6;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 14px;
}
.mono {
  font-family: ui-monospace, monospace;
}
.small {
  font-size: 0.82rem;
}
.num {
  text-align: right;
  font-family: ui-monospace, monospace;
}
.act {
  font-weight: 600;
}
.val {
  line-height: 1.45;
}
/* Giá trị TRƯỚC khi đổi mờ hơn để mắt bám vào giá trị sau. */
.val--old {
  color: var(--c-text-muted);
  text-decoration: line-through;
}
.msg--error {
  color: var(--c-danger, #ef4444);
  font-size: 0.88rem;
}
.sum {
  padding: 13px 16px;
  border-radius: 12px;
  margin-bottom: 14px;
  border-left: 4px solid var(--c-danger, #ef4444);
  background: color-mix(in srgb, var(--c-danger, #ef4444) 9%, transparent);
  font-size: 0.92rem;
  color: var(--c-text);
}
.sum--ok {
  border-left-color: #22c55e;
  background: rgba(34, 197, 94, 0.1);
}
.delta {
  display: block;
  margin-top: 5px;
  font-size: 0.85rem;
  color: var(--c-text-muted);
}
/* Cặp lành tính làm mờ đi — vẫn kể ra để không ai tưởng hệ thống giấu số. */
.vohai td {
  opacity: 0.6;
}
.tag {
  font-size: 0.68rem;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  padding: 1px 6px;
  border-radius: 10px;
  background: var(--c-border);
  color: var(--c-text-muted);
  margin-left: 6px;
}
.note {
  margin-top: 14px;
  font-size: 0.85rem;
  line-height: 1.6;
  color: var(--c-text-muted);
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
</style>
