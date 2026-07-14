<script setup>
// Trang chủ quảng cáo (chưa đăng nhập).
// Khối "Tính năng chính" theo kiểu MASTER–DETAIL: bấm 1 tính năng ở cột trái
// -> panel phải hiện chi tiết (mô tả + khả năng cụ thể + vai trò sử dụng).
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
    icon: 'ai',
    title: 'Trợ lý AI',
    tagline: 'Hỏi đáp vận hành',
    desc: 'Chatbot nội bộ trả lời câu hỏi về vận hành trung tâm ngay trong hệ thống.',
    points: [
      'Hỏi đáp về phân công, chấm công, bảng lương, cách dùng hệ thống',
      'Gợi ý sẵn các câu hỏi thường gặp để tra nhanh',
      'Nền tảng mở rộng: tra cứu số liệu thật và gợi ý xếp lịch về sau',
    ],
    roles: ['Quản trị', 'Nhân viên trung tâm'],
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

// Quy trình vận hành khép kín — đúng đường đi dữ liệu trong hệ thống.
const workflow = [
  { title: 'Phân công', desc: 'Trung tâm gán giáo viên ↔ trường ↔ lớp theo tiết hằng tuần.' },
  { title: 'Sinh lịch dạy', desc: 'Hệ thống tự trải thành từng buổi dạy cho cả giai đoạn.' },
  { title: 'Dạy & chấm công', desc: 'Điểm danh theo buổi, ghi giờ vào / giờ ra.' },
  { title: 'Tính lương', desc: 'Gom tiết theo kỳ, đơn giá theo cấp học, chốt bảng lương.' },
  { title: 'Theo dõi', desc: 'Dashboard số liệu thật + thông báo trong hệ thống.' },
]

// 4 cổng làm việc theo vai trò — mỗi vai trò một portal riêng đã có trong hệ thống.
const portals = [
  {
    icon: 'settings',
    title: 'Quản trị viên',
    items: ['Dashboard vận hành toàn trung tâm', 'Quản lý tài khoản & phân quyền', 'Toàn bộ nghiệp vụ điều phối'],
  },
  {
    icon: 'assignment',
    title: 'Nhân viên trung tâm',
    items: ['Phân công & xếp lịch dạy', 'Chấm công, tính lương', 'Quản lý hồ sơ GV & kho bài giảng'],
  },
  {
    icon: 'teacher',
    title: 'Giáo viên',
    items: ['Lịch dạy cá nhân theo tuần', 'Kho bài giảng đã xuất bản', 'Hồ sơ, phiếu lương, đánh giá'],
  },
  {
    icon: 'school',
    title: 'Trường liên kết',
    items: ['Giáo viên đang dạy tại trường', 'Lịch dạy tại trường mình', 'Gửi đánh giá giáo viên'],
  },
]
</script>

<template>
  <!-- Hero -->
  <section class="hero">
    <div class="container hero__inner">
      <h1 class="hero__title">
        Hệ thống quản lý &amp; điều phối giáo viên <span>tích hợp AI</span>
      </h1>
      <p class="hero__desc">
        Số hóa toàn bộ quy trình từ phân công giáo viên, xếp lịch dạy đến chấm công và tính lương
        cho trung tâm giáo dục.
      </p>
      <div class="hero__actions">
        <RouterLink to="/dashboard" class="btn btn--primary btn--lg">Vào hệ thống</RouterLink>
        <a href="#features" class="btn btn--ghost btn--lg">Khám phá tính năng</a>
      </div>
      <ul class="hero__chips">
        <li><SvgIcon name="assignment" :size="15" /> Phân công &amp; điều phối</li>
        <li><SvgIcon name="clock" :size="15" /> Chấm công theo buổi</li>
        <li><SvgIcon name="payroll" :size="15" /> Lương tính theo tiết</li>
        <li><SvgIcon name="ai" :size="15" /> Trợ lý AI nội bộ</li>
      </ul>
    </div>
  </section>

  <!-- Tính năng chính: bấm bên trái -> chi tiết bên phải -->
  <section id="features" class="section">
    <div class="container">
      <h2 class="section-title">Tính năng chính</h2>
      <p class="section-sub">Chọn một phân hệ để xem hệ thống làm được gì trong đó.</p>

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
            <SvgIcon class="feat__item-arrow" name="chevron" :size="16" />
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
      <h2 class="section-title">Quy trình vận hành khép kín</h2>
      <p class="section-sub">Dữ liệu chảy một đường từ phân công tới bảng lương — không nhập tay lại.</p>
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
      <h2 class="section-title">Mỗi vai trò một cổng làm việc riêng</h2>
      <p class="section-sub">Đăng nhập là vào thẳng đúng không gian của mình, thấy đúng việc của mình.</p>
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

  <!-- CTA cuối trang -->
  <section class="cta">
    <div class="container cta__inner">
      <div>
        <h2 class="cta__title">Sẵn sàng trải nghiệm?</h2>
        <p class="cta__desc">Đăng nhập để vào đúng cổng làm việc theo vai trò của bạn.</p>
      </div>
      <RouterLink to="/dashboard" class="btn btn--primary btn--lg">Vào hệ thống</RouterLink>
    </div>
  </section>
</template>

<style scoped>
/* ===== Hero ===== */
.hero {
  background: var(--grad-hero);
  color: #fff;
  padding: 4.5rem 0 3.5rem;
}
.hero__inner {
  max-width: 820px;
}
.hero__title {
  font-size: 2.6rem;
  line-height: 1.15;
  margin: 0 0 1rem;
}
.hero__title span {
  color: #ffb877; /* cam sáng nổi trên nền gradient */
}
.hero__desc {
  font-size: 1.1rem;
  color: rgba(255, 255, 255, 0.85);
  margin: 0 0 2rem;
}
.hero__actions {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}
.hero__chips {
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin: 2.2rem 0 0;
  padding: 0;
}
.hero__chips li {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.82rem;
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 9999px;
  padding: 0.32rem 0.8rem;
}

/* ===== Khung section chung ===== */
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
.section-title {
  text-align: center;
  font-size: 1.8rem;
  margin: 0 0 0.5rem;
  color: var(--c-text);
}
.section-sub {
  text-align: center;
  color: var(--c-text-muted);
  margin: 0 0 2.5rem;
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
    background var(--t-fast),
    transform var(--t-fast);
}
.feat__item:hover {
  border-color: var(--c-primary-light);
  transform: translateX(3px);
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
.feat__item-arrow {
  margin-left: auto;
  flex: 0 0 auto;
  color: var(--c-text-muted);
  transform: rotate(-90deg);
  opacity: 0;
  transition: opacity var(--t-fast);
}
.feat__item.is-active .feat__item-arrow {
  opacity: 1;
  color: var(--c-primary);
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
  text-align: center;
  padding: 0 0.4rem;
}
/* vạch nối giữa các bước (ẩn ở bước cuối) */
.flow__step:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 19px;
  left: calc(50% + 26px);
  width: calc(100% - 52px);
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
    transform var(--t),
    border-color var(--t);
}
.portal:hover {
  transform: translateY(-4px);
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

/* ===== CTA cuối trang ===== */
.cta {
  padding: 0 0 4rem;
}
.cta__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1.5rem;
  flex-wrap: wrap;
  background: var(--grad-hero);
  border-radius: 18px;
  padding: 2.2rem 2.4rem;
  color: #fff;
}
.cta__title {
  margin: 0 0 0.3rem;
  font-size: 1.5rem;
}
.cta__desc {
  margin: 0;
  color: rgba(255, 255, 255, 0.85);
}

/* ===== Responsive ===== */
@media (max-width: 900px) {
  .hero__title {
    font-size: 2rem;
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
  .feat__item-arrow {
    display: none;
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
}
</style>
