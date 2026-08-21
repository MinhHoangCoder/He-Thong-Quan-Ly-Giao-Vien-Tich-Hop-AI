# Bảng điều khiển: chỉ tải chiều đang xem, và giữ số liệu qua các lần chuyển trang (2026-08-22)

Tiếp `2026-08-21-fullstack-bang-dieu-khien-tinh-gon.md`. Bản đó ghi mục "còn nợ": *`/analytics`
2,3 giây là chậm*. Bài này trả món nợ đó, cộng thêm một than phiền dùng thật: **chuyển sang màn
khác rồi quay lại là Dashboard tải lại từ đầu**.

## Phần 1 — Đo trước, sửa sau

Giả thuyết đầu tiên ai cũng nghĩ tới: 27.484 dòng `Schedule`, chắc thiếu index. Bảng `Schedule`
đúng là **không có index nào dẫn đầu bằng `StartTime`** (các index hiện có dẫn đầu bằng
`TeacherId` / `RoomId` / `AssignmentId`), trong khi bộ lọc chính của dashboard lại là khoảng
`StartTime`.

Nghe rất thuyết phục. Nhưng đo thẳng bằng `sqlcmd` thì:

| Phép đo | Thời gian |
|---|---|
| Chi phí kết nối `sqlcmd` (SELECT 1) | 0,106s |
| **Một truy vấn `phanTich` đầy đủ** (7 bảng JOIN, GROUP BY, 27k dòng) | **0,070 – 0,086s** |
| Thêm cả ba điều kiện `(@p IS NULL OR cot = @p)` | 0,082 – 0,099s |

Truy vấn nặng nhất chạy **nhanh hơn cả chi phí mở kết nối**. Nút thắt **không nằm ở database**.
Thêm index sẽ không cải thiện được gì đo lường nổi.

> Bài học lặp lại lần thứ hai trong dự án này: giả thuyết nghe hợp lý ≠ nguyên nhân. Lần trước là
> "7 ngày tới gần như không bao giờ rỗng" (sai), lần này là "chậm vì thiếu index" (cũng sai).
> **Chạy phép đo ra trước khi sửa.**

Chỗ thời gian thật sự đi: `/analytics` gọi **7 truy vấn nối đuôi nhau**, và **5 trong số đó chỉ
để dựng bảng "Thống kê chi tiết"** — `phanTich` × 3 chiều + `diemDanhGiaTheo` × 2. Mà giao diện
chỉ hiện **một tab** tại một thời điểm.

## Phần 2 — Tách `/breakdown`

```
GET /dashboard/analytics            -> chỉ 2 biểu đồ (theoThang, coCauNhomMon)
GET /dashboard/breakdown?chieu=...  -> bảng chi tiết MỘT chiều
```

`DongPhanTich` vốn là record lồng trong `DashboardAnalyticsResponse`; nó không còn thuộc về
analytics nữa nên tách thành DTO riêng `DashboardBreakdownRow`.

Đo lại, 3 lượt mỗi endpoint:

| Endpoint | Trước | Sau |
|---|---|---|
| `/summary` | 0,19 – 0,27s | 0,22 – 0,39s |
| `/analytics` | **2,17 – 2,51s** | **0,47 – 0,52s** |
| `/operations` | 0,30 – 0,54s | 0,31 – 0,40s |
| `/breakdown` | — | 0,59 – 0,63s |

Bốn endpoint chạy song song nên thời gian chờ = truy vấn chậm nhất, không phải tổng.

## Phần 3 — Vì sao quay lại màn là tải lại từ đầu

`App.vue` render `<RouterView/>` **trần, không có `<KeepAlive>`**. Rời trang là Vue huỷ
component; quay lại là dựng mới → `onMounted` chạy lại → gọi `/filters` + toàn bộ endpoint. Không
có bộ nhớ đệm nào ở giữa.

Hai hướng chữa, đã cân nhắc cả hai:

| | `<KeepAlive>` | Pinia store |
|---|---|---|
| Giữ trạng thái giao diện | Tự động, kể cả vị trí cuộn | Phải tự lưu |
| Phạm vi ảnh hưởng | **Mọi trang khu admin** | Chỉ Dashboard |
| Kiểm soát lúc làm mới | Khó — component không chạy lại | Rõ ràng, gọi `nap()` khi nào muốn |

Chọn **store** (`stores/dashboard.js`) vì khoanh vùng được. `<KeepAlive>` áp cho cả khu admin
nghĩa là mọi trang khác cũng thành "không tự làm mới" — một thay đổi hành vi rộng hơn nhiều so
với vấn đề đang giải.

### Hiện số cũ, tải ngầm bên dưới

Quay lại trang là thấy nội dung **ngay**, đồng thời một lượt gọi chạy ngầm và thay số khi về.
Không bao giờ phải nhìn màn hình trắng, cũng không bao giờ xem phải số cũ mà không biết.

### `lechLoc` — hai tình huống nhìn giống nhau, xử lý ngược nhau

Cả hai đều là "đang tải mà trên màn đã có số":

- **Quay lại trang, bộ lọc y nguyên** → số cũ vẫn đúng. Cứ hiện, tải ngầm, **không làm mờ** —
  làm mờ ở đây chỉ khiến màn hình chớp một cái vô cớ.
- **Vừa bấm Áp dụng** → số trên màn là của bộ lọc **cũ**. Phải làm mờ để người dùng biết nó sắp đổi.

Phân biệt bằng hai khoá: `khoaYeuCau` (bộ lọc của lượt đang tải) và `khoaDuLieu` (bộ lọc đã sinh
ra số đang hiển thị, chỉ cập nhật khi `/summary` về).

### Khoá bộ lọc cho bộ đệm bảng chi tiết

Bảng chi tiết đệm theo chiều: `{ GIAO_VIEN: [...], TRUONG: [...] }`. Đổi kỳ hay đổi trường là
**xoá sạch bộ đệm đó**. Giữ lại thì màn hình ghép số của hai bộ lọc khác nhau — một lỗi rất khó
nhận ra vì con số nào trông cũng hợp lý.

### Không dùng được `useLatestRequest` ở store

Composable đó gắn `onBeforeUnmount` để tự vô hiệu hoá; store không có vòng đời component để gắn
vào. Store tự đếm lượt, **mỗi khối một bộ đếm riêng**.

## Phần 4 — Hai cái bẫy khi khôi phục vị trí cuộn

**Bẫy 1 — ngắm nhầm phần tử.** Tưởng `.main` của `PortalShell` là khung cuộn. Đo ra:

```
.main          : scrollHeight=2582  clientHeight=2582  overflowY=visible   <- KHÔNG cuộn
documentElement: scrollHeight=2582  clientHeight=900                        <- cuộn ở đây
```

`.main` cao đúng bằng nội dung, **cửa sổ** mới là thứ cuộn. Đặt `.main.scrollTop = 900` không có
tác dụng gì, và test vẫn "xanh" theo nghĩa không nổ lỗi — chỉ là vị trí cuộn im lìm ở 0.

**Bẫy 2 — `scroll-behavior: smooth`.** `main.css` đặt:

```css
@media (prefers-reduced-motion: no-preference) {
  html { scroll-behavior: smooth; }
}
```

Nên một lời gọi cuộn thường sẽ **chạy hoạt ảnh**: vừa vào trang đã thấy nội dung tự trôi xuống,
trông như lỗi. Khôi phục vị trí thì phải nhảy thẳng:

```js
window.scrollTo({ top: kho.viTriCuon, behavior: 'instant' })
```

Cùng lý do đó, `window.scrollTo(0, 700)` rồi đọc `window.scrollY` ngay sau sẽ trả về `0` — hoạt
ảnh chưa chạy xong. Viết test cuộn trên trang này phải nhớ điều đó.

## Phần 5 — Kết quả đo trên trình duyệt

| | Trước | Sau |
|---|---|---|
| Lần đầu — thẻ chỉ số | 890ms | 950ms |
| Lần đầu — biểu đồ + bảng | **2766ms** | **1066ms** |
| **Quay lại màn** | ~1000ms + màn chờ | **96ms**, đầy đủ biểu đồ + bảng |
| Đổi tab bảng chi tiết | (đã có sẵn cả 3) | 1 API; chiều đã xem lấy từ đệm |

Vị trí cuộn 900 → 900. Tab nhớ đúng. Không lỗi console.

## Còn nợ

- `charts/LineChart.vue` và `charts/MiniBars.vue` không trang nào import.
- `admin.routes.js` khai trùng ba route bài giảng.
- F5 bất kỳ trang nào cũng để lại hai dòng 401 trong console (token hết hạn → interceptor refresh
  rồi phát lại; màn hình vẫn đúng số). Đúng cơ chế đã thiết kế ở `api/http.js`, nhưng console đỏ
  thì khó giải thích khi demo.
- Bộ đệm hiện sống theo **phiên trình duyệt**; F5 là mất. Nếu muốn giữ qua F5 thì phải đẩy xuống
  `sessionStorage` — chưa làm vì chưa có nhu cầu thật.
