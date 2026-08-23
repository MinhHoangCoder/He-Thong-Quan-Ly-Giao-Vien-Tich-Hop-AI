<script setup>
// Brandmark Sao Việt: ngôi sao cam trong vòng tròn xanh lam, khớp bảng màu chung.
// Mọi nơi hiển thị logo đều gọi qua component này, nên đổi nhận diện chỉ cần thay
// một file ảnh và giá trị mặc định của prop text.
import logoUrl from '@/assets/logo-sao-viet.png'

defineProps({
  size: { type: [Number, String], default: 36 },
  showText: { type: Boolean, default: true },
  text: { type: String, default: 'Sao Việt' },
  // 'dark' = chữ tối (nền sáng) | 'light' = chữ trắng (nền tối)
  variant: { type: String, default: 'dark' },
})
</script>

<template>
  <span class="brandlogo" :class="`brandlogo--${variant}`">
    <img class="brandlogo__mark" :width="size" :height="size" :src="logoUrl" alt="Sao Việt" />
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
