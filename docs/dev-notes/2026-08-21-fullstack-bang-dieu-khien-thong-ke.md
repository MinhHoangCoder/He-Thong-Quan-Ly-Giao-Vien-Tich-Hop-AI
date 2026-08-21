# Viết lại Bảng điều khiển: thống kê bằng SQL aggregate, bộ lọc toàn trang, chi phí phân bổ (2026-08-21)

## Bối cảnh: màn hình chạy được nhưng không dùng được

Bảng điều khiển cũ (`2026-06-06-frontend-dashboard.md`) đã đọc dữ liệu thật, không mock. Nhưng khi
mở ra để soi thì có ba vấn đề khác nhau, chồng lên nhau:

1. **Cách lấy dữ liệu không còn dùng được ở quy mô này.** `DashboardService` nạp nguyên bảng
   `Schedule` (hơn 27.000 dòng) cùng `Assignment`, `Teacher`, `School`, `Subject`, `Payroll` vào
   bộ nhớ rồi lọc bằng Java Stream, kèm N+1 query slot gọi trong vòng lặp. Mỗi lần mở trang là một
   lần kéo gần hết CSDL qua mạng.
2. **Hai thẻ số sai nhãn.** "Tổng giáo viên" thực ra đếm giáo viên `ACTIVE` (90) chứ không phải
   tổng (100). "Tổng phân công trong tháng" thực ra đếm `ACTIVE` ở mọi thời điểm, không liên quan
   gì tới "trong tháng". Cả hai đều hiện ra một con số trông rất bình thường.
3. **Màn hình trống trơn.** Toàn bộ dữ liệu giao dịch nằm ở năm học 2026–2027, tức hoàn toàn ở
   TƯƠNG LAI so với ngày hệ thống, còn `Attendance`/`Payroll`/`TeacherEvaluation` thì rỗng. Mọi
   biểu đồ "N tháng gần nhất" đều nhìn về quá khứ nên không có gì để vẽ.

Vấn đề 3 quan trọng ở chỗ: **code không sai chỗ nào cả**, nhưng nếu không nhận ra thì sẽ đi sửa
code cho tới khi hỏng thật.

## Phần 1 — Chi phí lương: chỗ dễ tính sai nhất trong toàn bộ dự án

### Vấn đề: `Payroll` không lọc được theo trường

Bảng `Payroll` có grain là **(giáo viên × tháng)**. Nó không biết một giáo viên dạy ở trường nào,
môn nào. Nên nếu chỉ cộng `NetAmount`, thẻ "Chi phí lương" sẽ **không phản ứng với bộ lọc trường/
môn** — lọc "THCS Chu Văn An" mà chi phí vẫn là con số toàn trung tâm.

### Cách làm: phân bổ ngược về từng buổi dạy

```
chi phí một buổi = NetAmount của kỳ lương / TaughtHours của kỳ lương đó
```

Nhờ vậy chi phí bám theo `Schedule`, mà `Schedule` thì biết trường và môn → lọc được.

### Hai cái bẫy, cả hai đều ra tiền sai

**Bẫy 1 — `TaughtHours` KHÔNG phải số giờ.**

Tên cột là di sản từ hồi trung tâm trả lương theo giờ. Từ khi chuyển sang trả **theo tiết**
(`PayrollService`), cột này lưu **SỐ TIẾT**, còn `RatePerHour` lưu **đơn giá mỗi tiết**. Schema
không đổi tên vì đổi tên cột đang chạy là một migration rủi ro không cần thiết.

Bản đầu tôi viết công thức theo đúng nghĩa của cái tên:

```java
// SAI
NetAmount / TaughtHours * (thời lượng buổi tính bằng giờ)
```

Ra **8,1 tỉ** thay vì 1,45 tỉ — cao gấp 5,6 lần, vì đã chia cho số tiết rồi lại nhân thêm số giờ.
Con số đó vẫn hiện ra bình thường trên thẻ, không exception, không log.

**Bẫy 2 — chỉ buổi CÓ MẶT mới sinh chi phí.**

Mẫu số `TaughtHours` chỉ đếm buổi chấm công `PRESENT`/`LATE`. Nếu phân bổ cho **mọi** buổi đã
duyệt (kể cả buổi giáo viên vắng) thì tổng sẽ vượt tổng cột "Thực nhận" của Bảng lương đúng bằng
tỉ lệ vắng mặt — 1,518 tỉ so với 1,446 tỉ, lệch 5%. Một khoản tiền không có thật.

```sql
-- DashboardQueryRepository.CHI_PHI
(CASE WHEN att.Status IN ('PRESENT','LATE') AND ISNULL(pr.TaughtHours, 0) > 0
      THEN pr.NetAmount / pr.TaughtHours ELSE 0 END)
```

### Cách tự kiểm tra

Đây là bất biến để đối chiếu bất cứ lúc nào: **tổng chi phí trên Bảng điều khiển (bỏ hết bộ lọc,
chọn đúng kỳ) phải khớp ĐẾN TỪNG ĐỒNG với `SELECT SUM(NetAmount) FROM Payroll` của các kỳ tương
ứng.** Hiện tại: `1.446.125.000đ` cả hai bên. Lệch một đồng nghĩa là công thức phân bổ đã hỏng.

## Phần 2 — Kỳ mặc định là NĂM HỌC, không phải tháng

Chu kỳ kinh doanh của trung tâm là năm học (01/9 → 31/8), không phải tháng dương lịch. Lấy "tháng
này" làm mặc định thì mở dashboard vào tháng hè sẽ ra một màn hình toàn số 0 trong khi năm học vừa
rồi có cả chục nghìn buổi dạy — và người xem sẽ tưởng hệ thống hỏng.

`DashboardFilter.namHocHienHanh()` suy năm học từ tháng hiện tại: từ tháng 9 trở đi là năm học mới.

**Kỳ đối chiếu phải dài BẰNG ĐÚNG kỳ đang xem** (`DashboardFilter.kyTruoc()`). Đem một quý so với
một tháng rồi kết luận "giảm 66%" là con số vô nghĩa nhưng nhìn vẫn rất thuyết phục.

Và **kỳ trước bằng 0 thì KHÔNG hiện phần trăm** — trả `null`. Bản cũ từng hiện `+1801,6%`: tăng từ
1 lên 19 đúng là tăng 1800%, nhưng con số đó chỉ nói mẫu số quá nhỏ; đặt cạnh mũi tên xanh nó
thành lời khoe sai sự thật.

## Phần 3 — "Chưa đo được" khác "bằng không"

Nguyên tắc xuyên suốt: chỗ chưa có dữ liệu trả `null` để giao diện hiện `—`, **tuyệt đối không trả
0**.

Kỳ chưa có dòng chấm công nào mà hiện "Tỉ lệ chuyên cần: 0%" là khẳng định *"đã đo và không ai đi
dạy"* — sai hoàn toàn về nghĩa, và trên số liệu lương thì lẫn lộn hai điều đó là chuyện lớn.

Cả hai luật ở Phần 2 và Phần 3 đã được khoá bằng test (xem cuối bài).

## Phần 4 — Kiến trúc: SQL aggregate thay cho Java Stream

`DashboardQueryRepository` (mới) dùng `NamedParameterJdbcTemplate`, viết SQL tay. Mọi `GROUP BY` /
`SUM` / `COUNT` chạy trong SQL Server; mỗi truy vấn chỉ trả về vài chục dòng.

Vài điểm đáng nhớ:

- **Khối `FROM` dùng chung**: `LEFT JOIN AssignmentSlot` phải đứng **trước** `JOIN School` vì
  trường của buổi suy ra từ ô lịch (V27: một phiếu trải nhiều trường).
- **Nối `Attendance` an toàn cho mọi phép `COUNT`** vì quan hệ là một–một: DB có ràng buộc duy
  nhất `UX_Attendance_ScheduleId`. Nhờ vậy một câu lệnh vừa đếm được buổi, vừa đếm chuyên cần,
  vừa cộng chi phí.
- **Chín con số của một kỳ gộp vào MỘT câu lệnh** — không chỉ nhanh hơn, mà để chúng chắc chắn
  thuộc cùng một ảnh chụp dữ liệu. Chạy rời nhau thì giữa hai câu lệnh có thể có người vừa duyệt
  thêm một buổi, và màn hình hiện ra tỉ lệ lớn hơn 100% mà không ai tái hiện được.
- **Thứ trong tuần** tính bằng `(((DATEPART(WEEKDAY, ...) + @@DATEFIRST - 2) % 7) + 1)` để không
  phụ thuộc `SET DATEFIRST` của phiên kết nối.
- **Điểm đánh giá tách thành truy vấn riêng**: nó không gắn với buổi dạy, nối chung vào truy vấn
  bảng chi tiết sẽ nhân bản mỗi buổi lên đúng bằng số lượt đánh giá và thổi phồng mọi phép cộng.

### Ba endpoint, tách theo chi phí truy vấn

| Endpoint | Nội dung |
|---|---|
| `/dashboard/summary` | 6 thẻ chỉ số — quét một lượt, về gần như tức thì |
| `/dashboard/analytics` | biểu đồ + bảng chi tiết 3 chiều — nặng hơn hẳn |
| `/dashboard/operations` | việc cần xử lý, lịch trong ngày, phân công gần đây |

Tách để thẻ chỉ số hiện ngay, không phải chờ truy vấn chậm nhất; và một khối hỏng thì hai khối kia
vẫn hiển thị được (frontend dùng `Promise.allSettled`).

Thêm `/dashboard/filters` (danh mục cho ô lọc) và `/dashboard/export` (CSV).

> **BREAKING**: `GET /api/v1/dashboard?months=` và DTO `DashboardResponse` đã bị xoá.

### Xuất CSV cho Excel tiếng Việt

Ba chi tiết bắt buộc, thiếu cái nào file cũng hỏng theo một kiểu riêng:

- **BOM UTF-8** ở đầu file — thiếu thì Excel trên Windows đoán bảng mã theo vùng và mọi tên tiếng
  Việt thành ký tự lạ. File vẫn "mở được" nên lỗi này thường chỉ lộ khi đã gửi báo cáo cho người khác.
- **Phân cách bằng dấu chấm phẩy** — Excel bản tiếng Việt hiểu dấu phẩy là dấu thập phân, dùng
  phẩy ngăn cột thì mọi số tiền vỡ sang ô bên cạnh.
- **Thập phân dùng dấu phẩy** (`Locale.GERMANY`) để Excel nhận ra đó là số chứ không phải chữ.

## Phần 5 — Dữ liệu: seed năm học 2025–2026 đã hoàn thành

`database/seed/TSDMS_Seed_NamHoc2025.sql` nhân bản bộ phân công **lùi đúng 364 ngày (52 tuần)** để
giữ nguyên thứ trong tuần — lùi 365 là lệch một thứ, hỏng cả năm.

Ra: 90 phân công, 396 ô lịch, 13.388 buổi (11.557 đã duyệt sau khi gỡ 708 buổi rơi vào `Holiday`,
nên tháng 2 sụt còn 408 buổi vì nghỉ Tết), 11.557 dòng chấm công, 737 dòng lương 9 kỳ, 164 lượt
đánh giá.

Hai ràng buộc của seed:

- **Không dùng `NEWID()`/`RAND()`.** Mọi lựa chọn "ngẫu nhiên" là `CHECKSUM()` trên khóa chính nên
  chạy lại luôn ra cùng một bộ số. Bảo vệ đồ án mà số liệu nhảy mỗi lần chạy là hỏng.
- **Bảng lương tính đúng bằng công thức `PayrollService.generate()`**: trả theo tiết (TH 115k,
  THCS 125k), `TaughtHours` = số tiết, `BaseSalary`/`Allowance` để 0. Màn hình Bảng lương có nút
  "Tạo bảng lương" chạy lại đúng hàm đó; seed tính kiểu khác thì chỉ cần bấm nút một lần là mọi
  con số đổi hết mà không giải thích được.
- **Chép `AssignmentSlot` phải kèm `ClassId`** — thiếu thì `PayrollService.rateForAttendance` rơi
  về đơn giá mặc định TH và toàn bộ lương THCS tính thiếu.

Có file rollback (`TSDMS_Rollback_NamHoc2025.sql`), nhận diện theo `StartDate = 2025-09-08`, đã
chạy thử.

## Phần 6 — Frontend

Dùng lại bộ class chung `page-common.css` (`.page` / `.toolbar` / `.table-wrap` + `.table` /
`.badge-*`) và tái dùng `ui/StatCard.vue`, `ui/Pagination.vue` thay vì viết component riêng — màn
hình mới phải trông giống phần còn lại của app.

`StatCard.vue` thêm prop `invertTrend` (mặc định `false`, không đổi hành vi của các màn cũ): chi
phí **tăng** là tin **xấu**, phải tô đỏ. Màu chip đi theo Ý NGHĨA nghiệp vụ chứ không theo dấu của
phép trừ.

Biểu đồ dùng **chart.js + vue-chartjs**. `utils/chart.js` gom phần `Chart.register` — Chart.js v4
không register thành phần thì ra **khung trắng chứ không báo lỗi**, rất mất công dò. Riêng bản đồ
nhiệt thứ × tiết làm bằng CSS Grid vì Chart.js không có sẵn dạng này.

Màu trục biểu đồ dùng một tông xám trung tính đọc được trên cả hai theme, **không** đọc biến CSS:
`getComputedStyle` không phải reactive, nên lấy theo theme thì lúc người dùng bật nền tối biểu đồ
vẫn giữ màu cũ cho tới khi tải lại trang.

## Phần 7 — Test

| File | Nội dung |
|---|---|
| `DashboardFilterTest` | 13 test — kỳ đối chiếu dài bằng kỳ hiện tại, không hở/chồng ngày, năm học luôn chứa hôm nay, nhãn kỳ, ràng buộc đầu vào |
| `DashboardKpiTest` | 13 test — mock repository; khoá luật "chưa đo được ≠ bằng 0" và "kỳ trước bằng 0 thì không có %" |

Không cần DB, chạy trong ~2,5 giây.

Phần **SQL aggregate chưa có test** vì cần DB thật (testcontainers, mà máy hiện không có Docker).
Bù lại bằng bất biến đối chiếu ở Phần 1 — so tổng chi phí với `SUM(NetAmount)` của `Payroll`.

## Bẫy đã dính khi làm — ghi lại để khỏi mất thời gian lần sau

- **`git reset --hard origin/master` khi nhánh dựng từ `master` cục bộ.** `origin/master` có thể
  đi trước, kéo nhầm cả commit của người khác vào. Gốc đúng lấy bằng `git rev-parse <commit-đầu>^`.
  Luôn `git tag backup-truoc-<việc>` trước khi viết lại lịch sử.
- **Flyway checksum mismatch V31.** PR #165 **sửa nội dung** một migration đã áp vào DB → backend
  không khởi động được. Cách chữa tuỳ trạng thái DB từng máy: kiểm tra kết quả cuối (ở đây là cột
  `School.AppUserId` đã bị xoá chưa) rồi mới quyết định repair checksum hay chạy tay phần còn
  thiếu. **Đừng** xoá dòng khỏi `flyway_schema_history` và **đừng** tắt `validate-on-migrate`.
- **`mvnw clean` báo "Failed to delete target\classes"** — thủ phạm thường là Java extension của
  VS Code giữ khoá thư mục, không phải backend đang chạy. `compile` vẫn chạy bình thường.
- **Đường dẫn API giáo viên là `/api/v1/teacher` (SỐ ÍT)**, gọi `/teachers` trả 404.
