<script setup>
import { RouterLink } from 'vue-router'
import BrandLogo from '@/components/ui/BrandLogo.vue'

const year = new Date().getFullYear()
</script>

<template>
  <div class="layout">
    <header class="layout__header">
      <div class="container layout__bar">
        <RouterLink to="/" class="brand">
          <BrandLogo :size="34" />
          <span class="brand__sub">Điều phối giáo viên · tích hợp AI</span>
        </RouterLink>

        <nav class="nav">
          <RouterLink to="/" class="nav__link">Trang chủ</RouterLink>
          <a href="#features" class="nav__link">Tính năng</a>
          <RouterLink to="/dashboard" class="nav__link">Dashboard</RouterLink>
          <a href="#contact" class="nav__link">Liên hệ</a>
        </nav>

        <RouterLink to="/dashboard" class="btn btn--primary">Vào hệ thống</RouterLink>
      </div>
    </header>

    <main class="layout__main">
      <!-- Nội dung từng trang được render ở đây -->
      <slot />
    </main>

    <footer id="contact" class="layout__footer">
      <div class="container">
        <p>© {{ year }} KDC EduOps AI — Hệ thống quản lý &amp; điều phối giáo viên.</p>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}
.layout__header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #fff;
  border-bottom: 1px solid var(--c-border);
}
.layout__bar {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  height: 64px;
}
.brand {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
  text-decoration: none;
}
.brand__logo {
  font-weight: 800;
  font-size: 1.35rem;
  color: var(--c-primary);
  letter-spacing: 0.5px;
}
.brand__sub {
  font-size: 0.72rem;
  color: var(--c-text-muted);
}
.nav {
  display: flex;
  gap: 1.25rem;
  margin-left: auto;
}
.nav__link {
  color: var(--c-text);
  text-decoration: none;
  font-weight: 500;
}
.nav__link:hover {
  color: var(--c-primary);
}
.layout__main {
  flex: 1;
}
.layout__footer {
  background: var(--a-sidebar);
  color: #cdd7e5;
  padding: 1.25rem 0;
  font-size: 0.9rem;
}
.brand__sub {
  margin-top: 2px;
}
</style>
