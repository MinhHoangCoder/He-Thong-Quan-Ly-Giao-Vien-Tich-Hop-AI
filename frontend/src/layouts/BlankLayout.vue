<script setup>
// Layout trống — dùng cho trang đăng nhập, lỗi 404... (không header/footer).
</script>

<template>
  <div class="blank">
    <slot />
  </div>
</template>

<style scoped>
.blank {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
