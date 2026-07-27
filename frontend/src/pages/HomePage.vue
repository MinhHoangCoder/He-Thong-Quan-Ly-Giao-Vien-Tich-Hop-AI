<script setup>
// Trang chủ quảng cáo (chưa đăng nhập) — làm lại 2026-07-17 theo pattern
// "Operations Landing" (skill ui-ux-pro-max): hero PHẢI cho xem sản phẩm thật
// (khung xem trước lịch dạy vẽ bằng CSS, dữ liệu đúng seed demo), số liệu là
// sự thật của hệ thống, tiêu đề section căn TRÁI kèm kicker — tránh kiểu
// "mọi thứ căn giữa + khối gradient" đặc trưng của trang AI sinh sẵn.
// Khối "Tính năng chính" giữ kiểu MASTER–DETAIL (ý tưởng nhóm trưởng):
// bấm 1 tính năng ở cột trái -> panel phải hiện chi tiết.
// Nội dung chi tiết lấy từ các phân hệ CÓ THẬT trong hệ thống, không viết chung chung.
import { computed, ref } from 'vue'
import SvgIcon from '@/components/ui/SvgIcon.vue'

const features = [
  {
    icon: 'teacher',
    title: 'Quản lý giáo viên',
    tagline: 'Hồ sơ · chứng chỉ · hợp đồng',
    desc: 'Toàn bộ hồ sơ giáo viên của trung tâm nằm ở một chỗ, thay cho file Excel rời rạc từng phòng ban.',
    points: [
      'Hồ sơ đầy đủ: CCCD, liên hệ, ngày vào làm — có kiểm tra định dạng chuẩn Việt Nam',
      'Quản lý bằng cấp & chứng chỉ kèm ngày hết hạn',
      'Hợp đồng lao động gắn 1-1 với giáo viên: lương cơ bản, phụ cấp, thời hạn',
      'Xóa mềm vào thùng rác, khôi phục được — không mất dữ liệu lịch sử',
    ],
    roles: ['Quản trị', 'Nhân viên trung tâm'],
  },
  {
    icon: 'schedule',
    title: 'Phân công & lịch dạy',
    tagline: 'Gán GV ↔ trường ↔ lớp ↔ tiết',
    desc: 'Trung tâm phân công giáo viên tới từng trường theo tiết học lặp hằng tuần — hệ thống tự sinh lịch dạy cho cả giai đoạn.',
    points: [
      'Tạo phân công theo luồng: chọn Môn → Trường → Lớp → Thứ/Tiết → Giáo viên',
      'Tự động dò trùng lịch: một giáo viên không thể dạy 2 nơi cùng khung giờ',
      'Trải phân công thành từng buổi dạy cụ thể theo tuần',
      'Xem dạng lịch tháng hoặc thời khóa biểu tuần, lọc theo giáo viên / lớp',
    ],
    roles: ['Quản trị', 'Nhân viên trung tâm', 'Giáo viên'],
  },
  {
    icon: 'payroll',
    title: 'Chấm công & bảng lương',
    tagline: 'Từ buổi dạy tới tiền lương',
    desc: 'Một đường dữ liệu liền mạch: buổi dạy đã duyệt → chấm công → bảng lương, không phải nhập tay lại ở bất kỳ bước nào.',
    points: [
      'Sinh chấm công hàng loạt từ các buổi dạy đã duyệt trong khoảng ngày',
      'Ghi giờ vào / giờ ra từng buổi, tự tính giờ dạy',
      'Tính lương theo tiết, đơn giá phân theo cấp học (Tiểu học / THCS)',
      'Quy trình chốt lương: Nháp → Đã chốt — đã chốt thì không sửa được nữa',
    ],
    roles: ['Quản trị', 'Nhân viên trung tâm'],
  },
  {
    icon: 'subject',
    title: 'Kho bài giảng',
    tagline: 'Học liệu dùng chung',
    desc: 'Kho học liệu tập trung của trung tâm, phân loại theo môn học, nhóm môn và khối lớp.',
    points: [
      'Bài giảng gắn với môn học / nhóm môn / khối lớp, tìm kiếm + phân trang',
      'Đính kèm tài liệu PDF hoặc liên kết thiết kế Canva',
      'Giáo viên chỉ thấy bài đã xuất bản — bản nháp là việc nội bộ của trung tâm',
      'Tải tài liệu qua kênh có kiểm tra quyền, không lộ link công khai',
    ],
    roles: ['Quản trị', 'Nhân viên trung tâm', 'Giáo viên'],
  },
  {
    icon: 'shield',
    title: 'Bảo mật & phân quyền',
    tagline: 'RBAC tới từng nút bấm',
    desc: 'Bảo mật xây từ gốc chứ không gắn thêm sau: mỗi vai trò chỉ thấy và làm được đúng phần việc của mình.',
    points: [
      'Phân quyền RBAC ~30 quyền chi tiết theo phòng ban',
      'Đăng nhập JWT xoay vòng, tự phát hiện token bị đánh cắp',
      'Quản lý phiên đăng nhập từng thiết bị, thu hồi từ xa',
      'Giao diện sáng / tối, cỡ chữ lớn, giảm chuyển động — tùy chọn theo người dùng',
    ],
    roles: ['Mọi vai trò'],
  },
]

// ref = ô nhớ phản ứng: đổi selected là panel chi tiết tự vẽ lại theo.
const selected = ref(0)
const current = computed(() => features[selected.value])

// Khung xem trước trên hero: thời khóa biểu tuần của GV — dữ liệu ĐÚNG seed demo
// (môn Scratch, lớp 10A1, Trường THPT Demo) để khớp với những gì demo thật sẽ chiếu.
const previewRows = [
  { day: 'Thứ 2', period: 'Tiết 1–2', label: 'Scratch · Lớp 10A1', status: 'Đã duyệt', ok: true },
  { day: 'Thứ 4', period: 'Tiết 3–4', label: 'Scratch · Lớp 10A1', status: 'Đã duyệt', ok: true },
  { day: 'Thứ 6', period: 'Tiết 7–8', label: 'Scratch · Lớp 10A1', status: 'Chờ duyệt', ok: false },
]

// Dải số liệu: SỰ THẬT của hệ thống (đếm được trong code/DB), không phải con số quảng cáo.
const metrics = [
  { value: '3', label: 'cổng làm việc theo vai trò' },
  { value: '30', label: 'quyền chi tiết (RBAC)' },
  { value: '5', label: 'bước từ phân công tới lương' },
  { value: '0', label: 'lần nhập tay lại số liệu' },
]

// Quy trình vận hành khép kín — đúng đường đi dữ liệu trong hệ thống.
const workflow = [
  { title: 'Phân công', desc: 'Trung tâm gán giáo viên ↔ trường ↔ lớp theo tiết hằng tuần.' },
  { title: 'Sinh lịch dạy', desc: 'Hệ thống tự trải thành từng buổi dạy cho cả giai đoạn.' },
  { title: 'Dạy & chấm công', desc: 'Điểm danh theo buổi, ghi giờ vào / giờ ra.' },
  { title: 'Tính lương', desc: 'Gom tiết theo kỳ, đơn giá theo cấp học, chốt bảng lương.' },
  { title: 'Theo dõi', desc: 'Dashboard số liệu thật + thông báo trong hệ thống.' },
]

// Cổng làm việc theo vai trò — mỗi vai trò một portal riêng đã có trong hệ thống.
// Đợt bảo vệ TẠM bỏ "Trường liên kết" (nhà trường chưa tham dự hệ thống) — code
// khu School/SchoolLayout vẫn còn, muốn hiện lại chỉ cần thêm object school vào đây.
const portals = [
  {
    icon: 'settings',
    title: 'Quản trị viên',
    items: [
      'Dashboard vận hành toàn trung tâm',
      'Quản lý tài khoản & phân quyền',
      'Toàn bộ nghiệp vụ điều phối',
    ],
  },
  {
    icon: 'assignment',
    title: 'Nhân viên trung tâm',
    items: [
      'Phân công & xếp lịch dạy',
      'Chấm công, tính lương',
      'Quản lý hồ sơ GV & kho bài giảng',
    ],
  },
  {
    icon: 'teacher',
    title: 'Giáo viên',
    items: [
      'Lịch dạy cá nhân theo tuần',
      'Kho bài giảng đã xuất bản',
      'Hồ sơ, phiếu lương, đánh giá',
    ],
  },
]
</script>

<template>
  <!-- Hero: chữ bên trái + KHUNG SẢN PHẨM THẬT bên phải -->
  <section class="hero">
    <div class="container hero__inner">
      <div class="hero__copy">
        <h1 class="hero__title">Điều phối giáo viên, lịch dạy và lương trong một hệ thống</h1>
        <p class="hero__desc">
          Trung tâm phân công một lần — lịch dạy, chấm công và bảng lương tự nối tiếp nhau, không
          phải nhập tay lại ở bất kỳ bước nào.
        </p>
        <div class="hero__actions">
          <RouterLink to="/dashboard" class="btn btn--primary btn--lg">Vào hệ thống</RouterLink>
          <a href="#features" class="btn btn--ghost btn--lg">Xem tính năng</a>
        </div>
      </div>

      <!-- Khung xem trước: thời khóa biểu tuần đúng như trong app (dữ liệu seed demo) -->
      <div class="hero__preview" aria-hidden="true">
        <div class="win">
          <div class="win__bar">
            <span class="win__dots"><i></i><i></i><i></i></span>
            <span class="win__title">Lịch dạy tuần · Giáo viên Demo</span>
          </div>
          <div class="win__body">
            <div class="win__meta">
              <span class="win__school"><SvgIcon name="school" :size="14" /> Trường THPT Demo</span>
              <span class="win__week">Tuần này</span>
            </div>
            <ul class="win__rows">
              <li v-for="r in previewRows" :key="r.day + r.period" class="win__row">
                <span class="win__day">
                  <strong>{{ r.day }}</strong>
                  <small>{{ r.period }}</small>
                </span>
                <span class="win__label">{{ r.label }}</span>
                <span class="win__status" :class="r.ok ? 'is-ok' : 'is-wait'">{{ r.status }}</span>
              </li>
            </ul>
            <div class="win__foot">
              <span><strong>3</strong> buổi trong tuần</span>
              <span><strong>6</strong> tiết · Scratch</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>

  <!-- Dải số liệu: sự thật đếm được của hệ thống -->
  <section class="metrics">
    <div class="container metrics__inner">
      <div v-for="m in metrics" :key="m.label" class="metric">
        <span class="metric__value">{{ m.value }}</span>
        <span class="metric__label">{{ m.label }}</span>
      </div>
    </div>
  </section>

  <!-- Tính năng chính: bấm bên trái -> chi tiết bên phải -->
  <section id="features" class="section">
    <div class="container">
      <header class="section-head">
        <p class="kicker">Phân hệ</p>
        <h2 class="section-title">Hệ thống làm được gì?</h2>
        <p class="section-sub">Chọn một phân hệ để xem khả năng cụ thể và vai trò sử dụng.</p>
      </header>

      <div class="feat">
        <div class="feat__list" role="tablist" aria-label="Tính năng chính">
          <button
            v-for="(f, i) in features"
            :key="f.title"
            class="feat__item"
            :class="{ 'is-active': i === selected }"
            role="tab"
            :aria-selected="i === selected"
            @click="selected = i"
          >
            <span class="feat__item-icon"><SvgIcon :name="f.icon" :size="19" /></span>
            <span class="feat__item-text">
              <strong>{{ f.title }}</strong>
              <small>{{ f.tagline }}</small>
            </span>
          </button>
        </div>

        <Transition name="detail" mode="out-in">
          <article :key="current.title" class="feat__detail" role="tabpanel">
            <div class="feat__detail-head">
              <span class="feat__detail-icon"><SvgIcon :name="current.icon" :size="24" /></span>
              <h3>{{ current.title }}</h3>
            </div>
            <p class="feat__detail-desc">{{ current.desc }}</p>
            <ul class="feat__points">
              <li v-for="p in current.points" :key="p">
                <SvgIcon name="check" :size="15" />
                <span>{{ p }}</span>
              </li>
            </ul>
            <div class="feat__roles">
              <span class="feat__roles-label">Dành cho:</span>
              <span v-for="r in current.roles" :key="r" class="role-chip">{{ r }}</span>
            </div>
          </article>
        </Transition>
      </div>
    </div>
  </section>

  <!-- Quy trình vận hành -->
  <section id="workflow" class="section section--alt">
    <div class="container">
      <header class="section-head">
        <p class="kicker">Quy trình</p>
        <h2 class="section-title">Dữ liệu chảy một đường, không nhập tay lại</h2>
        <p class="section-sub">
          Từ phân công tới bảng lương — mỗi bước dùng kết quả của bước trước.
        </p>
      </header>
      <ol class="flow">
        <li v-for="(s, i) in workflow" :key="s.title" class="flow__step">
          <span class="flow__num">{{ i + 1 }}</span>
          <h3 class="flow__title">{{ s.title }}</h3>
          <p class="flow__desc">{{ s.desc }}</p>
        </li>
      </ol>
    </div>
  </section>

  <!-- Dành cho từng vai trò -->
  <section id="roles" class="section">
    <div class="container">
      <header class="section-head">
        <p class="kicker">Cổng làm việc</p>
        <h2 class="section-title">Mỗi vai trò một không gian riêng</h2>
        <p class="section-sub">
          Đăng nhập là vào thẳng đúng cổng của mình, thấy đúng việc của mình.
        </p>
      </header>
      <div class="portals">
        <article v-for="p in portals" :key="p.title" class="portal">
          <span class="portal__icon"><SvgIcon :name="p.icon" :size="22" /></span>
          <h3 class="portal__title">{{ p.title }}</h3>
          <ul class="portal__list">
            <li v-for="it in p.items" :key="it">{{ it }}</li>
          </ul>
        </article>
      </div>
    </div>
  </section>

  <!-- CTA cuối trang: dải lặng, không khối gradient -->
  <section class="cta">
    <div class="container">
      <div class="cta__inner">
        <div>
          <h2 class="cta__title">Dùng thử ngay</h2>
          <p class="cta__desc">
            Trang đăng nhập có sẵn tài khoản demo cho từng vai trò — bấm một nút là vào đúng cổng
            làm việc.
          </p>
        </div>
        <RouterLink to="/dashboard" class="btn btn--primary btn--lg">Vào hệ thống</RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
/* ===== Hero ===== */
.hero {
  background: var(--grad-hero);
  color: #fff;
  padding: 4rem 0;
}
.hero__inner {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  gap: 3rem;
  align-items: center;
}
.hero__title {
  font-size: 2.35rem;
  line-height: 1.18;
  margin: 0 0 1rem;
  letter-spacing: -0.02em;
}
.hero__desc {
  font-size: 1.05rem;
  color: rgba(255, 255, 255, 0.85);
  margin: 0 0 2rem;
  line-height: 1.6;
  max-width: 46ch;
}
.hero__actions {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

/* Khung xem trước sản phẩm — cửa sổ app vẽ bằng CSS, nội dung khớp seed demo.
   Màu CỐ TÌNH theo giao diện SÁNG của app (nền trắng) kể cả khi trang ở dark mode:
   nó là "ảnh chụp sản phẩm", không phải bề mặt của landing page. */
.win {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 24px 60px rgba(4, 18, 38, 0.45);
  overflow: hidden;
  transform: rotate(0.6deg);
}
.win__bar {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.6rem 0.9rem;
  background: #f1f5f9;
  border-bottom: 1px solid #e2e8f0;
}
.win__dots {
  display: inline-flex;
  gap: 5px;
}
.win__dots i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #cbd5e1;
}
.win__title {
  font-size: 0.78rem;
  font-weight: 600;
  color: #475569;
}
.win__body {
  padding: 0.9rem 1rem 1rem;
}
.win__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.6rem;
}
.win__school {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.8rem;
  font-weight: 600;
  color: #0f2850;
}
.win__school svg {
  color: #2563eb;
}
.win__week {
  font-size: 0.72rem;
  font-weight: 600;
  color: #f97316;
  background: rgba(249, 115, 22, 0.1);
  border-radius: 9999px;
  padding: 0.15rem 0.6rem;
}
.win__rows {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}
.win__row {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  padding: 0.55rem 0.2rem;
  border-bottom: 1px solid #eef2f7;
}
.win__row:last-child {
  border-bottom: none;
}
.win__day {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  min-width: 58px;
}
.win__day strong {
  font-size: 0.8rem;
  color: #0f2850;
}
.win__day small {
  font-size: 0.7rem;
  color: #64748b;
}
.win__label {
  flex: 1;
  font-size: 0.84rem;
  font-weight: 500;
  color: #1e293b;
}
.win__status {
  font-size: 0.7rem;
  font-weight: 700;
  border-radius: 9999px;
  padding: 0.14rem 0.55rem;
  white-space: nowrap;
}
.win__status.is-ok {
  color: #15803d;
  background: rgba(34, 197, 94, 0.13);
}
.win__status.is-wait {
  color: #92400e;
  background: rgba(245, 158, 11, 0.16);
}
.win__foot {
  display: flex;
  gap: 1.2rem;
  margin-top: 0.7rem;
  padding-top: 0.7rem;
  border-top: 1px dashed #e2e8f0;
  font-size: 0.76rem;
  color: #64748b;
}
.win__foot strong {
  color: #0f2850;
}

/* ===== Dải số liệu ===== */
.metrics {
  background: var(--c-surface);
  border-bottom: 1px solid var(--c-border-soft);
}
.metrics__inner {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
  padding-top: 1.4rem;
  padding-bottom: 1.4rem;
}
.metric {
  display: flex;
  align-items: baseline;
  gap: 0.55rem;
}
.metric__value {
  font-size: 1.7rem;
  font-weight: 800;
  color: var(--c-primary);
  letter-spacing: -0.02em;
  /* số dùng chữ số cùng bề rộng để không xô lệch giữa các ô */
  font-variant-numeric: tabular-nums;
}
.metric__label {
  font-size: 0.85rem;
  color: var(--c-text-muted);
  line-height: 1.3;
}

/* ===== Khung section chung: tiêu đề căn TRÁI + kicker ===== */
.section {
  padding: 4rem 0;
  /* header dính 64px — cuộn tới anchor không bị che tiêu đề */
  scroll-margin-top: 76px;
}
.section--alt {
  background: var(--c-surface);
  border-top: 1px solid var(--c-border-soft);
  border-bottom: 1px solid var(--c-border-soft);
}
.section-head {
  max-width: 560px;
  margin-bottom: 2.2rem;
}
.kicker {
  margin: 0 0 0.4rem;
  font-size: 0.76rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--c-primary);
}
.section-title {
  font-size: 1.7rem;
  margin: 0 0 0.5rem;
  color: var(--c-text);
  letter-spacing: -0.01em;
}
.section-sub {
  color: var(--c-text-muted);
  margin: 0;
  line-height: 1.55;
}

/* ===== Tính năng: master–detail ===== */
.feat {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 1.25rem;
  align-items: start;
}
.feat__list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.feat__item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
  text-align: left;
  padding: 0.75rem 0.9rem;
  border: 1px solid var(--c-border);
  border-radius: 12px;
  background: var(--c-surface);
  cursor: pointer;
  font: inherit;
  color: var(--c-text);
  transition:
    border-color var(--t-fast),
    background var(--t-fast);
}
.feat__item:hover {
  border-color: var(--c-primary-light);
}
.feat__item.is-active {
  border-color: var(--c-primary);
  background: rgba(249, 115, 22, 0.07);
}
.feat__item-icon {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: var(--c-surface-2);
  color: var(--c-text-muted);
  transition:
    background var(--t-fast),
    color var(--t-fast);
}
.feat__item.is-active .feat__item-icon {
  background: var(--grad-primary);
  color: #fff;
}
.feat__item-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
  min-width: 0;
}
.feat__item-text strong {
  font-size: 0.93rem;
}
.feat__item-text small {
  font-size: 0.76rem;
  color: var(--c-text-muted);
}

.feat__detail {
  border: 1px solid var(--c-border);
  border-radius: 16px;
  background: var(--c-surface);
  padding: 1.6rem 1.8rem;
  box-shadow: var(--a-shadow);
  min-height: 100%;
}
.feat__detail-head {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  margin-bottom: 0.7rem;
}
.feat__detail-head h3 {
  margin: 0;
  font-size: 1.3rem;
  color: var(--c-text);
}
.feat__detail-icon {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 12px;
  background: var(--grad-primary);
  color: #fff;
  flex: 0 0 auto;
}
.feat__detail-desc {
  margin: 0 0 1.1rem;
  color: var(--c-text-muted);
  line-height: 1.6;
}
.feat__points {
  list-style: none;
  margin: 0 0 1.2rem;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}
.feat__points li {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  font-size: 0.93rem;
  line-height: 1.45;
}
.feat__points svg {
  flex: 0 0 auto;
  margin-top: 3px;
  color: var(--c-primary);
}
.feat__roles {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.4rem;
  border-top: 1px dashed var(--c-border);
  padding-top: 0.9rem;
}
.feat__roles-label {
  font-size: 0.8rem;
  color: var(--c-text-muted);
}
.role-chip {
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--c-text);
  background: var(--c-surface-2);
  border: 1px solid var(--c-border);
  border-radius: 9999px;
  padding: 0.14rem 0.6rem;
}
/* panel chi tiết mờ + trượt nhẹ khi đổi tính năng */
.detail-enter-active,
.detail-leave-active {
  transition:
    opacity var(--t),
    transform var(--t);
}
.detail-enter-from,
.detail-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

/* ===== Quy trình ===== */
.flow {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 1rem;
  counter-reset: step;
}
.flow__step {
  position: relative;
  padding: 0 0.4rem;
}
/* vạch nối giữa các bước (ẩn ở bước cuối) */
.flow__step:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 19px;
  left: 52px;
  width: calc(100% - 62px);
  height: 2px;
  background: var(--c-border);
}
.flow__num {
  display: inline-grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: var(--grad-primary);
  color: #fff;
  font-weight: 700;
  box-shadow: 0 6px 14px rgba(249, 115, 22, 0.3);
}
.flow__title {
  margin: 0.7rem 0 0.3rem;
  font-size: 1rem;
  color: var(--c-text);
}
.flow__desc {
  margin: 0;
  font-size: 0.85rem;
  color: var(--c-text-muted);
  line-height: 1.5;
}

/* ===== Vai trò ===== */
.portals {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 1.25rem;
}
.portal {
  border: 1px solid var(--c-border);
  border-radius: 14px;
  background: var(--c-surface);
  padding: 1.4rem;
  transition:
    box-shadow var(--t),
    border-color var(--t);
}
.portal:hover {
  box-shadow: var(--a-shadow-lg);
  border-color: rgba(249, 115, 22, 0.35);
}
.portal__icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: rgba(37, 99, 235, 0.1);
  color: var(--c-accent);
}
.portal__title {
  margin: 0.8rem 0 0.5rem;
  font-size: 1.05rem;
  color: var(--c-text);
}
.portal__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.portal__list li {
  font-size: 0.88rem;
  color: var(--c-text-muted);
  padding-left: 1rem;
  position: relative;
  line-height: 1.45;
}
.portal__list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.55em;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--c-primary);
}

/* ===== CTA cuối trang: dải lặng trên nền thường ===== */
.cta {
  padding: 0 0 4rem;
}
.cta__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1.5rem;
  flex-wrap: wrap;
  border: 1px solid var(--c-border);
  border-left: 4px solid var(--c-primary);
  border-radius: 14px;
  background: var(--c-surface);
  padding: 1.8rem 2rem;
}
.cta__title {
  margin: 0 0 0.3rem;
  font-size: 1.35rem;
  color: var(--c-text);
}
.cta__desc {
  margin: 0;
  color: var(--c-text-muted);
  max-width: 52ch;
  line-height: 1.55;
}

/* ===== Responsive ===== */
@media (max-width: 900px) {
  .hero__inner {
    grid-template-columns: 1fr;
    gap: 2.2rem;
  }
  .hero__title {
    font-size: 1.9rem;
  }
  .win {
    transform: none;
  }
  .metrics__inner {
    grid-template-columns: 1fr 1fr;
    gap: 0.9rem 1.5rem;
  }
  .feat {
    grid-template-columns: 1fr;
  }
  /* cột trái chuyển thành lưới nút gọn phía trên panel chi tiết */
  .feat__list {
    flex-direction: row;
    flex-wrap: wrap;
  }
  .feat__item {
    width: auto;
    flex: 1 1 45%;
  }
  .flow {
    grid-template-columns: 1fr 1fr;
  }
  .flow__step::after {
    display: none;
  }
}
@media (max-width: 560px) {
  .feat__item {
    flex: 1 1 100%;
  }
  .flow {
    grid-template-columns: 1fr;
  }
  .metrics__inner {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
