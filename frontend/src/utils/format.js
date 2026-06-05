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
