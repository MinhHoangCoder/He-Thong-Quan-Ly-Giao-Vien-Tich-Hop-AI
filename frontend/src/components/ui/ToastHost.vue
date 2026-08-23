<script setup>
import { useToast } from '@/composables/useToast'

/**
 * Chỗ vẽ toast — đặt MỘT lần trong layout, không đặt trong từng trang.
 * Teleport ra body vì layout có phần tử overflow, để trong cây đó thì toast bị cắt.
 */
const { toasts, dismiss } = useToast()
</script>

<template>
  <Teleport to="body">
    <div class="toasts">
      <TransitionGroup name="toast">
        <div v-for="t in toasts" :key="t.id" class="toast" :class="`toast--${t.type}`">
          <span>{{ t.message }}</span>
          <button class="toast__x" aria-label="Đóng" @click="dismiss(t.id)">×</button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toasts {
  position: fixed;
  right: 1.2rem;
  bottom: 1.2rem;
  z-index: 300;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.toast {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  min-width: 240px;
  max-width: 380px;
  padding: 0.7rem 0.9rem;
  border-radius: 10px;
  font-size: 0.88rem;
  color: #fff;
  box-shadow: 0 10px 28px rgba(11, 42, 74, 0.25);
}
.toast--success {
  background: #16a34a;
}
.toast--error {
  background: var(--c-danger);
}
.toast__x {
  margin-left: auto;
  border: none;
  background: none;
  color: inherit;
  font-size: 1.1rem;
  line-height: 1;
  cursor: pointer;
  opacity: 0.8;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
.toast-enter-active,
.toast-leave-active {
  transition: 0.2s;
}
</style>
