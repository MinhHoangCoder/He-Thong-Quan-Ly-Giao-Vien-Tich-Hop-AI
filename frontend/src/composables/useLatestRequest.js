import { onBeforeUnmount } from 'vue'

/**
 * Chống race giữa các request cùng loại: chỉ kết quả của lượt gọi MỚI NHẤT được áp dụng.
 * Lượt cũ về muộn (kể cả lỗi của lượt cũ) bị bỏ qua im lặng — hết cảnh đổi bộ lọc nhanh
 * mà bảng hiển thị dữ liệu của bộ lọc trước.
 *
 * Dùng:
 *   const latest = useLatestRequest()
 *   await latest(
 *     () => api.list(params),
 *     ({ data }) => { items.value = data },
 *     (e) => { error.value = '...' },
 *   )
 *
 * Component unmount thì mọi lượt đang bay tự vô hiệu (không set state mồ côi).
 */
export function useLatestRequest() {
  let seq = 0
  const run = async (request, onResult, onError) => {
    const my = ++seq
    try {
      const res = await request()
      if (my === seq) onResult(res)
      return my === seq
    } catch (e) {
      if (my === seq && onError) onError(e)
      return false
    }
  }
  run.invalidate = () => {
    seq++
  }
  onBeforeUnmount(run.invalidate)
  return run
}
