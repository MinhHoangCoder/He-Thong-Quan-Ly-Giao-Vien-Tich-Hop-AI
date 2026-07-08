import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

// Khóa lưu phiên ở localStorage. Chỉ lưu refreshToken + user (KHÔNG lưu access token
// vào localStorage để giảm rủi ro XSS). Access token sống trong bộ nhớ (ramTokens).
const LS_KEY = 'tsdms.session'

// Store hỗ trợ NHIỀU tài khoản đăng nhập cùng lúc (account switcher — đổi nhanh
// admin/staff/teacher/school khi dev & demo, không phải logout/login liên tục).
// - accounts: [{ refreshToken, user }] — đăng nhập mới nhất đứng đầu danh sách.
// - activeUsername: tài khoản đang dùng; accessToken/refreshToken/user bên dưới là
//   computed trỏ vào tài khoản này -> phần còn lại của app đọc y như thời 1 tài khoản.
// Trade-off có ý thức: localStorage giữ refresh token của TẤT CẢ tài khoản đang
// đăng nhập — dính XSS là mất cả chùm (xem dev-note account-switcher).
export const useAuthStore = defineStore('auth', () => {
  const accounts = ref([])
  const activeUsername = ref(null)
  const ramTokens = ref({}) // username -> access token (mất khi F5 -> tự xin lại bằng refresh)

  // Khôi phục phiên khi mở lại app / F5. Đọc được cả shape cũ {refreshToken, user}
  // (trước khi có multi-account) để người dùng không bị văng khi cập nhật code.
  const saved = JSON.parse(localStorage.getItem(LS_KEY) || 'null')
  if (saved?.accounts) {
    accounts.value = saved.accounts
    activeUsername.value = saved.activeUsername ?? saved.accounts[0]?.user?.username ?? null
  } else if (saved?.refreshToken) {
    accounts.value = [{ refreshToken: saved.refreshToken, user: saved.user }]
    activeUsername.value = saved.user?.username ?? null
  }

  const activeAccount = computed(
    () => accounts.value.find((a) => a.user?.username === activeUsername.value) ?? null,
  )

  const accessToken = computed(() => ramTokens.value[activeUsername.value] ?? null)
  const refreshToken = computed(() => activeAccount.value?.refreshToken ?? null)
  const user = computed(() => activeAccount.value?.user ?? null)

  // "Đã đăng nhập" = tài khoản active còn refresh token (phiên bền).
  const isLoggedIn = computed(() => !!refreshToken.value)
  const roles = computed(() => user.value?.roles ?? [])
  const primaryRole = computed(() => roles.value[0] ?? null)

  function persist() {
    localStorage.setItem(
      LS_KEY,
      JSON.stringify({ accounts: accounts.value, activeUsername: activeUsername.value }),
    )
  }

  // Lưu phiên sau login / refresh / cập nhật hồ sơ. Upsert theo username:
  // login tài khoản MỚI -> thêm vào danh sách và thành active, tài khoản cũ giữ nguyên;
  // refresh/cập nhật tài khoản ĐANG CÓ -> chỉ thay token/user snapshot của nó.
  function setSession({ accessToken: at, refreshToken: rt, user: u }) {
    const username = u?.username ?? activeUsername.value
    if (!username) return
    const idx = accounts.value.findIndex((a) => a.user?.username === username)
    const cur = idx >= 0 ? accounts.value[idx] : { refreshToken: null, user: null }
    const entry = { refreshToken: rt ?? cur.refreshToken, user: u ?? cur.user }
    if (idx >= 0) accounts.value.splice(idx, 1)
    accounts.value.unshift(entry)
    if (at) ramTokens.value[username] = at
    activeUsername.value = username
    persist()
  }

  // Đổi tài khoản active. Access token của tài khoản đích có thể không còn trong RAM
  // (F5) hoặc đã hết hạn -> request đầu tiên 401 và interceptor tự refresh, y luồng F5.
  function switchAccount(username) {
    const target = accounts.value.find((a) => a.user?.username === username)
    if (!target) return null
    activeUsername.value = username
    persist()
    return target
  }

  // Gỡ tài khoản active khỏi máy này (logout / refresh token chết) rồi đôn tài khoản
  // kế tiếp lên active. Trả về tài khoản kế tiếp, hoặc null nếu hết.
  function dropActiveAccount() {
    delete ramTokens.value[activeUsername.value]
    accounts.value = accounts.value.filter((a) => a.user?.username !== activeUsername.value)
    const next = accounts.value[0] ?? null
    activeUsername.value = next?.user?.username ?? null
    if (next) persist()
    else localStorage.removeItem(LS_KEY)
    return next
  }

  // Xóa SẠCH mọi phiên trên máy này (chỉ phía client, không thu hồi token trên server).
  function clear() {
    accounts.value = []
    activeUsername.value = null
    ramTokens.value = {}
    localStorage.removeItem(LS_KEY)
  }

  return {
    accounts,
    accessToken,
    refreshToken,
    user,
    isLoggedIn,
    roles,
    primaryRole,
    setSession,
    switchAccount,
    dropActiveAccount,
    clear,
  }
})
