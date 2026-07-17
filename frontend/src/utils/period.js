// Tiện ích hiển thị TIẾT theo BUỔI, KHÔNG kèm giờ.
// Quy ước dữ liệu (seed): mỗi trường 9 tiết — Sáng: PeriodNumber 1..5, Chiều: 6..9.
// Yêu cầu hiển thị: buổi chiều đánh lại từ Tiết 1 (6→1, 7→2, 8→3, 9→4).
const MORNING_COUNT = 5

/** MORNING | AFTERNOON — ưu tiên sessionType, thiếu thì suy từ số tiết. */
export function sessionOf(periodNumber, sessionType) {
  if (sessionType) return sessionType
  return Number(periodNumber) <= MORNING_COUNT ? 'MORNING' : 'AFTERNOON'
}

/** Nhãn buổi: "Sáng" | "Chiều". */
export function sessionLabel(periodNumber, sessionType) {
  return sessionOf(periodNumber, sessionType) === 'AFTERNOON' ? 'Chiều' : 'Sáng'
}

/** Số tiết HIỂN THỊ trong buổi (chiều đánh lại từ 1). */
export function tietInSession(periodNumber, sessionType) {
  const n = Number(periodNumber)
  return sessionOf(n, sessionType) === 'AFTERNOON' ? n - MORNING_COUNT : n
}

/** Nhãn đầy đủ, vd: "Sáng · Tiết 3" hoặc "Chiều · Tiết 1". */
export function tietLabel(periodNumber, sessionType) {
  if (periodNumber == null) return ''
  return `${sessionLabel(periodNumber, sessionType)} · Tiết ${tietInSession(periodNumber, sessionType)}`
}

/** Nhãn ngắn cho ô hẹp, vd: "S·T3" / "C·T1". */
export function tietShort(periodNumber, sessionType) {
  if (periodNumber == null) return ''
  const s = sessionOf(periodNumber, sessionType) === 'AFTERNOON' ? 'C' : 'S'
  return `${s}·T${tietInSession(periodNumber, sessionType)}`
}
