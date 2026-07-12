import { defineStore } from 'pinia'
import { ref, computed, watchEffect } from 'vue'

// Khóa lưu TÙY CHỌN GIAO DIỆN ở localStorage — tách khỏi 'tsdms.session' (phiên đăng
// nhập) vì vòng đời khác nhau: logout xóa phiên nhưng KHÔNG được xóa tùy chọn giao diện.
const LS_KEY = 'tsdms.ui'

// Store giao diện dùng chung: theme sáng/tối, cỡ chữ, hiệu ứng chuyển động.
// Cách hoạt động: các lựa chọn được "dịch" thành thuộc tính data-* trên thẻ <html>
// (data-theme / data-fontsize / data-motion); main.css nhìn các thuộc tính đó để
// đổi TOÀN BỘ design token -> mọi component đổi màu theo mà không phải sửa gì.
// index.html có 1 script nhỏ set data-theme TRƯỚC khi Vue chạy để không chớp trắng.
export const useUiStore = defineStore('ui', () => {
  const saved = JSON.parse(localStorage.getItem(LS_KEY) || 'null') ?? {}

  const theme = ref(saved.theme ?? 'system') // 'light' | 'dark' | 'system'
  const fontSize = ref(saved.fontSize ?? 'normal') // 'normal' | 'large'
  const motion = ref(saved.motion ?? true) // false = giảm hiệu ứng chuyển động

  // Theo dõi cài đặt sáng/tối của HỆ ĐIỀU HÀNH — chỉ dùng khi theme = 'system'.
  // matchMedia có sự kiện 'change' nên đổi theme Windows là web đổi theo ngay.
  const media = window.matchMedia('(prefers-color-scheme: dark)')
  const systemDark = ref(media.matches)
  media.addEventListener('change', (e) => {
    systemDark.value = e.matches
  })

  const resolvedTheme = computed(() =>
    theme.value === 'system' ? (systemDark.value ? 'dark' : 'light') : theme.value,
  )
  const isDark = computed(() => resolvedTheme.value === 'dark')

  // watchEffect: chạy lại mỗi khi BẤT KỲ ref nào bên trong đổi -> vừa áp lên <html>
  // vừa lưu localStorage, không cần gọi thủ công ở từng setter.
  watchEffect(() => {
    const root = document.documentElement
    root.dataset.theme = resolvedTheme.value
    if (fontSize.value === 'large') root.dataset.fontsize = 'large'
    else delete root.dataset.fontsize
    if (!motion.value) root.dataset.motion = 'reduced'
    else delete root.dataset.motion
    localStorage.setItem(
      LS_KEY,
      JSON.stringify({ theme: theme.value, fontSize: fontSize.value, motion: motion.value }),
    )
  })

  function setTheme(value) {
    theme.value = value
  }
  // Nút toggle nhanh trên topbar: đảo sáng<->tối theo màu ĐANG THẤY (kể cả khi đang
  // ở chế độ 'system') — người dùng bấm là muốn đổi ngay cái đang nhìn.
  function toggleTheme() {
    theme.value = isDark.value ? 'light' : 'dark'
  }
  function setFontSize(value) {
    fontSize.value = value
  }
  function setMotion(value) {
    motion.value = value
  }

  return {
    theme,
    fontSize,
    motion,
    resolvedTheme,
    isDark,
    setTheme,
    toggleTheme,
    setFontSize,
    setMotion,
  }
})
