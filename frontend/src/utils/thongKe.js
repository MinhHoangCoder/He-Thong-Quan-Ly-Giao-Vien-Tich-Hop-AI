/**
 * Định dạng số cho Bảng điều khiển.
 *
 * VÌ SAO GOM VÀO MỘT FILE: các con số ở đây xuất hiện đồng thời trên thẻ chỉ số, trục biểu đồ,
 * tooltip và bảng phân tích. Mỗi chỗ tự định dạng một kiểu thì cùng một giá trị sẽ hiện ra
 * "1.446.125.000", "1,45 tỉ" và "1446125000" ở ba khối cạnh nhau, và người xem sẽ tưởng là ba
 * con số khác nhau.
 *
 * Backend luôn trả GIÁ TRỊ THÔ kèm một mã định dạng; toàn bộ việc thêm dấu chấm, dấu ₫ hay ký
 * hiệu phần trăm diễn ra ở đây.
 */

const VI = 'vi-VN'

/** Số nguyên có dấu phân cách hàng nghìn: 11557 → "11.557". */
export function soNguyen(v) {
  if (v == null || Number.isNaN(v)) return '—'
  return new Intl.NumberFormat(VI).format(Math.round(v))
}

/** Số có một chữ số thập phân: 7735.42 → "7.735,4". */
export function soLe(v, chuSo = 1) {
  if (v == null || Number.isNaN(v)) return '—'
  return new Intl.NumberFormat(VI, {
    minimumFractionDigits: chuSo,
    maximumFractionDigits: chuSo,
  }).format(v)
}

/**
 * Tiền RÚT GỌN: 1446125000 → "1,45 tỉ ₫".
 *
 * Thẻ chỉ số chỉ rộng chừng mười ký tự, mà tổng quỹ lương một năm học thì tới mười chữ số. In
 * đủ sẽ vỡ thẻ hoặc phải thu nhỏ cỡ chữ tới mức không đọc được; con số đầy đủ vẫn còn nguyên
 * trong tooltip và trong file CSV cho ai cần đối chiếu.
 */
export function tien(v) {
  if (v == null || Number.isNaN(v)) return '—'
  const abs = Math.abs(v)
  if (abs >= 1e9) return soLe(v / 1e9, 2) + ' tỉ ₫'
  if (abs >= 1e6) return soLe(v / 1e6, 1) + ' tr ₫'
  if (abs >= 1e3) return soNguyen(v) + ' ₫'
  return soNguyen(v) + ' ₫'
}

/** Tiền ĐẦY ĐỦ đến từng đồng — dùng cho tooltip và bảng, nơi có đủ chỗ. */
export function tienDay(v) {
  if (v == null || Number.isNaN(v)) return '—'
  return new Intl.NumberFormat(VI, {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(v)
}

/** Phần trăm: 94.87 → "94,9%". */
export function phanTram(v, chuSo = 1) {
  if (v == null || Number.isNaN(v)) return '—'
  return soLe(v, chuSo) + '%'
}

/**
 * Định dạng theo MÃ mà backend gửi kèm mỗi chỉ số.
 *
 * null KHÔNG được rơi về 0. Backend cố tình trả null cho những chỉ số chưa đo được (kỳ chưa có
 * dữ liệu chấm công chẳng hạn); hiển thị 0% ở đó là khẳng định "đã đo, kết quả bằng không" —
 * một câu sai hoàn toàn khác với "chưa có gì để đo".
 */
export function theoMa(v, ma) {
  if (v == null || Number.isNaN(v)) return '—'
  switch (ma) {
    case 'tien':
      return tien(v)
    case 'phanTram':
      return phanTram(v)
    default:
      return soNguyen(v)
  }
}

/**
 * Lấy phần GIỜ của mốc thời gian backend gửi ("16:06 21/08/2026" → "16:06").
 *
 * Thanh lọc chỉ đủ chỗ cho "Cập nhật 16:06"; phần ngày tháng vẫn còn nguyên trong tooltip vì
 * số liệu luôn chốt trong ngày, hiện thêm ngày ở đó là thừa.
 */
export function gioNgan(mocThoiGian) {
  return String(mocThoiGian ?? '').split(' ')[0]
}

/**
 * Các kỳ dựng sẵn của thanh lọc.
 *
 * NĂM HỌC (01/9 → 31/8) đứng đầu vì đó là chu kỳ kinh doanh thật của trung tâm.
 *
 * Kỳ thứ hai là năm học TỚI chứ không phải năm học liền trước. Phân công của học kỳ sau được
 * lập từ trước khi năm học bắt đầu, nên năm tới đã có buổi dạy để xem và để đối chiếu; còn
 * năm liền trước thì lùi quá xa mốc dữ liệu đầu tiên của hệ thống — chọn vào chỉ ra một màn
 * hình trắng, và màn hình trắng thì không phân biệt được với hệ thống hỏng.
 */
export function cacKyDungSan(homNay = new Date()) {
  const p = (n) => String(n).padStart(2, '0')
  const iso = (d) => `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
  const namHocBatDau = homNay.getMonth() + 1 >= 9 ? homNay.getFullYear() : homNay.getFullYear() - 1

  const namHoc = (nam) => ({
    ma: `nh${nam}`,
    nhan: `Năm học ${nam}–${nam + 1}`,
    from: `${nam}-09-01`,
    to: `${nam + 1}-08-31`,
  })

  const dauThang = new Date(homNay.getFullYear(), homNay.getMonth(), 1)
  const cuoiThang = new Date(homNay.getFullYear(), homNay.getMonth() + 1, 0)
  const quy = Math.floor(homNay.getMonth() / 3)
  const dauQuy = new Date(homNay.getFullYear(), quy * 3, 1)
  const cuoiQuy = new Date(homNay.getFullYear(), quy * 3 + 3, 0)
  const thu = (homNay.getDay() + 6) % 7 // 0 = Thứ Hai
  const dauTuan = new Date(homNay)
  dauTuan.setDate(homNay.getDate() - thu)
  const cuoiTuan = new Date(dauTuan)
  cuoiTuan.setDate(dauTuan.getDate() + 6)

  return [
    namHoc(namHocBatDau),
    namHoc(namHocBatDau + 1),
    { ma: 'thang', nhan: 'Tháng này', from: iso(dauThang), to: iso(cuoiThang) },
    { ma: 'quy', nhan: 'Quý này', from: iso(dauQuy), to: iso(cuoiQuy) },
    { ma: 'tuan', nhan: 'Tuần này', from: iso(dauTuan), to: iso(cuoiTuan) },
    { ma: 'homnay', nhan: 'Hôm nay', from: iso(homNay), to: iso(homNay) },
  ]
}
