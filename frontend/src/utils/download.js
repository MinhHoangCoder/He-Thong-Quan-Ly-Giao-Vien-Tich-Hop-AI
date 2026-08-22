/**
 * Tải file do SERVER dựng (Excel, PDF…) qua đúng lớp axios đang dùng.
 *
 * Vì sao không dùng thẻ `<a href="/api/...">`: token nằm trong header Authorization, mà trình
 * duyệt không gắn header vào một cú điều hướng thường — link sẽ trả 401. Phải gọi bằng axios
 * rồi tự dựng blob.
 *
 * Tên file lấy từ header `Content-Disposition` của server, ưu tiên `filename*` (RFC 5987) vì
 * chỉ dạng đó mang được tiếng Việt có dấu.
 */
import http from '@/api/http'

/** Bóc tên file từ Content-Disposition; không có thì dùng tên dự phòng. */
function tenFileTu(header, duPhong) {
  if (!header) return duPhong
  const rfc = /filename\*=UTF-8''([^;]+)/i.exec(header)
  if (rfc) {
    try {
      return decodeURIComponent(rfc[1])
    } catch {
      /* header hỏng — rơi xuống nhánh dưới */
    }
  }
  const thuong = /filename="?([^";]+)"?/i.exec(header)
  return thuong ? thuong[1] : duPhong
}

/**
 * @param url  đường dẫn API (đã bỏ tiền tố /api/v1, giống mọi lời gọi khác)
 * @param params tham số query
 * @param tenDuPhong tên file dùng khi server không gửi Content-Disposition
 */
export async function taiFile(url, params, tenDuPhong) {
  const res = await http.get(url, { params, responseType: 'blob' })
  const ten = tenFileTu(res.headers['content-disposition'], tenDuPhong)
  const href = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = href
  a.download = ten
  a.click()
  URL.revokeObjectURL(href)
}

/**
 * Lỗi của một request `responseType: 'blob'` cũng về dưới dạng blob, nên `e.response.data.message`
 * là `undefined` — thông báo thật nằm bên trong blob và phải đọc ra bằng text().
 *
 * Không có hàm này thì mọi lỗi khi tải file đều hiện thành "Tải file thất bại", kể cả khi
 * server đã nói rõ lý do ("khoảng đang chọn có 55.000 dòng, vượt mức…").
 */
export async function loiTaiFile(e, macDinh = 'Tải file thất bại') {
  const data = e?.response?.data
  if (data instanceof Blob) {
    try {
      return JSON.parse(await data.text()).message ?? macDinh
    } catch {
      return macDinh
    }
  }
  return data?.message ?? macDinh
}
