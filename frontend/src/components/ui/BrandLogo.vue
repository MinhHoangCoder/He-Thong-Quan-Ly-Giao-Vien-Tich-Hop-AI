<script setup>
// Brandmark KDC EduOps AI — huy hiệu lục giác gradient cam→xanh (gợi badge ngực robot).
// Dùng tạm bằng SVG inline để không phụ thuộc file ảnh; khi có PNG logo thật chỉ cần
// thay phần <svg> bằng <img src="@/assets/logo-kdc.png">.
import logoUrl from '@/assets/KDC_EduOps_AI_Logo.jpg'

defineProps({
  size: { type: [Number, String], default: 36 },
  showText: { type: Boolean, default: true },
  text: { type: String, default: 'KDC EduOps AI' },
  // 'dark' = chữ tối (nền sáng) | 'light' = chữ trắng (nền tối)
  variant: { type: String, default: 'dark' },
})
</script>

<template>
  <span class="brandlogo" :class="`brandlogo--${variant}`">
    <img
      class="brandlogo__mark"
      :width="size"
      :height="size"
      :src="logoUrl"
      alt="KDC EduOps AI"
    />
    <span v-if="showText" class="brandlogo__text">{{ text }}</span>
  </span>
</template>

<style scoped>
.brandlogo {
  display: inline-flex;
  align-items: center;
  gap: 0.6rem;
}
.brandlogo__mark {
  flex: 0 0 auto;
  border-radius: 50%;
  object-fit: cover;
  filter: drop-shadow(0 4px 10px rgba(15, 40, 80, 0.28));
}
.brandlogo__text {
  font-weight: 800;
  letter-spacing: 0.3px;
  line-height: 1;
  white-space: nowrap;
}
.brandlogo--dark .brandlogo__text {
  color: var(--c-text, #0f172a);
}
.brandlogo--light .brandlogo__text {
  color: #fff;
}
</style>
