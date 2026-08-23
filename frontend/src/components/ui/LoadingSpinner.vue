<script setup>
/**
 * Báo "đang tải" bằng vòng cung gradient xoay quanh logo Sao Việt.
 *
 * VÌ SAO LOGO ĐỨNG YÊN: logo là ảnh chụp, không phải icon đối xứng — xoay cả ảnh thì thấy rõ
 * nó lệch và giật ở mỗi vòng. Cho vòng cung xoay còn logo đứng im vừa mượt vừa giữ được nhận
 * diện thương hiệu.
 *
 * VÌ SAO GOM THÀNH COMPONENT: vòng xoay này trước nằm `scoped` trong TeacherListPage nên
 * không trang nào khác dùng lại được, và cả app còn hàng chục chỗ chỉ in chữ "Đang tải…".
 *
 * Dùng:
 *   <LoadingSpinner overlay text="Đang tải số liệu…" />   <!-- phủ lên vùng nội dung -->
 *   <LoadingSpinner bare :size="14" />                    <!-- trong nút bấm, không logo -->
 */
import logoUrl from '@/assets/logo-sao-viet.png'

defineProps({
  /** Đường kính vòng xoay, tính bằng px. */
  size: { type: [Number, String], default: 56 },
  /** Chữ hiện dưới vòng xoay. Cũng là nhãn cho trình đọc màn hình. */
  text: { type: String, default: '' },
  /** Phủ kín phần tử cha (cha phải có `position: relative`). */
  overlay: { type: Boolean, default: false },
  /** Chỉ vòng xoay, bỏ logo — dùng cho nút bấm và những chỗ chật. */
  bare: { type: Boolean, default: false },
})
</script>

<template>
  <div
    class="ls"
    :class="{ 'ls--overlay': overlay, 'ls--inline': bare }"
    role="status"
    :aria-label="text || 'Đang tải'"
  >
    <span class="ls__mark" :style="{ '--ls-size': typeof size === 'number' ? size + 'px' : size }">
      <span class="ls__ring" />
      <img v-if="!bare" class="ls__logo" :src="logoUrl" alt="" />
    </span>
    <span v-if="text" class="ls__text">{{ text }}</span>
  </div>
</template>

<style scoped>
.ls {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.6rem;
}
.ls--inline {
  display: inline-flex;
  vertical-align: middle;
}

/* Cha phải có position: relative, nếu không lớp phủ sẽ bám vào viewport.

   KHÔNG căn giữa theo chiều dọc: vùng nội dung của một trang dashboard cao vài nghìn px, căn
   giữa là đẩy vòng xoay xuống quá màn hình đầu tiên — người dùng chỉ thấy trang mờ đi mà không
   hiểu vì sao. Ghim gần đỉnh để nó luôn nằm trong tầm nhìn ngay khi trang vừa mở. */
.ls--overlay {
  position: absolute;
  inset: 0;
  z-index: 5;
  justify-content: flex-start;
  padding-top: clamp(2rem, 16vh, 10rem);
  border-radius: inherit;
  background: rgba(245, 248, 252, 0.72);
  backdrop-filter: blur(1.5px);
}
:root[data-theme='dark'] .ls--overlay {
  background: rgba(11, 21, 35, 0.72);
}

.ls__mark {
  position: relative;
  display: inline-grid;
  place-items: center;
  width: var(--ls-size);
  height: var(--ls-size);
  flex: 0 0 auto;
}

/* Vòng cung: nền conic-gradient rồi khoét ruột bằng mask -> ra một vành mảnh có chuyển màu,
   không cần ảnh hay thư viện. Bề dày vành theo tỉ lệ đường kính để cỡ nào cũng cân. */
.ls__ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: conic-gradient(
    from 0deg,
    rgba(249, 115, 22, 0) 0deg,
    rgba(249, 115, 22, 0.15) 90deg,
    var(--c-primary) 300deg,
    var(--c-primary-light) 360deg
  );
  -webkit-mask: radial-gradient(
    farthest-side,
    transparent calc(100% - max(2px, var(--ls-size) * 0.09)),
    #000 calc(100% - max(2px, var(--ls-size) * 0.09))
  );
  mask: radial-gradient(
    farthest-side,
    transparent calc(100% - max(2px, var(--ls-size) * 0.09)),
    #000 calc(100% - max(2px, var(--ls-size) * 0.09))
  );
  animation: ls-spin 0.85s linear infinite;
}
.ls__logo {
  width: 62%;
  height: 62%;
  border-radius: 50%;
  object-fit: cover;
}
.ls__text {
  font-size: 0.84rem;
  color: var(--c-text-muted);
}

@keyframes ls-spin {
  to {
    transform: rotate(360deg);
  }
}

/* Người tắt hiệu ứng (cài đặt trong app hoặc của hệ điều hành) thì DỪNG HẲN vòng xoay.
   Không thể để nó chạy tiếp: main.css ép animation-duration về 0.001ms !important, vòng
   xoay sẽ nhảy loạn xạ mỗi khung hình — khó chịu hơn hẳn so với lúc chưa giảm hiệu ứng.
   Bù lại bằng chữ "Đang tải…", vốn mới là thứ mang thông tin. */
:root[data-motion='reduced'] .ls__ring {
  animation-name: none;
}
@media (prefers-reduced-motion: reduce) {
  .ls__ring {
    animation-name: none;
  }
}
</style>
