// Tiện ích hiển thị TIẾT theo BUỔI, KHÔNG kèm giờ.
// Dữ liệu lưu số tiết trong NGÀY (PeriodNumber 1..n) nhưng nhà trường gọi tên theo BUỔI
// ("Chiều · Tiết 5"), nên phải quy đổi. Backend đã trả sẵn `indexInSession` cho mọi DTO
// có tiết — dùng thẳng, KHÔNG đoán. Mỗi trường một khung tiết riêng (tiểu học 10 tiết,
// THCS 9 tiết) nên "buổi sáng có mấy tiết" không phải hằng số.
//
// FALLBACK chỉ dùng khi thiếu indexInSession: dòng lưới dựng từ số tiết trơ (tuần chưa
// có buổi dạy nào để lấy khung thật), hoặc dữ liệu cũ từ bản API trước.
const FALLBACK_MORNING_COUNT = 5

/** MORNING | AFTERNOON — ưu tiên sessionType, thiếu thì suy từ số tiết. */
export function sessionOf(periodNumber, sessionType) {
  if (sessionType) return sessionType
  return Number(periodNumber) <= FALLBACK_MORNING_COUNT ? 'MORNING' : 'AFTERNOON'
}

/** Nhãn buổi: "Sáng" | "Chiều". */
export function sessionLabel(periodNumber, sessionType) {
  return sessionOf(periodNumber, sessionType) === 'AFTERNOON' ? 'Chiều' : 'Sáng'
}

/** Số tiết HIỂN THỊ trong buổi (chiều đánh lại từ 1). */
export function tietInSession(periodNumber, sessionType, indexInSession) {
  if (indexInSession != null) return Number(indexInSession)
  const n = Number(periodNumber)
  return sessionOf(n, sessionType) === 'AFTERNOON' ? n - FALLBACK_MORNING_COUNT : n
}

/** Nhãn đầy đủ, vd: "Sáng · Tiết 3" hoặc "Chiều · Tiết 5". */
export function tietLabel(periodNumber, sessionType, indexInSession) {
  if (periodNumber == null) return ''
  return `${sessionLabel(periodNumber, sessionType)} · Tiết ${tietInSession(periodNumber, sessionType, indexInSession)}`
}

/** Nhãn ngắn cho ô hẹp, vd: "S·T3" / "C·T5". */
export function tietShort(periodNumber, sessionType, indexInSession) {
  if (periodNumber == null) return ''
  const s = sessionOf(periodNumber, sessionType) === 'AFTERNOON' ? 'C' : 'S'
  return `${s}·T${tietInSession(periodNumber, sessionType, indexInSession)}`
}

/**
 * Dựng các DÒNG của lưới thời khóa biểu tuần từ chính các buổi dạy đang xem.
 *
 * Trước đây lưới cứng 9 dòng nên trường tiểu học (10 tiết từ khi có "Chiều · Tiết 5")
 * bị nuốt mất tiết cuối. Ở đây số dòng co giãn theo dữ liệu, và mỗi dòng mang luôn
 * buổi/nhãn lấy từ khung tiết THẬT của trường thay vì suy từ con số.
 *
 * @param events danh sách buổi dạy (cần periodNumber, sessionType, indexInSession)
 * @param minRows số dòng tối thiểu — để tuần trống vẫn ra hình thời khóa biểu
 * @returns [{ periodNumber, sessionType, indexInSession, label, firstOfSession }]
 */
export function periodRows(events, minRows = 9) {
  const meta = new Map()
  for (const e of events ?? []) {
    const n = Number(e?.periodNumber)
    if (!Number.isFinite(n) || meta.has(n)) continue
    meta.set(n, { sessionType: e.sessionType ?? null, indexInSession: e.indexInSession ?? null })
  }
  const maxNumber = Math.max(minRows, ...meta.keys())

  const rows = []
  let prevSession = null
  for (let n = 1; n <= maxNumber; n++) {
    const { sessionType = null, indexInSession = null } = meta.get(n) ?? {}
    const session = sessionOf(n, sessionType)
    rows.push({
      periodNumber: n,
      sessionType: session,
      indexInSession,
      label: tietLabel(n, sessionType, indexInSession),
      // Vạch phân cách giữa hai buổi — bám dữ liệu, không cố định ở tiết 6 như trước.
      firstOfSession: prevSession !== null && session !== prevSession,
    })
    prevSession = session
  }
  return rows
}
