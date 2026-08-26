// Helper format chung. Ví dụ định dạng ngày kiểu Việt Nam.
export function formatDate(value) {
  if (!value) return ''
  const d = new Date(value)
  return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

export function formatDateTime(value) {
  if (!value) return ''
  const d = new Date(value)
  return d.toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatCurrency(value) {
  if (value == null) return ''
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value)
}

/**
 * Hôm nay dạng `yyyy-MM-dd` theo GIỜ ĐỊA PHƯƠNG.
 *
 * KHÔNG dùng `new Date().toISOString().slice(0, 10)`: `toISOString()` quy về UTC, mà VN là
 * UTC+7 — nên trong khung 00:00–07:00 giờ VN nó trả về NGÀY HÔM QUA. Bẫy này đã được ghi
 * chú ở AttendancePage / TeacherAttendancePage / AssignmentFormPage (mỗi nơi tự viết một
 * bản), nhưng HolidayListPage và PayrollPage thì lỡ dùng `toISOString()` — đặt hàm dùng
 * chung ở đây để lần sau khỏi phải nhớ.
 */
export function isoToday() {
  const d = new Date()
  const thang = String(d.getMonth() + 1).padStart(2, '0')
  const ngay = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${thang}-${ngay}`
}
