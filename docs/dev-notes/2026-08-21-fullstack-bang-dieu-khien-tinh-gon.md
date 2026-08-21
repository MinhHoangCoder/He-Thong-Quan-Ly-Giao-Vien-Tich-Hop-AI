# Tinh gọn Bảng điều khiển: bỏ khối không dẫn tới hành động, tải độc lập, lọc có tìm kiếm (2026-08-21)

Tiếp ngay sau `2026-08-21-fullstack-bang-dieu-khien-thong-ke.md`. Bản đó làm cho màn hình **đúng
số**; bản này làm cho nó **dùng được**.

## Nguyên tắc chọn cái gì ở lại

Một khối được giữ khi trả lời được câu: *nhìn xong thì làm gì tiếp?* Khối chỉ để ngắm thì bỏ.

| Khối | Quyết định | Lý do |
|---|---|---|
| Mật độ dạy theo thứ × tiết | **Bỏ** | Nhìn ra "thứ Ba tiết 3 đông" rồi cũng không làm gì được trên màn này. Lại tốn riêng một `JOIN Period` + `GROUP BY`. |
| 10 trường nhiều buổi dạy nhất | **Bỏ** | Bảng chi tiết tab "Theo trường" đã sắp giảm dần theo số buổi — vẽ lại lần nữa là trùng lặp, không thêm thông tin. |
| Thẻ "Tổng giờ giảng" | **Bỏ thẻ, giữ cột** | Tổng giờ toàn trung tâm không dẫn tới việc gì; giờ giảng **của từng giáo viên** trong bảng chi tiết thì dùng để đối chiếu lương. |
| Lịch dạy hôm nay | **Đổi** thành "Buổi dạy sắp tới" | Xem phần 3. |

Kết quả: `/analytics` bớt hai truy vấn gom nhóm.

## Phần 1 — Hai thẻ không phụ thuộc kỳ, và cái bẫy đặt tên

"Giáo viên có lịch dạy" → **"Giáo viên đang làm việc"**, "Trường đang phục vụ" → **"Trường còn
hợp đồng"**.

**Bẫy đã suýt dính lại.** Dev-note trước ghi rõ: bản dashboard *cũ* có thẻ tên là "Tổng giáo
viên" nhưng thực ra đếm giáo viên `ACTIVE` (90) chứ không phải tổng (100) — và đó được liệt kê
là **lỗi**. Nếu lần này đặt nhãn "Tổng số giáo viên" mà vẫn gọi `demGiaoVienHoatDong()` thì
chính là tái tạo đúng con bug đó. Nhãn phải nói đúng cái đang đếm: **"Giáo viên đang làm việc"**.

Cùng lý do, "Trường còn hợp đồng" đếm theo `ServiceContract.Status = 'ACTIVE'` (18) chứ không
theo bảng `School` (30) — **và ô lọc Trường cũng chỉ liệt kê đúng 18 trường đó**. Hai chỗ khớp
nhau; nếu thẻ ghi 30 mà dropdown chỉ có 18 thì người xem sẽ đi tìm 12 trường còn lại.

### Vì sao `thayDoi = null` cho hai thẻ này

Hai con số này đếm trên toàn hệ thống, **không đọc từ số liệu kỳ**. Đem tổng giáo viên của kỳ
này so với kỳ trước thì lúc nào cũng ra `0%` — đúng về số học, vô nghĩa về ý nghĩa, và một chip
"0,0%" đứng im cạnh bốn chip đang nhảy thì nhìn hệt như thẻ tính hỏng.

Trả `null` ⇒ giao diện **giấu hẳn** chip, và dòng phụ ghi *"Toàn hệ thống — không theo kỳ"*. Đây
là chỗ dễ bị hỏi nhất khi demo: đổi bộ lọc mà hai thẻ đứng yên là **cố ý**, không phải hỏng.

Đối chiếu thật (`?schoolId=2`):

```
             không lọc        lọc TH Dư Hàng
buoiDay      11.557       →   825
chiPhi       1.446.125.000 →  98.555.000
giaoVien     90           →   90     ← không đổi, có chú thích
truong       18           →   18     ← không đổi, có chú thích
```

Luật này đã khoá bằng test `theKhongPhuThuocKy_khongBaoGioCoMuiTenPhanTram`.

## Phần 2 — Bất biến chi phí vẫn nguyên vẹn

Truy vấn `thongKeKy` bị cắt ba cột (`gioGiang`, `gvCoLich`, `truongCoLich`). Sau khi cắt, kiểm
tra lại bất biến của dev-note trước:

> tổng chi phí trên Bảng điều khiển (bỏ hết bộ lọc, đúng kỳ) phải khớp **đến từng đồng** với
> `SELECT SUM(NetAmount) FROM Payroll`

`1.446.125.000đ` — khớp. **Chạy lại phép so này sau bất kỳ thay đổi nào chạm vào `CHI_PHI` hoặc
khối `FROM`.**

## Phần 3 — "Buổi dạy 7 ngày tới" và bài học về việc kiểm chứng giả định

Yêu cầu ban đầu là bỏ khối "Buổi dạy gần nhất" (cơ chế: hôm nay trống thì nhảy sang ngày dạy kế
tiếp). Phương án chọn là thay bằng **"Buổi dạy 7 ngày tới"**, với lập luận *"khối này gần như
không bao giờ rỗng"*.

**Lập luận đó sai.** Đo bằng dữ liệu thật:

```
buổi dạy cuối cùng của năm học 2025–2026 : 29/05/2026
hôm nay                                  : 21/08/2026
buổi dạy kế tiếp                         : 07/09/2026
→ truy vấn "7 ngày tới" trả về 0 dòng
```

Giữa hai năm học là **hơn ba tháng nghỉ hè**. Chính cơ chế "nhảy sang buổi gần nhất" bị yêu cầu
bỏ là thứ đang che lỗ hổng này.

Cách chữa: bỏ luôn trần thời gian — lấy **10 buổi kế tiếp**, gọi là **"Buổi dạy sắp tới"**. Vẫn
bỏ hẳn khái niệm "gần nhất" (nhìn về quá khứ), khối chỉ nhìn về tương lai, và không bao giờ rỗng
trừ khi hết sạch lịch.

> Bài học: một khối "gần như không bao giờ rỗng" thì phải **chạy truy vấn ra mà xem**, đừng suy
> luận từ cảm giác về dữ liệu.

### Khối này CÓ chịu bộ lọc trường/nhóm môn

`WHERE_LOC` cũ gộp cả khoảng kỳ lẫn phạm vi. Đã tách `LOC_PHAM_VI` ra riêng để bảng lịch dùng
được phần phạm vi mà không bị chặn giới hạn trên. Lọc "THCS Chu Văn An" mà bảng lịch vẫn liệt kê
trường khác thì nhìn như hỏng — cùng đúng cái lý do đã xử lý ở Phần 1.

## Phần 4 — Ba khối tải độc lập (sửa một mâu thuẫn có sẵn)

Comment ở `DashboardController` và `api/dashboard.js` đều tuyên bố: *"tách ba endpoint để thẻ chỉ
số hiện ngay, không phải chờ truy vấn chậm nhất"*. Nhưng frontend gom cả ba vào một
`Promise.allSettled` **rồi mới gán state** — tức là vẫn chờ truy vấn chậm nhất. Code làm ngược
lại điều comment nói, và vô hiệu hoá đúng cái lý do người ta tách ba endpoint.

Độ trễ đo trên máy (3 lượt):

| Endpoint | Thời gian |
|---|---|
| `/summary` | 0,19 – 0,27s |
| `/operations` | 0,30 – 0,54s |
| `/analytics` | **2,17 – 2,51s** |

Sau khi tách ra gán riêng: thẻ chỉ số hiện sau **0,89s**, biểu đồ sau **2,77s** (trước là cả hai
cùng ~3,2s).

Mỗi khối một **bộ đếm `useLatestRequest` riêng** — composable này đã có sẵn trong dự án nhưng
Dashboard chưa dùng, nên đổi bộ lọc nhanh hai lần thì lượt cũ về muộn ghi đè lượt mới, màn hình
hiện số của bộ lọc trước mà không ai tái hiện được.

**Hệ quả phải xử lý kèm:** khi `/analytics` chưa về, khối biểu đồ phải hiện vòng xoay, **không
được in "Kỳ này chưa có buổi dạy nào"** — đó là một câu khẳng định SAI, đặt ngay cạnh cái thẻ ghi
11.557 buổi. Đây là lỗi lộ ra ngay ở ảnh chụp đầu tiên sau khi tách.

## Phần 5 — Bộ lọc

- **Tìm kiếm:** dùng lại `ui/SearchSelect.vue` (đang dùng ở trang Phân công) thay cho `<select>`.
  Nó lọc trên toàn chuỗi và **bỏ dấu tiếng Việt** — gõ `du hang` ra `TH Dư Hàng`. `<select>` chỉ
  nhảy theo ký tự đầu nên với 18 trường là không tìm nổi.
- **Chờ bấm "Áp dụng":** người dùng thường đổi kỳ → đổi trường → đổi nhóm môn. Gọi ngay mỗi lần
  đổi là **9 request** cho một lần lọc, 6 request đầu không ai kịp đọc. Nay còn 3.
- **Bộ lọc lưu lên URL** (`?from=&to=&schoolId=&categoryId=`): F5 không mất, copy link gửi được,
  Back/Forward chạy đúng. Tham số hỏng thì rơi về mặc định chứ không ném lỗi — URL là đầu vào của
  người ngoài, không phải dữ liệu tin được.
- Bỏ danh mục **chi nhánh** khỏi `/filters`: giao diện không có ô lọc đó, `boLoc.branchId` khai
  rồi để `null` vĩnh viễn. Tham số `branchId` vẫn giữ ở backend (có test phủ) cho trường hợp sau
  này mở nhiều chi nhánh.

## Phần 6 — Vòng xoay dùng chung

`ui/LoadingSpinner.vue`: logo KDC **đứng yên** ở giữa, vòng cung gradient cam xoay quanh. Logo là
ảnh chụp chứ không phải icon đối xứng — xoay cả ảnh thì thấy rõ nó lệch và giật mỗi vòng. Vành
làm bằng `conic-gradient` khoét ruột bằng `mask`, không cần ảnh hay thư viện.

Ba điểm phải nhớ:

- **Tôn trọng "giảm hiệu ứng".** `main.css` ép `animation-duration: 0.001ms !important` cho mọi
  phần tử khi bật `data-motion="reduced"`. Để vòng xoay chạy tiếp thì nó **nhảy loạn xạ mỗi khung
  hình** — khó chịu hơn hẳn lúc chưa giảm. Nên đặt `animation-name: none` để **dừng hẳn**, bù lại
  bằng chữ "Đang tải…" vốn mới là thứ mang thông tin.
- **Lớp phủ không căn giữa theo chiều dọc.** Vùng nội dung của dashboard cao ~2.500px; căn giữa
  là đẩy vòng xoay xuống quá màn hình đầu tiên, người dùng chỉ thấy trang mờ đi mà không hiểu vì
  sao. Ghim gần đỉnh (`padding-top: clamp(2rem, 16vh, 10rem)`).
- Vòng xoay này trước nằm `scoped` trong `TeacherListPage.vue` (4 chỗ dùng) nên không trang nào
  khác dùng lại được. Đã gỡ ra dùng chung; **các trang còn lại vẫn đang in chữ "Đang tải…" — còn
  40 chỗ trong 25 file**, đổi dần khi đụng tới từng trang.

## Phần 7 — Bố cục

Lưới 12 cột, `align-items: stretch` để hai khối cùng hàng cao bằng nhau. Đo thật: **362 vs 362**
và **564 vs 564** px — không hở đáy. Không tràn ngang ở cả 1440px lẫn 900px.

Thanh lọc: `minmax(148px, 1fr)` là bề rộng nhỏ nhất còn đọc trọn "Tất cả nhóm môn". Để rộng hơn
(170px) thì ở màn 1440px ô thứ năm bị đẩy xuống hàng hai và **hở một mảng trống to bên phải hàng
một** — đúng thứ cần tránh.

Padding/gap giảm ~25% nhưng **giữ nguyên cỡ chữ**: app có chế độ "cỡ chữ lớn"
(`data-fontsize="large"`) trong Cài đặt, thu nhỏ font sẽ vỡ với người bật nó.

## Phần 8 — Bấm logo về trang chủ

`PortalShell` dùng chung cho **cả quản trị lẫn giáo viên**, nên logo trỏ tới `roleHome()` chứ
không gắn cứng `/dashboard` — giáo viên bấm vào `/dashboard` sẽ bị route guard đá ngược ra, nhìn
hệt như logo bị hỏng. `roleHome()` đã có sẵn và đang dùng cho luồng đăng nhập, tái dùng để không
phát sinh luật điều hướng thứ hai có thể lệch nhau.

## Phần 9 — Test

`DashboardKpiTest`: 13 → **14 test**. Hai test mới khoá luật ở Phần 1:

- `theGiaoVienVaTruongDemToanHeThong_khongLayTuSoLieuKy` — số liệu kỳ trong test đặt khác hẳn
  90/18, nên nếu thẻ đọc nhầm nguồn là test đỏ ngay.
- `theKhongPhuThuocKy_khongBaoGioCoMuiTenPhanTram` — hai kỳ khác hẳn nhau: thẻ theo kỳ **phải**
  có `%`, hai thẻ toàn hệ thống **tuyệt đối không**.

Toàn bộ: 277/277 xanh.

## Còn nợ

- `/analytics` **2,3 giây** là chậm. Nó chạy 7 truy vấn tuần tự (3 chiều phân tích × `phanTich`,
  2 × `diemDanhGiaTheo`, `theoThang`, `coCauNhomMon`) trên 13k buổi có `JOIN Payroll`. Hướng xử
  lý: chỉ tải chiều đang xem của bảng chi tiết thay vì cả ba, hoặc thêm index phủ.
- `charts/LineChart.vue` và `charts/MiniBars.vue` **không trang nào import** — mồ côi từ trước
  đợt này, chưa xoá vì nằm ngoài phạm vi.
- `admin.routes.js` khai **trùng** ba route `/admin/lessons`, `/admin/lessons/new`,
  `/admin/lessons/:id/edit` (hai lần mỗi cái).
- F5 bất kỳ trang nào cũng để lại **hai dòng 401 trong console**: access token trong
  `localStorage` đã hết hạn, request đầu 401 rồi interceptor refresh và phát lại. Đúng cơ chế đã
  thiết kế ở `api/http.js`, màn hình vẫn ra đúng số — nhưng console đỏ thì khó giải thích khi
  demo.
