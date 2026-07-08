import { useRouter } from 'vue-router'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { roleHome } from '@/router/roleHome'

// Trả về hàm logout dùng chung cho mọi nơi (topbar admin, trang trường/giáo viên...).
// Gọi trong setup() của component.
// Multi-account: chỉ đăng xuất tài khoản ĐANG DÙNG; nếu còn tài khoản khác đã đăng nhập
// thì rơi về tài khoản đó (giống Google), hết mới về trang login.
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
      // bỏ qua: dù sao cũng gỡ phiên ở dưới
    } finally {
      const next = auth.dropActiveAccount()
      if (next) {
        // Điều hướng cứng (reload) để mọi trang nạp lại dữ liệu theo tài khoản mới,
        // không dính state cũ của tài khoản vừa đăng xuất.
        window.location.assign(router.resolve(roleHome(next.user?.roles ?? [])).fullPath)
      } else {
        router.push({ name: 'login' })
      }
    }
  }
}
