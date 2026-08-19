# Lịch nghỉ (V29) + seed luồng điều phối: Phân công → Lịch dạy → Chấm công → Lương (2026-08-19)

## Bối cảnh: bốn màn hình chạy được nhưng trống trơn

Quét lại DB thấy hồ sơ nền đã khá đầy (101 giáo viên, 30 trường, 198 lớp, 285 khung tiết) nhưng
**toàn bộ bảng vận hành đang 0 dòng**: `Assignment`, `AssignmentSlot`, `Schedule`, `Attendance`,
`Payroll`. Đây là một dây chuyền, mắt đầu trống thì cả dây chết theo:

```
Assignment → AssignmentSlot → Schedule → Attendance → Payroll
```

- `DashboardService` tính trực tiếp từ `Schedule`/`Attendance`/`Payroll` → mọi thẻ chỉ số và
  biểu đồ đều ra 0 hoặc "—".
- `AttendanceDailyService` dựng bảng "Hôm nay" **từ lịch dạy** chứ không từ bảng chấm công →
  không có `Schedule` thì màn hình trắng.
- `PayrollService.generate()` đếm `Attendance` có `PRESENT`/`LATE` → bấm "Tạo bảng lương" ra 0 dòng.

Code không sai chỗ nào cả. Chỉ là không có gì để đếm.

## Phần 1 — Bảng `Holiday` (Flyway V29): làm TRƯỚC khi seed

### Vì sao không seed luôn cho nhanh

`AssignmentService.generateSchedules` trải một ô thời khóa biểu thành buổi dạy bằng vòng lặp
"cộng thêm 1 tuần", và **không hỏi ngày đó có phải ngày nghỉ không**:

```java
for (; !d.isAfter(to); d = d.plusWeeks(1)) { ... }   // trước V29
```

Một phiếu kéo dài học kỳ vì thế luôn đẻ ra buổi rơi vào 30/4, 2/9, Tết. Nếu chỉ dừng ở "lịch
hiển thị hơi sai" thì còn nhẹ. Nhưng buổi "ma" đó không nằm yên:

1. `AttendanceSweepService` quét buổi đã qua mà không ai chấm, sau 30 phút ân hạn thì **tự ghi
   VẮNG** với nguồn `SYSTEM`.
2. `PayrollService` chỉ trả tiền cho buổi `PRESENT`/`LATE`.

Kết quả: giáo viên bị trừ lương và tụt tỷ lệ chuyên cần vì một buổi dạy **chưa từng tồn tại**,
và không ai bấm nút nào sai cả. Seed trước rồi vá sau nghĩa là đẻ ra hàng nghìn buổi hỏng rồi
phải đi dọn — nên bảng nghỉ phải có trước.

### Thiết kế bảng

Lưu **khoảng** `[FromDate, ToDate]` chứ không lưu từng ngày: nghỉ hè là MỘT dòng đọc được bằng
mắt thay vì sáu chục dòng rời; ngày lễ đơn thì hai cột bằng nhau. Các khoảng **được phép chồng
nhau** — một ngày là ngày nghỉ khi có bất kỳ dòng nào phủ nó, nên không cần ràng buộc chống trùng.

`SchoolId` null = áp dụng toàn hệ thống (lễ quốc gia, nghỉ hè); có giá trị = chỉ trường đó nghỉ
(sửa chữa, lịch riêng).

`Kind` = `NATIONAL` (lễ theo luật) | `BREAK` (kỳ nghỉ của học sinh) | `CENTER` (trung tâm nghỉ riêng).

### Vì sao dữ liệu nghỉ nằm trong migration chứ không trong seed

Đây là **danh mục nghiệp vụ** như `Role`/`Permission`, không phải data demo: thiếu nó thì lịch
sinh sai ở MỌI môi trường, kể cả production.

Nhưng migration cũng đánh dấu rõ hai loại dòng có độ tin cậy khác hẳn nhau:

| Loại | Ví dụ | Độ tin cậy |
|---|---|---|
| Ngày dương lịch cố định | 1/1, 30/4, 1/5, 2/9 | Chắc chắn — Bộ luật Lao động 2019 Điều 112 |
| Suy từ âm lịch / nghỉ bù | Tết, Giỗ Tổ, ngày liền kề 2/9 | **Cần rà soát** — đánh dấu `[CẦN RÀ SOÁT]` ở cột `Note` |

Tết và Giỗ Tổ phải quy đổi âm lịch, còn số ngày nghỉ Tết và ngày liền kề 2/9 do Chính phủ /
Sở GD&ĐT công bố riêng từng năm. Đối chiếu thông báo chính thức rồi `UPDATE` lại — đừng tin
thẳng số trong migration. (Cùng tinh thần với `TSDMS_TruongHaiPhong_CanRaSoat.md`.)

### Nối vào generator

`AssignmentService` nạp lịch nghỉ **một lần cho cả giai đoạn** rồi lọc trong bộ nhớ, thay vì
hỏi DB cho từng ngày:

```java
private record HolidayCalendar(List<Holiday> ranges) {
    boolean isOff(LocalDate day, Integer schoolId) { ... }
}
```

Điểm dễ sai: phải so theo **trường của TỪNG TIẾT** (`slot.getSchoolId()`), không phải trường cấp
phiếu — từ V27 một phiếu trải được nhiều trường, mà kỳ nghỉ riêng chỉ thuộc về một trường.

Kèm một chốt chặn mới ở cả `create()` và `update()`: nếu cả giai đoạn rơi trọn vào ngày nghỉ thì
không sinh được buổi nào → **chặn ngay**, không để lại phiếu rỗng. Phiếu rỗng vô dụng mà vẫn có
hại: giáo viên không có gì để xác nhận, còn luật chống trùng vẫn coi khung giờ đó là đã bị chiếm
suốt cả giai đoạn.

`AssignmentHolidaySkipTest` phủ ba tình huống và cố ý đi qua `create()` chứ không gọi thẳng bộ
lọc — bản thân luật lọc thì dễ đúng, cái dễ hỏng là nó có được **NỐI** vào luồng ghi hay không
(cùng lý do với `AssignmentReactivateConflictTest`). Mốc ngày trong test tính từ
`BusinessTime.today()` chứ không ghi cứng: phiếu tạo mới bị chặn nếu ngày bắt đầu nằm trong quá
khứ, ghi cứng ngày là hẹn giờ cho test tự hỏng.

## Phần 2 — Seed `TSDMS_Seed_PhanCong.sql`

### Ba đợt phủ trọn vòng đời phiếu

| Đợt | Giai đoạn | Trạng thái phiếu | Dùng để làm gì |
|---|---|---|---|
| A | HK2 2025-2026 (05/01 → 22/05/2026) | `COMPLETED`, buổi `APPROVED` | Lịch sử: biểu đồ 7 tháng, chuyên cần, lương đã chốt/đã trả |
| B | HK1 2026-2027 (10/08/2026 → 15/01/2027) | `ACTIVE`, buổi `APPROVED` | Hiện tại: lịch tuần này, chấm công hôm nay, lương tháng này |
| C | Từ thứ Hai tới → 15/01/2027 | `PENDING` (+ vài `REJECTED`) | Việc đang chờ: tab Chờ xác nhận, lời mời trong chuông |

Đợt A cần **lớp của năm học 2025-2026** — file seed nhân bản danh sách lớp từ năm 2026-2027.
Không có lớp đúng năm học thì phân công tháng 1/2026 lại trỏ vào lớp năm 2026-2027, dữ liệu tự
mâu thuẫn ngay trong chính nó.

### Vì sao phải xếp bằng vòng lặp thay vì một câu INSERT

Ba luật nghiệp vụ không thể bỏ qua, vì lịch trùng giờ làm **mọi báo cáo phía sau đều sai**:

1. Giáo viên chỉ dạy môn mình có trong `TeacherSubject`.
2. Không ai — giáo viên hay lớp — bị xếp hai chỗ trùng giờ trong cùng một năm học. So theo
   **khoảng giờ thật** của tiết, không so số tiết: "tiết 1" của hai trường là hai `PeriodId`
   khác nhau mà giờ vẫn đè nhau.
3. Một giáo viên không chạy hai trường trong cùng một ngày (không có thời gian di chuyển).

Vòng lặp tham lam duyệt từng khóa, tìm ô trống đầu tiên còn hợp mọi luật. Chậm hơn một câu
INSERT nhưng đây là chỗ duy nhất bảo đảm dữ liệu sinh ra không tự mâu thuẫn.

### Bẫy đã dẫm phải: rải đều thì lương ra 500 nghìn/tháng

Lần chạy đầu, thứ tự ưu tiên là "giao cho người đang **rảnh nhất**". Nghe rất hợp lý, và kết quả
đúng công thức: 297 tiết/tuần chia cho 86 giáo viên → ai cũng ~3 tiết/tuần → bảng lương ra
**~560.000đ/người/tháng**. Con số không sai một đồng nào, mà vô lý hoàn toàn.

Đổi lại thành "**dồn** việc cho người đang dạy nhiều nhất, còn dưới trần 8 tiết/tuần" — giống
cách một trường thật giữ giáo viên quen. Kết quả: 48 giáo viên tham gia, mỗi người 5–8 tiết/tuần,
lương trung bình **~2,6 triệu/tháng**, cao nhất 4,4 triệu. Đúng dáng một trung tâm thật.

> Con số này cũng nói lên một điều đáng ghi lại: **101 giáo viên là quá nhiều so với 18 trường
> đối tác / 198 lớp**. Toàn bộ nhu cầu chỉ khoảng 300–470 tiết/tuần. Muốn cả 101 người có việc
> thì phải mở thêm trường, chứ không phải chia nhỏ tiết ra.

### Khớp công thức lương với `PayrollService`

Đơn giá chép đúng hằng số trong service: **115.000đ/tiết** (khối 1–5), **125.000đ/tiết** (khối 6–9),
tra theo khối của lớp ở **chính tiết đó** (`Schedule.SourceSlotId → AssignmentSlot.ClassId`), không
phải lớp cấp phiếu — một phiếu nay trải nhiều lớp, mà lớp 5 và lớp 6 khác đơn giá.

`TaughtHours` = **số tiết** (cột bị dùng lại, không phải số giờ), `RatePerHour` = đơn giá hiệu
dụng = tổng tiền / số tiết. `NetAmount` là cột computed của DB nên không ghi tay. `BaseSalary`
để 0 cho khớp `generate()` — service cũng **không** đọc `Contract.BaseSalary`.

Nhờ khớp đúng, bấm "Tạo bảng lương" trên giao diện ra y hệt con số đã seed, không nhảy số. Dòng
lương tháng hiện tại để `DRAFT` cho đúng cơ chế: `generate()` chỉ ghi đè dòng nháp, tháng đã
`FINALIZED`/`PAID` được bảo vệ.

### Hai bẫy nữa của dữ liệu seed

**Hạn xác nhận phải nằm ở tương lai.** `AssignmentApprovalService.sweepExpired` chạy mỗi giờ,
chuyển mọi phiếu `PENDING` quá hạn thành `EXPIRED` và **hủy sạch buổi dạy chưa diễn ra**. Seed
hạn quá khứ thì chỉ sau một tiếng chạy backend là toàn bộ đợt C tự bốc hơi.

**Buổi sinh ra ở `PENDING` rồi mới `UPDATE` lên `APPROVED`**, thay vì insert thẳng trạng thái
cuối — để trigger `TR_Schedule_StatusLog` ghi lại được lịch sử đổi trạng thái, đúng như đường đi
thật. Bảng `ScheduleStatusLog` nhờ đó có dữ liệu thay vì rỗng.

## Kết quả đo được

| Bảng | Trước | Sau |
|---|---|---|
| Assignment | 0 | 296 (132 ACTIVE · 132 COMPLETED · 26 PENDING · 6 REJECTED) |
| AssignmentSlot | 0 | 476 tiết/tuần |
| Schedule | 0 | 9.712 (bỏ 497 buổi rơi vào ngày nghỉ) |
| ScheduleStatusLog | 0 | 9.179 |
| Attendance | 0 | 4.498 — chuyên cần 93% |
| Payroll | 0 | 226 dòng, 6 kỳ (1–5/2026 + 8/2026) |
| SchoolClass | 198 | 396 (thêm năm học 2025-2026) |

Năm phép kiểm tra toàn vẹn đều ra **0 lỗi**: không buổi nào rơi vào ngày nghỉ, không giáo viên
nào bị xếp hai chỗ trùng giờ, không lớp nào có hai giáo viên trùng giờ, không buổi nào vào T7/CN,
không phiếu nào giao môn ngoài chuyên môn giáo viên.

Biểu đồ nhóm môn 7 tháng ra đúng dáng thật: T2–T5 có số, **T6–T7 bằng 0 (nghỉ hè)**, T8 có lại.

## Chạy lại / gỡ ra

```bash
sqlcmd -S localhost -d TSDMS -U tsdms_app -P *** -i database/seed/TSDMS_Seed_PhanCong.sql
```

Gỡ: `database/seed/TSDMS_Rollback_PhanCong.sql`. ⚠ File gỡ xóa **toàn bộ** dữ liệu phân công /
chấm công / lương trong DB, không chỉ phần do seed sinh ra — bốn bảng đó rỗng trước khi seed
chạy nên "toàn bộ" và "phần của seed" là một. Chỉ chạy trên máy demo.

Bảng `Holiday` phải tồn tại trước (khởi động backend một lần cho Flyway nạp V29); thiếu thì seed
dừng ngay chứ không seed nửa vời.

## Còn lại (không nằm trong lần này)

- **Chưa có màn hình quản lý lịch nghỉ.** Thêm/sửa ngày nghỉ hiện phải làm bằng SQL. Cần một
  trang CRUD cho phòng Đào tạo trước khi vào năm học mới.
- Phiếu đã sinh buổi **trước** V29 không tự dọn: buổi ngày lễ cũ vẫn nằm đó. Ở DB này không có
  vấn đề (seed chạy sau V29), nhưng môi trường đã có dữ liệu thật thì cần một migration dọn thêm.
- Đơn giá tiết vẫn hard-code trong `PayrollService` — muốn đổi giá phải sửa code và build lại.
