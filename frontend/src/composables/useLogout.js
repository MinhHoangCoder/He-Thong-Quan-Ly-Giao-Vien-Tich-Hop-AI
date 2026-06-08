import { useRouter } from 'vue-router'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

// Trả về hàm logout dùng chung cho mọi nơi (topbar admin, trang trường/giáo viên...).
// Gọi trong setup() của component.
export function useLogout() {
  const auth = useAuthStore()
  const router = useRouter()

  return async function logout() {
    try {
      // Báo backend thu hồi refresh token (đăng xuất thật). Lỗi mạng cũng không sao,
      // vẫn xóa phiên ở client.
      if (auth.refreshToken) {
        await authApi.logout(auth.refreshToken)
      }
    } catch {
      // bỏ qua: dù sao cũng clear phiên ở dưới
    } finally {
      auth.clear()
      router.push({ name: 'login' })
    }
  }
}
