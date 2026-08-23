import { ref } from 'vue'

/**
 * Thông báo ngắn góc màn hình, dùng chung cả app.
 *
 * Danh sách để NGOÀI hàm (module scope) nên mọi trang gọi useToast() đều ghi vào cùng một
 * chỗ — nhờ vậy chỉ cần đặt <ToastHost /> một lần ở layout thay vì mỗi trang một cái.
 *
 * Vì sao không dùng alert(): alert chặn cả trang cho tới khi bấm OK, nên báo "Đã lưu" bằng
 * alert là bắt người dùng dừng lại để xác nhận một tin vui. Nó cũng không phân biệt được
 * thành công với thất bại.
 */
const items = ref([])
let seq = 0

const TIMEOUT_MS = 3500

export function useToast() {
  /**
   * @param {string} message nội dung
   * @param {'success'|'error'} type mặc định 'success'
   */
  function showToast(message, type = 'success') {
    const id = ++seq
    items.value.push({ id, message, type })
    setTimeout(() => dismiss(id), TIMEOUT_MS)
  }

  function dismiss(id) {
    items.value = items.value.filter((t) => t.id !== id)
  }

  return { toasts: items, showToast, dismiss }
}
