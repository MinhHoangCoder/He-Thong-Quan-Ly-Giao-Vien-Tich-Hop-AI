/* =====================================================================
   TSDMS — SEED CHẤM CÔNG + BẢNG LƯƠNG KỲ 9/2026
   ---------------------------------------------------------------------
   CHẠY THẾ NÀO
     Chạy SAU TSDMS_Seed_PhanCong_2026_09.sql — file này bám vào buổi dạy do
     file kia sinh ra và không tự tạo buổi nào.
       sqlcmd -S localhost -d TSDMS -E -i TSDMS_Seed_ChamCong_2026_09.sql

   FILE NÀY LÀM GÌ
     · Chấm công cho các buổi ĐÃ DIỄN RA của học kỳ mới: 03/09 → 06/09/2026.
       Buổi từ 07/09 trở đi KHÔNG có dòng chấm công nào — đó là ngày chưa tới,
       và một bảng chấm công điền sẵn cho tương lai là thứ mà người chấm nhìn
       một cái là biết dữ liệu bịa.
     · Chừa đúng HAI dòng bất thường ở trang đầu: một VẮNG KHÔNG PHÉP và một
       NGHỈ CÓ PHÉP. Hai dòng đủ để kể được cả hai nhánh xử lý mà bảng vẫn
       sạch; nhiều hơn thì thành một bảng đầy lỗi, không giống trung tâm đang
       vận hành bình thường.
     · Đóng băng đơn giá vào từng dòng chấm công (RateAmount / RateSource của
       V40) — sau này có tăng giá tiết thì kỳ lương này vẫn ra đúng số cũ.
     · Dựng lại phiếu lương nháp kỳ 9/2026 từ chính bảng chấm công vừa ghi.

   MỘT DÒNG NGHỈ CÓ PHÉP = MỘT CHUỖI HOÀN CHỈNH, KHÔNG PHẢI MỘT Ô ĐƠN LẺ
     Dòng nghỉ phép được dựng đủ ba mắt cho khớp nhau, đúng như khi giáo viên
     thao tác thật trên phần mềm:
         đơn xin nghỉ (APPROVED)
              → buổi dạy CANCELLED + CancelKind = 'LEAVE'  → hiện "Nghỉ có phép"
              → dòng chấm công Status = 'LEAVE'            → không tính lương
     Chỉ sửa mỗi cột Status của chấm công thì mở màn Lịch dạy ra buổi ấy vẫn
     xanh như thường, và người xem sẽ hỏi ngay câu đó.

   VỀ BẢNG LƯƠNG
     Phiếu lương ở đây tính theo ĐÚNG công thức của PayrollService: cộng
     RateAmount của các dòng PRESENT/LATE, số tiết là số dòng, đơn giá hiệu
     dụng là tổng chia số tiết. Chúng được ghi ở trạng thái DRAFT nên bấm
     "Tính lại" trên màn Bảng lương sẽ ghi đè bằng chính con số đó — nói cách
     khác, seed này chỉ làm sẵn việc mà nút bấm vẫn làm được, không thay thế
     nó. Nếu hai bên lệch nhau thì tin cái nút, vì nó là mã nguồn chạy thật.
   ===================================================================== */

USE TSDMS;
GO

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

DECLARE @tuNgay  DATE = '2026-09-03';   -- khai giảng học kỳ
DECLARE @denNgay DATE = '2026-09-06';   -- hôm nay; từ đây trở đi là chưa dạy
DECLARE @admin   INT  = (SELECT MIN(Id) FROM AppUser WHERE Username = 'admin');

/* =====================================================================
   1) DỌN CHẤM CÔNG CŨ TRONG ĐÚNG KHOẢNG NÀY
   ---------------------------------------------------------------------
   Giới hạn theo khoảng ngày chứ không xóa cả bảng: chấm công của năm học
   2025-2026 là phần lịch sử đang đỡ cho biểu đồ và các kỳ lương đã trả.
   ===================================================================== */

DELETE FROM AttendanceChangeLog
WHERE AttendanceId IN (SELECT Id FROM Attendance WHERE WorkDate BETWEEN @tuNgay AND @denNgay);
DELETE FROM AttendanceAmendRequest
WHERE ScheduleId IN (SELECT Id FROM Schedule WHERE CAST(StartTime AS DATE) BETWEEN @tuNgay AND @denNgay);
DELETE FROM Attendance WHERE WorkDate BETWEEN @tuNgay AND @denNgay;
GO

/* =====================================================================
   2) CHẤM CÔNG CÓ MẶT CHO MỌI BUỔI ĐÃ DIỄN RA
   ---------------------------------------------------------------------
   Đơn giá tra theo KHỐI của lớp trong ô thời khóa biểu sinh ra buổi (không
   phải lớp cấp phiếu — từ V16 một phiếu trải nhiều lớp, mà khối 5 và khối 6
   khác đơn giá), và theo NGÀY DẠY chứ không theo hôm nay. Chép luật này từ
   AttendanceRepository.findPayableWithGrade để hai bên không nói khác nhau.
   ===================================================================== */

DECLARE @tuNgay  DATE = '2026-09-03';
DECLARE @denNgay DATE = '2026-09-06';
DECLARE @admin   INT  = (SELECT MIN(Id) FROM AppUser WHERE Username = 'admin');

INSERT INTO Attendance
    (TeacherId, ScheduleId, WorkDate, CheckIn, CheckOut, Status, CheckInMethod,
     ConfirmedByUserId, ConfirmedAt, Note, CreatedAt, CreatedBy, AutoCheckOut,
     RateAmount, RateSource)
SELECT s.TeacherId,
       s.Id,
       CAST(s.StartTime AS DATE),
       /* Giáo viên tới sớm vài phút — số phút suy từ chính Id buổi để mỗi dòng
          một khác mà chạy lại vẫn ra y hệt (không dùng RAND: seed phải lặp lại được). */
       CAST(DATEADD(MINUTE, -(s.Id % 6) - 2, CAST(s.StartTime AS DATETIME)) AS TIME(0)),
       CAST(DATEADD(MINUTE,  (s.Id % 4) + 1, CAST(s.EndTime   AS DATETIME)) AS TIME(0)),
       'PRESENT',
       'SELF',
       @admin,
       CAST(s.EndTime AS DATETIME2(3)),
       NULL,
       CAST(s.EndTime AS DATETIME2(3)),
       @admin,
       0,
       pr.Amount,
       'PAY_RATE'
FROM Schedule s
JOIN Assignment a       ON a.Id = s.AssignmentId
LEFT JOIN AssignmentSlot sl ON sl.Id = s.SourceSlotId
LEFT JOIN SchoolClass c ON c.Id = COALESCE(sl.ClassId, a.ClassId)
JOIN PayRate pr ON TRY_CAST(c.GradeLevel AS INT) BETWEEN pr.GradeFrom AND pr.GradeTo
               AND CAST(s.StartTime AS DATE) >= pr.EffectiveFrom
               AND (pr.EffectiveTo IS NULL OR CAST(s.StartTime AS DATE) <= pr.EffectiveTo)
WHERE s.IsDeleted = 0
  AND s.Status = 'APPROVED'
  AND CAST(s.StartTime AS DATE) BETWEEN @tuNgay AND @denNgay;
GO

/* =====================================================================
   3) HAI DÒNG BẤT THƯỜNG Ở TRANG ĐẦU
   ---------------------------------------------------------------------
   Màn Chấm công sắp xếp WorkDate giảm dần rồi Id giảm dần, 10 dòng một trang
   (AttendanceRepository, PAGE_SIZE = 10). Chọn theo THỨ HẠNG chứ không theo
   Id cụ thể để chạy lại lần nào cũng rơi đúng trang đầu.
   ===================================================================== */

DECLARE @tuNgay  DATE = '2026-09-03';
DECLARE @denNgay DATE = '2026-09-06';
DECLARE @admin   INT  = (SELECT MIN(Id) FROM AppUser WHERE Username = 'admin');

IF OBJECT_ID('tempdb..#Dau') IS NOT NULL DROP TABLE #Dau;
SELECT Id, ScheduleId, TeacherId, WorkDate,
       ROW_NUMBER() OVER (ORDER BY WorkDate DESC, Id DESC) AS hang
INTO #Dau
FROM Attendance
WHERE WorkDate BETWEEN @tuNgay AND @denNgay;

DECLARE @idVang  BIGINT = (SELECT Id FROM #Dau WHERE hang = 3);
DECLARE @idNghi  BIGINT = (SELECT Id FROM #Dau WHERE hang = 5);

/* ---- Dòng 1: VẮNG KHÔNG PHÉP ----
   Không có giờ vào/giờ ra, và nguồn là 'SYSTEM': giáo viên không check-in,
   hết buổi tác vụ nền tự ghi vắng. Đây đúng là cách một dòng vắng thật ra
   đời, khác hẳn dòng do kế toán ghi tay. */
UPDATE Attendance
SET Status        = 'ABSENT',
    CheckIn       = NULL,
    CheckOut      = NULL,
    CheckInMethod = 'SYSTEM',
    ConfirmedByUserId = NULL,
    ConfirmedAt   = NULL,
    /* Vắng thì không có tiền — để NULL chứ không để 0, vì 0 nghĩa là "đơn giá
       bằng không", còn NULL nghĩa là "không phát sinh đơn giá". */
    RateAmount    = NULL,
    RateSource    = NULL,
    Note          = N'Không có dữ liệu check-in đến hết buổi.'
WHERE Id = @idVang;

/* ---- Dòng 2: NGHỈ CÓ PHÉP — dựng đủ cả chuỗi ---- */
DECLARE @schNghi BIGINT = (SELECT ScheduleId FROM #Dau WHERE hang = 5);
DECLARE @gvNghi  INT    = (SELECT TeacherId  FROM #Dau WHERE hang = 5);
DECLARE @ngayNghi DATE  = (SELECT WorkDate   FROM #Dau WHERE hang = 5);
DECLARE @pcNghi  INT    = (SELECT AssignmentId FROM Schedule WHERE Id = @schNghi);

DELETE FROM AssignmentLeaveRequest WHERE AssignmentId = @pcNghi AND EffectiveDate = @ngayNghi;

INSERT INTO AssignmentLeaveRequest
    (AssignmentId, TeacherId, EffectiveDate, Reason, Status, DecisionNote, DecidedAt, DecidedByUserId, CreatedAt)
VALUES
    (@pcNghi, @gvNghi, @ngayNghi,
     N'Con nhỏ ốm phải đưa đi khám, xin nghỉ buổi này.',
     'APPROVED',
     N'Đồng ý. Nhờ thầy/cô báo lại lớp giúp trung tâm.',
     DATEADD(DAY, -1, CAST(@ngayNghi AS DATETIME2(3))),
     @admin,
     DATEADD(DAY, -2, CAST(@ngayNghi AS DATETIME2(3))));

/* Buổi dạy chuyển sang "Nghỉ có phép". Phải set UpdatedBy TRƯỚC khi đổi Status
   vì trigger TR_Schedule_StatusLog đọc cột này để biết ai đã đổi. */
UPDATE Schedule
SET UpdatedBy  = @admin,
    UpdatedAt  = SYSUTCDATETIME(),
    Status     = 'CANCELLED',
    CancelKind = 'LEAVE'
WHERE Id = @schNghi;

UPDATE Attendance
SET Status     = 'LEAVE',
    CheckIn    = NULL,
    CheckOut   = NULL,
    RateAmount = NULL,
    RateSource = NULL,
    Note       = N'Nghỉ có phép — đơn xin nghỉ đã được duyệt.'
WHERE Id = @idNghi;
GO

/* =====================================================================
   4) DỰNG LẠI PHIẾU LƯƠNG NHÁP KỲ 9/2026
   ===================================================================== */

DECLARE @admin INT = (SELECT MIN(Id) FROM AppUser WHERE Username = 'admin');

DELETE FROM PayrollChangeLog
WHERE PayrollId IN (SELECT Id FROM Payroll WHERE PeriodYear = 2026 AND PeriodMonth = 9);
DELETE FROM Payroll WHERE PeriodYear = 2026 AND PeriodMonth = 9;

INSERT INTO Payroll
    (TeacherId, PeriodMonth, PeriodYear, BaseSalary, TaughtHours, RatePerHour,
     Allowance, Bonus, Deduction, Status, CreatedAt, CreatedBy)
SELECT z.TeacherId,
       9,
       2026,
       COALESCE(ct.BaseSalary, 0),
       z.soTiet,
       /* Đơn giá HIỆU DỤNG = tổng tiền / số tiết — khớp PayrollService: giáo
          viên dạy một cấp thì bằng đúng đơn giá cấp đó, dạy hai cấp thì là
          trung bình có trọng số, và tổng tiền vẫn đúng. */
       CAST(z.tongTien / z.soTiet AS DECIMAL(18, 2)),
       0, 0, 0,
       'DRAFT',
       SYSUTCDATETIME(),
       @admin
FROM (
    SELECT a.TeacherId,
           CAST(COUNT(*) AS DECIMAL(18, 2)) AS soTiet,
           SUM(a.RateAmount)                AS tongTien
    FROM Attendance a
    WHERE a.WorkDate BETWEEN '2026-09-01' AND '2026-09-30'
      AND a.Status IN ('PRESENT', 'LATE')     -- đúng bộ lọc của findPayableWithGrade
      AND a.RateAmount IS NOT NULL
    GROUP BY a.TeacherId
) z
LEFT JOIN (
    /* Một giáo viên có thể có nhiều hợp đồng theo thời gian — lấy hợp đồng
       đang hiệu lực tại kỳ lương, không lấy bừa hợp đồng đầu tiên. */
    SELECT c.TeacherId, MAX(c.BaseSalary) AS BaseSalary
    FROM Contract c
    WHERE c.Status = 'ACTIVE'
      AND c.StartDate <= '2026-09-30'
      AND (c.EndDate IS NULL OR c.EndDate >= '2026-09-01')
    GROUP BY c.TeacherId
) ct ON ct.TeacherId = z.TeacherId;
GO

/* =====================================================================
   5) TỰ KIỂM CHỨNG
   ===================================================================== */

SELECT N'Dòng chấm công 03–06/09' AS chiTieu, COUNT(*) AS soLuong
FROM Attendance WHERE WorkDate BETWEEN '2026-09-03' AND '2026-09-06'
UNION ALL
SELECT N'  · CÓ MẶT',  COUNT(*) FROM Attendance
 WHERE WorkDate BETWEEN '2026-09-03' AND '2026-09-06' AND Status = 'PRESENT'
UNION ALL
SELECT N'  · VẮNG',    COUNT(*) FROM Attendance
 WHERE WorkDate BETWEEN '2026-09-03' AND '2026-09-06' AND Status = 'ABSENT'
UNION ALL
SELECT N'  · NGHỈ PHÉP', COUNT(*) FROM Attendance
 WHERE WorkDate BETWEEN '2026-09-03' AND '2026-09-06' AND Status = 'LEAVE'
UNION ALL
SELECT N'Chấm công cho ngày CHƯA TỚI (phải = 0)', COUNT(*) FROM Attendance
 WHERE WorkDate > '2026-09-06'
UNION ALL
SELECT N'Phiếu lương nháp kỳ 9/2026', COUNT(*) FROM Payroll
 WHERE PeriodYear = 2026 AND PeriodMonth = 9;

/* Hai dòng bất thường phải nằm trong 10 dòng đầu của bảng Chấm công. */
SELECT N'Vị trí dòng bất thường' AS chiTieu, hang, Status, WorkDate
FROM (
    SELECT Status, WorkDate, ROW_NUMBER() OVER (ORDER BY WorkDate DESC, Id DESC) AS hang
    FROM Attendance WHERE WorkDate BETWEEN '2026-09-03' AND '2026-09-06'
) x
WHERE Status <> 'PRESENT'
ORDER BY hang;
GO
