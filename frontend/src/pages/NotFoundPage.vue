<script setup>
// Trang 404 dùng CHUNG cho mọi URL không khớp route nào (catch-all).
// Bọc bởi BlankLayout (không header/sidebar) — layout này vốn đã canh giữa màn hình.
// Nút "về nhà" thông minh: đã đăng nhập -> trang chính theo vai trò (roleHome);
// chưa đăng nhập -> trang chủ công khai.
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import SvgIcon from '@/components/ui/SvgIcon.vue'
import { useAuthStore } from '@/stores/auth'
import { roleHome } from '@/router/roleHome'

const route = useRoute()
const auth = useAuthStore()

// Đích nút chính: người đã đăng nhập về đúng "nhà" của họ, khách về landing.
const homeTarget = computed(() => (auth.isLoggedIn ? roleHome(auth.roles) : { name: 'home' }))
const homeLabel = computed(() => (auth.isLoggedIn ? 'Về trang chính' : 'Về trang chủ'))
</script>

<template>
  <div class="nf">
    <p class="nf__code">404</p>
    <h1 class="nf__title">Không tìm thấy trang</h1>
    <p class="nf__desc">
      Đường dẫn <code class="nf__path">{{ route.fullPath }}</code> không tồn tại hoặc đã được chuyển
      đi. Hãy kiểm tra lại địa chỉ.
    </p>
    <div class="nf__actions">
      <RouterLink :to="homeTarget" class="btn btn--primary">
        <SvgIcon name="home" :size="16" /> {{ homeLabel }}
      </RouterLink>
    </div>
  </div>
</template>

<style scoped>
.nf {
  max-width: 480px;
  padding: 2rem 1.5rem;
  text-align: center;
}
/* "404" lớn dùng gradient thương hiệu — dấu hiệu thị giác chính, không cần ảnh */
.nf__code {
  margin: 0;
  font-size: 6rem;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.03em;
  background: var(--grad-primary);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.nf__title {
  margin: 0.75rem 0 0.5rem;
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--c-text);
}
.nf__desc {
  margin: 0 0 1.75rem;
  color: var(--c-text-muted);
  line-height: 1.6;
}
.nf__path {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.85em;
  background: var(--c-surface-2);
  border: 1px solid var(--c-border);
  border-radius: 6px;
  padding: 0.08rem 0.4rem;
  color: var(--c-text);
  overflow-wrap: anywhere;
}
.nf__actions {
  display: flex;
  justify-content: center;
}
/* Nút theo tông CTA cam của site (đồng bộ .btn--primary các trang khác) */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.6rem 1.3rem;
  border-radius: 10px;
  font-size: 0.9rem;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
  border: 1px solid transparent;
  transition:
    background var(--t-fast),
    transform var(--t-fast),
    box-shadow var(--t-fast);
}
.btn--primary {
  background: var(--c-primary);
  color: #fff;
  box-shadow: 0 6px 14px rgba(249, 115, 22, 0.28);
}
.btn--primary:hover {
  background: var(--c-primary-dark);
  transform: translateY(-1px);
  box-shadow: 0 9px 18px rgba(249, 115, 22, 0.36);
}
</style>
