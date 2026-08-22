# Lịch dạy: tìm kiếm hai tầng + tô ngày nghỉ · Lịch nghỉ: thùng rác (2026-08-22)

## Phần 1 — Lịch nghỉ: chức năng đúng nhưng không ai hiểu nó làm gì

Câu hỏi đặt ra khi rà lại: "kỳ nghỉ để làm gì, không có thì sao?"

Trả lời: `AssignmentService.generateSchedules` hỏi bảng `Holiday` mỗi lần trải một ô thời khóa
biểu (V29). Thiếu một ngày ở đó thì lịch đẻ ra buổi dạy vào hôm trường đóng cửa →
`AttendanceSweepService` quét buổi đã qua không ai chấm và **tự ghi VẮNG** →
`PayrollService` chỉ trả tiền cho `PRESENT`/`LATE`. Giáo viên mất tiền và tụt chuyên cần vì
một buổi dạy chưa từng tồn tại, mà không ai bấm nút nào sai.

Đo được trên chính DB này: **497 buổi** đã bị chặn không sinh ra nhờ bảng `Holiday`.

Việc bản thân câu hỏi này được đặt ra chính là bằng chứng giao diện chưa nói được nó làm gì —
nên thêm một đoạn mô tả ngay đầu trang thay vì chỉ để cái tiêu đề "Lịch nghỉ".

### Thùng rác

`HolidayService.delete()` đã xóa mềm từ đầu, nhưng frontend không có màn hình nào xem lại →
xóa nhầm kỳ nghỉ Tết là phải vào SQL mới lấy lại được. Thêm tab "Thùng rác" +
`GET /trash` + `POST /{id}/restore`.

### Xóa kỳ nghỉ: cảnh báo, KHÔNG chặn

Trường/Lớp/Giáo viên đều theo luật "còn dữ liệu con thì cấm xóa" (`DeleteGuard`). Kỳ nghỉ cố ý
KHÔNG theo luật đó, vì loại kỳ nghỉ để lại nhiều hậu quả nhất — gõ nhầm năm, 2027 thành 2026 —
cũng chính là loại cần xóa gấp nhất. Chặn cứng ở đây là tự nhốt mình.

Đổi lại phải kể đủ những gì KHÔNG hoàn lại được (`GET /{id}/delete-impact`):

- số buổi dạy ĐÃ HỦY trong khoảng ngày — xóa kỳ nghỉ không dựng chúng dậy;
- số dòng chấm công đang là NGHỈ PHÉP — giữ nguyên;
- số buổi CHƯA diễn ra — những buổi này sẽ chạy lại bình thường.

Con số đầu là **ước lượng**, và giao diện nói rõ như vậy: buổi bị hủy không lưu lại nó bị hủy
vì kỳ nghỉ nào. Đủ để người dùng biết thao tác đang đụng tới cái gì.

## Phần 2 — Lịch dạy

### Tìm kiếm có HAI tầng, cố ý

| Tầng | Cái gì | Chạy ở đâu |
|---|---|---|
| Dropdown Giáo viên / Trường / Lớp | đổi hẳn tập dữ liệu | gọi lại server |
| Ô "Tìm nhanh" | tỉa trong khoảng đang xem | lọc tại chỗ |

Ô tìm nhanh lọc ở client vì **lịch tháng vốn đã phải tải trọn khoảng ngày** — không thể phân
trang một cái lịch. Dữ liệu đã nằm sẵn trong tay, gọi server thêm một vòng chỉ làm chậm và nhấp
nháy. Đây cũng là điểm khác Chấm công (phân trang ở server): khác nhau vì bản chất hai màn hình
khác nhau, không phải vì làm ẩu.

Ba dropdown đổi sang `SearchSelect` — component dự án đã có sẵn, đang dùng ở Dashboard. Với 101
giáo viên thì thẻ `<select>` chỉ nhảy theo ký tự đầu: gõ "an" không ra "Nguyễn Văn An".

### Tô ngày nghỉ

Khai kỳ nghỉ xong, ngày 2/9 trên lịch trông y hệt một ngày bị quên xếp lịch — cùng là ô trống.
Hai thứ đó cần hai hành động khác hẳn nhau. Thêm `GET /schedules/holidays` trả các khoảng nghỉ
chạm vào khoảng đang xem; frontend trải khoảng ra từng ngày và tô kèm tên kỳ nghỉ.

### Lọc trạng thái

Trước đây lịch chỉ hiện buổi `APPROVED`, nên buổi `PENDING` là vô hình: điều phối viên muốn biết
"tuần sau còn bao nhiêu buổi chưa ai xác nhận" phải mở sang trang Phân công đếm tay. Thêm bộ lọc,
**mặc định vẫn chỉ APPROVED** — lịch là thứ đã chốt, không đổi thói quen của người đang dùng.

### In + xuất CSV

Trường thật vẫn cần bản TKB in ra dán phòng hội đồng. Không thêm thư viện nào:

- **In** — một khối `@media print` trong `page-common.css` (khai ở đó chứ không trong style
  scoped của trang, vì sidebar và topbar thuộc `PortalShell`, style scoped của trang con không
  với tới). Trang nào cần in thì gắn `.no-print` lên phần điều khiển của mình.
- **CSV** — tự ghép chuỗi, 15 dòng. Bẫy: **phải có BOM ở đầu file**, thiếu nó thì Excel đọc theo
  bảng mã hệ thống và mọi tên tiếng Việt thành ký tự lạ.

### Hiệu năng

`ScheduleService.classesOf()` và `filterOptions()` gọi `findAll()` rồi lọc trong Java — nạp cả
gần 400 lớp / 30 trường về bộ nhớ mỗi lần người dùng đổi dropdown, để lấy ra hơn chục dòng.
Đổi sang query đúng điều kiện (`findBySchoolIdAndDeletedFalseOrderByName`).

## Kiểm thử

`HolidayTrashTest` (4 ca): xóa chỉ gắn cờ; khôi phục về lại danh sách chính; khôi phục kỳ đang
dùng thì báo 404 nói rõ "trong thùng rác" (bấm hai lần liên tiếp là ra tình huống này); cảnh báo
xóa đếm đúng ba con số, và chỉ đếm buổi TƯƠNG LAI vào ô "sẽ chạy lại".
