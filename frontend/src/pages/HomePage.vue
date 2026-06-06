<script setup>
// Trang chủ — nhóm cắt giao diện vào đây.
const features = [
  {
    icon: '🧑‍🏫',
    title: 'Quản lý giáo viên',
    desc: 'Hồ sơ, môn dạy, bằng cấp, hợp đồng và trạng thái.',
  },
  { icon: '🏫', title: 'Trường khách hàng', desc: 'Thông tin trường, phòng học, lớp và học sinh.' },
  {
    icon: '🗓️',
    title: 'Điều phối & xếp lịch',
    desc: 'Trung tâm phân công → lịch dạy với quy trình duyệt.',
  },
  {
    icon: '🤖',
    title: 'Tích hợp AI',
    desc: 'Tự xếp lịch, cảnh báo trùng, gợi ý ghép giáo viên, chatbot.',
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
        Số hóa toàn bộ quy trình từ phân công giáo viên, xếp lịch dạy đến chấm công và
        tính lương cho trung tâm giáo dục.
      </p>
      <div class="hero__actions">
        <a href="#features" class="btn btn--primary btn--lg">Khám phá tính năng</a>
        <a href="#contact" class="btn btn--ghost btn--lg">Liên hệ</a>
      </div>
    </div>
  </section>

  <!-- Tính năng -->
  <section id="features" class="features">
    <div class="container">
      <h2 class="section-title">Tính năng chính</h2>
      <div class="features__grid">
        <article v-for="f in features" :key="f.title" class="card">
          <div class="card__icon">{{ f.icon }}</div>
          <h3 class="card__title">{{ f.title }}</h3>
          <p class="card__desc">{{ f.desc }}</p>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.hero {
  background: linear-gradient(135deg, var(--c-primary) 0%, var(--c-primary-dark) 100%);
  color: #fff;
  padding: 5rem 0;
}
.hero__inner {
  max-width: 760px;
}
.hero__title {
  font-size: 2.6rem;
  line-height: 1.15;
  margin: 0 0 1rem;
}
.hero__title span {
  color: #a7f3d0; /* xanh ngọc nhạt nổi trên nền teal */
}
.hero__desc {
  font-size: 1.1rem;
  color: #d4f0ea;
  margin: 0 0 2rem;
}
.hero__actions {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}
.features {
  padding: 4rem 0;
}
.section-title {
  text-align: center;
  font-size: 1.8rem;
  margin: 0 0 2.5rem;
  color: var(--c-text);
}
.features__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1.5rem;
}
.card {
  border: 1px solid var(--c-border);
  border-radius: 12px;
  padding: 1.5rem;
  background: #fff;
  transition:
    box-shadow var(--t),
    transform var(--t),
    border-color var(--t);
}
.card:hover {
  box-shadow: 0 14px 30px rgba(13, 60, 54, 0.12);
  transform: translateY(-4px);
  border-color: var(--c-primary-light);
}
.card__icon {
  font-size: 2rem;
  transition: transform var(--t);
}
/* icon nảy nhẹ khi rê chuột vào thẻ */
.card:hover .card__icon {
  transform: scale(1.15) rotate(-4deg);
}
.card__title {
  margin: 0.75rem 0 0.5rem;
  font-size: 1.15rem;
  color: var(--c-primary);
}
.card__desc {
  margin: 0;
  color: var(--c-text-muted);
  font-size: 0.95rem;
}
</style>
