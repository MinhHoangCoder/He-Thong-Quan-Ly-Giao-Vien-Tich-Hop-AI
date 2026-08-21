import http from './http'

/**
 * API Bảng điều khiển (admin) — base `/api/v1/dashboard`.
 *
 * Các endpoint tách theo CHI PHÍ TRUY VẤN, không theo khối giao diện. `summary` chỉ quét một lượt
 * nên về gần như tức thì và các thẻ chỉ số hiện ngay. Gộp làm một thì cả trang phải đứng chờ truy
 * vấn chậm nhất, và bất kỳ truy vấn nào hỏng cũng xoá trắng toàn bộ màn hình.
 *
 * `breakdown` tách riêng khỏi `analytics` vì bảng chi tiết có ba chiều mà màn hình chỉ hiện một
 * tab — gom sẵn cả ba là làm thừa hai phần ba công việc ở mọi lần mở trang.
 */

/** Bỏ các khoá rỗng để URL không lủng củng `&schoolId=` — backend hiểu "thiếu" là "không lọc". */
function thamSo(boLoc) {
  const p = {}
  for (const [k, v] of Object.entries(boLoc || {})) {
    if (v !== null && v !== undefined && v !== '') p[k] = v
  }
  return p
}

export const dashboardApi = {
  /** Các thẻ chỉ số kèm đối chiếu kỳ trước. */
  summary(boLoc) {
    return http.get('/dashboard/summary', { params: thamSo(boLoc) })
  },

  /** Hai biểu đồ: số buổi/chi phí theo tháng và cơ cấu nhóm môn. */
  analytics(boLoc) {
    return http.get('/dashboard/analytics', { params: thamSo(boLoc) })
  },

  /** Bảng thống kê chi tiết theo MỘT chiều: GIAO_VIEN | TRUONG | MON. */
  breakdown(boLoc, chieu) {
    return http.get('/dashboard/breakdown', { params: { ...thamSo(boLoc), chieu } })
  },

  /** Việc cần xử lý, buổi dạy sắp tới, phân công gần đây. */
  operations(boLoc) {
    return http.get('/dashboard/operations', { params: thamSo(boLoc) })
  },

  /** Danh mục đổ vào các ô chọn của thanh lọc (chi nhánh / trường / nhóm môn). */
  filters() {
    return http.get('/dashboard/filters')
  },

  /**
   * Tải file CSV theo bộ lọc đang áp.
   *
   * Phải đi qua axios chứ không mở thẳng đường dẫn bằng `window.open`: endpoint yêu cầu
   * header `Authorization`, mà một tab mới thì không mang theo header nào. Đổi lại, file về
   * dưới dạng blob nên phải tự tạo liên kết tải rồi thu hồi URL tạm.
   */
  async xuatCsv(boLoc, chieu = 'GIAO_VIEN') {
    const { data, headers } = await http.get('/dashboard/export', {
      params: { ...thamSo(boLoc), chieu },
      responseType: 'blob',
    })

    // Tên file do server đặt (đã kèm chiều phân tích và ngày đầu kỳ); lấy được thì dùng.
    const cd = headers?.['content-disposition'] || ''
    const khop = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(cd)
    const ten = khop ? decodeURIComponent(khop[1]) : 'thong-ke.csv'

    const url = URL.createObjectURL(new Blob([data], { type: 'text/csv;charset=utf-8' }))
    const a = document.createElement('a')
    a.href = url
    a.download = ten
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  },
}
