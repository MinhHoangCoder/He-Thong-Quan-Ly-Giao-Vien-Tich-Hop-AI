# Domain: bỏ hẳn cấp 3 — hệ thống chỉ còn khối 1..9 (2026-08-14)

## Vì sao đây không phải chuyện "sửa dropdown cho gọn"

Trung tâm chỉ dạy tiểu học (khối 1–5) và THCS (khối 6–9). Cấp 3 chưa bao giờ là nghiệp vụ thật,
chỉ còn sót trong dữ liệu demo và trong các ràng buộc `1..12` của tầng service. Nhưng chính chỗ
sót đó sinh ra **lỗi tính sai lương**.

`PayrollService` suy đơn giá 1 tiết từ khối của lớp, bằng một biểu thức ba ngôi nằm lọt giữa
thân hàm:

```java
rate = grade <= 5 ? TH_RATE : THCS_RATE;
```

Đọc lướt thì rất hợp lý — "1–5 tiểu học, còn lại THCS". Nhưng vế "còn lại" nuốt gọn cả khối 10,
11, 12: một lớp cấp 3 được tính **125.000đ/tiết theo giá THCS**. Không exception, không log,
không test nào. Sai tiền lương âm thầm.

Đó là lý do phải dọn tận DB chứ không chỉ sửa validate ở form: chỉ cần một dòng khối 10 còn nằm
dưới bảng `SchoolClass` là lỗi vẫn nguyên.

## Sửa

### Tầng service — biến biểu thức ngầm thành hàm có tên

```java
static BigDecimal rateForGrade(Integer grade) {
    if (grade == null || grade < 1 || grade > 9) {
        return null;                       // dữ liệu hỏng: KHÔNG đoán bừa
    }
    return grade <= 5 ? TH_RATE : THCS_RATE;
}
```

Bên gọi rơi về đơn giá thấp nhất **kèm cảnh báo log**. Điểm đổi khác quan trọng không phải là
con số, mà là: dữ liệu hỏng giờ **kêu lên** thay vì lặng lẽ trả một đơn giá trông có vẻ đúng.

`SchoolClassService` siết theo: `VALID_GRADES` còn 1–9, `normalizeGradeLevel` chỉ nhận 1–9 (dữ
liệu "Lớp 10" cũ trả nguyên văn để bị chặn kèm thông báo rõ, thay vì lặng lẽ thành khối 1), regex
tên lớp bỏ nhánh `1[0-2]`.

### Migration V26

| Bước | Việc | Lý do |
|---|---|---|
| 1 | Xóa **mềm** lớp khối 10–12 | Lớp có thể đang bị Assignment / Schedule / Attendance tham chiếu — xóa cứng là gãy FK hoặc mất lịch sử chấm công đã tính lương |
| 2 | Chuẩn hóa `GradeLevel` về đúng một chữ số | Dữ liệu cũ lẫn "7", "Lớp 7", "Khối 7" |
| 3 | Gỡ nhãn khối cấp 3 khỏi `Lesson` | Chỉ gỡ NHÃN, giữ bài giảng — nội dung là công sức soạn thật |
| 4 | `CHECK CK_Class_Grade` | Chốt chặn ở DB: `GradeLevel` chỉ nhận NULL hoặc một chữ số 1–9 |
| 5 | Đổi tên trường demo THPT → THCS | Không xóa: gắn với tài khoản đăng nhập demo `school` (cổng Trường khách hàng) và 3 hợp đồng seed ở V4 |

**Bẫy đáng nhớ nhất — thứ tự nhánh trong bước 2.** Nhánh bắt 10–12 phải chạy **trước**
`PATINDEX`:

```sql
WHEN GradeLevel LIKE '%1[0-2]%' THEN NULL
WHEN PATINDEX('%[1-9]%', GradeLevel) > 0
    THEN SUBSTRING(GradeLevel, PATINDEX('%[1-9]%', GradeLevel), 1)
```

Nếu để `PATINDEX` chạy trước trên `'Khối 10'`, nó tìm thấy ký tự `1` đầu tiên và cho ra khối
`"1"` — biến một lớp cấp 3 thành lớp 1. Sai âm thầm, đúng kiểu lỗi mà cả migration này sinh ra
để dọn.

## Hai lỗi tôi tự tạo ra trong lúc làm, ghi lại vì dễ lặp

**1. Sửa migration bằng tay làm hở khối comment.** Lúc gỡ đoạn `UPDATE AppUser SET FullName`
(cột đó đã bị V6 xóa từ lâu) tôi để sót một mẩu chữ nằm **ngoài** `/* */`. SQL Server báo
`Incorrect syntax near 's?a'` ở dòng 77, Flyway chết ngay, và hậu quả không hề trông giống
nguyên nhân: **toàn bộ 13 integration test đỏ** với thông báo "Failed to load ApplicationContext"
— vì context không dựng nổi khi Flyway hỏng. Muốn thấy nguyên nhân thật phải lọc
`grep -A6 "SQL State  :"`, chứ đọc stack trace của Spring thì chỉ thấy tầng bean.

**2. Tin vào build xanh khi Docker tắt.** `*IT` tự skip khi không có Docker
(`disabledWithoutDocker = true`) và `mvnw verify` vẫn **BUILD SUCCESS**. Lần chạy đầu tôi thấy
xanh trong khi V26 chưa từng được thực thi một lần nào. Với migration thì màu xanh đó vô nghĩa —
phải đọc `Skipped` trong dòng tổng kết, hoặc mở `target/failsafe-reports/*.txt`.

## Kiểm chứng

`mvnw verify` với Docker bật: **13/13 integration test xanh**, trong đó
`SchemaMigrationValidationIT` dựng DB rỗng chạy **V1 → V26** rồi Hibernate `validate` khớp toàn
bộ entity. Unit: 189 test (thêm `PayrollRateByGradeTest` 18 test khóa ranh giới 5/6 và khóa việc
khối 10–12 không còn được gán đơn giá nào). `vite build` OK.

## Còn lại

`AttendanceFutureSessionTest.trongCuaSoThiGhiDuoc` đỏ khi chạy trong khoảng **23:30 → 24:00** —
không liên quan tới thay đổi này, xem phần "test phụ thuộc giờ chạy" ở ghi chú riêng.
