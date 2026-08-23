# Chấm công: tìm kiếm, phân trang server, và cột "Buổi dạy" bị bỏ quên (2026-08-22)

## Nghịch lý phát hiện lúc rà lại: giáo viên thấy nhiều hơn kế toán

Hai hàm cùng đọc bảng `Attendance`, nhưng chỉ một hàm ghép thêm thông tin buổi dạy:

```java
listMine(...)  // của giáo viên  → có gọi enrichWithSchedule() → Trường · Lớp · Môn · Tiết
list(...)      // của admin      → KHÔNG gọi
```

Hệ quả: kế toán mở bảng chấm công thấy đúng `Nguyễn Văn A — 07:00 — Có mặt`, không biết buổi
đó dạy ở đâu, lớp nào. Còn chính giáo viên bị chấm thì thấy đủ.

Hàm `enrichWithSchedule()` đã có sẵn và làm đúng việc — `list()` chỉ là quên gọi. Sửa bằng
cách gọi lại đúng hàm đó, không viết code mới.

## Phân trang: đổi sang server, và cái giá phải trả

Bản cũ tải HẾT một tháng rồi mới cắt 10 dòng ở trình duyệt. Với 101 giáo viên đó là hơn nghìn
dòng JSON mỗi lần mở trang, để hiện đúng 10 dòng đầu.

Đổi sang `Page<AttendanceResponse>` (`AttendanceRepository.search`). Nhưng phân trang kéo theo
hai thứ **bắt buộc phải đổi theo**, và đây mới là phần dễ làm sai:

### 1. Ô tìm và bộ lọc phải chạy ở server

Lọc ở client trên dữ liệu đã cắt trang thì chỉ tìm được trong 10 dòng đang xem — tệ hơn không
có ô tìm, vì nó im lặng trả về "không thấy" cho một người rõ ràng có trong danh sách.

Từ khóa tìm theo **tên giáo viên**, join thẳng sang `Teacher`. Cố ý KHÔNG tìm theo tên
trường/lớp: hai thứ đó phải đi vòng `Attendance → Schedule → AssignmentSlot → SchoolClass`,
một câu join bốn tầng chỉ để phục vụ ô tìm — trong khi trường và lớp đã có dropdown lọc riêng.

### 2. Ba thẻ tổng quan phải có endpoint riêng

`Tổng dòng` / `Buổi có công` / `Tổng giờ dạy` trước đây cộng dồn `rows.value` ở frontend. Sau
khi phân trang, cộng dồn đó chỉ ra tổng của 10 dòng đang hiện — một con số vô nghĩa nằm ngay
dưới cái nhãn "Tổng giờ dạy". Đây là kiểu lỗi không ai báo vì màn hình vẫn hiện số.

Thêm `GET /attendance/summary` dùng **đúng bộ tham số lọc** của `list()`, kể cả bước ép
`teacherId` chống IDOR — hai câu nói khác nhau thì bảng và thẻ tổng sẽ mâu thuẫn ngay trên cùng
một màn hình.

Câu này phải viết **SQL thuần** chứ không JPQL: số giờ không có trong bảng, nó được tính từ
`CheckIn`/`CheckOut` (`AttendanceResponse.computeHours`), mà JPQL không có hàm trừ hai mốc giờ.

```sql
COALESCE(SUM(CASE WHEN a.CheckIn IS NOT NULL AND a.CheckOut > a.CheckIn
                  THEN DATEDIFF(MINUTE, a.CheckIn, a.CheckOut) ELSE 0 END), 0) / 60.0
```

## Vì sao Chấm công phân trang ở server mà Lịch dạy thì không

Câu này đáng chuẩn bị vì nhìn qua thì có vẻ không nhất quán:

| | Chấm công | Lịch dạy | Bảng lương |
|---|---|---|---|
| Phân trang | server | không phân trang | client |
| Ô tìm | server | client | client |

- **Chấm công** là BẢNG thật, một tháng hơn nghìn dòng → phân trang server đúng bài.
- **Lịch dạy** không phân trang được: một cái lịch tháng phải vẽ đủ 42 ô, thiếu ngày nào là
  thủng lưới. Dữ liệu vốn đã phải tải trọn khoảng → lọc tại chỗ là rẻ nhất.
- **Bảng lương** một kỳ chỉ có tối đa vài chục dòng (mỗi giáo viên một dòng), và dòng "Tổng
  thực nhận" ở chân bảng phải cộng CẢ KỲ. Tải trọn rồi lọc tại chỗ vừa đúng số vừa không độ trễ.

Ba màn hình khác nhau vì bản chất dữ liệu khác nhau, không phải vì làm ẩu.

## Giáo viên nghỉ ốm bị đánh y hệt người bỏ dạy

`AttendanceSweepService` ghi VẮNG cho mọi buổi hết giờ mà không ai check-in. Nó không có cách
nào biết giáo viên nằm viện hay bỏ dạy — cả hai đều mất tiền buổi đó và tụt chuyên cần như nhau.

Không dựng module "Đơn xin nghỉ" riêng. Luồng **Yêu cầu bổ sung** (`AttendanceAmendService`) đã
làm đúng việc cần: giáo viên gửi yêu cầu kèm lý do, admin duyệt. Và backend **đã nhận sẵn**
`status` override khi duyệt (`AttendanceAmendReviewRequest.status` cho phép `PRESENT|LATE|LEAVE`)
— chỉ thiếu đường bấm trên giao diện, vì `approveAmend()` gọi với body rỗng `{}`.

Thêm nút **"Nghỉ phép"** bên cạnh **"Ghi công"**. Nghỉ phép KHÔNG được tính tiết nên số tiền
giữ nguyên — thao tác này chỉ làm sạch hồ sơ chuyên cần, không đụng tới lương.

## Còn lại

Ô tìm hiện chỉ theo tên giáo viên. Muốn tìm theo tên trường/lớp thì phải viết câu join bốn tầng
— để lại khi có nhu cầu thật, vì hai thứ đó đã có dropdown lọc.
