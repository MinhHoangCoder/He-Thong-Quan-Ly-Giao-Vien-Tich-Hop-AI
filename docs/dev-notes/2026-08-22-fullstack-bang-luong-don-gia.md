# Bảng lương: khép vòng đời phiếu + đơn giá ra khỏi code (V38, 2026-08-22)

## Ba lỗi, đều nằm ở bước cuối của dây chuyền tiền lương

### 1. `PAID` là trạng thái CHẾT

Trạng thái "Đã trả" có mặt ở khắp nơi:

- trong `CHECK` constraint của bảng `Payroll`;
- trong `TEACHER_VISIBLE_STATUS` — danh sách trạng thái giáo viên được xem;
- trong `assertReopenable()` — từ chối mở lại phiếu `PAID` với câu "tiền đã ra khỏi quỹ".

Nhưng **không có đường code nào ĐẶT được nó**. Kế toán chi tiền xong không có nút nào để ghi
nhận, nên trên hệ thống "đã chốt" và "đã trả" là một.

Hệ quả nặng hơn việc thiếu một nút: rào chắn *"phiếu đã trả thì không mở lại được"* là rào duy
nhất bảo vệ sổ sách sau khi tiền ra khỏi quỹ — mà rào đó **chưa bao giờ chạy**, vì không phiếu
nào tới được trạng thái `PAID`.

Thêm `POST /{id}/pay` và `/pay-period`, quyền RIÊNG `PAYROLL_PAY`. Không gộp vào
`PAYROLL_MANAGE`, cùng lý lẽ với `PAYROLL_REOPEN` ở V32: "tính lại lương" và "xác nhận tiền đã
ra khỏi quỹ" là hai trách nhiệm khác nhau.

Chỉ đi được từ `FINALIZED`. Nhảy thẳng `DRAFT → PAID` là bỏ qua bước chốt — mà bước chốt mới là
chỗ có cảnh báo ngày nghỉ và khóa chấm công.

### 2. Đơn giá hard-code, và sửa code cũng vẫn sai

115.000đ (khối 1–5) / 125.000đ (khối 6–9) là hằng số Java. Tăng giá = sửa code, build, deploy —
một quyết định kinh doanh bình thường phải đi qua lập trình viên.

Nhưng đó chưa phải phần tệ nhất. **Kể cả sửa code cũng sai**: bảng lương tính lại được bất cứ
lúc nào (`generate()` ghi đè dòng `DRAFT`). Bấm "Tính lại" tháng 7 sau khi tăng giá từ 1/9 sẽ
tính tháng 7 theo giá mới — hằng số không biết gì về thời gian. Số tiền đổi mà không ai đụng vào
chấm công.

Nên bảng `PayRate` lưu theo **khoảng hiệu lực** `[EffectiveFrom, EffectiveTo]`, và
`resolveRate()` tra theo **ngày dạy của từng buổi**, không theo hôm nay.

Tăng giá = đóng dòng cũ rồi thêm dòng mới, **không sửa đè**. Sửa đè là xóa lịch sử giá: mọi phiếu
lương cũ tính lại sẽ lệch với số đã trả, và không có dấu vết nào giải thích. Vì thế
`PayRateController` cố ý **không có `PUT`**.

Thứ tự tra giá:

```
Contract.RatePerPeriod (thương lượng riêng)  →  PayRate (barem chung theo khối + ngày)  →  null
```

Trả `null` chứ không lấy một mức mặc định. Đoán một con số ở đây là ghi tiền sai vào phiếu lương
mà không ai biết; trả `null` thì bên gọi ghi cảnh báo và bỏ qua tiết đó, số tiết trên phiếu lệch
đi và người dùng nhìn thấy có gì đó không ổn.

### 3. Lương cứng: cột có từ V1, chưa ai đọc

`Contract.BaseSalary` tồn tại từ migration đầu tiên nhưng `generate()` không đọc, nên ô "Lương
cứng" trên giao diện luôn bằng 0 trừ khi kế toán gõ tay.

Nay đọc từ hợp đồng, và **chỉ áp cho giáo viên cơ hữu** (`EmploymentType = CO_HUU`). Thỉnh giảng
ăn theo tiết — cộng lương cứng cho họ là trả tiền cho những tháng họ không dạy buổi nào.

## Hiệu năng: cache không bao giờ trúng

`rateForAttendance()` có cache, đánh theo `scheduleId`:

```java
int key = a.getScheduleId().intValue();
BigDecimal cached = cache.get(key);   // không bao giờ trúng
```

Mỗi dòng chấm công có một `scheduleId` RIÊNG (một buổi dạy = một dòng công), nên cache miss
100%. Mỗi lần miss là bốn câu SQL: `Schedule → Assignment → AssignmentSlot → SchoolClass`.
Một tháng ~750 dòng → **~3.000 câu SQL** cho một lần bấm "Tính lương".

Gộp thành MỘT câu JOIN lấy sẵn khối lớp cho cả kỳ (`findPayableWithGrade`). Khối lấy theo lớp
của ô thời khóa biểu sinh ra buổi (`COALESCE(sl.ClassId, asg.ClassId)`) — giống hệt luật ở
`ScheduleService.classIdOf`, vì từ V16 một phiếu trải nhiều lớp mà lớp 5 và lớp 6 khác giá.

**Đo thật trên DB có dữ liệu** (kỳ 5/2026, 82 giáo viên): bấm "Tính lương" mất **3,8 giây**, và
bấm lần hai ra **đúng cùng con số** — điều kiện bắt buộc của một hàm tính lại được.

## Số buổi đi muộn: hiện ra, không tự trừ

`LATE` vẫn được trả đủ tiền tiết (tiết đó vẫn dạy hết nội dung). Nhưng giấu con số đi thì trên
hệ thống người đi muộn 40 phút và người đúng giờ giống hệt nhau.

Thêm cột "Đi muộn", **tính lại mỗi lần đọc**, không thêm cột vào bảng `Payroll`: cột lưu sẵn sẽ
cũ đi mỗi khi chấm công được sửa. Kế toán nhìn thấy rồi tự quyết định điền ô "Khấu trừ" —
giữ luật tính tiền đơn giản mà không giấu thông tin.

## Nút "Lịch sử": chức năng đã viết xong nhưng không có đường vào

`GET /payroll/{id}/logs` và bảng `PayrollChangeLog` có từ V32. Frontend chưa gọi bao giờ — lịch
sử chốt / mở lại / trả lương nằm trong DB mà không màn hình nào nhìn thấy.

## Vì sao KHÔNG đổi tên cột `TaughtHours`

Tên cột gây nhầm thật: `TaughtHours` lưu SỐ TIẾT, `RatePerHour` lưu ĐƠN GIÁ/TIẾT.

Đã cân nhắc đổi tên và **quyết định không**: `NetAmount` là cột COMPUTED của SQL Server dựng
trên chính hai cột đó. Đổi tên phải drop rồi tạo lại cột computed — đúng loại thao tác dự án đã
dẫm bẫy một lần (`sp_rename` không sửa thân trigger, note V11). Đánh đổi không xứng với việc chỉ
để tên đọc xuôi hơn; giao diện đã hiện đúng nhãn "Số tiết".

## Bẫy đã dẫm phải khi làm: checksum V38

Chạy `mvnw test` khởi động Spring context thật → Flyway áp V38 vào DB local. **Sau đó** mới sửa
file V38 (thêm `ALTER TABLE Contract ADD RatePerPeriod`) → checksum đổi → backend chết lúc khởi
động với `Migration checksum mismatch for migration version 37`.

Cách chữa (giống note 2026-08-21, nhưng nguyên nhân khác — lần đó là PR sửa file đã merge):

1. Kiểm tra trạng thái CUỐI trong DB trước. Ở đây `PayRate` + hai quyền + `CHECK` đã có, chỉ
   thiếu đúng cột `Contract.RatePerPeriod`.
2. Chạy tay phần còn thiếu (câu `ALTER` idempotent).
3. Giờ schema đã đúng ⇒ mới cập nhật checksum:
   `UPDATE dbo.flyway_schema_history SET checksum = <"Resolved locally" trong log> WHERE version='37'`.

**Bài học mới:** chạy test cũng áp migration vào DB local. Sửa file migration sau khi chạy test
là dẫm bẫy checksum, không cần đợi tới lúc merge.

## Kiểm thử

- `PayrollPayTest` (5 ca): chốt → đã trả ghi đúng nhật ký; `DRAFT` không nhảy thẳng sang
  `PAID`; bấm hai lần thì báo "đã đánh dấu" chứ không ghi thêm log; trả cả kỳ chỉ đụng phiếu đã
  chốt; kỳ không có phiếu nào đã chốt thì báo rõ chứ không im lặng trả về 0.
- `PayrollRateByGradeTest` viết lại cho bảng giá mới — giữ nguyên các ca cũ (ranh giới khối 5/6,
  khối 10–12 không được lặng lẽ nhận giá THCS) và thêm: tra theo ngày dạy, biên đúng mốc tăng
  giá, hợp đồng thắng barem chung.
- **Chạy thật end-to-end**: `FINALIZED → PAID` đổi trạng thái đúng; bấm lại lần hai trả 409;
  mở lại phiếu `PAID` trả 409; nhật ký hiện `PAY: FINALIZED→PAID bởi admin`.

## Còn lại

- Đơn giá riêng theo hợp đồng (`Contract.RatePerPeriod`) chưa có màn hình nhập — hiện phải
  `UPDATE` bằng SQL. Cần thêm ô vào form sửa hợp đồng của trang Giáo viên.
- Chưa có ngày chi và hình thức chi (chuyển khoản / tiền mặt) khi đánh dấu đã trả — cần thêm cột
  nếu kế toán yêu cầu đối chiếu sao kê.
