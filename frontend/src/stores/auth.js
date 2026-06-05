import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

// authStore: lưu token/user/role trong BỘ NHỚ (không dùng localStorage cho access token).
export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(null)
  const user = ref(null) // { id, fullName, ... }
  const role = ref(null) // ADMIN | EMPLOYEE | SCHOOL | TEACHER

  const isLoggedIn = computed(() => !!accessToken.value)

  function setSession({ token, user: u, role: r }) {
    accessToken.value = token
    user.value = u ?? null
    role.value = r ?? null
  }

  function logout() {
    accessToken.value = null
    user.value = null
    role.value = null
  }

  return { accessToken, user, role, isLoggedIn, setSession, logout }
})
