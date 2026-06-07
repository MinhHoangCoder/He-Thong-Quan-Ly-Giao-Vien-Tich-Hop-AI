import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

// Khóa lưu phiên ở localStorage. Chỉ lưu refreshToken + user (KHÔNG lưu access token
// vào localStorage để giảm rủi ro XSS). Access token sống trong bộ nhớ (biến ref).
const LS_KEY = 'tsdms.session'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(null) // chỉ trong RAM, mất khi F5 -> sẽ tự xin lại bằng refresh
  const refreshToken = ref(null)
  const user = ref(null) // { id, username, fullName, email, roles: [] }

  // Khôi phục phiên khi mở lại app / F5
  const saved = JSON.parse(localStorage.getItem(LS_KEY) || 'null')
  if (saved) {
    refreshToken.value = saved.refreshToken
    user.value = saved.user
  }

  // "Đã đăng nhập" = còn refresh token (phiên bền). Access token lấy lại khi cần.
  const isLoggedIn = computed(() => !!refreshToken.value)
  const roles = computed(() => user.value?.roles ?? [])
  const primaryRole = computed(() => roles.value[0] ?? null)

  function persist() {
    localStorage.setItem(
      LS_KEY,
      JSON.stringify({ refreshToken: refreshToken.value, user: user.value }),
    )
  }

  // Lưu cả cặp token + user (sau login / refresh)
  function setSession({ accessToken: at, refreshToken: rt, user: u }) {
    accessToken.value = at ?? accessToken.value
    refreshToken.value = rt ?? refreshToken.value
    user.value = u ?? user.value
    persist()
  }

  function clear() {
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    localStorage.removeItem(LS_KEY)
  }

  return {
    accessToken,
    refreshToken,
    user,
    isLoggedIn,
    roles,
    primaryRole,
    setSession,
    clear,
  }
})
